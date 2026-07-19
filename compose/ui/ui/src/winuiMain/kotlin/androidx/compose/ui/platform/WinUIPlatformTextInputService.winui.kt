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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.BackspaceCommand
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.SetComposingRegionCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
internal object WinUIPlatformTextInputService : PlatformTextInputService {
    private var activeInputSession: WinUITextInputSessionState? = null
    private var activeInputMethodSession: WinUITextInputMethodSessionState? = null
    private var rootToScreenMapperOwner: Any? = null
    private var rootToScreenMapper: (Offset) -> Offset = { it }
    private var rootToScreenMapperReady: () -> Boolean = { false }
    private var rootToViewportMapper: (Offset) -> Offset = { it }
    private var rootViewportBoundsInRoot: () -> Rect? = { null }
    private var inputPaneControllerOwner: Any? = null
    private var inputPaneController: WinUIInputPaneController? = null
    internal val nativeBridge: WinUINativeTextInputBridge = WinUINativeTextInputBridge(this)

    internal val isInputActive: Boolean
        get() = activeInputSession != null

    internal val isInputMethodActive: Boolean
        get() = activeInputMethodSession != null

    internal val isSoftwareKeyboardVisible: Boolean
        get() = activeInputSession?.isSoftwareKeyboardVisible == true ||
            activeInputMethodSession?.isSoftwareKeyboardVisible == true

    internal val isNativeTextInputFocused: Boolean
        get() = activeInputSession?.isNativeTextInputFocused == true ||
            activeInputMethodSession?.isNativeTextInputFocused == true

    internal val isCoreTextInputActive: Boolean
        get() = nativeBridge.isCoreTextSessionActive

    internal val isCoreTextCompositionActive: Boolean
        get() = nativeBridge.isCoreTextCompositionActive

    internal val currentValue: TextFieldValue?
        get() = activeInputSession?.value ?: activeInputMethodSession?.request?.value?.invoke()
            ?: activeInputMethodSession?.value

    internal val currentImeOptions: ImeOptions?
        get() = activeInputSession?.imeOptions ?: activeInputMethodSession?.request?.imeOptions

    internal val previousValue: TextFieldValue?
        get() = activeInputSession?.oldValue ?: activeInputMethodSession?.oldValue

    internal val currentInputMethodRequest: PlatformTextInputMethodRequest?
        get() = activeInputMethodSession?.request

    internal val currentTextLayoutBoundsInRoot: WinUITextLayoutBounds?
        get() = activeInputSession?.textLayoutBoundsInRoot
            ?: activeInputMethodSession?.textLayoutBoundsInRoot

    internal val currentTextLayoutBoundsOnScreen: WinUITextLayoutBounds?
        get() = currentTextLayoutBoundsInRoot?.toCoreTextLayoutSnapshot()?.layoutBounds

    internal val currentCoreTextLayoutSnapshot: WinUICoreTextLayoutSnapshot?
        get() = currentTextLayoutBoundsInRoot?.toCoreTextLayoutSnapshot()

    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit,
    ) {
        debugTextInput {
            "startInput legacy value=${value.debugString()} imeOptions=${imeOptions.debugString()}"
        }
        nativeBridge.disposeCoreTextSession()
        activeInputSession = WinUITextInputSessionState(
            value = value,
            imeOptions = imeOptions,
            onEditCommand = onEditCommand,
            onImeActionPerformed = onImeActionPerformed,
        )
        nativeBridge.attachCoreTextForCurrentInput()
    }

    override fun stopInput() {
        debugTextInput { "stopInput legacy active=${activeInputSession != null}" }
        nativeBridge.disposeCoreTextSession()
        activeInputSession = null
    }

    override fun showSoftwareKeyboard() {
        inputPaneController?.show()
        activeInputSession = activeInputSession?.copy(isSoftwareKeyboardVisible = true)
        activeInputMethodSession =
            activeInputMethodSession?.copy(isSoftwareKeyboardVisible = true)
    }

    override fun hideSoftwareKeyboard() {
        inputPaneController?.hide()
        activeInputSession = activeInputSession?.copy(isSoftwareKeyboardVisible = false)
        activeInputMethodSession =
            activeInputMethodSession?.copy(isSoftwareKeyboardVisible = false)
    }

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        debugTextInput {
            "updateState legacy old=${oldValue?.debugString()} new=${newValue.debugString()}"
        }
        activeInputSession = activeInputSession?.copy(
            oldValue = oldValue,
            value = newValue,
        )
        nativeBridge.updateCoreTextState(oldValue, newValue)
    }

    override fun updateTextLayoutResult(
        textFieldValue: TextFieldValue,
        offsetMapping: OffsetMapping,
        textLayoutResult: TextLayoutResult,
        textFieldToRootTransform: (Matrix) -> Unit,
        innerTextFieldBounds: Rect,
        decorationBoxBounds: Rect,
    ) {
        val textFieldToRootMatrix = Matrix()
        textFieldToRootTransform(textFieldToRootMatrix)
        val textLayoutBoundsInRoot = WinUITextLayoutBounds(
            innerTextFieldBounds = textFieldToRootMatrix.map(innerTextFieldBounds),
            decorationBoxBounds = textFieldToRootMatrix.map(decorationBoxBounds),
        )
        debugTextInput {
            "updateTextLayoutResult legacy value=${textFieldValue.debugString()} " +
                "boundsInRoot=${textLayoutBoundsInRoot.debugString()}"
        }
        activeInputSession = activeInputSession?.copy(
            textFieldValue = textFieldValue,
            offsetMapping = offsetMapping,
            textLayoutResult = textLayoutResult,
            innerTextFieldBounds = innerTextFieldBounds,
            decorationBoxBounds = decorationBoxBounds,
            textLayoutBoundsInRoot = textLayoutBoundsInRoot,
            coreTextLayoutSnapshot = textLayoutBoundsInRoot.toCoreTextLayoutSnapshot(),
        )
        nativeBridge.notifyCoreTextLayoutChanged()
    }

    internal fun enterNativeTextInputFocus(): Boolean {
        val inputSession = activeInputSession
        if (inputSession != null) {
            activeInputSession = inputSession.copy(isNativeTextInputFocused = true)
            return true
        }
        val inputMethodSession = activeInputMethodSession
        if (inputMethodSession != null) {
            activeInputMethodSession = inputMethodSession.copy(isNativeTextInputFocused = true)
            return true
        }
        return false
    }

    internal fun exitNativeTextInputFocus(): Boolean {
        val inputSession = activeInputSession
        if (inputSession != null) {
            activeInputSession = inputSession.copy(
                isNativeTextInputFocused = false,
                isSoftwareKeyboardVisible = false,
            )
            return true
        }
        val inputMethodSession = activeInputMethodSession
        if (inputMethodSession != null) {
            activeInputMethodSession = inputMethodSession.copy(
                isNativeTextInputFocused = false,
                isSoftwareKeyboardVisible = false,
            )
            return true
        }
        return false
    }

    internal fun sendEditCommands(commands: List<EditCommand>): Boolean {
        debugTextInput {
            "sendEditCommands commands=${commands.debugString()} " +
                "legacyActive=${activeInputSession != null} " +
                "inputMethodActive=${activeInputMethodSession != null}"
        }
        activeInputSession?.let { session ->
            session.onEditCommand(commands)
            debugTextInput { "sendEditCommands delivered=legacy" }
            return true
        }
        activeInputMethodSession?.request?.onEditCommand?.invoke(commands)
        val delivered = activeInputMethodSession != null
        debugTextInput { "sendEditCommands delivered=inputMethod result=$delivered" }
        return delivered
    }

    internal fun commitText(text: String, newCursorPosition: Int = 1): Boolean =
        sendEditCommands(listOf(CommitTextCommand(text, newCursorPosition))).also { result ->
            debugTextInput {
                "commitText text=${text.debugForLog()} newCursorPosition=$newCursorPosition " +
                    "result=$result coreTextActive=$isCoreTextInputActive"
            }
        }

    internal fun setComposingText(text: String, newCursorPosition: Int = 1): Boolean =
        sendEditCommands(listOf(SetComposingTextCommand(text, newCursorPosition)))

    internal fun setComposingRegion(start: Int, end: Int): Boolean =
        sendEditCommands(listOf(SetComposingRegionCommand(start, end)))

    internal fun finishComposingText(): Boolean =
        sendEditCommands(listOf(FinishComposingTextCommand()))

    internal fun setSelection(start: Int, end: Int): Boolean =
        sendEditCommands(listOf(SetSelectionCommand(start, end)))

    internal fun deleteSurroundingText(lengthBeforeCursor: Int, lengthAfterCursor: Int): Boolean =
        sendEditCommands(
            listOf(DeleteSurroundingTextCommand(lengthBeforeCursor, lengthAfterCursor))
        )

    internal fun backspace(): Boolean =
        sendEditCommands(listOf(BackspaceCommand()))

    internal fun performImeAction(action: ImeAction): Boolean {
        activeInputSession?.let { session ->
            session.onImeActionPerformed(action)
            return true
        }
        val onImeAction = activeInputMethodSession?.request?.onImeAction ?: return false
        onImeAction(action)
        return true
    }

    internal fun resetForTest() {
        nativeBridge.disposeCoreTextSession()
        activeInputSession = null
        activeInputMethodSession = null
        unregisterRootToScreenMapper(rootToScreenMapperOwner)
        unregisterInputPaneController(inputPaneControllerOwner)
    }

    internal fun onWindowFocusChanged(isFocused: Boolean) {
        nativeBridge.onWindowFocusChanged(isFocused)
    }

    internal fun registerRootToScreenMapper(
        owner: Any,
        mapper: (Offset) -> Offset,
        screenMapperReady: () -> Boolean = { true },
        viewportMapper: (Offset) -> Offset = { it },
        viewportBoundsInRoot: () -> Rect? = { null },
    ) {
        rootToScreenMapperOwner = owner
        rootToScreenMapper = mapper
        rootToScreenMapperReady = screenMapperReady
        rootToViewportMapper = viewportMapper
        rootViewportBoundsInRoot = viewportBoundsInRoot
    }

    internal fun unregisterRootToScreenMapper(owner: Any?) {
        if (owner != null && rootToScreenMapperOwner !== owner) {
            return
        }
        rootToScreenMapperOwner = null
        rootToScreenMapper = { it }
        rootToScreenMapperReady = { false }
        rootToViewportMapper = { it }
        rootViewportBoundsInRoot = { null }
    }

    internal fun registerInputPaneController(
        owner: Any,
        controller: WinUIInputPaneController,
    ) {
        inputPaneControllerOwner = owner
        inputPaneController = controller
    }

    internal fun unregisterInputPaneController(owner: Any?) {
        if (owner != null && inputPaneControllerOwner !== owner) {
            return
        }
        inputPaneControllerOwner = null
        inputPaneController = null
    }

    internal fun mapRootOffsetToScreen(offset: Offset): Offset =
        rootToScreenMapper(offset)

    internal fun mapRootOffsetToViewport(offset: Offset): Offset =
        rootToViewportMapper(offset)

    internal val hasRootToScreenMapper: Boolean
        get() = rootToScreenMapperOwner != null

    internal val isRootToScreenMapperReady: Boolean
        get() = hasRootToScreenMapper && rootToScreenMapperReady()

    internal val currentRootViewportBoundsOnScreen: Rect?
        get() = rootViewportBoundsInRoot()?.toScreenRect()

    internal fun startInputMethod(request: PlatformTextInputMethodRequest) {
        nativeBridge.disposeCoreTextSession()
        val textLayoutBoundsInRoot = request.textLayoutBoundsInRoot()
        val value = request.value()
        debugTextInput {
            "startInputMethod request=${request.debugIdentity()} value=${value.debugString()} " +
                "imeOptions=${request.imeOptions.debugString()} " +
                "boundsInRoot=${textLayoutBoundsInRoot?.debugString()} " +
                "hasRootToScreenMapper=$hasRootToScreenMapper"
        }
        activeInputMethodSession = WinUITextInputMethodSessionState(
            request = request,
            value = value,
            isSoftwareKeyboardVisible = true,
            textLayoutBoundsInRoot = textLayoutBoundsInRoot,
            coreTextLayoutSnapshot = textLayoutBoundsInRoot?.toCoreTextLayoutSnapshot(),
        )
        nativeBridge.attachCoreTextForCurrentInput()
    }

    internal fun requestTextLayoutBoundsInRoot(
        request: PlatformTextInputMethodRequest,
    ): WinUITextLayoutBounds? = request.textLayoutBoundsInRoot()

    internal fun updateInputMethodState(
        request: PlatformTextInputMethodRequest,
        newValue: TextFieldValue,
    ) {
        val session = activeInputMethodSession ?: return
        if (session.request !== request || session.value == newValue) return
        val textLayoutBoundsInRoot = request.textLayoutBoundsInRoot()
        val coreTextLayoutSnapshot = textLayoutBoundsInRoot?.toCoreTextLayoutSnapshot()
        debugTextInput {
            "updateInputMethodState request=${request.debugIdentity()} " +
                "old=${session.value.debugString()} new=${newValue.debugString()} " +
                "boundsInRoot=${textLayoutBoundsInRoot?.debugString()} " +
                "coreTextBounds=${coreTextLayoutSnapshot?.debugString()}"
        }
        activeInputMethodSession = session.copy(
            oldValue = session.value,
            value = newValue,
            textLayoutBoundsInRoot = textLayoutBoundsInRoot,
            coreTextLayoutSnapshot = coreTextLayoutSnapshot,
        )
        nativeBridge.updateCoreTextState(session.value, newValue)
    }

    internal fun updateInputMethodLayout(
        request: PlatformTextInputMethodRequest,
        textLayoutBoundsInRoot: WinUITextLayoutBounds?,
    ) {
        val session = activeInputMethodSession ?: return
        if (session.request !== request) return
        val coreTextLayoutSnapshot = textLayoutBoundsInRoot?.toCoreTextLayoutSnapshot()
        if (
            session.textLayoutBoundsInRoot == textLayoutBoundsInRoot &&
            session.coreTextLayoutSnapshot == coreTextLayoutSnapshot
        ) {
            return
        }
        debugTextInput {
            "updateInputMethodLayout request=${request.debugIdentity()} " +
                "boundsInRoot=${textLayoutBoundsInRoot?.debugString()} " +
                "coreTextBounds=${coreTextLayoutSnapshot?.debugString()}"
        }
        activeInputMethodSession = session.copy(
            textLayoutBoundsInRoot = textLayoutBoundsInRoot,
            coreTextLayoutSnapshot = coreTextLayoutSnapshot,
        )
        nativeBridge.notifyCoreTextLayoutChanged()
    }

    internal fun stopInputMethod(request: PlatformTextInputMethodRequest) {
        if (activeInputMethodSession?.request === request) {
            debugTextInput { "stopInputMethod request=${request.debugIdentity()}" }
            nativeBridge.disposeCoreTextSession()
            activeInputMethodSession = null
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun PlatformTextInputMethodRequest.textLayoutBoundsInRoot(): WinUITextLayoutBounds? {
    val textFieldBounds = textFieldRectInRoot()
    val innerBounds = focusedRectInRoot() ?: textClippingRectInRoot() ?: textFieldBounds
    val decorationBounds = textFieldBounds ?: textClippingRectInRoot() ?: innerBounds
    return if (innerBounds != null && decorationBounds != null) {
        WinUITextLayoutBounds(
            innerTextFieldBounds = innerBounds,
            decorationBoxBounds = decorationBounds,
        )
    } else {
        null
    }
}

private fun WinUITextLayoutBounds.toScreenBounds(): WinUITextLayoutBounds =
    WinUITextLayoutBounds(
        innerTextFieldBounds = innerTextFieldBounds.toScreenRect(),
        decorationBoxBounds = decorationBoxBounds.toScreenRect(),
    )

private fun WinUITextLayoutBounds.toViewportBounds(): WinUITextLayoutBounds =
    WinUITextLayoutBounds(
        innerTextFieldBounds = innerTextFieldBounds.toViewportRect(),
        decorationBoxBounds = decorationBoxBounds.toViewportRect(),
    )

private fun WinUITextLayoutBounds.toCoreTextLayoutSnapshot(): WinUICoreTextLayoutSnapshot =
    WinUICoreTextLayoutSnapshot(
        layoutBounds = if (WinUIPlatformTextInputService.isRootToScreenMapperReady) {
            toScreenBounds()
        } else {
            null
        },
        visualBounds = toViewportBounds(),
    )

private fun Rect.toScreenRect(): Rect {
    val topLeft = WinUIPlatformTextInputService.mapRootOffsetToScreen(topLeft)
    val bottomRight = WinUIPlatformTextInputService.mapRootOffsetToScreen(bottomRight)
    return Rect(
        left = minOf(topLeft.x, bottomRight.x),
        top = minOf(topLeft.y, bottomRight.y),
        right = maxOf(topLeft.x, bottomRight.x),
        bottom = maxOf(topLeft.y, bottomRight.y),
    )
}

private fun Rect.toViewportRect(): Rect {
    val topLeft = WinUIPlatformTextInputService.mapRootOffsetToViewport(topLeft)
    val bottomRight = WinUIPlatformTextInputService.mapRootOffsetToViewport(bottomRight)
    return Rect(
        left = minOf(topLeft.x, bottomRight.x),
        top = minOf(topLeft.y, bottomRight.y),
        right = maxOf(topLeft.x, bottomRight.x),
        bottom = maxOf(topLeft.y, bottomRight.y),
    )
}

internal class WinUINativeTextInputBridge(
    private val textInputService: WinUIPlatformTextInputService,
) {
    private var coreTextSession: WinUICoreTextInputSessionHandle? = null

    val isFocused: Boolean
        get() = textInputService.isNativeTextInputFocused

    val isCoreTextSessionActive: Boolean
        get() = coreTextSession != null

    val isCoreTextCompositionActive: Boolean
        get() = coreTextSession?.isCompositionActive == true

    fun enterFocus(): Boolean =
        textInputService.enterNativeTextInputFocus()

    fun exitFocus(): Boolean =
        textInputService.exitNativeTextInputFocus().also { didExit ->
            if (didExit) {
                coreTextSession?.notifyFocusLeave()
            }
        }

    fun attachCoreTextForCurrentInput(
        notifyNativeFocus: Boolean = true,
    ): Boolean {
        if (!textInputService.hasRootToScreenMapper) {
            debugTextInput { "CoreText attach skipped: no root-to-screen mapper" }
            return false
        }
        if (!isCoreTextEnabled()) {
            return false
        }
        val value = textInputService.currentValue
        if (value == null) {
            debugTextInput { "CoreText attach skipped: no current TextFieldValue" }
            return false
        }
        val imeOptions = textInputService.currentImeOptions
        if (imeOptions == null) {
            debugTextInput { "CoreText attach skipped: no current ImeOptions" }
            return false
        }
        return attachCoreTextForCurrentInput(
            editContext = null,
            initialValue = value,
            imeOptions = imeOptions,
            notifyNativeFocus = notifyNativeFocus,
        )
    }

    internal fun attachCoreTextForCurrentInput(
        editContext: WinUICoreTextEditContext,
        notifyNativeFocus: Boolean = true,
    ): Boolean {
        if (!isCoreTextEnabled()) {
            return false
        }
        val value = textInputService.currentValue
        if (value == null) {
            debugTextInput { "CoreText fake attach skipped: no current TextFieldValue" }
            return false
        }
        val imeOptions = textInputService.currentImeOptions
        if (imeOptions == null) {
            debugTextInput { "CoreText fake attach skipped: no current ImeOptions" }
            return false
        }
        return attachCoreTextForCurrentInput(
            editContext = editContext,
            initialValue = value,
            imeOptions = imeOptions,
            notifyNativeFocus = notifyNativeFocus,
        )
    }

    private fun attachCoreTextForCurrentInput(
        editContext: WinUICoreTextEditContext?,
        initialValue: TextFieldValue,
        imeOptions: ImeOptions,
        notifyNativeFocus: Boolean,
    ): Boolean {
        debugTextInput {
            "CoreText attach begin fake=${editContext != null} " +
                "initial=${initialValue.debugString()} " +
                "imeOptions=${imeOptions.debugString()} " +
                "layout=${textInputService.currentCoreTextLayoutSnapshot?.debugString()}"
        }
        disposeCoreTextSession()
        val session = runCatching {
            if (editContext != null) {
                WinUICoreTextInputSession.create(
                    initialValue = initialValue,
                    imeOptions = imeOptions,
                    editContext = editContext,
                    currentValue = { textInputService.currentValue },
                    currentLayoutBounds = { textInputService.currentCoreTextLayoutSnapshot },
                    dispatchEditCommands = textInputService::sendEditCommands,
                )
            } else {
                WinUICoreTextInputSession.create(
                    initialValue = initialValue,
                    imeOptions = imeOptions,
                    currentValue = { textInputService.currentValue },
                    currentLayoutBounds = { textInputService.currentCoreTextLayoutSnapshot },
                    dispatchEditCommands = textInputService::sendEditCommands,
                )
            }
        }.onFailure { throwable ->
            debugTextInput {
                "CoreText attach failed: ${throwable.stackTraceToString()}"
            }
        }.getOrNull() ?: return false

        coreTextSession = session
        textInputService.enterNativeTextInputFocus()
        if (notifyNativeFocus) {
            session.notifyFocusEnter()
        }
        debugTextInput {
            "CoreText attach success focused=${textInputService.isNativeTextInputFocused} " +
                "notifyNativeFocus=$notifyNativeFocus"
        }
        return true
    }

    internal fun updateCoreTextState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        coreTextSession?.updateState(oldValue, newValue)
    }

    internal fun notifyCoreTextLayoutChanged() {
        debugTextInput { "CoreText notifyLayoutChanged active=${coreTextSession != null}" }
        coreTextSession?.notifyLayoutChanged()
    }

    internal fun onWindowFocusChanged(isFocused: Boolean) {
        val session = coreTextSession ?: return
        debugTextInput { "CoreText windowFocusChanged focused=$isFocused" }
        if (isFocused) {
            if (textInputService.enterNativeTextInputFocus()) {
                session.notifyFocusEnter()
                session.notifyLayoutChanged()
            }
        } else {
            session.notifyFocusLeave()
            textInputService.exitNativeTextInputFocus()
        }
    }

    internal fun disposeCoreTextSession() {
        val session = coreTextSession ?: return
        debugTextInput { "CoreText dispose" }
        coreTextSession = null
        session.notifyFocusLeave()
        session.dispose()
        textInputService.exitNativeTextInputFocus()
    }

    fun commitText(text: String, newCursorPosition: Int = 1): Boolean =
        textInputService.commitText(text, newCursorPosition)

    fun setComposingText(text: String, newCursorPosition: Int = 1): Boolean =
        textInputService.setComposingText(text, newCursorPosition)

    fun setComposingRegion(start: Int, end: Int): Boolean =
        textInputService.setComposingRegion(start, end)

    fun finishComposingText(): Boolean =
        textInputService.finishComposingText()

    fun setSelection(start: Int, end: Int): Boolean =
        textInputService.setSelection(start, end)

    fun deleteSurroundingText(lengthBeforeCursor: Int, lengthAfterCursor: Int): Boolean =
        textInputService.deleteSurroundingText(lengthBeforeCursor, lengthAfterCursor)

    fun backspace(): Boolean =
        textInputService.backspace()

    fun performImeAction(action: ImeAction): Boolean =
        textInputService.performImeAction(action)

    private fun isCoreTextEnabled(): Boolean {
        if (winUISystemBooleanProperty(CoreTextInputDisabledProperty)) {
            debugTextInput { "CoreText attach skipped: disabled by system property" }
            return false
        }
        return true
    }

    private companion object {
        const val CoreTextInputDisabledProperty = "compose.winui.textInput.coreText.disabled"
    }
}

private data class WinUITextInputSessionState(
    val value: TextFieldValue,
    val imeOptions: ImeOptions,
    val onEditCommand: (List<EditCommand>) -> Unit,
    val onImeActionPerformed: (ImeAction) -> Unit,
    val oldValue: TextFieldValue? = null,
    val isSoftwareKeyboardVisible: Boolean = false,
    val isNativeTextInputFocused: Boolean = false,
    val textFieldValue: TextFieldValue? = null,
    val offsetMapping: OffsetMapping? = null,
    val textLayoutResult: TextLayoutResult? = null,
    val innerTextFieldBounds: Rect? = null,
    val decorationBoxBounds: Rect? = null,
    val textLayoutBoundsInRoot: WinUITextLayoutBounds? = null,
    val coreTextLayoutSnapshot: WinUICoreTextLayoutSnapshot? = null,
)

private data class WinUITextInputMethodSessionState(
    val request: PlatformTextInputMethodRequest,
    val value: TextFieldValue,
    val isSoftwareKeyboardVisible: Boolean,
    val isNativeTextInputFocused: Boolean = false,
    val oldValue: TextFieldValue? = null,
    val textLayoutBoundsInRoot: WinUITextLayoutBounds? = null,
    val coreTextLayoutSnapshot: WinUICoreTextLayoutSnapshot? = null,
)

internal data class WinUITextLayoutBounds(
    val innerTextFieldBounds: Rect,
    val decorationBoxBounds: Rect,
)

internal val WinUITextLayoutBounds.hasUsableBounds: Boolean
    get() = innerTextFieldBounds.width >= 0f &&
        innerTextFieldBounds.height > 0f &&
        decorationBoxBounds.width > 0f &&
        decorationBoxBounds.height > 0f

private inline fun debugTextInput(message: () -> String) {
    if (winUISystemBooleanProperty("compose.winui.textInput.debug")) {
        winUIDebugLog("text-input", message())
    }
}

private fun PlatformTextInputMethodRequest.debugIdentity(): String =
    "${this::class.simpleName}@${hashCode().toString(16)}"

internal fun ImeOptions.debugString(): String =
    "ImeOptions(keyboardType=$keyboardType, imeAction=$imeAction, singleLine=$singleLine)"

internal fun TextFieldValue.debugString(): String =
    "TextFieldValue(text=${text.debugForLog()}, selection=$selection, composition=$composition)"

internal fun WinUITextLayoutBounds.debugString(): String =
    "WinUITextLayoutBounds(inner=$innerTextFieldBounds, decoration=$decorationBoxBounds)"

private fun WinUICoreTextLayoutSnapshot.debugString(): String =
    "WinUICoreTextLayoutSnapshot(layout=${layoutBounds?.debugString()}, " +
        "visual=${visualBounds?.debugString()})"

private fun List<EditCommand>.debugString(): String =
    joinToString(prefix = "[", postfix = "]") { command -> command.debugString() }

private fun EditCommand.debugString(): String =
    when (this) {
        is CommitTextCommand -> "CommitText(text=${text.debugForLog()}, cursor=$newCursorPosition)"
        is SetComposingTextCommand ->
            "SetComposingText(text=${text.debugForLog()}, cursor=$newCursorPosition)"
        is SetComposingRegionCommand -> "SetComposingRegion(start=$start, end=$end)"
        is SetSelectionCommand -> "SetSelection(start=$start, end=$end)"
        is DeleteSurroundingTextCommand ->
            "DeleteSurroundingText(before=$lengthBeforeCursor, after=$lengthAfterCursor)"
        is FinishComposingTextCommand -> "FinishComposingText"
        is BackspaceCommand -> "Backspace"
        else -> this::class.simpleName ?: toString()
    }

internal fun String.debugForLog(maxLength: Int = 80): String {
    val escaped = buildString {
        this@debugForLog.take(maxLength).forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        if (this@debugForLog.length > maxLength) {
            append("...")
        }
    }
    return "\"$escaped\""
}
