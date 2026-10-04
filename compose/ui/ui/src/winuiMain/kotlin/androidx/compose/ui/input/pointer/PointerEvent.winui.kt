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

package androidx.compose.ui.input.pointer

import androidx.collection.LongSparseArray
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.util.fastForEach

fun PointerButtons(
    isPrimaryPressed: Boolean = false,
    isSecondaryPressed: Boolean = false,
    isTertiaryPressed: Boolean = false,
    isBackPressed: Boolean = false,
    isForwardPressed: Boolean = false,
): PointerButtons {
    var value = 0
    if (isPrimaryPressed) value = value or ButtonMasks.Primary
    if (isSecondaryPressed) value = value or ButtonMasks.Secondary
    if (isTertiaryPressed) value = value or ButtonMasks.Tertiary
    if (isBackPressed) value = value or ButtonMasks.Back
    if (isForwardPressed) value = value or ButtonMasks.Forward
    return PointerButtons(value)
}

fun PointerKeyboardModifiers(
    isCtrlPressed: Boolean = false,
    isMetaPressed: Boolean = false,
    isAltPressed: Boolean = false,
    isShiftPressed: Boolean = false,
    isAltGraphPressed: Boolean = false,
    isSymPressed: Boolean = false,
    isFunctionPressed: Boolean = false,
    isCapsLockOn: Boolean = false,
    isScrollLockOn: Boolean = false,
    isNumLockOn: Boolean = false,
): PointerKeyboardModifiers {
    var value = 0
    if (isCtrlPressed) value = value or KeyboardModifierMasks.CtrlPressed
    if (isMetaPressed) value = value or KeyboardModifierMasks.MetaPressed
    if (isAltPressed) value = value or KeyboardModifierMasks.AltPressed
    if (isShiftPressed) value = value or KeyboardModifierMasks.ShiftPressed
    if (isAltGraphPressed) value = value or KeyboardModifierMasks.AltGraphPressed
    if (isSymPressed) value = value or KeyboardModifierMasks.SymPressed
    if (isFunctionPressed) value = value or KeyboardModifierMasks.FunctionPressed
    if (isCapsLockOn) value = value or KeyboardModifierMasks.CapsLockOn
    if (isScrollLockOn) value = value or KeyboardModifierMasks.ScrollLockOn
    if (isNumLockOn) value = value or KeyboardModifierMasks.NumLockOn
    return PointerKeyboardModifiers(value)
}

@OptIn(ExperimentalComposeUiApi::class)
actual class PointerEvent internal constructor(
    actual val changes: List<PointerInputChange>,
    actual val buttons: PointerButtons,
    actual val keyboardModifiers: PointerKeyboardModifiers,
    type: PointerEventType,
    val nativeEvent: Any?,
    val button: PointerButton?,
) {
    internal actual constructor(
        changes: List<PointerInputChange>,
        internalPointerEvent: InternalPointerEvent?,
    ) : this(
        changes = changes,
        buttons = internalPointerEvent?.buttons ?: PointerButtons(),
        keyboardModifiers = internalPointerEvent?.keyboardModifiers ?: PointerKeyboardModifiers(),
        type = internalPointerEvent?.type ?: PointerEventType.Unknown,
        nativeEvent = internalPointerEvent?.nativeEvent,
        button = internalPointerEvent?.button,
    )

    actual constructor(changes: List<PointerInputChange>) : this(
        changes = changes,
        buttons = PointerButtons(),
        keyboardModifiers = PointerKeyboardModifiers(),
        type = calculatePointerEventType(changes),
        nativeEvent = null,
        button = null,
    )

    actual var type: PointerEventType = type
        internal set

    companion object {
        private fun calculatePointerEventType(changes: List<PointerInputChange>): PointerEventType {
            if (changes.isEmpty()) return PointerEventType.Unknown
            changes.fastForEach {
                if (it.changedToUpIgnoreConsumed()) return PointerEventType.Release
                if (it.changedToDownIgnoreConsumed()) return PointerEventType.Press
            }
            return PointerEventType.Move
        }
    }
}

internal actual data class PointerInputEvent(
    val eventType: PointerEventType,
    actual val uptime: Long,
    actual val pointers: List<PointerInputEventData>,
    val buttons: PointerButtons = PointerButtons(),
    val keyboardModifiers: PointerKeyboardModifiers = PointerKeyboardModifiers(),
    val nativeEvent: Any? = null,
    val button: PointerButton? = null,
    val activeGesture: PointerClassification = PointerClassification.None,
    val isGestureStart: Boolean = false,
    val isGestureEnd: Boolean = false,
)

internal actual class InternalPointerEvent(
    val type: PointerEventType,
    actual val changes: LongSparseArray<PointerInputChange>,
    val buttons: PointerButtons,
    val keyboardModifiers: PointerKeyboardModifiers,
    val nativeEvent: Any?,
    val button: PointerButton?,
    actual val activeGesture: PointerClassification,
    actual val isGestureStart: Boolean,
    actual val isGestureEnd: Boolean,
) {
    actual constructor(
        changes: LongSparseArray<PointerInputChange>,
        pointerInputEvent: PointerInputEvent,
    ) : this(
        type = pointerInputEvent.eventType,
        changes = changes,
        buttons = pointerInputEvent.buttons,
        keyboardModifiers = pointerInputEvent.keyboardModifiers,
        nativeEvent = pointerInputEvent.nativeEvent,
        button = pointerInputEvent.button,
        activeGesture = pointerInputEvent.activeGesture,
        isGestureStart = pointerInputEvent.isGestureStart,
        isGestureEnd = pointerInputEvent.isGestureEnd,
    )

    actual var suppressMovementConsumption: Boolean = false

    actual fun activeHoverEvent(pointerId: PointerId): Boolean =
        changes[pointerId.value]?.type == PointerType.Mouse
}

private object ButtonMasks {
    const val Primary = 1 shl 0
    const val Secondary = 1 shl 1
    const val Tertiary = 1 shl 2
    const val Back = 1 shl 3
    const val Forward = 1 shl 4
}

private object KeyboardModifierMasks {
    const val CtrlPressed = 1 shl 0
    const val MetaPressed = 1 shl 1
    const val AltPressed = 1 shl 2
    const val AltGraphPressed = 1 shl 3
    const val SymPressed = 1 shl 4
    const val ShiftPressed = 1 shl 5
    const val FunctionPressed = 1 shl 6
    const val CapsLockOn = 1 shl 7
    const val ScrollLockOn = 1 shl 8
    const val NumLockOn = 1 shl 9
}

actual val PointerButtons.isPrimaryPressed: Boolean
    get() = packedValue and ButtonMasks.Primary != 0

actual val PointerButtons.isSecondaryPressed: Boolean
    get() = packedValue and ButtonMasks.Secondary != 0

actual val PointerButtons.isTertiaryPressed: Boolean
    get() = packedValue and ButtonMasks.Tertiary != 0

actual val PointerButtons.isBackPressed: Boolean
    get() = packedValue and ButtonMasks.Back != 0

actual val PointerButtons.isForwardPressed: Boolean
    get() = packedValue and ButtonMasks.Forward != 0

actual fun PointerButtons.isPressed(buttonIndex: Int): Boolean =
    when (buttonIndex) {
        0 -> isPrimaryPressed
        1 -> isSecondaryPressed
        2 -> isTertiaryPressed
        3 -> isBackPressed
        4 -> isForwardPressed
        else -> false
    }

actual val PointerButtons.areAnyPressed: Boolean
    get() = isPrimaryPressed || isSecondaryPressed || isTertiaryPressed ||
        isBackPressed || isForwardPressed

actual fun PointerButtons.indexOfFirstPressed(): Int = when {
    isPrimaryPressed -> 0
    isSecondaryPressed -> 1
    isTertiaryPressed -> 2
    isBackPressed -> 3
    isForwardPressed -> 4
    else -> -1
}

actual fun PointerButtons.indexOfLastPressed(): Int = when {
    isForwardPressed -> 4
    isBackPressed -> 3
    isTertiaryPressed -> 2
    isSecondaryPressed -> 1
    isPrimaryPressed -> 0
    else -> -1
}

actual val PointerKeyboardModifiers.isCtrlPressed: Boolean
    get() = packedValue and KeyboardModifierMasks.CtrlPressed != 0

actual val PointerKeyboardModifiers.isMetaPressed: Boolean
    get() = packedValue and KeyboardModifierMasks.MetaPressed != 0

actual val PointerKeyboardModifiers.isAltPressed: Boolean
    get() = packedValue and KeyboardModifierMasks.AltPressed != 0

actual val PointerKeyboardModifiers.isAltGraphPressed: Boolean
    get() = packedValue and KeyboardModifierMasks.AltGraphPressed != 0

actual val PointerKeyboardModifiers.isSymPressed: Boolean
    get() = packedValue and KeyboardModifierMasks.SymPressed != 0

actual val PointerKeyboardModifiers.isShiftPressed: Boolean
    get() = packedValue and KeyboardModifierMasks.ShiftPressed != 0

actual val PointerKeyboardModifiers.isFunctionPressed: Boolean
    get() = packedValue and KeyboardModifierMasks.FunctionPressed != 0

actual val PointerKeyboardModifiers.isCapsLockOn: Boolean
    get() = packedValue and KeyboardModifierMasks.CapsLockOn != 0

actual val PointerKeyboardModifiers.isScrollLockOn: Boolean
    get() = packedValue and KeyboardModifierMasks.ScrollLockOn != 0

actual val PointerKeyboardModifiers.isNumLockOn: Boolean
    get() = packedValue and KeyboardModifierMasks.NumLockOn != 0
