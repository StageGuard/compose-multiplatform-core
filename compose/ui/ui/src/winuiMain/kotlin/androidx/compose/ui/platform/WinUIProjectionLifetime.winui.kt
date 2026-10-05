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

internal class WinUIOwnedResource<T : Any>(resource: T, private val closeResource: (T) -> Unit) :
    AutoCloseable {
    private var resource: T? = resource

    val value: T
        get() = checkNotNull(resource) { "Resource ownership was already released." }

    fun releaseOwnership(): T = value.also { resource = null }

    override fun close() {
        val current = resource
        resource = null
        if (current != null) closeResource(current)
    }
}

internal inline fun <T : Any, R : Any> mapWinUIOwnedList(
    values: List<T>,
    dropLast: Int,
    noinline closeValues: (List<T>) -> Unit,
    noinline closeValue: (T) -> Unit,
    transform: (T) -> R?,
): List<R> =
    WinUIOwnedResource(values, closeValues).use {
        buildList {
            val size = values.size
            val endExclusive = size - dropLast.coerceIn(0, size)
            repeat(endExclusive) { index ->
                WinUIOwnedResource(values[index], closeValue).use { value ->
                    transform(value.value)?.let(::add)
                }
            }
        }
    }
