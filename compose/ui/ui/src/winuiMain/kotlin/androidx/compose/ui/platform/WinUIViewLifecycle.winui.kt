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
import windows.foundation.EventRegistrationToken

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

internal class WinUIRootLifecycleBinding(
    private val root: FrameworkElement,
    private val controller: WinUIViewLifecycleController,
) : AutoCloseable {
    private var isClosed = false

    private val loadedHandler: RoutedEventHandler = { _, _ -> controller.setLoaded(true) }
    private val unloadedHandler: RoutedEventHandler = { _, _ -> controller.setLoaded(false) }
    private val visibilityHandler = DependencyPropertyChangedCallback { _, _ ->
        runCatching { root.visibility == Visibility.Visible }
            .getOrNull()
            ?.let(controller::setVisible)
    }

    private val loadedToken: EventRegistrationToken? =
        runCatching { root.loaded.add(loadedHandler) }.getOrNull()
    private val unloadedToken: EventRegistrationToken? =
        runCatching { root.unloaded.add(unloadedHandler) }.getOrNull()
    private val visibilityToken: Long? = runCatching {
        root.registerPropertyChangedCallback(
            UIElement.visibilityProperty,
            visibilityHandler,
        )
    }.getOrNull()

    init {
        controller.setLoaded(runCatching { root.isLoaded }.getOrDefault(false))
        controller.setVisible(
            runCatching { root.visibility == Visibility.Visible }.getOrDefault(true),
        )
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        loadedToken?.let { token ->
            runCatching { root.loaded.remove(token) }
        }
        unloadedToken?.let { token ->
            runCatching { root.unloaded.remove(token) }
        }
        visibilityToken?.let { token ->
            runCatching {
                root.unregisterPropertyChangedCallback(UIElement.visibilityProperty, token)
            }
        }
        controller.dispose()
    }
}
