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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ComposeFeatureFlags
import androidx.compose.ui.LayerType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWinUIRoot
import androidx.compose.ui.platform.LocalWinUIWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WinUIComposeView
import androidx.compose.ui.platform.toWinUIXamlPoint
import androidx.compose.ui.platform.toWinUIXamlSize
import androidx.compose.ui.platform.winUIPositionToComposeOffset
import androidx.compose.ui.semantics.popup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
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
import microsoft.ui.xaml.input.KeyEventHandler
import microsoft.ui.xaml.input.PointerEventHandler
import microsoft.ui.xaml.media.SolidColorBrush
import microsoft.ui.xaml.Application as XamlApplication
import windows.system.VirtualKey
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
        onPreviewKeyEvent = null,
        onKeyEvent = null,
        content = content,
    )
}

@Composable
actual fun Popup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)?,
    properties: PopupProperties,
    content: @Composable () -> Unit,
): Unit = Popup(
    popupPositionProvider = popupPositionProvider,
    onDismissRequest = onDismissRequest,
    properties = properties,
    onPreviewKeyEvent = null,
    onKeyEvent = null,
    content = content,
)

@Composable
fun Popup(
    alignment: Alignment = Alignment.TopStart,
    offset: IntOffset = IntOffset(0, 0),
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit,
) {
    val popupPositionProvider = remember(alignment, offset) {
        AlignmentOffsetPositionProvider(alignment, offset)
    }
    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = properties,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        content = content,
    )
}

@Composable
fun Popup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit,
) {
    val currentOnPreviewKeyEvent by rememberUpdatedState(onPreviewKeyEvent)
    val currentOnKeyEvent by rememberUpdatedState(onKeyEvent)
    if (properties.layerType == LayerType.OnWindow) {
        WinUIWindowPopupLayout(
            popupPositionProvider = popupPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = properties,
            onPreviewKeyEvent = currentOnPreviewKeyEvent,
            onKeyEvent = currentOnKeyEvent,
            content = content,
        )
    } else {
        WinUICanvasPopupLayout(
            popupPositionProvider = popupPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = properties,
            onPreviewKeyEvent = currentOnPreviewKeyEvent,
            onKeyEvent = currentOnKeyEvent,
            content = content,
        )
    }
}

@Composable
private fun WinUICanvasPopupLayout(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)?,
    properties: PopupProperties,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
    onKeyEvent: ((KeyEvent) -> Boolean)?,
    content: @Composable () -> Unit,
) {
    var parentBoundsInWindow by remember { mutableStateOf(IntRect.Zero) }
    var popupBoundsInRoot by remember { mutableStateOf(IntRect.Zero) }
    val root = LocalWinUIRoot.current?.let { it as? UIElement }
    val density = LocalDensity.current
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val dismissalHost = remember(root) { WinUICanvasPopupDismissHost(root) }

    SideEffect {
        dismissalHost.update(
            properties = properties,
            popupBoundsInRoot = popupBoundsInRoot,
            density = density,
            onDismissRequest = currentOnDismissRequest,
        )
    }
    DisposableEffect(dismissalHost) {
        dismissalHost.start()
        onDispose { dismissalHost.dispose() }
    }

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
        modifier = Modifier
            .onPlaced { coordinates ->
                popupBoundsInRoot = IntRect(
                    offset = coordinates.positionInRoot().round(),
                    size = coordinates.size,
                )
            }
            .semantics { popup() }
            .popupKeyEventHandlers(onPreviewKeyEvent, onKeyEvent),
    ) { measurables, constraints ->
        val windowSize = containerSize.takeIf { it != IntSize.Zero }
            ?: constraints.finiteMaxSizeOr(IntSize.Zero)
        val availableWidth = constraints.finiteMaxWidthOr(windowSize.width)
        val looseConstraints = constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxWidth = winUIPopupMaxWidth(
                windowWidth = windowSize.width,
                availableWidth = availableWidth,
                usePlatformDefaultWidth = properties.usePlatformDefaultWidth,
            ),
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
    onDismissRequest: (() -> Unit)?,
    properties: PopupProperties,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
    onKeyEvent: ((KeyEvent) -> Boolean)?,
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
    val parentIsActive = LocalWindowInfo.current.isWindowFocused
    val layoutDirection = LocalLayoutDirection.current
    val rasterizationScale = LocalDensity.current.density
    val currentContent by rememberUpdatedState(content)
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val parentCompositionContext = rememberCompositionContext()
    val popupHost = rememberNativePopupHost(parentWindow, parentRoot)

    SideEffect {
        popupHost.update(
            popupPositionProvider = popupPositionProvider,
            properties = properties,
            parentBoundsInWindow = parentBoundsInWindow.value,
            windowSize = containerSize,
            parentIsActive = parentIsActive,
            layoutDirection = layoutDirection,
            rasterizationScale = rasterizationScale,
            onDismissRequest = currentOnDismissRequest,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
            content = currentContent,
        )
    }
    DisposableEffect(popupHost) {
        popupHost.setContent(parentCompositionContext) {
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
    private val composeView = parentWindow?.let { window ->
        WinUIComposeView(
            window = window,
            onSensitiveContentChanged = {},
            observeWindowActivation = false,
        )
    } ?: WinUIComposeView()
    private val flyout = TransparentComposeFlyout(composeView.root)
    private val transparentBackdrop = parentWindow?.let {
        WinUITransparentBackdrop(
            window = it,
            enableWindowTransparentBackdrop = false,
        )
    }
    private var shouldBeOpen = false
    private var isOpen = false
    private var parentIsActive = false
    private var contentSize = IntSize.Zero
    private var popupPositionProvider: PopupPositionProvider? = null
    private var properties: PopupProperties by mutableStateOf(PopupProperties())
    private var parentBoundsInWindow: IntRect = IntRect.Zero
    private var windowSize: IntSize by mutableStateOf(IntSize.Zero)
    private var layoutDirection: androidx.compose.ui.unit.LayoutDirection by mutableStateOf(
        androidx.compose.ui.unit.LayoutDirection.Ltr
    )
    private var rasterizationScale: Float = 1f
    private var currentContent: @Composable () -> Unit by mutableStateOf({})
    private var onDismissRequest: (() -> Unit)? = null
    private var onPreviewKeyEvent: ((KeyEvent) -> Boolean)? by mutableStateOf(null)
    private var onKeyEvent: ((KeyEvent) -> Boolean)? by mutableStateOf(null)
    private val dismissalState = WinUIPopupDismissState(
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        onDismissRequest = { onDismissRequest?.invoke() },
    )
    private var closingToken: EventRegistrationToken? = null
    private var closedToken: EventRegistrationToken? = null
    private var keyDownToken: EventRegistrationToken? = null
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
        closingToken = flyout.closing.add { _, args ->
            args.cancel = dismissalState.onNativeClosing(shouldBeOpen)
        }
        closedToken = flyout.closed.add { _, _ ->
            isOpen = false
            updateHostActivation()
        }
        keyDownToken = composeView.root.previewKeyDown.add(KeyEventHandler { _, args ->
            if (!args.handled && properties.focusable && isOpen && args.key.isPopupBackKey()) {
                args.handled = dismissalState.onFlyoutBackKey()
            }
        })
        registerParentRootLoadedHandler()
    }

    fun setContent(
        parentCompositionContext: CompositionContext,
        content: @Composable () -> Unit,
    ) {
        composeView.setContent(parentCompositionContext, content)
    }

    @Composable
    fun Content() {
        val content = currentContent
        val parentWindowSize = windowSize
        Layout(
            content = content,
            modifier = Modifier
                .onPlaced { coordinates -> updateContentSize(coordinates.size) }
                .semantics { popup() }
                .popupKeyEventHandlers(onPreviewKeyEvent, onKeyEvent),
        ) { measurables, constraints ->
            val windowSize = parentWindowSize.takeIf { it != IntSize.Zero }
                ?: constraints.finiteMaxSizeOr(IntSize.Zero)
            val availableWidth = constraints.finiteMaxWidthOr(windowSize.width)
            val looseConstraints = constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = winUIPopupMaxWidth(
                    windowWidth = windowSize.width,
                    availableWidth = availableWidth,
                    usePlatformDefaultWidth = properties.usePlatformDefaultWidth,
                ),
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
        updateHostActivation()
        closingToken?.let { token ->
            runCatching { flyout.closing.remove(token) }
        }
        closingToken = null
        closedToken?.let { token ->
            runCatching { flyout.closed.remove(token) }
        }
        closedToken = null
        parentRootLoadedToken?.let { token ->
            runCatching { parentRoot?.loaded?.remove(token) }
        }
        parentRootLoadedToken = null
        parentRootLoadedHandler = null
        keyDownToken?.let { token ->
            runCatching { composeView.root.previewKeyDown.remove(token) }
        }
        keyDownToken = null
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
        parentIsActive: Boolean,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        rasterizationScale: Float,
        onDismissRequest: (() -> Unit)?,
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?,
        content: @Composable () -> Unit,
    ) {
        this.popupPositionProvider = popupPositionProvider
        this.properties = properties
        this.parentBoundsInWindow = parentBoundsInWindow
        this.windowSize = windowSize
        this.parentIsActive = parentIsActive
        this.layoutDirection = layoutDirection
        this.rasterizationScale = rasterizationScale
        this.onDismissRequest = onDismissRequest
        this.onPreviewKeyEvent = onPreviewKeyEvent
        this.onKeyEvent = onKeyEvent
        this.currentContent = content
        flyout.allowFocusOnInteraction = properties.focusable
        flyout.lightDismissOverlayMode = if (properties.dismissOnClickOutside) {
            LightDismissOverlayMode.On
        } else {
            LightDismissOverlayMode.Off
        }
        dismissalState.update(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            onDismissRequest = onDismissRequest,
        )
        updateHostActivation()
        if (windowSize != IntSize.Zero) {
            composeView.setWindowContainerSize(windowSize)
            val xamlSize = windowSize.toWinUIXamlSize(rasterizationScale)
            composeView.rootFrameworkElement.width = xamlSize.width
            composeView.rootFrameworkElement.height = xamlSize.height
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
        val xamlHostSize = IntSize(
            width = hostSize.width.coerceAtLeast(1),
            height = hostSize.height.coerceAtLeast(1),
        ).toWinUIXamlSize(rasterizationScale)
        composeView.rootFrameworkElement.width = xamlHostSize.width
        composeView.rootFrameworkElement.height = xamlHostSize.height
        if (shouldBeOpen && !isOpen) {
            isOpen = true
            flyout.xamlRoot = rootXamlRoot
            val showOptions = FlyoutShowOptions().also { options ->
                options.position = popupPosition.toWinUIXamlPoint(rasterizationScale)
                options.placement = FlyoutPlacementMode.BottomEdgeAlignedLeft
                options.showMode = FlyoutShowMode.Transient
            }
            runCatching {
                flyout.showAt(root, showOptions)
            }.onFailure {
                isOpen = false
            }
            updateHostActivation()
        }
    }

    private fun updateHostActivation() {
        composeView.setHostActive(parentIsActive && isOpen)
    }
}

internal class WinUIPopupDismissState(
    dismissOnBackPress: Boolean,
    dismissOnClickOutside: Boolean,
    onDismissRequest: (() -> Unit)?,
) {
    private var dismissOnBackPress = dismissOnBackPress
    private var dismissOnClickOutside = dismissOnClickOutside
    private var onDismissRequest = onDismissRequest

    fun update(
        dismissOnBackPress: Boolean = this.dismissOnBackPress,
        dismissOnClickOutside: Boolean = this.dismissOnClickOutside,
        onDismissRequest: (() -> Unit)? = this.onDismissRequest,
    ) {
        this.dismissOnBackPress = dismissOnBackPress
        this.dismissOnClickOutside = dismissOnClickOutside
        this.onDismissRequest = onDismissRequest
    }

    fun onBackPress(): Boolean {
        if (!dismissOnBackPress) return false
        return dispatchDismiss()
    }

    fun onOutsidePointer(position: Offset, popupBoundsInRoot: IntRect): Boolean {
        if (!dismissOnClickOutside || popupBoundsInRoot.isEmpty) return false
        val inside = position.x >= popupBoundsInRoot.left &&
            position.x < popupBoundsInRoot.right &&
            position.y >= popupBoundsInRoot.top &&
            position.y < popupBoundsInRoot.bottom
        if (inside) return false
        return dispatchDismiss()
    }

    fun onOutside(): Boolean {
        if (!dismissOnClickOutside) return false
        return dispatchDismiss()
    }

    fun onFlyoutBackKey(): Boolean {
        onBackPress()
        return true
    }

    fun onNativeClosing(shouldBeOpen: Boolean): Boolean {
        if (!shouldBeOpen) return false
        onOutside()
        return true
    }

    private fun dispatchDismiss(): Boolean {
        val callback = onDismissRequest ?: return false
        callback()
        return true
    }
}

internal fun winUIPopupMaxWidth(
    windowWidth: Int,
    availableWidth: Int,
    usePlatformDefaultWidth: Boolean,
): Int =
    winUIDialogMaxWidth(
        windowWidth = windowWidth,
        availableWidth = availableWidth,
        usePlatformDefaultWidth = usePlatformDefaultWidth,
    )

private class WinUICanvasPopupDismissHost(
    private val root: UIElement?,
) {
    private var properties = PopupProperties()
    private var popupBoundsInRoot = IntRect.Zero
    private var density = Density(1f)
    private val dismissalState = WinUIPopupDismissState(
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        onDismissRequest = null,
    )
    private var keyDownToken: EventRegistrationToken? = null
    private var pointerPressedToken: EventRegistrationToken? = null

    fun update(
        properties: PopupProperties,
        popupBoundsInRoot: IntRect,
        density: Density,
        onDismissRequest: (() -> Unit)?,
    ) {
        this.properties = properties
        this.popupBoundsInRoot = popupBoundsInRoot
        this.density = density
        dismissalState.update(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            onDismissRequest = onDismissRequest,
        )
    }

    fun start() {
        val root = root ?: return
        if (keyDownToken == null) {
            keyDownToken = root.keyDown.add(KeyEventHandler { _, args ->
                if (!args.handled && properties.focusable && args.key.isPopupBackKey()) {
                    if (dismissalState.onBackPress()) args.handled = true
                }
            })
        }
        if (pointerPressedToken == null) {
            pointerPressedToken = root.pointerPressed.add(PointerEventHandler { _, args ->
                if (!args.handled) {
                    val point = args.getCurrentPoint(root).position
                    val dismissed = dismissalState.onOutsidePointer(
                        winUICanvasPopupPointerPositionInRoot(
                            Offset(point.x, point.y),
                            density,
                        ),
                        popupBoundsInRoot,
                    )
                    if (dismissed && properties.focusable) args.handled = true
                }
            })
        }
    }

    fun dispose() {
        root?.let { root ->
            keyDownToken?.let { token -> runCatching { root.keyDown.remove(token) } }
            pointerPressedToken?.let { token ->
                runCatching { root.pointerPressed.remove(token) }
            }
        }
        keyDownToken = null
        pointerPressedToken = null
    }
}

internal fun winUICanvasPopupPointerPositionInRoot(
    pointInXamlDips: Offset,
    density: Density,
): Offset = winUIPositionToComposeOffset(
    pointInXamlDips.x,
    pointInXamlDips.y,
    density,
)

private fun VirtualKey.isPopupBackKey(): Boolean =
    this == VirtualKey.Escape || this == VirtualKey.GoBack || this == VirtualKey.NavigationCancel

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

private fun Modifier.popupKeyEventHandlers(
    onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
    onKeyEvent: ((KeyEvent) -> Boolean)?,
): Modifier {
    var result = this
    if (onPreviewKeyEvent != null) result = result.onPreviewKeyEvent(onPreviewKeyEvent)
    if (onKeyEvent != null) result = result.onKeyEvent(onKeyEvent)
    return result
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
