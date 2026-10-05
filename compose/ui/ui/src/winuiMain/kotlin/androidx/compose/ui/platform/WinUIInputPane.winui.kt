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

import androidx.compose.ui.window.winuiWindowHandle
import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.InspectableReference
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.RawAddress
import microsoft.ui.xaml.Window
import windows.foundation.Rect
import windows.foundation.TypedEventHandler
import windows.ui.viewmanagement.InputPane
import windows.ui.viewmanagement.InputPaneVisibilityEventArgs
import kotlin.math.roundToInt

internal data class WinUIInputPaneOccludedRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

internal fun calculateWinUIImeBottomInset(
    rootHeightDp: Float,
    occludedRect: WinUIInputPaneOccludedRect,
    density: Float,
): Int {
    if (!rootHeightDp.isFinite() || rootHeightDp <= 0f) return 0
    if (!density.isFinite() || density <= 0f) return 0
    if (
        !occludedRect.x.isFinite() ||
        !occludedRect.y.isFinite() ||
        !occludedRect.width.isFinite() ||
        !occludedRect.height.isFinite() ||
        occludedRect.width <= 0f ||
        occludedRect.height <= 0f
    ) {
        return 0
    }

    val occludedBottom = occludedRect.y + occludedRect.height
    if (!occludedBottom.isFinite() || occludedBottom < rootHeightDp) return 0

    val occludedTop = occludedRect.y.coerceAtLeast(0f)
    if (occludedTop >= rootHeightDp) return 0

    val overlapPx = (rootHeightDp - occludedTop) * density
    if (!overlapPx.isFinite() || overlapPx <= 0f) return 0
    return overlapPx.roundToInt().coerceIn(0, 0xFFFF)
}

internal fun interface WinUIInputPaneEventRegistration {
    fun dispose()
}

internal interface WinUIInputPaneAdapter {
    fun tryShow(): Boolean

    fun tryHide(): Boolean

    fun addShowing(
        handler: (WinUIInputPaneOccludedRect) -> Unit,
    ): WinUIInputPaneEventRegistration

    fun addHiding(
        handler: () -> Unit,
    ): WinUIInputPaneEventRegistration

    fun currentOccludedRect(): WinUIInputPaneOccludedRect?

    fun dispose() {}
}

internal class WinUIInputPaneController(
    private val inputPane: WinUIInputPaneAdapter,
    private val onOccludedRectChanged: (WinUIInputPaneOccludedRect?) -> Unit = {},
) {
    private var isDisposed = false
    private var showingRegistration: WinUIInputPaneEventRegistration? = null
    private var hidingRegistration: WinUIInputPaneEventRegistration? = null

    init {
        val showing = inputPane.addShowing { occludedRect ->
            if (!isDisposed) {
                onOccludedRectChanged(occludedRect)
            }
        }
        showingRegistration = showing
        try {
            hidingRegistration = inputPane.addHiding {
                if (!isDisposed) {
                    onOccludedRectChanged(null)
                }
            }
        } catch (throwable: Throwable) {
            showingRegistration = null
            showing.dispose()
            throw throwable
        }
        runCatching { inputPane.currentOccludedRect() }
            .getOrNull()
            ?.let(onOccludedRectChanged)
    }

    fun show(): Boolean =
        !isDisposed && runCatching(inputPane::tryShow).getOrDefault(false)

    fun hide(): Boolean =
        !isDisposed && runCatching(inputPane::tryHide).getOrDefault(false)

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        val showingFailure = runCatching { showingRegistration?.dispose() }.exceptionOrNull()
        showingRegistration = null
        val hidingFailure = runCatching { hidingRegistration?.dispose() }.exceptionOrNull()
        hidingRegistration = null
        val paneFailure = runCatching { inputPane.dispose() }.exceptionOrNull()
        onOccludedRectChanged(null)
        showingFailure?.let { firstFailure ->
            hidingFailure?.let(firstFailure::addSuppressed)
            paneFailure?.let(firstFailure::addSuppressed)
            throw firstFailure
        }
        hidingFailure?.let { secondFailure ->
            paneFailure?.let(secondFailure::addSuppressed)
            throw secondFailure
        }
        paneFailure?.let { throw it }
    }
}

internal fun createWinUIInputPaneController(
    window: Window,
    onOccludedRectChanged: (WinUIInputPaneOccludedRect?) -> Unit = {},
): WinUIInputPaneController? = runCatching {
    val inputPane = acquireWinUIInputPane(window) ?: return null
    WinUIInputPaneController(
        inputPane = ProjectedWinUIInputPaneAdapter(inputPane),
        onOccludedRectChanged = onOccludedRectChanged,
    )
}.getOrNull()

internal fun acquireWinUIInputPane(window: Window): InputPane? =
    runCatching {
        val windowHandle = winuiWindowHandle(window)
        check(windowHandle != RawAddress.Null) { "WinUI window does not have an HWND." }
        winUIInputPaneForWindow(windowHandle)
    }.getOrNull()

private val IInputPaneInteropIid = Guid("75CF2C57-9195-4931-8332-F0B409E916AF")

/**
 * The input pane of the window [windowHandle], from `IInputPaneInterop.GetForWindow`, which a
 * desktop application has to use instead of `InputPane.GetForCurrentView`.
 *
 * KWINRT-077: kotlin-winrt has a generated helper for this,
 * `windows.ui.viewmanagement.InputPaneInterop`, but does not generate it for this module (see
 * [winuiWindowHandle]). This is the call that the helper makes.
 */
private fun winUIInputPaneForWindow(windowHandle: RawAddress): InputPane =
    PlatformAbi.confinedScope().use { scope ->
        val inputPaneIid = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
        PlatformAbi.writeGuid(inputPaneIid, InputPane.DEFAULT_INTERFACE_IID)
        val inputPaneOut = PlatformAbi.allocatePointerSlot(scope)
        ActivationFactory.get(InputPane.TYPE_NAME, IInputPaneInteropIid).use { interop ->
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = interop.pointer,
                    slot = 6,
                    arg0 = windowHandle,
                    arg1 = inputPaneIid,
                    arg2 = inputPaneOut,
                ),
            ).requireSuccess("IInputPaneInterop.GetForWindow")
            InputPane.wrap(
                InspectableReference(
                    pointer = PlatformAbi.toRawComPtr(PlatformAbi.readPointer(inputPaneOut)),
                    interfaceId = InputPane.DEFAULT_INTERFACE_IID,
                ),
            )
        }
    }

private class ProjectedWinUIInputPaneAdapter(
    private val inputPane: InputPane,
) : WinUIInputPaneAdapter {
    override fun tryShow(): Boolean = inputPane.tryShow()

    override fun tryHide(): Boolean = inputPane.tryHide()

    override fun currentOccludedRect(): WinUIInputPaneOccludedRect? =
        inputPane.occludedRect.toWinUIInputPaneOccludedRectOrNull()

    override fun addShowing(
        handler: (WinUIInputPaneOccludedRect) -> Unit,
    ): WinUIInputPaneEventRegistration {
        val winRTHandler: TypedEventHandler<InputPane, InputPaneVisibilityEventArgs> = { _, args ->
            handler(args.occludedRect.toWinUIInputPaneOccludedRect())
        }
        val token = inputPane.showing.add(winRTHandler)
        return ProjectedWinUIInputPaneEventRegistration(winRTHandler) {
            inputPane.showing.remove(token)
        }
    }

    override fun addHiding(
        handler: () -> Unit,
    ): WinUIInputPaneEventRegistration {
        val winRTHandler: TypedEventHandler<InputPane, InputPaneVisibilityEventArgs> = { _, _ ->
            handler()
        }
        val token = inputPane.hiding.add(winRTHandler)
        return ProjectedWinUIInputPaneEventRegistration(winRTHandler) {
            inputPane.hiding.remove(token)
        }
    }

    // The input pane of a window is one object, and every Compose view of the window (a window
    // popup has a view of its own) gets the same projection of it. Its reference is therefore
    // not closed with one of the views: the others would fail to remove their handlers.
    override fun dispose() = Unit
}

private class ProjectedWinUIInputPaneEventRegistration(
    private var retainedHandler: Any?,
    private var removeAction: (() -> Unit)?,
) : WinUIInputPaneEventRegistration {
    override fun dispose() {
        val remove = removeAction ?: return
        removeAction = null
        try {
            remove()
        } finally {
            retainedHandler = null
        }
    }
}

private fun Rect.toWinUIInputPaneOccludedRect(): WinUIInputPaneOccludedRect =
    WinUIInputPaneOccludedRect(
        x = x,
        y = y,
        width = width,
        height = height,
    )

private fun Rect.toWinUIInputPaneOccludedRectOrNull(): WinUIInputPaneOccludedRect? {
    if (
        !x.isFinite() ||
        !y.isFinite() ||
        !width.isFinite() ||
        !height.isFinite() ||
        width <= 0f ||
        height <= 0f
    ) {
        return null
    }
    return toWinUIInputPaneOccludedRect()
}
