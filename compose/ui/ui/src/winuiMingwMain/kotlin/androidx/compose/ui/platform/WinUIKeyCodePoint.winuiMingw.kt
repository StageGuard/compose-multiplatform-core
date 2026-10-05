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

package androidx.compose.ui.platform

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.windows.GetKeyboardLayout
import platform.windows.GetKeyboardState
import platform.windows.ToUnicodeEx

// Keeps the keyboard state, so that a dead key typed after this call still combines.
private const val ToUnicodeNoKeyboardStateChange = 0x4

internal actual fun winUIKeyCodePoint(virtualKey: Int, scanCode: Int): Int =
    memScoped {
        val keyboardState = allocArray<UByteVar>(256)
        if (GetKeyboardState(keyboardState) == 0) {
            return@memScoped 0
        }
        val buffer = allocArray<UShortVar>(8)
        val count = ToUnicodeEx(
            virtualKey.toUInt(),
            scanCode.toUInt(),
            keyboardState,
            buffer,
            8,
            ToUnicodeNoKeyboardStateChange.toUInt(),
            GetKeyboardLayout(0u),
        )
        if (count >= 1) buffer[0].toInt() else 0
    }
