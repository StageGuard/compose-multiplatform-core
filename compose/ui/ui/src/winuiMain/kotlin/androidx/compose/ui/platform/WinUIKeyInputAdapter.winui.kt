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

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.node.WinUIOwner
import windows.foundation.EventRegistrationToken
import io.github.composefluent.winrt.runtime.WinRTEvent
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.input.CharacterReceivedRoutedEventArgs
import microsoft.ui.xaml.input.KeyEventHandler
import windows.foundation.TypedEventHandler
import windows.system.VirtualKey

internal class WinUIKeyInputAdapter(
    private val root: UIElement,
    private val owner: WinUIOwner,
    private val composeEventSources: () -> List<Any?> = { listOf(root) },
    private val composeEventSubtreeSources: () -> List<Any?> = { emptyList() },
    private val sendKeyEvent: (KeyEvent) -> Boolean = owner::sendKeyEvent,
) {
    private var isDisposed = false
    private val keyEventProcessor = WinUIKeyEventProcessor()
    private val characterInputProcessor = WinUICharacterInputProcessor()
    private val registrations = listOf(
        registerKey(KeyEventType.KeyDown, root.keyDown),
        registerKey(KeyEventType.KeyUp, root.keyUp),
        registerCharacter(root.characterReceived),
    )

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        registrations.forEach { registration ->
            runCatching { registration.remove() }
        }
    }

    /**
     * Forgets the pressed modifier keys. Called when the window loses focus, because the key up of
     * a modifier released in another window never reaches this one.
     */
    fun resetModifiers() {
        keyEventProcessor.resetModifiers()
    }

    @OptIn(InternalComposeUiApi::class)
    private fun registerKey(
        eventType: KeyEventType,
        event: WinRTEvent<KeyEventHandler>,
    ): WinUIInputEventRegistration<KeyEventHandler> {
        val handler: KeyEventHandler = { sender, args ->
            if (!isDisposed) {
                try {
                    debugKeyInput {
                        "native event=$eventType callback enter sender=${sender.debugClassNameOrNull()}"
                    }
                    val key = readKeyInputProperty(eventType, "key") { args.key }
                    val handledBefore = readKeyInputProperty(eventType, "handled") { args.handled }
                    val keyStatus = readKeyInputProperty(eventType, "keyStatus") { args.keyStatus }
                    val nativeKeyLParam = readKeyInputProperty(eventType, "keyStatus.lParam") {
                        keyStatus.toWin32KeyLParam()
                    }
                    val originalSource = readKeyInputProperty(eventType, "originalSource") {
                        args.originalSource
                    }
                    val composeSources = composeEventSources()
                    val composeSubtreeSources = composeEventSubtreeSources()
                    val shouldDispatch = originalSource.isComposeSource(
                        composeSources = composeSources,
                        composeSubtreeSources = composeSubtreeSources,
                    )
                    debugKeyInput {
                        "native event=$eventType key=$key handledBefore=$handledBefore " +
                            "lParam=0x${nativeKeyLParam.toString(16)} " +
                            "source=${originalSource.debugClassNameOrNull()} " +
                            "composeSources=${composeSources.debugClassNames()} " +
                            "composeSubtreeSources=${composeSubtreeSources.debugClassNames()} " +
                            "shouldDispatch=$shouldDispatch"
                    }
                    val isExtendedKey = readKeyInputProperty(eventType, "keyStatus.isExtendedKey") {
                        keyStatus.isExtendedKey
                    }
                    // The character of the key in the current keyboard layout, as AWT reports it
                    // in KeyEvent.keyChar on desktop.
                    val codePoint = readKeyInputProperty(eventType, "codePoint") {
                        winUIKeyCodePoint(key.abiValue, keyStatus.scanCode.toInt())
                    }
                    val handled = keyEventProcessor.process(
                        eventType = eventType,
                        key = key,
                        isHandled = handledBefore,
                        nativeEvent = args,
                        shouldDispatchEvent = {
                            shouldDispatch
                        },
                        isExtendedKey = isExtendedKey,
                        codePoint = codePoint,
                        sendKeyEvent = sendKeyEvent,
                    ).also { handled ->
                        debugKeyInput {
                            "compose event=$eventType key=$key handled=$handled"
                        }
                    }
                    if (eventType == KeyEventType.KeyDown) {
                        characterInputProcessor.onKeyDownProcessed(
                            keyCodePoint = codePoint,
                            wasHandled = handled == true,
                        )
                    }
                    handled?.let { didHandle ->
                        args.handled = didHandle
                    }
                    debugKeyInput {
                        "native handledAfter event=$eventType key=$key " +
                            "handled=${args.handled}"
                    }
                } catch (throwable: Throwable) {
                    if (throwable.isInvalidVirtualKeyProjectionFailure()) {
                        // KWINRT-051: RDP can surface WM_KEYDOWN as an invalid VirtualKey ABI value.
                        runCatching { args.handled = true }
                        debugKeyInput {
                            "native event=$eventType suppressed invalid VirtualKey projection failure: " +
                                throwable.stackTraceToString()
                        }
                    } else {
                        debugKeyInput {
                            "native event=$eventType failed before leaving WinRT callback: " +
                                throwable.stackTraceToString()
                        }
                    }
                }
            }
        }
        return WinUIInputEventRegistration(event, event.add(handler), handler)
    }

    private fun registerCharacter(
        event: WinRTEvent<TypedEventHandler<UIElement, CharacterReceivedRoutedEventArgs>>,
    ): WinUIInputEventRegistration<TypedEventHandler<UIElement, CharacterReceivedRoutedEventArgs>> {
        val handler: TypedEventHandler<UIElement, CharacterReceivedRoutedEventArgs> = { _, args ->
            if (!isDisposed) {
                try {
                    val character = args.character
                    val handledBefore = args.handled
                    val originalSource = args.originalSource
                    val composeSources = composeEventSources()
                    val composeSubtreeSources = composeEventSubtreeSources()
                    val shouldDispatch = originalSource.isComposeSource(
                        composeSources = composeSources,
                        composeSubtreeSources = composeSubtreeSources,
                    )
                    debugKeyInput {
                        "native event=CharacterReceived character=${character.code} " +
                            "handledBefore=$handledBefore " +
                            "source=${originalSource.debugClassNameOrNull()} " +
                            "shouldDispatch=$shouldDispatch"
                    }
                    if (shouldDispatch) {
                        val coreTextActive = WinUIPlatformTextInputService.isCoreTextInputActive
                        val coreTextComposing =
                            WinUIPlatformTextInputService.isCoreTextCompositionActive
                        characterInputProcessor.process(
                            codePoint = character.code,
                            isHandled = handledBefore,
                            isCoreTextInputActive = coreTextActive,
                            isCoreTextCompositionActive = coreTextComposing,
                            commitText = WinUIPlatformTextInputService::commitText,
                        ).also { handled ->
                            debugKeyInput {
                                "compose event=CharacterReceived character=${character.code} " +
                                    "handled=$handled coreTextActive=$coreTextActive " +
                                    "coreTextComposing=$coreTextComposing"
                            }
                        }?.let { handled ->
                            args.handled = handled
                        }
                    }
                    debugKeyInput {
                        "native handledAfter event=CharacterReceived " +
                            "character=${character.code} " +
                            "handled=${args.handled}"
                    }
                } catch (throwable: Throwable) {
                    debugKeyInput {
                        "native event=CharacterReceived failed before leaving WinRT callback: " +
                            throwable.stackTraceToString()
                    }
                }
            }
        }
        return WinUIInputEventRegistration(event, event.add(handler), handler)
    }
}

internal class WinUIKeyEventProcessor {
    private val modifierState = WinUIKeyModifierState()

    @OptIn(InternalComposeUiApi::class)
    fun process(
        eventType: KeyEventType,
        key: VirtualKey,
        isHandled: Boolean,
        nativeEvent: Any?,
        shouldDispatchEvent: () -> Boolean = { true },
        isExtendedKey: Boolean = false,
        codePoint: Int = 0,
        sendKeyEvent: (KeyEvent) -> Boolean,
    ): Boolean? {
        if (isHandled) return null
        if (!shouldDispatchEvent()) return null
        if (eventType == KeyEventType.KeyDown) {
            modifierState.update(key, isPressed = true)
        }
        val handled = sendKeyEvent(
            key.toComposeKeyEvent(eventType, modifierState, nativeEvent, isExtendedKey, codePoint)
        )
        if (eventType == KeyEventType.KeyUp) {
            modifierState.update(key, isPressed = false)
        }
        return handled
    }

    fun resetModifiers() {
        modifierState.reset()
    }
}

internal class WinUICharacterInputProcessor {
    private var skipNextCharacter = false

    fun onKeyDownProcessed(
        keyCodePoint: Int,
        wasHandled: Boolean,
    ) {
        skipNextCharacter = wasHandled && keyCodePoint.toCommittedTextOrNull() != null
    }

    fun process(
        codePoint: Int,
        isHandled: Boolean,
        isCoreTextInputActive: Boolean = false,
        isCoreTextCompositionActive: Boolean = false,
        commitText: (String) -> Boolean,
    ): Boolean? {
        if (isHandled) return null
        val text = codePoint.toCommittedTextOrNull()
        if (text == null) {
            skipNextCharacter = false
            return null
        }
        if (isCoreTextInputActive && isCoreTextCompositionActive) {
            skipNextCharacter = false
            return true
        }
        if (skipNextCharacter) {
            skipNextCharacter = false
            return null
        }
        return commitText(text)
    }
}

private fun windows.ui.core.CorePhysicalKeyStatus.toWin32KeyLParam(): Long {
    var value = repeatCount.toLong() and 0xFFFFL
    value = value or ((scanCode.toLong() and 0xFFL) shl 16)
    if (isExtendedKey) value = value or (1L shl 24)
    if (isMenuKeyDown) value = value or (1L shl 29)
    if (wasKeyDown) value = value or (1L shl 30)
    if (isKeyReleased) value = value or (1L shl 31)
    return value
}

private fun Any?.isComposeSource(
    composeSources: List<Any?>,
    composeSubtreeSources: List<Any?>,
): Boolean {
    if (this == null) return true
    if (composeSources.any { source -> this == source }) return true
    val sourceElement = asWinRTUIElement() ?: return false
    val subtreeSources = composeSubtreeSources.filterIsInstance<UIElement>()
    return runCatching {
        isComposeKeyEventSubtreeSource(
            source = sourceElement,
            subtreeSources = subtreeSources,
            parentOf = { element ->
                element.asWinRTFrameworkElement()
                    ?.let { frameworkElement ->
                        runCatching { frameworkElement.parent.asWinRTUIElement() }.getOrNull()
                    }
            },
            sameIdentity = { first, second ->
                first.nativeObject.sameIdentity(second.nativeObject)
            },
        )
    }.getOrDefault(false)
}

private fun Any?.asWinRTUIElement(): UIElement? =
    asExistingInstance(UIElement::class.java)
        ?: runCatching { this?.asWinRT<UIElement>() }.getOrNull()

private fun Any?.asWinRTFrameworkElement(): FrameworkElement? =
    asExistingInstance(FrameworkElement::class.java)
        ?: runCatching { this?.asWinRT<FrameworkElement>() }.getOrNull()

private fun <T> Any?.asExistingInstance(type: Class<T>): T? =
    if (this != null && type.isInstance(this)) type.cast(this) else null

internal fun <T : Any> isComposeKeyEventSubtreeSource(
    source: T?,
    subtreeSources: List<T>,
    parentOf: (T) -> T?,
    sameIdentity: (T, T) -> Boolean,
    maxDepth: Int = 32,
): Boolean {
    var current = source
    var depth = 0
    while (current != null && depth < maxDepth) {
        val currentSource = current
        if (subtreeSources.any { candidate ->
                runCatching { sameIdentity(currentSource, candidate) }.getOrDefault(false)
            }
        ) {
            return true
        }
        current = runCatching { parentOf(currentSource) }.getOrNull()
        depth += 1
    }
    return false
}

private inline fun debugKeyInput(message: () -> String) {
    if (winUISystemBooleanProperty("compose.winui.keyInput.debug")) {
        winUIDebugLog("key", message())
    }
}

private inline fun <T> readKeyInputProperty(
    eventType: KeyEventType,
    name: String,
    read: () -> T,
): T {
    debugKeyInput { "native event=$eventType read $name begin" }
    return read().also { value ->
        debugKeyInput { "native event=$eventType read $name end value=${value.debugKeyInputValue()}" }
    }
}

private fun Any?.debugKeyInputValue(): String =
    when (this) {
        null -> "null"
        is Boolean,
        is Number,
        is CharSequence,
        is VirtualKey -> toString()
        else -> debugClassNameOrNull()
    }

private fun Any?.debugClassNameOrNull(): String =
    this?.let { it::class.qualifiedName ?: it::class.simpleName ?: it.toString() } ?: "null"

internal fun Throwable.isInvalidVirtualKeyProjectionFailure(): Boolean =
    this is IllegalStateException &&
        stackTrace.any { frame ->
            frame.className == "windows.system.VirtualKey\$Metadata" &&
                frame.methodName == "fromAbi"
        }

private fun List<Any?>.debugClassNames(): String =
    joinToString(prefix = "[", postfix = "]") { it.debugClassNameOrNull() }

private data class WinUIInputEventRegistration<T : Any>(
    val event: WinRTEvent<T>,
    val token: EventRegistrationToken,
    val handler: T,
) {
    fun remove() {
        event.remove(token)
    }
}

/**
 * The UTF-16 code unit that [virtualKey] types in the current keyboard layout, or 0.
 */
internal expect fun winUIKeyCodePoint(virtualKey: Int, scanCode: Int): Int

private fun Int.toCommittedTextOrNull(): String? {
    if (this <= 0 || this > Char.MAX_VALUE.code) return null
    val char = toChar()
    if (char.isISOControl() || char.isSurrogate()) return null
    return char.toString()
}

private class WinUIKeyModifierState {
    var isCtrlPressed = false
        private set
    var isMetaPressed = false
        private set
    var isAltPressed = false
        private set
    var isShiftPressed = false
        private set

    fun reset() {
        isCtrlPressed = false
        isMetaPressed = false
        isAltPressed = false
        isShiftPressed = false
    }

    fun update(key: VirtualKey, isPressed: Boolean) {
        when (key) {
            VirtualKey.Control,
            VirtualKey.LeftControl,
            VirtualKey.RightControl -> isCtrlPressed = isPressed
            VirtualKey.LeftWindows,
            VirtualKey.RightWindows -> isMetaPressed = isPressed
            VirtualKey.Menu,
            VirtualKey.LeftMenu,
            VirtualKey.RightMenu -> isAltPressed = isPressed
            VirtualKey.Shift,
            VirtualKey.LeftShift,
            VirtualKey.RightShift -> isShiftPressed = isPressed
            else -> Unit
        }
    }
}

@OptIn(InternalComposeUiApi::class)
private fun VirtualKey.toComposeKeyEvent(
    eventType: KeyEventType,
    modifierState: WinUIKeyModifierState,
    nativeEvent: Any?,
    isExtendedKey: Boolean,
    codePoint: Int,
) = KeyEvent(
    key = if (this == VirtualKey.Enter && isExtendedKey) Key.NumPadEnter else toComposeKey(),
    type = eventType,
    codePoint = codePoint,
    isCtrlPressed = modifierState.isCtrlPressed,
    isMetaPressed = modifierState.isMetaPressed,
    isAltPressed = modifierState.isAltPressed,
    isShiftPressed = modifierState.isShiftPressed,
    nativeEvent = nativeEvent,
)

private fun VirtualKey.toComposeKey(): Key =
    when (this) {
        VirtualKey.Back -> Key.Backspace
        VirtualKey.Tab -> Key.Tab
        VirtualKey.Clear -> Key.Clear
        VirtualKey.Enter -> Key.Enter
        VirtualKey.Shift,
        VirtualKey.LeftShift -> Key.ShiftLeft
        VirtualKey.RightShift -> Key.ShiftRight
        VirtualKey.Control,
        VirtualKey.LeftControl -> Key.CtrlLeft
        VirtualKey.RightControl -> Key.CtrlRight
        VirtualKey.Menu,
        VirtualKey.LeftMenu -> Key.AltLeft
        VirtualKey.RightMenu -> Key.AltRight
        VirtualKey.Pause -> Key.Break
        VirtualKey.CapitalLock -> Key.CapsLock
        VirtualKey.Escape -> Key.Escape
        VirtualKey.Space -> Key.Spacebar
        VirtualKey.PageUp -> Key.PageUp
        VirtualKey.PageDown -> Key.PageDown
        VirtualKey.End -> Key.MoveEnd
        VirtualKey.Home -> Key.MoveHome
        VirtualKey.Left -> Key.DirectionLeft
        VirtualKey.Up -> Key.DirectionUp
        VirtualKey.Right -> Key.DirectionRight
        VirtualKey.Down -> Key.DirectionDown
        VirtualKey.Snapshot -> Key.PrintScreen
        VirtualKey.Insert -> Key.Insert
        VirtualKey.Delete -> Key.Delete
        VirtualKey.Help -> Key.Help
        VirtualKey.Number0 -> Key.Zero
        VirtualKey.Number1 -> Key.One
        VirtualKey.Number2 -> Key.Two
        VirtualKey.Number3 -> Key.Three
        VirtualKey.Number4 -> Key.Four
        VirtualKey.Number5 -> Key.Five
        VirtualKey.Number6 -> Key.Six
        VirtualKey.Number7 -> Key.Seven
        VirtualKey.Number8 -> Key.Eight
        VirtualKey.Number9 -> Key.Nine
        VirtualKey.A -> Key.A
        VirtualKey.B -> Key.B
        VirtualKey.C -> Key.C
        VirtualKey.D -> Key.D
        VirtualKey.E -> Key.E
        VirtualKey.F -> Key.F
        VirtualKey.G -> Key.G
        VirtualKey.H -> Key.H
        VirtualKey.I -> Key.I
        VirtualKey.J -> Key.J
        VirtualKey.K -> Key.K
        VirtualKey.L -> Key.L
        VirtualKey.M -> Key.M
        VirtualKey.N -> Key.N
        VirtualKey.O -> Key.O
        VirtualKey.P -> Key.P
        VirtualKey.Q -> Key.Q
        VirtualKey.R -> Key.R
        VirtualKey.S -> Key.S
        VirtualKey.T -> Key.T
        VirtualKey.U -> Key.U
        VirtualKey.V -> Key.V
        VirtualKey.W -> Key.W
        VirtualKey.X -> Key.X
        VirtualKey.Y -> Key.Y
        VirtualKey.Z -> Key.Z
        VirtualKey.LeftWindows -> Key.MetaLeft
        VirtualKey.RightWindows -> Key.MetaRight
        VirtualKey.Application -> Key.Menu
        VirtualKey.NumberPad0 -> Key.NumPad0
        VirtualKey.NumberPad1 -> Key.NumPad1
        VirtualKey.NumberPad2 -> Key.NumPad2
        VirtualKey.NumberPad3 -> Key.NumPad3
        VirtualKey.NumberPad4 -> Key.NumPad4
        VirtualKey.NumberPad5 -> Key.NumPad5
        VirtualKey.NumberPad6 -> Key.NumPad6
        VirtualKey.NumberPad7 -> Key.NumPad7
        VirtualKey.NumberPad8 -> Key.NumPad8
        VirtualKey.NumberPad9 -> Key.NumPad9
        VirtualKey.Multiply -> Key.NumPadMultiply
        VirtualKey.Add -> Key.NumPadAdd
        VirtualKey.Separator -> Key.NumPadComma
        VirtualKey.Subtract -> Key.NumPadSubtract
        VirtualKey.Decimal -> Key.NumPadDot
        VirtualKey.Divide -> Key.NumPadDivide
        VirtualKey.F1 -> Key.F1
        VirtualKey.F2 -> Key.F2
        VirtualKey.F3 -> Key.F3
        VirtualKey.F4 -> Key.F4
        VirtualKey.F5 -> Key.F5
        VirtualKey.F6 -> Key.F6
        VirtualKey.F7 -> Key.F7
        VirtualKey.F8 -> Key.F8
        VirtualKey.F9 -> Key.F9
        VirtualKey.F10 -> Key.F10
        VirtualKey.F11 -> Key.F11
        VirtualKey.F12 -> Key.F12
        VirtualKey.NumberKeyLock -> Key.NumLock
        VirtualKey.Scroll -> Key.ScrollLock
        VirtualKey.GoBack,
        VirtualKey.NavigationCancel -> Key.Back
        VirtualKey.GoForward -> Key.Forward
        VirtualKey.Search -> Key.Search
        VirtualKey.GoHome -> Key.SystemHome
        VirtualKey.GamepadA -> Key.ButtonA
        VirtualKey.GamepadB -> Key.ButtonB
        VirtualKey.GamepadX -> Key.ButtonX
        VirtualKey.GamepadY -> Key.ButtonY
        VirtualKey.GamepadLeftShoulder -> Key.ButtonL1
        VirtualKey.GamepadRightShoulder -> Key.ButtonR1
        VirtualKey.GamepadLeftTrigger -> Key.ButtonL2
        VirtualKey.GamepadRightTrigger -> Key.ButtonR2
        VirtualKey.GamepadDPadUp -> Key.DirectionUp
        VirtualKey.GamepadDPadDown -> Key.DirectionDown
        VirtualKey.GamepadDPadLeft -> Key.DirectionLeft
        VirtualKey.GamepadDPadRight -> Key.DirectionRight
        VirtualKey.GamepadMenu -> Key.ButtonStart
        VirtualKey.GamepadView -> Key.ButtonSelect
        VirtualKey.GamepadLeftThumbstickButton -> Key.ButtonThumbLeft
        VirtualKey.GamepadRightThumbstickButton -> Key.ButtonThumbRight
        else -> abiValue.oemKeyToComposeKey()
}

// The punctuation keys have no named VirtualKey values (VK_OEM_*). Their meaning depends on the
// keyboard layout; these are the US layout keys, as on the desktop target.
private fun Int.oemKeyToComposeKey(): Key =
    when (this) {
        0xBA -> Key.Semicolon
        0xBB -> Key.Equals
        0xBC -> Key.Comma
        0xBD -> Key.Minus
        0xBE -> Key.Period
        0xBF -> Key.Slash
        0xC0 -> Key.Grave
        0xDB -> Key.LeftBracket
        0xDC,
        0xE2 -> Key.Backslash
        0xDD -> Key.RightBracket
        0xDE -> Key.Apostrophe
        else -> Key.Unknown
    }
