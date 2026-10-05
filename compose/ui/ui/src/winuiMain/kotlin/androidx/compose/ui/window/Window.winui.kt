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
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Stable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.WinUIComposeView
import androidx.compose.ui.platform.debugRender
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import kotlin.math.roundToInt
import windows.foundation.EventRegistrationToken
import microsoft.ui.composition.Compositor
import microsoft.ui.dispatching.DispatcherQueue
import microsoft.ui.windowing.AppWindow
import microsoft.ui.windowing.AppWindowChangedEventArgs
import microsoft.ui.windowing.AppWindowClosingEventArgs
import microsoft.ui.windowing.AppWindowPresenterKind
import microsoft.ui.windowing.DisplayArea
import microsoft.ui.windowing.DisplayAreaFallback
import microsoft.ui.windowing.OverlappedPresenter
import microsoft.ui.windowing.OverlappedPresenterState
import windows.graphics.PointInt32
import windows.graphics.SizeInt32
import microsoft.ui.xaml.WindowActivatedEventArgs
import microsoft.ui.xaml.WindowActivationState
import microsoft.ui.xaml.media.DesktopAcrylicBackdrop
import microsoft.ui.xaml.media.MicaBackdrop
import microsoft.ui.xaml.media.SystemBackdrop
import windows.foundation.TypedEventHandler
import microsoft.ui.xaml.Window as XamlWindow

sealed class WindowBackdrop {
    internal abstract fun createSystemBackdrop(): SystemBackdrop?

    object None : WindowBackdrop() {
        override fun createSystemBackdrop(): SystemBackdrop? = null
    }

    object Mica : WindowBackdrop() {
        override fun createSystemBackdrop(): SystemBackdrop = MicaBackdrop()
    }

    object DesktopAcrylic : WindowBackdrop() {
        override fun createSystemBackdrop(): SystemBackdrop = DesktopAcrylicBackdrop()
    }

    class Custom(
        private val systemBackdrop: SystemBackdrop,
    ) : WindowBackdrop() {
        override fun createSystemBackdrop(): SystemBackdrop = systemBackdrop
    }
}

@Stable
interface WindowScope {
    val window: XamlWindow
    val appWindow: AppWindow
    val compositor: Compositor
    val dispatcherQueue: DispatcherQueue
}

@InternalComposeUiApi
val WindowScope.currentComposeViewForTest: WinUIComposeView?
    get() = (this as? WinUIWindowScopeTestAccess)?.composeViewForTest

private interface WinUIWindowScopeTestAccess {
    val composeViewForTest: WinUIComposeView?
}

/**
 * Composes a WinUI window.
 *
 * @param onCloseRequest called when the user closes the window.
 * @param state the state of the window: its size, position, placement and whether it is
 * minimized, as on desktop. The window applies changes of the state and writes back the changes
 * the user makes, such as moving, resizing or maximizing it. [WindowState.size] is the outer size
 * of the window; an unspecified width or height keeps the size Windows gives the window.
 */
@Composable
fun ApplicationScope.Window(
    onCloseRequest: () -> Unit = { exitApplication() },
    state: WindowState = rememberWindowState(),
    title: String = "Untitled",
    extendsContentIntoTitleBar: Boolean = false,
    backdrop: WindowBackdrop = WindowBackdrop.None,
    content: @Composable WindowScope.() -> Unit,
) {
    val applicationContext = this as? WinUIApplicationContext
    ComposeNode<WinUIWindowNode, WinUIApplicationApplier>(
        factory = {
            WinUIWindowNode(applicationContext)
        },
        update = {
            set(onCloseRequest) { this.onCloseRequest = it }
            set(state) { this.state = it }
            // Reading the state here recomposes the node when it changes.
            set(state.size) { applySize(it) }
            set(state.position) { applyPosition(it) }
            set(state.placement) { applyPlacement(it) }
            set(state.isMinimized) { applyMinimized(it) }
            set(title) { this.title = it }
            set(extendsContentIntoTitleBar) { this.extendsContentIntoTitleBar = it }
            set(backdrop) { this.backdrop = it }
            set(content) { this.content = it }
        },
    )
}

private class WinUIWindowNode(
    applicationContext: WinUIApplicationContext?,
) : WinUIApplicationNode(), WindowScope, WinUIWindowScopeTestAccess {
    override val window: XamlWindow = XamlWindow()
    override val appWindow: AppWindow
        get() = window.requiredAppWindow()
    override val compositor: Compositor
        get() = window.requiredCompositor()
    override val dispatcherQueue: DispatcherQueue = DispatcherQueue.getForCurrentThread()

    private var composeView: WinUIComposeView? = null
    private var hasActivated = false
    private var isWindowFocused = false
    private var isReleased = false
    private var isClosingFromRelease = false
    private var activatedHandler: TypedEventHandler<Any?, WindowActivatedEventArgs>? =
        { _, args -> handleActivated(args.windowActivationState) }
    private var activatedToken: EventRegistrationToken? =
        window.activated.add(requireNotNull(activatedHandler))
    private var closedHandler: TypedEventHandler<Any?, microsoft.ui.xaml.WindowEventArgs>? =
        { _, _ -> handleClosed() }
    private var closedToken: EventRegistrationToken? = window.closed.add(requireNotNull(closedHandler))
    private var appWindowChangedHandler: TypedEventHandler<AppWindow, AppWindowChangedEventArgs>? =
        { _, _ -> updateWindowInfo() }
    private var appWindowChangedToken: EventRegistrationToken? = null
    private var appWindowClosingHandler: TypedEventHandler<AppWindow, AppWindowClosingEventArgs>? =
        { _, args -> handleClosing(args) }
    private var appWindowClosingToken: EventRegistrationToken? = null
    private var isCaptureProtected = false
    override val composeViewForTest: WinUIComposeView?
        get() = composeView

    var onCloseRequest: () -> Unit = {}

    var state: WindowState = WindowState()

    // Our own presenter, so that it can be maximized, minimized and restored.
    private val overlappedPresenter: OverlappedPresenter = OverlappedPresenter.create()
    private var isUpdatingFromWindow = false

    init {
        applicationContext?.attachWindow(dispatcherQueue)
        appWindow.setPresenter(overlappedPresenter)
    }

    private val scale: Float
        get() = windowDpiScale(window).takeIf { it.isFinite() && it > 0f } ?: 1f

    fun applySize(size: DpSize) {
        if (isUpdatingFromWindow || isReleased) return
        val current = appWindow.size
        val scale = scale
        val width = if (size.width.isSpecified) (size.width.value * scale).roundToInt() else current.width
        val height = if (size.height.isSpecified) (size.height.value * scale).roundToInt() else current.height
        if (width != current.width || height != current.height) {
            appWindow.resize(SizeInt32(width.coerceAtLeast(1), height.coerceAtLeast(1)))
        }
    }

    fun applyPosition(position: WindowPosition) {
        if (isUpdatingFromWindow || isReleased) return
        val scale = scale
        when (position) {
            is WindowPosition.Absolute -> {
                val x = (position.x.value * scale).roundToInt()
                val y = (position.y.value * scale).roundToInt()
                val current = appWindow.position
                if (x != current.x || y != current.y) {
                    appWindow.move(PointInt32(x, y))
                }
            }
            is WindowPosition.Aligned -> {
                val workArea = DisplayArea.getFromWindowId(appWindow.id, DisplayAreaFallback.Nearest)
                    ?.workArea ?: return
                val size = appWindow.size
                val offset = position.alignment.align(
                    IntSize(size.width, size.height),
                    IntSize(workArea.width, workArea.height),
                    LayoutDirection.Ltr,
                )
                appWindow.move(PointInt32(workArea.x + offset.x, workArea.y + offset.y))
            }
            WindowPosition.PlatformDefault -> Unit
        }
    }

    fun applyPlacement(placement: WindowPlacement) {
        if (isUpdatingFromWindow || isReleased) return
        when (placement) {
            WindowPlacement.Floating -> {
                ensureOverlappedPresenter()
                if (overlappedPresenter.state == OverlappedPresenterState.Maximized) {
                    overlappedPresenter.restore()
                }
            }
            WindowPlacement.Maximized -> {
                ensureOverlappedPresenter()
                if (overlappedPresenter.state != OverlappedPresenterState.Maximized) {
                    overlappedPresenter.maximize()
                }
            }
            WindowPlacement.Fullscreen -> {
                if (appWindow.presenter?.kind != AppWindowPresenterKind.FullScreen) {
                    appWindow.setPresenter(AppWindowPresenterKind.FullScreen)
                }
            }
        }
    }

    fun applyMinimized(isMinimized: Boolean) {
        if (isUpdatingFromWindow || isReleased || !hasActivated) return
        val isWindowMinimized = isWindowMinimized(window)
        if (isMinimized && !isWindowMinimized) {
            ensureOverlappedPresenter()
            overlappedPresenter.minimize()
        } else if (!isMinimized && isWindowMinimized) {
            ensureOverlappedPresenter()
            overlappedPresenter.restore()
            applyPlacement(state.placement)
        }
    }

    private fun ensureOverlappedPresenter() {
        if (appWindow.presenter?.kind != AppWindowPresenterKind.Overlapped) {
            appWindow.setPresenter(overlappedPresenter)
        }
    }

    /**
     * Writes what the user did to the window back into [state], as desktop does: the position and
     * the size of a floating window, the placement, and whether the window is minimized.
     */
    private fun updateStateFromWindow() {
        if (isReleased || !hasActivated) return
        val state = state
        val scale = scale
        val isMinimized = isWindowMinimized(window)
        val placement = when {
            appWindow.presenter?.kind == AppWindowPresenterKind.FullScreen ->
                WindowPlacement.Fullscreen
            overlappedPresenter.state == OverlappedPresenterState.Maximized ->
                WindowPlacement.Maximized
            isMinimized -> state.placement
            else -> WindowPlacement.Floating
        }
        isUpdatingFromWindow = true
        try {
            state.isMinimized = isMinimized
            state.placement = placement
            if (!isMinimized && placement == WindowPlacement.Floating) {
                val position = appWindow.position
                state.position = WindowPosition((position.x / scale).dp, (position.y / scale).dp)
                val size = appWindow.size
                state.size = DpSize((size.width / scale).dp, (size.height / scale).dp)
            }
        } finally {
            isUpdatingFromWindow = false
        }
    }

    var title: String
        get() = window.title
        set(value) {
            window.title = value
            appWindow.title = value
        }

    var extendsContentIntoTitleBar: Boolean
        get() = window.extendsContentIntoTitleBar
        set(value) {
            window.extendsContentIntoTitleBar = value
            updateWindowInfo()
        }

    var backdrop: WindowBackdrop = WindowBackdrop.None
        set(value) {
            field = value
            val systemBackdrop = value.createSystemBackdrop()
            if (systemBackdrop != null) {
                setWindowSystemBackdrop(window, systemBackdrop)
            } else {
                clearWindowSystemBackdrop(window)
            }
            composeView?.setWindowBackground(isOpaque = value == WindowBackdrop.None)
        }

    var content: @Composable WindowScope.() -> Unit = {}
        set(value) {
            field = value
            if (isReleased) return
            val isFirstActivation = !hasActivated
            val view = composeView ?: WinUIComposeView(window, ::updateCaptureProtection).also {
                composeView = it
                it.setWindowBackground(isOpaque = backdrop == WindowBackdrop.None)
                setWindowContent(window, it.root)
                registerAppWindowChangedHandler()
                registerAppWindowClosingHandler()
            }
            if (!hasActivated) {
                window.activate()
                hasActivated = true
                updateWindowFocus(true)
            }
            view.setWindowFocused(isWindowFocused)
            updateWindowInfo()
            if (isFirstActivation) {
                // The window has its real position and size now, which an unspecified position
                // or size of the state takes, as on desktop.
                applyMinimized(state.isMinimized)
                updateStateFromWindow()
            }
            view.setContent {
                this@WinUIWindowNode.field()
            }
        }

    override fun onRelease() {
        if (!isReleased) {
            isReleased = true
            isClosingFromRelease = true
            disposeContent()
            removeAppWindowClosingHandler()
            removeAppWindowChangedHandler()
            removeActivatedHandler()
            removeClosedHandler()
            if (hasActivated) {
                runCatching { window.close() }
            }
        }
        super.onRelease()
    }

    private fun handleClosed() {
        if (isReleased) return
        isReleased = true
        disposeContent()
        removeAppWindowClosingHandler()
        removeAppWindowChangedHandler()
        removeActivatedHandler()
        removeClosedHandler()
        if (!isClosingFromRelease) {
            onCloseRequest()
        }
    }

    private fun handleClosing(args: AppWindowClosingEventArgs) {
        if (isReleased || isClosingFromRelease) return
        args.cancel = true
        onCloseRequest()
    }

    private fun disposeContent() {
        updateCaptureProtection(false)
        composeView?.dispose()
        composeView = null
    }

    private fun handleActivated(activationState: WindowActivationState) {
        if (isReleased) return
        hasActivated = true
        debugRender { "window activated state=$activationState" }
        updateWindowFocus(activationState != WindowActivationState.Deactivated)
    }

    private fun updateWindowFocus(isFocused: Boolean) {
        isWindowFocused = isFocused
        debugRender { "window focus focused=$isFocused" }
        composeView?.setWindowFocused(isFocused)
    }

    private fun updateWindowInfo() {
        updateStateFromWindow()
        val view = composeView ?: return
        // The content gets the client area, as on desktop: AppWindow.Size includes the title bar
        // and the resize borders.
        val clientSize = appWindow.clientSize
        debugRender {
            "window info clientSize=${clientSize.width}x${clientSize.height} " +
                "extendsTitleBar=${window.extendsContentIntoTitleBar}"
        }
        // A minimized window has an empty client area; keep the last size, as desktop does.
        if (clientSize.width > 0 && clientSize.height > 0) {
            view.setWindowContainerSize(
                IntSize(
                    width = clientSize.width,
                    height = clientSize.height,
                )
            )
        }
        view.setWindowMinimized(isWindowMinimized(window))
        view.invalidatePositionOnScreen()
        val titleBar = appWindow.titleBar
        if (window.extendsContentIntoTitleBar && titleBar != null) {
            view.setWindowTitleBarInsets(
                height = titleBar.height,
                leftPadding = titleBar.leftInset,
                rightPadding = titleBar.rightInset,
            )
        } else {
            view.setWindowTitleBarInsets(
                height = 0,
                leftPadding = 0,
                rightPadding = 0,
            )
        }
    }

    private fun updateCaptureProtection(isProtected: Boolean) {
        if (isCaptureProtected == isProtected) return
        if (setWindowCaptureProtection(window, isProtected)) {
            isCaptureProtected = isProtected
        }
    }

    private fun registerAppWindowChangedHandler() {
        if (appWindowChangedToken != null) return
        appWindowChangedToken = appWindow.changed.add(requireNotNull(appWindowChangedHandler))
    }

    private fun registerAppWindowClosingHandler() {
        if (appWindowClosingToken != null) return
        appWindowClosingToken = appWindow.closing.add(requireNotNull(appWindowClosingHandler))
    }

    private fun removeAppWindowClosingHandler() {
        val token = appWindowClosingToken ?: return
        appWindowClosingToken = null
        appWindowClosingHandler = null
        runCatching { appWindow.closing.remove(token) }
    }

    private fun removeAppWindowChangedHandler() {
        val token = appWindowChangedToken ?: return
        appWindowChangedToken = null
        appWindowChangedHandler = null
        runCatching { appWindow.changed.remove(token) }
    }

    private fun removeActivatedHandler() {
        val token = activatedToken ?: return
        activatedToken = null
        activatedHandler = null
        runCatching { window.activated.remove(token) }
    }

    private fun removeClosedHandler() {
        val token = closedToken ?: return
        closedToken = null
        closedHandler = null
        runCatching { window.closed.remove(token) }
    }
}

private fun XamlWindow.requiredCompositor(): Compositor =
    checkNotNull(compositor) {
        "Window.Compositor returned null."
    }

private fun XamlWindow.requiredAppWindow(): AppWindow =
    checkNotNull(appWindow) {
        "Window.AppWindow returned null."
    }

internal expect fun setWindowCaptureProtection(window: XamlWindow, isProtected: Boolean): Boolean

internal expect fun isWindowMinimized(window: XamlWindow): Boolean

/**
 * The scale of the monitor the window is on: its DPI divided by 96.
 */
internal expect fun windowDpiScale(window: XamlWindow): Float

private fun setWindowContent(window: XamlWindow, content: microsoft.ui.xaml.UIElement) {
    window.content = content
}

private fun setWindowSystemBackdrop(window: XamlWindow, systemBackdrop: SystemBackdrop) {
    window.systemBackdrop = systemBackdrop
}

private fun clearWindowSystemBackdrop(window: XamlWindow) {
    window.systemBackdrop = null
}
