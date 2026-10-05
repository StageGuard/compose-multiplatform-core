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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.winUISystemBooleanProperty
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.coroutines.CoroutineContext
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Properties used to customize the behavior of a [Dialog], the same as on the Skiko targets.
 *
 * @property dismissOnBackPress whether the dialog can be dismissed by pressing the escape key.
 * If true, pressing the escape key will call onDismissRequest.
 * @property dismissOnClickOutside whether the dialog can be dismissed by clicking outside the
 * dialog's bounds. If true, clicking outside the dialog will call onDismissRequest.
 * @property usePlatformDefaultWidth Whether the width of the dialog's content should be limited to
 * the platform default, which is smaller than the screen width.
 * @property usePlatformInsets Whether the size of the dialog's content should be limited by
 * platform insets.
 * @property useSoftwareKeyboardInset Whether the size of the dialog's content should be limited by
 * software keyboard inset.
 * @property scrimColor Color of background fill.
 * @property animateTransition Whether to animate the appearance and disappearance of the dialog.
 */
@Immutable
actual class DialogProperties @ExperimentalComposeUiApi constructor(
    actual val dismissOnBackPress: Boolean = true,
    actual val dismissOnClickOutside: Boolean = true,
    actual val usePlatformDefaultWidth: Boolean = true,
    val usePlatformInsets: Boolean = true,
    val useSoftwareKeyboardInset: Boolean = true,
    val scrimColor: Color = DefaultScrimColor,
    @property:ExperimentalComposeUiApi
    val animateTransition: Boolean = WinUIDialogAnimationEnabled,
) {
    actual constructor(
        dismissOnBackPress: Boolean,
        dismissOnClickOutside: Boolean,
        usePlatformDefaultWidth: Boolean,
    ) : this(
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        usePlatformDefaultWidth = usePlatformDefaultWidth,
        usePlatformInsets = true,
        useSoftwareKeyboardInset = true,
        scrimColor = DefaultScrimColor,
    )

    constructor(
        dismissOnBackPress: Boolean = true,
        dismissOnClickOutside: Boolean = true,
        usePlatformDefaultWidth: Boolean = true,
        usePlatformInsets: Boolean = true,
        useSoftwareKeyboardInset: Boolean = true,
        scrimColor: Color = DefaultScrimColor,
    ) : this(
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        usePlatformDefaultWidth = usePlatformDefaultWidth,
        usePlatformInsets = usePlatformInsets,
        useSoftwareKeyboardInset = useSoftwareKeyboardInset,
        scrimColor = scrimColor,
        animateTransition = WinUIDialogAnimationEnabled,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DialogProperties) return false

        if (dismissOnBackPress != other.dismissOnBackPress) return false
        if (dismissOnClickOutside != other.dismissOnClickOutside) return false
        if (usePlatformDefaultWidth != other.usePlatformDefaultWidth) return false
        if (usePlatformInsets != other.usePlatformInsets) return false
        if (useSoftwareKeyboardInset != other.useSoftwareKeyboardInset) return false
        if (scrimColor != other.scrimColor) return false
        @OptIn(ExperimentalComposeUiApi::class)
        if (animateTransition != other.animateTransition) return false

        return true
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun hashCode(): Int {
        var result = dismissOnBackPress.hashCode()
        result = 31 * result + dismissOnClickOutside.hashCode()
        result = 31 * result + usePlatformDefaultWidth.hashCode()
        result = 31 * result + usePlatformInsets.hashCode()
        result = 31 * result + useSoftwareKeyboardInset.hashCode()
        result = 31 * result + scrimColor.hashCode()
        result = 31 * result + animateTransition.hashCode()
        return result
    }
}

@Composable
actual fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties,
    content: @Composable () -> Unit,
) {
    val layer = rememberWinUIComposeLayer(focusable = true, consumePointerInputOutside = true)
    if (layer == null) {
        // Not hosted by a WinUIComposeView, which provides the layers.
        WinUIInlineDialogLayout(content)
        return
    }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    WinUIBackHandler(enabled = properties.dismissOnBackPress) {
        currentOnDismissRequest()
    }
    layer.onOutsidePointerEvent = if (properties.dismissOnClickOutside) {
        { eventType: PointerEventType, button: PointerButton? ->
            // Clicking outside dialog is clicking on scrim.
            // So this behavior should match regular clicks or [detectTapGestures] that accepts
            // only primary mouse button clicks.
            if (eventType == PointerEventType.Release &&
                (button == null || button == PointerButton.Primary)
            ) {
                currentOnDismissRequest()
            }
        }
    } else {
        null
    }
    WinUIDialogLayout(
        layer = layer,
        properties = properties,
        modifier = Modifier.semantics { dialog() },
        content = content,
    )
}

/**
 * The dialog of the Skiko targets (`DialogLayout`): centered in the window above a scrim, with the
 * same appearance and disappearance animation.
 */
@Composable
private fun WinUIDialogLayout(
    layer: WinUIComposeLayer,
    properties: DialogProperties,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val currentContent by rememberUpdatedState(content)
    val currentProperties by rememberUpdatedState(properties)
    val parentCompositionContext = rememberCompositionContext()
    val graphicsContext = LocalGraphicsContext.current
    val animator = remember(layer) {
        DialogAppearanceController(
            layer = layer,
            parentCompositionContext = parentCompositionContext,
            graphicsContext = graphicsContext,
        )
    }
    animator.properties = properties
    var layerScope: CoroutineScope? = null

    layer.Content {
        layerScope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            animator.onDialogShown()
        }
        val containerSize = LocalWindowInfo.current.containerSize
        Layout(
            content = currentContent,
            modifier = animator.modifier.then(modifier),
            measurePolicy = rememberDialogMeasurePolicy(layer, currentProperties, containerSize),
        )
    }

    DisposableEffect(layer) {
        onDispose {
            if (layerScope?.isActive == true) {
                animator.hideDialog()
            } else {
                layer.close()
            }
        }
    }
}

private class DialogAppearanceController(
    private val layer: WinUIComposeLayer,
    private val parentCompositionContext: CompositionContext,
    private val graphicsContext: GraphicsContext,
) {
    private var appearanceProgress by mutableFloatStateOf(0f)
    private val graphicsLayer = graphicsContext.createGraphicsLayer()
    var properties: DialogProperties = DialogProperties()

    val modifier = Modifier.drawWithContent {
        graphicsLayer.record {
            this@drawWithContent.drawContent()
        }
        graphicsLayer.applyAnimationProgress(appearanceProgress, density)
        drawLayer(graphicsLayer)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    suspend fun onDialogShown() {
        if (properties.animateTransition) {
            val durationScale = currentCoroutineContext().durationScale()
            withAnimationProgress(
                duration = (durationScale * AnimatedLayerAppearanceDuration).seconds,
            ) { progress ->
                appearanceProgress = progress
                updateScrimLayerColor(progress)
            }
        }
        appearanceProgress = 1f
        layer.scrimColor = properties.scrimColor
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun hideDialog() {
        if (!properties.animateTransition) {
            graphicsContext.releaseGraphicsLayer(graphicsLayer)
            layer.close()
            return
        }
        // Keep drawing the last frame of the dialog while it disappears.
        layer.setContent(parentCompositionContext) {
            val containerSize = LocalWindowInfo.current.containerSize
            Layout(
                modifier = Modifier.drawBehind {
                    graphicsLayer.applyAnimationProgress(appearanceProgress, density)
                    drawLayer(graphicsLayer)
                },
                measurePolicy = rememberDialogMeasurePolicy(layer, properties, containerSize),
            )
            LaunchedEffect(Unit) {
                val durationScale = currentCoroutineContext().durationScale()
                val initialProgress = appearanceProgress
                val duration = durationScale * initialProgress * AnimatedLayerDisappearanceDuration
                withAnimationProgress(duration = duration.seconds) { progress ->
                    val reversedProgress = (1f - progress) * initialProgress
                    appearanceProgress = reversedProgress
                    updateScrimLayerColor(reversedProgress)
                }
                graphicsContext.releaseGraphicsLayer(graphicsLayer)
                layer.close()
            }
        }
    }

    private fun updateScrimLayerColor(progress: Float) {
        layer.scrimColor =
            properties.scrimColor.copy(properties.scrimColor.alpha * contentAlpha(progress))
    }

    private fun contentAlpha(progress: Float): Float =
        AnimatedLayerInitialAlpha + (1f - AnimatedLayerInitialAlpha) * progress

    private fun GraphicsLayer.applyAnimationProgress(progress: Float, density: Float) {
        alpha = contentAlpha(progress)
        val reversedProgress = 1f - progress
        val scale = 1f - reversedProgress * AnimatedLayerScale
        scaleX = scale
        scaleY = scale
        translationY = AnimatedLayerOffsetDp * reversedProgress * density
    }
}

@Composable
private fun rememberDialogMeasurePolicy(
    layer: WinUIComposeLayer,
    properties: DialogProperties,
    containerSize: IntSize,
): MeasurePolicy = remember(layer, properties, containerSize) {
    WinUIComposeLayerMeasurePolicy(
        usePlatformDefaultWidth = properties.usePlatformDefaultWidth,
    ) { contentSize ->
        val windowSize = containerSize.takeIf { it != IntSize.Zero }
            ?: IntSize(contentSize.width, contentSize.height)
        val position = IntOffset(
            x = (windowSize.width - contentSize.width) / 2,
            y = (windowSize.height - contentSize.height) / 2,
        )
        layer.boundsInWindow = IntRect(position, contentSize)
        position
    }
}

/**
 * The dialog as it was before layers: laid out in place, for content that is not hosted by a
 * WinUIComposeView.
 */
@Composable
private fun WinUIInlineDialogLayout(content: @Composable () -> Unit) {
    val currentContent by rememberUpdatedState(content)
    val containerSize = LocalWindowInfo.current.containerSize
    Layout(
        content = currentContent,
        modifier = Modifier.semantics { dialog() },
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { measurable ->
            measurable.measure(looseConstraints)
        }
        val contentSize = IntSize(
            width = placeables.maxOfOrNull { it.width } ?: 0,
            height = placeables.maxOfOrNull { it.height } ?: 0,
        )
        val windowSize = containerSize.takeIf { it != IntSize.Zero }
            ?: constraints.finiteMaxSizeOr(contentSize)
        val position = IntOffset(
            x = ((windowSize.width - contentSize.width) / 2).coerceAtLeast(0),
            y = ((windowSize.height - contentSize.height) / 2).coerceAtLeast(0),
        )

        layout(0, 0) {
            placeables.forEach { placeable ->
                placeable.placeRelative(position)
            }
        }
    }
}

private fun Constraints.finiteMaxSizeOr(fallback: IntSize): IntSize =
    IntSize(
        width = if (hasBoundedWidth) maxWidth else fallback.width,
        height = if (hasBoundedHeight) maxHeight else fallback.height,
    )

private fun CoroutineContext.durationScale(): Float {
    val scale = this[MotionDurationScale]?.scaleFactor ?: 1f
    check(scale >= 0f)
    return scale
}

private suspend fun withAnimationProgress(
    duration: Duration,
    update: (Float) -> Unit,
) {
    update(0f)
    var firstFrameTime = -1L
    var progressDuration = Duration.ZERO
    while (progressDuration < duration) {
        withFrameNanos { frameTime ->
            if (firstFrameTime == -1L) {
                firstFrameTime = frameTime
            }
            progressDuration = (frameTime - firstFrameTime).nanoseconds
            update(easeOutTimingFunction(min(1.0, progressDuration / duration).toFloat()))
        }
    }
}

private fun easeOutTimingFunction(progress: Float): Float = -progress * (progress - 2f)

// The dialog animation of the Skiko targets, `ComposeUiFlags.isDialogAnimationEnabled` there.
private val WinUIDialogAnimationEnabled: Boolean
    get() = !winUISystemBooleanProperty("compose.winui.dialog.animation.disabled")

private val DefaultScrimColor = Color.Black.copy(alpha = 0.6f)
private const val AnimatedLayerOffsetDp = 10f
private const val AnimatedLayerInitialAlpha = 0.2f
private const val AnimatedLayerScale = 0.05f
private const val AnimatedLayerAppearanceDuration = 0.2
private const val AnimatedLayerDisappearanceDuration = 0.1
