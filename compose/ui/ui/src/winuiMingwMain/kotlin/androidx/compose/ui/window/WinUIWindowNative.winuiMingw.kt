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

import androidx.compose.ui.platform.winUIDebugLog
import androidx.compose.ui.platform.winUISystemBooleanProperty
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
import microsoft.ui.xaml.XamlRoot
import microsoft.ui.xaml.Window as XamlWindow
import platform.windows.CreateRectRgn
import platform.windows.DWM_BB_BLURREGION
import platform.windows.DWM_BB_ENABLE
import platform.windows.DWM_BLURBEHIND
import platform.windows.DeleteObject
import platform.windows.DwmEnableBlurBehindWindow
import platform.windows.DwmSetWindowAttribute
import platform.windows.HWND__

private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
private const val DWMWCP_DONOTROUND = 1

internal actual fun setWindowTransparentBackdrop(
    window: XamlWindow,
    xamlRoot: XamlRoot?,
    enabled: Boolean,
): Boolean {
    val hwndValue = winuiWindowHwnd(window)
    if (hwndValue == 0L) return false
    val hwnd = hwndValue.toCPointer<HWND__>() ?: return false

    return memScoped {
        val blurBehind = alloc<DWM_BLURBEHIND>()
        val blurRegion = if (enabled) CreateRectRgn(-2, -2, -1, -1) else null
        if (enabled && blurRegion == null) return@memScoped false
        try {
            blurBehind.dwFlags = if (enabled) {
                (DWM_BB_ENABLE or DWM_BB_BLURREGION).toUInt()
            } else {
                DWM_BB_ENABLE.toUInt()
            }
            blurBehind.fEnable = if (enabled) 1 else 0
            blurBehind.hRgnBlur = blurRegion
            blurBehind.fTransitionOnMaximized = 0

            val enableResult = DwmEnableBlurBehindWindow(hwnd, blurBehind.ptr)
            if (winUISystemBooleanProperty("compose.winui.transparentBackdrop.debug")) {
                winUIDebugLog(
                    "transparent-backdrop",
                    "enabled=$enabled hwnd=0x${hwndValue.toString(16)} " +
                        "hasXamlRoot=${xamlRoot != null} " +
                        "dwmEnable=0x${enableResult.toUInt().toString(16)}",
                )
            }
            if (enabled) {
                setDoNotRoundWindowCorners(hwnd)
            }
            enableResult >= 0
        } finally {
            if (blurRegion != null) {
                DeleteObject(blurRegion)
            }
        }
    }
}

internal actual fun setWindowTransparentBackdropDirect(
    window: XamlWindow,
    enabled: Boolean,
): Boolean = setWindowTransparentBackdrop(window, xamlRoot = null, enabled = enabled)

private fun setDoNotRoundWindowCorners(hwnd: kotlinx.cinterop.CPointer<HWND__>) {
    memScoped {
        val preference = alloc<IntVar>()
        preference.value = DWMWCP_DONOTROUND
        DwmSetWindowAttribute(
            hwnd,
            DWMWA_WINDOW_CORNER_PREFERENCE.toUInt(),
            preference.ptr,
            sizeOf<IntVar>().toUInt(),
        )
    }
}
