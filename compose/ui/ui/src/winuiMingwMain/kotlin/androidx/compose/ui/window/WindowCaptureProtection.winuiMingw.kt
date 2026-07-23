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

import kotlinx.cinterop.toCPointer
import microsoft.ui.xaml.Window as XamlWindow
import platform.windows.HWND__
import platform.windows.SetWindowDisplayAffinity
import platform.windows.WDA_NONE

private const val WDA_EXCLUDEFROMCAPTURE = 0x00000011

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
