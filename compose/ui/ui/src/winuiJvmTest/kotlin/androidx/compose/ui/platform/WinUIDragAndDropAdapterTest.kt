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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import windows.applicationmodel.datatransfer.DataPackageOperation

@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class WinUIDragAndDropAdapterTest {
    @Test
    fun acceptedOperationAdvertisesCopyOnlyForAcceptedTargets() {
        assertEquals(DataPackageOperation.Copy, winUIDragAcceptedOperation(true))
        assertEquals(DataPackageOperation.None, winUIDragAcceptedOperation(false))
    }

    @Test
    fun dragSessionTerminationEndsAndClearsStateAfterCallbackFailure() {
        var endCalls = 0
        var clearCalls = 0
        val controller =
            WinUIDragSessionController(
                onStart = { true },
                onEnd = {
                    endCalls++
                    error("end failed")
                },
                onClearTransfer = { clearCalls++ },
            )

        assertTrue(controller.ensureStarted(DragAndDropEvent()))
        assertTrue(controller.isActive)
        assertFailsWith<IllegalStateException> { controller.terminate(DragAndDropEvent()) }

        assertFalse(controller.isActive)
        assertEquals(1, endCalls)
        assertEquals(1, clearCalls)
    }

    @Test
    fun dropTerminationFinishesDropAndDeferralExactlyOnce() {
        var dropCalls = 0
        var abortCalls = 0
        var deferralCalls = 0
        var terminatedCalls = 0
        val events = mutableListOf<String>()
        val termination =
            WinUIDropTermination(
                finishDrop = {
                    dropCalls++
                    events += "drop"
                    true
                },
                abortDrop = { abortCalls++ },
                publishResult = { handled -> events += "handled=$handled" },
                completeDeferral = {
                    deferralCalls++
                    events += "deferral"
                },
                onTerminated = {
                    terminatedCalls++
                    events += "terminated"
                },
            )

        assertEquals(true, termination.finish())
        assertNull(termination.finish())
        termination.abort()

        assertEquals(1, dropCalls)
        assertEquals(0, abortCalls)
        assertEquals(1, deferralCalls)
        assertEquals(1, terminatedCalls)
        assertEquals(listOf("drop", "handled=true", "deferral", "terminated"), events)
    }

    @Test
    fun dropTerminationPublishesRejectionAndCompletesDeferralWhenDropCallbackFails() {
        var deferralCalls = 0
        var terminatedCalls = 0
        val events = mutableListOf<String>()
        val termination =
            WinUIDropTermination(
                finishDrop = {
                    events += "drop"
                    error("drop failed")
                },
                abortDrop = {},
                publishResult = { handled -> events += "handled=$handled" },
                completeDeferral = {
                    deferralCalls++
                    events += "deferral"
                },
                onTerminated = {
                    terminatedCalls++
                    events += "terminated"
                },
            )

        val failure = assertFailsWith<IllegalStateException> { termination.finish() }

        assertEquals("drop failed", failure.message)
        assertEquals(1, deferralCalls)
        assertEquals(1, terminatedCalls)
        assertEquals(listOf("drop", "handled=false", "deferral", "terminated"), events)
    }

    @Test
    fun cancellationBeforeReadStartsAbortsAndCompletesDeferral() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        var readCalls = 0
        val counters = DropTerminationCounters()
        val termination = counters.createTermination()

        val job =
            scope.launchWinUIDropRead(
                loadPlainText = {
                    readCalls++
                    "text"
                },
                termination = termination,
                onHandled = {},
            )
        job.cancel()
        runCurrent()
        scope.cancel()

        assertEquals(0, readCalls)
        counters.assertAbortedExactlyOnce()
    }

    @Test
    fun cancellationDuringReadAbortsAndCompletesDeferral() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val readStarted = CompletableDeferred<Unit>()
        val text = CompletableDeferred<String?>()
        val counters = DropTerminationCounters()
        val termination = counters.createTermination()

        val job =
            scope.launchWinUIDropRead(
                loadPlainText = {
                    readStarted.complete(Unit)
                    text.await()
                },
                termination = termination,
                onHandled = {},
            )
        runCurrent()
        assertTrue(readStarted.isCompleted)

        job.cancel()
        runCurrent()
        scope.cancel()

        counters.assertAbortedExactlyOnce()
    }

    @Test
    fun thrownReadPreparationFailureAbortsAndCompletesDeferral() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var preparationFailure: Throwable? = null
        val scope =
            CoroutineScope(
                dispatcher +
                    SupervisorJob() +
                    CoroutineExceptionHandler { _, throwable -> preparationFailure = throwable }
            )
        val counters = DropTerminationCounters()
        val termination = counters.createTermination()

        scope.launchWinUIDropRead(
            loadPlainText = { error("preparation failed") },
            termination = termination,
            onHandled = {},
        )
        runCurrent()
        scope.cancel()

        assertEquals("preparation failed", preparationFailure?.message)
        counters.assertAbortedExactlyOnce()
    }

    @Test
    fun nullReadStillDispatchesDropAndReportsHandledResult() = runTest {
        val counters = DropTerminationCounters(finishResult = true)
        val termination = counters.createTermination()
        var handled: Boolean? = null

        backgroundScope.launchWinUIDropRead(
            loadPlainText = { null },
            termination = termination,
            onHandled = { handled = it },
        )
        runCurrent()

        assertEquals(true, handled)
        counters.assertFinishedExactlyOnce()
    }

    @Test
    fun dropResultIsPublishedBeforeDeferralCompletes() = runTest {
        val events = mutableListOf<String>()
        val termination =
            WinUIDropTermination(
                finishDrop = {
                    events += "drop"
                    false
                },
                abortDrop = { events += "abort" },
                completeDeferral = { events += "deferral" },
                onTerminated = { events += "terminated" },
            )

        backgroundScope.launchWinUIDropRead(
            loadPlainText = { "text" },
            termination = termination,
            onHandled = { handled -> events += "handled=$handled" },
        )
        runCurrent()

        assertEquals(
            listOf("drop", "handled=false", "deferral", "terminated"),
            events,
        )
    }

    @Test
    fun successfulReadDispatchesDropAndReportsHandledResult() = runTest {
        val counters = DropTerminationCounters(finishResult = true)
        val termination = counters.createTermination()
        var handled: Boolean? = null

        backgroundScope.launchWinUIDropRead(
            loadPlainText = { "text" },
            termination = termination,
            onHandled = { handled = it },
        )
        runCurrent()

        assertEquals(true, handled)
        counters.assertFinishedExactlyOnce()
    }

    @Test
    fun cleanupRunsEveryActionAndPreservesFirstFailure() {
        val events = mutableListOf<String>()
        val firstFailure = IllegalStateException("pending drop abort failed")
        val laterFailure = IllegalArgumentException("event removal failed")

        val failure =
            assertFailsWith<IllegalStateException> {
                runWinUIDragAndDropCleanup(
                    {
                        events += "pending drop"
                        throw firstFailure
                    },
                    { events += "lifecycle job" },
                    {
                        events += "event one"
                        throw laterFailure
                    },
                    { events += "event two" },
                    { events += "drag session" },
                    { events += "allow drop" },
                )
            }

        assertSame(firstFailure, failure)
        assertEquals(
            listOf(
                "pending drop",
                "lifecycle job",
                "event one",
                "event two",
                "drag session",
                "allow drop",
            ),
            events,
        )
        assertEquals(listOf(laterFailure), failure.suppressed.toList())
    }
}

private class DropTerminationCounters(private val finishResult: Boolean = false) {
    private var finishCalls = 0
    private var abortCalls = 0
    private var deferralCalls = 0
    private var terminatedCalls = 0

    fun createTermination(): WinUIDropTermination =
        WinUIDropTermination(
            finishDrop = {
                finishCalls++
                finishResult
            },
            abortDrop = { abortCalls++ },
            completeDeferral = { deferralCalls++ },
            onTerminated = { terminatedCalls++ },
        )

    fun assertAbortedExactlyOnce() {
        assertEquals(0, finishCalls)
        assertEquals(1, abortCalls)
        assertEquals(1, deferralCalls)
        assertEquals(1, terminatedCalls)
    }

    fun assertFinishedExactlyOnce() {
        assertEquals(1, finishCalls)
        assertEquals(0, abortCalls)
        assertEquals(1, deferralCalls)
        assertEquals(1, terminatedCalls)
    }
}
