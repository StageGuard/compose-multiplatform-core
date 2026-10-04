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
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.ValueLayout

private val getClipboardSequenceNumber = Linker.nativeLinker().downcallHandle(
    user32Lookup.find("GetClipboardSequenceNumber").orElseThrow(),
    FunctionDescriptor.of(ValueLayout.JAVA_INT),
)

internal actual fun clipboardSequenceNumber(): Long =
    (getClipboardSequenceNumber.invokeWithArguments() as Int).toLong() and 0xFFFFFFFFL
