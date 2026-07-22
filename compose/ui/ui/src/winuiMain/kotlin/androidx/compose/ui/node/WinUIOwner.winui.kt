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

import androidx.collection.IntObjectMap
import androidx.collection.MutableIntObjectMap
import androidx.collection.mutableIntObjectMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.RetainedValuesStore
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.SessionMutex
import androidx.compose.ui.autofill.Autofill
import androidx.compose.ui.autofill.AutofillManager
import androidx.compose.ui.autofill.AutofillTree
import androidx.compose.ui.autofill.WinUIAutofill
import androidx.compose.ui.autofill.WinUIAutofillState
import androidx.compose.ui.draganddrop.DragAndDropManager
import androidx.compose.ui.draganddrop.WinUIDragAndDropManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusOwner
import androidx.compose.ui.focus.FocusOwnerImpl
import androidx.compose.ui.focus.IndirectPointerInputFocusListener
import androidx.compose.ui.focus.PlatformFocusOwner
import androidx.compose.ui.focus.WinUIEmbeddedViewPlatformFocusOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.WinUIGraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.WinUIHapticFeedback
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.WinUIInputModeManager
import androidx.compose.ui.input.indirect.IndirectPointerEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.MatrixPositionCalculator
import androidx.compose.ui.input.pointer.PointerIconService
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputEvent
import androidx.compose.ui.input.pointer.PointerInputEventData
import androidx.compose.ui.input.pointer.PointerInputEventProcessor
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.WinUIPointerIconService
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.modifier.ModifierLocalManager
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WinUIAccessibilityBridge
import androidx.compose.ui.platform.WinUIAccessibilityBridgeState
import androidx.compose.ui.platform.WinUIAccessibilityUpdate
import androidx.compose.ui.platform.WinUIAccessibilityManager
import androidx.compose.ui.platform.WinUIClipboard
import androidx.compose.ui.platform.WinUIClipboardManager
import androidx.compose.ui.platform.WinUIPointerEvent
import androidx.compose.ui.platform.WinUIPointerSample
import androidx.compose.ui.platform.WinUIPlatformTextInputSession
import androidx.compose.ui.platform.WinUIPlatformTextInputService
import androidx.compose.ui.platform.WinUISoftwareKeyboardController
import androidx.compose.ui.platform.WinUITextToolbar
import androidx.compose.ui.platform.WinUIViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.WindowInfoImpl
import androidx.compose.ui.semantics.EmptySemanticsModifier
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.spatial.RectManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.WinUIFontResourceLoader
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.InteropView
import androidx.compose.ui.viewinterop.WinUIInteropAction
import org.jetbrains.skiko.winui.WinUIAccessibilityProvider
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal class WinUIOwner(
    override val root: LayoutNode,
    platformFocusOwner: PlatformFocusOwner,
    override val retainedValuesStore: RetainedValuesStore,
    private val coroutineContextProvider: () -> CoroutineContext = { EmptyCoroutineContext },
    private val onMeasureAndLayoutRequested: () -> Unit = {},
    private val onInteropTreeChanged: () -> Unit = {},
    private val onRootInvalidated: () -> Unit = {},
    private val onSemanticsChanged: (SemanticsOwner) -> Unit = {},
    private val onLayoutChanged: (SemanticsOwner, Int) -> Unit = { _, _ -> },
    private val onScrollChanged: (Offset) -> Unit = {},
    private val onInteropTransactionScheduled: (WinUIInteropAction) -> Unit = {},
    private val onAccessibilityUpdate: (WinUIAccessibilityUpdate) -> Unit = {},
    private val onKeepScreenOnChanged: (Boolean) -> Unit = {},
    private val onSensitiveContentChanged: (Boolean) -> Unit = {},
    private val scheduleOutOfFrame: (() -> Unit) -> Unit = { it() },
    private val coordinateMapper: WinUICoordinateMapper = WinUICoordinateMapper(),
    override val textToolbar: TextToolbar = WinUITextToolbar(),
    override val pointerIconService: PointerIconService = WinUIPointerIconService(),
    internal val winUIDragAndDropManager: WinUIDragAndDropManager = WinUIDragAndDropManager(),
) : Owner, OutOfFrameExecutor, MatrixPositionCalculator {
    override val coroutineContext: CoroutineContext
        get() = coroutineContextProvider()

    private val onEndApplyChangesListeners = mutableListOf<(() -> Unit)?>()
    private val outOfFrameQueue = ArrayDeque<() -> Unit>()
    private var hasPendingLayoutCompletedListener = false
    private var isDisposing = false
    private var isDisposed = false
    private val isShuttingDown: Boolean
        get() = isDisposing || isDisposed
    private val accessibilityBridge = WinUIAccessibilityBridge(
        onUpdate = onAccessibilityUpdate,
    )
    private val snapshotInvalidationTracker = WinUISnapshotInvalidationTracker {
        schedule(::invalidateRootLayer)
    }

    override val sharedDrawScope = LayoutNodeDrawScope()
    override val layoutNodes: MutableIntObjectMap<LayoutNode> = mutableIntObjectMapOf()
    override val rootForTest: RootForTest = WinUIRootForTest(
        densityProvider = { density },
        semanticsOwnerProvider = { semanticsOwner },
        textInputServiceProvider = { textInputService },
        sendKeyEvent = ::sendKeyEvent,
        sendIndirectPointerEvent = { focusOwner.dispatchIndirectPointerEvent(it) },
        measureAndLayout = { measureAndLayout() },
        drainOutOfFrameQueue = ::drainOutOfFrameQueue,
        setUncaughtExceptionHandler = { measureAndLayoutDelegate.uncaughtExceptionHandler = it },
        forceAccessibilityForTesting = accessibilityBridge::forceAccessibilityForTesting,
        setAccessibilityEventBatchIntervalMillis =
            accessibilityBridge::setAccessibilityEventBatchIntervalMillis,
    )
    override val hapticFeedBack: HapticFeedback = WinUIHapticFeedback
    override val inputModeManager: InputModeManager =
        WinUIInputModeManager()
    private val winUIClipboard = WinUIClipboard()
    @Suppress("DEPRECATION")
    override val clipboardManager: ClipboardManager = WinUIClipboardManager(winUIClipboard)
    override val clipboard: Clipboard = winUIClipboard
    override val accessibilityManager: AccessibilityManager = WinUIAccessibilityManager()
    override val semanticsOwner: SemanticsOwner =
        SemanticsOwner(root, EmptySemanticsModifier(), layoutNodes)
    internal val accessibilityProvider: WinUIAccessibilityProvider
        get() = accessibilityBridge

    internal fun onAccessibilityProviderAttached() {
        accessibilityBridge.onAccessibilityProviderAttached()
    }

    internal fun onAccessibilityProviderDetached() {
        accessibilityBridge.onAccessibilityProviderDetached()
    }

    override val graphicsContext: GraphicsContext = WinUIGraphicsContext
    @Suppress("DEPRECATION")
    override val autofillTree: AutofillTree = AutofillTree()
    private val winUIAutofill = WinUIAutofill(autofillTree, semanticsOwner)
    @Suppress("DEPRECATION")
    override val autofill: Autofill = winUIAutofill
    override val autofillManager: AutofillManager = winUIAutofill.manager
    override var density: Density by mutableStateOf(Density(1f))
        private set
    @Suppress("DEPRECATION")
    override val textInputService: TextInputService = TextInputService(WinUIPlatformTextInputService)
    override val softwareKeyboardController: SoftwareKeyboardController = WinUISoftwareKeyboardController
    private var interopViewFocusRect: Rect? = null
    override val focusOwner: FocusOwner = FocusOwnerImpl(
        WinUIEmbeddedViewPlatformFocusOwner(
            delegate = platformFocusOwner,
            embeddedViewFocusRect = { interopViewFocusRect },
        ),
        this,
    )
    private val mutableWindowInfo = WindowInfoImpl()
    override val windowInfo: WindowInfo = mutableWindowInfo
    override val rectManager: RectManager = RectManager(layoutNodes)
    private val textInputSessionMutex = SessionMutex<WinUIPlatformTextInputSession>()
    private val pointerInputEventProcessor = PointerInputEventProcessor(root)
    private val pointerEventSender = WinUIPointerEventSender(::processPointerInputEvent)
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val fontLoader: Font.ResourceLoader = WinUIFontResourceLoader
    override val fontFamilyResolver: FontFamily.Resolver = createFontFamilyResolver()
    override var layoutDirection: LayoutDirection by mutableStateOf(LayoutDirection.Ltr)
        private set
    override val localeList: LocaleList = LocaleList.current
    override val snapshotObserver = snapshotInvalidationTracker.snapshotObserver()
    override val modifierLocalManager: ModifierLocalManager = ModifierLocalManager(this)
    override val dragAndDropManager: DragAndDropManager = winUIDragAndDropManager
    private val measureAndLayoutDelegate = MeasureAndLayoutDelegate(root)
    private var keepScreenOnCount = 0
    private var sensitiveContentCount = 0
    private val interopViewBounds = mutableMapOf<Any, Rect>()
    private var semanticsChangeCount = 0
    private var layoutChangeCount = 0
    private var lastLayoutChangedSemanticsId = -1
    private var rootInvalidationCount = 0
    private var lastFrameRateVote = Float.NaN
    private var scrollChangeCount = 0
    private var lastScrollDelta = Offset.Zero
    private var lastMousePointerEvent: WinUIPointerEvent? = null
    override val measureIteration: Long
        get() = measureAndLayoutDelegate.measureIteration
    internal val isMeasureLayoutInProgress: Boolean
        get() = measureAndLayoutDelegate.duringMeasureLayout
    override val viewConfiguration: ViewConfiguration = WinUIViewConfiguration

    @InternalComposeUiApi
    override var showLayoutBounds: Boolean = false

    init {
        root.density = density
        root.layoutDirection = layoutDirection
        root.viewConfiguration = viewConfiguration
        root.modifier = focusOwner.modifier.then(dragAndDropManager.modifier)
        snapshotObserver.startObserving()
        root.attach(this)
        semanticsOwner.listeners += winUIAutofill
        focusOwner.listeners += winUIAutofill
        focusOwner.listeners += IndirectPointerInputFocusListener
        measureAndLayoutDelegate.updateRootConstraints(Constraints())
    }

    fun dispose() {
        if (isShuttingDown) return
        isDisposing = true
        (textToolbar as? WinUITextToolbar)?.close()
        outOfFrameQueue.clear()
        if (root.isAttached) {
            root.detach()
        }
        semanticsOwner.listeners -= winUIAutofill
        focusOwner.listeners -= winUIAutofill
        focusOwner.listeners -= IndirectPointerInputFocusListener
        releaseActivePlatformState()
        winUIAutofill.dispose()
        cancelPointerInput()
        accessibilityBridge.dispose()
        snapshotObserver.stopObserving()
        rectManager.removeScheduledCallback()
        onEndApplyChangesListeners.clear()
        isDisposed = true
        isDisposing = false
    }

    private fun releaseActivePlatformState() {
        if (keepScreenOnCount > 0) {
            keepScreenOnCount = 0
            onKeepScreenOnChanged(false)
        }
        if (sensitiveContentCount > 0) {
            sensitiveContentCount = 0
            onSensitiveContentChanged(false)
        }
    }

    fun setWindowFocused(isWindowFocused: Boolean) {
        if (isShuttingDown) return
        mutableWindowInfo.isWindowFocused = isWindowFocused
    }

    fun setWindowContainerSize(size: IntSize) {
        if (isShuttingDown) return
        updateWindowContainerSize(size)
        if (size.width > 0 && size.height > 0) {
            measureAndLayoutDelegate.updateRootConstraints(
                Constraints(maxWidth = size.width, maxHeight = size.height)
            )
            onMeasureAndLayoutRequested()
        }
    }

    fun updateDensity(density: Density) {
        if (isShuttingDown || this.density == density) return
        this.density = density
        root.density = density
        updateWindowContainerSize(mutableWindowInfo.containerSize)
        if (
            mutableWindowInfo.containerSize.width > 0 &&
                mutableWindowInfo.containerSize.height > 0
        ) {
            measureAndLayoutDelegate.updateRootConstraints(
                Constraints(
                    maxWidth = mutableWindowInfo.containerSize.width,
                    maxHeight = mutableWindowInfo.containerSize.height,
                )
            )
            onMeasureAndLayoutRequested()
        }
    }

    fun updateLayoutDirection(layoutDirection: LayoutDirection) {
        if (isShuttingDown || this.layoutDirection == layoutDirection) return
        this.layoutDirection = layoutDirection
        root.layoutDirection = layoutDirection
        onMeasureAndLayoutRequested()
    }

    private fun updateWindowContainerSize(size: IntSize) {
        mutableWindowInfo.containerSize = size
        mutableWindowInfo.containerDpSize = with(density) {
            DpSize(size.width.toDp(), size.height.toDp())
        }
    }

    override fun onRequestMeasure(
        layoutNode: LayoutNode,
        affectsLookahead: Boolean,
        forceRequest: Boolean,
        scheduleMeasureAndLayout: Boolean,
    ) {
        if (isShuttingDown) return
        if (affectsLookahead) {
            if (
                measureAndLayoutDelegate.requestLookaheadRemeasure(layoutNode, forceRequest) &&
                    scheduleMeasureAndLayout
            ) {
                onMeasureAndLayoutRequested()
            }
        } else if (
            measureAndLayoutDelegate.requestRemeasure(layoutNode, forceRequest) &&
                scheduleMeasureAndLayout
        ) {
            onMeasureAndLayoutRequested()
        }
    }

    override fun onRequestRelayout(
        layoutNode: LayoutNode,
        affectsLookahead: Boolean,
        forceRequest: Boolean,
    ) {
        if (isShuttingDown) return
        if (affectsLookahead) {
            if (measureAndLayoutDelegate.requestLookaheadRelayout(layoutNode, forceRequest)) {
                onMeasureAndLayoutRequested()
            }
        } else {
            if (measureAndLayoutDelegate.requestRelayout(layoutNode, forceRequest)) {
                onMeasureAndLayoutRequested()
            }
        }
    }

    override fun requestOnPositionedCallback(layoutNode: LayoutNode) {
        if (isShuttingDown) return
        measureAndLayoutDelegate.requestOnPositionedCallback(layoutNode)
        onMeasureAndLayoutRequested()
    }

    override fun onPreAttach(node: LayoutNode) {
        layoutNodes[node.semanticsId] = node
    }

    override fun onPostAttach(node: LayoutNode) {
        winUIAutofill.onPostAttach(node)
    }

    override fun onDetach(node: LayoutNode) {
        checkNotNull(layoutNodes.remove(node.semanticsId)) {
            "Invalid usage of Owner.onDetach: layoutNode was not previously attached"
        }
        measureAndLayoutDelegate.onNodeDetached(node)
        snapshotObserver.clear(node)
        winUIAutofill.onDetach(node)
        rectManager.remove(node)
    }

    override fun calculatePositionInWindow(localPosition: Offset): Offset =
        coordinateMapper.calculatePositionInWindow(localPosition)

    override fun calculateLocalPosition(positionInWindow: Offset): Offset =
        coordinateMapper.calculateLocalPosition(positionInWindow)

    override fun requestAutofill(node: LayoutNode) {
        if (isShuttingDown) return
        winUIAutofill.requestAutofill(node)
    }

    override fun measureAndLayout(sendPointerUpdate: Boolean) {
        if (isShuttingDown) return
        if (
            measureAndLayoutDelegate.hasPendingMeasureOrLayout ||
            measureAndLayoutDelegate.hasPendingOnPositionedCallbacks ||
            hasPendingLayoutCompletedListener
        ) {
            hasPendingLayoutCompletedListener = false
            val rootNodeResized = measureAndLayoutDelegate.measureAndLayout()
            if (rootNodeResized) {
                invalidateRootLayer()
            }
            measureAndLayoutDelegate.dispatchOnPositionedCallbacks()
            rectManager.dispatchCallbacks()
            if (sendPointerUpdate) {
                pointerEventSender.needUpdatePointerPosition = true
                pointerEventSender.updatePointerPosition()
            }
        }
    }

    override fun measureAndLayout(layoutNode: LayoutNode, constraints: Constraints) {
        if (isShuttingDown) return
        hasPendingLayoutCompletedListener = false
        measureAndLayoutDelegate.measureAndLayout(layoutNode, constraints)
        pointerEventSender.needUpdatePointerPosition = true
        pointerEventSender.updatePointerPosition()
        if (!measureAndLayoutDelegate.hasPendingMeasureOrLayout) {
            measureAndLayoutDelegate.dispatchOnPositionedCallbacks()
        }
        rectManager.dispatchCallbacks()
    }

    override fun forceMeasureTheSubtree(layoutNode: LayoutNode, affectsLookahead: Boolean) {
        measureAndLayoutDelegate.forceMeasureTheSubtree(layoutNode, affectsLookahead)
    }

    override fun createLayer(
        drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
        invalidateParentLayer: () -> Unit,
        explicitLayer: GraphicsLayer?,
    ): OwnedLayer {
        val layer = explicitLayer ?: graphicsContext.createGraphicsLayer()
        return WinUIOwnerLayer(
            graphicsLayer = layer,
            graphicsContext = if (explicitLayer == null) graphicsContext else null,
            drawBlock = drawBlock,
            invalidateParentLayer = invalidateParentLayer,
            voteFrameRate = ::voteFrameRate,
        )
    }

    override fun onSemanticsChange() {
        if (isShuttingDown) return
        semanticsChangeCount += 1
        accessibilityBridge.onSemanticsChange(semanticsOwner)
        onSemanticsChanged(semanticsOwner)
    }

    override fun onLayoutChange(layoutNode: LayoutNode) {
        if (isShuttingDown) return
        layoutChangeCount += 1
        lastLayoutChangedSemanticsId = layoutNode.semanticsId
        accessibilityBridge.onLayoutChange(semanticsOwner, layoutNode.semanticsId)
        onLayoutChanged(semanticsOwner, layoutNode.semanticsId)
    }

    override fun onLayoutNodeDeactivated(layoutNode: LayoutNode) {
        winUIAutofill.onLayoutNodeDeactivated(layoutNode)
        rectManager.remove(layoutNode)
        if (!isShuttingDown) {
            notifyInteropTreeChanged()
        }
    }

    override fun onPreLayoutNodeReused(layoutNode: LayoutNode, oldSemanticsId: Int) {
        layoutNodes.remove(oldSemanticsId)
        layoutNodes[layoutNode.semanticsId] = layoutNode
    }

    override fun onPostLayoutNodeReused(layoutNode: LayoutNode, oldSemanticsId: Int) {
        if (isShuttingDown) return
        winUIAutofill.onPostLayoutNodeReused(layoutNode, oldSemanticsId)
        notifyInteropTreeChanged()
    }

    @InternalComposeUiApi
    override fun onInteropViewLayoutChange(view: InteropView) {
        if (isShuttingDown) return
        onMeasureAndLayoutRequested()
    }

    internal fun scheduleInteropTransaction(action: WinUIInteropAction) {
        if (isShuttingDown) return
        onInteropTransactionScheduled(action)
    }

    internal fun setInteropViewFocusRect(rect: Rect?) {
        if (isShuttingDown) return
        interopViewFocusRect = rect
    }

    internal fun setInteropViewBounds(key: Any, bounds: Rect?) {
        if (isShuttingDown) return
        if (bounds == null) {
            interopViewBounds.remove(key)
        } else {
            interopViewBounds[key] = bounds
        }
    }

    internal fun notifyInteropTreeChanged() {
        onInteropTreeChanged()
        registerOnEndApplyChangesListener(onInteropTreeChanged)
    }

    override fun registerOnEndApplyChangesListener(listener: () -> Unit) {
        if (isShuttingDown) return
        if (listener !in onEndApplyChangesListeners) {
            onEndApplyChangesListeners += listener
        }
    }

    override fun onEndApplyChanges() {
        if (isShuttingDown) {
            onEndApplyChangesListeners.clear()
            return
        }
        while (onEndApplyChangesListeners.isNotEmpty()) {
            val size = onEndApplyChangesListeners.size
            for (i in 0 until size) {
                val listener = onEndApplyChangesListeners[i]
                onEndApplyChangesListeners[i] = null
                listener?.invoke()
            }
            onEndApplyChangesListeners.subList(0, size).clear()
        }
        winUIAutofill.onEndApplyChanges()
    }

    internal fun winUIAutofillForTest(): WinUIAutofill = winUIAutofill

    internal fun winUIAutofillStateForTest(): WinUIAutofillState =
        winUIAutofill.stateForTest()

    override fun registerOnLayoutCompletedListener(listener: Owner.OnLayoutCompletedListener) {
        if (isShuttingDown) return
        hasPendingLayoutCompletedListener = true
        measureAndLayoutDelegate.registerOnLayoutCompletedListener(listener)
        onMeasureAndLayoutRequested()
    }

    override suspend fun textInputSession(
        session: suspend PlatformTextInputSessionScope.() -> Nothing
    ): Nothing =
        textInputSessionMutex.withSessionCancellingPrevious(
            sessionInitializer = ::WinUIPlatformTextInputSession,
            session = session,
        )

    override fun incrementSensitiveComponentCount() {
        if (isShuttingDown) return
        sensitiveContentCount += 1
        if (sensitiveContentCount == 1) {
            onSensitiveContentChanged(true)
        }
    }

    override fun decrementSensitiveComponentCount() {
        if (isDisposed || sensitiveContentCount == 0) return
        sensitiveContentCount -= 1
        if (sensitiveContentCount == 0) {
            onSensitiveContentChanged(false)
        }
    }

    override fun incrementKeepScreenOnCount() {
        if (isShuttingDown) return
        keepScreenOnCount += 1
        if (keepScreenOnCount == 1) {
            onKeepScreenOnChanged(true)
        }
    }

    override fun decrementKeepScreenOnCount() {
        if (isDisposed || keepScreenOnCount == 0) return
        keepScreenOnCount -= 1
        if (keepScreenOnCount == 0) {
            onKeepScreenOnChanged(false)
        }
    }

    override fun voteFrameRate(frameRate: Float) {
        if (isShuttingDown) return
        lastFrameRateVote = frameRate
    }

    override fun dispatchOnScrollChanged(delta: Offset) {
        if (isShuttingDown) return
        scrollChangeCount += 1
        lastScrollDelta = delta
        accessibilityBridge.onScrollChanged(delta)
        onScrollChanged(delta)
    }

    override fun invalidateRootLayer() {
        if (isShuttingDown) return
        rootInvalidationCount += 1
        onRootInvalidated()
    }

    internal fun sendAndPerformSnapshotChanges() {
        if (!isShuttingDown) {
            snapshotInvalidationTracker.sendAndPerformSnapshotChanges()
        }
    }

    override val outOfFrameExecutor: OutOfFrameExecutor?
        get() = if (isShuttingDown) null else this

    override fun schedule(block: () -> Unit) {
        if (isShuttingDown) return
        val shouldSchedule = outOfFrameQueue.isEmpty()
        outOfFrameQueue.addLast(block)
        if (shouldSchedule) {
            scheduleOutOfFrame(::drainOutOfFrameQueue)
        }
    }

    private fun drainOutOfFrameQueue() {
        while (!isShuttingDown && outOfFrameQueue.isNotEmpty()) {
            outOfFrameQueue.removeLast().invoke()
        }
    }

    fun ownerStateForTest(): WinUIOwnerStateForTest =
        WinUIOwnerStateForTest(
            keepScreenOnCount = keepScreenOnCount,
            sensitiveContentCount = sensitiveContentCount,
            semanticsChangeCount = semanticsChangeCount,
            layoutChangeCount = layoutChangeCount,
            lastLayoutChangedSemanticsId = lastLayoutChangedSemanticsId,
            rootInvalidationCount = rootInvalidationCount,
            lastFrameRateVote = lastFrameRateVote,
            scrollChangeCount = scrollChangeCount,
            lastScrollDelta = lastScrollDelta,
            accessibility = accessibilityBridge.stateForTest(),
            interopViewFocusRect = interopViewFocusRect,
            interopViewBounds = interopViewBounds.values.toList(),
            lastMousePointerEvent = lastMousePointerEvent,
        )

    override fun screenToLocal(positionOnScreen: Offset): Offset =
        coordinateMapper.screenToLocal(positionOnScreen)

    override fun localToScreen(localPosition: Offset): Offset =
        coordinateMapper.localToScreen(localPosition)

    override fun localToScreen(localTransform: Matrix) {
        coordinateMapper.localToScreen(localTransform)
    }

    @OptIn(InternalComposeUiApi::class)
    fun sendPointerEventForTest(
        eventType: PointerEventType,
        position: Offset,
        uptimeMillis: Long,
        pointerId: Long,
        down: Boolean,
        type: PointerType,
        buttons: PointerButtons,
        keyboardModifiers: PointerKeyboardModifiers,
        button: PointerButton?,
        scrollDelta: Offset = Offset.Zero,
        isInBounds: Boolean = eventType != PointerEventType.Exit,
    ): Boolean {
        return sendPointerEvent(
            eventType = eventType,
            position = position,
            uptimeMillis = uptimeMillis,
            pointerId = pointerId,
            down = down,
            type = type,
            buttons = buttons,
            keyboardModifiers = keyboardModifiers,
            button = button,
            scrollDelta = scrollDelta,
            isInBounds = isInBounds,
            nativeEvent = null,
        )
    }

    internal fun sendPointerEventForTest(
        eventType: PointerEventType,
        changedPointerId: Long,
        pointers: List<WinUIPointerSample>,
        buttons: PointerButtons,
        keyboardModifiers: PointerKeyboardModifiers,
        button: PointerButton?,
        scrollDelta: Offset = Offset.Zero,
        isInBounds: Boolean = eventType != PointerEventType.Exit,
    ): Boolean {
        val changedPointer = pointers.firstOrNull { it.id == changedPointerId }
            ?: error("Missing changed pointer $changedPointerId")
        return sendPointerEvent(
            eventType = eventType,
            position = changedPointer.position,
            uptimeMillis = changedPointer.uptimeMillis,
            pointerId = changedPointer.id,
            down = changedPointer.down,
            type = changedPointer.type,
            buttons = buttons,
            keyboardModifiers = keyboardModifiers,
            button = button,
            scrollDelta = scrollDelta,
            isInBounds = isInBounds,
            nativeEvent = null,
            pointerSamples = pointers,
        )
    }

    internal fun sendPointerEvent(
        eventType: PointerEventType,
        position: Offset,
        uptimeMillis: Long,
        pointerId: Long,
        down: Boolean,
        type: PointerType,
        buttons: PointerButtons,
        keyboardModifiers: PointerKeyboardModifiers,
        button: PointerButton?,
        scrollDelta: Offset = Offset.Zero,
        isInBounds: Boolean = eventType != PointerEventType.Exit,
        nativeEvent: Any?,
        updateLastPointerEvent: Boolean = true,
        pointerSamples: List<WinUIPointerSample> = emptyList(),
    ): Boolean {
        if (isShuttingDown) return false
        if (winUIShouldRequestTouchInputMode(down, type, button)) {
            inputModeManager.requestInputMode(InputMode.Touch)
        }
        if (updateLastPointerEvent) {
            updateLastMousePointerEvent(
                WinUIPointerEvent(
                    eventType = eventType,
                    position = position,
                    uptimeMillis = uptimeMillis,
                    pointerId = pointerId,
                    down = down,
                    type = type,
                    buttons = buttons,
                    keyboardModifiers = keyboardModifiers,
                    button = button,
                    scrollDelta = scrollDelta,
                    isInBounds = isInBounds,
                    nativeEvent = nativeEvent,
                )
            )
        }
        updateKeyboardModifiers(keyboardModifiers)
        if (eventType != PointerEventType.Exit && isInInteropViewBounds(position)) {
            return false
        }
        val samples = pointerSamples.ifEmpty {
            listOf(
                WinUIPointerSample(
                    id = pointerId,
                    uptimeMillis = uptimeMillis,
                    position = position,
                    down = down,
                    type = type,
                    pressure = if (down) 1f else 0f,
                    activeHover = type == PointerType.Mouse && !down,
                    historical = emptyList(),
                )
            )
        }
        val event = PointerInputEvent(
            eventType = eventType,
            uptime = uptimeMillis,
            pointers = winUIPointerSamplesToEventData(
                samples = samples,
                changedPointerId = pointerId,
                scrollDelta = scrollDelta,
            ),
            buttons = buttons,
            keyboardModifiers = keyboardModifiers,
            button = button,
            nativeEvent = nativeEvent,
        )
        return pointerEventSender.send(event)
    }

    @OptIn(InternalComposeUiApi::class)
    private fun processPointerInputEvent(event: PointerInputEvent): Boolean {
        val result = pointerInputEventProcessor.process(
            pointerEvent = event,
            positionCalculator = this,
            isInBounds = event.eventType != PointerEventType.Exit,
        )
        return result.dispatchedToAPointerInputModifier || result.anyChangeConsumed
    }

    private fun updateLastMousePointerEvent(event: WinUIPointerEvent) {
        if (event.type != PointerType.Mouse || event.eventType == PointerEventType.Exit) {
            lastMousePointerEvent = null
            return
        }
        if (event.eventType == PointerEventType.Scroll) return
        lastMousePointerEvent = event.copy(
            eventType = PointerEventType.Move,
            button = null,
            scrollDelta = Offset.Zero,
            nativeEvent = null,
        )
    }

    private fun isInInteropViewBounds(position: Offset): Boolean =
        interopViewBounds.values.any { bounds ->
            position.x >= bounds.left &&
                position.x < bounds.right &&
                position.y >= bounds.top &&
                position.y < bounds.bottom
        }

    internal fun cancelPointerInput() {
        lastMousePointerEvent = null
        pointerEventSender.reset()
        pointerInputEventProcessor.processCancel()
    }

    internal fun sendIndirectPointerEvent(event: IndirectPointerEvent): Boolean {
        if (isShuttingDown) return false
        return focusOwner.dispatchIndirectPointerEvent(event)
    }

    internal fun cancelIndirectPointerInput() {
        if (!isShuttingDown) {
            focusOwner.dispatchIndirectPointerCancel()
        }
    }

    internal fun sendKeyEvent(keyEvent: KeyEvent): Boolean {
        if (isShuttingDown) return false
        inputModeManager.requestInputMode(InputMode.Keyboard)
        return focusOwner.dispatchKeyEvent(keyEvent) || handleFocusKeys(keyEvent)
    }

    internal fun updateKeyboardModifiers(keyboardModifiers: PointerKeyboardModifiers) {
        if (!isShuttingDown) {
            mutableWindowInfo.keyboardModifiers = keyboardModifiers
        }
    }

    private fun handleFocusKeys(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        val focusDirection = when (keyEvent.key) {
            Key.Tab -> if (keyEvent.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
            Key.DirectionCenter -> FocusDirection.Enter
            Key.Back -> FocusDirection.Exit
            else -> return false
        }
        inputModeManager.requestInputMode(InputMode.Keyboard)
        return focusOwner.moveFocus(focusDirection)
    }
}

internal fun winUIShouldRequestTouchInputMode(
    down: Boolean,
    type: PointerType,
    button: PointerButton?,
): Boolean = button != null ||
    (down && (type == PointerType.Touch || type == PointerType.Stylus || type == PointerType.Eraser))

internal fun winUIPointerSamplesToEventData(
    samples: List<WinUIPointerSample>,
    changedPointerId: Long,
    scrollDelta: Offset,
): List<PointerInputEventData> = samples.map { sample ->
    PointerInputEventData(
        id = PointerId(sample.id),
        uptime = sample.uptimeMillis,
        positionOnScreen = sample.position,
        position = sample.position,
        down = sample.down,
        pressure = sample.pressure,
        type = sample.type,
        activeHover = sample.activeHover,
        historical = if (sample.id == changedPointerId) sample.historical else emptyList(),
        scrollDelta = if (sample.id == changedPointerId) scrollDelta else Offset.Zero,
        scaleGestureFactor = 1f,
        panGestureOffset = Offset.Zero,
        originalEventPosition = sample.position,
    )
}

internal data class WinUIOwnerStateForTest(
    val keepScreenOnCount: Int,
    val sensitiveContentCount: Int,
    val semanticsChangeCount: Int,
    val layoutChangeCount: Int,
    val lastLayoutChangedSemanticsId: Int,
    val rootInvalidationCount: Int,
    val lastFrameRateVote: Float,
    val scrollChangeCount: Int,
    val lastScrollDelta: Offset,
    val accessibility: WinUIAccessibilityBridgeState,
    val interopViewFocusRect: Rect?,
    val interopViewBounds: List<Rect>,
    val lastMousePointerEvent: WinUIPointerEvent?,
)

@OptIn(InternalComposeUiApi::class)
private class WinUIPointerEventSender(
    private val dispatch: (PointerInputEvent) -> Boolean,
) {
    private var previousEvent: PointerInputEvent? = null
    private var isMousePointerInside = false
    var needUpdatePointerPosition: Boolean = false

    fun reset() {
        needUpdatePointerPosition = false
        previousEvent = null
        isMousePointerInside = false
    }

    fun send(event: PointerInputEvent): Boolean {
        trackMousePointerState(event)
        var handled = false
        handled = sendMissingMoveForHover(event) || handled
        handled = sendMissingReleases(event) || handled
        handled = sendMissingPresses(event) || handled
        handled = if (event.shouldSend()) {
            sendInternal(event)
        } else {
            sendNativeEventOnly(event)
        } || handled
        return handled
    }

    fun updatePointerPosition(): Boolean {
        if (!needUpdatePointerPosition) return false
        needUpdatePointerPosition = false
        val previousEvent = previousEvent ?: return false
        val mousePointer = previousEvent.pointers.firstOrNull { it.type == PointerType.Mouse }
            ?: return false
        return if (isMousePointerInside || mousePointer.down) {
            sendSyntheticMove(previousEvent)
        } else {
            false
        }
    }

    private fun trackMousePointerState(event: PointerInputEvent) {
        if (event.pointers.none { it.type == PointerType.Mouse }) return
        when (event.eventType) {
            PointerEventType.Enter,
            PointerEventType.Move,
            PointerEventType.Press,
            PointerEventType.Scroll -> isMousePointerInside = true
            PointerEventType.Exit -> isMousePointerInside = false
            else -> Unit
        }
    }

    private fun sendMissingMoveForHover(currentEvent: PointerInputEvent): Boolean =
        if (currentEvent.pointers.any { it.activeHover } &&
            !currentEvent.isMove() &&
            !currentEvent.isSamePosition(previousEvent)
        ) {
            sendSyntheticMove(currentEvent)
        } else {
            false
        }

    private fun sendMissingReleases(currentEvent: PointerInputEvent): Boolean {
        val previousEvent = previousEvent ?: return false
        val previousPressed = previousEvent.pressedIds()
        val currentPressed = currentEvent.pressedIds()
        val newReleased = previousPressed - currentPressed.toSet()
        val sendingAsUp = HashSet<PointerId>(newReleased.size)
        var handled = false
        val lastIndex = when (currentEvent.eventType) {
            PointerEventType.Release -> newReleased.lastIndex - 1
            else -> newReleased.lastIndex
        }
        for (index in lastIndex downTo 0) {
            sendingAsUp.add(newReleased[index])
            handled = sendInternal(
                previousEvent.copySynthetic(PointerEventType.Release) { pointer ->
                    pointer.copySynthetic(down = pointer.down && pointer.id !in sendingAsUp)
                }
            ) || handled
        }
        return handled
    }

    private fun sendMissingPresses(currentEvent: PointerInputEvent): Boolean {
        val previousPressed = previousEvent?.pressedIds().orEmpty().toSet()
        val currentPressed = currentEvent.pressedIds()
        val newPressed = currentPressed - previousPressed
        val sendingAsDown = HashSet<PointerId>(newPressed.size)
        var handled = false
        val lastIndex = when (currentEvent.eventType) {
            PointerEventType.Press -> newPressed.lastIndex - 1
            else -> newPressed.lastIndex
        }
        for (index in 0..lastIndex) {
            sendingAsDown.add(newPressed[index])
            handled = sendInternal(
                currentEvent.copySynthetic(PointerEventType.Press) { pointer ->
                    pointer.copySynthetic(
                        down = pointer.id in previousPressed || pointer.id in sendingAsDown
                    )
                }
            ) || handled
        }
        return handled
    }

    private fun sendSyntheticMove(pointersSourceEvent: PointerInputEvent): Boolean {
        val previousEvent = previousEvent ?: return false
        val idToPosition = pointersSourceEvent.pointers.associate { it.id to it.position }
        return sendInternal(
            previousEvent.copySynthetic(PointerEventType.Move) { pointer ->
                pointer.copySynthetic(position = idToPosition[pointer.id] ?: pointer.position)
            }
        )
    }

    private fun PointerInputEvent.shouldSend(): Boolean {
        fun areSameParams(first: PointerInputEvent, second: PointerInputEvent): Boolean =
            first.pressedIds().toSet() == second.pressedIds().toSet() &&
                first.buttons == second.buttons &&
                first.keyboardModifiers == second.keyboardModifiers

        return when (eventType) {
            PointerEventType.Press -> previousEvent?.let { !areSameParams(this, it) } ?: true
            PointerEventType.Release -> previousEvent?.let { !areSameParams(this, it) } ?: false
            else -> true
        }
    }

    private fun sendNativeEventOnly(event: PointerInputEvent): Boolean =
        event.nativeEvent != null && dispatch(event.copy(eventType = PointerEventType.Unknown))

    private fun sendInternal(event: PointerInputEvent): Boolean {
        val handled = dispatch(event)
        if (!handled) {
            sendNativeEventOnly(event)
        }
        previousEvent = event.copy(
            nativeEvent = null,
            pointers = event.pointers.toList(),
        )
        return handled
    }

    private fun PointerInputEvent.pressedIds(): List<PointerId> =
        pointers.mapNotNull { pointer -> if (pointer.down) pointer.id else null }

    private fun PointerInputEvent.isMove(): Boolean =
        eventType == PointerEventType.Move ||
            eventType == PointerEventType.Enter ||
            eventType == PointerEventType.Exit

    private fun PointerInputEvent.isSamePosition(previousEvent: PointerInputEvent?): Boolean {
        val previousIdToPosition = previousEvent?.pointers?.associate { it.id to it.position }
        return pointers.all { pointer ->
            val previousPosition = previousIdToPosition?.get(pointer.id)
            previousPosition == null || pointer.position == previousPosition
        }
    }

    private fun PointerInputEvent.copySynthetic(
        eventType: PointerEventType,
        copyPointer: (PointerInputEventData) -> PointerInputEventData,
    ): PointerInputEvent = PointerInputEvent(
        eventType = eventType,
        uptime = uptime,
        pointers = pointers.map(copyPointer),
        buttons = buttons,
        keyboardModifiers = keyboardModifiers,
        nativeEvent = null,
        button = null,
    )

    private fun PointerInputEventData.copySynthetic(
        position: Offset = this.position,
        down: Boolean = this.down,
    ): PointerInputEventData = copy(
        positionOnScreen = position,
        position = position,
        down = down,
        historical = emptyList(),
        scrollDelta = Offset.Zero,
        scaleGestureFactor = 1f,
        panGestureOffset = Offset.Zero,
        originalEventPosition = position,
    )
}

private class WinUISnapshotInvalidationTracker(
    private val invalidate: () -> Unit,
) {
    private val commands = mutableListOf<() -> Unit>()
    private val commandsToRun = mutableListOf<() -> Unit>()
    private var isPerforming = false

    fun snapshotObserver(): OwnerSnapshotObserver = OwnerSnapshotObserver { command ->
        if (isPerforming) {
            command()
        } else {
            commands += command
            invalidate()
        }
    }

    fun sendAndPerformSnapshotChanges() {
        Snapshot.sendApplyNotifications()
        while (true) {
            if (commands.isEmpty()) return
            commandsToRun += commands
            commands.clear()
            isPerforming = true
            try {
                commandsToRun.forEach { command -> command() }
            } finally {
                commandsToRun.clear()
                isPerforming = false
            }
        }
    }
}
