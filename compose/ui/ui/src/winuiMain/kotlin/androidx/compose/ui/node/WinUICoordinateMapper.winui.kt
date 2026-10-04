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
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.UIElement
import windows.foundation.Point
import windows.graphics.PointInt32

/**
 * Maps Compose root coordinates to the window and the screen.
 *
 * Compose positions are pixels, while XAML positions are device independent pixels (DIPs). The
 * conversion functions passed in work in DIPs for local and window positions and in physical pixels
 * for screen positions, as the XAML APIs do; [density] converts between the two.
 */
internal class WinUICoordinateMapper(
    private val calculatePositionInWindow: (Offset) -> Offset = { it },
    private val calculateLocalPosition: (Offset) -> Offset = { it },
    private val screenCoordinatesReady: () -> Boolean = { true },
    private val localToScreen: (Offset) -> Offset = { it },
    private val screenToLocal: (Offset) -> Offset = { it },
    private val density: () -> Float = { 1f },
) {
    private val scale: Float
        get() = density().takeIf { it.isFinite() && it > 0f } ?: 1f

    fun calculatePositionInWindow(localPosition: Offset): Offset {
        val scale = scale
        return calculatePositionInWindow.invoke(localPosition / scale) * scale
    }

    fun calculateLocalPosition(positionInWindow: Offset): Offset {
        val scale = scale
        return calculateLocalPosition.invoke(positionInWindow / scale) * scale
    }

    fun localToScreen(localPosition: Offset): Offset =
        if (screenCoordinatesReady()) {
            localToScreen.invoke(localPosition / scale)
        } else {
            calculatePositionInWindow(localPosition)
        }

    fun screenToLocal(positionOnScreen: Offset): Offset =
        if (screenCoordinatesReady()) {
            screenToLocal.invoke(positionOnScreen) * scale
        } else {
            positionOnScreen
        }

    fun localToScreen(localTransform: Matrix) {
        val screenOrigin = localToScreen(Offset.Zero)
        localTransform.translate(screenOrigin.x, screenOrigin.y)
    }

    companion object {
        fun forRoot(
            root: UIElement,
            screenCoordinatesReady: () -> Boolean = { true },
            density: () -> Float = { 1f },
        ): WinUICoordinateMapper =
            WinUICoordinateMapper(
                calculatePositionInWindow = { root.calculatePositionInWindow(it) },
                calculateLocalPosition = { root.calculateLocalPosition(it) },
                screenCoordinatesReady = screenCoordinatesReady,
                localToScreen = { root.localToScreen(it) },
                screenToLocal = { root.screenToLocal(it) },
                density = density,
            )

        private fun UIElement.calculatePositionInWindow(localPosition: Offset): Offset {
            val transform = rootTransformToWindow() ?: return localPosition
            return transform.transformPoint(localPosition.toWinRTPoint()).toOffset()
        }

        private fun UIElement.calculateLocalPosition(positionInWindow: Offset): Offset {
            val transform = rootTransformToWindow()?.inverse ?: return positionInWindow
            return transform.transformPoint(positionInWindow.toWinRTPoint()).toOffset()
        }

        private fun UIElement.localToScreen(localPosition: Offset): Offset {
            val xamlRoot = runCatching { xamlRoot }.getOrNull() ?: return localPosition
            val positionInWindow = calculatePositionInWindow(localPosition)
            val coordinateConverter = xamlRoot.coordinateConverter ?: return positionInWindow
            return coordinateConverter
                .convertLocalToScreen(positionInWindow.toWinRTPoint())
                .toOffset()
        }

        private fun UIElement.screenToLocal(positionOnScreen: Offset): Offset {
            val xamlRoot = runCatching { xamlRoot }.getOrNull() ?: return positionOnScreen
            val coordinateConverter = xamlRoot.coordinateConverter ?: return positionOnScreen
            val positionInWindow = coordinateConverter
                .convertScreenToLocal(positionOnScreen.toWinRTPointInt32())
                .toOffset()
            return calculateLocalPosition(positionInWindow)
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

        private fun Offset.toWinRTPoint(): Point = Point(x, y)

        private fun Offset.toWinRTPointInt32(): PointInt32 =
            PointInt32(x.toInt(), y.toInt())

        private fun Point.toOffset(): Offset = Offset(x, y)

        private fun PointInt32.toOffset(): Offset = Offset(x.toFloat(), y.toFloat())
    }
}
