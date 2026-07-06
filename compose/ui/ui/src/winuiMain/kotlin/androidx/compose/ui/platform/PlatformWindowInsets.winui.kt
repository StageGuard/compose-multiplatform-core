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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.jvm.JvmInline

@InternalComposeUiApi
val LocalPlatformWindowInsets = staticCompositionLocalOf<PlatformWindowInsets> {
    error("CompositionLocal LocalPlatformWindowInsets not present")
}

@InternalComposeUiApi
interface PlatformWindowInsets {
    val displayCutouts: List<Rect> get() = emptyList()
    val cutoutPath: Path? get() = null
    val captionBarLeftPadding: Int get() = 0
    val captionBarRightPadding: Int get() = 0
    val captionBar: PlatformInsets get() = PlatformInsets.Zero
    val displayCutout: PlatformInsets get() = PlatformInsets.Zero
    val ime: PlatformInsets get() = PlatformInsets.Zero
    val mandatorySystemGestures: PlatformInsets get() = PlatformInsets.Zero
    val navigationBars: PlatformInsets get() = PlatformInsets.Zero
    val statusBars: PlatformInsets get() = PlatformInsets.Zero
    val systemBars: PlatformInsets get() = PlatformInsets.Zero
    val systemGestures: PlatformInsets get() = PlatformInsets.Zero
    val tappableElement: PlatformInsets get() = PlatformInsets.Zero
    val waterfall: PlatformInsets get() = PlatformInsets.Zero

    fun excluding(
        safeInsets: Boolean = true,
        ime: Boolean = true,
    ): PlatformWindowInsets = this
}

@Composable
internal fun PlatformWindowInsets.exclude(
    safeInsets: Boolean,
    ime: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPlatformWindowInsets provides excluding(safeInsets, ime),
        content = content,
    )
}

internal object EmptyPlatformWindowInsets : PlatformWindowInsets

internal data class WinUIPlatformWindowInsets(
    private val captionBarHeight: Int = 0,
    override val captionBarLeftPadding: Int = 0,
    override val captionBarRightPadding: Int = 0,
) : PlatformWindowInsets {
    override val captionBar: PlatformInsets = PlatformInsets(
        left = captionBarLeftPadding,
        top = captionBarHeight,
        right = captionBarRightPadding,
        bottom = 0,
    )
    override val systemBars: PlatformInsets = captionBar

    override fun excluding(
        safeInsets: Boolean,
        ime: Boolean,
    ): PlatformWindowInsets {
        if (!safeInsets && !ime) return this
        if (!safeInsets) return this

        return WinUIPlatformWindowInsets()
    }
}

@InternalComposeUiApi
val PlatformWindowInsets.safeDrawing: PlatformInsets get() = object : PlatformInsets {
    override val left: Int get() = maxOf(displayCutout.left, ime.left, systemBars.left)
    override val top: Int get() = maxOf(displayCutout.top, ime.top, systemBars.top)
    override val right: Int get() = maxOf(displayCutout.right, ime.right, systemBars.right)
    override val bottom: Int get() = maxOf(displayCutout.bottom, ime.bottom, systemBars.bottom)
}

@InternalComposeUiApi
val PlatformWindowInsets.safeGestures: PlatformInsets get() = object : PlatformInsets {
    override val left: Int get() = maxOf(
        mandatorySystemGestures.left,
        systemGestures.left,
        tappableElement.left,
        waterfall.left,
    )
    override val top: Int get() = maxOf(
        mandatorySystemGestures.top,
        systemGestures.top,
        tappableElement.top,
        waterfall.top,
    )
    override val right: Int get() = maxOf(
        mandatorySystemGestures.right,
        systemGestures.right,
        tappableElement.right,
        waterfall.right,
    )
    override val bottom: Int get() = maxOf(
        mandatorySystemGestures.bottom,
        systemGestures.bottom,
        tappableElement.bottom,
        waterfall.bottom,
    )
}

@InternalComposeUiApi
val PlatformWindowInsets.safeContent: PlatformInsets get() = object : PlatformInsets {
    override val left: Int get() = maxOf(safeDrawing.left, safeGestures.left)
    override val top: Int get() = maxOf(safeDrawing.top, safeGestures.top)
    override val right: Int get() = maxOf(safeDrawing.right, safeGestures.right)
    override val bottom: Int get() = maxOf(safeDrawing.bottom, safeGestures.bottom)
}

@InternalComposeUiApi
interface PlatformInsets {
    val left: Int
    val top: Int
    val right: Int
    val bottom: Int

    companion object {
        val Zero: PlatformInsets = ValuePlatformInsets(0L)
    }
}

@InternalComposeUiApi
fun PlatformInsets(
    getLeft: () -> Int = { 0 },
    getTop: () -> Int = { 0 },
    getRight: () -> Int = { 0 },
    getBottom: () -> Int = { 0 },
): PlatformInsets = DynamicPlatformInsets(getLeft, getTop, getRight, getBottom)

internal fun PlatformInsets.exclude(insets: PlatformInsets) = PlatformInsets(
    getLeft = { (left - insets.left).coerceAtLeast(0) },
    getTop = { (top - insets.top).coerceAtLeast(0) },
    getRight = { (right - insets.right).coerceAtLeast(0) },
    getBottom = { (bottom - insets.bottom).coerceAtLeast(0) },
)

internal fun PlatformInsets.union(insets: PlatformInsets) = PlatformInsets(
    getLeft = { maxOf(left, insets.left) },
    getTop = { maxOf(top, insets.top) },
    getRight = { maxOf(right, insets.right) },
    getBottom = { maxOf(bottom, insets.bottom) },
)

internal fun List<PlatformInsets>.union(): PlatformInsets = if (isEmpty()) {
    PlatformInsets.Zero
} else {
    reduce { acc, insets -> acc.union(insets) }
}

private class DynamicPlatformInsets(
    private val getLeft: () -> Int = { 0 },
    private val getTop: () -> Int = { 0 },
    private val getRight: () -> Int = { 0 },
    private val getBottom: () -> Int = { 0 },
) : PlatformInsets {
    override val left: Int get() = getLeft()
    override val top: Int get() = getTop()
    override val right: Int get() = getRight()
    override val bottom: Int get() = getBottom()
}

@InternalComposeUiApi
fun Density.PlatformInsets(
    left: Dp = 0.dp,
    top: Dp = 0.dp,
    right: Dp = 0.dp,
    bottom: Dp = 0.dp,
): PlatformInsets = ValuePlatformInsets(
    left.roundToPx(),
    top.roundToPx(),
    right.roundToPx(),
    bottom.roundToPx(),
)

@InternalComposeUiApi
fun PlatformInsets(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
): PlatformInsets = ValuePlatformInsets(left, top, right, bottom)

@JvmInline
private value class ValuePlatformInsets(
    val packedValue: Long,
) : PlatformInsets {

    constructor(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ) : this(checkBoundsAndPackInsets(left, top, right, bottom))

    override val left: Int
        get() = ((packedValue ushr 48) and 0xFFFF).toInt()

    override val top: Int
        get() = ((packedValue ushr 32) and 0xFFFF).toInt()

    override val right: Int
        get() = ((packedValue ushr 16) and 0xFFFF).toInt()

    override val bottom: Int
        get() = (packedValue and 0xFFFF).toInt()

    companion object {
        private fun checkBoundsAndPackInsets(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ): Long {
            checkBounds(left, "left")
            checkBounds(top, "top")
            checkBounds(right, "right")
            checkBounds(bottom, "bottom")
            return (left.toLong() shl 48) or
                (top.toLong() shl 32) or
                (right.toLong() shl 16) or
                bottom.toLong()
        }

        private fun checkBounds(value: Int, name: String) {
            check(value in 0..0xFFFF) {
                "$name should be in 0..0xFFFF range, but was $value"
            }
        }
    }
}
