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

package androidx.compose.foundation.gestures

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

private const val SPI_GETWHEELSCROLLLINES = 0x0068

// The Windows default, used when the setting cannot be read.
private const val DefaultWheelScrollLines = 3

private val systemParametersInfo: MethodHandle? by lazy {
    runCatching {
        Linker.nativeLinker().downcallHandle(
            SymbolLookup.libraryLookup("user32", Arena.global())
                .find("SystemParametersInfoW")
                .orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
        )
    }.getOrNull()
}

internal actual fun systemWheelScrollLines(): Int {
    val function = systemParametersInfo ?: return DefaultWheelScrollLines
    return Arena.ofConfined().use { arena ->
        val lines = arena.allocate(ValueLayout.JAVA_INT)
        val succeeded =
            function.invokeWithArguments(SPI_GETWHEELSCROLLLINES, 0, lines, 0) as Int != 0
        if (succeeded) {
            // WHEEL_PAGESCROLL is UINT_MAX, which reads as -1.
            lines.get(ValueLayout.JAVA_INT, 0)
        } else {
            DefaultWheelScrollLines
        }
    }
}
