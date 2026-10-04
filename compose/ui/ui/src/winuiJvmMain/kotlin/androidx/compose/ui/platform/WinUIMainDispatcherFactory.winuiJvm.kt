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

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.internal.MainDispatcherFactory

/**
 * Provides `Dispatchers.Main` on the WinUI JVM target: the UI thread of the WinUI application.
 *
 * The desktop target gets `Dispatchers.Main` from kotlinx-coroutines-swing, which the WinUI target
 * does not use. Without one, `Dispatchers.Main` fails, and with it everything built on it, such as
 * `repeatOnLifecycle`, `collectAsStateWithLifecycle` and `viewModelScope`.
 *
 * Registered through `META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory`.
 */
@OptIn(InternalCoroutinesApi::class)
internal class WinUIMainDispatcherFactory : MainDispatcherFactory {
    override val loadPriority: Int
        get() = 1

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher =
        WinUIMainDispatcher(isImmediate = false)

    override fun hintOnError(): String =
        "Dispatchers.Main is available once a WinUI Application or WinUIComposeView has started."
}

/**
 * Dispatches to the dispatcher queue that [WinUIScheduler] has registered, which is the queue of
 * the thread that runs the WinUI application.
 */
internal class WinUIMainDispatcher(
    private val isImmediate: Boolean,
) : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher
        get() = if (isImmediate) this else immediateDispatcher

    private val immediateDispatcher by lazy { WinUIMainDispatcher(isImmediate = true) }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        !isImmediate || !WinUIScheduler.hasThreadAccess

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        check(WinUIScheduler.isRegistered) {
            "Dispatchers.Main is used before the WinUI application has started."
        }
        if (!WinUIScheduler.dispatch { block.run() }) {
            // The queue no longer takes work: the application is shutting down. Cancel the work, as
            // the Android main dispatcher does, and let it complete off the UI thread.
            context.cancel(
                CancellationException("The WinUI dispatcher queue was shut down, so $block was rejected.")
            )
            Dispatchers.IO.dispatch(context, block)
        }
    }

    override fun toString(): String =
        if (isImmediate) "Dispatchers.Main.immediate (WinUI)" else "Dispatchers.Main (WinUI)"
}
