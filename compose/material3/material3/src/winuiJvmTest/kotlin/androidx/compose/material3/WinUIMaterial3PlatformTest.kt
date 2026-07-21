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

package androidx.compose.material3

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIMaterial3PlatformTest {
    @Test
    fun modalWideNavigationRailPropertiesCompareTheirOwnTypeAndValue() {
        assertTrue(
            ModalWideNavigationRailProperties(true) ==
                ModalWideNavigationRailProperties(true)
        )
        assertFalse(
            ModalWideNavigationRailProperties(true) ==
                ModalWideNavigationRailProperties(false)
        )
    }

    @Test
    fun precisionPointerSizingRequiresOptInAndBothWinUICapabilities() {
        val both = WinUIPrecisionPointerCapabilities(
            keyboardPresent = true,
            mousePresent = true,
        )
        val keyboardOnly = both.copy(mousePresent = false)
        val mouseOnly = both.copy(keyboardPresent = false)

        assertTrue(winUIShouldUsePrecisionPointerComponentSizing(true, both))
        assertFalse(winUIShouldUsePrecisionPointerComponentSizing(false, both))
        assertFalse(winUIShouldUsePrecisionPointerComponentSizing(true, keyboardOnly))
        assertFalse(winUIShouldUsePrecisionPointerComponentSizing(true, mouseOnly))
    }

    @Test
    @OptIn(InternalComposeUiApi::class)
    fun dropdownMenuDirectionalKeysMoveFocusInKeyboardMode() {
        val focusDirections = mutableListOf<FocusDirection>()
        val requestedInputModes = mutableListOf<InputMode>()
        val focusManager = object : FocusManager {
            override fun clearFocus(force: Boolean) = Unit

            override fun moveFocus(focusDirection: FocusDirection): Boolean {
                focusDirections += focusDirection
                return true
            }
        }
        val inputModeManager = object : InputModeManager {
            override val inputMode: InputMode = InputMode.Touch

            override fun requestInputMode(inputMode: InputMode): Boolean {
                requestedInputModes += inputMode
                return true
            }
        }

        assertTrue(
            handleDropdownOnKeyEvent(
                KeyEvent(Key.DirectionDown, KeyEventType.KeyDown),
                focusManager,
                inputModeManager,
            )
        )
        assertTrue(
            handleDropdownOnKeyEvent(
                KeyEvent(Key.DirectionUp, KeyEventType.KeyDown),
                focusManager,
                inputModeManager,
            )
        )
        assertFalse(
            handleDropdownOnKeyEvent(
                KeyEvent(Key.DirectionDown, KeyEventType.KeyUp),
                focusManager,
                inputModeManager,
            )
        )

        assertEquals(listOf(FocusDirection.Next, FocusDirection.Previous), focusDirections)
        assertEquals(listOf(InputMode.Keyboard, InputMode.Keyboard), requestedInputModes)
    }

    @Test
    fun winuiDropdownMenuPopupsForwardKeyEvents() {
        val source = material3ModuleRoot()
            .resolve("src/winuiJvmMain/kotlin/androidx/compose/material3/SkikoMenu.winui.kt")
            .readText()

        assertEquals(2, Regex("onKeyEvent\\s*=").findAll(source).count())
    }

    @Test
    fun skikoTooltipUsesLocalizedMaterialStrings() {
        val source = material3ModuleRoot()
            .resolve("src/skikoMain/kotlin/androidx/compose/material3/internal/BasicTooltip.skiko.kt")
            .readText()

        assertTrue(source.contains("getString(Strings.TooltipLongPressLabel)"))
        assertTrue(source.contains("getString(Strings.TooltipPaneDescription)"))
        assertFalse(source.contains("\"show tooltip\""))
    }

    private fun material3ModuleRoot(): Path {
        val start = Paths.get("").toAbsolutePath()
        generateSequence(start) { it.parent }.forEach { candidate ->
            val direct = candidate.resolve("src/winuiJvmMain/kotlin")
            if (direct.exists() && candidate.name == "material3") return candidate

            val fromRepoRoot = candidate.resolve("compose/material3/material3/src/winuiJvmMain/kotlin")
            if (fromRepoRoot.exists()) return candidate.resolve("compose/material3/material3")
        }
        error("Could not find compose/material3/material3 module root from $start.")
    }
}
