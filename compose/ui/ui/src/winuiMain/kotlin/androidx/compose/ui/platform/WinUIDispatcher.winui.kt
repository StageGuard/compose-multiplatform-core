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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import microsoft.ui.dispatching.DispatcherQueue

internal class WinUIDispatcher(
    private val dispatcherQueue: DispatcherQueue,
) : CoroutineDispatcher() {
    private val dispatchQueue = WinUIDispatchQueue(dispatcherQueue)

    // Frame recomposition and layout are separate host phases. Always queue continuations so a
    // coroutine resumed during recomposition cannot re-enter and advance animation before layout.
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!dispatchQueue.dispatch { block.run() }) {
            block.run()
        }
    }
}
