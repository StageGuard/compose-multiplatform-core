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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIMaterialGlobalizationSourceSetTest {
    @Test
    fun materialDateFormattingUsesWinRTFromSharedWinuiSourceSet() {
        val moduleRoot = findMaterialModuleRoot()
        val localeSource = moduleRoot.resolve(
            "src/winuiMain/kotlin/androidx/compose/material3/CalendarLocale.winui.kt"
        )
        val dateSource = moduleRoot.resolve(
            "src/winuiMain/kotlin/androidx/compose/material3/internal/PlatformDateFormat.winui.kt"
        )

        assertTrue(localeSource.exists(), "WinUI CalendarLocale should live in winuiMain.")
        assertTrue(dateSource.exists(), "WinUI date formatting should live in winuiMain.")
        val source = localeSource.readText() + dateSource.readText()
        assertTrue(source.contains("windows.globalization.datetimeformatting.DateTimeFormatter"))
        assertTrue(source.contains("windows.globalization.numberformatting.DecimalFormatter"))
        assertTrue(source.contains("windows.system.userprofile.GlobalizationPreferences"))
        listOf("java.util.Locale", "java.text.", "java.time.").forEach { forbidden ->
            assertFalse(source.contains(forbidden), "WinUI globalization must not use $forbidden")
        }
        assertFalse(
            moduleRoot.resolve(
                "src/winuiJvmMain/kotlin/androidx/compose/material3/CalendarLocale.winui.kt"
            ).exists(),
        )
        assertFalse(
            moduleRoot.resolve(
                "src/winuiJvmMain/kotlin/androidx/compose/material3/internal/PlatformDateFormat.winui.kt"
            ).exists(),
        )
    }

    private fun findMaterialModuleRoot(): Path {
        val start = Paths.get("").toAbsolutePath()
        return generateSequence(start) { it.parent }
            .flatMap { root -> sequenceOf(root.resolve("compose/material3/material3"), root) }
            .firstOrNull { candidate ->
                candidate.fileName?.toString() == "material3" &&
                    Files.exists(candidate.resolve("src/commonMain/kotlin"))
            } ?: error("Could not locate material3 module from $start")
    }
}
