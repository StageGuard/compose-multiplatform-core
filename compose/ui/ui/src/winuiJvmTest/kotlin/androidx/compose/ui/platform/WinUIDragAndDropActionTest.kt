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
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import windows.applicationmodel.datatransfer.DataPackageOperation

@OptIn(ExperimentalComposeUiApi::class)
class WinUIDragAndDropActionTest {
    private val allOperations =
        DataPackageOperation.Copy or DataPackageOperation.Move or DataPackageOperation.Link

    @Test
    fun withoutModifierKeysADragCopiesWhenTheSourceAllowsIt() {
        assertEquals(DragAndDropTransferAction.Copy, action(allOperations))
        assertEquals(
            DragAndDropTransferAction.Move,
            action(DataPackageOperation.Move or DataPackageOperation.Link),
        )
        assertEquals(DragAndDropTransferAction.Link, action(DataPackageOperation.Link))
        assertNull(action(DataPackageOperation.None))
    }

    @Test
    fun modifierKeysPickTheActionAsInAwt() {
        assertEquals(DragAndDropTransferAction.Copy, action(allOperations, control = true))
        assertEquals(DragAndDropTransferAction.Move, action(allOperations, shift = true))
        assertEquals(
            DragAndDropTransferAction.Link,
            action(allOperations, control = true, shift = true),
        )
        // The keys ask for an action that the source doesn't allow.
        assertNull(action(DataPackageOperation.Copy, shift = true))
    }

    @Test
    fun supportedActionsBecomeTheAllowedOperations() {
        assertEquals(
            allOperations,
            listOf(
                DragAndDropTransferAction.Copy,
                DragAndDropTransferAction.Move,
                DragAndDropTransferAction.Link,
            ).toDataPackageOperation(),
        )
        assertEquals(
            DataPackageOperation.Move,
            listOf(DragAndDropTransferAction.Move).toDataPackageOperation(),
        )
    }

    @Test
    fun dropResultsBecomeTransferActions() {
        assertEquals(DragAndDropTransferAction.Copy, DataPackageOperation.Copy.toTransferAction())
        assertEquals(DragAndDropTransferAction.Move, DataPackageOperation.Move.toTransferAction())
        assertEquals(DragAndDropTransferAction.Link, DataPackageOperation.Link.toTransferAction())
        assertNull(DataPackageOperation.None.toTransferAction())
        assertEquals(DataPackageOperation.None, null.toDataPackageOperation())
    }

    private fun action(
        allowedOperations: DataPackageOperation,
        control: Boolean = false,
        shift: Boolean = false,
    ): DragAndDropTransferAction? =
        winUIDragAction(
            allowedOperations = allowedOperations,
            isControlPressed = control,
            isShiftPressed = shift,
        )
}
