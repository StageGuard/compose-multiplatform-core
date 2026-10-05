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

import io.github.composefluent.winrt.runtime.WindowsAppSdkLauncherSupport
import microsoft.ui.dispatching.DispatcherQueueController

/**
 * Starts the Windows App SDK for the tests that need its objects, such as a dispatcher queue.
 *
 * The test task passes the self-contained runtime that `:compose:ui:ui:winui-samples` stages.
 */
internal object WinUITestRuntime {
    private const val RuntimeAssetsRootProperty = "compose.winui.test.runtimeAssetsRoot"
    private const val ShutdownGraceMillis = 3_000L

    /**
     * KWINRT-079: when the JVM exits, kotlin-winrt removes the event registrations that are
     * still alive from its shutdown hook. The scheduler keeps the tick registration of its timer
     * for the lifetime of the process, and removing it calls into the apartment of the test
     * thread, which is blocked in `System.exit` by then: the test worker would never exit.
     * The results are reported before the worker exits, so it is ended after a grace period.
     */
    private fun exitWhenShutdownHangs() {
        Runtime.getRuntime().addShutdownHook(
            Thread(
                {
                    Thread.sleep(ShutdownGraceMillis)
                    Runtime.getRuntime().halt(0)
                },
                "winui-test-exit",
            ).apply { isDaemon = true }
        )
    }

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

        val runtimeAssetsRoot = checkNotNull(System.getProperty(RuntimeAssetsRootProperty)) {
            "The WinUI tests need -D$RuntimeAssetsRootProperty; winuiJvmTest sets it."
        }
        val hostScope = WindowsAppSdkLauncherSupport.initializeApplicationHost(
            packageIdentity = "Unpackaged",
            deploymentMode = "SelfContained",
            runtimeAssetsRoot = runtimeAssetsRoot,
        )
        try {
            val controller = DispatcherQueueController.createOnCurrentThread()
            WinUIScheduler.register(checkNotNull(controller.dispatcherQueue))
            applicationHostScope = hostScope
            dispatcherQueueController = controller
            ownerThread = currentThread
            exitWhenShutdownHangs()
        } catch (throwable: Throwable) {
            runCatching { hostScope.close() }
            throw throwable
        }
    }
}
