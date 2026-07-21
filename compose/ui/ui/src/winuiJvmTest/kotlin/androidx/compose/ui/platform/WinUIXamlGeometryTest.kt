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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinUIXamlGeometryTest {
    @Test
    fun physicalPixelsConvertToXamlDipsAtCommonScales() {
        listOf(
            1f to WinUIXamlSize(width = 300.0, height = 150.0),
            1.5f to WinUIXamlSize(width = 200.0, height = 100.0),
            2f to WinUIXamlSize(width = 150.0, height = 75.0),
        ).forEach { (scale, expectedSize) ->
            assertEquals(expectedSize, IntSize(300, 150).toWinUIXamlSize(scale))

            val point = IntOffset(30, 60).toWinUIXamlPoint(scale)
            assertEquals(30f / scale, point.x)
            assertEquals(60f / scale, point.y)

            val toolbarPoint = Rect(30f, 15f, 90f, 60f)
                .toWinUITextToolbarXamlPoint(scale)
            assertEquals(30f / scale, toolbarPoint.x)
            assertEquals(60f / scale, toolbarPoint.y)
        }
    }

    @Test
    fun invalidRasterizationScaleFallsBackToOne() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f).forEach { scale ->
            assertEquals(1f, validateWinUIRasterizationScale(scale))
            assertEquals(
                WinUIXamlSize(width = 12.0, height = 6.0),
                IntSize(12, 6).toWinUIXamlSize(scale),
            )
        }
    }

    @Test
    fun nativeSurfacesUseSharedPhysicalPixelToXamlDipBoundary() {
        val moduleRoot = findUiModuleRoot()
        val popup = moduleRoot
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/window/Popup.winui.kt")
            .readText()
        val dialog = moduleRoot
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/window/Dialog.winui.kt")
            .readText()
        val toolbar = moduleRoot
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/platform/WinUITextToolbar.winui.kt")
            .readText()
        val renderHost = moduleRoot
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/platform/WinUISkikoRenderHost.winui.kt")
            .readText()

        assertTrue(popup.contains("toWinUIXamlSize(rasterizationScale)"))
        assertTrue(popup.contains("toWinUIXamlPoint(rasterizationScale)"))
        assertTrue(dialog.contains("toWinUIXamlSize(rasterizationScale)"))
        assertTrue(toolbar.contains("FlyoutShowOptions()"))
        assertTrue(toolbar.contains("toWinUITextToolbarXamlPoint"))
        assertTrue(renderHost.contains("toWinUIXamlSize(density.density)"))
    }

    private fun findUiModuleRoot(): Path {
        val start = Paths.get("").toAbsolutePath()
        generateSequence(start) { it.parent }.forEach { candidate ->
            val direct = candidate.resolve("src/winuiMain/kotlin")
            if (direct.exists() && candidate.name == "ui") return candidate

            val fromRepoRoot = candidate.resolve("compose/ui/ui/src/winuiMain/kotlin")
            if (fromRepoRoot.exists()) return candidate.resolve("compose/ui/ui")
        }
        error("Could not find compose/ui/ui module root from $start.")
    }
}
