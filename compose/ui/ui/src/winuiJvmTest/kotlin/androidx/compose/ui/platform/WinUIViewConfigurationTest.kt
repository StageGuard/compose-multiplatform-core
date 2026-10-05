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

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class WinUIViewConfigurationTest {
    @Test
    fun usesTheDesktopInteractionDefaults() {
        val configuration = DefaultWinUIViewConfiguration()

        assertEquals(500L, configuration.longPressTimeoutMillis)
        assertEquals(300L, configuration.doubleTapTimeoutMillis)
        assertEquals(40L, configuration.doubleTapMinTimeMillis)
        assertEquals(DpSize(48.dp, 48.dp), configuration.minimumTouchTargetSize)
    }

    @Test
    fun touchSlopFollowsDensity() {
        var density = Density(1f)
        val configuration = DefaultWinUIViewConfiguration { density }

        assertEquals(18f, configuration.touchSlop)
        density = Density(1.5f)
        assertEquals(27f, configuration.touchSlop)
    }
}
