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

package androidx.compose.foundation.text.input.internal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.FakeClipMetadata
import androidx.compose.foundation.content.MediaType
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.PlatformDragAndDropData
import androidx.compose.ui.platform.PlatformDragAndDropEventData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class,
    InternalComposeUiApi::class,
)
class WinUITextFieldDragAndDropTest {
    @Test
    fun acceptedTextForwardsDropAndNativePosition() {
        val metadata = FakeClipMetadata(setOf(MediaType.PlainText.representation), "hello")
        val clipEntry = ClipEntry(metadata)
        val event = DragAndDropEvent()
        PlatformDragAndDropEventData.set(
            event,
            PlatformDragAndDropData(
                clipEntry = clipEntry,
                clipMetadata = clipEntry.clipMetadata,
                positionInRoot = Offset(12f, 34f),
            ),
        )
        var permissionRequests = 0
        var movedPosition = Offset.Zero
        var droppedEntry: ClipEntry? = null
        val target =
            winUITextFieldDragAndDropTarget(
                onDrop = { entry, _ ->
                    droppedEntry = entry
                    true
                },
                dragAndDropRequestPermission = { permissionRequests++ },
                onMoved = { movedPosition = it },
            )

        try {
            assertTrue(winUIAcceptsTextFieldDrop(event, setOf(MediaType.PlainText)))
            target.onMoved(event)
            assertTrue(target.onDrop(event))

            assertEquals(Offset(12f, 34f), movedPosition)
            assertEquals(1, permissionRequests)
            assertEquals(clipEntry, droppedEntry)
        } finally {
            PlatformDragAndDropEventData.clear(event)
        }
    }

    @Test
    fun unsupportedContentIsRejected() {
        val metadata = FakeClipMetadata(setOf(MediaType.Image.representation), null)
        val clipEntry = ClipEntry(metadata)
        val event = DragAndDropEvent()
        PlatformDragAndDropEventData.set(
            event,
            PlatformDragAndDropData(
                clipEntry = clipEntry,
                clipMetadata = clipEntry.clipMetadata,
                positionInRoot = Offset.Zero,
            ),
        )

        try {
            assertFalse(winUIAcceptsTextFieldDrop(event, setOf(MediaType.PlainText)))
        } finally {
            PlatformDragAndDropEventData.clear(event)
        }
    }
}
