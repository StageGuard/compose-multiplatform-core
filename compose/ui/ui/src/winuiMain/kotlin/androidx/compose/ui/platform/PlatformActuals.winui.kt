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

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.UiApplier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.LifecycleOwner

actual val LocalLifecycleOwner: ProvidableCompositionLocal<LifecycleOwner>
    get() = androidx.lifecycle.compose.LocalLifecycleOwner

actual interface PlatformTextInputMethodRequest {
    @ExperimentalComposeUiApi
    val value: () -> TextFieldValue
    @ExperimentalComposeUiApi
    val state: TextEditorState
    @ExperimentalComposeUiApi
    val imeOptions: ImeOptions
    @ExperimentalComposeUiApi
    val onEditCommand: (List<EditCommand>) -> Unit
    @ExperimentalComposeUiApi
    val onImeAction: ((ImeAction) -> Unit)?
    @ExperimentalComposeUiApi
    val textLayoutResult: () -> TextLayoutResult?
    @ExperimentalComposeUiApi
    val focusedRectInRoot: () -> Rect?
    @ExperimentalComposeUiApi
    val textFieldRectInRoot: () -> Rect?
    @ExperimentalComposeUiApi
    val textClippingRectInRoot: () -> Rect?
    @ExperimentalComposeUiApi
    val unclippedTextOffsetInRoot: () -> Offset?
    @ExperimentalComposeUiApi
    val editText: (block: TextEditingScope.() -> Unit) -> Unit

    /**
     * Opaque token that uniquely identifies the text editor.
     */
    @ExperimentalComposeUiApi
    val editorToken: Any?
        get() = null
}

actual interface PlatformTextInputSession {
    actual suspend fun startInputMethod(request: PlatformTextInputMethodRequest): Nothing
}

internal actual fun createApplier(container: LayoutNode): AbstractApplier<LayoutNode> =
    UiApplier(container)
