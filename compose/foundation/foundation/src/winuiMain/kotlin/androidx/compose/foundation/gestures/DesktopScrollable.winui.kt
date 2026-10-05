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

package androidx.compose.foundation.gestures

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastFold

internal val LocalScrollConfig = compositionLocalOf<ScrollConfig> { WindowsWinUIConfig }

internal actual fun CompositionLocalConsumerModifierNode.platformScrollConfig(): ScrollConfig =
    currentValueOf(LocalScrollConfig)

internal expect fun isWinUISmoothScrollingEnabled(): Boolean

internal object WindowsWinUIConfig : ScrollConfig {
    override var isSmoothScrollingEnabled = isWinUISmoothScrollingEnabled()

    override fun isPreciseWheelScroll(event: PointerEvent): Boolean = false

    // The formula is the one of the desktop target on Windows, which multiplies by the number of
    // lines a wheel notch scrolls in the system settings (MouseWheelEvent.scrollAmount).
    override fun Density.calculateMouseWheelScroll(event: PointerEvent, bounds: IntSize): Offset {
        if (event.type == PointerEventType.PanMove) {
            return event.totalPanGestureOffset
        }

        val wheelScrollLines = systemWheelScrollLines()
        return if (wheelScrollLines == WheelPageScroll) {
            Offset(
                x = event.totalScrollDelta.x * bounds.width,
                y = event.totalScrollDelta.y * bounds.height
            ) * -1f
        } else {
            Offset(
                x = event.totalScrollDelta.x * (bounds.width / 20f),
                y = event.totalScrollDelta.y * (bounds.height / 20f)
            ) * -wheelScrollLines.toFloat()
        }
    }
}

/**
 * [systemWheelScrollLines] when the system scrolls one page per wheel notch.
 */
internal const val WheelPageScroll = -1

/**
 * The number of lines one wheel notch scrolls (`SPI_GETWHEELSCROLLLINES`), or [WheelPageScroll].
 */
internal expect fun systemWheelScrollLines(): Int

private val PointerEvent.totalScrollDelta
    get() = changes.fastFold(Offset.Zero) { acc, c -> acc + c.scrollDelta }

private val PointerEvent.totalPanGestureOffset
    get() = -changes.fastFold(Offset.Zero) { acc, c -> acc + c.panOffset }
