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

import io.github.composefluent.winrt.runtime.WinRTWindowsAppSdkBootstrap
import microsoft.ui.dispatching.DispatcherQueueController

internal object WinUITestRuntime {
    private var applicationHostScope: AutoCloseable? = null
    private var dispatcherQueueController: DispatcherQueueController? = null
    private var ownerThread: Thread? = null

    fun ensureInitialized() {
        val currentThread = Thread.currentThread()
        ownerThread?.let { initializedThread ->
            check(initializedThread === currentThread) {
                "The WinUI test runtime must be used from its initializing thread."
            }
            return
        }

        val hostScope = WinRTWindowsAppSdkBootstrap.initializeApplicationHost()
        try {
            val controller = DispatcherQueueController.createOnCurrentThread()
            WinUIScheduler.register(checkNotNull(controller.dispatcherQueue))
            applicationHostScope = hostScope
            dispatcherQueueController = controller
            ownerThread = currentThread
        } catch (throwable: Throwable) {
            runCatching { hostScope.close() }
            throw throwable
        }
    }
}
