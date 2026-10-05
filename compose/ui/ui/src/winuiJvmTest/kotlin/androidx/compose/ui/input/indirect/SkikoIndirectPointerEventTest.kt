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

package androidx.compose.ui.input.indirect

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import org.jetbrains.skiko.winui.WinUIIndirectPointerChange
import org.jetbrains.skiko.winui.WinUIIndirectPointerDeviceRect
import org.jetbrains.skiko.winui.WinUIIndirectPointerEvent
import org.jetbrains.skiko.winui.WinUIIndirectPointerEventType
import org.jetbrains.skiko.winui.WinUIIndirectPointerPrimaryDirectionalMotionAxis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SkikoIndirectPointerEventTest {
    @Test
    fun mapsMoveWithoutScalingDeviceRelativeCoordinates() {
        val native =
            WinUIIndirectPointerEvent(
                type = WinUIIndirectPointerEventType.MOVE,
                changes =
                    listOf(
                        WinUIIndirectPointerChange(
                            pointerId = 9,
                            timestampMillis = 30,
                            x = 8125f,
                            y = 4030f,
                            pressed = true,
                            pressure = 0.75f,
                            previousTimestampMillis = 20,
                            previousX = 8000f,
                            previousY = 4000f,
                            previousPressed = true,
                        )
                    ),
                primaryDirectionalMotionAxis =
                    WinUIIndirectPointerPrimaryDirectionalMotionAxis.NONE,
                deviceId = 44,
                deviceRect = WinUIIndirectPointerDeviceRect(0, 0, 12000, 7000),
                frameId = 71,
            )

        val event = native.toComposeIndirectPointerEvent()
        val change = event.changes.single()

        assertEquals(PointerId(9), change.id)
        assertEquals(30, change.uptimeMillis)
        assertEquals(Offset(8125f, 4030f), change.position)
        assertEquals(true, change.pressed)
        assertEquals(0.75f, change.pressure)
        assertEquals(20, change.previousUptimeMillis)
        assertEquals(Offset(8000f, 4000f), change.previousPosition)
        assertEquals(true, change.previousPressed)
        assertEquals(IndirectPointerEventType.Move, event.type)
        assertEquals(
            IndirectPointerEventPrimaryDirectionalMotionAxis.None,
            event.primaryDirectionalMotionAxis,
        )
        assertSame(native, (event as SkikoIndirectPointerEvent).nativeEvent)
    }

    @Test
    fun mapsPressReleaseAndPrimaryAxes() {
        val press =
            nativeEvent(
                type = WinUIIndirectPointerEventType.PRESS,
                axis = WinUIIndirectPointerPrimaryDirectionalMotionAxis.X,
                pressed = true,
                previousPressed = false,
            )
        val release =
            nativeEvent(
                type = WinUIIndirectPointerEventType.RELEASE,
                axis = WinUIIndirectPointerPrimaryDirectionalMotionAxis.Y,
                pressed = false,
                previousPressed = true,
            )

        assertEquals(IndirectPointerEventType.Press, press.toComposeIndirectPointerEvent().type)
        assertEquals(
            IndirectPointerEventPrimaryDirectionalMotionAxis.X,
            press.toComposeIndirectPointerEvent().primaryDirectionalMotionAxis,
        )
        assertEquals(IndirectPointerEventType.Release, release.toComposeIndirectPointerEvent().type)
        assertEquals(
            IndirectPointerEventPrimaryDirectionalMotionAxis.Y,
            release.toComposeIndirectPointerEvent().primaryDirectionalMotionAxis,
        )
    }

    @Test
    fun mapsMultipleContactJoinAndReleaseTransitionsOneToOne() {
        val native =
            WinUIIndirectPointerEvent(
                type = WinUIIndirectPointerEventType.PRESS,
                changes =
                    listOf(
                        change(pointerId = 1, x = 1200f, pressed = true, previousPressed = true),
                        change(pointerId = 2, x = 4200f, pressed = true, previousPressed = false),
                        change(pointerId = 3, x = 7200f, pressed = false, previousPressed = true),
                    ),
                primaryDirectionalMotionAxis =
                    WinUIIndirectPointerPrimaryDirectionalMotionAxis.NONE,
                deviceId = 44,
                deviceRect = null,
                frameId = 72,
            )

        val changes = native.toComposeIndirectPointerEvent().changes

        assertEquals(listOf(PointerId(1), PointerId(2), PointerId(3)), changes.map { it.id })
        assertEquals(listOf(true, true, false), changes.map { it.pressed })
        assertEquals(listOf(true, false, true), changes.map { it.previousPressed })
        assertEquals(
            listOf(Offset(1200f, 40f), Offset(4200f, 40f), Offset(7200f, 40f)),
            changes.map { it.position },
        )
    }

    @Test
    fun publicTestFactoryHasNoNativeEvent() {
        val event =
            IndirectPointerEvent(
                changes = listOf(composeChange()),
                type = IndirectPointerEventType.Press,
            )

        assertNull((event as SkikoIndirectPointerEvent).nativeEvent)
    }

    private fun nativeEvent(
        type: WinUIIndirectPointerEventType,
        axis: WinUIIndirectPointerPrimaryDirectionalMotionAxis,
        pressed: Boolean,
        previousPressed: Boolean,
    ) =
        WinUIIndirectPointerEvent(
            type = type,
            changes =
                listOf(
                    change(
                        pointerId = 1,
                        x = 10f,
                        pressed = pressed,
                        previousPressed = previousPressed,
                    )
                ),
            primaryDirectionalMotionAxis = axis,
            deviceId = 1,
            deviceRect = null,
            frameId = 1,
        )

    private fun change(
        pointerId: Long,
        x: Float,
        pressed: Boolean,
        previousPressed: Boolean,
    ) =
        WinUIIndirectPointerChange(
            pointerId = pointerId,
            timestampMillis = 20,
            x = x,
            y = 40f,
            pressed = pressed,
            pressure = if (pressed) 0.5f else 0f,
            previousTimestampMillis = 10,
            previousX = x - 10f,
            previousY = 30f,
            previousPressed = previousPressed,
        )

    private fun composeChange() =
        IndirectPointerInputChange(
            id = PointerId(1),
            uptimeMillis = 20,
            position = Offset(10f, 20f),
            pressed = true,
            pressure = 1f,
            previousUptimeMillis = 10,
            previousPosition = Offset(5f, 10f),
            previousPressed = false,
        )
}
