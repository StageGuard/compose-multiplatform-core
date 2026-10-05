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

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import microsoft.ui.xaml.XamlRoot
import microsoft.ui.xaml.Window as XamlWindow

private const val DWM_BB_ENABLE = 0x00000001
private const val DWM_BB_BLURREGION = 0x00000002
private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
private const val DWMWCP_DONOTROUND = 1

internal val user32Lookup: SymbolLookup = SymbolLookup.libraryLookup("user32", Arena.global())
private val dwmapiLookup: SymbolLookup = SymbolLookup.libraryLookup("dwmapi", Arena.global())
private val gdi32Lookup: SymbolLookup = SymbolLookup.libraryLookup("gdi32", Arena.global())

private val dwmEnableBlurBehindWindow = Linker.nativeLinker().downcallHandle(
    dwmapiLookup.find("DwmEnableBlurBehindWindow").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
    ),
)

private val dwmSetWindowAttribute = Linker.nativeLinker().downcallHandle(
    dwmapiLookup.find("DwmSetWindowAttribute").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
    ),
)

private val createRectRgn = Linker.nativeLinker().downcallHandle(
    gdi32Lookup.find("CreateRectRgn").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
    ),
)

private val deleteObject = Linker.nativeLinker().downcallHandle(
    gdi32Lookup.find("DeleteObject").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
    ),
)

private val DwmBlurBehindLayout = MemoryLayout.structLayout(
    ValueLayout.JAVA_INT.withName("dwFlags"),
    ValueLayout.JAVA_INT.withName("fEnable"),
    ValueLayout.ADDRESS.withName("hRgnBlur"),
    ValueLayout.JAVA_INT.withName("fTransitionOnMaximized"),
    MemoryLayout.paddingLayout(32),
)
private val DwmBlurBehindFlags = DwmBlurBehindLayout.varHandle(
    MemoryLayout.PathElement.groupElement("dwFlags"),
)
private val DwmBlurBehindEnable = DwmBlurBehindLayout.varHandle(
    MemoryLayout.PathElement.groupElement("fEnable"),
)
private val DwmBlurBehindRegion = DwmBlurBehindLayout.varHandle(
    MemoryLayout.PathElement.groupElement("hRgnBlur"),
)
private val DwmBlurBehindTransition = DwmBlurBehindLayout.varHandle(
    MemoryLayout.PathElement.groupElement("fTransitionOnMaximized"),
)

internal actual fun setWindowTransparentBackdrop(
    window: XamlWindow,
    xamlRoot: XamlRoot?,
    enabled: Boolean,
): Boolean {
    val xamlRootWindowId = xamlRoot?.contentIslandEnvironment?.appWindowId?.value?.toLong()
        ?.takeIf { it != 0L }
    val windowHwnd = runCatching { winuiWindowHwnd(window) }.getOrDefault(0L)
        .takeIf { it != 0L }
    val hwnd = windowHwnd ?: 0L
    if (hwnd == 0L) return false
    return Arena.ofConfined().use { scope ->
        val blurBehind = scope.allocate(DwmBlurBehindLayout)
        var blurRegion = MemorySegment.NULL
        try {
            if (enabled) {
                blurRegion = createRectRgn.invokeWithArguments(-2, -2, -1, -1) as MemorySegment
                DwmBlurBehindFlags.set(blurBehind, 0L, DWM_BB_ENABLE or DWM_BB_BLURREGION)
                DwmBlurBehindEnable.set(blurBehind, 0L, 1)
                DwmBlurBehindRegion.set(blurBehind, 0L, blurRegion)
            } else {
                DwmBlurBehindFlags.set(blurBehind, 0L, DWM_BB_ENABLE)
                DwmBlurBehindEnable.set(blurBehind, 0L, 0)
                DwmBlurBehindRegion.set(blurBehind, 0L, MemorySegment.NULL)
            }
            DwmBlurBehindTransition.set(blurBehind, 0L, 0)
            val enableResult = dwmEnableBlurBehindWindow.invokeWithArguments(
                MemorySegment.ofAddress(hwnd),
                blurBehind,
            ) as Int
            debugTransparentBackdrop {
                "enabled=$enabled hwnd=0x${hwnd.toString(16)} " +
                    "xamlRootWindowId=${xamlRootWindowId?.let { "0x${it.toString(16)}" }} " +
                    "dwmEnable=0x${enableResult.toUInt().toString(16)}"
            }
            if (enabled) {
                setDoNotRoundWindowCorners(scope, hwnd)
            }
            enableResult >= 0
        } finally {
            if (blurRegion != MemorySegment.NULL) {
                runCatching { deleteObject.invokeWithArguments(blurRegion) }
            }
        }
    }
}

internal actual fun setWindowTransparentBackdropDirect(
    window: XamlWindow,
    enabled: Boolean,
): Boolean = setWindowTransparentBackdrop(window, xamlRoot = null, enabled = enabled)

private val isTransparentBackdropDebugEnabled: Boolean by lazy {
    java.lang.Boolean.getBoolean("compose.winui.transparentBackdrop.debug")
}

private inline fun debugTransparentBackdrop(message: () -> String) {
    if (isTransparentBackdropDebugEnabled) {
        println("[compose-winui:transparent-backdrop] ${message()}")
    }
}

private fun setDoNotRoundWindowCorners(scope: Arena, hwnd: Long) {
    runCatching {
        val preference = scope.allocate(ValueLayout.JAVA_INT)
        preference.set(ValueLayout.JAVA_INT, 0L, DWMWCP_DONOTROUND)
        dwmSetWindowAttribute.invokeWithArguments(
            MemorySegment.ofAddress(hwnd),
            DWMWA_WINDOW_CORNER_PREFERENCE,
            preference,
            ValueLayout.JAVA_INT.byteSize().toInt(),
        )
    }
}
