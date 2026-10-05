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

import microsoft.ui.input.InputSystemCursorShape
import kotlin.test.Test
import kotlin.test.assertEquals

class WinUIPointerIconServiceTest {
    @Test
    fun pointerIconsMapToWinUICursorShapes() {
        assertEquals(InputSystemCursorShape.Arrow, (PointerIcon.Default as WinUIPointerIcon).cursorShape)
        assertEquals(InputSystemCursorShape.Cross, (PointerIcon.Crosshair as WinUIPointerIcon).cursorShape)
        assertEquals(InputSystemCursorShape.IBeam, (PointerIcon.Text as WinUIPointerIcon).cursorShape)
        assertEquals(InputSystemCursorShape.Hand, (PointerIcon.Hand as WinUIPointerIcon).cursorShape)
    }

    @Test
    fun setIconAppliesResolvedPointerIcon() {
        val applied = mutableListOf<PointerIcon>()
        val service = WinUIPointerIconService { applied += it }

        service.setIcon(PointerIcon.Hand)
        service.setIcon(null)
        service.setIcon(PointerIcon.Text)

        assertEquals(PointerIcon.Text, service.getIcon())
        assertEquals(
            listOf(PointerIcon.Hand, PointerIcon.Default, PointerIcon.Text),
            applied,
        )
    }

    @Test
    fun stylusHoverIconOverridesAndRestoresRootPointerIcon() {
        val applied = mutableListOf<PointerIcon>()
        val service = WinUIPointerIconService { applied += it }

        service.setIcon(PointerIcon.Hand)
        service.setStylusHoverIcon(PointerIcon.Crosshair)
        service.setIcon(PointerIcon.Text)

        assertEquals(PointerIcon.Crosshair, service.getStylusHoverIcon())
        assertEquals(PointerIcon.Text, service.getIcon())

        service.setStylusHoverIcon(null)

        assertEquals(
            listOf(
                PointerIcon.Hand,
                PointerIcon.Crosshair,
                PointerIcon.Crosshair,
                PointerIcon.Text,
            ),
            applied,
        )
    }
}
