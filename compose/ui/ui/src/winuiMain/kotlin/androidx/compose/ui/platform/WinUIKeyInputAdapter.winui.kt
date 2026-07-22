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
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
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
    keyboardModifierState: WinUIKeyboardModifierState = WinUIKeyboardModifierState(),
    private val composeEventSources: () -> List<Any?> = { listOf(root) },
    private val composeEventSubtreeSources: () -> List<Any?> = { emptyList() },
) {
    private var isDisposed = false
    private val keyEventProcessor = WinUIKeyEventProcessor(keyboardModifierState)
    private val characterInputProcessor = WinUICharacterInputProcessor()
    private val registrations = listOf(
        registerKey(KeyEventType.KeyDown, root.keyDown),
        registerKey(KeyEventType.KeyUp, root.keyUp),
        registerCharacter(root.characterReceived),
    )

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        reset()
        registrations.forEach { registration ->
            runCatching { registration.remove() }
        }
    }

    internal fun reset() {
        keyEventProcessor.reset()
        characterInputProcessor.reset()
        owner.updateKeyboardModifiers(keyEventProcessor.currentKeyboardModifiers())
    }

    internal fun refreshKeyboardModifiers() {
        owner.updateKeyboardModifiers(keyEventProcessor.currentKeyboardModifiers())
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
                    val handled = keyEventProcessor.process(
                        eventType = eventType,
                        key = key,
                        isHandled = handledBefore,
                        nativeEvent = args,
                        shouldDispatchEvent = {
                            shouldDispatch
                        },
                        onKeyboardModifiersChanged = owner::updateKeyboardModifiers,
                        sendKeyEvent = owner::sendKeyEvent,
                    ).also { handled ->
                        debugKeyInput {
                            "compose event=$eventType key=$key handled=$handled"
                        }
                    }
                    characterInputProcessor.onKeyEventProcessed(
                        eventType = eventType,
                        key = key,
                        wasHandled = handled == true,
                    )
                    handled?.let { didHandle ->
                        args.handled = didHandle
                    }
                    debugKeyInput {
                        "native handledAfter event=$eventType key=$key " +
                            "handled=${args.handled}"
                    }
                } catch (throwable: Throwable) {
                    debugKeyInput {
                        "native event=$eventType failed before leaving WinRT callback: " +
                            throwable.stackTraceToString()
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

internal class WinUIKeyEventProcessor(
    private val modifierState: WinUIKeyboardModifierState = WinUIKeyboardModifierState(),
) {

    fun reset() {
        modifierState.clearPressed()
    }

    fun currentKeyboardModifiers(): PointerKeyboardModifiers =
        modifierState.toPointerKeyboardModifiers()

    @OptIn(InternalComposeUiApi::class)
    fun process(
        eventType: KeyEventType,
        key: VirtualKey,
        isHandled: Boolean,
        nativeEvent: Any?,
        shouldDispatchEvent: () -> Boolean = { true },
        onKeyboardModifiersChanged: (PointerKeyboardModifiers) -> Unit = {},
        sendKeyEvent: (KeyEvent) -> Boolean,
    ): Boolean? {
        if (isHandled) return null
        if (!shouldDispatchEvent()) return null
        if (eventType == KeyEventType.KeyDown) {
            modifierState.update(key, isPressed = true)
        }
        onKeyboardModifiersChanged(modifierState.toPointerKeyboardModifiers())
        return try {
            sendKeyEvent(key.toComposeKeyEvent(eventType, modifierState, nativeEvent))
        } finally {
            if (eventType == KeyEventType.KeyUp) {
                modifierState.update(key, isPressed = false)
                onKeyboardModifiersChanged(modifierState.toPointerKeyboardModifiers())
            }
        }
    }
}

internal class WinUICharacterInputProcessor {
    private var skipNextCharacter = false

    fun reset() {
        skipNextCharacter = false
    }

    fun onKeyEventProcessed(
        eventType: KeyEventType,
        key: VirtualKey,
        wasHandled: Boolean,
    ) {
        skipNextCharacter = eventType == KeyEventType.KeyDown &&
            wasHandled &&
            key.canProduceCharacter()
    }

    fun process(
        codePoint: Int,
        isHandled: Boolean,
        isCoreTextInputActive: Boolean = false,
        isCoreTextCompositionActive: Boolean = false,
        commitText: (String) -> Boolean,
    ): Boolean? {
        val shouldSkip = skipNextCharacter
        skipNextCharacter = false
        if (isHandled) return null
        val text = codePoint.toCommittedTextOrNull()
        if (text == null) return null
        if (isCoreTextInputActive && isCoreTextCompositionActive) {
            return true
        }
        if (shouldSkip) return null
        return commitText(text)
    }
}

private fun VirtualKey.canProduceCharacter(): Boolean {
    val value = abiValue
    return value == 0x20 || // VK_SPACE
        value in 0x30..0x39 || // VK_0 .. VK_9
        value in 0x41..0x5A || // VK_A .. VK_Z
        value in 0x60..0x6F || // numpad digits and operators
        value in 0xBA..0xC0 || // VK_OEM_1 .. VK_OEM_3
        value in 0xDB..0xE2 || // VK_OEM_4 .. VK_OEM_102
        value == 0xE7 // VK_PACKET
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
    runCatching { this?.asWinRT<UIElement>() }.getOrNull()

private fun Any?.asWinRTFrameworkElement(): FrameworkElement? =
    runCatching { this?.asWinRT<FrameworkElement>() }.getOrNull()

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

private fun Int.toCommittedTextOrNull(): String? {
    if (this <= 0 || this > Char.MAX_VALUE.code) return null
    val char = toChar()
    if (char.isISOControl() || char.isSurrogate()) return null
    return char.toString()
}

@OptIn(InternalComposeUiApi::class)
private fun VirtualKey.toComposeKeyEvent(
    eventType: KeyEventType,
    modifierState: WinUIKeyboardModifierState,
    nativeEvent: Any?,
): KeyEvent {
    val modifiers = modifierState.toPointerKeyboardModifiers(lockKeys = WinUILockKeyState())
    return KeyEvent(
        key = toComposeKey(),
        type = eventType,
        codePoint = 0,
        isCtrlPressed = modifiers.isCtrlPressed,
        isMetaPressed = modifiers.isMetaPressed,
        isAltPressed = modifiers.isAltPressed,
        isShiftPressed = modifiers.isShiftPressed,
        nativeEvent = nativeEvent,
    )
}

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
        VirtualKey.NavigationView -> Key.Menu
        VirtualKey.NavigationMenu -> Key.Menu
        VirtualKey.NavigationUp -> Key.DirectionUp
        VirtualKey.NavigationDown -> Key.DirectionDown
        VirtualKey.NavigationLeft -> Key.DirectionLeft
        VirtualKey.NavigationRight -> Key.DirectionRight
        VirtualKey.NavigationAccept -> Key.Enter
        VirtualKey.Print -> Key.PrintScreen
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
        VirtualKey.Refresh -> Key.Refresh
        VirtualKey.Stop -> Key.MediaStop
        VirtualKey.Favorites -> Key.Bookmark
        VirtualKey.Search -> Key.Search
        VirtualKey.GoHome -> Key.SystemHome
        VirtualKey.Sleep -> Key.Sleep
        VirtualKey.Kana -> Key.Kana
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
        else -> toComposeExtendedKey()
    }

private fun VirtualKey.toComposeExtendedKey(): Key =
    when (abiValue) {
        0xA6 -> Key.Back // VK_BROWSER_BACK
        0xA7 -> Key.Forward // VK_BROWSER_FORWARD
        0xA8 -> Key.Refresh // VK_BROWSER_REFRESH
        0xA9 -> Key.MediaStop // VK_BROWSER_STOP
        0xAA -> Key.Search // VK_BROWSER_SEARCH
        0xAB -> Key.Bookmark // VK_BROWSER_FAVORITES
        0xAC -> Key.SystemHome // VK_BROWSER_HOME
        0xAD -> Key.VolumeMute
        0xAE -> Key.VolumeDown
        0xAF -> Key.VolumeUp
        0xB0 -> Key.MediaNext
        0xB1 -> Key.MediaPrevious
        0xB2 -> Key.MediaStop
        0xB3 -> Key.MediaPlayPause
        0xB4 -> Key.Envelope
        0xB5 -> Key.Music
        0xB7 -> Key.Calculator
        0xBA -> Key.Semicolon // VK_OEM_1
        0xBB -> Key.Equals // VK_OEM_PLUS
        0xBC -> Key.Comma // VK_OEM_COMMA
        0xBD -> Key.Minus // VK_OEM_MINUS
        0xBE -> Key.Period // VK_OEM_PERIOD
        0xBF -> Key.Slash // VK_OEM_2
        0xC0 -> Key.Grave // VK_OEM_3
        0xDB -> Key.LeftBracket // VK_OEM_4
        0xDC -> Key.Backslash // VK_OEM_5
        0xDD -> Key.RightBracket // VK_OEM_6
        0xDE -> Key.Apostrophe // VK_OEM_7
        0xE2 -> Key.Backslash // VK_OEM_102
        else -> Key.Unknown
    }
