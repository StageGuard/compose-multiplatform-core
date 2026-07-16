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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.WinUIDragAndDropManager
import androidx.compose.ui.draganddrop.WinUIDragAndDropStarter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.node.WinUIOwner
import androidx.compose.ui.unit.LayoutDirection
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRTEvent
import io.github.composefluent.winrt.runtime.await
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import microsoft.ui.input.PointerPoint
import microsoft.ui.xaml.DragEventArgs
import microsoft.ui.xaml.DragEventHandler
import microsoft.ui.xaml.DragStartingEventArgs
import microsoft.ui.xaml.DropCompletedEventArgs
import microsoft.ui.xaml.UIElement
import windows.applicationmodel.datatransfer.DataPackage
import windows.applicationmodel.datatransfer.DataPackageOperation
import windows.applicationmodel.datatransfer.DataPackageView
import windows.applicationmodel.datatransfer.DataProviderHandler
import windows.foundation.EventRegistrationToken
import windows.graphics.imaging.BitmapAlphaMode
import windows.graphics.imaging.BitmapPixelFormat
import windows.graphics.imaging.SoftwareBitmap
import windows.storage.streams.DataWriter

@OptIn(InternalComposeUiApi::class)
internal class WinUIDragAndDropAdapter(
    private val root: UIElement,
    private val source: UIElement = root,
    private val owner: WinUIOwner,
    private val coroutineContextProvider: () -> CoroutineContext = { owner.coroutineContext },
) : WinUIDragAndDropStarter {
    private val dragAndDropManager: WinUIDragAndDropManager = owner.winUIDragAndDropManager
    private val lifecycleJob = SupervisorJob()
    private var isDisposed = false
    private var eventScope: CoroutineScope? = null
    private var activeTransfer: WinUIActiveDragTransfer? = null
    private var pendingDrop: WinUIPendingDrop? = null
    private var latestPointerPoint: PointerPoint? = null
    private var pendingSourceTransfer: WinUIPendingSourceTransfer? = null
    private var activeDragOperation: WinRTAsyncOperationReference<DataPackageOperation>? = null
    private val dragSession =
        WinUIDragSessionController(
            onStart = dragAndDropManager::onDragStarted,
            onEnd = dragAndDropManager::onDragEnded,
            onClearTransfer = ::clearActiveTransfer,
        )
    private val registrations =
        listOf(
            register(root.dragEnter, ::handleDragEnter),
            register(root.dragOver, ::handleDragOver),
            register(root.dragLeave, ::handleDragLeave),
            register(root.drop, ::handleDrop),
        )
    private val sourceUnregisterActions = registerSourceEvents()

    init {
        root.allowDrop = true
        dragAndDropManager.setStarter(this)
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        val cleanupActions = buildList<() -> Unit> {
            add { dragAndDropManager.clearStarter(this@WinUIDragAndDropAdapter) }
            add(::cancelPendingDrop)
            add {
                runWinUIDragSourceCleanup(
                    closeActiveDragOperation = ::closeActiveDragOperation,
                    clearPendingSourceTransfer = ::clearPendingSourceTransfer,
                    clearPointerPoint = { latestPointerPoint = null },
                )
            }
            add { lifecycleJob.cancel() }
            registrations.forEach { registration ->
                add { registration.event.remove(registration.token) }
            }
            addAll(sourceUnregisterActions)
            add { dragSession.terminate(DragAndDropEvent()) }
            add { root.allowDrop = false }
        }
        runWinUIDragAndDropCleanup(*cleanupActions.toTypedArray())
    }

    override fun startDragAndDropTransfer(
        transferData: DragAndDropTransferData,
        decorationSize: Size,
        drawDragDecoration: DrawScope.() -> Unit,
    ): Boolean {
        if (isDisposed || pendingSourceTransfer != null || activeDragOperation != null) return false
        val pointerPoint = latestPointerPoint ?: return false
        pendingSourceTransfer =
            WinUIPendingSourceTransfer(
                transferData = transferData,
                decoration =
                    runCatching {
                            renderWinUIDragDecoration(
                                    decorationSize = decorationSize,
                                    density = owner.density,
                                    drawDragDecoration = drawDragDecoration,
                                )
                                .toSoftwareBitmap()
                        }
                        .getOrNull(),
            )
        val operation = runCatching { source.startDragAsync(pointerPoint) }.getOrNull()
        if (operation == null) {
            clearPendingSourceTransfer()
            return false
        }
        if (pendingSourceTransfer == null) {
            runCatching { operation.cancel() }
            runCatching { operation.close() }
            return false
        }
        activeDragOperation = operation
        return true
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDragEnter(args: DragEventArgs): Boolean {
        val dispatch = args.toComposeDragAndDropDispatch() ?: return rejectTerminalEvent(args)
        val accepted =
            dispatch.withPlatformData {
                val accepted = dragSession.ensureStarted(dispatch.event)
                dragAndDropManager.onDragEntered(dispatch.event)
                accepted
            }
        return advertiseAcceptedOperation(args, dispatch, accepted)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDragOver(args: DragEventArgs): Boolean {
        val dispatch = args.toComposeDragAndDropDispatch() ?: return rejectTerminalEvent(args)
        val accepted =
            dispatch.withPlatformData {
                val accepted = dragSession.ensureStarted(dispatch.event)
                dragAndDropManager.onDragMoved(dispatch.event)
                accepted
            }
        return advertiseAcceptedOperation(args, dispatch, accepted)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDragLeave(args: DragEventArgs): Boolean {
        setAcceptedOperation(args, false)
        if (pendingDrop != null) return true
        val dispatch = args.toComposeDragAndDropDispatch() ?: return rejectTerminalEvent(args)
        dispatch.withPlatformData {
            try {
                if (dragSession.isActive) {
                    dragAndDropManager.onDragExited(dispatch.event)
                }
            } finally {
                dragSession.terminate(dispatch.event)
            }
        }
        return false
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDrop(args: DragEventArgs): Boolean {
        cancelPendingDrop()
        val dispatch = args.toComposeDragAndDropDispatch() ?: return rejectTerminalEvent(args)
        val accepted = dispatch.withPlatformData { dragSession.ensureStarted(dispatch.event) }
        if (!setAcceptedOperation(args, accepted)) {
            abortDrop(dispatch)
            return false
        }

        if (!dispatch.transfer.metadata.containsPlainText) {
            return finishSynchronousDrop(args, dispatch)
        }

        val scope = eventCoroutineScope() ?: return finishSynchronousDrop(args, dispatch)
        val deferral =
            runCatching { args.getDeferral() }.getOrNull()
                ?: return finishSynchronousDrop(args, dispatch)
        startPlainTextRead(dispatch.transfer)
        lateinit var pending: WinUIPendingDrop
        val termination =
            createDropTermination(
                args = args,
                dispatch = dispatch,
                completeDeferral = deferral::complete,
                onTerminated = {
                    if (pendingDrop === pending) pendingDrop = null
                },
            )
        pending = WinUIPendingDrop(termination)
        pendingDrop = pending
        pending.job =
            scope.launchWinUIDropRead(
                loadPlainText = {
                    val plainTextRead = dispatch.transfer.plainTextRead
                    if (plainTextRead != null) {
                        plainTextRead.await()
                    } else {
                        dispatch.transfer.metadata.loadPlainText()
                    }
                },
                termination = termination,
                onHandled = {},
            )
        return true
    }

    private fun advertiseAcceptedOperation(
        args: DragEventArgs,
        dispatch: WinUIComposeDragAndDropDispatch,
        accepted: Boolean,
    ): Boolean {
        val advertised = setAcceptedOperation(args, accepted)
        if (advertised) {
            startPlainTextRead(dispatch.transfer)
        } else {
            abortDrop(dispatch)
        }
        return advertised
    }

    private fun setAcceptedOperation(args: DragEventArgs, accepted: Boolean): Boolean {
        val assigned =
            runCatching { args.acceptedOperation = winUIDragAcceptedOperation(accepted) }.isSuccess
        return accepted && assigned
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun rejectTerminalEvent(args: DragEventArgs): Boolean {
        setAcceptedOperation(args, false)
        dragSession.terminate(DragAndDropEvent())
        return false
    }

    private fun register(
        event: WinRTEvent<DragEventHandler>,
        dispatch: (DragEventArgs) -> Boolean,
    ): WinUIDragAndDropEventRegistration {
        val handler: DragEventHandler = { _, args ->
            if (!isDisposed && !args.handled) {
                args.handled = dispatch(args)
            }
        }
        return WinUIDragAndDropEventRegistration(event, event.add(handler), handler)
    }

    private fun registerSourceEvents(): List<() -> Unit> {
        val dragStarting: (UIElement, DragStartingEventArgs) -> Unit = { _, args ->
            handleDragStarting(args)
        }
        val dropCompleted: (UIElement, DropCompletedEventArgs) -> Unit = { _, _ ->
            if (
                shouldHandleWinUIDropCompleted(
                    hasPendingSourceTransfer = pendingSourceTransfer != null,
                    hasActiveDragOperation = activeDragOperation != null,
                )
            ) {
                runWinUIDragSourceCleanup(
                    closeActiveDragOperation = { closeActiveDragOperation(cancel = false) },
                    clearPendingSourceTransfer = ::clearPendingSourceTransfer,
                    clearPointerPoint = { latestPointerPoint = null },
                )
            }
        }
        return listOf(
            registerSourceEvent(source.dragStarting, dragStarting),
            registerSourceEvent(source.dropCompleted, dropCompleted),
        )
    }

    private fun <T : Any> registerSourceEvent(event: WinRTEvent<T>, handler: T): () -> Unit {
        val token = event.add(handler)
        return { event.remove(token) }
    }

    private fun handleDragStarting(args: DragStartingEventArgs) {
        val pending = pendingSourceTransfer
        val dataPackage = args.data
        val populated =
            pending != null &&
                dataPackage != null &&
                runCatching { populateDragDataPackage(dataPackage, pending) }.getOrDefault(false)
        when (
            winUIDragStartingDecision(
                hasPendingSourceTransfer = pending != null,
                hasDataPackage = dataPackage != null,
                populatedDataPackage = populated,
            )
        ) {
            WinUIDragStartingDecision.Ignore -> return
            WinUIDragStartingDecision.Cancel -> {
                args.cancel = true
                clearPendingSourceTransfer()
                return
            }
            WinUIDragStartingDecision.Start -> Unit
        }
        args.allowedOperations = DataPackageOperation.Copy
        val dragUI = args.dragUI
        val decoration = checkNotNull(pending).decoration
        if (decoration != null && dragUI != null) {
            runCatching { dragUI.setContentFromSoftwareBitmap(decoration) }
                .onFailure { runCatching { dragUI.setContentFromDataPackage() } }
        } else if (dragUI != null) {
            runCatching { dragUI.setContentFromDataPackage() }
        }
    }

    private fun populateDragDataPackage(
        target: DataPackage,
        pending: WinUIPendingSourceTransfer,
    ): Boolean =
        when (val nativeData = pending.transferData.nativeTransferData) {
            is String -> runCatching { target.setText(nativeData) }.isSuccess
            is DataPackage -> forwardDataPackage(nativeData, target, pending)
            is Map<*, *> -> {
                var wroteData = false
                nativeData.forEach { (format, value) ->
                    if (format is String && value != null) {
                        if (runCatching { target.setData(format, value) }.isSuccess) {
                            wroteData = true
                        }
                    }
                }
                wroteData
            }
            else -> false
        }

    private fun forwardDataPackage(
        source: DataPackage,
        target: DataPackage,
        pending: WinUIPendingSourceTransfer,
    ): Boolean {
        val sourceView = runCatching { source.getView() }.getOrNull() ?: return false
        val formats = runCatching { sourceView.availableFormats.toList() }.getOrDefault(emptyList())
        if (formats.isEmpty()) return false
        runCatching { target.requestedOperation = source.requestedOperation }
        var forwardedFormat = false
        formats.forEach { format ->
            val handler =
                DataProviderHandler { request ->
                    val deferral = runCatching { request.getDeferral() }.getOrNull()
                    val scope = eventCoroutineScope()
                    if (scope == null) {
                        runCatching { deferral?.complete() }
                    } else {
                        scope.launch {
                            try {
                                runCatching {
                                    val value = sourceView.getDataAsync(format).await()
                                    request.setData(value)
                                }
                            } finally {
                                runCatching { deferral?.complete() }
                            }
                        }
                    }
                }
            if (runCatching { target.setDataProvider(format, handler) }.isSuccess) {
                pending.dataProviders += handler
                forwardedFormat = true
            }
        }
        return forwardedFormat
    }

    private fun clearPendingSourceTransfer() {
        pendingSourceTransfer?.decoration?.let { runCatching { it.close() } }
        pendingSourceTransfer = null
    }

    private fun closeActiveDragOperation(cancel: Boolean = true) {
        if (cancel) {
            activeDragOperation?.let { runCatching { it.cancel() } }
        }
        activeDragOperation?.let { runCatching { it.close() } }
        activeDragOperation = null
    }

    internal fun updateSourcePointerPoint(pointerPoint: PointerPoint?) {
        if (!isDisposed) {
            latestPointerPoint = pointerPoint
        }
    }

    internal fun isSourceElementForTest(element: UIElement): Boolean = source === element

    @OptIn(ExperimentalComposeUiApi::class)
    private fun DragEventArgs.toComposeDragAndDropDispatch(): WinUIComposeDragAndDropDispatch? {
        val currentDataView = runCatching { dataView }.getOrNull() ?: return null
        val transfer = activeTransferFor(currentDataView)
        val position = runCatching { getPosition(root) }.getOrNull()
        val positionInRoot =
            if (position != null) {
                winUIPositionToComposeOffset(position.x, position.y, owner.density)
            } else {
                Offset.Zero
            }
        val event = DragAndDropEvent(nativeEvent = this, positionInRootImpl = positionInRoot)
        return WinUIComposeDragAndDropDispatch(
            event = event,
            data =
                PlatformDragAndDropData(
                    clipEntry = transfer.clipEntry,
                    clipMetadata = transfer.clipEntry.clipMetadata,
                    positionInRoot = positionInRoot,
                ),
            transfer = transfer,
        )
    }

    private fun activeTransferFor(dataView: DataPackageView): WinUIActiveDragTransfer {
        activeTransfer
            ?.takeIf { it.dataView == dataView }
            ?.let {
                return it
            }
        clearActiveTransfer()
        return WinUIActiveDragTransfer(
                dataView = dataView,
                metadata = WinUIDataPackageViewClipMetadata(dataView),
            )
            .also { transfer -> activeTransfer = transfer }
    }

    private fun startPlainTextRead(transfer: WinUIActiveDragTransfer) {
        if (!transfer.metadata.containsPlainText || transfer.plainTextRead != null) return
        val scope = eventCoroutineScope() ?: return
        transfer.plainTextRead = scope.async { transfer.metadata.loadPlainText() }
    }

    private fun eventCoroutineScope(): CoroutineScope? {
        if (isDisposed) return null
        eventScope?.let {
            return it
        }
        val context = coroutineContextProvider()
        if (context == EmptyCoroutineContext || context[ContinuationInterceptor] == null)
            return null
        return CoroutineScope(context.minusKey(Job) + lifecycleJob).also { eventScope = it }
    }

    private fun finishSynchronousDrop(
        args: DragEventArgs,
        dispatch: WinUIComposeDragAndDropDispatch,
    ): Boolean = createDropTermination(args, dispatch).finish() ?: false

    private fun createDropTermination(
        args: DragEventArgs,
        dispatch: WinUIComposeDragAndDropDispatch,
        completeDeferral: () -> Unit = {},
        onTerminated: () -> Unit = {},
    ): WinUIDropTermination =
        WinUIDropTermination(
            finishDrop = { dispatchDropAndTerminate(dispatch) },
            abortDrop = { abortDrop(dispatch) },
            publishResult = { handled -> publishDropResult(args, handled) },
            completeDeferral = completeDeferral,
            onTerminated = onTerminated,
        )

    private fun dispatchDropAndTerminate(dispatch: WinUIComposeDragAndDropDispatch): Boolean {
        var handled = false
        runWinUIDragAndDropCleanup(
            {
                handled =
                    dispatch.withPlatformData { dragAndDropManager.onDrop(dispatch.event) }
            },
            { dispatch.withPlatformData { dragSession.terminate(dispatch.event) } },
        )
        return handled
    }

    private fun publishDropResult(args: DragEventArgs, handled: Boolean) {
        runWinUIDragAndDropCleanup(
            { args.acceptedOperation = winUIDragAcceptedOperation(handled) },
            { args.handled = handled },
        )
    }

    private fun abortDrop(dispatch: WinUIComposeDragAndDropDispatch) {
        dispatch.withPlatformData { dragSession.terminate(dispatch.event) }
    }

    private fun cancelPendingDrop() {
        val pending = pendingDrop ?: return
        pending.termination.abort()
        pending.job?.cancel()
    }

    private fun clearActiveTransfer() {
        activeTransfer?.plainTextRead?.cancel()
        activeTransfer = null
    }

    private inline fun <T> WinUIComposeDragAndDropDispatch.withPlatformData(block: () -> T): T {
        PlatformDragAndDropEventData.set(event, data)
        return try {
            block()
        } finally {
            PlatformDragAndDropEventData.clear(event)
        }
    }
}

internal fun runWinUIDragSourceCleanup(
    closeActiveDragOperation: () -> Unit,
    clearPendingSourceTransfer: () -> Unit,
    clearPointerPoint: () -> Unit,
) {
    runWinUIDragAndDropCleanup(
        closeActiveDragOperation,
        clearPendingSourceTransfer,
        clearPointerPoint,
    )
}

internal enum class WinUIDragStartingDecision {
    Ignore,
    Cancel,
    Start,
}

internal fun winUIDragStartingDecision(
    hasPendingSourceTransfer: Boolean,
    hasDataPackage: Boolean,
    populatedDataPackage: Boolean,
): WinUIDragStartingDecision =
    when {
        !hasPendingSourceTransfer -> WinUIDragStartingDecision.Ignore
        !hasDataPackage || !populatedDataPackage -> WinUIDragStartingDecision.Cancel
        else -> WinUIDragStartingDecision.Start
    }

internal fun shouldHandleWinUIDropCompleted(
    hasPendingSourceTransfer: Boolean,
    hasActiveDragOperation: Boolean,
): Boolean = hasPendingSourceTransfer || hasActiveDragOperation

private class WinUIActiveDragTransfer(
    val dataView: DataPackageView,
    val metadata: WinUIDataPackageViewClipMetadata,
) {
    val clipEntry: ClipEntry = ClipEntry(metadata)
    var plainTextRead: Deferred<String?>? = null
}

private data class WinUIComposeDragAndDropDispatch(
    val event: DragAndDropEvent,
    val data: PlatformDragAndDropData,
    val transfer: WinUIActiveDragTransfer,
)

private class WinUIPendingDrop(val termination: WinUIDropTermination) {
    var job: Job? = null
}

private class WinUIPendingSourceTransfer(
    val transferData: DragAndDropTransferData,
    val decoration: SoftwareBitmap?,
) {
    val dataProviders = mutableListOf<DataProviderHandler>()
}

private data class WinUIDragAndDropEventRegistration(
    val event: WinRTEvent<DragEventHandler>,
    val token: EventRegistrationToken,
    val handler: DragEventHandler,
)

internal data class WinUIDragDecoration(
    val width: Int,
    val height: Int,
    val bgraPixels: ByteArray,
)

internal fun renderWinUIDragDecoration(
    decorationSize: Size,
    density: androidx.compose.ui.unit.Density,
    drawDragDecoration: DrawScope.() -> Unit,
): WinUIDragDecoration {
    val width = ceil(decorationSize.width.toDouble()).toInt().coerceAtLeast(1)
    val height = ceil(decorationSize.height.toDouble()).toInt().coerceAtLeast(1)
    val imageBitmap = ImageBitmap(width, height)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(imageBitmap),
        size = Size(width.toFloat(), height.toFloat()),
        block = drawDragDecoration,
    )
    val argbPixels = IntArray(width * height)
    imageBitmap.readPixels(argbPixels)
    val bgraPixels = ByteArray(argbPixels.size * 4)
    argbPixels.forEachIndexed { index, argb ->
        val offset = index * 4
        val alpha = argb ushr 24 and 0xff
        bgraPixels[offset] = premultiplyWinUIColorChannel(argb and 0xff, alpha)
        bgraPixels[offset + 1] = premultiplyWinUIColorChannel(argb ushr 8 and 0xff, alpha)
        bgraPixels[offset + 2] = premultiplyWinUIColorChannel(argb ushr 16 and 0xff, alpha)
        bgraPixels[offset + 3] = alpha.toByte()
    }
    return WinUIDragDecoration(width, height, bgraPixels)
}

private fun premultiplyWinUIColorChannel(channel: Int, alpha: Int): Byte =
    ((channel * alpha + 127) / 255).toByte()

@OptIn(ExperimentalUnsignedTypes::class)
private fun WinUIDragDecoration.toSoftwareBitmap(): SoftwareBitmap {
    val writer = DataWriter()
    return try {
        writer.writeBytes(Array(bgraPixels.size) { index -> bgraPixels[index].toUByte() })
        SoftwareBitmap.createCopyFromBuffer(
            writer.detachBuffer(),
            BitmapPixelFormat.Bgra8,
            width,
            height,
            BitmapAlphaMode.Premultiplied,
        )
    } finally {
        runCatching { writer.close() }
    }
}
