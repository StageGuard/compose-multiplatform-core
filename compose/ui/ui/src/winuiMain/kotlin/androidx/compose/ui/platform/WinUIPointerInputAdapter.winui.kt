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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.node.WinUIOwner
import io.github.composefluent.winrt.runtime.WinRTEvent
import microsoft.ui.input.PointerDeviceType
import microsoft.ui.input.PointerPoint
import microsoft.ui.input.PointerUpdateKind
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.input.Pointer
import microsoft.ui.xaml.input.PointerEventHandler
import microsoft.ui.xaml.input.PointerRoutedEventArgs
import windows.foundation.EventRegistrationToken

internal class WinUIPointerInputAdapter(
    private val root: UIElement,
    private val owner: WinUIOwner,
    private val onSourcePointerPointChanged: (PointerPoint?) -> Unit = {},
) {
    private var isDisposed = false
    private val pointerEventProcessor = WinUIPointerEventProcessor()
    private val capturedPointers = mutableMapOf<Long, Pointer>()
    private val registrations = listOf(
        register(PointerEventType.Press, root.pointerPressed),
        register(PointerEventType.Move, root.pointerMoved),
        register(PointerEventType.Release, root.pointerReleased),
        register(PointerEventType.Enter, root.pointerEntered),
        register(PointerEventType.Exit, root.pointerExited),
        register(PointerEventType.Scroll, root.pointerWheelChanged),
        registerCancel(root.pointerCanceled),
        registerCaptureLost(root.pointerCaptureLost),
    )

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        onSourcePointerPointChanged(null)
        releasePointerCaptures()
        registrations.forEach { registration ->
            runCatching { registration.event.remove(registration.token) }
        }
        owner.cancelPointerInput()
    }

    private fun register(
        eventType: PointerEventType,
        event: WinRTEvent<PointerEventHandler>,
    ): WinUIPointerEventRegistration {
        val handler: PointerEventHandler = { sender, args ->
            if (!isDisposed) {
                val pointerEvent = createPointerEvent(eventType, args)
                updatePointerCapture(pointerEvent, args)
                debugPointerInput {
                    "native event=$eventType sender=${sender?.debugClassName()} " +
                        "handledBefore=${args.handled} ${pointerEvent.debugString()}"
                }
                val handled = pointerEventProcessor.process(
                    event = pointerEvent,
                    onBeforeDispatch = ::updateDragSourcePointer,
                    sendPointerEvent = { eventType, position, uptimeMillis, pointerId, down, type,
                            buttons, keyboardModifiers, button, scrollDelta, isInBounds,
                            nativeEvent ->
                        owner.sendPointerEvent(
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
                    },
                ).also { handled ->
                    debugPointerInput {
                        "compose event=$eventType id=${pointerEvent.pointerId} " +
                            "pos=${pointerEvent.position} down=${pointerEvent.down} " +
                            "type=${pointerEvent.type} buttons=${pointerEvent.buttons} " +
                            "button=${pointerEvent.button} scroll=${pointerEvent.scrollDelta} " +
                            "inBounds=${pointerEvent.isInBounds} handled=$handled"
                    }
                }
                args.handled = handled
                debugPointerInput {
                    "native handledAfter event=$eventType handled=${args.handled}"
                }
            }
        }
        return WinUIPointerEventRegistration(event, event.add(handler), handler)
    }

    private fun registerCancel(
        event: WinRTEvent<PointerEventHandler>,
    ): WinUIPointerEventRegistration {
        val handler: PointerEventHandler = { sender, args ->
            if (!isDisposed) {
                debugPointerInput {
                    "native cancel sender=${sender?.debugClassName()} handledBefore=${args.handled}"
                }
                releasePointerCapture(args)
                onSourcePointerPointChanged(null)
                owner.cancelPointerInput()
            }
        }
        return WinUIPointerEventRegistration(event, event.add(handler), handler)
    }

    private fun registerCaptureLost(
        event: WinRTEvent<PointerEventHandler>,
    ): WinUIPointerEventRegistration {
        val handler: PointerEventHandler = { sender, args ->
            debugPointerInput {
                "native captureLost sender=${sender?.debugClassName()} handledBefore=${args.handled}"
            }
            onSourcePointerPointChanged(null)
            removeCapturedPointer(args)
        }
        return WinUIPointerEventRegistration(event, event.add(handler), handler)
    }

    private fun createPointerEvent(
        eventType: PointerEventType,
        args: PointerRoutedEventArgs,
    ): WinUIPointerEvent {
        val point = args.getCurrentPoint(root)
        val position = point.position
        val properties = checkNotNull(point.properties) {
            "WinUI pointer properties are not available."
        }
        val buttons = properties.toComposeButtons()
        return WinUIPointerEvent(
            eventType = eventType,
            position = winUIPositionToComposeOffset(position.x, position.y, owner.density),
            uptimeMillis = point.timestamp.toLong() / MicrosecondsPerMillisecond,
            pointerId = point.pointerId.toLong(),
            down = point.isComposePointerDown(eventType, buttons),
            type = point.toComposePointerType(),
            buttons = buttons,
            keyboardModifiers = args.toComposeKeyboardModifiers(),
            button = properties.pointerUpdateKind.toComposeButton(),
            scrollDelta = if (eventType == PointerEventType.Scroll) {
                properties.toComposeScrollDelta()
            } else {
                Offset.Zero
            },
            isInBounds = eventType != PointerEventType.Exit,
            nativeEvent = args,
            sourcePointerPoint = point,
        )
    }

    private fun updateDragSourcePointer(event: WinUIPointerEvent) {
        when (event.eventType) {
            PointerEventType.Press,
            PointerEventType.Move -> onSourcePointerPointChanged(event.sourcePointerPoint)
            PointerEventType.Release -> onSourcePointerPointChanged(null)
            PointerEventType.Exit -> if (!event.down) onSourcePointerPointChanged(null)
            else -> Unit
        }
    }

    private fun updatePointerCapture(
        event: WinUIPointerEvent,
        args: PointerRoutedEventArgs,
    ) {
        when (event.eventType) {
            PointerEventType.Press -> capturePointer(event.pointerId, args)
            PointerEventType.Release -> releasePointerCapture(args)
            PointerEventType.Exit -> if (!event.down) {
                releasePointerCapture(args)
            }
            else -> Unit
        }
    }

    private fun capturePointer(pointerId: Long, args: PointerRoutedEventArgs) {
        val pointer = args.pointer ?: return
        if (runCatching { root.capturePointer(pointer) }.getOrDefault(false)) {
            capturedPointers[pointerId] = pointer
        }
    }

    private fun releasePointerCapture(args: PointerRoutedEventArgs) {
        val pointer = args.pointer ?: return
        runCatching { root.releasePointerCapture(pointer) }
        removeCapturedPointer(args)
    }

    private fun removeCapturedPointer(args: PointerRoutedEventArgs) {
        val pointer = args.pointer ?: return
        capturedPointers.entries.removeAll { (_, capturedPointer) ->
            capturedPointer.nativeObject.sameIdentity(pointer.nativeObject)
        }
    }

    private fun releasePointerCaptures() {
        capturedPointers.values.forEach { pointer ->
            runCatching { root.releasePointerCapture(pointer) }
        }
        capturedPointers.clear()
    }
}

private val IsPointerInputDebugEnabled: Boolean by lazy {
    winUISystemBooleanProperty("compose.winui.pointerInput.debug")
}

private inline fun debugPointerInput(message: () -> String) {
    if (IsPointerInputDebugEnabled) {
        winUIDebugLog("pointer", message())
    }
}

private fun Any.debugClassName(): String = this::class.qualifiedName ?: this::class.simpleName ?: toString()

private fun WinUIPointerEvent.debugString(): String =
    "id=$pointerId pos=$position uptimeMs=$uptimeMillis down=$down type=$type " +
        "buttons=$buttons button=$button scroll=$scrollDelta inBounds=$isInBounds"

internal class WinUIPointerEventProcessor {
    fun process(
        event: WinUIPointerEvent,
        onBeforeDispatch: (WinUIPointerEvent) -> Unit = {},
        sendPointerEvent: (
            PointerEventType,
            Offset,
            Long,
            Long,
            Boolean,
            PointerType,
            PointerButtons,
            PointerKeyboardModifiers,
            PointerButton?,
            Offset,
            Boolean,
            Any?,
        ) -> Boolean,
    ): Boolean {
        // This adapter is attached to the dedicated Skiko render surface. Routed-event handled
        // state from that surface should not suppress Compose input; WinUIView interop is hosted
        // outside this surface and remains isolated by the native XAML tree.
        onBeforeDispatch(event)
        return sendPointerEvent(
            event.eventType,
            event.position,
            event.uptimeMillis,
            event.pointerId,
            event.down,
            event.type,
            event.buttons,
            event.keyboardModifiers,
            event.button,
            event.scrollDelta,
            event.isInBounds,
            event.nativeEvent,
        )
    }
}

internal data class WinUIPointerEvent(
    val eventType: PointerEventType,
    val position: Offset,
    val uptimeMillis: Long,
    val pointerId: Long,
    val down: Boolean,
    val type: PointerType,
    val buttons: PointerButtons,
    val keyboardModifiers: PointerKeyboardModifiers,
    val button: PointerButton?,
    val scrollDelta: Offset,
    val isInBounds: Boolean,
    val nativeEvent: Any?,
    val sourcePointerPoint: PointerPoint? = null,
)

internal fun winUIPositionToComposeOffset(
    x: Float,
    y: Float,
    density: Density = Density(1f),
): Offset {
    val scale = density.density.takeIf { it.isFinite() && it > 0f } ?: 1f
    return Offset(x * scale, y * scale)
}

private data class WinUIPointerEventRegistration(
    val event: WinRTEvent<PointerEventHandler>,
    val token: EventRegistrationToken,
    val handler: PointerEventHandler,
)

private const val MicrosecondsPerMillisecond = 1_000L

private fun PointerPoint.toComposePointerType(): PointerType =
    when (pointerDeviceType) {
        PointerDeviceType.Mouse -> PointerType.Mouse
        PointerDeviceType.Pen -> PointerType.Stylus
        PointerDeviceType.Touch,
        PointerDeviceType.Touchpad -> PointerType.Touch
        else -> PointerType.Mouse
    }

private fun PointerPoint.isComposePointerDown(
    eventType: PointerEventType,
    buttons: PointerButtons,
): Boolean =
    when (eventType) {
        PointerEventType.Press -> true
        PointerEventType.Release,
        PointerEventType.Exit,
        PointerEventType.Scroll -> false
        else -> isInContact || buttons.areAnyPressed
    }

private fun microsoft.ui.input.PointerPointProperties.toComposeButtons(): PointerButtons {
    return PointerButtons(
        isPrimaryPressed = isLeftButtonPressed,
        isSecondaryPressed = isRightButtonPressed,
        isTertiaryPressed = isMiddleButtonPressed,
        isBackPressed = isXButton1Pressed,
        isForwardPressed = isXButton2Pressed,
    )
}

private fun PointerUpdateKind.toComposeButton(): PointerButton? =
    when (this) {
        PointerUpdateKind.LeftButtonPressed,
        PointerUpdateKind.LeftButtonReleased -> PointerButton.Primary
        PointerUpdateKind.RightButtonPressed,
        PointerUpdateKind.RightButtonReleased -> PointerButton.Secondary
        PointerUpdateKind.MiddleButtonPressed,
        PointerUpdateKind.MiddleButtonReleased -> PointerButton.Tertiary
        PointerUpdateKind.XButton1Pressed,
        PointerUpdateKind.XButton1Released -> PointerButton.Back
        PointerUpdateKind.XButton2Pressed,
        PointerUpdateKind.XButton2Released -> PointerButton.Forward
        PointerUpdateKind.Other -> null
        else -> null
    }

private fun microsoft.ui.input.PointerPointProperties.toComposeScrollDelta(): Offset {
    val wheelTicks = mouseWheelDelta.toFloat() / MouseWheelDeltaPerTick
    return if (isHorizontalMouseWheel) {
        Offset(wheelTicks, 0f)
    } else {
        Offset(0f, -wheelTicks)
    }
}

private const val MouseWheelDeltaPerTick = 120f

private fun PointerRoutedEventArgs.toComposeKeyboardModifiers(): PointerKeyboardModifiers =
    runCatching { winUIPointerKeyboardModifiersFromWinUI(keyModifiers) }
        .getOrDefault(PointerKeyboardModifiers())
