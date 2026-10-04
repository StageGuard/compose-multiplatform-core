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
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LocalHostDefaultProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.retain.LocalRetainedValuesStoreProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.focus.WinUIPlatformFocusOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.keyboardModifiers
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.input.pointer.WinUIPointerIconService
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.navigationevent.WinUIBackNavigationEventInput
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.UiApplier
import androidx.compose.ui.node.WinUICoordinateMapper
import androidx.compose.ui.node.WinUIOwner
import androidx.compose.ui.skiko.RecordDrawRectRenderDecorator
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.WinUIInteropAction
import androidx.compose.ui.viewinterop.WinUIInteropTransaction
import androidx.compose.ui.viewinterop.WinUIRootContentHost
import androidx.compose.ui.viewinterop.WinUIRootContentControl
import androidx.compose.ui.viewinterop.collectWinUIInteropRoots
import androidx.compose.ui.window.LocalWinUIComposeLayerHost
import androidx.compose.ui.window.WinUIComposeLayerHost
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.enableSavedStateHandles
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import windows.foundation.EventRegistrationToken
import microsoft.ui.xaml.ElementTheme
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.XamlRoot
import microsoft.ui.xaml.XamlRootChangedEventArgs
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkikoRenderDelegate
import org.jetbrains.skiko.winui.WinUIAccessibilityActionRequest
import org.jetbrains.skiko.winui.WinUIAccessibilitySnapshot
import windows.foundation.TypedEventHandler
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Root host for a Compose hierarchy embedded in a WinUI tree.
 *
 * This is intentionally independent from Skiko/Desktop/AWT. Rendering, scheduling, and Owner
 * integration are filled in by the WinUI target rather than delegated to the desktop backend.
 */
class WinUIComposeView internal constructor(
    private val rootContentControl: WinUIRootContentControl,
    private val setBaseContent: (List<UIElement>) -> Unit,
    private val setRootContent: (List<UIElement>) -> Unit,
    private val scheduleInteropUpdate: (WinUIInteropAction) -> Unit,
    private val retrieveInteropTransaction: () -> WinUIInteropTransaction,
    private val onSensitiveContentChanged: (Boolean) -> Unit = {},
    private val window: Window? = null,
) {
    constructor() : this(WinUIRootContentHost())

    internal constructor(
        onSensitiveContentChanged: (Boolean) -> Unit,
    ) : this(WinUIRootContentHost(), onSensitiveContentChanged)

    internal constructor(
        window: Window,
        onSensitiveContentChanged: (Boolean) -> Unit,
    ) : this(WinUIRootContentHost(), onSensitiveContentChanged, window)

    // Register before any property initializer below (e.g. [owner]) touches the Skiko backend.
    init {
        registerSkikoComposeImplementation()
    }

    internal val rootNode = LayoutNode().also {
        it.measurePolicy = RootMeasurePolicy
    }
    val root: UIElement
        get() = rootContentControl
    internal val rootFrameworkElement: FrameworkElement
        get() = rootContentControl

    @InternalComposeUiApi
    val renderVersionForTest: Long
        get() = renderHost.renderVersionForTest

    @InternalComposeUiApi
    val renderApiForTest: GraphicsApi
        get() = renderHost.renderApiForTest

    @InternalComposeUiApi
    val lastRenderSizeForTest: IntSize?
        get() = renderHost.lastRenderSizeForTest

    @InternalComposeUiApi
    val lastRenderedStateSizeForTest: IntSize?
        get() = renderHost.lastRenderedStateSizeForTest

    @InternalComposeUiApi
    val pendingRenderStateSizeForTest: IntSize?
        get() = renderHost.pendingRenderStateSizeForTest

    @InternalComposeUiApi
    val renderFailureForTest: String?
        get() = renderHost.renderFailureForTest

    @InternalComposeUiApi
    val lastDrawRectForTest: Rect
        get() = lastDrawRect

    @InternalComposeUiApi
    val isRenderSchedulerStartedForTest: Boolean
        get() = renderHost.isFrameSchedulerStartedForTest

    @InternalComposeUiApi
    val isLoadedRenderSchedulerRegistrationPendingForTest: Boolean
        get() = loadedRenderSchedulerToken != null

    @InternalComposeUiApi
    val accessibilitySnapshotForTest: WinUIAccessibilitySnapshot?
        get() = owner.accessibilityProvider.snapshot()

    @InternalComposeUiApi
    fun performAccessibilityActionForTest(request: WinUIAccessibilityActionRequest): Boolean =
        owner.accessibilityProvider.performAction(request)

    @InternalComposeUiApi
    fun sendMouseMoveForTest(position: Offset): Boolean =
        sendMousePointerEventForTest(
            eventType = PointerEventType.Move,
            position = position,
            down = false,
            buttons = PointerButtons(),
            button = null,
        )

    @InternalComposeUiApi
    fun sendMousePressForTest(position: Offset): Boolean =
        sendMousePointerEventForTest(
            eventType = PointerEventType.Press,
            position = position,
            down = true,
            buttons = PointerButtons(isPrimaryPressed = true),
            button = PointerButton.Primary,
        )

    @InternalComposeUiApi
    fun sendMouseReleaseForTest(position: Offset): Boolean =
        sendMousePointerEventForTest(
            eventType = PointerEventType.Release,
            position = position,
            down = false,
            buttons = PointerButtons(),
            button = PointerButton.Primary,
        )

    @InternalComposeUiApi
    fun sendMouseScrollForTest(position: Offset, scrollDelta: Offset): Boolean =
        sendMousePointerEventForTest(
            eventType = PointerEventType.Scroll,
            position = position,
            down = false,
            buttons = PointerButtons(),
            button = null,
            scrollDelta = scrollDelta,
        )

    @InternalComposeUiApi
    private fun sendMousePointerEventForTest(
        eventType: PointerEventType,
        position: Offset,
        down: Boolean,
        buttons: PointerButtons,
        button: PointerButton?,
        scrollDelta: Offset = Offset.Zero,
    ): Boolean =
        owner.sendPointerEventForTest(
            eventType = eventType,
            position = position,
            uptimeMillis = System.nanoTime() / 1_000_000L,
            pointerId = 1L,
            down = down,
            type = PointerType.Mouse,
            buttons = buttons,
            keyboardModifiers = PointerKeyboardModifiers(),
            button = button,
            scrollDelta = scrollDelta,
        )

    private val architectureComponentsOwner = DefaultArchitectureComponentsOwner(
        enforceMainThread = false,
    ).apply {
        enableSavedStateHandles()
        setLifecycleState(Lifecycle.State.RESUMED)
    }
    private val hostDefaultProvider = WinUIHostDefaultProvider(architectureComponentsOwner)
    private val retainedValuesStore = WinUIRetainedValuesStore()
    private val displayRequestController = WinUIDisplayRequestController()
    private val pointerCursorAdapter = WinUIPointerCursorAdapter(rootContentControl)
    private val pointerIconService = WinUIPointerIconService(pointerCursorAdapter::setIcon)
    private var lastDrawRect = Rect.Zero
    private val renderDelegate = RecordDrawRectRenderDecorator(
        object : SkikoRenderDelegate {
            override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                render(canvas, nanoTime)
            }
        },
        onDrawRectChange = { lastDrawRect = it },
    )
    private val renderHost = WinUISkikoRenderHost(
        renderDelegate = renderDelegate,
        beforeDrawSubmission = ::drainPendingInteropTransactions,
    )
    init {
        setBaseContent(listOf(renderHost.component))
    }
    private val dispatchQueue by lazy { WinUIDispatchQueue(requireRootDispatcherQueue()) }
    private var ownerCoroutineContext: CoroutineContext = EmptyCoroutineContext

    internal val owner = WinUIOwner(
        root = rootNode,
        platformFocusOwner = WinUIPlatformFocusOwner(root),
        retainedValuesStore = retainedValuesStore,
        coroutineContextProvider = { ownerCoroutineContext },
        onMeasureAndLayoutRequested = ::scheduleRootContentSync,
        onInteropTreeChanged = ::syncRootContent,
        onRootInvalidated = ::invalidateRootLayer,
        onInteropTransactionScheduled = ::scheduleInteropTransaction,
        onAccessibilityUpdate = renderHost::notifyAccessibilityChanged,
        onKeepScreenOnChanged = displayRequestController::setKeepScreenOn,
        onSensitiveContentChanged = onSensitiveContentChanged,
        scheduleOutOfFrame = ::scheduleOutOfFrame,
        coordinateMapper = WinUICoordinateMapper.forRoot(
            root,
            screenCoordinatesReady = { isScreenCoordinateConversionReady },
            density = { rootNode.density.density },
        ),
        textToolbar = WinUITextToolbar(
            hostProvider = { rootContentControl },
            densityProvider = { rootNode.density },
        ),
        pointerIconService = pointerIconService,
    )
    internal val isScreenCoordinateConversionReady: Boolean
        get() = rootContentControl.isLoaded && !owner.isMeasureLayoutInProgress
    init {
        renderHost.setAccessibilityProvider(owner.accessibilityProvider)
        WinUIPlatformTextInputService.registerRootToScreenMapper(
            owner = this,
            mapper = ::rootPixelOffsetToScreen,
            screenMapperReady = { isScreenCoordinateConversionReady },
            viewportMapper = ::rootPixelOffsetToViewport,
            viewportBoundsInRoot = ::rootViewportBoundsInRoot,
        )
    }

    private var frameRecomposer: FrameRecomposer? = null
    private var composition: Composition? = null
    private var saveableState: Map<String, List<Any?>>? = null
    private var saveableStateRegistry: SaveableStateRegistry? = null
    private var content: (@Composable () -> Unit)? = null
    private var platformWindowInsets: PlatformWindowInsets by mutableStateOf(EmptyPlatformWindowInsets)
    private var currentInteropRoots: List<UIElement> = emptyList()
    private var isRootContentSyncScheduled = false
    private var isRenderRequestFlushScheduled = false
    private var isApplyingOwnerChanges = false
    private var isDrawingFrame = false
    private var hasPendingRenderRequest = false
    private var isDisposed = false
    private var loadedRenderSchedulerHandler: RoutedEventHandler? = null
    private var loadedRenderSchedulerToken: EventRegistrationToken? = null
    private var xamlRoot: XamlRoot? = null
    private var xamlRootChangedHandler: TypedEventHandler<XamlRoot, XamlRootChangedEventArgs>? =
        null
    private var xamlRootChangedToken: EventRegistrationToken? = null
    private val backNavigationEventInput = WinUIBackNavigationEventInput().also {
        architectureComponentsOwner.navigationEventDispatcherOwner
            .navigationEventDispatcher.addInput(it)
    }
    private val keyInputAdapter = WinUIKeyInputAdapter(
        root = root,
        owner = owner,
        composeEventSources = { listOf(root) },
        composeEventSubtreeSources = { renderHost.keyEventSources },
        sendKeyEvent = ::sendKeyEvent,
    )
    private val layerHost = WinUIComposeLayerHost(
        root = rootNode,
        focusOwner = { owner.focusOwner },
    )
    private val pointerInputAdapter = WinUIPointerInputAdapter(
        root = renderHost.component,
        owner = owner,
        beforeEvent = ::prepareForPointerEvent,
        afterEvent = ::performInputEventWork,
    )
    private var systemTheme: SystemTheme by mutableStateOf(SystemTheme.Unknown)
    private val actualThemeChangedHandler: TypedEventHandler<FrameworkElement, Any?> =
        { _, _ -> updateSystemTheme() }
    private val actualThemeChangedToken: EventRegistrationToken? = runCatching {
        rootContentControl.actualThemeChanged.add(actualThemeChangedHandler)
    }.getOrNull()
    private var isWindowFocused = true
    private var isWindowMinimized = false
    private val dragAndDropAdapter = WinUIDragAndDropAdapter(root, owner)

    fun setContent(content: @Composable () -> Unit) {
        check(!isDisposed) {
            "Cannot set content on a disposed WinUIComposeView."
        }
        this.content = content
        val currentComposition = composition ?: createComposition().also {
            composition = it
        }
        currentComposition.setContent {
            val registry = remember {
                SaveableStateRegistry(saveableState) { true }.also {
                    saveableStateRegistry = it
                }
            }
            CompositionLocalProvider(
                androidx.lifecycle.compose.LocalLifecycleOwner provides
                    architectureComponentsOwner.lifecycleOwner,
                LocalSavedStateRegistryOwner provides
                    architectureComponentsOwner.savedStateRegistryOwner,
                LocalSaveableStateRegistry provides registry,
                LocalHostDefaultProvider provides hostDefaultProvider,
                LocalPlatformWindowInsets provides platformWindowInsets,
                LocalPlatformPrefetchScheduler provides NoOpPlatformPrefetchScheduler,
                LocalWinUIRoot provides rootContentControl,
                LocalWinUIWindow provides window,
                LocalSystemTheme provides systemTheme,
                LocalWinUIComposeLayerHost provides layerHost,
            ) {
                LocalRetainedValuesStoreProvider(retainedValuesStore) {
                    ProvideCommonCompositionLocals(
                        owner = owner,
                        content = content,
                    )
                }
            }
        }
        updateSystemTheme()
        startRenderSchedulerWhenLoaded()
        syncRootContent()
        requestRender()
    }

    /**
     * Follows the theme of the XAML root, which follows the system app theme unless the
     * application requests one, as `isSystemInDarkTheme()` follows the system theme on desktop.
     */
    private fun updateSystemTheme() {
        if (isDisposed) return
        systemTheme = when (runCatching { rootContentControl.actualTheme }.getOrNull()) {
            ElementTheme.Dark -> SystemTheme.Dark
            ElementTheme.Light -> SystemTheme.Light
            else -> SystemTheme.Unknown
        }
    }

    fun disposeComposition() {
        layerHost.dispose()
        val currentComposition = composition
        if (currentComposition != null) {
            saveableState = saveableStateRegistry?.performSave()
            saveableStateRegistry = null
            currentComposition.dispose()
        }
        ownerCoroutineContext = EmptyCoroutineContext
        composition = null
        frameRecomposer?.close()
        frameRecomposer = null
        content = null
        clearLoadedRenderSchedulerRequest()
        renderHost.detachSurface()
        updateRootContent(emptyList())
        rootNode.removeAll()
        displayRequestController.setKeepScreenOn(false)
        requestRender()
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        disposeComposition()
        keyInputAdapter.dispose()
        pointerInputAdapter.dispose()
        dragAndDropAdapter.dispose()
        pointerCursorAdapter.dispose()
        retainedValuesStore.dispose()
        architectureComponentsOwner.navigationEventDispatcherOwner
            .navigationEventDispatcher.removeInput(backNavigationEventInput)
        actualThemeChangedToken?.let { token ->
            runCatching { rootContentControl.actualThemeChanged.remove(token) }
        }
        architectureComponentsOwner.setLifecycleState(Lifecycle.State.DESTROYED)
        clearXamlRootDensityObserver()
        WinUIPlatformTextInputService.unregisterRootToScreenMapper(this)
        owner.dispose()
        clearLoadedRenderSchedulerRequest()
        renderHost.close()
    }

    internal fun setWindowFocused(isWindowFocused: Boolean) {
        owner.setWindowFocused(isWindowFocused)
        WinUIPlatformTextInputService.onWindowFocusChanged(isWindowFocused)
        if (!isWindowFocused) {
            keyInputAdapter.resetModifiers()
        }
        this.isWindowFocused = isWindowFocused
        updateLifecycleState()
    }

    internal fun setWindowMinimized(isWindowMinimized: Boolean) {
        this.isWindowMinimized = isWindowMinimized
        updateLifecycleState()
    }

    /**
     * Called when the window has moved on the screen.
     */
    internal fun invalidatePositionOnScreen() {
        if (isDisposed) return
        owner.invalidatePositionOnScreen()
    }

    // The same states as on desktop: resumed while the window has focus, started while it is
    // visible without focus, created while it is minimized.
    private fun updateLifecycleState() {
        if (isDisposed) return
        architectureComponentsOwner.setLifecycleState(
            when {
                isWindowMinimized -> Lifecycle.State.CREATED
                isWindowFocused -> Lifecycle.State.RESUMED
                else -> Lifecycle.State.STARTED
            }
        )
    }

    internal fun setWindowContainerSize(size: IntSize) {
        updateDensityFromXamlRoot()
        owner.setWindowContainerSize(size)
        renderHost.setSize(size, owner.density)
        requestRender()
    }

    internal fun setTransparentRootBackground() {
        rootContentControl.setTransparentBackground()
    }

    /**
     * Gives the window the opaque background of a desktop window, which content without a
     * background of its own shows; without one, the XAML window shows its theme background,
     * which is black in dark mode. A system backdrop needs the root to be transparent.
     */
    internal fun setWindowBackground(isOpaque: Boolean) {
        if (isOpaque) {
            // The Swing panel background, which a desktop ComposeWindow clears with.
            rootContentControl.setOpaqueBackground(0xEEu, 0xEEu, 0xEEu)
            // The swap chain ignores alpha, so the frame itself has to start with the background.
            frameBackgroundColor = DesktopWindowBackground
        } else {
            rootContentControl.clearBackground()
            frameBackgroundColor = null
        }
        if (content != null) {
            requestRender()
        }
    }

    private var frameBackgroundColor: Int? = null

    internal fun setWindowTitleBarInsets(
        height: Int,
        leftPadding: Int,
        rightPadding: Int,
    ) {
        platformWindowInsets = WinUIPlatformWindowInsets(
            captionBarHeight = height,
            captionBarLeftPadding = leftPadding,
            captionBarRightPadding = rightPadding,
        )
    }

    @InternalComposeUiApi
    fun setWindowContainerSizeForTest(size: IntSize) {
        setWindowContainerSize(size)
    }

    @InternalComposeUiApi
    fun performFrameForTest(nanoTime: Long = System.nanoTime()) {
        applyOwnerChanges {
            frameRecomposer?.performFrame(nanoTime)
            owner.sendAndPerformSnapshotChanges()
            owner.measureAndLayout(sendPointerUpdate = false)
            owner.sendAndPerformSnapshotChanges()
            updateRootContent(rootNode.collectWinUIInteropRoots())
        }
    }

    private fun createComposition(): Composition {
        val dispatcherQueue = requireRootDispatcherQueue()
        WinUIScheduler.register(dispatcherQueue)
        // FrameRecomposer queues its work on dispatchers that follow the host dispatcher and rolls
        // those queues in performFrame, so the host dispatcher must always dispatch: with an
        // immediate one, work resumed on the UI thread would bypass the queues and miss the frame.
        val dispatcher = WinUIDispatcher(dispatcherQueue, immediate = false)
        val currentFrameRecomposer = FrameRecomposer(dispatcher, ::requestRender)
        frameRecomposer = currentFrameRecomposer
        ownerCoroutineContext = currentFrameRecomposer.compositionContext.effectCoroutineContext
        val applier = UiApplier(rootNode, ::scheduleRootContentSync)
        return Composition(
            applier = applier,
            parent = currentFrameRecomposer.compositionContext,
        )
    }

    private fun syncRootContent() {
        if (owner.isMeasureLayoutInProgress) {
            scheduleRootContentSync()
            return
        }
        applyOwnerChanges {
            owner.sendAndPerformSnapshotChanges()
            updateRootContent(rootNode.collectWinUIInteropRoots())
            measureAndLayout()
            owner.sendAndPerformSnapshotChanges()
            updateRootContent(rootNode.collectWinUIInteropRoots())
            requestRender()
        }
    }

    /**
     * Lays out the content and, when the layout under a resting mouse pointer may have changed,
     * sends it a synthetic move once the layout is over, as the Skiko scenes do. Without it, hover
     * state stays on the old element after scrolling or navigation until the mouse moves.
     */
    private fun measureAndLayout() {
        owner.measureAndLayout(sendPointerUpdate = true)
        if (owner.needUpdatePointerPosition) {
            frameRecomposer?.dispatch(owner::updatePointerPosition)
        }
    }

    private fun prepareForPointerEvent(event: WinUIPointerEvent) {
        if (isDisposed) return
        dragAndDropAdapter.onPointerEvent(event)
        if (owner.isMeasureLayoutInProgress) return
        // Hit testing must see the layout of the latest changes.
        owner.measureAndLayout(sendPointerUpdate = false)
        layerHost.onPointerEvent(
            eventType = event.eventType,
            position = event.position,
            button = event.button,
            isAnyButtonPressed = event.buttons.areAnyPressed,
        )
    }

    // Runs the work that the event handlers scheduled (coroutines they resumed, effects they
    // launched) right after the event, as the Skiko scenes do, instead of a frame later.
    private fun performInputEventWork() {
        if (isDisposed) return
        frameRecomposer?.performTrampolineDispatch()
    }

    private fun sendKeyEvent(keyEvent: KeyEvent): Boolean {
        owner.setKeyboardModifiers(keyEvent.keyboardModifiers)
        val handled = owner.sendKeyEvent(keyEvent) ||
            backNavigationEventInput.onKeyEvent(keyEvent)
        performInputEventWork()
        return handled
    }

    private fun render(canvas: Canvas, nanoTime: Long) {
        renderHost.performDrawSubmission {
            if (isDisposed) return@performDrawSubmission
            check(!isDrawingFrame) {
                "WinUIComposeView cannot render recursively."
            }
            isDrawingFrame = true
            try {
                applyOwnerChanges {
                    frameRecomposer?.performFrame(nanoTime)
                    owner.sendAndPerformSnapshotChanges()
                    measureAndLayout()
                    owner.sendAndPerformSnapshotChanges()
                    updateRootContent(rootNode.collectWinUIInteropRoots())
                    frameBackgroundColor?.let(canvas::clear)
                    owner.draw(canvas.asComposeCanvas())
                }
            } finally {
                isDrawingFrame = false
                if (hasPendingRenderRequest) {
                    debugRender { "drawSubmit schedule pending render after draw" }
                    scheduleRenderRequestFlush()
                }
            }
        }
    }

    private fun invalidateRootLayer() {
        scheduleRootContentSync()
        requestRender()
    }

    private fun startRenderSchedulerWhenLoaded() {
        if (runCatching { rootContentControl.isLoaded }.getOrDefault(false)) {
            startRenderScheduler()
        } else if (loadedRenderSchedulerToken == null) {
            val handler: RoutedEventHandler = { _, _ ->
                clearLoadedRenderSchedulerRequest()
                if (!isDisposed) {
                    updateDensityFromXamlRoot()
                    updateSystemTheme()
                    startRenderScheduler()
                    requestRender()
                }
            }
            loadedRenderSchedulerHandler = handler
            loadedRenderSchedulerToken = rootContentControl.loaded.add(handler)
        }
    }

    private fun startRenderScheduler() {
        if (!isDisposed && content != null) {
            renderHost.startFrameScheduler()
        }
    }

    private fun clearLoadedRenderSchedulerRequest() {
        loadedRenderSchedulerToken?.let { token ->
            runCatching { rootContentControl.loaded.remove(token) }
            loadedRenderSchedulerToken = null
        }
        loadedRenderSchedulerHandler = null
    }

    private fun updateDensityFromXamlRoot() {
        val currentXamlRoot = runCatching { rootContentControl.xamlRoot }.getOrNull()
        if (currentXamlRoot != xamlRoot) {
            clearXamlRootDensityObserver()
            xamlRoot = currentXamlRoot
            if (currentXamlRoot != null) {
                val handler: TypedEventHandler<XamlRoot, XamlRootChangedEventArgs> = { _, _ ->
                    if (!isDisposed) {
                        updateDensityFromXamlRoot()
                        scheduleRootContentSync()
                        requestRender()
                    }
                }
                xamlRootChangedHandler = handler
                xamlRootChangedToken = runCatching { currentXamlRoot.changed.add(handler) }
                    .getOrNull()
            }
        }

        val scale = currentXamlRoot?.rasterizationScale?.toFloat()
            ?.takeIf { it.isFinite() && it > 0f }
            ?: 1f
        owner.updateDensity(Density(scale, owner.density.fontScale))
        owner.windowInfo.containerSize.takeIf { it.width > 0 && it.height > 0 }?.let { size ->
            renderHost.setSize(size, owner.density)
        }
    }

    private fun clearXamlRootDensityObserver() {
        val currentXamlRoot = xamlRoot
        val token = xamlRootChangedToken
        xamlRootChangedToken = null
        xamlRootChangedHandler = null
        if (currentXamlRoot != null && token != null) {
            runCatching { currentXamlRoot.changed.remove(token) }
        }
        xamlRoot = null
    }

    private fun requestRender() {
        if (isDisposed) return
        hasPendingRenderRequest = true
        debugRender {
            "requestRender scheduled drawing=$isDrawingFrame applying=$isApplyingOwnerChanges " +
                "measureLayout=${owner.isMeasureLayoutInProgress}"
        }
        scheduleRenderRequestFlush()
    }

    private fun applyOwnerChanges(block: () -> Unit) {
        if (isApplyingOwnerChanges) {
            block()
            return
        }
        isApplyingOwnerChanges = true
        try {
            block()
        } finally {
            isApplyingOwnerChanges = false
            if (hasPendingRenderRequest) {
                scheduleRenderRequestFlush()
            }
        }
    }

    private fun flushPendingRenderRequest() {
        if (isDisposed || !hasPendingRenderRequest) return
        if (isDrawingFrame || isApplyingOwnerChanges || owner.isMeasureLayoutInProgress) {
            debugRender {
                "flushRender deferred drawing=$isDrawingFrame applying=$isApplyingOwnerChanges " +
                    "measureLayout=${owner.isMeasureLayoutInProgress}"
            }
            if (!isDrawingFrame) {
                scheduleRenderRequestFlush()
            }
            return
        }
        hasPendingRenderRequest = false
        debugRender { "flushRender delegated" }
        renderHost.requestRender()
    }

    private fun scheduleRenderRequestFlush() {
        if (isDisposed || isRenderRequestFlushScheduled) return
        isRenderRequestFlushScheduled = true
        if (!dispatchQueue.dispatch {
                isRenderRequestFlushScheduled = false
                flushPendingRenderRequest()
            }
        ) {
            isRenderRequestFlushScheduled = false
            if (!isDrawingFrame && !isApplyingOwnerChanges && !owner.isMeasureLayoutInProgress) {
                flushPendingRenderRequest()
            }
        }
    }

    private fun scheduleRootContentSync() {
        if (isDisposed || isRootContentSyncScheduled) return
        isRootContentSyncScheduled = true
        if (!dispatchQueue.dispatch {
                isRootContentSyncScheduled = false
                if (!isDisposed) {
                    syncRootContent()
                }
            }
        ) {
            isRootContentSyncScheduled = false
            if (!isDisposed) {
                syncRootContent()
            }
        }
    }

    private fun scheduleOutOfFrame(block: () -> Unit) {
        if (!dispatchQueue.dispatch { block() }) {
            block()
        }
    }

    private fun requireRootDispatcherQueue() = checkNotNull(root.dispatcherQueue) {
        "WinUI root DispatcherQueue is not available."
    }

    private fun rootPixelOffsetToScreen(offset: Offset): Offset = owner.localToScreen(offset)

    private fun rootPixelOffsetToViewport(offset: Offset): Offset {
        val scale = owner.density.density.takeIf { it.isFinite() && it > 0f } ?: 1f
        return rootPixelOffsetToCoreTextViewportVisualPixels(offset, scale)
    }

    private fun rootViewportBoundsInRoot(): Rect? {
        val size = owner.windowInfo.containerSize
        return if (size.width > 0 && size.height > 0) {
            Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
        } else {
            null
        }
    }

    private fun updateRootContent(content: List<UIElement>) {
        val contentChanged = !currentInteropRoots.hasSameIdentityOrder(content)
        if (contentChanged) {
            currentInteropRoots = content
            setRootContent(content)
        }
        val transaction = retrieveInteropTransaction()
        if (contentChanged || transaction.actions.isNotEmpty()) {
            transaction.performTransaction()
        }
        drainPendingInteropTransactions()
    }

    private fun drainPendingInteropTransactions() {
        while (true) {
            val transaction = retrieveInteropTransaction()
            if (transaction.actions.isEmpty()) return
            transaction.performTransaction()
        }
    }

    private fun scheduleInteropTransaction(action: WinUIInteropAction) {
        scheduleInteropUpdate(action)
        scheduleRootContentSync()
    }

    private constructor(host: WinUIRootContentHost) : this(
        host.root,
        host::setBaseContent,
        host::setRootContent,
        host::scheduleUpdate,
        host::retrieveTransaction,
    )

    private constructor(
        host: WinUIRootContentHost,
        onSensitiveContentChanged: (Boolean) -> Unit,
        window: Window? = null,
    ) : this(
        host.root,
        host::setBaseContent,
        host::setRootContent,
        host::scheduleUpdate,
        host::retrieveTransaction,
        onSensitiveContentChanged,
        window,
    )
}

private const val DesktopWindowBackground = 0xFFEEEEEE.toInt()

@Suppress("UNUSED_PARAMETER")
internal fun rootPixelOffsetToCoreTextViewportVisualPixels(
    offset: Offset,
    densityScale: Float,
): Offset {
    val scale = densityScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    // Despite the property name, CoreText LayoutBoundsVisualPixels are
    // viewport-relative device-independent pixels.
    return Offset(offset.x / scale, offset.y / scale)
}

fun Window.setContent(content: @Composable () -> Unit): WinUIComposeView {
    val composeView = WinUIComposeView(this) {}
    this.content = composeView.root
    composeView.setContent(content)
    return composeView
}

@InternalComposeUiApi
fun WinUIComposeView.sendPointerEventForTest(
    eventType: PointerEventType,
    position: Offset,
    uptimeMillis: Long,
    pointerId: Long = 0L,
    down: Boolean = eventType != PointerEventType.Release && eventType != PointerEventType.Exit,
    type: PointerType = PointerType.Touch,
    buttons: PointerButtons = PointerButtons(),
    keyboardModifiers: PointerKeyboardModifiers = PointerKeyboardModifiers(),
    button: PointerButton? = null,
    scrollDelta: Offset = Offset.Zero,
    isInBounds: Boolean = eventType != PointerEventType.Exit,
): Boolean = owner.sendPointerEventForTest(
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
)

private fun List<UIElement>.hasSameIdentityOrder(other: List<UIElement>): Boolean {
    if (size != other.size) return false
    return indices.all { index ->
        this[index].nativeObject.sameIdentity(other[index].nativeObject)
    }
}
