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

package androidx.compose.ui.node

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.platform.validateWinUIRasterizationScale
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.UIElement
import windows.foundation.Point
import windows.graphics.PointInt32

internal class WinUICoordinateMapper(
    private val calculatePositionInWindow: (Offset) -> Offset = { it },
    private val calculateLocalPosition: (Offset) -> Offset = { it },
    private val screenCoordinatesReady: () -> Boolean = { true },
    private val localToScreen: (Offset) -> Offset = { it },
    private val screenToLocal: (Offset) -> Offset = { it },
) {
    fun calculatePositionInWindow(localPosition: Offset): Offset =
        calculatePositionInWindow.invoke(localPosition)

    fun calculateLocalPosition(positionInWindow: Offset): Offset =
        calculateLocalPosition.invoke(positionInWindow)

    fun localToScreen(localPosition: Offset): Offset =
        if (screenCoordinatesReady()) {
            localToScreen.invoke(localPosition)
        } else {
            calculatePositionInWindow.invoke(localPosition)
        }

    fun screenToLocal(positionOnScreen: Offset): Offset =
        if (screenCoordinatesReady()) screenToLocal.invoke(positionOnScreen) else positionOnScreen

    fun localToScreen(localTransform: Matrix) {
        val screenOrigin = localToScreen(Offset.Zero)
        localTransform.translate(screenOrigin.x, screenOrigin.y)
    }

    companion object {
        fun forRoot(
            root: UIElement,
            screenCoordinatesReady: () -> Boolean = { true },
            rasterizationScale: () -> Float = { 1f },
        ): WinUICoordinateMapper =
            WinUICoordinateMapper(
                calculatePositionInWindow = {
                    root.calculatePositionInWindow(it, rasterizationScale())
                },
                calculateLocalPosition = {
                    root.calculateLocalPosition(it, rasterizationScale())
                },
                screenCoordinatesReady = screenCoordinatesReady,
                localToScreen = { root.localToScreen(it, rasterizationScale()) },
                screenToLocal = { root.screenToLocal(it, rasterizationScale()) },
            )

        private fun UIElement.calculatePositionInWindow(
            localPosition: Offset,
            rasterizationScale: Float,
        ): Offset {
            val transform = rootTransformToWindow() ?: return localPosition
            return transform.transformPoint(localPosition.toWinUIXamlDipPoint(rasterizationScale))
                .toComposePixelOffset(rasterizationScale)
        }

        private fun UIElement.calculateLocalPosition(
            positionInWindow: Offset,
            rasterizationScale: Float,
        ): Offset {
            val transform = rootTransformToWindow()?.inverse ?: return positionInWindow
            return transform.transformPoint(positionInWindow.toWinUIXamlDipPoint(rasterizationScale))
                .toComposePixelOffset(rasterizationScale)
        }

        private fun UIElement.localToScreen(
            localPosition: Offset,
            rasterizationScale: Float,
        ): Offset {
            val xamlRoot = runCatching { xamlRoot }.getOrNull() ?: return localPosition
            val positionInWindow = calculatePositionInWindow(localPosition, rasterizationScale)
            val coordinateConverter = xamlRoot.coordinateConverter ?: return positionInWindow
            return coordinateConverter
                .convertLocalToScreen(positionInWindow.toWinUIXamlDipPoint(rasterizationScale))
                .toComposeScreenPixelOffset()
        }

        private fun UIElement.screenToLocal(
            positionOnScreen: Offset,
            rasterizationScale: Float,
        ): Offset {
            val xamlRoot = runCatching { xamlRoot }.getOrNull() ?: return positionOnScreen
            val coordinateConverter = xamlRoot.coordinateConverter ?: return positionOnScreen
            val positionInWindow = coordinateConverter
                .convertScreenToLocal(positionOnScreen.toWinUIScreenPixelPoint())
                .toComposePixelOffset(rasterizationScale)
            return calculateLocalPosition(positionInWindow, rasterizationScale)
        }

        private fun UIElement.rootTransformToWindow() = runCatching {
            val root = xamlRoot ?: return@runCatching null
            transformToVisual(root.content.asWinRTUIElement() ?: return@runCatching null)
        }.getOrNull()

        private fun Any?.asWinRTUIElement(): UIElement? {
            return asExistingInstance(UIElement::class.java)
                ?: runCatching { this?.asWinRT<UIElement>() }.getOrNull()
        }

        private fun <T> Any?.asExistingInstance(type: Class<T>): T? =
            if (this != null && type.isInstance(this)) type.cast(this) else null

    }
}

internal fun Offset.toWinUIXamlDipPoint(rasterizationScale: Float): Point {
    val scale = validateWinUIRasterizationScale(rasterizationScale)
    return Point(x / scale, y / scale)
}

private fun Offset.toWinUIScreenPixelPoint(): PointInt32 =
    PointInt32(x.toInt(), y.toInt())

internal fun Point.toComposePixelOffset(rasterizationScale: Float): Offset {
    val scale = validateWinUIRasterizationScale(rasterizationScale)
    return Offset(x * scale, y * scale)
}

internal fun PointInt32.toComposeScreenPixelOffset(): Offset =
    Offset(x.toFloat(), y.toFloat())
