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

package androidx.compose.ui.window

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import microsoft.ui.xaml.Window as XamlWindow

private const val WDA_NONE = 0x00000000
private const val WDA_EXCLUDEFROMCAPTURE = 0x00000011

private val setWindowDisplayAffinity = Linker.nativeLinker().downcallHandle(
    user32Lookup.find("SetWindowDisplayAffinity").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
    ),
)

private val isIconic = Linker.nativeLinker().downcallHandle(
    user32Lookup.find("IsIconic").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
    ),
)

private val getDpiForWindow = Linker.nativeLinker().downcallHandle(
    user32Lookup.find("GetDpiForWindow").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
    ),
)

internal actual fun windowDpiScale(window: XamlWindow): Float {
    val hwnd = runCatching { winuiWindowHwnd(window) }.getOrDefault(0L)
    if (hwnd == 0L) return 1f
    val dpi = getDpiForWindow.invokeWithArguments(MemorySegment.ofAddress(hwnd)) as Int
    return if (dpi > 0) dpi / 96f else 1f
}

internal actual fun isWindowMinimized(window: XamlWindow): Boolean {
    val hwnd = runCatching { winuiWindowHwnd(window) }.getOrDefault(0L)
    if (hwnd == 0L) return false
    return (isIconic.invokeWithArguments(MemorySegment.ofAddress(hwnd)) as Int) != 0
}

internal actual fun setWindowCaptureProtection(window: XamlWindow, isProtected: Boolean): Boolean {
    val hwnd = winuiWindowHwnd(window)
    if (hwnd == 0L) return false
    val affinity = if (isProtected) WDA_EXCLUDEFROMCAPTURE else WDA_NONE
    return (setWindowDisplayAffinity.invokeWithArguments(
        MemorySegment.ofAddress(hwnd),
        affinity,
    ) as Int) != 0
}
