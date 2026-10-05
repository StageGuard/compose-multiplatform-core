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
import androidx.compose.ui.WinUISkikoTestBase
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.input.BackspaceCommand
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.SetComposingRegionCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlin.test.BeforeTest
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import windows.ui.text.core.CoreTextFormatUpdatingResult
import windows.ui.text.core.CoreTextInputPaneDisplayPolicy
import windows.ui.text.core.CoreTextInputScope
import windows.ui.text.core.CoreTextRange
import windows.ui.text.core.CoreTextSelectionUpdatingResult
import windows.ui.text.core.CoreTextTextUpdatingResult

class WinUIPlatformTextInputServiceTest : WinUISkikoTestBase() {
    @BeforeTest
    fun setUp() {
        System.setProperty(CoreTextInputDisabledProperty, "true")
    }

    @AfterTest
    fun tearDown() {
        WinUIPlatformTextInputService.resetForTest()
        System.clearProperty(CoreTextInputDisabledProperty)
    }

    @Test
    fun startUpdateAndStopInputTracksCurrentSessionState() {
        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue(
                text = "initial",
                selection = TextRange(1, 3),
                composition = TextRange(0, 2),
            ),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )

        assertTrue(WinUIPlatformTextInputService.isInputActive)
        assertEquals(
            TextFieldValue(
                text = "initial",
                selection = TextRange(1, 3),
                composition = TextRange(0, 2),
            ),
            WinUIPlatformTextInputService.currentValue,
        )

        WinUIPlatformTextInputService.updateState(
            oldValue = TextFieldValue(
                text = "initial",
                selection = TextRange(1, 3),
                composition = TextRange(0, 2),
            ),
            newValue = TextFieldValue(
                text = "updated",
                selection = TextRange(2),
                composition = TextRange(1, 4),
            ),
        )

        assertEquals(
            TextFieldValue(
                text = "initial",
                selection = TextRange(1, 3),
                composition = TextRange(0, 2),
            ),
            WinUIPlatformTextInputService.previousValue,
        )
        assertEquals(
            TextFieldValue(
                text = "updated",
                selection = TextRange(2),
                composition = TextRange(1, 4),
            ),
            WinUIPlatformTextInputService.currentValue,
        )

        WinUIPlatformTextInputService.stopInput()

        assertFalse(WinUIPlatformTextInputService.isInputActive)
        assertEquals(null, WinUIPlatformTextInputService.currentValue)
        assertEquals(null, WinUIPlatformTextInputService.previousValue)
    }

    @Test
    fun editCommandsAndImeActionsDelegateToActiveSessionCallbacks() {
        val editCommandBatches = mutableListOf<List<EditCommand>>()
        val imeActions = mutableListOf<ImeAction>()
        val commit = CommitTextCommand("hello", 1)
        val selection = SetSelectionCommand(1, 3)

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue(""),
            imeOptions = ImeOptions.Default,
            onEditCommand = { editCommandBatches += it },
            onImeActionPerformed = { imeActions += it },
        )

        assertTrue(WinUIPlatformTextInputService.sendEditCommands(listOf(commit, selection)))
        assertTrue(WinUIPlatformTextInputService.performImeAction(ImeAction.Done))

        assertEquals(1, editCommandBatches.size)
        assertSame(commit, editCommandBatches.single()[0])
        assertSame(selection, editCommandBatches.single()[1])
        assertEquals(listOf(ImeAction.Done), imeActions)

        WinUIPlatformTextInputService.stopInput()

        assertFalse(WinUIPlatformTextInputService.sendEditCommands(listOf(commit)))
        assertFalse(WinUIPlatformTextInputService.performImeAction(ImeAction.Search))
    }

    @Test
    fun nativeTextInputHelpersDelegateStandardEditCommands() {
        val editCommandBatches = mutableListOf<List<EditCommand>>()

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue(""),
            imeOptions = ImeOptions.Default,
            onEditCommand = { editCommandBatches += it },
            onImeActionPerformed = {},
        )

        assertTrue(WinUIPlatformTextInputService.setComposingText("draft"))
        assertTrue(WinUIPlatformTextInputService.setComposingRegion(1, 4))
        assertTrue(WinUIPlatformTextInputService.commitText("done"))
        assertTrue(WinUIPlatformTextInputService.setSelection(2, 5))
        assertTrue(WinUIPlatformTextInputService.deleteSurroundingText(1, 2))
        assertTrue(WinUIPlatformTextInputService.backspace())
        assertTrue(WinUIPlatformTextInputService.finishComposingText())

        assertEquals(
            listOf(
                listOf(SetComposingTextCommand("draft", 1)),
                listOf(SetComposingRegionCommand(1, 4)),
                listOf(CommitTextCommand("done", 1)),
                listOf(SetSelectionCommand(2, 5)),
                listOf(DeleteSurroundingTextCommand(1, 2)),
                listOf(BackspaceCommand()),
                listOf(FinishComposingTextCommand()),
            ),
            editCommandBatches,
        )

        WinUIPlatformTextInputService.stopInput()

        assertFalse(WinUIPlatformTextInputService.commitText("ignored"))
        assertFalse(WinUIPlatformTextInputService.setComposingText("ignored"))
        assertFalse(WinUIPlatformTextInputService.setComposingRegion(0, 1))
        assertFalse(WinUIPlatformTextInputService.finishComposingText())
        assertFalse(WinUIPlatformTextInputService.setSelection(0, 1))
        assertFalse(WinUIPlatformTextInputService.deleteSurroundingText(1, 0))
        assertFalse(WinUIPlatformTextInputService.backspace())
    }

    @Test
    fun nativeTextInputBridgeTracksFocusAndDelegatesEditingEvents() {
        val editCommandBatches = mutableListOf<List<EditCommand>>()
        val imeActions = mutableListOf<ImeAction>()
        val bridge = WinUIPlatformTextInputService.nativeBridge

        assertFalse(bridge.enterFocus())
        assertFalse(bridge.commitText("ignored"))
        assertFalse(bridge.performImeAction(ImeAction.Done))

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue(""),
            imeOptions = ImeOptions.Default,
            onEditCommand = { editCommandBatches += it },
            onImeActionPerformed = { imeActions += it },
        )

        assertFalse(bridge.isFocused)
        assertTrue(bridge.enterFocus())
        assertTrue(bridge.isFocused)
        assertTrue(WinUIPlatformTextInputService.isNativeTextInputFocused)

        assertTrue(bridge.setComposingText("draft", newCursorPosition = 2))
        assertTrue(bridge.setComposingRegion(0, 5))
        assertTrue(bridge.commitText("done"))
        assertTrue(bridge.setSelection(1, 3))
        assertTrue(bridge.deleteSurroundingText(1, 0))
        assertTrue(bridge.backspace())
        assertTrue(bridge.finishComposingText())
        assertTrue(bridge.performImeAction(ImeAction.Search))

        assertEquals(
            listOf(
                listOf(SetComposingTextCommand("draft", 2)),
                listOf(SetComposingRegionCommand(0, 5)),
                listOf(CommitTextCommand("done", 1)),
                listOf(SetSelectionCommand(1, 3)),
                listOf(DeleteSurroundingTextCommand(1, 0)),
                listOf(BackspaceCommand()),
                listOf(FinishComposingTextCommand()),
            ),
            editCommandBatches,
        )
        assertEquals(listOf(ImeAction.Search), imeActions)

        WinUISoftwareKeyboardController.show()
        assertTrue(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)
        assertTrue(bridge.exitFocus())
        assertFalse(bridge.isFocused)
        assertFalse(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)
    }

    @Test
    fun startInputReplacesNativeTextInputBridgeFocusState() {
        val bridge = WinUIPlatformTextInputService.nativeBridge

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("first"),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )
        assertTrue(bridge.enterFocus())
        assertTrue(bridge.isFocused)

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("second"),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )

        assertFalse(bridge.isFocused)
        assertEquals(TextFieldValue("second"), WinUIPlatformTextInputService.currentValue)
    }

    @Test
    fun startInputReplacesPreviousSessionCallbacks() {
        val firstCommands = mutableListOf<List<EditCommand>>()
        val secondCommands = mutableListOf<List<EditCommand>>()

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("first"),
            imeOptions = ImeOptions.Default,
            onEditCommand = { firstCommands += it },
            onImeActionPerformed = {},
        )
        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("second"),
            imeOptions = ImeOptions.Default,
            onEditCommand = { secondCommands += it },
            onImeActionPerformed = {},
        )

        assertTrue(WinUIPlatformTextInputService.commitText("new"))

        assertEquals(emptyList(), firstCommands)
        assertEquals(1, secondCommands.size)
        assertEquals(listOf(CommitTextCommand("new", 1)), secondCommands.single())
        assertEquals(TextFieldValue("second"), WinUIPlatformTextInputService.currentValue)
    }

    @Test
    fun softwareKeyboardControllerDelegatesToActiveTextInputSession() {
        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue(""),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )

        assertFalse(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)

        WinUISoftwareKeyboardController.show()

        assertTrue(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)

        WinUISoftwareKeyboardController.hide()

        assertFalse(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)
    }

    @Test
    fun updateTextLayoutResultStoresRootAndScreenBounds() {
        val mapperOwner = Any()
        WinUIPlatformTextInputService.registerRootToScreenMapper(
            owner = mapperOwner,
            mapper = { offset -> offset + Offset(100f, 200f) },
            viewportBoundsInRoot = { Rect(0f, 0f, 200f, 200f) },
        )
        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("hello"),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )

        WinUIPlatformTextInputService.updateTextLayoutResult(
            textFieldValue = TextFieldValue("hello"),
            offsetMapping = OffsetMapping.Identity,
            textLayoutResult = testTextLayoutResult("hello"),
            textFieldToRootTransform = { it.setTranslate(Offset(5f, 7f)) },
            innerTextFieldBounds = Rect(10f, 20f, 30f, 40f),
            decorationBoxBounds = Rect(0f, 10f, 50f, 60f),
        )

        assertEquals(
            WinUITextLayoutBounds(
                innerTextFieldBounds = Rect(15f, 27f, 35f, 47f),
                decorationBoxBounds = Rect(5f, 17f, 55f, 67f),
            ),
            WinUIPlatformTextInputService.currentTextLayoutBoundsInRoot,
        )
        assertEquals(
            WinUITextLayoutBounds(
                innerTextFieldBounds = Rect(115f, 227f, 135f, 247f),
                decorationBoxBounds = Rect(105f, 217f, 155f, 267f),
            ),
            WinUIPlatformTextInputService.currentTextLayoutBoundsOnScreen,
        )

        WinUIPlatformTextInputService.unregisterRootToScreenMapper(mapperOwner)
    }

    @Test
    fun currentScreenBoundsAreRecomputedFromLatestRootMapper() {
        val mapperOwner = Any()
        WinUIPlatformTextInputService.registerRootToScreenMapper(
            owner = mapperOwner,
            mapper = { offset -> offset + Offset(100f, 200f) },
        )
        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("hello"),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )
        WinUIPlatformTextInputService.updateTextLayoutResult(
            textFieldValue = TextFieldValue("hello"),
            offsetMapping = OffsetMapping.Identity,
            textLayoutResult = testTextLayoutResult("hello"),
            textFieldToRootTransform = { it.setTranslate(Offset(5f, 7f)) },
            innerTextFieldBounds = Rect(10f, 20f, 30f, 40f),
            decorationBoxBounds = Rect(0f, 10f, 50f, 60f),
        )

        WinUIPlatformTextInputService.registerRootToScreenMapper(
            owner = mapperOwner,
            mapper = { offset -> offset + Offset(300f, 400f) },
        )

        assertEquals(
            WinUITextLayoutBounds(
                innerTextFieldBounds = Rect(315f, 427f, 335f, 447f),
                decorationBoxBounds = Rect(305f, 417f, 355f, 467f),
            ),
            WinUIPlatformTextInputService.currentTextLayoutBoundsOnScreen,
        )

        WinUIPlatformTextInputService.unregisterRootToScreenMapper(mapperOwner)
    }

    @Test
    fun currentScreenBoundsAreUnavailableUntilScreenMapperIsReady() {
        val mapperOwner = Any()
        var screenMapperReady = false
        WinUIPlatformTextInputService.registerRootToScreenMapper(
            owner = mapperOwner,
            mapper = { offset -> offset + Offset(100f, 200f) },
            screenMapperReady = { screenMapperReady },
        )
        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("hello"),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )
        WinUIPlatformTextInputService.updateTextLayoutResult(
            textFieldValue = TextFieldValue("hello"),
            offsetMapping = OffsetMapping.Identity,
            textLayoutResult = testTextLayoutResult("hello"),
            textFieldToRootTransform = { it.setTranslate(Offset(5f, 7f)) },
            innerTextFieldBounds = Rect(10f, 20f, 30f, 40f),
            decorationBoxBounds = Rect(0f, 10f, 50f, 60f),
        )

        assertEquals(null, WinUIPlatformTextInputService.currentTextLayoutBoundsOnScreen)

        screenMapperReady = true

        assertEquals(
            WinUITextLayoutBounds(
                innerTextFieldBounds = Rect(115f, 227f, 135f, 247f),
                decorationBoxBounds = Rect(105f, 217f, 155f, 267f),
            ),
            WinUIPlatformTextInputService.currentTextLayoutBoundsOnScreen,
        )

        WinUIPlatformTextInputService.unregisterRootToScreenMapper(mapperOwner)
    }

    @Test
    fun coreTextViewportVisualPixelsUseDeviceIndependentPixels() {
        assertEquals(
            Offset(1920f, 1032.5f),
            rootPixelOffsetToCoreTextViewportVisualPixels(
                Offset(3840f, 2065f),
                densityScale = 2f,
            ),
        )
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun startInputMethodTracksActiveRequestUntilCancelled() = runTest {
        val session = WinUIPlatformTextInputSession(this)
        val request = TestPlatformTextInputMethodRequest()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            session.startInputMethod(request)
        }

        assertTrue(WinUIPlatformTextInputService.isInputMethodActive)
        assertTrue(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)
        assertEquals(request, WinUIPlatformTextInputService.currentInputMethodRequest)

        WinUISoftwareKeyboardController.hide()

        assertFalse(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)

        job.cancelAndJoin()

        assertFalse(WinUIPlatformTextInputService.isInputMethodActive)
        assertEquals(null, WinUIPlatformTextInputService.currentInputMethodRequest)
        assertFalse(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun startInputMethodUsesPlatformTextInputMethodRequestAsPrimarySession() = runTest {
        val editCommandBatches = mutableListOf<List<EditCommand>>()
        val imeActions = mutableListOf<ImeAction>()
        val request = TestPlatformTextInputMethodRequest(
            textValue = { TextFieldValue("request text") },
            imeOptions = ImeOptions.Default.copy(imeAction = ImeAction.Search),
            onEditCommand = { editCommandBatches += it },
            onImeAction = { imeActions += it },
            focusedRectInRoot = { Rect(1f, 2f, 3f, 4f) },
            textFieldRectInRoot = { Rect(0f, 1f, 4f, 5f) },
        )
        val session = WinUIPlatformTextInputSession(this)

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            session.startInputMethod(request)
        }

        assertEquals(TextFieldValue("request text"), WinUIPlatformTextInputService.currentValue)
        assertEquals(
            ImeOptions.Default.copy(imeAction = ImeAction.Search),
            WinUIPlatformTextInputService.currentImeOptions,
        )
        assertEquals(
            WinUITextLayoutBounds(
                innerTextFieldBounds = Rect(1f, 2f, 3f, 4f),
                decorationBoxBounds = Rect(0f, 1f, 4f, 5f),
            ),
            WinUIPlatformTextInputService.currentTextLayoutBoundsInRoot,
        )

        assertTrue(WinUIPlatformTextInputService.commitText("new"))
        assertTrue(WinUIPlatformTextInputService.performImeAction(ImeAction.Search))

        assertEquals(1, editCommandBatches.size)
        assertEquals(listOf(CommitTextCommand("new", 1)), editCommandBatches.single())
        assertEquals(listOf(ImeAction.Search), imeActions)

        job.cancelAndJoin()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun startInputMethodNotifiesCoreTextTextChangesFromAppState() = runTest {
        var textValue by mutableStateOf(TextFieldValue("cat", selection = TextRange(3)))
        val request = TestPlatformTextInputMethodRequest(
            textValue = { textValue },
        )
        val session = WinUIPlatformTextInputSession(this)

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            session.startInputMethod(request)
        }
        runCurrent()

        withCoreTextAvailableForTest {
            val editContext = RecordingCoreTextEditContext()
            assertTrue(WinUIPlatformTextInputService.nativeBridge.attachCoreTextForCurrentInput(editContext))
            editContext.textChanges.clear()

            textValue = TextFieldValue("cart", selection = TextRange(4))
            Snapshot.sendApplyNotifications()
            runCurrent()

            val textChange = editContext.textChanges.single()
            assertCoreTextRangeEquals(CoreTextRange(2, 2), textChange.modifiedRange)
            assertEquals(1, textChange.newLength)
            assertCoreTextRangeEquals(CoreTextRange(4, 4), textChange.newSelection)
        }

        job.cancelAndJoin()
    }

    @Test
    fun coreTextAttachIsEnabledByDefault() {
        System.clearProperty(CoreTextInputDisabledProperty)
        WinUIPlatformTextInputService.startInputMethod(TestPlatformTextInputMethodRequest())

        val editContext = RecordingCoreTextEditContext()

        assertTrue(WinUIPlatformTextInputService.nativeBridge.attachCoreTextForCurrentInput(editContext))
        assertTrue(WinUIPlatformTextInputService.isCoreTextInputActive)
    }

    @Test
    fun coreTextAttachCanBeDisabledByProperty() {
        System.clearProperty(CoreTextInputDisabledProperty)
        System.setProperty(CoreTextInputDisabledProperty, "true")
        WinUIPlatformTextInputService.startInputMethod(TestPlatformTextInputMethodRequest())

        val editContext = RecordingCoreTextEditContext()

        assertFalse(WinUIPlatformTextInputService.nativeBridge.attachCoreTextForCurrentInput(editContext))
        assertFalse(WinUIPlatformTextInputService.nativeBridge.isCoreTextSessionActive)
        assertFalse(WinUIPlatformTextInputService.isCoreTextInputActive)
    }

    @Test
    fun coreTextNotifiesTextAndSelectionOnAppStateChanges() {
        var textValue = TextFieldValue("cat", selection = TextRange(3))
        val request = TestPlatformTextInputMethodRequest(
            textValue = { textValue },
        )
        WinUIPlatformTextInputService.startInputMethod(request)
        withCoreTextAvailableForTest {
            val editContext = RecordingCoreTextEditContext()
            assertTrue(WinUIPlatformTextInputService.nativeBridge.attachCoreTextForCurrentInput(editContext))

            textValue = TextFieldValue("cart", selection = TextRange(4))
            WinUIPlatformTextInputService.updateInputMethodState(request, textValue)
            textValue = TextFieldValue("cart", selection = TextRange(1))
            WinUIPlatformTextInputService.updateInputMethodState(request, textValue)

            val textChange = editContext.textChanges.single()
            assertCoreTextRangeEquals(CoreTextRange(2, 2), textChange.modifiedRange)
            assertEquals(1, textChange.newLength)
            assertCoreTextRangeEquals(CoreTextRange(4, 4), textChange.newSelection)
            assertCoreTextRangeEquals(CoreTextRange(1, 1), editContext.selectionChanges.single())
        }
    }

    @Test
    fun coreTextFollowsWindowFocusWithoutReplacingInputSession() {
        WinUIPlatformTextInputService.startInputMethod(TestPlatformTextInputMethodRequest())
        withCoreTextAvailableForTest {
            val editContext = RecordingCoreTextEditContext()
            assertTrue(WinUIPlatformTextInputService.nativeBridge.attachCoreTextForCurrentInput(editContext))
            assertTrue(WinUIPlatformTextInputService.isNativeTextInputFocused)
            assertEquals(1, editContext.focusEnterCount)

            WinUIPlatformTextInputService.onWindowFocusChanged(false)

            assertFalse(WinUIPlatformTextInputService.isNativeTextInputFocused)
            assertEquals(1, editContext.focusLeaveCount)

            WinUIPlatformTextInputService.onWindowFocusChanged(true)

            assertTrue(WinUIPlatformTextInputService.isNativeTextInputFocused)
            assertEquals(2, editContext.focusEnterCount)
        }
    }

    @Test
    fun updateInputMethodStateRefreshesLayoutBeforeCoreTextSelectionNotification() {
        var textValue = TextFieldValue("hello", selection = TextRange(5))
        val request = TestPlatformTextInputMethodRequest(
            textValue = { textValue },
            focusedRectInRoot = {
                if (textValue.selection.min == 1) {
                    Rect(10f, 20f, 10f, 40f)
                } else {
                    Rect(50f, 20f, 50f, 40f)
                }
            },
            textFieldRectInRoot = { Rect(0f, 10f, 100f, 50f) },
        )

        WinUIPlatformTextInputService.startInputMethod(request)
        withCoreTextAvailableForTest {
            val editContext = RecordingCoreTextEditContext()
            assertTrue(WinUIPlatformTextInputService.nativeBridge.attachCoreTextForCurrentInput(editContext))

            textValue = TextFieldValue("hello", selection = TextRange(1))
            WinUIPlatformTextInputService.updateInputMethodState(request, textValue)

            assertCoreTextRangeEquals(CoreTextRange(1, 1), editContext.selectionChanges.single())
            assertEquals(
                null,
                editContext.layoutRequestsDuringSelectionChange.single().layoutTextBounds,
            )
            assertEquals(
                null,
                editContext.layoutRequestsDuringSelectionChange.single().visualTextBounds,
            )
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun startInputMethodNotifiesCoreTextWhenRequestLayoutChanges() = runTest {
        var focusedRect by mutableStateOf(Rect(1f, 2f, 3f, 4f))
        var textFieldRect by mutableStateOf(Rect(0f, 1f, 4f, 5f))
        val request = TestPlatformTextInputMethodRequest(
            focusedRectInRoot = { focusedRect },
            textFieldRectInRoot = { textFieldRect },
        )
        val session = WinUIPlatformTextInputSession(this)

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            session.startInputMethod(request)
        }
        runCurrent()

        withCoreTextAvailableForTest {
            val editContext = RecordingCoreTextEditContext()
            assertTrue(WinUIPlatformTextInputService.nativeBridge.attachCoreTextForCurrentInput(editContext))
            editContext.layoutChangedCount = 0

            focusedRect = Rect(11f, 12f, 13f, 14f)
            textFieldRect = Rect(10f, 11f, 14f, 15f)
            Snapshot.sendApplyNotifications()
            runCurrent()

            assertEquals(1, editContext.layoutChangedCount)
            assertEquals(
                WinUITextLayoutBounds(
                    innerTextFieldBounds = Rect(11f, 12f, 13f, 14f),
                    decorationBoxBounds = Rect(10f, 11f, 14f, 15f),
                ),
                WinUIPlatformTextInputService.currentTextLayoutBoundsInRoot,
            )
        }

        job.cancelAndJoin()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun startInputMethodCancelsPreviousRequestBeforeStartingNext() = runTest {
        val session = WinUIPlatformTextInputSession(this)
        val firstRequest = TestPlatformTextInputMethodRequest()
        val secondRequest = TestPlatformTextInputMethodRequest()

        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            session.startInputMethod(firstRequest)
        }

        assertEquals(firstRequest, WinUIPlatformTextInputService.currentInputMethodRequest)

        val second = launch(start = CoroutineStart.UNDISPATCHED) {
            session.startInputMethod(secondRequest)
        }
        runCurrent()

        assertFalse(first.isActive)
        assertTrue(second.isActive)
        assertTrue(WinUIPlatformTextInputService.isInputMethodActive)
        assertEquals(secondRequest, WinUIPlatformTextInputService.currentInputMethodRequest)

        second.cancelAndJoin()

        assertFalse(WinUIPlatformTextInputService.isInputMethodActive)
    }
}

private const val CoreTextInputDisabledProperty = "compose.winui.textInput.coreText.disabled"
private inline fun <T> withCoreTextAvailableForTest(block: () -> T): T {
    val previousDisabled = System.getProperty(CoreTextInputDisabledProperty)
    System.clearProperty(CoreTextInputDisabledProperty)
    return try {
        block()
    } finally {
        restoreSystemProperty(CoreTextInputDisabledProperty, previousDisabled)
    }
}

private fun restoreSystemProperty(name: String, value: String?) {
    if (value == null) {
        System.clearProperty(name)
    } else {
        System.setProperty(name, value)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private class TestPlatformTextInputMethodRequest(
    private val textValue: () -> TextFieldValue = { TextFieldValue("") },
    override val imeOptions: ImeOptions = ImeOptions.Default,
    override val onEditCommand: (List<EditCommand>) -> Unit = {},
    override val onImeAction: ((ImeAction) -> Unit)? = null,
    override val focusedRectInRoot: () -> Rect? = { null },
    override val textFieldRectInRoot: () -> Rect? = { null },
    override val textClippingRectInRoot: () -> Rect? = { null },
) : PlatformTextInputMethodRequest {
    override val value: () -> TextFieldValue = textValue
    override val state: TextEditorState = object : TextEditorState {
        override val text: String get() = textValue().text
        override val selection: TextRange get() = textValue().selection
        override val composition: TextRange? get() = textValue().composition
        override val length: Int get() = textValue().text.length
        override fun get(index: Int): Char = textValue().text[index]
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            textValue().text.subSequence(startIndex, endIndex)
        override fun toString(): String = textValue().text
    }
    override val textLayoutResult: () -> TextLayoutResult? = { null }
    override val unclippedTextOffsetInRoot: () -> Offset? = { null }
    override val editText: (TextEditingScope.() -> Unit) -> Unit = {}
}

private fun assertCoreTextRangeEquals(expected: CoreTextRange, actual: CoreTextRange) {
    assertEquals(expected.startCaretPosition, actual.startCaretPosition)
    assertEquals(expected.endCaretPosition, actual.endCaretPosition)
}

private data class RecordingTextChange(
    val modifiedRange: CoreTextRange,
    val newLength: Int,
    val newSelection: CoreTextRange,
)

private class RecordingCoreTextEditContext : WinUICoreTextEditContext {
    override var name: String = ""
    override var inputScope: CoreTextInputScope = CoreTextInputScope.Default
    override var inputPaneDisplayPolicy: CoreTextInputPaneDisplayPolicy =
        CoreTextInputPaneDisplayPolicy.Manual
    var layoutChangedCount = 0
    val textChanges = mutableListOf<RecordingTextChange>()
    val selectionChanges = mutableListOf<CoreTextRange>()
    val layoutRequestsDuringSelectionChange = mutableListOf<RecordingCoreTextLayoutRequest>()
    var focusEnterCount = 0
        private set
    var focusLeaveCount = 0
        private set

    private var layoutRequested: ((WinUICoreTextLayoutRequest) -> Unit)? = null

    override fun addTextRequested(
        handler: (WinUICoreTextTextRequest) -> Unit,
    ): WinUICoreTextEventToken = token()

    override fun addSelectionRequested(
        handler: (WinUICoreTextSelectionRequest) -> Unit,
    ): WinUICoreTextEventToken = token()

    override fun addLayoutRequested(
        handler: (WinUICoreTextLayoutRequest) -> Unit,
    ): WinUICoreTextEventToken {
        layoutRequested = handler
        return token()
    }

    override fun addTextUpdating(
        handler: (WinUICoreTextTextUpdatingEvent) -> Unit,
    ): WinUICoreTextEventToken = token()

    override fun addSelectionUpdating(
        handler: (WinUICoreTextSelectionUpdatingEvent) -> Unit,
    ): WinUICoreTextEventToken = token()

    override fun addFormatUpdating(
        handler: (WinUICoreTextFormatUpdatingEvent) -> Unit,
    ): WinUICoreTextEventToken = token()

    override fun addCompositionStarted(handler: () -> Unit): WinUICoreTextEventToken = token()

    override fun addCompositionCompleted(handler: () -> Unit): WinUICoreTextEventToken = token()

    override fun removeEventHandler(token: WinUICoreTextEventToken) {
        token.remove()
    }

    override fun notifyFocusEnter() {
        focusEnterCount += 1
    }

    override fun notifyFocusLeave() {
        focusLeaveCount += 1
    }

    override fun notifyTextChanged(
        modifiedRange: CoreTextRange,
        newLength: Int,
        newSelection: CoreTextRange,
    ) {
        textChanges += RecordingTextChange(
            modifiedRange = modifiedRange,
            newLength = newLength,
            newSelection = newSelection,
        )
    }

    override fun notifySelectionChanged(selection: CoreTextRange) {
        selectionChanges += selection
        val request = RecordingCoreTextLayoutRequest()
        layoutRequested?.invoke(request)
        layoutRequestsDuringSelectionChange += request
    }

    override fun notifyLayoutChanged() {
        layoutChangedCount += 1
    }

    private fun token(): WinUICoreTextEventToken = WinUICoreTextEventToken {}
}

private class RecordingCoreTextLayoutRequest(
    override val isCanceled: Boolean = false,
) : WinUICoreTextLayoutRequest {
    var layoutTextBounds: Rect? = null
        private set
    var visualTextBounds: Rect? = null
        private set

    override fun setLayoutBounds(bounds: WinUICoreTextLayoutSnapshot) {
        layoutTextBounds = bounds.layoutBounds?.innerTextFieldBounds
        visualTextBounds = bounds.visualBounds?.innerTextFieldBounds
    }
}

private fun testTextLayoutResult(text: String): TextLayoutResult {
    val annotatedString = AnnotatedString(text)
    val density = Density(1f)
    val constraints = Constraints(maxWidth = 100)
    val fontFamilyResolver = createFontFamilyResolver()
    val layoutInput = TextLayoutInput(
        text = annotatedString,
        style = TextStyle.Default,
        placeholders = emptyList(),
        maxLines = Int.MAX_VALUE,
        softWrap = true,
        overflow = TextOverflow.Clip,
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        fontFamilyResolver = fontFamilyResolver,
        constraints = constraints,
    )
    val multiParagraph = MultiParagraph(
        annotatedString = annotatedString,
        style = TextStyle.Default,
        constraints = constraints,
        density = density,
        fontFamilyResolver = fontFamilyResolver,
    )
    return TextLayoutResult(layoutInput, multiParagraph, IntSize(100, multiParagraph.height.toInt()))
}

private fun Matrix.setTranslate(offset: Offset) {
    reset()
    translate(offset.x, offset.y)
}
