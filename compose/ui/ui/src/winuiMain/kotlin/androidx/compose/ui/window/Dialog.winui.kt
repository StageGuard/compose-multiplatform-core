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
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWinUIRoot
import androidx.compose.ui.platform.LocalWinUIWindow
import androidx.compose.ui.platform.winUIPositionToComposeOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.Window as XamlWindow
import windows.foundation.EventRegistrationToken
import windows.foundation.Point
import microsoft.ui.xaml.controls.LightDismissOverlayMode
import microsoft.ui.xaml.controls.primitives.FlyoutPlacementMode
import microsoft.ui.xaml.controls.primitives.FlyoutShowOptions
import microsoft.ui.xaml.input.KeyEventHandler
import microsoft.ui.xaml.input.PointerEventHandler
import windows.system.VirtualKey
import androidx.compose.ui.platform.WinUIComposeView
import kotlin.math.roundToInt

@Immutable
actual class DialogProperties actual constructor(
    actual val dismissOnBackPress: Boolean,
    actual val dismissOnClickOutside: Boolean,
    actual val usePlatformDefaultWidth: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DialogProperties) return false

        if (dismissOnBackPress != other.dismissOnBackPress) return false
        if (dismissOnClickOutside != other.dismissOnClickOutside) return false
        if (usePlatformDefaultWidth != other.usePlatformDefaultWidth) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dismissOnBackPress.hashCode()
        result = 31 * result + dismissOnClickOutside.hashCode()
        result = 31 * result + usePlatformDefaultWidth.hashCode()
        return result
    }
}

@Composable
actual fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties,
    content: @Composable () -> Unit,
) {
    val parentWindow = LocalWinUIWindow.current
    val parentRoot = LocalWinUIRoot.current
    val containerSize = LocalWindowInfo.current.containerSize
    val currentContent by rememberUpdatedState(content)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val parentCompositionContext = rememberCompositionContext()
    val dialogHost = remember(parentWindow, parentRoot) {
        WinUIDialogHost(parentWindow, parentRoot)
    }

    SideEffect {
        dialogHost.update(
            properties = properties,
            windowSize = containerSize,
            onDismissRequest = currentOnDismissRequest,
            content = currentContent,
        )
    }
    DisposableEffect(dialogHost) {
        dialogHost.setContent(parentCompositionContext) { dialogHost.Content() }
        dialogHost.open()
        onDispose { dialogHost.close() }
    }
}

internal fun winUIDialogMaxWidth(
    windowWidth: Int,
    availableWidth: Int,
    usePlatformDefaultWidth: Boolean,
): Int {
    if (!usePlatformDefaultWidth) return availableWidth
    val platformWidth = (windowWidth * 0.9f).roundToInt().coerceAtMost(560)
    return availableWidth.coerceAtMost(platformWidth.coerceAtLeast(1))
}

private class WinUIDialogHost(
    private val parentWindow: XamlWindow?,
    private val parentRoot: FrameworkElement?,
) {
    private val composeView = WinUIComposeView()
    private val flyout = TransparentComposeFlyout(composeView.root)
    private var properties: DialogProperties by mutableStateOf(DialogProperties())
    private var windowSize: IntSize by mutableStateOf(IntSize.Zero)
    private var currentContent: @Composable () -> Unit by mutableStateOf({})
    private var onDismissRequest: (() -> Unit)? = null
    private var shouldBeOpen = false
    private var isOpen = false
    private var isClosed = false
    private var dialogContentBoundsInRoot = IntRect.Zero
    private val dismissalState = WinUIPopupDismissState(
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        onDismissRequest = { onDismissRequest?.invoke() },
    )
    private var closingToken: EventRegistrationToken? = null
    private var closedToken: EventRegistrationToken? = null
    private var keyDownToken: EventRegistrationToken? = null
    private var pointerPressedToken: EventRegistrationToken? = null
    private var parentRootLoadedToken: EventRegistrationToken? = null

    init {
        composeView.setTransparentRootBackground()
        flyout.content = composeView.root
        flyout.areOpenCloseAnimationsEnabled = false
        flyout.shouldConstrainToRootBounds = false
        flyout.placement = FlyoutPlacementMode.BottomEdgeAlignedLeft
        flyout.showMode = microsoft.ui.xaml.controls.primitives.FlyoutShowMode.Transient
        closingToken = flyout.closing.add { _, args ->
            args.cancel = dismissalState.onNativeClosing(shouldBeOpen)
        }
        closedToken = flyout.closed.add { _, _ ->
            isOpen = false
        }
        keyDownToken = composeView.root.previewKeyDown.add(KeyEventHandler { _, args ->
            if (!args.handled && isOpen && args.key.isDialogBackKey()) {
                args.handled = dismissalState.onFlyoutBackKey()
            }
        })
        pointerPressedToken = composeView.root.pointerPressed.add(PointerEventHandler { _, args ->
            if (!args.handled && isOpen) {
                val point = args.getCurrentPoint(composeView.root).position
                if (
                    dismissalState.onOutsidePointer(
                        winUIPositionToComposeOffset(
                            x = point.x,
                            y = point.y,
                            density = composeView.owner.density,
                        ),
                        dialogContentBoundsInRoot,
                    )
                ) {
                    args.handled = true
                }
            }
        })
        parentRoot?.let { root ->
            parentRootLoadedToken = root.loaded.add(RoutedEventHandler { _, _ -> updateFlyout() })
        }
    }

    fun setContent(
        parentCompositionContext: CompositionContext,
        content: @Composable () -> Unit,
    ) {
        if (!isClosed) composeView.setContent(parentCompositionContext, content)
    }

    @Composable
    fun Content() {
        val content = currentContent
        val targetWindowSize = windowSize
        Layout(
            content = content,
            modifier = Modifier.semantics { dialog() },
        ) { measurables, constraints ->
            val effectiveWindowSize = targetWindowSize.takeIf { it != IntSize.Zero }
                ?: constraints.finiteMaxSizeOr(IntSize.Zero)
            val availableWidth = if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                effectiveWindowSize.width
            }
            val maxWidth = winUIDialogMaxWidth(
                windowWidth = effectiveWindowSize.width,
                availableWidth = availableWidth,
                usePlatformDefaultWidth = properties.usePlatformDefaultWidth,
            )
            val childConstraints = constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = maxWidth.coerceAtLeast(0),
                maxHeight = if (constraints.hasBoundedHeight) {
                    constraints.maxHeight
                } else {
                    effectiveWindowSize.height
                },
            )
            val placeables = measurables.map { measurable ->
                measurable.measure(childConstraints)
            }
            val contentSize = IntSize(
                width = placeables.maxOfOrNull { it.width } ?: 0,
                height = placeables.maxOfOrNull { it.height } ?: 0,
            )
            val hostSize = IntSize(
                width = effectiveWindowSize.width.coerceAtLeast(contentSize.width),
                height = effectiveWindowSize.height.coerceAtLeast(contentSize.height),
            )
            val position = IntOffset(
                x = ((hostSize.width - contentSize.width) / 2).coerceAtLeast(0),
                y = ((hostSize.height - contentSize.height) / 2).coerceAtLeast(0),
            )
            dialogContentBoundsInRoot = IntRect(position, contentSize)
            layout(hostSize.width, hostSize.height) {
                placeables.forEach { placeable -> placeable.placeRelative(position) }
            }
        }
    }

    fun update(
        properties: DialogProperties,
        windowSize: IntSize,
        onDismissRequest: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        this.properties = properties
        this.windowSize = windowSize
        this.onDismissRequest = onDismissRequest
        this.currentContent = content
        dismissalState.update(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            onDismissRequest = onDismissRequest,
        )
        flyout.lightDismissOverlayMode = if (properties.dismissOnClickOutside) {
            LightDismissOverlayMode.On
        } else {
            LightDismissOverlayMode.Off
        }
        if (windowSize != IntSize.Zero) {
            composeView.setWindowContainerSize(windowSize)
            composeView.rootFrameworkElement.width = windowSize.width.coerceAtLeast(1).toDouble()
            composeView.rootFrameworkElement.height = windowSize.height.coerceAtLeast(1).toDouble()
        }
        updateFlyout()
    }

    fun open() {
        if (isClosed) return
        shouldBeOpen = true
        updateFlyout()
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        shouldBeOpen = false
        isOpen = false
        closingToken?.let { token -> runCatching { flyout.closing.remove(token) } }
        closingToken = null
        closedToken?.let { token -> runCatching { flyout.closed.remove(token) } }
        closedToken = null
        keyDownToken?.let { token ->
            runCatching { composeView.root.previewKeyDown.remove(token) }
        }
        keyDownToken = null
        pointerPressedToken?.let { token ->
            runCatching { composeView.root.pointerPressed.remove(token) }
        }
        pointerPressedToken = null
        parentRootLoadedToken?.let { token ->
            runCatching { parentRoot?.loaded?.remove(token) }
        }
        parentRootLoadedToken = null
        runCatching { flyout.hide() }
        flyout.popupContent = null
        composeView.dispose()
    }

    private fun updateFlyout() {
        if (isClosed || !shouldBeOpen) return
        val root = parentRoot ?: return
        val xamlRoot = runCatching { root.xamlRoot }.getOrNull() ?: return
        if (!runCatching { root.isLoaded }.getOrDefault(false)) return
        val size = windowSize.takeIf { it != IntSize.Zero } ?: return
        composeView.rootFrameworkElement.width = size.width.coerceAtLeast(1).toDouble()
        composeView.rootFrameworkElement.height = size.height.coerceAtLeast(1).toDouble()
        if (!isOpen) {
            isOpen = true
            flyout.xamlRoot = xamlRoot
            val options = FlyoutShowOptions().also {
                it.position = Point(0f, 0f)
                it.placement = FlyoutPlacementMode.BottomEdgeAlignedLeft
            }
            runCatching { flyout.showAt(root, options) }.onFailure { isOpen = false }
        }
    }
}

private fun VirtualKey.isDialogBackKey(): Boolean =
    this == VirtualKey.Escape || this == VirtualKey.GoBack || this == VirtualKey.NavigationCancel

private fun Constraints.finiteMaxSizeOr(fallback: IntSize): IntSize =
    IntSize(
        width = if (hasBoundedWidth) maxWidth else fallback.width,
        height = if (hasBoundedHeight) maxHeight else fallback.height,
    )
