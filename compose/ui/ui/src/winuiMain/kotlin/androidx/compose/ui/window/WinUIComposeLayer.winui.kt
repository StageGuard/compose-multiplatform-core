/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusOwner
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusTargetNode
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.node.UiApplier
import androidx.compose.ui.unit.IntRect

/**
 * Hosts the layers of one root: popups and dialogs drawn above its content in the same window,
 * the way the Skiko targets draw `ComposeSceneLayer`s on the same canvas
 * (`CanvasLayersComposeScene`).
 *
 * A layer is a separate composition whose layout node is a child of the root node, after the
 * content. So it is laid out against the window, drawn above the content, hit tested before it,
 * and its semantics are part of the same tree.
 */
internal class WinUIComposeLayerHost(
    private val root: LayoutNode,
    internal val focusOwner: () -> FocusOwner,
) {
    private val layers = mutableListOf<WinUIComposeLayer>()
    private var isGestureInProgress = false

    // The layer that took a press outside of it and kept it from the content below. It also gets
    // the release of that press, as dialogs dismiss on the release.
    private var outsidePressLayer: WinUIComposeLayer? = null

    val layersForTest: List<WinUIComposeLayer>
        get() = layers

    fun createLayer(
        focusable: Boolean,
        consumePointerInputOutside: Boolean,
    ): WinUIComposeLayer = WinUIComposeLayer(this, focusable, consumePointerInputOutside)

    internal fun attach(layer: WinUIComposeLayer) {
        if (layer in layers) return
        layers += layer
        // Layers stay after the nodes of the content, which the content composition inserts by
        // index from the start.
        root.insertAt(root.foldedChildren.size, layer.node)
        remeasureRootAfterItsLayout()
    }

    // A popup is often composed while its parent is measured, in a Scaffold or another
    // SubcomposeLayout. When that is the pass that measures the root, the root does not measure
    // a child that is added meanwhile, and the request of the insertion is dropped: the layer
    // would stay unmeasured, and invisible, until the window is resized. Ask again after the pass.
    private fun remeasureRootAfterItsLayout() {
        val owner = root.owner ?: return
        if (root.layoutState == LayoutNode.LayoutState.Idle) return
        owner.registerOnLayoutCompletedListener(
            object : Owner.OnLayoutCompletedListener {
                override fun onLayoutComplete() {
                    if (root.isAttached) {
                        root.requestRemeasure(forceRequest = true)
                    }
                }
            }
        )
    }

    internal fun detach(layer: WinUIComposeLayer) {
        if (!layers.remove(layer)) return
        if (outsidePressLayer === layer) {
            outsidePressLayer = null
        }
        val index = root.foldedChildren.indexOf(layer.node)
        if (index >= 0) {
            root.removeAt(index, 1)
        }
    }

    /**
     * Tells the layers about a pointer event outside of them, before the content gets the event.
     *
     * As in `CanvasLayersComposeScene`, a press that starts a gesture goes to every layer above
     * the one that contains it, up to the first layer that consumes input outside of it. The
     * release of a press that such a layer kept from the content goes to that layer too.
     */
    fun onPointerEvent(
        eventType: PointerEventType,
        position: Offset,
        button: PointerButton?,
        isAnyButtonPressed: Boolean,
    ) {
        when (eventType) {
            PointerEventType.Press -> {
                if (isGestureInProgress) return
                isGestureInProgress = true
                outsidePressLayer = null
                for (layer in layers.asReversed().toList()) {
                    if (layer.contains(position)) break
                    layer.onOutsidePointerEvent?.invoke(PointerEventType.Press, button)
                    if (layer.consumePointerInputOutside) {
                        outsidePressLayer = layer
                        break
                    }
                }
            }
            PointerEventType.Release -> {
                if (isAnyButtonPressed) return
                isGestureInProgress = false
                val layer = outsidePressLayer ?: return
                outsidePressLayer = null
                if (layer in layers && !layer.contains(position)) {
                    layer.onOutsidePointerEvent?.invoke(PointerEventType.Release, button)
                }
            }
            else -> Unit
        }
    }

    fun cancelPointerInput() {
        isGestureInProgress = false
        outsidePressLayer = null
    }

    fun dispose() {
        layers.toList().forEach { it.close() }
    }
}

/**
 * A popup or dialog above the content of a [WinUIComposeLayerHost].
 *
 * @param focusable whether the layer takes focus while it is shown, so that key events go to it
 *   and focus cannot leave it, as with a focusable `ComposeSceneLayer`.
 * @param consumePointerInputOutside whether the content below the layer gets no pointer input
 *   while the layer is shown.
 */
internal class WinUIComposeLayer internal constructor(
    private val host: WinUIComposeLayerHost,
    val focusable: Boolean,
    val consumePointerInputOutside: Boolean,
) {
    private val focusRequester = FocusRequester()
    private var previouslyFocused: FocusTargetNode? = null
    private var hasTakenFocus = false
    private var composition: Composition? = null
    private var isClosed = false

    /**
     * The bounds of the content in the window, set by the measure policy of the content. Pointer
     * events outside of them are outside of the layer.
     */
    var boundsInWindow: IntRect = IntRect.Zero

    /**
     * The color drawn over the window below the content of the layer.
     */
    var scrimColor: Color by mutableStateOf(Color.Transparent)

    var onOutsidePointerEvent: ((eventType: PointerEventType, button: PointerButton?) -> Unit)? =
        null

    private var onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null
    private var onKeyEvent: ((KeyEvent) -> Boolean)? = null

    /**
     * Listens to the key events of a focusable layer: [onPreviewKeyEvent] before the focused
     * content gets them and [onKeyEvent] after it did not consume them, as the key event listener
     * of a Skiko `ComposeSceneLayer`.
     */
    fun setKeyEventListener(
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?,
    ) {
        this.onPreviewKeyEvent = onPreviewKeyEvent
        this.onKeyEvent = onKeyEvent
    }

    internal val node = LayoutNode().also { node ->
        node.measurePolicy = LayerMeasurePolicy
        node.modifier = Modifier
            .drawBehind {
                if (scrimColor.alpha > 0f) {
                    drawRect(scrimColor)
                }
            }
            .then(
                if (consumePointerInputOutside) {
                    // The layer covers the window, so the content below is not hit at all.
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (focusable) {
                    // Before the focus target, so that the listeners are ancestors of every focused
                    // node of the layer, the layer itself included.
                    Modifier
                        .onPreviewKeyEvent { onPreviewKeyEvent?.invoke(it) ?: false }
                        .onKeyEvent { onKeyEvent?.invoke(it) ?: false }
                        .focusRequester(focusRequester)
                        .focusProperties { onExit = { cancelFocusChange() } }
                        .focusTarget()
                } else {
                    Modifier
                }
            )
    }

    val isShown: Boolean
        get() = composition != null && !isClosed

    fun contains(position: Offset): Boolean {
        val bounds = boundsInWindow
        return position.x >= bounds.left &&
            position.x < bounds.right &&
            position.y >= bounds.top &&
            position.y < bounds.bottom
    }

    fun setContent(parent: CompositionContext, content: @Composable () -> Unit) {
        if (isClosed) return
        host.attach(this)
        val current = composition ?: Composition(UiApplier(node), parent).also {
            composition = it
        }
        current.setContent(content)
        if (focusable && !hasTakenFocus) {
            hasTakenFocus = true
            val focusOwner = host.focusOwner()
            previouslyFocused = focusOwner.activeFocusTargetNode
            focusRequester.requestFocus()
        }
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        composition?.dispose()
        composition = null
        host.detach(this)
        if (hasTakenFocus) {
            // The content below keeps its focus while a focusable layer is shown on desktop,
            // because the layer has its own focus tree. Give it back here.
            previouslyFocused?.takeIf { it.isAttached }?.requestFocus()
            previouslyFocused = null
        }
    }
}

// Fills the window and places the content at the window origin; the content positions itself.
private val LayerMeasurePolicy = MeasurePolicy { measurables, constraints ->
    val contentConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val placeables = measurables.map { it.measure(contentConstraints) }
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeables.forEach { it.place(0, 0) }
    }
}

internal val LocalWinUIComposeLayerHost = staticCompositionLocalOf<WinUIComposeLayerHost?> { null }

/**
 * Creates a layer for the lifetime of the call site and shows [content] in it.
 */
@Composable
internal fun rememberWinUIComposeLayer(
    focusable: Boolean,
    consumePointerInputOutside: Boolean,
): WinUIComposeLayer? {
    val host = LocalWinUIComposeLayerHost.current ?: return null
    return remember(host, focusable, consumePointerInputOutside) {
        host.createLayer(focusable, consumePointerInputOutside)
    }
}

@Composable
internal fun WinUIComposeLayer.Content(content: @Composable () -> Unit) {
    val parentComposition = rememberCompositionContext()
    DisposableEffect(this) {
        setContent(parentComposition, content)
        onDispose {}
    }
}
