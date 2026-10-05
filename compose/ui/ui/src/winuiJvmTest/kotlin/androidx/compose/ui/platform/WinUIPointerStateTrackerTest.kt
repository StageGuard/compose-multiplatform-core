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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.HistoricalChange
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIPointerStateTrackerTest {
    @Test
    fun secondPressDispatchesBothActivePointersInInsertionOrder() {
        val tracker = WinUIPointerStateTracker()

        tracker.update(PointerEventType.Press, pointer(id = 11L, position = Offset(1f, 2f)))
        val event = tracker.update(
            PointerEventType.Press,
            pointer(id = 22L, position = Offset(3f, 4f)),
        )

        assertEquals(listOf(11L, 22L), event.map { it.id })
        assertEquals(listOf(Offset(1f, 2f), Offset(3f, 4f)), event.map { it.position })
    }

    @Test
    fun releaseDispatchesReleasedPointerBeforeRemovingIt() {
        val tracker = WinUIPointerStateTracker()
        tracker.update(PointerEventType.Press, pointer(id = 11L))
        tracker.update(PointerEventType.Press, pointer(id = 22L))

        val event = tracker.update(
            PointerEventType.Release,
            pointer(id = 11L, down = false, pressure = 0f),
        )

        assertEquals(listOf(11L to false, 22L to true), event.map { it.id to it.down })
        assertEquals(listOf(22L), tracker.snapshot().map { it.id })
    }

    @Test
    fun moveUpdatesChangedPointerWithoutDroppingOtherContacts() {
        val tracker = WinUIPointerStateTracker()
        tracker.update(PointerEventType.Press, pointer(id = 11L, position = Offset(1f, 2f)))
        tracker.update(PointerEventType.Press, pointer(id = 22L, position = Offset(3f, 4f)))

        val event = tracker.update(
            PointerEventType.Move,
            pointer(id = 22L, position = Offset(30f, 40f), pressure = 0.75f),
        )

        assertEquals(listOf(Offset(1f, 2f), Offset(30f, 40f)), event.map { it.position })
        assertEquals(0.75f, event.last().pressure)
    }

    @Test
    fun changedPointerCarriesHistoryAndHoverState() {
        val history = listOf(
            HistoricalChange(uptimeMillis = 8L, position = Offset(4f, 5f)),
            HistoricalChange(uptimeMillis = 9L, position = Offset(6f, 7f)),
        )
        val tracker = WinUIPointerStateTracker()

        val event = tracker.update(
            PointerEventType.Move,
            pointer(
                id = 33L,
                down = false,
                type = PointerType.Stylus,
                activeHover = true,
                historical = history,
            ),
        )

        assertEquals(history, event.single().historical)
        assertTrue(event.single().activeHover)
    }

    @Test
    fun clearReportsRetainedStateOnlyOnce() {
        val tracker = WinUIPointerStateTracker()
        tracker.update(PointerEventType.Press, pointer())

        assertTrue(tracker.clear())
        assertFalse(tracker.clear())
        assertTrue(tracker.snapshot().isEmpty())
    }

    @Test
    fun exitRemovesNonPressedPointerAfterDispatch() {
        val tracker = WinUIPointerStateTracker()
        tracker.update(
            PointerEventType.Enter,
            pointer(id = 44L, down = false, activeHover = true),
        )

        val event = tracker.update(
            PointerEventType.Exit,
            pointer(id = 44L, down = false, activeHover = false),
        )

        assertEquals(listOf(44L), event.map { it.id })
        assertEquals(emptyList(), tracker.snapshot())
    }

    @Test
    fun exitKeepsPressedPointerForCaptureUntilRelease() {
        val tracker = WinUIPointerStateTracker()
        tracker.update(PointerEventType.Press, pointer(id = 55L, down = true))

        val event = tracker.update(
            PointerEventType.Exit,
            pointer(id = 55L, down = true, activeHover = false),
        )

        assertEquals(listOf(55L), event.map { it.id })
        assertEquals(listOf(55L), tracker.snapshot().map { it.id })
    }

    @Test
    fun clearDropsPressedAndHoverPointersTogether() {
        val tracker = WinUIPointerStateTracker()
        tracker.update(PointerEventType.Press, pointer(id = 66L))
        tracker.update(
            PointerEventType.Enter,
            pointer(id = 77L, down = false, activeHover = true),
        )

        assertTrue(tracker.clear())
        assertTrue(tracker.snapshot().isEmpty())
    }

    @Test
    fun deliberateCaptureReleaseIsRemovedBeforeNativeCaptureLostCallback() {
        val captures = WinUIPointerCaptureTracker<String> { first, second -> first == second }
        captures.record(pointerId = 1L, pointer = "pointer")
        var unexpectedCaptureLoss = true

        captures.release("pointer") { pointer ->
            unexpectedCaptureLoss = captures.onCaptureLost(pointer)
        }

        assertFalse(unexpectedCaptureLoss)
    }

    @Test
    fun unexpectedCaptureLossIsReportedOnce() {
        val captures = WinUIPointerCaptureTracker<String> { first, second -> first == second }
        captures.record(pointerId = 1L, pointer = "pointer")

        assertTrue(captures.onCaptureLost("pointer"))
        assertFalse(captures.onCaptureLost("pointer"))
    }

    @Test
    fun releaseAllClearsCapturesBeforeNativeCallbacks() {
        val captures = WinUIPointerCaptureTracker<String> { first, second -> first == second }
        captures.record(pointerId = 1L, pointer = "first")
        captures.record(pointerId = 2L, pointer = "second")
        val unexpectedLosses = mutableListOf<Boolean>()

        captures.releaseAll { pointer ->
            unexpectedLosses += captures.onCaptureLost(pointer)
        }

        assertEquals(listOf(false, false), unexpectedLosses)
    }

    private fun pointer(
        id: Long = 1L,
        uptimeMillis: Long = 10L,
        position: Offset = Offset.Zero,
        down: Boolean = true,
        type: PointerType = PointerType.Touch,
        pressure: Float = 1f,
        activeHover: Boolean = false,
        historical: List<HistoricalChange> = emptyList(),
    ) = WinUIPointerSample(
        id = id,
        uptimeMillis = uptimeMillis,
        position = position,
        down = down,
        type = type,
        pressure = pressure,
        activeHover = activeHover,
        historical = historical,
    )
}
