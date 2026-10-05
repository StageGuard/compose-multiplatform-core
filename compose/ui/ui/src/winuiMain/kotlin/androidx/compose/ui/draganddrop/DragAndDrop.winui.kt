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

package androidx.compose.ui.draganddrop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import kotlin.jvm.JvmInline

/**
 * The information needed to start a drag-and-drop session from Compose on WinUI.
 */
actual class DragAndDropTransferData @ExperimentalComposeUiApi constructor(
    /**
     * What is dragged: a [String], which the drag carries as text, or a function
     * `(windows.applicationmodel.datatransfer.DataPackage) -> Unit` that fills the data package of
     * the drag.
     */
    val nativeTransferData: Any? = null,

    /**
     * The transfer actions supported by the source of the drag-and-drop session.
     */
    @property:ExperimentalComposeUiApi
    val supportedActions: Iterable<DragAndDropTransferAction> = listOf(
        DragAndDropTransferAction.Copy,
    ),

    /**
     * The offset of the pointer relative to the drag decoration.
     */
    @property:ExperimentalComposeUiApi
    val dragDecorationOffset: Offset = Offset.Zero,

    /**
     * Invoked when the drag-and-drop gesture completes.
     *
     * The argument to the callback specifies the transfer action with which the gesture completed,
     * or `null` if the gesture did not complete successfully.
     */
    @property:ExperimentalComposeUiApi
    val onTransferCompleted: ((userAction: DragAndDropTransferAction?) -> Unit)? = null,
) {
    init {
        require(supportedActions.firstOrNull() != null) { "supportedActions may not be empty" }
    }
}

/**
 * The possible actions on the transferred object in a drag-and-drop session.
 */
@ExperimentalComposeUiApi
@JvmInline
value class DragAndDropTransferAction private constructor(private val id: Int) {
    override fun toString(): String {
        return when (this) {
            Copy -> "Copy"
            Move -> "Move"
            Link -> "Link"
            else -> "Unknown"
        }
    }

    companion object {
        /**
         * Indicates the dragged object should be copied into the target.
         */
        @ExperimentalComposeUiApi
        val Copy: DragAndDropTransferAction
            get() = DragAndDropTransferAction(0)

        /**
         * Indicates the dragged object should be moved ("cut" and "pasted") into the target.
         */
        @ExperimentalComposeUiApi
        val Move: DragAndDropTransferAction
            get() = DragAndDropTransferAction(1)

        /**
         * Indicates the dragged object should be linked to at the target.
         */
        @ExperimentalComposeUiApi
        val Link: DragAndDropTransferAction
            get() = DragAndDropTransferAction(2)
    }
}

/**
 * The event dispatched to [DragAndDropTarget] implementations during a drag-and-drop session.
 */
actual class DragAndDropEvent @ExperimentalComposeUiApi constructor(
    /**
     * The underlying `microsoft.ui.xaml.DragEventArgs`.
     */
    val nativeEvent: Any? = null,
    internal val positionInRootImpl: Offset = Offset.Zero,
    /**
     * The action currently selected by the user.
     */
    @property:ExperimentalComposeUiApi
    val action: DragAndDropTransferAction? = null,
)

internal actual val DragAndDropEvent.positionInRoot: Offset
    get() = positionInRootImpl
