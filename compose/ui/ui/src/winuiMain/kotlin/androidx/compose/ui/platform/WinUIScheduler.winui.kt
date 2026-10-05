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

import kotlin.concurrent.Volatile
import microsoft.ui.dispatching.DispatcherQueue

internal object WinUIScheduler {
    @Volatile
    private var dispatcherQueue: DispatcherQueue? = null
    @Volatile
    private var dispatchQueue: WinUIDispatchQueue? = null

    fun register(dispatcherQueue: DispatcherQueue) {
        if (this.dispatchQueue == null) {
            this.dispatchQueue = WinUIDispatchQueue(dispatcherQueue)
            this.dispatcherQueue = dispatcherQueue
            installWinUIMainDispatcher()
        }
    }

    val isRegistered: Boolean
        get() = dispatchQueue != null

    /**
     * Whether the calling thread is the thread of the registered queue, `false` when no queue is
     * registered.
     */
    val hasThreadAccess: Boolean
        get() = dispatcherQueue?.let { queue ->
            runCatching { queue.hasThreadAccess }.getOrDefault(false)
        } ?: false

    fun dispatch(block: () -> Unit): Boolean {
        return dispatchQueue?.dispatch(block) == true
    }

    fun postDelayed(delayMillis: Long, block: () -> Unit): WinUIDelayedTask =
        requireDispatchQueue().postDelayed(delayMillis, block)

    private fun requireDispatchQueue(): WinUIDispatchQueue =
        dispatchQueue ?: WinUIDispatchQueue(DispatcherQueue.getForCurrentThread()).also {
            dispatchQueue = it
        }
}
