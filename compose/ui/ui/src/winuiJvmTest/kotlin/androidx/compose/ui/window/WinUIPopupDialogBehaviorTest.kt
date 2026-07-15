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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIPopupDialogBehaviorTest {
    @Test
    fun backPressUsesLatestCallbackAndIsDeliveredOnce() {
        var calls = emptyList<String>()
        val state = WinUIPopupDismissState(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            onDismissRequest = { calls += "first" },
        )
        state.update(onDismissRequest = { calls += "latest" })

        assertTrue(state.onBackPress())
        assertFalse(state.onBackPress())
        assertEquals(listOf("latest"), calls)
    }

    @Test
    fun disabledDismissPropertiesDoNotDispatch() {
        var calls = 0
        val state = WinUIPopupDismissState(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            onDismissRequest = { calls++ },
        )

        assertFalse(state.onBackPress())
        assertFalse(state.onOutsidePointer(Offset(200f, 200f), IntRect(0, 0, 100, 100)))
        assertEquals(0, calls)
    }

    @Test
    fun outsidePointerOnlyDismissesOutsideBounds() {
        var calls = 0
        val state = WinUIPopupDismissState(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            onDismissRequest = { calls++ },
        )

        assertFalse(state.onOutsidePointer(Offset(50f, 50f), IntRect(0, 0, 100, 100)))
        assertTrue(state.onOutsidePointer(Offset(200f, 200f), IntRect(0, 0, 100, 100)))
        assertEquals(1, calls)
    }

    @Test
    fun dialogDefaultWidthIsConstrained() {
        val unconstrained = winUIDialogMaxWidth(
            windowWidth = 1200,
            availableWidth = 1200,
            usePlatformDefaultWidth = false,
        )
        val constrained = winUIDialogMaxWidth(
            windowWidth = 1200,
            availableWidth = 1200,
            usePlatformDefaultWidth = true,
        )

        assertEquals(1200, unconstrained)
        assertTrue(constrained < unconstrained)
        assertEquals(constrained, winUIDialogMaxWidth(1200, 1200, true))
    }
}
