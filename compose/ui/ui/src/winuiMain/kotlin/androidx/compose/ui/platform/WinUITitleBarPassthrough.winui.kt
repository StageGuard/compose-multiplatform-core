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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
 * Unlike a hit test, which looks at one point, the regions are rectangles decided ahead of time, so
 * the content that counts is narrowed down:
 * - the caption buttons always stay buttons: the regions end at the insets the title bar reserves
 *   for them;
 * - of nested interactive elements only the innermost ones count, so that a clickable container
 *   does not cover the controls in it;
 * - an element taller than [MaxControlHeightInStrips] title bars is a container (a page with a
 *   click or context menu handler, say), not a control in the title bar, and leaves the strip a
 *   caption.
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
    private var titleBarLeftInset = 0
    private var titleBarRightInset = 0
    private var lastUpdate: TimeSource.Monotonic.ValueTimeMark? = null
    private var trailingUpdate: WinUIDispatcherTimer? = null
    private var isClosed = false

    /**
     * The title bar strip in pixels: its height, 0 when the content is not in the title bar, and the
     * insets that the caption buttons take at its sides.
     */
    fun setTitleBar(height: Int, leftInset: Int, rightInset: Int) {
        if (titleBarHeight == height && titleBarLeftInset == leftInset && titleBarRightInset == rightInset) {
            return
        }
        titleBarHeight = height
        titleBarLeftInset = leftInset
        titleBarRightInset = rightInset
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
        val regions = if (titleBarHeight > 0) {
            runCatching {
                semanticsOwner().titleBarPassthroughRegions(titleBarHeight, titleBarLeftInset, titleBarRightInset)
            }.getOrNull() ?: return
        } else {
            emptyList()
        }
        apply(regions.map { it.rect }) {
            regions.joinToString { region -> "${region.rect} from ${region.source}" }
        }
    }

    private fun apply(rects: List<IntRect>, sources: () -> String = { "" }) {
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
            debugRender { "title bar passthrough strip=$titleBarHeight rects=[${sources()}]" }
        }
    }

    private companion object {
        val UpdateInterval = 100.milliseconds
    }
}

/** How many title bar heights an element can be tall and still be a control in the title bar. */
internal const val MaxControlHeightInStrips = 3

internal class TitleBarPassthroughRegion(val rect: IntRect, val source: String)

/**
 * The passthrough regions of the title bar strip `[0, height)` at the top of the root, in root
 * pixels: the innermost interactive semantics nodes that overlap the strip and are not taller than
 * [MaxControlHeightInStrips] strips, clipped to the strip between the caption button insets.
 */
internal fun SemanticsOwner.titleBarPassthroughRegions(
    height: Int,
    leftInset: Int,
    rightInset: Int,
): List<TitleBarPassthroughRegion> {
    val root = unmergedRootSemanticsNode
    val right = floor(root.boundsInRoot.right).toInt() - rightInset
    val maxControlHeight = height * MaxControlHeightInStrips
    fun collect(node: SemanticsNode): List<TitleBarPassthroughRegion> {
        val bounds = node.boundsInRoot
        if (bounds.isEmpty || bounds.top >= height) return emptyList()
        val inner = node.children.flatMap(::collect)
        if (inner.isNotEmpty()) return inner
        val config = node.unmergedConfig
        if (!config.isInteractive() || bounds.height > maxControlHeight) return emptyList()
        val rect = bounds.clippedTo(left = leftInset, right = right, bottom = height) ?: return emptyList()
        return listOf(TitleBarPassthroughRegion(rect, config.describe(node.id)))
    }
    return collect(root)
}

private fun SemanticsConfiguration.isInteractive(): Boolean =
    contains(SemanticsActions.OnClick) ||
        contains(SemanticsActions.OnLongClick) ||
        contains(SemanticsActions.SetText) ||
        contains(SemanticsActions.SetProgress)

private fun SemanticsConfiguration.describe(id: Int): String {
    val actions = listOfNotNull(
        "click".takeIf { contains(SemanticsActions.OnClick) },
        "longClick".takeIf { contains(SemanticsActions.OnLongClick) },
        "setText".takeIf { contains(SemanticsActions.SetText) },
        "setProgress".takeIf { contains(SemanticsActions.SetProgress) },
    )
    val label = getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
        ?: getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        ?: getOrNull(SemanticsProperties.TestTag)
    return "node $id $actions" + (label?.let { " '$it'" } ?: "")
}

private fun Rect.clippedTo(left: Int, right: Int, bottom: Int): IntRect? {
    val clipped = IntRect(
        left = floor(this.left).toInt().coerceAtLeast(left),
        top = floor(top).toInt().coerceAtLeast(0),
        right = ceil(this.right).toInt().coerceAtMost(right),
        bottom = ceil(this.bottom).toInt().coerceAtMost(bottom),
    )
    return clipped.takeIf { it.width > 0 && it.height > 0 }
}
