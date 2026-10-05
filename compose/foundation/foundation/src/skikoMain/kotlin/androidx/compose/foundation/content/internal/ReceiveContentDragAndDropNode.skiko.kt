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

package androidx.compose.foundation.content.internal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTargetModifierNode
import androidx.compose.ui.platform.PlatformDragAndDropEventData

@OptIn(InternalComposeUiApi::class)
internal actual fun ReceiveContentDragAndDropNode(
    receiveContentConfiguration: ReceiveContentConfiguration,
    dragAndDropRequestPermission: (DragAndDropEvent) -> Unit,
): DragAndDropTargetModifierNode =
    DragAndDropTargetModifierNode(
        shouldStartDragAndDrop = ::winUIAcceptsReceiveContentDrop,
        target =
            winUIReceiveContentDragAndDropTarget(
                receiveContentConfiguration = receiveContentConfiguration,
                dragAndDropRequestPermission = dragAndDropRequestPermission,
            ),
    )

@OptIn(InternalComposeUiApi::class)
internal fun winUIAcceptsReceiveContentDrop(event: DragAndDropEvent): Boolean =
    PlatformDragAndDropEventData.get(event) != null

@OptIn(ExperimentalFoundationApi::class, InternalComposeUiApi::class)
internal fun winUIReceiveContentDragAndDropTarget(
    receiveContentConfiguration: ReceiveContentConfiguration,
    dragAndDropRequestPermission: (DragAndDropEvent) -> Unit,
): DragAndDropTarget =
    object : DragAndDropTarget {
        override fun onStarted(event: DragAndDropEvent) {
            receiveContentConfiguration.receiveContentListener.onDragStart()
        }

        override fun onEnded(event: DragAndDropEvent) {
            receiveContentConfiguration.receiveContentListener.onDragEnd()
        }

        override fun onEntered(event: DragAndDropEvent) {
            receiveContentConfiguration.receiveContentListener.onDragEnter()
        }

        override fun onExited(event: DragAndDropEvent) {
            receiveContentConfiguration.receiveContentListener.onDragExit()
        }

        override fun onDrop(event: DragAndDropEvent): Boolean {
            val data = PlatformDragAndDropEventData.get(event) ?: return false
            dragAndDropRequestPermission(event)

            val original =
                TransferableContent(
                    clipEntry = data.clipEntry,
                    clipMetadata = data.clipMetadata,
                    source = TransferableContent.Source.DragAndDrop,
                )
            val remaining = receiveContentConfiguration.receiveContentListener.onReceive(original)
            return original != remaining
        }
    }
