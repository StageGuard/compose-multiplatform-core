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

package androidx.compose.ui.input.pointer

import androidx.collection.LongSparseArray
import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIActiveHoverTest {
    @Test
    fun activeHoverUsesPlatformHoverFlagRatherThanPointerTypeOnly() {
        val stylusHover = pointerInputEvent(activeHover = true, type = PointerType.Stylus)
        val stylusPressed = pointerInputEvent(activeHover = false, type = PointerType.Stylus)

        assertTrue(activeHoverEvent(stylusHover))
        assertFalse(activeHoverEvent(stylusPressed))
    }

    private fun activeHoverEvent(event: PointerInputEvent): Boolean {
        val internalEvent = InternalPointerEvent(LongSparseArray(), event)
        return internalEvent.activeHoverEvent(PointerId(1L))
    }

    private fun pointerInputEvent(
        activeHover: Boolean,
        type: PointerType,
    ) = PointerInputEvent(
        eventType = PointerEventType.Move,
        uptime = 1L,
        pointers = listOf(
            PointerInputEventData(
                id = PointerId(1L),
                uptime = 1L,
                positionOnScreen = Offset.Zero,
                position = Offset.Zero,
                down = false,
                pressure = 0f,
                type = type,
                activeHover = activeHover,
                scaleGestureFactor = 1f,
                panGestureOffset = Offset.Zero,
            ),
        ),
    )
}
