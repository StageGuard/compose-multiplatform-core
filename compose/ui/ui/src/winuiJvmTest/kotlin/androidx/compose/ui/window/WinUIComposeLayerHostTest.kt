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

package androidx.compose.ui.window

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals

class WinUIComposeLayerHostTest {
    private val root = LayoutNode().also { it.measurePolicy = RootMeasurePolicy }
    private val host = WinUIComposeLayerHost(
        root = root,
        focusOwner = { error("no focusable layer in these tests") },
    )
    private val events = mutableListOf<String>()

    private fun layer(
        name: String,
        bounds: IntRect,
        consumePointerInputOutside: Boolean,
    ): WinUIComposeLayer =
        host.createLayer(
            focusable = false,
            consumePointerInputOutside = consumePointerInputOutside,
        ).also { layer ->
            layer.boundsInWindow = bounds
            layer.onOutsidePointerEvent = { eventType, _ -> events += "$name $eventType" }
            host.attach(layer)
        }

    private fun press(position: Offset) =
        host.onPointerEvent(PointerEventType.Press, position, PointerButton.Primary, true)

    private fun release(position: Offset) =
        host.onPointerEvent(PointerEventType.Release, position, PointerButton.Primary, false)

    @Test
    fun layersAreAddedAfterTheContentAndRemovedAgain() {
        root.insertAt(0, LayoutNode())
        val layer = layer("popup", IntRect(0, 0, 10, 10), consumePointerInputOutside = false)

        assertEquals(listOf(root.foldedChildren[0], layer.node), root.foldedChildren)

        host.detach(layer)

        assertEquals(1, root.foldedChildren.size)
    }

    @Test
    fun pressOutsideGoesToTheLayersAboveTheFirstOneThatConsumesInputOutside() {
        layer("dialog", IntRect(100, 100, 200, 200), consumePointerInputOutside = true)
        layer("lower popup", IntRect(300, 300, 400, 400), consumePointerInputOutside = true)
        layer("popup", IntRect(500, 500, 600, 600), consumePointerInputOutside = false)

        press(Offset(10f, 10f))

        assertEquals(listOf("popup Press", "lower popup Press"), events)
    }

    @Test
    fun pressInsideALayerIsNotOutsideOfTheLayersBelow() {
        layer("dialog", IntRect(100, 100, 200, 200), consumePointerInputOutside = true)
        layer("popup", IntRect(500, 500, 600, 600), consumePointerInputOutside = false)

        press(Offset(550f, 550f))
        release(Offset(550f, 550f))

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun releaseOfAPressKeptFromTheContentGoesToTheLayerThatKeptIt() {
        // A dialog dismisses on the release outside of it, but only for a press outside of it.
        layer("dialog", IntRect(100, 100, 200, 200), consumePointerInputOutside = true)

        press(Offset(10f, 10f))
        release(Offset(10f, 10f))
        press(Offset(150f, 150f))
        release(Offset(10f, 10f))

        assertEquals(listOf("dialog Press", "dialog Release"), events)
    }

    @Test
    fun pressesDuringAGestureAreNotOutsidePresses() {
        layer("popup", IntRect(100, 100, 200, 200), consumePointerInputOutside = false)

        press(Offset(150f, 150f))
        host.onPointerEvent(PointerEventType.Press, Offset(10f, 10f), PointerButton.Secondary, true)

        assertEquals(emptyList<String>(), events)
    }
}
