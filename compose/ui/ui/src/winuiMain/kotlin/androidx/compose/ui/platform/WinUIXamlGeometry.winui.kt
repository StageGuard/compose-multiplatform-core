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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import windows.foundation.Point
import windows.foundation.Rect as XamlRect
import kotlin.math.roundToInt

internal data class WinUIXamlSize(
    val width: Double,
    val height: Double,
)

internal fun WinUIXamlSize.toComposePixelSize(rasterizationScale: Float): IntSize {
    val scale = validateWinUIRasterizationScale(rasterizationScale).toDouble()
    return IntSize(
        width = (width * scale).roundToInt().coerceAtLeast(0),
        height = (height * scale).roundToInt().coerceAtLeast(0),
    )
}

internal fun XamlRect.toComposePixelRect(rasterizationScale: Float): Rect {
    val scale = validateWinUIRasterizationScale(rasterizationScale).toDouble()
    return Rect(
        left = (x * scale).toFloat(),
        top = (y * scale).toFloat(),
        right = ((x + width) * scale).toFloat(),
        bottom = ((y + height) * scale).toFloat(),
    )
}

internal fun validateWinUIRasterizationScale(scale: Float): Float =
    scale.takeIf { it.isFinite() && it > 0f } ?: 1f

internal fun IntSize.toWinUIXamlSize(rasterizationScale: Float): WinUIXamlSize {
    val scale = validateWinUIRasterizationScale(rasterizationScale).toDouble()
    return WinUIXamlSize(
        width = width.toDouble() / scale,
        height = height.toDouble() / scale,
    )
}

internal fun Int.toWinUIXamlSize(rasterizationScale: Float): Double {
    val scale = validateWinUIRasterizationScale(rasterizationScale).toDouble()
    return if (this > 0) toDouble() / scale else Double.NaN
}

internal fun IntOffset.toWinUIXamlPoint(rasterizationScale: Float): Point {
    val scale = validateWinUIRasterizationScale(rasterizationScale)
    return Point(x / scale, y / scale)
}

internal fun Rect.toWinUITextToolbarXamlPoint(rasterizationScale: Float): Point {
    val scale = validateWinUIRasterizationScale(rasterizationScale)
    return Point(left / scale, bottom / scale)
}

internal fun Rect.toXamlPoint(density: Density): Point =
    toWinUITextToolbarXamlPoint(density.density)
