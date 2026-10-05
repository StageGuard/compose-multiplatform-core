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

package androidx.compose.mpp.demo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.WinUIView
import microsoft.ui.xaml.controls.Grid
import microsoft.ui.xaml.media.SolidColorBrush
import windows.ui.Color as WinUIColor

@Composable
internal actual fun TestInteropView(modifier: Modifier, color: Color) {
    // A native panel filled with the color, as the JPanel of the desktop demo.
    WinUIView(
        factory = {
            Grid().also { grid ->
                grid.background = SolidColorBrush().also { brush ->
                    brush.color = color.toWinUIColor()
                }
            }
        },
        modifier = modifier,
    )
}

private fun Color.toWinUIColor(): WinUIColor {
    val argb = toArgb()
    return WinUIColor(
        a = (argb ushr 24).toUByte(),
        r = (argb shr 16 and 0xFF).toUByte(),
        g = (argb shr 8 and 0xFF).toUByte(),
        b = (argb and 0xFF).toUByte(),
    )
}
