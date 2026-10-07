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

import windows.foundation.EventRegistrationToken
import kotlin.time.Duration.Companion.milliseconds
import microsoft.ui.dispatching.DispatcherQueue
import microsoft.ui.dispatching.DispatcherQueueHandler
import microsoft.ui.dispatching.DispatcherQueueTimer
import windows.foundation.TypedEventHandler

/**
 * Runs blocks on the thread of [dispatcherQueue], in order.
 *
 * [dispatch] is called from any thread (it backs `Dispatchers.Main`), so the pending blocks and the
 * scheduling state are guarded by [lock]. The blocks themselves run outside the lock.
 *
 * A drain is always a dispatcher queue item, also when [dispatch] is called on the thread of the
 * queue: the blocks leave the current WinUI callback, but run as soon as the message loop gets to
 * them. (A DispatcherQueueTimer, even with a 1 ms interval, fires on the next tick of the system
 * timer, 15.6 ms by default, which capped the frame loop of Compose at 64 Hz.)
 */
internal class WinUIDispatchQueue(
    private val dispatcherQueue: DispatcherQueue,
) {
    private val lock = makeSynchronizedObject()
    private val pending = ArrayDeque<() -> Unit>()
    private var isScheduled = false
    private var isDraining = false
    private var isClosed = false
    private val drainHandler = DispatcherQueueHandler {
        runCatching {
            drain()
        }.onFailure { throwable ->
            logDispatchFailure(throwable)
        }
    }

    fun dispatch(block: () -> Unit): Boolean {
        synchronized(lock) {
            if (isClosed) return false
            pending.addLast(block)
            if (isScheduled || isDraining) return true
            isScheduled = true
        }
        if (scheduleDrain()) return true
        val task = synchronized(lock) {
            pending.removeLastOrNull().also {
                if (pending.isEmpty()) {
                    isScheduled = false
                }
            }
        }
        if (task === block) {
            block()
        }
        return false
    }

    fun postDelayed(delayMillis: Long, block: () -> Unit): WinUIDelayedTask {
        val delayedTimer = dispatcherQueue.createTimer().also { timer ->
            timer.interval = delayMillis.coerceAtLeast(1L).milliseconds
            timer.isRepeating = false
        }
        lateinit var delayedTask: WinUIDelayedTask
        val handler: TypedEventHandler<DispatcherQueueTimer, Any?> = { _, _ ->
            delayedTask.cancel()
            runCatching(block).onFailure { throwable ->
                logDispatchFailure(throwable)
            }
        }
        val token = delayedTimer.tick.add(handler)
        delayedTask = WinUIDelayedTask(delayedTimer, token)
        try {
            delayedTimer.start()
        } catch (throwable: Throwable) {
            delayedTask.cancel()
            throw throwable
        }
        return delayedTask
    }

    private fun drain() {
        val tasks = synchronized(lock) {
            isScheduled = false
            isDraining = true
            buildList {
                while (pending.isNotEmpty()) {
                    add(pending.removeFirst())
                }
            }
        }
        try {
            tasks.forEach { task ->
                runCatching {
                    task()
                }.onFailure { throwable ->
                    logDispatchFailure(throwable)
                }
            }
        } finally {
            val shouldSchedule = synchronized(lock) {
                isDraining = false
                if (!isClosed && pending.isNotEmpty() && !isScheduled) {
                    isScheduled = true
                    true
                } else {
                    false
                }
            }
            if (shouldSchedule) {
                scheduleDrain()
            }
        }
    }

    private fun scheduleDrain(): Boolean =
        runCatching {
            dispatcherQueue.tryEnqueue(drainHandler)
        }.getOrElse { throwable ->
            logDispatchFailure(throwable)
            false
        }

    fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            isScheduled = false
            pending.clear()
        }
    }

    private fun logDispatchFailure(throwable: Throwable) {
        println("WinUIDispatchQueue task failed: ${throwable::class.qualifiedName}: ${throwable.message}")
        throwable.printStackTrace()
    }
}

internal class WinUIDelayedTask(
    private val timer: DispatcherQueueTimer,
    private val tickToken: EventRegistrationToken,
) {
    private var isCancelled = false

    fun cancel() {
        if (isCancelled) return
        isCancelled = true
        runCatching { timer.stop() }
        runCatching { timer.tick.remove(tickToken) }
    }
}
