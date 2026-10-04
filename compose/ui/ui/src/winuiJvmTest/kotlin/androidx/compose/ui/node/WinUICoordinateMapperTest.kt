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

package androidx.compose.ui.node

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class WinUICoordinateMapperTest {
    @Test
    fun screenCoordinateConversionsAreSkippedUntilReady() {
        var calculatePositionInWindowCalls = 0
        var localToScreenCalls = 0
        var screenToLocalCalls = 0
        val mapper = WinUICoordinateMapper(
            calculatePositionInWindow = {
                calculatePositionInWindowCalls += 1
                it + Offset(5f, 7f)
            },
            screenCoordinatesReady = { false },
            localToScreen = {
                localToScreenCalls += 1
                error("localToScreen should not be called before screen coordinates are ready")
            },
            screenToLocal = {
                screenToLocalCalls += 1
                error("screenToLocal should not be called before screen coordinates are ready")
            },
        )

        assertEquals(Offset(16f, 29f), mapper.localToScreen(Offset(11f, 22f)))
        assertEquals(Offset(33f, 44f), mapper.screenToLocal(Offset(33f, 44f)))
        assertEquals(1, calculatePositionInWindowCalls)
        assertEquals(0, localToScreenCalls)
        assertEquals(0, screenToLocalCalls)
    }

    @Test
    fun pixelPositionsAreConvertedToDipsForXaml() {
        // XAML works in DIPs for local and window positions and in physical pixels on the screen.
        val mapper = WinUICoordinateMapper(
            calculatePositionInWindow = { it + Offset(10f, 20f) },
            calculateLocalPosition = { it - Offset(10f, 20f) },
            localToScreen = { (it + Offset(10f, 20f)) * 2f + Offset(650f, 695f) },
            screenToLocal = { (it - Offset(650f, 695f)) / 2f - Offset(10f, 20f) },
            density = { 2f },
        )

        assertEquals(Offset(52f, 84f), mapper.calculatePositionInWindow(Offset(32f, 44f)))
        assertEquals(Offset(32f, 44f), mapper.calculateLocalPosition(Offset(52f, 84f)))
        assertEquals(Offset(702f, 779f), mapper.localToScreen(Offset(32f, 44f)))
        assertEquals(Offset(32f, 44f), mapper.screenToLocal(Offset(702f, 779f)))
    }
}
