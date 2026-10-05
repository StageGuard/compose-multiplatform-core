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

internal data class WinUIOwnedResourceUpdate(
    val ownsIncoming: Boolean,
    val failure: Throwable? = null,
)

internal class WinUIOwnedResourceSlot<T : Any>(private val closeResource: (T) -> Unit) {
    private var isDisposed = false

    var value: T? = null
        private set

    fun replace(next: T?): WinUIOwnedResourceUpdate {
        if (isDisposed) return WinUIOwnedResourceUpdate(ownsIncoming = false)
        if (value === next) return WinUIOwnedResourceUpdate(ownsIncoming = next != null)
        val previous = value
        value = next
        val failure = previous?.let { runCatching { closeResource(it) }.exceptionOrNull() }
        return WinUIOwnedResourceUpdate(ownsIncoming = next != null, failure = failure)
    }

    fun clear(): WinUIOwnedResourceUpdate = replace(null)

    fun dispose(): WinUIOwnedResourceUpdate {
        if (isDisposed) return WinUIOwnedResourceUpdate(ownsIncoming = false)
        isDisposed = true
        val previous = value
        value = null
        val failure = previous?.let { runCatching { closeResource(it) }.exceptionOrNull() }
        return WinUIOwnedResourceUpdate(ownsIncoming = false, failure = failure)
    }
}
