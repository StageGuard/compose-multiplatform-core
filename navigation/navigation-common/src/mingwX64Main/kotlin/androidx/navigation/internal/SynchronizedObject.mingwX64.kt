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

@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package androidx.navigation.internal

import kotlin.native.ref.createCleaner
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import platform.windows.CRITICAL_SECTION
import platform.windows.DeleteCriticalSection
import platform.windows.EnterCriticalSection
import platform.windows.InitializeCriticalSection
import platform.windows.LeaveCriticalSection

internal actual class SynchronizedObject actual constructor() {
    private val section = nativeHeap.alloc<CRITICAL_SECTION>().ptr

    @Suppress("unused")
    private val cleaner = createCleaner(section) { pointer ->
        DeleteCriticalSection(pointer)
        nativeHeap.free(pointer.rawValue)
    }

    init {
        InitializeCriticalSection(section)
    }

    fun lock() {
        EnterCriticalSection(section)
    }

    fun unlock() {
        LeaveCriticalSection(section)
    }
}

internal actual inline fun <T> synchronizedImpl(
    lock: SynchronizedObject,
    crossinline action: () -> T,
): T {
    lock.lock()
    return try {
        action()
    } finally {
        lock.unlock()
    }
}
