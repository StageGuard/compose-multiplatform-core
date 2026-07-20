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

package androidx.compose.ui.platform

import androidx.lifecycle.Lifecycle
import microsoft.ui.xaml.DependencyPropertyChangedCallback
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.Visibility
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.WindowActivatedEventArgs
import microsoft.ui.xaml.WindowActivationState
import windows.foundation.TypedEventHandler

internal fun calculateWinUIViewLifecycleState(
    isLoaded: Boolean,
    isVisible: Boolean,
    isActive: Boolean,
    isDisposed: Boolean,
): Lifecycle.State = when {
    isDisposed -> Lifecycle.State.DESTROYED
    !isLoaded || !isVisible -> Lifecycle.State.CREATED
    !isActive -> Lifecycle.State.STARTED
    else -> Lifecycle.State.RESUMED
}

internal class WinUIViewLifecycleController(
    private val onStateChanged: (Lifecycle.State) -> Unit,
) {
    private var isLoaded = false
    private var isVisible = true
    private var isActive = false
    private var isDisposed = false
    private var lastState: Lifecycle.State? = null

    init {
        publishState()
    }

    fun setLoaded(value: Boolean) {
        if (!isDisposed) {
            isLoaded = value
            publishState()
        }
    }

    fun setVisible(value: Boolean) {
        if (!isDisposed) {
            isVisible = value
            publishState()
        }
    }

    fun setRootState(isLoaded: Boolean, isVisible: Boolean) {
        if (!isDisposed) {
            this.isLoaded = isLoaded
            this.isVisible = isVisible
            publishState()
        }
    }

    fun setActive(value: Boolean) {
        if (!isDisposed) {
            isActive = value
            publishState()
        }
    }

    fun dispose() {
        if (!isDisposed) {
            isDisposed = true
            publishState()
        }
    }

    private fun publishState() {
        val next = calculateWinUIViewLifecycleState(
            isLoaded = isLoaded,
            isVisible = isVisible,
            isActive = isActive,
            isDisposed = isDisposed,
        )
        if (next != lastState) {
            lastState = next
            onStateChanged(next)
        }
    }
}

internal data class WinUIViewLifecycleRootState(
    val isLoaded: Boolean,
    val isVisible: Boolean,
)

internal interface WinUIViewLifecycleSource {
    fun register(
        onLoadedChanged: (Boolean) -> Unit,
        onVisibilityChanged: (Boolean) -> Unit,
    ): List<() -> Unit>

    fun readRootState(): WinUIViewLifecycleRootState
}

internal class WinUIRootLifecycleBinding(
    private val source: WinUIViewLifecycleSource,
    private val controller: WinUIViewLifecycleController,
) : AutoCloseable {
    constructor(
        root: FrameworkElement,
        controller: WinUIViewLifecycleController,
    ) : this(FrameworkElementWinUIViewLifecycleSource(root), controller)

    private var isClosed = false
    private val unregisterActions = source.register(
        onLoadedChanged = { isLoaded ->
            if (!isClosed) controller.setLoaded(isLoaded)
        },
        onVisibilityChanged = { isVisible ->
            if (!isClosed) controller.setVisible(isVisible)
        },
    )

    init {
        val rootState = source.readRootState()
        controller.setRootState(
            isLoaded = rootState.isLoaded,
            isVisible = rootState.isVisible,
        )
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        unregisterActions.forEach { unregister ->
            runCatching { unregister() }
        }
        controller.dispose()
    }
}

internal interface WinUIWindowActivationSource {
    fun register(onActivated: (WindowActivationState) -> Unit): () -> Unit
}

internal class WinUIWindowActivationBinding(
    private val source: WinUIWindowActivationSource,
    private val onActiveChanged: (Boolean) -> Unit,
) : AutoCloseable {
    constructor(
        window: Window,
        onActiveChanged: (Boolean) -> Unit,
    ) : this(WindowWinUIViewActivationSource(window), onActiveChanged)

    private var isClosed = false
    private var unregisterAction: (() -> Unit)? = runCatching {
        source.register { activationState ->
            if (!isClosed) {
                onActiveChanged(activationState != WindowActivationState.Deactivated)
            }
        }
    }.getOrNull()

    override fun close() {
        if (isClosed) return
        isClosed = true
        val unregister = unregisterAction
        unregisterAction = null
        if (unregister != null) {
            runCatching { unregister() }
        }
    }
}

private class WindowWinUIViewActivationSource(
    private val window: Window,
) : WinUIWindowActivationSource {
    override fun register(onActivated: (WindowActivationState) -> Unit): () -> Unit {
        val handler: TypedEventHandler<Any?, WindowActivatedEventArgs> = { _, args ->
            onActivated(args.windowActivationState)
        }
        val token = window.activated.add(handler)
        return { window.activated.remove(token) }
    }
}

private class FrameworkElementWinUIViewLifecycleSource(
    private val root: FrameworkElement,
) : WinUIViewLifecycleSource {
    override fun register(
        onLoadedChanged: (Boolean) -> Unit,
        onVisibilityChanged: (Boolean) -> Unit,
    ): List<() -> Unit> {
        val loadedHandler: RoutedEventHandler = { _, _ -> onLoadedChanged(true) }
        val unloadedHandler: RoutedEventHandler = { _, _ -> onLoadedChanged(false) }
        val visibilityHandler = DependencyPropertyChangedCallback { _, _ ->
            runCatching { root.visibility == Visibility.Visible }
                .getOrNull()
                ?.let(onVisibilityChanged)
        }

        return buildList {
            runCatching { root.loaded.add(loadedHandler) }.getOrNull()?.let { token ->
                add { root.loaded.remove(token) }
            }
            runCatching { root.unloaded.add(unloadedHandler) }.getOrNull()?.let { token ->
                add { root.unloaded.remove(token) }
            }
            runCatching {
                root.registerPropertyChangedCallback(
                    UIElement.visibilityProperty,
                    visibilityHandler,
                )
            }.getOrNull()?.let { token ->
                add {
                    root.unregisterPropertyChangedCallback(UIElement.visibilityProperty, token)
                }
            }
        }
    }

    override fun readRootState(): WinUIViewLifecycleRootState = WinUIViewLifecycleRootState(
        isLoaded = runCatching { root.isLoaded }.getOrDefault(false),
        isVisible = runCatching { root.visibility == Visibility.Visible }.getOrDefault(true),
    )
}
