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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel

/**
 * Makes [WinUIMainDispatcher] the `Dispatchers.Main` of the process, where the platform does not
 * find it by itself.
 */
internal expect fun installWinUIMainDispatcher()

/**
 * `Dispatchers.Main` of a WinUI application: the dispatcher queue that [WinUIScheduler] has.
 *
 * @param rejectedWorkDispatcher runs the cancelled work that the queue no longer takes
 */
internal class WinUIMainDispatcher(
    private val isImmediate: Boolean,
    private val rejectedWorkDispatcher: CoroutineDispatcher,
) : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher
        get() = if (isImmediate) this else immediateDispatcher

    private val immediateDispatcher by lazy {
        WinUIMainDispatcher(isImmediate = true, rejectedWorkDispatcher)
    }

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
            rejectedWorkDispatcher.dispatch(context, block)
        }
    }

    override fun toString(): String =
        if (isImmediate) "Dispatchers.Main.immediate (WinUI)" else "Dispatchers.Main (WinUI)"
}
