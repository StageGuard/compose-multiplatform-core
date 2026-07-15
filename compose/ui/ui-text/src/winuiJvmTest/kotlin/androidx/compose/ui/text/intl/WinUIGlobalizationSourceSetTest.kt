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

package androidx.compose.ui.text.intl

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIGlobalizationSourceSetTest {
    @Test
    fun localeUsesWinRTFromSharedWinuiSourceSet() {
        val moduleRoot = findModuleRoot("ui-text")
        val sharedSource = moduleRoot.resolve(
            "src/winuiMain/kotlin/androidx/compose/ui/text/intl/PlatformLocale.winui.kt"
        )

        assertTrue(sharedSource.exists(), "WinUI locale should live in winuiMain.")
        val source = sharedSource.readText()
        assertTrue(source.contains("windows.globalization.ApplicationLanguages"))
        assertTrue(source.contains("windows.globalization.Language"))
        assertFalse(source.contains("java.util.Locale"))
        assertFalse(
            moduleRoot.resolve(
                "src/winuiJvmMain/kotlin/androidx/compose/ui/text/intl/PlatformLocale.winuiJvm.kt"
            ).exists(),
            "WinUI locale should not retain a JVM actual.",
        )
        assertFalse(
            moduleRoot.resolve(
                "src/winuiMingwMain/kotlin/androidx/compose/ui/text/intl/PlatformLocale.winuiMingw.kt"
            ).exists(),
            "WinUI locale should not retain a separate Mingw actual.",
        )
    }

    @Test
    fun localeUsesWinRTLanguageMetadata() {
        val locale = Locale("sr-Latn-RS")

        assertEquals("sr", locale.language)
        assertEquals("Latn", locale.script)
        assertEquals("RS", locale.region)
        assertFalse(locale.isRtl())
        assertTrue(Locale("ar-SA").isRtl())
    }

    private fun findModuleRoot(name: String): Path {
        val start = Paths.get("").toAbsolutePath()
        return generateSequence(start) { it.parent }
            .flatMap { root -> sequenceOf(root.resolve("compose/ui/$name"), root) }
            .firstOrNull { candidate ->
                candidate.fileName?.toString() == name &&
                    Files.exists(candidate.resolve("src/commonMain/kotlin"))
            } ?: error("Could not locate $name module from $start")
    }
}
