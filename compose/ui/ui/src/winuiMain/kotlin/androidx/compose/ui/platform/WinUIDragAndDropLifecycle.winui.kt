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

import androidx.compose.ui.draganddrop.DragAndDropEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import windows.applicationmodel.datatransfer.DataPackageOperation

internal fun winUIDragAcceptedOperation(accepted: Boolean): DataPackageOperation =
    if (accepted) DataPackageOperation.Copy else DataPackageOperation.None

internal fun runWinUIDragAndDropCleanup(vararg cleanupActions: () -> Unit) {
    var failure: Throwable? = null
    cleanupActions.forEach { cleanupAction ->
        try {
            cleanupAction()
        } catch (throwable: Throwable) {
            if (failure == null) {
                failure = throwable
            } else if (failure !== throwable) {
                failure.addSuppressed(throwable)
            }
        }
    }
    failure?.let { throw it }
}

internal class WinUIDragSessionController(
    private val onStart: (DragAndDropEvent) -> Boolean,
    private val onEnd: (DragAndDropEvent) -> Unit,
    private val onClearTransfer: () -> Unit,
) {
    var isActive: Boolean = false
        private set

    fun ensureStarted(event: DragAndDropEvent): Boolean {
        if (isActive) return true
        isActive = onStart(event)
        return isActive
    }

    fun terminate(event: DragAndDropEvent) {
        val shouldEnd = isActive
        isActive = false
        try {
            if (shouldEnd) onEnd(event)
        } finally {
            onClearTransfer()
        }
    }
}

internal class WinUIDropTermination(
    private val finishDrop: () -> Boolean,
    private val abortDrop: () -> Unit,
    private val publishResult: (Boolean) -> Unit = {},
    private val completeDeferral: () -> Unit,
    private val onTerminated: () -> Unit,
) {
    private var isTerminated = false

    fun finish(onHandled: (Boolean) -> Unit = {}): Boolean? {
        if (isTerminated) return null
        isTerminated = true
        var handled = false
        var dropCompleted = false
        runWinUIDragAndDropCleanup(
            {
                handled = finishDrop()
                dropCompleted = true
            },
            { publishResult(dropCompleted && handled) },
            { onHandled(dropCompleted && handled) },
            completeDeferral,
            onTerminated,
        )
        return handled
    }

    fun abort() {
        if (isTerminated) return
        isTerminated = true
        runWinUIDragAndDropCleanup(
            abortDrop,
            { publishResult(false) },
            completeDeferral,
            onTerminated,
        )
    }
}

internal fun CoroutineScope.launchWinUIDropRead(
    loadPlainText: suspend () -> String?,
    termination: WinUIDropTermination,
    onHandled: (Boolean) -> Unit,
): Job {
    val job = launch {
        loadPlainText()
        termination.finish(onHandled)
    }
    job.invokeOnCompletion { termination.abort() }
    return job
}
