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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.HistoricalChange
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType

internal data class WinUIPointerSample(
    val id: Long,
    val uptimeMillis: Long,
    val position: Offset,
    val down: Boolean,
    val type: PointerType,
    val pressure: Float,
    val activeHover: Boolean,
    val historical: List<HistoricalChange>,
)

internal class WinUIPointerStateTracker {
    private val activePointers = linkedMapOf<Long, WinUIPointerSample>()

    fun update(
        eventType: PointerEventType,
        changedPointer: WinUIPointerSample,
    ): List<WinUIPointerSample> {
        activePointers[changedPointer.id] = changedPointer
        val pointers = activePointers.values.toList()
        if (
            eventType == PointerEventType.Release ||
                (eventType == PointerEventType.Exit && !changedPointer.down)
        ) {
            activePointers.remove(changedPointer.id)
        }
        return pointers
    }

    fun snapshot(): List<WinUIPointerSample> = activePointers.values.toList()

    fun clear(): Boolean {
        val hadPointers = activePointers.isNotEmpty()
        activePointers.clear()
        return hadPointers
    }
}

internal class WinUIPointerCaptureTracker<T>(
    private val sameIdentity: (T, T) -> Boolean,
) {
    private val capturedPointers = linkedMapOf<Long, T>()

    fun record(pointerId: Long, pointer: T) {
        capturedPointers[pointerId] = pointer
    }

    fun release(pointer: T, releaseNative: (T) -> Unit) {
        remove(pointer)
        releaseNative(pointer)
    }

    fun onCaptureLost(pointer: T): Boolean = remove(pointer)

    fun releaseAll(releaseNative: (T) -> Unit) {
        val pointers = capturedPointers.values.toList()
        capturedPointers.clear()
        pointers.forEach(releaseNative)
    }

    private fun remove(pointer: T): Boolean {
        var removed = false
        val iterator = capturedPointers.entries.iterator()
        while (iterator.hasNext()) {
            if (sameIdentity(iterator.next().value, pointer)) {
                iterator.remove()
                removed = true
            }
        }
        return removed
    }
}
