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

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import io.github.composefluent.winrt.runtime.DerivedComposed
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.Window
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkikoRenderDelegate
import org.jetbrains.skiko.winui.WinUIAccessibilityChange
import org.jetbrains.skiko.winui.WinUIAccessibilityChangeType
import org.jetbrains.skiko.winui.WinUIAccessibilityInfo
import org.jetbrains.skiko.winui.WinUIAccessibilityNode
import org.jetbrains.skiko.winui.WinUIAccessibilityProvider
import org.jetbrains.skiko.winui.WinUIAccessibilitySnapshot
import org.jetbrains.skiko.winui.WinUIAccessibilityState
import org.jetbrains.skiko.winui.WinUIIndirectPointerChange
import org.jetbrains.skiko.winui.WinUIIndirectPointerEvent
import org.jetbrains.skiko.winui.WinUIIndirectPointerEventType
import org.jetbrains.skiko.winui.WinUIIndirectPointerPrimaryDirectionalMotionAxis
import org.jetbrains.skiko.winui.WinUIInputHandler
import org.jetbrains.skiko.winui.WinUIRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WinUISkikoRenderHostTest {
    @Test
    fun coalescesRenderRequestsAndDelegatesResizeToLayer() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        host.requestRender()
        host.requestRender(throttledToVsync = false)
        host.setSize(IntSize(30, 40))

        assertEquals(listOf(true), layer.renderRequests)
        assertEquals(listOf(IntSize(30, 40)), layer.sizes)
        assertEquals(
            2,
            host.diagnosticsForTest.renderInvalidationCount,
        )
        assertEquals(
            1,
            host.diagnosticsForTest.delegatedRenderInvalidationCount,
        )
        assertTrue(host.diagnosticsForTest.pendingRenderInvalidation)
    }

    @Test
    fun drawSubmissionDrainsInteropBeforeDrawingAndAllowsNextRenderRequest() {
        val layer = FakeWinUISkikoLayerAdapter()
        val events = mutableListOf<String>()
        val host = WinUISkikoRenderHost(
            layer = layer,
            beforeDrawSubmission = { events += "drainInterop" },
        )

        host.requestRender()
        host.requestRender()
        host.performDrawSubmission {
            events += "draw"
        }
        assertFalse(host.diagnosticsForTest.pendingRenderInvalidation)
        host.requestRender(throttledToVsync = false)

        assertEquals(listOf("drainInterop", "draw"), events)
        assertEquals(listOf(true, false), layer.renderRequests)
        assertEquals(1, host.diagnosticsForTest.drawSubmissionCount)
        assertEquals(1, host.diagnosticsForTest.interopTransactionDrainCount)
        assertTrue(host.diagnosticsForTest.pendingRenderInvalidation)
    }

    @Test
    fun exposesRenderDiagnosticsFromLayer() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        layer.renderVersion = 7L
        layer.lastRenderSize = IntSize(80, 60)
        layer.lastRenderedStateSize = IntSize(80, 60)
        layer.pendingRenderStateSize = IntSize(120, 90)
        layer.renderFailure = "render failed"

        assertEquals(7L, host.renderVersionForTest)
        assertEquals(GraphicsApi.DIRECT3D, host.renderApiForTest)
        assertEquals(IntSize(80, 60), host.lastRenderSizeForTest)
        assertEquals(IntSize(80, 60), host.lastRenderedStateSizeForTest)
        assertEquals(IntSize(120, 90), host.pendingRenderStateSizeForTest)
        assertEquals("render failed", host.renderFailureForTest)
        assertEquals(
            WinUISkikoRenderHostDiagnostics(
                isClosed = false,
                isSurfaceAttached = false,
                isFrameSchedulerStarted = false,
                requestedSurfaceSize = null,
                appliedSurfaceSize = null,
                pendingRenderInvalidation = false,
                renderInvalidationCount = 0,
                delegatedRenderInvalidationCount = 0,
                drawSubmissionCount = 0,
                interopTransactionDrainCount = 0,
                renderApi = GraphicsApi.DIRECT3D,
                renderVersion = 7L,
                lastRenderSize = IntSize(80, 60),
                lastRenderedStateSize = IntSize(80, 60),
                pendingRenderStateSize = IntSize(120, 90),
                renderFailure = "render failed",
            ),
            host.diagnosticsForTest,
        )
    }

    @Test
    fun forwardsAccessibilityProviderAndChangesToLayer() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)
        val provider = FakeWinUIAccessibilityProvider()
        val change = WinUIAccessibilityChange(
            type = WinUIAccessibilityChangeType.STRUCTURE_CHANGED,
            nodeId = 42L,
        )

        host.setAccessibilityProvider(provider)
        host.notifyAccessibilityChanged(
            WinUIAccessibilityUpdate(
                semanticsOwner = null,
                semanticsChanged = true,
                layoutChangedSemanticsIds = emptyList(),
                scrollDelta = null,
                change = change,
            )
        )

        assertSame(provider, layer.installedAccessibilityProvider)
        assertEquals(listOf(change), layer.accessibilityChanges)
        assertEquals(1, host.accessibilityUpdateCountForTest)
    }

    @Test
    fun indirectPointerBindingForwardsConsumptionAndCancellation() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)
        val window = FakeWindow()
        val event = indirectPointerEvent()
        var receivedEvent: WinUIIndirectPointerEvent? = null
        var consume = false
        var cancellationCount = 0

        assertNull(layer.inputHandler)
        assertEquals(0, layer.bindIndirectPointerInputCount)

        host.bindIndirectPointerInput(
            window = window,
            onEvent = { nativeEvent ->
                receivedEvent = nativeEvent
                consume
            },
            onCancel = { cancellationCount += 1 },
        )

        val handler = assertNotNull(layer.inputHandler)
        assertEquals(1, layer.bindIndirectPointerInputCount)
        assertSame(window, layer.boundWindows.single())
        assertFalse(handler.onIndirectPointerEvent(event))
        assertSame(event, receivedEvent)

        consume = true
        assertTrue(handler.onIndirectPointerEvent(event))
        handler.onIndirectPointerCancel()
        assertEquals(1, cancellationCount)
    }

    @Test
    fun replacingIndirectPointerBindingClosesPreviousBinding() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        host.bindIndirectPointerInput(FakeWindow(), onEvent = { false }, onCancel = {})
        val firstHandler = layer.inputHandler
        val firstBinding = layer.indirectPointerBindings.single()
        host.bindIndirectPointerInput(FakeWindow(), onEvent = { true }, onCancel = {})

        assertEquals(1, firstBinding.closeCount)
        assertEquals(2, layer.bindIndirectPointerInputCount)
        assertTrue(firstHandler !== layer.inputHandler)
        assertEquals(
            listOf(
                "bindIndirectPointerInput",
                "closeIndirectPointerInput",
                "bindIndirectPointerInput",
            ),
            layer.events,
        )
    }

    @Test
    fun closesIndirectPointerBindingBeforeFrameSchedulerAndLayer() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        host.bindIndirectPointerInput(FakeWindow(), onEvent = { false }, onCancel = {})
        host.startFrameScheduler()
        host.close()
        host.close()

        assertNull(layer.inputHandler)
        assertEquals(1, layer.indirectPointerBindings.single().closeCount)
        assertEquals(
            listOf(
                "bindIndirectPointerInput",
                "startFrameScheduler",
                "closeIndirectPointerInput",
                "closeFrameScheduler",
                "closeLayer",
            ),
            layer.events,
        )
    }

    @Test
    fun startsFrameSchedulerOnlyOnceAndClosesItWithLayer() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        assertFalse(host.isFrameSchedulerStartedForTest)
        val firstScheduler = host.startFrameScheduler()
        val secondScheduler = host.startFrameScheduler()

        assertTrue(host.isFrameSchedulerStartedForTest)
        assertTrue(host.diagnosticsForTest.isSurfaceAttached)
        assertSame(firstScheduler, secondScheduler)
        assertEquals(1, layer.startFrameSchedulerCount)

        host.close()
        host.close()

        assertFalse(host.isFrameSchedulerStartedForTest)
        assertEquals(listOf("startFrameScheduler", "closeFrameScheduler", "closeLayer"), layer.events)
        assertEquals(1, layer.scheduler.closeCount)
        assertEquals(1, layer.closeCount)
    }

    @Test
    fun surfaceDetachStopsFrameSchedulerWithoutClosingLayer() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        host.attachSurface()
        host.startFrameScheduler()
        host.detachSurface()

        assertFalse(host.diagnosticsForTest.isSurfaceAttached)
        assertFalse(host.isFrameSchedulerStartedForTest)
        assertEquals(0, layer.closeCount)
        assertEquals(
            listOf("startFrameScheduler", "closeFrameScheduler"),
            layer.events,
        )
    }

    @Test
    fun repeatedSizeRequestsOnlyApplyChangedSurfaceSize() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        host.setSize(IntSize(20, 10))
        host.setSize(IntSize(20, 10))
        host.setSize(IntSize(30, 10))

        assertEquals(listOf(IntSize(20, 10), IntSize(30, 10)), layer.sizes)
        assertEquals(IntSize(30, 10), host.diagnosticsForTest.requestedSurfaceSize)
        assertEquals(IntSize(30, 10), host.diagnosticsForTest.appliedSurfaceSize)
    }

    @Test
    fun convertsPhysicalSizeToXamlDipsForLayer() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        host.setSize(IntSize(300, 150), Density(1.5f))

        assertEquals(IntSize(300, 150), host.diagnosticsForTest.requestedSurfaceSize)
        assertEquals(IntSize(300, 150), host.diagnosticsForTest.appliedSurfaceSize)
        assertEquals(
            WinUISkikoSurfaceSize(
                physicalSize = IntSize(300, 150),
                xamlWidth = 200.0,
                xamlHeight = 100.0,
            ),
            layer.surfaceSizes.single(),
        )
    }

    @Test
    fun ignoresRenderAndResizeAfterClose() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        host.close()
        host.requestRender()
        host.setSize(IntSize(10, 20))
        host.performDrawSubmission {
            error("Draw submission should not run after close.")
        }

        assertEquals(emptyList(), layer.renderRequests)
        assertEquals(emptyList(), layer.sizes)
        assertEquals(1, layer.closeCount)
        assertTrue(host.diagnosticsForTest.isClosed)
    }

    @Test
    fun ignoresAccessibilityUpdatesAfterClose() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)
        val provider = FakeWinUIAccessibilityProvider()
        val change = WinUIAccessibilityChange(
            type = WinUIAccessibilityChangeType.NODE_UPDATED,
            nodeId = 7L,
        )

        host.close()
        host.setAccessibilityProvider(provider)
        host.notifyAccessibilityChanged(
            WinUIAccessibilityUpdate(
                semanticsOwner = null,
                semanticsChanged = false,
                layoutChangedSemanticsIds = listOf(7),
                scrollDelta = null,
                change = change,
            )
        )

        assertEquals(null, layer.installedAccessibilityProvider)
        assertEquals(emptyList(), layer.accessibilityChanges)
        assertEquals(0, host.accessibilityUpdateCountForTest)
        assertEquals(1, layer.closeCount)
    }

    @Test
    fun rejectsStartingFrameSchedulerAfterClose() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        host.close()

        assertFailsWith<IllegalStateException> {
            host.startFrameScheduler()
        }
        assertEquals(0, layer.startFrameSchedulerCount)
    }

    @Test
    fun failedFrameSchedulerStartIsNotCachedAndCanBeRetried() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)
        layer.startFrameSchedulerFailure = IllegalStateException("scheduler start failed")

        val failure = assertFailsWith<IllegalStateException> {
            host.startFrameScheduler()
        }
        assertEquals("scheduler start failed", failure.message)
        assertFalse(host.isFrameSchedulerStartedForTest)
        assertEquals(1, layer.startFrameSchedulerCount)

        layer.startFrameSchedulerFailure = null
        val scheduler = host.startFrameScheduler()

        assertSame(layer.scheduler, scheduler)
        assertTrue(host.isFrameSchedulerStartedForTest)
        assertEquals(2, layer.startFrameSchedulerCount)
        assertEquals(
            listOf("startFrameScheduler", "startFrameScheduler"),
            layer.events,
        )
    }

    @Test
    fun closesLayerWhenFrameSchedulerCloseFails() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)
        layer.scheduler.closeFailure = IllegalStateException("scheduler close failed")

        host.startFrameScheduler()
        val failure = assertFailsWith<IllegalStateException> {
            host.close()
        }

        assertEquals("scheduler close failed", failure.message)
        assertEquals(listOf("startFrameScheduler", "closeFrameScheduler", "closeLayer"), layer.events)
        assertFalse(host.isFrameSchedulerStartedForTest)
        assertEquals(1, layer.scheduler.closeCount)
        assertEquals(1, layer.closeCount)
    }

    @Test
    fun suppressesLayerCloseFailureWhenFrameSchedulerCloseAlsoFails() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)
        layer.scheduler.closeFailure = IllegalStateException("scheduler close failed")
        layer.closeFailure = IllegalArgumentException("layer close failed")

        host.startFrameScheduler()
        val failure = assertFailsWith<IllegalStateException> {
            host.close()
        }

        assertEquals("scheduler close failed", failure.message)
        assertEquals("layer close failed", failure.suppressed.single().message)
        assertEquals(listOf("startFrameScheduler", "closeFrameScheduler", "closeLayer"), layer.events)
        assertFalse(host.isFrameSchedulerStartedForTest)
        assertEquals(1, layer.scheduler.closeCount)
        assertEquals(1, layer.closeCount)
    }

    @Test
    fun closesAutoCloseableRenderDelegateAfterLayer() {
        val layer = FakeWinUISkikoLayerAdapter()
        val renderDelegate = FakeAutoCloseableRenderDelegate(layer.events)
        val host = WinUISkikoRenderHost(layer, renderDelegate)

        host.startFrameScheduler()
        host.close()

        assertEquals(
            listOf(
                "startFrameScheduler",
                "closeFrameScheduler",
                "closeLayer",
                "closeRenderDelegate",
            ),
            layer.events,
        )
        assertEquals(1, renderDelegate.closeCount)
    }

    @Test
    fun closesAutoCloseableRenderDelegateWhenLayerCloseFails() {
        val layer = FakeWinUISkikoLayerAdapter()
        val renderDelegate = FakeAutoCloseableRenderDelegate(layer.events)
        val host = WinUISkikoRenderHost(layer, renderDelegate)
        layer.closeFailure = IllegalArgumentException("layer close failed")
        renderDelegate.closeFailure = IllegalStateException("delegate close failed")

        val failure = assertFailsWith<IllegalArgumentException> {
            host.close()
        }

        assertEquals("layer close failed", failure.message)
        assertEquals("delegate close failed", failure.suppressed.single().message)
        assertEquals(listOf("closeLayer", "closeRenderDelegate"), layer.events)
        assertEquals(1, layer.closeCount)
        assertEquals(1, renderDelegate.closeCount)
    }

    @Test
    fun preservesAllCloseFailuresWhenSchedulerLayerAndRenderDelegateFail() {
        val layer = FakeWinUISkikoLayerAdapter()
        val renderDelegate = FakeAutoCloseableRenderDelegate(layer.events)
        val host = WinUISkikoRenderHost(layer, renderDelegate)
        layer.scheduler.closeFailure = IllegalStateException("scheduler close failed")
        layer.closeFailure = IllegalArgumentException("layer close failed")
        renderDelegate.closeFailure = UnsupportedOperationException("delegate close failed")

        host.startFrameScheduler()
        val failure = assertFailsWith<IllegalStateException> {
            host.close()
        }

        assertEquals("scheduler close failed", failure.message)
        assertEquals(
            listOf("layer close failed", "delegate close failed"),
            failure.suppressed.map { it.message },
        )
        assertEquals(
            listOf(
                "startFrameScheduler",
                "closeFrameScheduler",
                "closeLayer",
                "closeRenderDelegate",
            ),
            layer.events,
        )
        assertFalse(host.isFrameSchedulerStartedForTest)
        assertEquals(1, layer.scheduler.closeCount)
        assertEquals(1, layer.closeCount)
        assertEquals(1, renderDelegate.closeCount)
    }
}

private class FakeWinUISkikoLayerAdapter : WinUISkikoLayerAdapter {
    val events = mutableListOf<String>()
    val renderRequests = mutableListOf<Boolean>()
    val sizes = mutableListOf<IntSize>()
    val surfaceSizes = mutableListOf<WinUISkikoSurfaceSize>()
    val boundWindows = mutableListOf<Window>()
    val indirectPointerBindings = mutableListOf<FakeIndirectPointerInputBinding>()
    val scheduler = FakeFrameScheduler(events)
    var startFrameSchedulerCount = 0
    var startFrameSchedulerFailure: Throwable? = null
    var bindIndirectPointerInputCount = 0
    var closeCount = 0
    var closeFailure: Throwable? = null
    override var inputHandler: WinUIInputHandler? = null
    override var renderVersion: Long = 0L
    override var renderApi: GraphicsApi = GraphicsApi.DIRECT3D
    override var lastRenderSize: IntSize? = null
    override var lastRenderedStateSize: IntSize? = null
    override var pendingRenderStateSize: IntSize? = null
    override var renderFailure: String? = null
    var installedAccessibilityProvider: WinUIAccessibilityProvider? = null
    val accessibilityChanges = mutableListOf<WinUIAccessibilityChange>()

    override val component: FrameworkElement
        get() = error("Fake layer does not expose a WinUI component.")

    override fun requestRender(throttledToVsync: Boolean) {
        renderRequests += throttledToVsync
    }

    override fun setSize(size: WinUISkikoSurfaceSize) {
        surfaceSizes += size
        sizes += size.physicalSize
    }

    override fun setAccessibilityProvider(provider: WinUIAccessibilityProvider) {
        installedAccessibilityProvider = provider
    }

    override fun notifyAccessibilityChanged(update: WinUIAccessibilityUpdate) {
        accessibilityChanges += update.change
    }

    override fun bindIndirectPointerInput(window: Window): AutoCloseable {
        bindIndirectPointerInputCount += 1
        boundWindows += window
        events += "bindIndirectPointerInput"
        return FakeIndirectPointerInputBinding(events).also {
            indirectPointerBindings += it
        }
    }

    override fun startFrameScheduler(): AutoCloseable {
        startFrameSchedulerCount += 1
        events += "startFrameScheduler"
        startFrameSchedulerFailure?.let { throw it }
        return scheduler
    }

    override fun close() {
        closeCount += 1
        events += "closeLayer"
        closeFailure?.let { throw it }
    }
}

private class FakeIndirectPointerInputBinding(
    private val events: MutableList<String>,
) : AutoCloseable {
    var closeCount = 0

    override fun close() {
        closeCount += 1
        events += "closeIndirectPointerInput"
    }
}

private class FakeWindow : Window(DerivedComposed.Instance)

private fun indirectPointerEvent() =
    WinUIIndirectPointerEvent(
        type = WinUIIndirectPointerEventType.MOVE,
        changes =
            listOf(
                WinUIIndirectPointerChange(
                    pointerId = 9,
                    timestampMillis = 30,
                    x = 8125f,
                    y = 4030f,
                    pressed = true,
                    pressure = 0.75f,
                    previousTimestampMillis = 20,
                    previousX = 8000f,
                    previousY = 4000f,
                    previousPressed = true,
                )
            ),
        primaryDirectionalMotionAxis =
            WinUIIndirectPointerPrimaryDirectionalMotionAxis.NONE,
        deviceId = 44,
        deviceRect = null,
        frameId = 71,
    )

private class FakeWinUIAccessibilityProvider : WinUIAccessibilityProvider {
    override fun snapshot(): WinUIAccessibilitySnapshot =
        WinUIAccessibilitySnapshot(
            root = WinUIAccessibilityNode(
                id = 0L,
                bounds = WinUIRect(0f, 0f, 0f, 0f),
                info = WinUIAccessibilityInfo(),
                state = WinUIAccessibilityState(),
                children = emptyList(),
            ),
        )
}

private class FakeFrameScheduler(
    private val events: MutableList<String>,
) : AutoCloseable {
    var closeCount = 0
    var closeFailure: Throwable? = null

    override fun close() {
        closeCount += 1
        events += "closeFrameScheduler"
        closeFailure?.let { throw it }
    }
}

private class FakeAutoCloseableRenderDelegate(
    private val events: MutableList<String>,
) : SkikoRenderDelegate, AutoCloseable {
    var closeCount = 0
    var closeFailure: Throwable? = null

    override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) = Unit

    override fun close() {
        closeCount += 1
        events += "closeRenderDelegate"
        closeFailure?.let { throw it }
    }
}
