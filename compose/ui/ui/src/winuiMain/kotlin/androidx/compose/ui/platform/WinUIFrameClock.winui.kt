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

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CancellationException
import microsoft.ui.dispatching.DispatcherQueue

internal class WinUIFrameClock(
    dispatcherQueue: DispatcherQueue,
    private val onFrame: () -> Unit = {},
) : MonotonicFrameClock {
    private val dispatchQueue = WinUIDispatchQueue(dispatcherQueue)
    private var isFrameScheduled = false
    private var isCancelled = false
    private val frameClock = BroadcastFrameClock(::scheduleFrame)

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
        frameClock.withFrameNanos(onFrame)

    fun cancel() {
        isCancelled = true
        dispatchQueue.close()
        frameClock.cancel(CancellationException("WinUIComposeView disposed"))
    }

    private fun scheduleFrame() {
        if (isCancelled || isFrameScheduled) return
        isFrameScheduled = true
        if (!dispatchQueue.dispatch {
                isFrameScheduled = false
                if (!isCancelled) {
                    frameClock.sendFrame(winUINanoTime())
                    onFrame()
                }
            }
        ) {
            isFrameScheduled = false
        }
    }
}
