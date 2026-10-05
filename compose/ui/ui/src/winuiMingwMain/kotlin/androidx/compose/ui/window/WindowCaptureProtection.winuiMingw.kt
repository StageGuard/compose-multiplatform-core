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

package androidx.compose.ui.window

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.invoke
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import microsoft.ui.xaml.Window as XamlWindow
import platform.windows.GetModuleHandleW
import platform.windows.GetProcAddress
import platform.windows.HWND__
import platform.windows.IsIconic
import platform.windows.SetWindowDisplayAffinity
import platform.windows.WDA_NONE

private const val WDA_EXCLUDEFROMCAPTURE = 0x00000011

// GetDpiForWindow (Windows 10 1607) is not in the Windows bindings of Kotlin/Native.
private val getDpiForWindow: CPointer<CFunction<(CPointer<HWND__>?) -> UInt>>? by lazy {
    GetModuleHandleW("user32.dll")?.let { user32 -> GetProcAddress(user32, "GetDpiForWindow") }
        ?.reinterpret()
}

internal actual fun windowDpiScale(window: XamlWindow): Float {
    val hwnd = runCatching { winuiWindowHwnd(window) }.getOrDefault(0L).toCPointer<HWND__>()
        ?: return 1f
    val dpi = getDpiForWindow?.invoke(hwnd)?.toInt() ?: 0
    return if (dpi > 0) dpi / 96f else 1f
}

internal actual fun isWindowMinimized(window: XamlWindow): Boolean {
    val hwnd = runCatching { winuiWindowHwnd(window) }.getOrDefault(0L).toCPointer<HWND__>()
        ?: return false
    return IsIconic(hwnd) != 0
}

internal actual fun setWindowCaptureProtection(
    window: XamlWindow,
    isProtected: Boolean,
): Boolean {
    val hwndValue = winuiWindowHwnd(window)
    if (hwndValue == 0L) return false
    val hwnd = hwndValue.toCPointer<HWND__>() ?: return false
    val affinity = if (isProtected) WDA_EXCLUDEFROMCAPTURE else WDA_NONE
    return SetWindowDisplayAffinity(hwnd, affinity.toUInt()) != 0
}
