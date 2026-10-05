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

import androidx.compose.ui.window.user32Lookup
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

// Keeps the keyboard state, so that a dead key typed after this call still combines.
private const val ToUnicodeNoKeyboardStateChange = 0x4

private val getKeyboardState = Linker.nativeLinker().downcallHandle(
    user32Lookup.find("GetKeyboardState").orElseThrow(),
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
)

private val getKeyboardLayout = Linker.nativeLinker().downcallHandle(
    user32Lookup.find("GetKeyboardLayout").orElseThrow(),
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
)

private val toUnicodeEx = Linker.nativeLinker().downcallHandle(
    user32Lookup.find("ToUnicodeEx").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
    ),
)

internal actual fun winUIKeyCodePoint(virtualKey: Int, scanCode: Int): Int =
    runCatching {
        Arena.ofConfined().use { arena ->
            val keyboardState = arena.allocate(256)
            if (getKeyboardState.invokeWithArguments(keyboardState) as Int == 0) {
                return@use 0
            }
            val buffer = arena.allocate(ValueLayout.JAVA_CHAR, 8)
            val layout = getKeyboardLayout.invokeWithArguments(0) as MemorySegment
            val count = toUnicodeEx.invokeWithArguments(
                virtualKey,
                scanCode,
                keyboardState,
                buffer,
                8,
                ToUnicodeNoKeyboardStateChange,
                layout,
            ) as Int
            if (count >= 1) buffer.getAtIndex(ValueLayout.JAVA_CHAR, 0).code else 0
        }
    }.getOrDefault(0)
