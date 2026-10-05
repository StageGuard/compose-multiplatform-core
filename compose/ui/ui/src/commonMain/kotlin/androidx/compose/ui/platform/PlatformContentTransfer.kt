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

import androidx.annotation.RestrictTo
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.geometry.Offset

/**
 * Platform-neutral metadata used by shared Compose code to inspect transferred content without
 * depending on a native clipboard or drag event type.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@InternalComposeUiApi
interface PlatformClipMetadata {
    val availableFormats: Set<String>

    fun hasMediaType(representation: String): Boolean =
        when {
            representation == "*/*" -> availableFormats.isNotEmpty()
            representation.endsWith("/*") -> {
                val prefix = representation.removeSuffix("*")
                availableFormats.any { format -> format.startsWith(prefix, ignoreCase = true) }
            }
            else -> representation in availableFormats
        }

    fun readPlainText(): String?
}

/** Platform-neutral data exposed while a native drag event is dispatched through Compose. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@InternalComposeUiApi
class PlatformDragAndDropData(
    val clipEntry: ClipEntry,
    val clipMetadata: ClipMetadata,
    val positionInRoot: Offset,
)

/**
 * Associates platform-neutral transfer data with a [DragAndDropEvent] for the duration of its
 * synchronous Compose dispatch.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@InternalComposeUiApi
object PlatformDragAndDropEventData {
    private val eventData = mutableMapOf<DragAndDropEvent, PlatformDragAndDropData>()

    fun set(event: DragAndDropEvent, data: PlatformDragAndDropData) {
        eventData[event] = data
    }

    fun get(event: DragAndDropEvent): PlatformDragAndDropData? = eventData[event]

    fun clear(event: DragAndDropEvent) {
        eventData.remove(event)
    }
}
