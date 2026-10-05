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

package androidx.compose.foundation.text

import androidx.compose.foundation.text.selection.SelectionHandleAnchor
import androidx.compose.foundation.text.selection.SelectionHandleInfo
import androidx.compose.ui.geometry.Offset
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUICursorHandleTest {
    @Test
    fun cursorHandleSemanticsTrackPositionAndVisibility() {
        val position = Offset(12f, 34f)

        assertEquals(
            SelectionHandleInfo(
                handle = Handle.Cursor,
                position = position,
                anchor = SelectionHandleAnchor.Middle,
                visible = true,
            ),
            winUICursorHandleInfo(position),
        )
        assertFalse(winUICursorHandleInfo(Offset.Unspecified).visible)
    }

    @Test
    fun winuiCursorHandleUsesSkikoPopupTouchTargetAndComposeDrawing() {
        val source = findFoundationModuleRoot()
            .resolve(
                "src/winuiMain/kotlin/androidx/compose/foundation/text/" +
                    "DesktopCursorHandle.winui.kt"
            )
            .readText()

        listOf(
            "HandlePopup(",
            "handleReferencePoint = Alignment.TopCenter",
            "requiredSizeIn(",
            "SelectionHandleInfoKey",
            "LocalTextSelectionColors.current.handleColor",
            "drawWithCache",
        ).forEach { required ->
            assertTrue(source.contains(required), "Missing WinUI cursor-handle behavior: $required")
        }
        assertFalse(source.contains("Not implemented"))
    }

    private fun findFoundationModuleRoot(): Path {
        val start = Paths.get("").toAbsolutePath()
        generateSequence(start) { it.parent }.forEach { candidate ->
            val direct = candidate.resolve("src/winuiMain/kotlin")
            if (direct.exists() && candidate.name == "foundation") return candidate

            val fromRepoRoot = candidate.resolve("compose/foundation/foundation/src/winuiMain/kotlin")
            if (fromRepoRoot.exists()) return candidate.resolve("compose/foundation/foundation")
        }
        error("Could not find compose/foundation/foundation module root from $start.")
    }
}
