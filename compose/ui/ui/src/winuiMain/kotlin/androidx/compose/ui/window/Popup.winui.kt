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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ComposeFeatureFlags
import androidx.compose.ui.LayerType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWinUIRoot
import androidx.compose.ui.platform.LocalWinUIWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WinUIComposeView
import androidx.compose.ui.semantics.popup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import windows.foundation.EventRegistrationToken
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.CornerRadius
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.Setter
import microsoft.ui.xaml.Style
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.controls.Control
import microsoft.ui.xaml.controls.Flyout
import microsoft.ui.xaml.controls.FlyoutPresenter
import microsoft.ui.xaml.controls.LightDismissOverlayMode
import microsoft.ui.xaml.controls.primitives.FlyoutPlacementMode
import microsoft.ui.xaml.controls.primitives.FlyoutShowMode
import microsoft.ui.xaml.controls.primitives.FlyoutShowOptions
import microsoft.ui.xaml.media.SolidColorBrush
import microsoft.ui.xaml.Application as XamlApplication
import windows.foundation.Point
import windows.ui.Color
import microsoft.ui.xaml.Window as XamlWindow

@Immutable
actual class PopupProperties private constructor(
    actual val focusable: Boolean,
    actual val dismissOnBackPress: Boolean,
    actual val dismissOnClickOutside: Boolean,
    actual val clippingEnabled: Boolean,
    actual val usePlatformDefaultWidth: Boolean,
    internal val layerType: LayerType,
) {
    actual constructor(
        focusable: Boolean,
        dismissOnBackPress: Boolean,
        dismissOnClickOutside: Boolean,
        clippingEnabled: Boolean,
        usePlatformDefaultWidth: Boolean,
    ) : this(
        focusable = focusable,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        clippingEnabled = clippingEnabled,
        usePlatformDefaultWidth = usePlatformDefaultWidth,
        layerType = ComposeFeatureFlags.layerType.value,
    )

    @Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
    actual constructor(
        focusable: Boolean,
        dismissOnBackPress: Boolean,
        dismissOnClickOutside: Boolean,
        clippingEnabled: Boolean,
    ) : this(
        focusable = focusable,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        clippingEnabled = clippingEnabled,
        usePlatformDefaultWidth = false,
        layerType = ComposeFeatureFlags.layerType.value,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PopupProperties) return false

        if (focusable != other.focusable) return false
        if (dismissOnBackPress != other.dismissOnBackPress) return false
        if (dismissOnClickOutside != other.dismissOnClickOutside) return false
        if (clippingEnabled != other.clippingEnabled) return false
        if (usePlatformDefaultWidth != other.usePlatformDefaultWidth) return false
        if (layerType != other.layerType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = focusable.hashCode()
        result = 31 * result + dismissOnBackPress.hashCode()
        result = 31 * result + dismissOnClickOutside.hashCode()
        result = 31 * result + clippingEnabled.hashCode()
        result = 31 * result + usePlatformDefaultWidth.hashCode()
        result = 31 * result + layerType.hashCode()
        return result
    }
}

@Composable
actual fun Popup(
    alignment: Alignment,
    offset: IntOffset,
    onDismissRequest: (() -> Unit)?,
    properties: PopupProperties,
    content: @Composable () -> Unit,
) {
    val popupPositionProvider = remember(alignment, offset) {
        AlignmentOffsetPositionProvider(alignment, offset)
    }
    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = properties,
        content = content,
    )
}

@Composable
actual fun Popup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)?,
    properties: PopupProperties,
    content: @Composable () -> Unit,
) {
    if (properties.layerType == LayerType.OnWindow) {
        WinUIWindowPopupLayout(
            popupPositionProvider = popupPositionProvider,
            properties = properties,
            content = content,
        )
    } else {
        WinUICanvasPopupLayout(
            popupPositionProvider = popupPositionProvider,
            properties = properties,
            content = content,
        )
    }
}

@Composable
private fun WinUICanvasPopupLayout(
    popupPositionProvider: PopupPositionProvider,
    properties: PopupProperties,
    content: @Composable () -> Unit,
) {
    var parentBoundsInWindow by remember { mutableStateOf(IntRect.Zero) }

    Layout(
        content = {},
        modifier = Modifier.onPlaced { coordinates ->
            val parentCoordinates = coordinates.parentCoordinates ?: return@onPlaced
            parentBoundsInWindow = IntRect(
                offset = parentCoordinates.positionInWindow().round(),
                size = parentCoordinates.size,
            )
        },
    ) { _, _ ->
        layout(0, 0) {}
    }

    val currentContent by rememberUpdatedState(content)
    val containerSize = LocalWindowInfo.current.containerSize
    val layoutDirection = LocalLayoutDirection.current
    Layout(
        content = currentContent,
        modifier = Modifier.semantics { popup() },
    ) { measurables, constraints ->
        val windowSize = containerSize.takeIf { it != IntSize.Zero }
            ?: constraints.finiteMaxSizeOr(IntSize.Zero)
        val looseConstraints = constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxWidth = constraints.finiteMaxWidthOr(windowSize.width),
            maxHeight = constraints.finiteMaxHeightOr(windowSize.height),
        )
        val placeables = measurables.map { measurable ->
            measurable.measure(looseConstraints)
        }
        val contentSize = IntSize(
            width = placeables.maxOfOrNull { it.width } ?: 0,
            height = placeables.maxOfOrNull { it.height } ?: 0,
        )
        val effectiveWindowSize = windowSize.takeIf { it != IntSize.Zero }
            ?: constraints.finiteMaxSizeOr(contentSize)
        val popupPosition = popupPositionProvider.calculatePosition(
            anchorBounds = parentBoundsInWindow,
            windowSize = effectiveWindowSize,
            layoutDirection = layoutDirection,
            popupContentSize = contentSize,
        ).let { position ->
            if (properties.clippingEnabled) {
                position.clipToWindow(contentSize, effectiveWindowSize)
            } else {
                position
            }
        }
        val localPosition = popupPosition - parentBoundsInWindow.topLeft

        layout(0, 0) {
            placeables.forEach { placeable ->
                placeable.placeRelative(localPosition)
            }
        }
    }
}

@Composable
private fun WinUIWindowPopupLayout(
    popupPositionProvider: PopupPositionProvider,
    properties: PopupProperties,
    content: @Composable () -> Unit,
) {
    val parentBoundsInWindow = remember { mutableStateOf(IntRect.Zero) }
    Layout(
        content = {},
        modifier = Modifier.onPlaced { coordinates ->
            val parentCoordinates = coordinates.parentCoordinates ?: return@onPlaced
            parentBoundsInWindow.value = IntRect(
                offset = parentCoordinates.positionInWindow().round(),
                size = parentCoordinates.size,
            )
        },
    ) { _, _ ->
        layout(0, 0) {}
    }

    val parentWindow = LocalWinUIWindow.current
    val parentRoot = LocalWinUIRoot.current
    val containerSize = LocalWindowInfo.current.containerSize
    val layoutDirection = LocalLayoutDirection.current
    val currentContent by rememberUpdatedState(content)
    val popupHost = rememberNativePopupHost(parentWindow, parentRoot)

    SideEffect {
        popupHost.update(
            popupPositionProvider = popupPositionProvider,
            properties = properties,
            parentBoundsInWindow = parentBoundsInWindow.value,
            windowSize = containerSize,
            layoutDirection = layoutDirection,
            content = currentContent,
        )
    }
    DisposableEffect(popupHost) {
        popupHost.setContent {
            popupHost.Content()
        }
        popupHost.open()
        onDispose {
            popupHost.close()
        }
    }
}

@Composable
private fun rememberNativePopupHost(
    parentWindow: XamlWindow?,
    parentRoot: FrameworkElement?,
): WinUIFlyoutPopupHost =
    remember(parentWindow, parentRoot) {
        WinUIFlyoutPopupHost(parentWindow, parentRoot)
    }

private class WinUIFlyoutPopupHost(
    parentWindow: XamlWindow?,
    private val parentRoot: FrameworkElement?,
) {
    private val composeView = WinUIComposeView()
    private val flyout = TransparentComposeFlyout(composeView.root)
    private val transparentBackdrop = parentWindow?.let {
        WinUITransparentBackdrop(
            window = it,
            enableWindowTransparentBackdrop = false,
        )
    }
    private var shouldBeOpen = false
    private var isOpen = false
    private var contentSize = IntSize.Zero
    private var popupPositionProvider: PopupPositionProvider? = null
    private var properties: PopupProperties by mutableStateOf(PopupProperties())
    private var parentBoundsInWindow: IntRect = IntRect.Zero
    private var windowSize: IntSize by mutableStateOf(IntSize.Zero)
    private var layoutDirection: androidx.compose.ui.unit.LayoutDirection by mutableStateOf(
        androidx.compose.ui.unit.LayoutDirection.Ltr
    )
    private var currentContent: @Composable () -> Unit by mutableStateOf({})
    private var closedToken: EventRegistrationToken? = null
    private var parentRootLoadedHandler: RoutedEventHandler? = null
    private var parentRootLoadedToken: EventRegistrationToken? = null

    init {
        composeView.setTransparentRootBackground()
        flyout.content = composeView.root
        flyout.areOpenCloseAnimationsEnabled = false
        flyout.lightDismissOverlayMode = LightDismissOverlayMode.Off
        flyout.shouldConstrainToRootBounds = false
        flyout.placement = FlyoutPlacementMode.BottomEdgeAlignedLeft
        flyout.showMode = FlyoutShowMode.Transient
        flyout.systemBackdrop = transparentBackdrop
        flyout.flyoutPresenterStyle = createTransparentFlyoutPresenterStyle()
        closedToken = flyout.closed.add { _, _ ->
            isOpen = false
        }
        registerParentRootLoadedHandler()
    }

    fun setContent(content: @Composable () -> Unit) {
        composeView.setContent(content)
    }

    @Composable
    fun Content() {
        val content = currentContent
        val parentWindowSize = windowSize
        Layout(
            content = content,
            modifier = Modifier
                .onPlaced { coordinates -> updateContentSize(coordinates.size) }
                .semantics { popup() },
        ) { measurables, constraints ->
            val windowSize = parentWindowSize.takeIf { it != IntSize.Zero }
                ?: constraints.finiteMaxSizeOr(IntSize.Zero)
            val looseConstraints = constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = constraints.finiteMaxWidthOr(windowSize.width),
                maxHeight = constraints.finiteMaxHeightOr(windowSize.height),
            )
            val placeables = measurables.map { measurable ->
                measurable.measure(looseConstraints)
            }
            val contentSize = IntSize(
                width = placeables.maxOfOrNull { it.width } ?: 0,
                height = placeables.maxOfOrNull { it.height } ?: 0,
            )
            layout(contentSize.width, contentSize.height) {
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, 0)
                }
            }
        }
    }

    fun open() {
        shouldBeOpen = true
        updateFlyout()
    }

    fun close() {
        shouldBeOpen = false
        isOpen = false
        closedToken?.let { token ->
            runCatching { flyout.closed.remove(token) }
        }
        closedToken = null
        parentRootLoadedToken?.let { token ->
            runCatching { parentRoot?.loaded?.remove(token) }
        }
        parentRootLoadedToken = null
        parentRootLoadedHandler = null
        runCatching { flyout.hide() }
        flyout.popupContent = null
        flyout.systemBackdrop = null
        composeView.dispose()
    }

    fun update(
        popupPositionProvider: PopupPositionProvider,
        properties: PopupProperties,
        parentBoundsInWindow: IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        content: @Composable () -> Unit,
    ) {
        this.popupPositionProvider = popupPositionProvider
        this.properties = properties
        this.parentBoundsInWindow = parentBoundsInWindow
        this.windowSize = windowSize
        this.layoutDirection = layoutDirection
        this.currentContent = content
        flyout.allowFocusOnInteraction = properties.focusable
        if (windowSize != IntSize.Zero) {
            composeView.setWindowContainerSize(windowSize)
            composeView.rootFrameworkElement.width = windowSize.width.toDouble()
            composeView.rootFrameworkElement.height = windowSize.height.toDouble()
        }
        updateFlyout()
    }

    fun updateContentSize(size: IntSize) {
        if (contentSize == size) return
        contentSize = size
        updateFlyout()
    }

    private fun registerParentRootLoadedHandler() {
        if (parentRootLoadedToken != null) return
        val root = parentRoot ?: return
        val handler: RoutedEventHandler = { _, _ ->
            updateFlyout()
        }
        parentRootLoadedHandler = handler
        parentRootLoadedToken = root.loaded.add(handler)
    }

    private fun updateFlyout() {
        val root = parentRoot ?: return
        registerParentRootLoadedHandler()
        val rootXamlRoot = runCatching { root.xamlRoot }.getOrNull()
        val rootLoaded = runCatching { root.isLoaded }.getOrDefault(false)
        if (!rootLoaded || rootXamlRoot == null) return
        val provider = popupPositionProvider ?: return
        val effectiveWindowSize = windowSize.takeIf { it != IntSize.Zero }
            ?: contentSize
        if (effectiveWindowSize == IntSize.Zero) return
        val hasMeasuredContent = contentSize != IntSize.Zero
        val effectiveContentSize = contentSize.takeIf { it != IntSize.Zero }
            ?: IntSize(1, 1)
        val popupPosition = provider.calculatePosition(
            anchorBounds = parentBoundsInWindow,
            windowSize = effectiveWindowSize,
            layoutDirection = layoutDirection,
            popupContentSize = effectiveContentSize,
        ).let { position ->
            if (properties.clippingEnabled) {
                position.clipToWindow(effectiveContentSize, effectiveWindowSize)
            } else {
                position
            }
        }
        val hostSize = if (hasMeasuredContent) {
            effectiveContentSize
        } else {
            effectiveWindowSize
        }
        composeView.rootFrameworkElement.width = hostSize.width.coerceAtLeast(1).toDouble()
        composeView.rootFrameworkElement.height = hostSize.height.coerceAtLeast(1).toDouble()
        if (shouldBeOpen && !isOpen) {
            isOpen = true
            flyout.xamlRoot = rootXamlRoot
            val showOptions = FlyoutShowOptions().also { options ->
                options.position = Point(popupPosition.x.toFloat(), popupPosition.y.toFloat())
                options.placement = FlyoutPlacementMode.BottomEdgeAlignedLeft
                options.showMode = FlyoutShowMode.Transient
            }
            runCatching {
                flyout.showAt(root, showOptions)
            }.onFailure {
                isOpen = false
            }
        }
    }
}

internal class TransparentComposeFlyout(
    popupContent: microsoft.ui.xaml.UIElement,
) : Flyout() {
    var popupContent: microsoft.ui.xaml.UIElement? = popupContent

    override fun createPresenter(): Control {
        return FlyoutPresenter().also { presenter ->
            presenter.isDefaultShadowEnabled = false
            presenter.useSystemFocusVisuals = false
            presenter.minWidth = 0.0
            presenter.minHeight = 0.0
            presenter.padding = Thickness(0.0, 0.0, 0.0, 0.0)
            presenter.cornerRadius = CornerRadius(0.0, 0.0, 0.0, 0.0)
            presenter.background = TransparentBrush()
            presenter.borderBrush = TransparentBrush()
            presenter.borderThickness = Thickness(0.0, 0.0, 0.0, 0.0)
            presenter.content = popupContent
        }
    }
}

private fun createTransparentFlyoutPresenterStyle(): Style =
    Style().also { style ->
        style.basedOn = runCatching {
            XamlApplication.current?.resources
                ?.get("DefaultFlyoutPresenterStyle")
                ?.asWinRT<Style>()
        }.getOrNull()
        style.setters.add(Setter(Control.backgroundProperty, TransparentBrush()))
        style.setters.add(Setter(Control.borderBrushProperty, TransparentBrush()))
        style.setters.add(Setter(Control.borderThicknessProperty, Thickness(0.0, 0.0, 0.0, 0.0)))
        style.setters.add(Setter(Control.paddingProperty, Thickness(0.0, 0.0, 0.0, 0.0)))
        style.setters.add(Setter(Control.cornerRadiusProperty, CornerRadius(0.0, 0.0, 0.0, 0.0)))
        style.setters.add(Setter(FrameworkElement.minWidthProperty, 0.0))
        style.setters.add(Setter(FrameworkElement.minHeightProperty, 0.0))
        style.setters.add(Setter(UIElement.useSystemFocusVisualsProperty, false))
    }

private fun Constraints.finiteMaxSizeOr(fallback: IntSize): IntSize =
    IntSize(
        width = if (hasBoundedWidth) maxWidth else fallback.width,
        height = if (hasBoundedHeight) maxHeight else fallback.height,
    )

private fun Constraints.finiteMaxWidthOr(fallback: Int): Int =
    if (hasBoundedWidth) maxWidth else fallback.takeIf { it > 0 } ?: Constraints.Infinity

private fun Constraints.finiteMaxHeightOr(fallback: Int): Int =
    if (hasBoundedHeight) maxHeight else fallback.takeIf { it > 0 } ?: Constraints.Infinity

private fun IntOffset.clipToWindow(contentSize: IntSize, windowSize: IntSize): IntOffset =
    IntOffset(
        x = if (contentSize.width < windowSize.width) {
            x.coerceIn(0, windowSize.width - contentSize.width)
        } else {
            0
        },
        y = if (contentSize.height < windowSize.height) {
            y.coerceIn(0, windowSize.height - contentSize.height)
        } else {
            0
        },
    )

private fun TransparentBrush(): SolidColorBrush =
    SolidColorBrush().also { brush ->
        brush.color = Color(a = 0u, r = 0u, g = 0u, b = 0u)
    }
