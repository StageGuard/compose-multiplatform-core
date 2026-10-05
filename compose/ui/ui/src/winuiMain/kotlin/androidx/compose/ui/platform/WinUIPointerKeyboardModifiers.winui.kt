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

import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import microsoft.ui.input.InputKeyboardSource
import windows.system.VirtualKey
import windows.system.VirtualKeyModifiers
import windows.ui.core.CoreVirtualKeyStates

internal data class WinUILockKeyState(
    val capsLockOn: Boolean = false,
    val scrollLockOn: Boolean = false,
    val numLockOn: Boolean = false,
)

internal class WinUIKeyboardModifierState {
    private val pressedControlKeys = mutableSetOf<VirtualKey>()
    private val pressedMetaKeys = mutableSetOf<VirtualKey>()
    private val pressedAltKeys = mutableSetOf<VirtualKey>()
    private val pressedShiftKeys = mutableSetOf<VirtualKey>()

    fun update(key: VirtualKey, isPressed: Boolean) {
        when (key) {
            VirtualKey.Control,
            VirtualKey.LeftControl,
            VirtualKey.RightControl -> pressedControlKeys.update(key, isPressed)
            VirtualKey.LeftWindows,
            VirtualKey.RightWindows -> pressedMetaKeys.update(key, isPressed)
            VirtualKey.Menu,
            VirtualKey.LeftMenu,
            VirtualKey.RightMenu -> pressedAltKeys.update(key, isPressed)
            VirtualKey.Shift,
            VirtualKey.LeftShift,
            VirtualKey.RightShift -> pressedShiftKeys.update(key, isPressed)
            else -> Unit
        }
    }

    fun reconcilePressed(modifiers: PointerKeyboardModifiers) {
        pressedControlKeys.replaceWith(modifiers.isCtrlPressed)
        pressedMetaKeys.replaceWith(modifiers.isMetaPressed)
        pressedAltKeys.replaceWith(modifiers.isAltPressed)
        pressedShiftKeys.replaceWith(modifiers.isShiftPressed)
    }

    fun clearPressed() {
        pressedControlKeys.clear()
        pressedMetaKeys.clear()
        pressedAltKeys.clear()
        pressedShiftKeys.clear()
    }

    fun toPointerKeyboardModifiers(
        lockKeys: WinUILockKeyState = winUICurrentLockKeyState(),
    ): PointerKeyboardModifiers = PointerKeyboardModifiers(
        isCtrlPressed = pressedControlKeys.isNotEmpty(),
        isMetaPressed = pressedMetaKeys.isNotEmpty(),
        isAltPressed = pressedAltKeys.isNotEmpty(),
        isShiftPressed = pressedShiftKeys.isNotEmpty(),
        isCapsLockOn = lockKeys.capsLockOn,
        isScrollLockOn = lockKeys.scrollLockOn,
        isNumLockOn = lockKeys.numLockOn,
    )
}

private fun MutableSet<VirtualKey>.update(key: VirtualKey, isPressed: Boolean) {
    if (isPressed) {
        remove(UnknownPressedModifierKey)
        add(key)
    } else if (!remove(key)) {
        remove(UnknownPressedModifierKey)
    }
}

private fun MutableSet<VirtualKey>.replaceWith(isPressed: Boolean) {
    if (!isPressed) {
        clear()
    } else if (isEmpty()) {
        add(UnknownPressedModifierKey)
    }
}

private val UnknownPressedModifierKey = VirtualKey.None

internal fun winUIPointerKeyboardModifiersFromWinUI(
    modifiers: VirtualKeyModifiers,
    lockKeys: WinUILockKeyState = winUICurrentLockKeyState(),
): PointerKeyboardModifiers =
    PointerKeyboardModifiers(
        isCtrlPressed = modifiers.hasFlag(VirtualKeyModifiers.Control),
        isAltPressed = modifiers.hasFlag(VirtualKeyModifiers.Menu),
        isShiftPressed = modifiers.hasFlag(VirtualKeyModifiers.Shift),
        isMetaPressed = modifiers.hasFlag(VirtualKeyModifiers.Windows),
        isCapsLockOn = lockKeys.capsLockOn,
        isScrollLockOn = lockKeys.scrollLockOn,
        isNumLockOn = lockKeys.numLockOn,
    )

internal fun winUICurrentLockKeyState(): WinUILockKeyState = runCatching {
    WinUILockKeyState(
        capsLockOn = InputKeyboardSource
            .getKeyStateForCurrentThread(VirtualKey.CapitalLock)
            .hasFlag(CoreVirtualKeyStates.Locked),
        scrollLockOn = InputKeyboardSource
            .getKeyStateForCurrentThread(VirtualKey.Scroll)
            .hasFlag(CoreVirtualKeyStates.Locked),
        numLockOn = InputKeyboardSource
            .getKeyStateForCurrentThread(VirtualKey.NumberKeyLock)
            .hasFlag(CoreVirtualKeyStates.Locked),
    )
}.getOrDefault(WinUILockKeyState())
