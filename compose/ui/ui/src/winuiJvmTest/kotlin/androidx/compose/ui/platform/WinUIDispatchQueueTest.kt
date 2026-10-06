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

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import microsoft.ui.dispatching.DispatcherQueueController

class WinUIDispatchQueueTest {
    /**
     * `Dispatchers.Main` dispatches from any thread: concurrent dispatches must neither lose blocks
     * nor reorder the blocks of one thread.
     */
    @Test
    fun dispatchFromManyThreadsRunsEveryBlockOnceAndInOrder() {
        WinUITestRuntime.ensureInitialized()
        val controller = DispatcherQueueController.createOnDedicatedThread()
        try {
            val queue = WinUIDispatchQueue(checkNotNull(controller.dispatcherQueue))
            val producers = 8
            val blocksPerProducer = 2_000
            val done = CountDownLatch(producers * blocksPerProducer)
            val start = CountDownLatch(1)
            // Written by the blocks, which all run on the dedicated thread of the queue.
            val lastRun = IntArray(producers) { -1 }
            val outOfOrder = AtomicInteger()
            val rejected = AtomicInteger()
            val threads = List(producers) { producer ->
                thread(name = "dispatch-producer-$producer") {
                    start.await()
                    repeat(blocksPerProducer) { index ->
                        val accepted = queue.dispatch {
                            if (lastRun[producer] != index - 1) outOfOrder.incrementAndGet()
                            lastRun[producer] = index
                            done.countDown()
                        }
                        if (!accepted) rejected.incrementAndGet()
                    }
                }
            }
            start.countDown()
            threads.forEach { it.join() }

            assertTrue(done.await(30, TimeUnit.SECONDS), "${done.count} dispatched blocks never ran.")
            assertEquals(0, rejected.get(), "The queue rejected dispatched blocks.")
            assertEquals(0, outOfOrder.get(), "Blocks of one thread ran out of order.")
            queue.close()
        } finally {
            controller.shutdownQueueAsync()
        }
    }
}
