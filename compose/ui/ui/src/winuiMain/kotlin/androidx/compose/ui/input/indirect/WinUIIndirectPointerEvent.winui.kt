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
import org.jetbrains.skiko.winui.WinUIIndirectPointerEvent
import org.jetbrains.skiko.winui.WinUIIndirectPointerEventType
import org.jetbrains.skiko.winui.WinUIIndirectPointerPrimaryDirectionalMotionAxis

internal fun WinUIIndirectPointerEvent.toComposeIndirectPointerEvent(): IndirectPointerEvent =
    SkikoIndirectPointerEvent(
        changes =
            changes.map { change ->
                IndirectPointerInputChange(
                    id = PointerId(change.pointerId),
                    uptimeMillis = change.timestampMillis,
                    position = Offset(change.x, change.y),
                    pressed = change.pressed,
                    pressure = change.pressure,
                    previousUptimeMillis = change.previousTimestampMillis,
                    previousPosition = Offset(change.previousX, change.previousY),
                    previousPressed = change.previousPressed,
                )
            },
        type =
            when (type) {
                WinUIIndirectPointerEventType.PRESS -> IndirectPointerEventType.Press
                WinUIIndirectPointerEventType.MOVE -> IndirectPointerEventType.Move
                WinUIIndirectPointerEventType.RELEASE -> IndirectPointerEventType.Release
            },
        primaryDirectionalMotionAxis =
            when (primaryDirectionalMotionAxis) {
                WinUIIndirectPointerPrimaryDirectionalMotionAxis.NONE ->
                    IndirectPointerEventPrimaryDirectionalMotionAxis.None
                WinUIIndirectPointerPrimaryDirectionalMotionAxis.X ->
                    IndirectPointerEventPrimaryDirectionalMotionAxis.X
                WinUIIndirectPointerPrimaryDirectionalMotionAxis.Y ->
                    IndirectPointerEventPrimaryDirectionalMotionAxis.Y
            },
        nativeEvent = this,
    )

/**
 * Create an [IndirectPointerEvent] for WinUI test use cases.
 *
 * In production this event should come from the platform input pipeline.
 */
fun IndirectPointerEvent(
    changes: List<IndirectPointerInputChange>,
    type: IndirectPointerEventType,
    primaryDirectionalMotionAxis: IndirectPointerEventPrimaryDirectionalMotionAxis =
        IndirectPointerEventPrimaryDirectionalMotionAxis.None,
): IndirectPointerEvent =
    SkikoIndirectPointerEvent(
        changes = changes,
        type = type,
        primaryDirectionalMotionAxis = primaryDirectionalMotionAxis,
        nativeEvent = null,
    )
