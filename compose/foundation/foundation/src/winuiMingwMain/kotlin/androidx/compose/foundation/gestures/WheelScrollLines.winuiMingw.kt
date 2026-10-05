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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package androidx.compose.foundation.gestures

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.windows.SPI_GETWHEELSCROLLLINES
import platform.windows.SystemParametersInfoW

// The Windows default, used when the setting cannot be read.
private const val DefaultWheelScrollLines = 3

internal actual fun systemWheelScrollLines(): Int =
    memScoped {
        val lines = alloc<IntVar>()
        if (SystemParametersInfoW(SPI_GETWHEELSCROLLLINES.toUInt(), 0u, lines.ptr, 0u) != 0) {
            // WHEEL_PAGESCROLL is UINT_MAX, which reads as -1.
            lines.value
        } else {
            DefaultWheelScrollLines
        }
    }
