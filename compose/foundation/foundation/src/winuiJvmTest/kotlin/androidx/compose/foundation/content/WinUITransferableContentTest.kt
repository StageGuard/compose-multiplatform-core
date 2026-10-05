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

package androidx.compose.foundation.content

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.internal.ReceiveContentConfiguration
import androidx.compose.foundation.content.internal.winUIAcceptsReceiveContentDrop
import androidx.compose.foundation.content.internal.winUIReceiveContentDragAndDropTarget
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.PlatformClipMetadata
import androidx.compose.ui.platform.PlatformDragAndDropData
import androidx.compose.ui.platform.PlatformDragAndDropEventData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class,
    InternalComposeUiApi::class,
)
class WinUITransferableContentTest {
    @Test
    fun mediaTypePreservesRepresentationAndUsesValueEquality() {
        assertEquals("custom/format", MediaType("custom/format").representation)
        assertEquals("text/*", MediaType.Text.representation)
        assertEquals("text/plain", MediaType.PlainText.representation)
        assertEquals("text/html", MediaType.HtmlText.representation)
        assertEquals("image/*", MediaType.Image.representation)
        assertEquals("*/*", MediaType.All.representation)
        assertEquals(MediaType.PlainText, MediaType(MediaType.PlainText.representation))
        assertEquals(
            MediaType.PlainText.hashCode(),
            MediaType(MediaType.PlainText.representation).hashCode(),
        )
    }

    @Test
    fun transferableContentUsesAvailableFormatsAndPlainText() {
        val metadata = FakeClipMetadata(setOf(MediaType.PlainText.representation), "hello")
        val clipEntry = ClipEntry(metadata)
        val content =
            TransferableContent(
                clipEntry = clipEntry,
                clipMetadata = clipEntry.clipMetadata,
                source = TransferableContent.Source.DragAndDrop,
            )

        assertTrue(content.hasMediaType(MediaType.PlainText))
        assertTrue(content.hasMediaType(MediaType.Text))
        assertTrue(content.hasMediaType(MediaType.All))
        assertFalse(content.hasMediaType(MediaType.Image))
        assertEquals(metadata.availableFormats, clipEntry.clipMetadata.availableFormats)
        assertEquals("hello", clipEntry.readPlainText())
    }

    @Test
    fun transferableContentMatchesCanonicalHtmlAsText() {
        val metadata = FakeClipMetadata(setOf(MediaType.HtmlText.representation), null)
        val clipEntry = ClipEntry(metadata)
        val content =
            TransferableContent(
                clipEntry = clipEntry,
                clipMetadata = clipEntry.clipMetadata,
                source = TransferableContent.Source.DragAndDrop,
            )

        assertTrue(content.hasMediaType(MediaType.HtmlText))
        assertTrue(content.hasMediaType(MediaType.Text))
        assertFalse(content.hasMediaType(MediaType.PlainText))
    }

    @Test
    fun emptyMetadataDoesNotMatchAllMediaTypes() {
        val metadata = FakeClipMetadata(emptySet(), null)
        val clipEntry = ClipEntry(metadata)
        val content =
            TransferableContent(
                clipEntry = clipEntry,
                clipMetadata = clipEntry.clipMetadata,
                source = TransferableContent.Source.DragAndDrop,
            )

        assertFalse(content.hasMediaType(MediaType.All))
    }

    @Test
    fun receiveContentTargetForwardsDropAndRequestsPermission() {
        val metadata = FakeClipMetadata(setOf(MediaType.PlainText.representation), "hello")
        val clipEntry = ClipEntry(metadata)
        val event = DragAndDropEvent()
        PlatformDragAndDropEventData.set(
            event,
            PlatformDragAndDropData(
                clipEntry = clipEntry,
                clipMetadata = clipEntry.clipMetadata,
                positionInRoot = Offset(8f, 13f),
            ),
        )
        var permissionRequests = 0
        var received: TransferableContent? = null
        val target =
            winUIReceiveContentDragAndDropTarget(
                receiveContentConfiguration =
                    ReceiveContentConfiguration(
                        ReceiveContentListener { content ->
                            received = content
                            null
                        }
                    ),
                dragAndDropRequestPermission = { permissionRequests++ },
            )

        try {
            assertTrue(winUIAcceptsReceiveContentDrop(event))
            assertTrue(target.onDrop(event))
            assertEquals(1, permissionRequests)
            assertSame(clipEntry, received?.clipEntry)
            assertEquals(TransferableContent.Source.DragAndDrop, received?.source)
        } finally {
            PlatformDragAndDropEventData.clear(event)
        }
    }

    @Test
    fun receiveContentTargetRejectsEventWithoutPlatformData() {
        assertFalse(winUIAcceptsReceiveContentDrop(DragAndDropEvent()))
    }
}

@OptIn(InternalComposeUiApi::class)
internal class FakeClipMetadata(
    override val availableFormats: Set<String>,
    private val text: String?,
) : PlatformClipMetadata {
    override fun readPlainText(): String? = text
}
