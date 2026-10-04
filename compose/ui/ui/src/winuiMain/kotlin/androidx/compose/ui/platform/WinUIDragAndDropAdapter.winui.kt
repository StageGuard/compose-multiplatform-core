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
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.WinUIDragAndDropManager
import androidx.compose.ui.draganddrop.WinUIDragAndDropStarter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.node.WinUIOwner
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRTAsyncStatus
import io.github.composefluent.winrt.runtime.WinRTDelegateHandle
import io.github.composefluent.winrt.runtime.WinRTEvent
import kotlin.math.roundToInt
import microsoft.ui.dispatching.DispatcherQueue
import microsoft.ui.dispatching.DispatcherQueueHandler
import microsoft.ui.input.PointerPoint
import microsoft.ui.xaml.DragEventArgs
import microsoft.ui.xaml.DragEventHandler
import microsoft.ui.xaml.DragStartingEventArgs
import microsoft.ui.xaml.DropCompletedEventArgs
import microsoft.ui.xaml.UIElement
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import windows.applicationmodel.datatransfer.DataPackage
import windows.applicationmodel.datatransfer.DataPackageOperation
import windows.applicationmodel.datatransfer.dragdrop.DragDropModifiers
import windows.foundation.EventRegistrationToken
import windows.foundation.Point
import windows.foundation.TypedEventHandler
import windows.graphics.imaging.SoftwareBitmap

/**
 * Connects the WinUI drag and drop of [root] to Compose: drags that enter the root go to the
 * Compose drop targets, and drags that Compose sources start become WinUI drags of the root.
 */
internal class WinUIDragAndDropAdapter(
    private val root: UIElement,
    private val owner: WinUIOwner,
) : WinUIDragAndDropStarter {
    private val dragAndDropManager: WinUIDragAndDropManager = owner.winUIDragAndDropManager
    private var isDisposed = false
    private var isDragSessionActive = false
    private var lastAction: DragAndDropTransferAction? = null

    // WinUI starts a drag from a pointer that is down, so the adapter keeps the last one.
    private var dragPointerId: Long? = null
    private var dragPointerPoint: PointerPoint? = null

    // The drag that a Compose source started, until WinUI reports that it is over.
    private var outgoingTransfer: OutgoingTransfer? = null
    private val dispatcherQueue: DispatcherQueue? =
        runCatching { DispatcherQueue.getForCurrentThread() }.getOrNull()

    private val registrations = listOf(
        register(root.dragEnter, ::handleDragEnter),
        register(root.dragOver, ::handleDragOver),
        register(root.dragLeave, ::handleDragLeave),
        register(root.drop, ::handleDrop),
    )
    private val dragStartingHandler: TypedEventHandler<UIElement, DragStartingEventArgs> =
        { _, args -> if (!isDisposed) handleDragStarting(args) }
    private val dragStartingToken: EventRegistrationToken =
        root.dragStarting.add(dragStartingHandler)
    private val dropCompletedHandler: TypedEventHandler<UIElement, DropCompletedEventArgs> =
        { _, args -> handleDropCompleted(args) }
    private val dropCompletedToken: EventRegistrationToken =
        root.dropCompleted.add(dropCompletedHandler)

    init {
        root.allowDrop = true
        dragAndDropManager.starter = this
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        registrations.forEach { registration ->
            runCatching { registration.event.remove(registration.token) }
        }
        runCatching { root.dragStarting.remove(dragStartingToken) }
        runCatching { root.dropCompleted.remove(dropCompletedToken) }
        if (isDragSessionActive) {
            dragAndDropManager.onDragEnded(DragAndDropEvent())
            isDragSessionActive = false
        }
        dragAndDropManager.starter = null
        dragPointerPoint = null
        outgoingTransfer = null
        root.allowDrop = false
    }

    /**
     * Remembers the pointer that a drag would start from. The host calls this before Compose
     * handles the event, because a Compose source starts its drag while it handles one.
     */
    fun onPointerEvent(event: WinUIPointerEvent) {
        if (event.down && event.pointerPoint != null) {
            dragPointerId = event.pointerId
            dragPointerPoint = event.pointerPoint
        } else if (event.pointerId == dragPointerId) {
            dragPointerId = null
            dragPointerPoint = null
        }
    }

    override fun startDragAndDropTransfer(
        transferData: DragAndDropTransferData,
        decorationSize: Size,
        drawDragDecoration: DrawScope.() -> Unit,
    ): Boolean {
        if (isDisposed) return false
        val pointerPoint = dragPointerPoint ?: return false
        // WinUI runs one drag at a time, so a drag that never reported its end is over.
        outgoingTransfer?.let { finishTransfer(it, DataPackageOperation.None) }
        val transfer = OutgoingTransfer(transferData, decorationSize, drawDragDecoration)
        outgoingTransfer = transfer
        val operation = runCatching { root.startDragAsync(pointerPoint) }.getOrElse {
            outgoingTransfer = null
            return false
        }
        transfer.operation = operation
        transfer.completedHandle = runCatching {
            operation.whenCompleted { completedOperation, status ->
                val result = if (status == WinRTAsyncStatus.Completed) {
                    runCatching { completedOperation.getResults() }
                        .getOrDefault(DataPackageOperation.None)
                } else {
                    DataPackageOperation.None
                }
                // The drop completed event normally ends the drag first; this covers drags
                // that end without it.
                dispatcherQueue?.tryEnqueue(DispatcherQueueHandler { finishTransfer(transfer, result) })
            }
        }.getOrNull()
        return true
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDragStarting(args: DragStartingEventArgs) {
        val transfer = outgoingTransfer ?: return
        val transferData = transfer.transferData
        val dataPackage = args.data ?: return
        when (val data = transferData.nativeTransferData) {
            is String -> dataPackage.setText(data)
            is Function1<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (data as (DataPackage) -> Unit).invoke(dataPackage)
            }
        }
        args.allowedOperations = transferData.supportedActions.toDataPackageOperation()
        val decoration = runCatching { renderDragDecoration(transfer) }.getOrNull()
        if (decoration != null) {
            // The anchor is in pixels of the bitmap, like the offset.
            val anchor = transferData.dragDecorationOffset
            runCatching {
                args.dragUI?.setContentFromSoftwareBitmap(decoration, Point(anchor.x, anchor.y))
            }
        }
    }

    private fun handleDropCompleted(args: DropCompletedEventArgs) {
        val transfer = outgoingTransfer ?: return
        finishTransfer(transfer, args.dropResult)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun finishTransfer(transfer: OutgoingTransfer, result: DataPackageOperation) {
        if (outgoingTransfer !== transfer) return
        outgoingTransfer = null
        transfer.completedHandle = null
        transfer.operation = null
        // The drag took the pointer away from Compose, which never saw it released.
        if (!isDisposed) {
            owner.cancelPointerInput()
        }
        transfer.transferData.onTransferCompleted?.invoke(result.toTransferAction())
    }

    private fun renderDragDecoration(transfer: OutgoingTransfer): SoftwareBitmap? {
        val size = transfer.decorationSize
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        if (width <= 0 || height <= 0) return null
        val imageBitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(
            density = owner.density,
            layoutDirection = owner.layoutDirection,
            canvas = Canvas(imageBitmap),
            size = size,
            block = transfer.drawDragDecoration,
        )
        val pixels = imageBitmap.asSkiaBitmap().readPixels(
            ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL),
            dstRowBytes = width * 4,
        ) ?: return null
        val bitmap = createSoftwareBitmap(width, height, pixels) ?: return null
        // Show the bitmap at its size in physical pixels, as Compose drew it.
        val dpi = 96.0 * owner.density.density
        runCatching {
            bitmap.dpiX = dpi
            bitmap.dpiY = dpi
        }
        return bitmap
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDragEnter(args: DragEventArgs): Boolean {
        val event = args.toComposeDragAndDropEvent()
        val accepted = ensureDragSessionStarted(event)
        lastAction = event.action
        dragAndDropManager.onDragEntered(event)
        args.acceptedOperation = if (accepted) {
            event.action.toDataPackageOperation()
        } else {
            DataPackageOperation.None
        }
        return accepted
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDragOver(args: DragEventArgs): Boolean {
        val event = args.toComposeDragAndDropEvent()
        val accepted = ensureDragSessionStarted(event)
        if (accepted && event.action != lastAction) {
            lastAction = event.action
            dragAndDropManager.onDragChanged(event)
        }
        dragAndDropManager.onDragMoved(event)
        // Like AWT, accept the drag only over a target that takes it, so that WinUI shows that
        // a drop elsewhere does nothing.
        args.acceptedOperation = if (accepted && dragAndDropManager.hasEligibleDropTarget) {
            event.action.toDataPackageOperation()
        } else {
            DataPackageOperation.None
        }
        return accepted
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDragLeave(args: DragEventArgs): Boolean {
        val event = args.toComposeDragAndDropEvent()
        if (isDragSessionActive) {
            dragAndDropManager.onDragExited(event)
            dragAndDropManager.onDragEnded(event)
            isDragSessionActive = false
        }
        lastAction = null
        return false
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDrop(args: DragEventArgs): Boolean {
        val event = args.toComposeDragAndDropEvent()
        ensureDragSessionStarted(event)
        val handled = dragAndDropManager.onDrop(event)
        // The source learns the action from the accepted operation.
        args.acceptedOperation = if (handled) {
            event.action.toDataPackageOperation()
        } else {
            DataPackageOperation.None
        }
        dragAndDropManager.onDragEnded(event)
        isDragSessionActive = false
        lastAction = null
        return handled
    }

    private fun ensureDragSessionStarted(event: DragAndDropEvent): Boolean {
        if (isDragSessionActive) return true
        isDragSessionActive = dragAndDropManager.onDragStarted(event)
        return isDragSessionActive
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

    @OptIn(ExperimentalComposeUiApi::class)
    private fun DragEventArgs.toComposeDragAndDropEvent(): DragAndDropEvent {
        val position = getPosition(root)
        return DragAndDropEvent(
            nativeEvent = this,
            positionInRootImpl = winUIPositionToComposeOffset(position.x, position.y, owner.density),
            action = winUIDragAction(
                allowedOperations = allowedOperations,
                isControlPressed = modifiers.hasFlag(DragDropModifiers.Control),
                isShiftPressed = modifiers.hasFlag(DragDropModifiers.Shift),
            ),
        )
    }
}

private class OutgoingTransfer(
    val transferData: DragAndDropTransferData,
    val decorationSize: Size,
    val drawDragDecoration: DrawScope.() -> Unit,
) {
    // Kept so that the operation and its completion handler live as long as the drag.
    var operation: WinRTAsyncOperationReference<DataPackageOperation>? = null
    var completedHandle: WinRTDelegateHandle? = null
}

/**
 * Returns the action of a drag from the operations that its source allows and the modifier keys,
 * with the keys that AWT uses: Ctrl copies, Shift moves and Ctrl+Shift links. Without keys, the
 * drag copies when the source allows it, which is what Windows apps do for data other than files.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun winUIDragAction(
    allowedOperations: DataPackageOperation,
    isControlPressed: Boolean,
    isShiftPressed: Boolean,
): DragAndDropTransferAction? {
    val requested = when {
        isControlPressed && isShiftPressed -> DataPackageOperation.Link
        isControlPressed -> DataPackageOperation.Copy
        isShiftPressed -> DataPackageOperation.Move
        else -> listOf(
            DataPackageOperation.Copy,
            DataPackageOperation.Move,
            DataPackageOperation.Link,
        ).firstOrNull { allowedOperations.hasFlag(it) } ?: return null
    }
    return if (allowedOperations.hasFlag(requested)) requested.toTransferAction() else null
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun DataPackageOperation.toTransferAction(): DragAndDropTransferAction? = when {
    hasFlag(DataPackageOperation.Copy) -> DragAndDropTransferAction.Copy
    hasFlag(DataPackageOperation.Move) -> DragAndDropTransferAction.Move
    hasFlag(DataPackageOperation.Link) -> DragAndDropTransferAction.Link
    else -> null
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun DragAndDropTransferAction?.toDataPackageOperation(): DataPackageOperation =
    when (this) {
        DragAndDropTransferAction.Copy -> DataPackageOperation.Copy
        DragAndDropTransferAction.Move -> DataPackageOperation.Move
        DragAndDropTransferAction.Link -> DataPackageOperation.Link
        else -> DataPackageOperation.None
    }

@OptIn(ExperimentalComposeUiApi::class)
internal fun Iterable<DragAndDropTransferAction>.toDataPackageOperation(): DataPackageOperation =
    fold(DataPackageOperation.None) { operations, action ->
        operations or action.toDataPackageOperation()
    }

/**
 * Returns a bitmap with the given premultiplied BGRA [pixels], or `null` if it can't be created.
 */
internal expect fun createSoftwareBitmap(
    width: Int,
    height: Int,
    pixels: ByteArray,
): SoftwareBitmap?

private data class WinUIDragAndDropEventRegistration(
    val event: WinRTEvent<DragEventHandler>,
    val token: EventRegistrationToken,
    val handler: DragEventHandler,
)
