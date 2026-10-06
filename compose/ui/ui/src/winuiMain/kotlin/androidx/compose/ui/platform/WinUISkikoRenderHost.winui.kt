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

import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.Window
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkikoRenderDelegate
import org.jetbrains.skiko.winui.WinUIAccessibilityProvider
import org.jetbrains.skiko.winui.WinUIFrameScheduler
import org.jetbrains.skiko.winui.WinUIIndirectPointerEvent
import org.jetbrains.skiko.winui.WinUIInputHandler
import org.jetbrains.skiko.winui.WinUISkiaLayer
import org.jetbrains.skiko.winui.bindWinUIIndirectPointerInput

internal class WinUISkikoRenderHost(
    private val layer: WinUISkikoLayerAdapter,
    private val renderDelegateCloseable: AutoCloseable? = null,
    private val beforeDrawSubmission: () -> Unit = {},
) : AutoCloseable {
    constructor(
        renderDelegate: SkikoRenderDelegate? = null,
        beforeDrawSubmission: () -> Unit = {},
    ) : this(
        layer = DefaultWinUISkikoLayerAdapter(renderDelegate),
        renderDelegateCloseable = renderDelegate as? AutoCloseable,
        beforeDrawSubmission = beforeDrawSubmission,
    )

    private var frameScheduler: AutoCloseable? = null
    private var indirectPointerInputBinding: AutoCloseable? = null
    private var isClosed = false
    private var isSurfaceAttached = false
    private var requestedSurfaceSize: IntSize? = null
    private var requestedSurfaceDensity: Density = Density(1f)
    private var appliedSurfaceSize: WinUISkikoSurfaceSize? = null
    private var pendingRenderInvalidation = false
    private var renderInvalidationCount = 0
    private var delegatedRenderInvalidationCount = 0
    private var drawSubmissionCount = 0
    private var interopTransactionDrainCount = 0
    private var accessibilityUpdateCount = 0

    val component: FrameworkElement
        get() = layer.component

    /** The Skiko layer of this host, for [LocalWinUISkiaLayer]. */
    val skiaLayer: WinUISkiaLayer?
        get() = layer.skiaLayer

    /**
     * The elements whose key events are Compose key events: the host and the swap chain panel in
     * it, which has the XAML focus after a click into the content.
     */
    val keyEventSources: List<FrameworkElement>
        get() = layer.keyEventSources

    val renderApiForTest: GraphicsApi
        get() = layer.renderApi

    val renderVersionForTest: Long
        get() = layer.renderVersion

    val lastRenderSizeForTest: IntSize?
        get() = layer.lastRenderSize

    val lastRenderedStateSizeForTest: IntSize?
        get() = layer.lastRenderedStateSize

    val pendingRenderStateSizeForTest: IntSize?
        get() = layer.pendingRenderStateSize

    val renderFailureForTest: String?
        get() = layer.renderFailure

    val isFrameSchedulerStartedForTest: Boolean
        get() = frameScheduler != null

    val accessibilityUpdateCountForTest: Int
        get() = accessibilityUpdateCount

    val diagnosticsForTest: WinUISkikoRenderHostDiagnostics
        get() = diagnostics()

    fun setAccessibilityProvider(provider: WinUIAccessibilityProvider) {
        if (!isClosed) {
            layer.setAccessibilityProvider(provider)
        }
    }

    fun notifyAccessibilityChanged(update: WinUIAccessibilityUpdate) {
        if (!isClosed) {
            layer.notifyAccessibilityChanged(update)
            accessibilityUpdateCount += 1
        }
    }

    fun bindIndirectPointerInput(
        window: Window,
        onEvent: (WinUIIndirectPointerEvent) -> Boolean,
        onCancel: () -> Unit,
    ) {
        check(!isClosed) {
            "Cannot bind WinUI indirect pointer input after the render host is closed."
        }
        closeIndirectPointerInput()
        layer.inputHandler = object : WinUIInputHandler {
            override fun onIndirectPointerEvent(event: WinUIIndirectPointerEvent): Boolean =
                onEvent(event)

            override fun onIndirectPointerCancel() {
                onCancel()
            }
        }
        try {
            indirectPointerInputBinding = layer.bindIndirectPointerInput(window)
        } catch (throwable: Throwable) {
            layer.inputHandler = null
            throw throwable
        }
    }

    fun closeIndirectPointerInput() {
        if (isClosed) return
        closeIndirectPointerInputPreserving(null)?.let { throw it }
    }

    fun requestRender(throttledToVsync: Boolean = true) {
        if (!isClosed) {
            renderInvalidationCount += 1
            // Every request goes to the layer, whose render dispatcher coalesces them. Holding
            // requests back here until the next draw lost them when the layer did not draw for a
            // request, for example before its surface had a size: nothing was drawn again until
            // an input event, with no continuous frame scheduler running.
            pendingRenderInvalidation = true
            delegatedRenderInvalidationCount += 1
            debugRender {
                "layer.requestRender throttled=$throttledToVsync " +
                    "invalidations=$renderInvalidationCount delegated=$delegatedRenderInvalidationCount"
            }
            layer.requestRender(throttledToVsync)
        }
    }

    fun setSize(size: IntSize, density: Density = Density(1f)) {
        if (!isClosed) {
            requestedSurfaceSize = size
            requestedSurfaceDensity = density
            applyRequestedSurfaceSize()
        }
    }

    fun attachSurface() {
        check(!isClosed) {
            "Cannot attach a WinUI Skiko surface after the render host is closed."
        }
        if (isSurfaceAttached) return
        isSurfaceAttached = true
        applyRequestedSurfaceSize()
    }

    fun detachSurface() {
        if (isClosed || !isSurfaceAttached) return
        isSurfaceAttached = false
        closeFrameScheduler()
    }

    fun startFrameScheduler(): AutoCloseable {
        check(!isClosed) {
            "Cannot start a WinUI Skiko frame scheduler after the render host is closed."
        }
        attachSurface()
        debugRender { "startFrameScheduler alreadyStarted=${frameScheduler != null}" }
        return frameScheduler ?: layer.startFrameScheduler().also { frameScheduler = it }
    }

    fun performDrawSubmission(draw: () -> Unit) {
        if (isClosed) return
        drawSubmissionCount += 1
        interopTransactionDrainCount += 1
        debugRender {
            "drawSubmit begin submissions=$drawSubmissionCount " +
                "renderVersion=${layer.renderVersion} size=${layer.lastRenderSize}"
        }
        beforeDrawSubmission()
        pendingRenderInvalidation = false
        try {
            draw()
        } finally {
            debugRender {
                "drawSubmit end submissions=$drawSubmissionCount " +
                    "renderVersion=${layer.renderVersion} size=${layer.lastRenderSize} " +
                    "failure=${layer.renderFailure}"
            }
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        isSurfaceAttached = false
        var failure: Throwable? = null
        failure = closeIndirectPointerInputPreserving(failure)
        failure = closeFrameSchedulerPreserving(failure)
        failure = layer.closePreserving(failure)
        failure = renderDelegateCloseable.closePreserving(failure)
        failure?.let { throw it }
    }

    private fun applyRequestedSurfaceSize() {
        val size = requestedSurfaceSize
        if (size != null) {
            val surfaceSize = size.toWinUISkikoSurfaceSize(requestedSurfaceDensity)
            if (surfaceSize != appliedSurfaceSize) {
                appliedSurfaceSize = surfaceSize
                layer.setSize(surfaceSize)
            }
        }
    }

    private fun closeFrameScheduler() {
        closeFrameSchedulerPreserving(null)?.let { throw it }
    }

    private fun closeFrameSchedulerPreserving(previousFailure: Throwable?): Throwable? {
        val scheduler = frameScheduler
        frameScheduler = null
        debugRender { "closeFrameScheduler hadScheduler=${scheduler != null}" }
        return scheduler.closePreserving(previousFailure)
    }

    private fun closeIndirectPointerInputPreserving(previousFailure: Throwable?): Throwable? {
        val binding = indirectPointerInputBinding
        indirectPointerInputBinding = null
        var failure = binding.closePreserving(previousFailure)
        try {
            layer.inputHandler = null
        } catch (throwable: Throwable) {
            failure?.addSuppressed(throwable) ?: run { failure = throwable }
        }
        return failure
    }

    private fun diagnostics(): WinUISkikoRenderHostDiagnostics =
        WinUISkikoRenderHostDiagnostics(
            isClosed = isClosed,
            isSurfaceAttached = isSurfaceAttached,
            isFrameSchedulerStarted = frameScheduler != null,
            requestedSurfaceSize = requestedSurfaceSize,
            appliedSurfaceSize = appliedSurfaceSize?.physicalSize,
            pendingRenderInvalidation = pendingRenderInvalidation,
            renderInvalidationCount = renderInvalidationCount,
            delegatedRenderInvalidationCount = delegatedRenderInvalidationCount,
            drawSubmissionCount = drawSubmissionCount,
            interopTransactionDrainCount = interopTransactionDrainCount,
            renderApi = layer.renderApi,
            renderVersion = layer.renderVersion,
            lastRenderSize = layer.lastRenderSize,
            lastRenderedStateSize = layer.lastRenderedStateSize,
            pendingRenderStateSize = layer.pendingRenderStateSize,
            renderFailure = layer.renderFailure,
        )

    private fun AutoCloseable?.closePreserving(previousFailure: Throwable?): Throwable? {
        if (this == null) return previousFailure
        var failure = previousFailure
        try {
            close()
        } catch (e: Throwable) {
            failure?.addSuppressed(e) ?: run { failure = e }
        }
        return failure
    }
}

internal val isWinUIRenderDebugEnabled: Boolean by lazy {
    winUISystemBooleanProperty("compose.winui.render.debug")
}

internal inline fun debugRender(message: () -> String) {
    if (isWinUIRenderDebugEnabled) {
        println("[compose-winui:render] ${message()}")
    }
}

internal data class WinUISkikoRenderHostDiagnostics(
    val isClosed: Boolean,
    val isSurfaceAttached: Boolean,
    val isFrameSchedulerStarted: Boolean,
    val requestedSurfaceSize: IntSize?,
    val appliedSurfaceSize: IntSize?,
    val pendingRenderInvalidation: Boolean,
    val renderInvalidationCount: Int,
    val delegatedRenderInvalidationCount: Int,
    val drawSubmissionCount: Int,
    val interopTransactionDrainCount: Int,
    val renderApi: GraphicsApi,
    val renderVersion: Long,
    val lastRenderSize: IntSize?,
    val lastRenderedStateSize: IntSize?,
    val pendingRenderStateSize: IntSize?,
    val renderFailure: String?,
)

internal interface WinUISkikoLayerAdapter : AutoCloseable {
    val component: FrameworkElement

    /** The Skiko layer behind this adapter, when it is a real one (null for fakes). */
    val skiaLayer: WinUISkiaLayer?
        get() = null

    val keyEventSources: List<FrameworkElement>
        get() = listOf(component)

    var inputHandler: WinUIInputHandler?

    val renderApi: GraphicsApi

    val renderVersion: Long

    val lastRenderSize: IntSize?

    val lastRenderedStateSize: IntSize?

    val pendingRenderStateSize: IntSize?

    val renderFailure: String?

    fun setAccessibilityProvider(provider: WinUIAccessibilityProvider)

    fun notifyAccessibilityChanged(update: WinUIAccessibilityUpdate)

    fun requestRender(throttledToVsync: Boolean)

    fun setSize(size: WinUISkikoSurfaceSize)

    fun bindIndirectPointerInput(window: Window): AutoCloseable

    fun startFrameScheduler(): AutoCloseable
}

internal data class WinUISkikoSurfaceSize(
    val physicalSize: IntSize,
    val xamlWidth: Double,
    val xamlHeight: Double,
)

internal fun IntSize.toWinUISkikoSurfaceSize(density: Density): WinUISkikoSurfaceSize {
    val xamlSize = toWinUIXamlSize(density.density)
    return WinUISkikoSurfaceSize(
        physicalSize = this,
        xamlWidth = xamlSize.width,
        xamlHeight = xamlSize.height,
    )
}

private class DefaultWinUISkikoLayerAdapter(
    renderDelegate: SkikoRenderDelegate?,
) : WinUISkikoLayerAdapter {
    private val layer = WinUISkiaLayer(renderDelegate)

    override val skiaLayer: WinUISkiaLayer
        get() = layer

    override val component: FrameworkElement
        get() = layer.component

    override val keyEventSources: List<FrameworkElement>
        get() = listOf(layer.component, layer.renderPanel)

    override var inputHandler: WinUIInputHandler?
        get() = layer.inputHandler
        set(value) {
            layer.inputHandler = value
        }

    override val renderApi: GraphicsApi
        get() = layer.renderApi

    override val renderVersion: Long
        get() = layer.renderDiagnostics.renderVersion

    override val lastRenderSize: IntSize?
        get() = layer.renderDiagnostics.lastPlatformResult?.let {
            IntSize(width = it.width, height = it.height)
        }

    override val lastRenderedStateSize: IntSize?
        get() = layer.renderDiagnostics.lastRenderedState?.let {
            IntSize(width = it.scaledWidth, height = it.scaledHeight)
        }

    override val pendingRenderStateSize: IntSize?
        get() = layer.renderDiagnostics.pendingInvalidatedState?.let {
            IntSize(width = it.scaledWidth, height = it.scaledHeight)
        }

    override val renderFailure: String?
        get() = layer.renderDiagnostics.lastFailure?.let {
            listOfNotNull(
                it.exceptionClass,
                it.message,
                it.causeClass,
                it.causeMessage,
            )
                .joinToString(separator = ": ")
        }

    override fun setAccessibilityProvider(provider: WinUIAccessibilityProvider) {
        layer.accessibilityProvider = provider
    }

    override fun notifyAccessibilityChanged(update: WinUIAccessibilityUpdate) {
        layer.notifyAccessibilityChanged(update.change)
    }

    override fun requestRender(throttledToVsync: Boolean) {
        layer.needRender(throttledToVsync)
    }

    override fun setSize(size: WinUISkikoSurfaceSize) {
        component.width = size.xamlWidth
        component.height = size.xamlHeight
    }

    override fun bindIndirectPointerInput(window: Window): AutoCloseable =
        window.bindWinUIIndirectPointerInput(layer)

    override fun startFrameScheduler(): WinUIFrameScheduler =
        layer.startFrameScheduler()

    override fun close() {
        layer.close()
    }
}
