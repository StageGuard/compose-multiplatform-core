/*
 * Copyright 2024 The Android Open Source Project
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

import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTargetModifierNode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.ClipMetadata
import androidx.compose.ui.platform.PlatformDragAndDropEventData

@OptIn(InternalComposeUiApi::class)
internal actual fun textFieldDragAndDropNode(
    hintMediaTypes: () -> Set<MediaType>,
    onDrop: (clipEntry: ClipEntry, clipMetadata: ClipMetadata) -> Boolean,
    dragAndDropRequestPermission: (DragAndDropEvent) -> Unit,
    onStarted: ((event: DragAndDropEvent) -> Unit)?,
    onEntered: ((event: DragAndDropEvent) -> Unit)?,
    onMoved: ((position: Offset) -> Unit)?,
    onChanged: ((event: DragAndDropEvent) -> Unit)?,
    onExited: ((event: DragAndDropEvent) -> Unit)?,
    onEnded: ((event: DragAndDropEvent) -> Unit)?,
): DragAndDropTargetModifierNode =
    DragAndDropTargetModifierNode(
        shouldStartDragAndDrop = { event -> winUIAcceptsTextFieldDrop(event, hintMediaTypes()) },
        target =
            winUITextFieldDragAndDropTarget(
                onDrop = onDrop,
                dragAndDropRequestPermission = dragAndDropRequestPermission,
                onStarted = onStarted,
                onEntered = onEntered,
                onMoved = onMoved,
                onChanged = onChanged,
                onExited = onExited,
                onEnded = onEnded,
            ),
    )

@OptIn(InternalComposeUiApi::class)
internal fun winUIAcceptsTextFieldDrop(
    event: DragAndDropEvent,
    hintMediaTypes: Set<MediaType>,
): Boolean {
    val data = PlatformDragAndDropEventData.get(event) ?: return false
    val content =
        TransferableContent(
            clipEntry = data.clipEntry,
            clipMetadata = data.clipMetadata,
            source = TransferableContent.Source.DragAndDrop,
        )
    return hintMediaTypes.any(content::hasMediaType)
}

@OptIn(InternalComposeUiApi::class)
internal fun winUITextFieldDragAndDropTarget(
    onDrop: (clipEntry: ClipEntry, clipMetadata: ClipMetadata) -> Boolean,
    dragAndDropRequestPermission: (DragAndDropEvent) -> Unit,
    onStarted: ((event: DragAndDropEvent) -> Unit)? = null,
    onEntered: ((event: DragAndDropEvent) -> Unit)? = null,
    onMoved: ((position: Offset) -> Unit)? = null,
    onChanged: ((event: DragAndDropEvent) -> Unit)? = null,
    onExited: ((event: DragAndDropEvent) -> Unit)? = null,
    onEnded: ((event: DragAndDropEvent) -> Unit)? = null,
): DragAndDropTarget =
    object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val data = PlatformDragAndDropEventData.get(event) ?: return false
            dragAndDropRequestPermission(event)
            return onDrop(data.clipEntry, data.clipMetadata)
        }

        override fun onStarted(event: DragAndDropEvent) = onStarted?.invoke(event) ?: Unit

        override fun onEntered(event: DragAndDropEvent) = onEntered?.invoke(event) ?: Unit

        override fun onMoved(event: DragAndDropEvent) {
            val position = PlatformDragAndDropEventData.get(event)?.positionInRoot ?: return
            onMoved?.invoke(position)
        }

        override fun onExited(event: DragAndDropEvent) = onExited?.invoke(event) ?: Unit

        override fun onChanged(event: DragAndDropEvent) = onChanged?.invoke(event) ?: Unit

        override fun onEnded(event: DragAndDropEvent) = onEnded?.invoke(event) ?: Unit
    }
