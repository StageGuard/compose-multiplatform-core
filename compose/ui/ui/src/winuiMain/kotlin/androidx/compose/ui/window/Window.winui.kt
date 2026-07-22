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
import androidx.compose.ui.unit.IntSize
import windows.foundation.EventRegistrationToken
import microsoft.ui.composition.Compositor
import microsoft.ui.dispatching.DispatcherQueue
import microsoft.ui.windowing.AppWindow
import microsoft.ui.windowing.AppWindowChangedEventArgs
import microsoft.ui.windowing.AppWindowClosingEventArgs
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

@Composable
fun ApplicationScope.Window(
    onCloseRequest: () -> Unit = { exitApplication() },
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

    init {
        applicationContext?.attachWindow(dispatcherQueue)
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
        }

    var content: @Composable WindowScope.() -> Unit = {}
        set(value) {
            field = value
            if (isReleased) return
            val view = composeView ?: WinUIComposeView(window, ::updateCaptureProtection).also {
                composeView = it
                setWindowContent(window, it.root)
                registerAppWindowChangedHandler()
                registerAppWindowClosingHandler()
            }
            updateWindowInfo()
            view.setContentWhenWindowReady {
                this@WinUIWindowNode.field()
            }
            if (!hasActivated) {
                window.activate()
                hasActivated = true
                updateWindowFocus(true)
            }
            view.setWindowFocused(isWindowFocused)
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
        val view = composeView ?: return
        val appWindowSize = appWindow.size
        debugRender {
            "window info size=${appWindowSize.width}x${appWindowSize.height} " +
                "extendsTitleBar=${window.extendsContentIntoTitleBar}"
        }
        view.setWindowBootstrapSize(
            IntSize(
                width = appWindowSize.width,
                height = appWindowSize.height,
            ),
        )
        val titleBar = appWindow.titleBar
        if (window.extendsContentIntoTitleBar && titleBar != null) {
            view.setWindowTitleBarInsets(
                height = titleBar.height,
                leftInset = titleBar.leftInset,
                rightInset = titleBar.rightInset,
            )
        } else {
            view.setWindowTitleBarInsets(
                height = 0,
                leftInset = 0,
                rightInset = 0,
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

private fun setWindowContent(window: XamlWindow, content: microsoft.ui.xaml.UIElement) {
    window.content = content
}

private fun setWindowSystemBackdrop(window: XamlWindow, systemBackdrop: SystemBackdrop) {
    window.systemBackdrop = systemBackdrop
}

private fun clearWindowSystemBackdrop(window: XamlWindow) {
    window.systemBackdrop = null
}
