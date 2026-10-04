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

package androidx.compose.ui.graphics

import androidx.compose.ui.WinUISkikoTestBase
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIGraphicsContextTest : WinUISkikoTestBase() {
    @BeforeTest
    fun setUp() {
        WinUIGraphicsContext.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        WinUIGraphicsContext.resetForTest()
    }

    @Test
    fun createAndReleaseGraphicsLayerTracksActiveLayerCount() {
        val first = WinUIGraphicsContext.createGraphicsLayer()
        val second = WinUIGraphicsContext.createGraphicsLayer()

        assertFalse(first.isReleased)
        assertFalse(second.isReleased)
        assertEquals(2, WinUIGraphicsContext.activeGraphicsLayersCount)

        WinUIGraphicsContext.releaseGraphicsLayer(first)

        assertTrue(first.isReleased)
        assertFalse(second.isReleased)
        assertEquals(1, WinUIGraphicsContext.activeGraphicsLayersCount)

        WinUIGraphicsContext.releaseGraphicsLayer(first)

        assertEquals(1, WinUIGraphicsContext.activeGraphicsLayersCount)

        WinUIGraphicsContext.releaseGraphicsLayer(second)

        assertTrue(second.isReleased)
        assertEquals(0, WinUIGraphicsContext.activeGraphicsLayersCount)
    }

    @Test
    fun createdGraphicsLayerKeepsMinimalRecordedState() {
        val layer = WinUIGraphicsContext.createGraphicsLayer()

        layer.record(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            size = IntSize(24, 36),
        ) {}

        assertEquals(IntSize(24, 36), layer.size)

        WinUIGraphicsContext.releaseGraphicsLayer(layer)

        assertTrue(layer.isReleased)
    }
}
