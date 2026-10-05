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

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue
import microsoft.ui.dispatching.DispatcherQueue

class WinUIDispatcherTest {
    @Test
    fun dispatchIsRequiredOnTheOwnerThread() {
        WinUITestRuntime.ensureInitialized()
        val dispatcherQueue = checkNotNull(DispatcherQueue.getForCurrentThread())
        val dispatcher = WinUIDispatcher(dispatcherQueue)

        assertTrue(
            dispatcher.isDispatchNeeded(EmptyCoroutineContext),
            "WinUI coroutine continuations must not run inline across frame phases.",
        )
    }
}
