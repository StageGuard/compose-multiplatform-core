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

import microsoft.ui.xaml.Window
import windows.foundation.Rect
import windows.foundation.TypedEventHandler
import windows.ui.viewmanagement.InputPane
import windows.ui.viewmanagement.InputPaneVisibilityEventArgs

internal data class WinUIInputPaneOccludedRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

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

internal expect fun acquireWinUIInputPane(window: Window): InputPane?

private class ProjectedWinUIInputPaneAdapter(
    private val inputPane: InputPane,
) : WinUIInputPaneAdapter {
    override fun tryShow(): Boolean = inputPane.tryShow()

    override fun tryHide(): Boolean = inputPane.tryHide()

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

    override fun dispose() {
        inputPane.nativeObject.close()
    }
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
