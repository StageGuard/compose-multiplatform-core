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

import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isCapsLockOn
import androidx.compose.ui.input.pointer.isNumLockOn
import androidx.compose.ui.input.pointer.isScrollLockOn
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import windows.system.VirtualKey
import windows.system.VirtualKeyModifiers

class WinUIPointerKeyboardModifiersTest {
    @Test
    fun lockKeyStateIsMergedWithRoutedModifierFlags() {
        val modifiers = winUIPointerKeyboardModifiersFromWinUI(
            modifiers = VirtualKeyModifiers.Control,
            lockKeys = WinUILockKeyState(
                capsLockOn = true,
                scrollLockOn = true,
                numLockOn = true,
            ),
        )

        assertTrue(modifiers.isCtrlPressed)
        assertTrue(modifiers.isCapsLockOn)
        assertTrue(modifiers.isScrollLockOn)
        assertTrue(modifiers.isNumLockOn)
    }

    @Test
    fun virtualKeyModifierFlagsMapIndividually() {
        assertTrue(winUIPointerKeyboardModifiersFromWinUI(VirtualKeyModifiers.Control).isCtrlPressed)
        assertTrue(winUIPointerKeyboardModifiersFromWinUI(VirtualKeyModifiers.Menu).isAltPressed)
        assertTrue(winUIPointerKeyboardModifiersFromWinUI(VirtualKeyModifiers.Shift).isShiftPressed)
        assertTrue(winUIPointerKeyboardModifiersFromWinUI(VirtualKeyModifiers.Windows).isMetaPressed)
    }

    @Test
    fun virtualKeyModifierFlagsMapCombinations() {
        val modifiers = winUIPointerKeyboardModifiersFromWinUI(
            VirtualKeyModifiers.Control or
                VirtualKeyModifiers.Menu or
                VirtualKeyModifiers.Shift or
                VirtualKeyModifiers.Windows
        )

        assertTrue(modifiers.isCtrlPressed)
        assertTrue(modifiers.isAltPressed)
        assertTrue(modifiers.isShiftPressed)
        assertTrue(modifiers.isMetaPressed)
    }

    @Test
    fun virtualKeyModifierFlagsIgnoreUnknownBits() {
        val modifiers = winUIPointerKeyboardModifiersFromWinUI(VirtualKeyModifiers(16u))

        assertFalse(modifiers.isCtrlPressed)
        assertFalse(modifiers.isAltPressed)
        assertFalse(modifiers.isShiftPressed)
        assertFalse(modifiers.isMetaPressed)
    }

    @Test
    fun clearingPressedModifiersPreservesCurrentLockState() {
        val state = WinUIKeyboardModifierState()
        state.update(VirtualKey.Control, isPressed = true)

        state.clearPressed()
        val modifiers = state.toPointerKeyboardModifiers(
            lockKeys = WinUILockKeyState(
                capsLockOn = true,
                scrollLockOn = true,
                numLockOn = true,
            ),
        )

        assertFalse(modifiers.isCtrlPressed)
        assertTrue(modifiers.isCapsLockOn)
        assertTrue(modifiers.isScrollLockOn)
        assertTrue(modifiers.isNumLockOn)
    }

    @Test
    fun pointerModifierFlagsCanSeedMissingPressedState() {
        val state = WinUIKeyboardModifierState()

        state.reconcilePressed(
            androidx.compose.ui.input.pointer.PointerKeyboardModifiers(isCtrlPressed = true),
        )

        assertTrue(
            state.toPointerKeyboardModifiers(lockKeys = WinUILockKeyState()).isCtrlPressed,
        )
    }
}
