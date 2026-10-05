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

package androidx.compose.ui.window

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNodeLifecycleCallback
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.GlobalSnapshotManager
import androidx.compose.ui.platform.WinUIDispatchQueue
import androidx.compose.ui.platform.WinUIDispatcher
import androidx.compose.ui.platform.WinUIFrameClock
import androidx.compose.ui.platform.WinUIScheduler
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import microsoft.ui.dispatching.DispatcherQueue
import microsoft.ui.xaml.DispatcherShutdownMode
import microsoft.ui.xaml.LaunchActivatedEventArgs
import microsoft.ui.xaml.controls.XamlControlsResources
import microsoft.ui.xaml.Application as XamlApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

fun Application(
    content: @Composable ApplicationScope.() -> Unit,
) {
    PendingWinUIApplicationContent.content = content
    try {
        XamlApplication.start {
            WinUIXamlApplication()
        }
    } finally {
        PendingWinUIApplicationContent.content = null
    }
}

class WinUIXamlApplication : XamlApplication() {
    private var runtime: WinUIApplicationRuntime? = null

    internal fun dispatchLaunch(args: LaunchActivatedEventArgs) {
        onLaunched(args)
    }

    override fun onLaunched(args: LaunchActivatedEventArgs) {
        dispatcherShutdownMode = DispatcherShutdownMode.OnExplicitShutdown
        installDefaultXamlResources()
        runtime = WinUIApplicationRuntime(
            application = this,
            dispatcherQueue = DispatcherQueue.getForCurrentThread(),
        ).also { runtime ->
            runtime.setContent(PendingWinUIApplicationContent.content ?: {})
        }
    }

    private fun installDefaultXamlResources() {
        resources.mergedDictionaries.add(XamlControlsResources())
    }
}

private object PendingWinUIApplicationContent {
    var content: (@Composable ApplicationScope.() -> Unit)? = null
}

@Stable
interface ApplicationScope {
    fun exitApplication()
}

internal interface WinUIApplicationContext : ApplicationScope {
    fun attachWindow(dispatcherQueue: DispatcherQueue)
}

private class WinUIApplicationRuntime(
    private val application: XamlApplication,
    private val dispatcherQueue: DispatcherQueue,
) : WinUIApplicationContext {
    private val frameClock = WinUIFrameClock(dispatcherQueue)
    private val dispatchQueue = WinUIDispatchQueue(dispatcherQueue)
    private val recomposerParentJob = SupervisorJob()
    private val recomposerContext =
        WinUIDispatcher(dispatcherQueue) + frameClock + recomposerParentJob
    private val recomposer = Recomposer(recomposerContext)

    // The shared manager pumps the apply notifications on the dispatcher queue.
    private val globalSnapshotRegistration =
        GlobalSnapshotManager.register(WinUIDispatcher(dispatcherQueue))
    private val root = WinUIApplicationNode()
    private val composition = Composition(
        // As on desktop, the application ends when its content has no window left.
        applier = WinUIApplicationApplier(root, onEndChanges = ::exitApplicationIfEmpty),
        parent = recomposer,
    )
    private val recomposerJob: Job

    private var isDisposeRequested = false
    private var isDisposed = false

    init {
        registerSkikoComposeImplementation()
        WinUIScheduler.register(dispatcherQueue)
        recomposerJob = CoroutineScope(recomposerContext).launch {
            recomposer.runRecomposeAndApplyChanges()
        }
    }

    fun setContent(content: @Composable ApplicationScope.() -> Unit) {
        composition.setContent {
            content()
        }
    }

    override fun exitApplication() {
        if (isDisposeRequested) return
        isDisposeRequested = true
        val enqueued = dispatchQueue.dispatch {
            dispose()
            application.exit()
        }
        if (enqueued) return
        dispose()
        application.exit()
    }

    private fun exitApplicationIfEmpty() {
        if (root.isEmpty) {
            exitApplication()
        }
    }

    override fun attachWindow(dispatcherQueue: DispatcherQueue) {
        check(this.dispatcherQueue == dispatcherQueue) {
            "WinUI windows must be created on the application DispatcherQueue."
        }
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        composition.dispose()
        recomposer.close()
        recomposerJob.cancel()
        recomposerParentJob.cancel()
        globalSnapshotRegistration?.close()
        frameClock.cancel()
        root.removeAll()
    }
}

internal open class WinUIApplicationNode : ComposeNodeLifecycleCallback {
    private val children = mutableListOf<WinUIApplicationNode>()

    val isEmpty: Boolean
        get() = children.isEmpty()

    open fun insertAt(index: Int, instance: WinUIApplicationNode) {
        children.add(index, instance)
    }

    open fun removeAt(index: Int, count: Int) {
        repeat(count) {
            children.removeAt(index).onRelease()
        }
    }

    open fun move(from: Int, to: Int, count: Int) {
        val moved = ArrayList<WinUIApplicationNode>(count)
        repeat(count) {
            moved += children.removeAt(from)
        }
        children.addAll(if (to > from) to - count else to, moved)
    }

    fun removeAll() {
        children.asReversed().forEach { it.onRelease() }
        children.clear()
    }

    override fun onReuse() = Unit

    override fun onDeactivate() = Unit

    override fun onRelease() {
        removeAll()
    }
}

internal class WinUIApplicationApplier(
    root: WinUIApplicationNode,
    private val onEndChanges: () -> Unit = {},
) : AbstractApplier<WinUIApplicationNode>(root) {
    override fun insertTopDown(index: Int, instance: WinUIApplicationNode) = Unit

    override fun insertBottomUp(index: Int, instance: WinUIApplicationNode) {
        current.insertAt(index, instance)
    }

    override fun remove(index: Int, count: Int) {
        current.removeAt(index, count)
    }

    override fun move(from: Int, to: Int, count: Int) {
        current.move(from, to, count)
    }

    override fun onClear() {
        root.removeAll()
    }

    override fun onEndChanges() {
        onEndChanges.invoke()
    }
}
