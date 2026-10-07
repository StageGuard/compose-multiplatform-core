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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.unit.IntRect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import microsoft.ui.input.InputNonClientPointerSource
import microsoft.ui.input.NonClientRegionKind
import microsoft.ui.xaml.Window
import org.jetbrains.skiko.winui.WinUIDispatcherTimer
import windows.graphics.RectInt32

/**
 * Keeps the interactive content in the title bar of a window whose content extends into the title
 * bar clickable.
 *
 * With `Window.ExtendsContentIntoTitleBar`, XAML makes the title bar strip a caption region of the
 * window: the pointer input there moves the window and never reaches the content. A desktop
 * window hit-tests the content first and only treats the points without clickable content as the
 * caption. The same is done here with the regions of the window's [InputNonClientPointerSource]:
 * the elements of the content that can be clicked, edited or adjusted and overlap the strip become
 * passthrough regions, from the semantics of the content (popups and dialogs included, they are
 * layers of the same root).
 *
 * The regions follow the layout, at most every [UpdateInterval] while it keeps changing (scrolling,
 * animations) and once more after it settles.
 */
internal class WinUITitleBarPassthrough(
    private val window: Window,
    private val semanticsOwner: () -> SemanticsOwner,
) {
    private val source: InputNonClientPointerSource? by lazy {
        runCatching {
            InputNonClientPointerSource.getForWindowId(requireNotNull(window.appWindow).id)
        }.getOrNull()
    }
    private var appliedRects: List<IntRect> = emptyList()
    private var titleBarHeight = 0
    private var lastUpdate: TimeSource.Monotonic.ValueTimeMark? = null
    private var trailingUpdate: WinUIDispatcherTimer? = null
    private var isClosed = false

    /** The height in pixels of the title bar strip, 0 when the content is not in the title bar. */
    fun setTitleBarHeight(height: Int) {
        if (titleBarHeight == height) return
        titleBarHeight = height
        update()
    }

    /** Called after each layout of the content. */
    fun onLayout() {
        if (isClosed || titleBarHeight <= 0) return
        val last = lastUpdate
        if (last == null || last.elapsedNow() >= UpdateInterval) {
            update()
        } else {
            val timer = trailingUpdate ?: WinUIDispatcherTimer(
                interval = UpdateInterval,
                repeating = false,
                onTick = ::update,
            ).also { trailingUpdate = it }
            if (!timer.isRunning) timer.start()
        }
    }

    fun close() {
        if (isClosed) return
        apply(emptyList())
        isClosed = true
        trailingUpdate?.close()
        trailingUpdate = null
    }

    private fun update() {
        if (isClosed) return
        lastUpdate = TimeSource.Monotonic.markNow()
        val rects = if (titleBarHeight > 0) {
            runCatching { semanticsOwner().interactiveRectsAbove(titleBarHeight) }.getOrDefault(appliedRects)
        } else {
            emptyList()
        }
        apply(rects)
    }

    private fun apply(rects: List<IntRect>) {
        if (rects == appliedRects) return
        val source = source ?: return
        runCatching {
            if (rects.isEmpty()) {
                source.clearRegionRects(NonClientRegionKind.Passthrough)
            } else {
                source.setRegionRects(
                    NonClientRegionKind.Passthrough,
                    rects.map { RectInt32(it.left, it.top, it.width, it.height) }.toTypedArray(),
                )
            }
            appliedRects = rects
            debugRender { "title bar passthrough strip=$titleBarHeight rects=$rects" }
        }
    }

    private companion object {
        val UpdateInterval = 100.milliseconds
    }
}

/**
 * The bounds in root pixels, clipped to the strip, of the interactive semantics nodes that overlap
 * the strip `[0, height)` at the top of the root. A node that is interactive covers its subtree.
 */
internal fun SemanticsOwner.interactiveRectsAbove(height: Int): List<IntRect> {
    val rects = ArrayList<IntRect>()
    fun visit(node: SemanticsNode) {
        val bounds = node.boundsInRoot
        if (bounds.isEmpty || bounds.top >= height) return
        if (node.unmergedConfig.isInteractive()) {
            bounds.clippedTo(height)?.let(rects::add)
            return
        }
        node.children.forEach(::visit)
    }
    visit(unmergedRootSemanticsNode)
    return rects
}

private fun SemanticsConfiguration.isInteractive(): Boolean =
    contains(SemanticsActions.OnClick) ||
        contains(SemanticsActions.OnLongClick) ||
        contains(SemanticsActions.SetText) ||
        contains(SemanticsActions.SetProgress)

private fun Rect.clippedTo(height: Int): IntRect? {
    val clipped = IntRect(
        left = floor(left).toInt(),
        top = floor(top).toInt().coerceAtLeast(0),
        right = ceil(right).toInt(),
        bottom = ceil(bottom).toInt().coerceAtMost(height),
    )
    return clipped.takeIf { it.width > 0 && it.height > 0 }
}
