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
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals

class WinUIPointerEventProcessorTest {
    @Test
    fun pointerEventSamplePreservesPressureHistoryAndHover() {
        val history = listOf(
            HistoricalChange(uptimeMillis = 4L, position = Offset(1f, 2f)),
        )
        val sample = samplePointerEvent().copy(
            pressure = 0.6f,
            activeHover = true,
            historical = history,
        ).toPointerSample()

        assertEquals(0.6f, sample.pressure)
        assertEquals(history, sample.historical)
        assertEquals(true, sample.activeHover)
    }

    @Test
    fun dispatchesHandledRenderSurfaceEvents() {
        val processor = WinUIPointerEventProcessor()
        var dispatchCount = 0

        val handled = processor.process(
            event = samplePointerEvent(),
        ) { _, _, _, _, _, _, _, _, _, _, _, _ ->
            dispatchCount += 1
            true
        }

        assertEquals(true, handled)
        assertEquals(1, dispatchCount)
    }

    @Test
    fun capturesDragSourcePointerBeforeComposeHandlesRenderSurfaceEvent() {
        val processor = WinUIPointerEventProcessor()
        val events = mutableListOf<String>()

        val handled =
            processor.process(
                event = samplePointerEvent(),
                onBeforeDispatch = { events += "source" },
            ) { _, _, _, _, _, _, _, _, _, _, _, _ ->
                events += "compose"
                true
            }

        assertEquals(true, handled)
        assertEquals(listOf("source", "compose"), events)
    }

    @Test
    fun dispatchesUnhandledNativeEvents() {
        val processor = WinUIPointerEventProcessor()
        var dispatchedEvent: WinUIPointerEvent? = null
        val event = samplePointerEvent()

        val handled = processor.process(
            event = event,
        ) { eventType, position, uptimeMillis, pointerId, down, type, buttons,
                keyboardModifiers, button, scrollDelta, isInBounds, nativeEvent ->
            dispatchedEvent = WinUIPointerEvent(
                eventType = eventType,
                position = position,
                uptimeMillis = uptimeMillis,
                pointerId = pointerId,
                down = down,
                type = type,
                buttons = buttons,
                keyboardModifiers = keyboardModifiers,
                button = button,
                scrollDelta = scrollDelta,
                isInBounds = isInBounds,
                nativeEvent = nativeEvent,
            )
            true
        }

        assertEquals(true, handled)
        assertEquals(event, dispatchedEvent)
    }

    @Test
    fun preservesWinUIRenderSurfaceRootCoordinates() {
        assertEquals(
            Offset(10f, 20f),
            winUIPositionToComposeOffset(10f, 20f),
        )
    }

    @Test
    fun doesNotApplyWindowTransformToRenderSurfaceRootCoordinates() {
        assertEquals(
            Offset(60f, 120f),
            winUIPositionToComposeOffset(60f, 120f),
        )
    }

    @Test
    fun convertsWinUIDipsToComposeRootPixels() {
        assertEquals(
            Offset(20f, 40f),
            winUIPositionToComposeOffset(10f, 20f, Density(2f)),
        )
        assertEquals(
            Offset(15f, 30f),
            winUIPositionToComposeOffset(10f, 20f, Density(1.5f)),
        )
    }

    @Test
    fun capturedPointerRemainsDownWhenItExitsWhileInContact() {
        assertEquals(
            true,
            winUIIsComposePointerDown(
                eventType = PointerEventType.Exit,
                isInContact = true,
                buttons = PointerButtons(),
            ),
        )
        assertEquals(
            false,
            winUIIsComposePointerDown(
                eventType = PointerEventType.Exit,
                isInContact = false,
                buttons = PointerButtons(),
            ),
        )
    }
}

private fun samplePointerEvent(
    eventType: PointerEventType = PointerEventType.Press,
    down: Boolean = true,
    buttons: PointerButtons = PointerButtons(isPrimaryPressed = true),
) = WinUIPointerEvent(
    eventType = eventType,
    position = Offset(3f, 4f),
    uptimeMillis = 17L,
    pointerId = 23L,
    down = down,
    type = PointerType.Mouse,
    buttons = buttons,
    keyboardModifiers = PointerKeyboardModifiers(isCtrlPressed = true),
    button = PointerButton.Primary,
    scrollDelta = Offset.Zero,
    isInBounds = true,
    nativeEvent = "native",
)
