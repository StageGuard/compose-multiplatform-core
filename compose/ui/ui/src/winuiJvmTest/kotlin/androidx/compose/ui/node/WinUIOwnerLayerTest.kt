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

import androidx.compose.ui.WinUISkikoTestBase
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ReusableGraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.WinUIGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The owned layers of WinUI are the GraphicsLayer-backed layers of the Skiko targets.
 */
class WinUIOwnerLayerTest : WinUISkikoTestBase() {
    private val manager = TestLayerManager()
    private var records = 0

    private fun createLayer(): GraphicsLayerOwnerLayer =
        GraphicsLayerOwnerLayer(
            graphicsLayer = WinUIGraphicsContext.createGraphicsLayer(),
            context = WinUIGraphicsContext,
            layerManager = manager,
            drawBlock = { _, _ -> records++ },
            invalidateParentLayer = {},
        )

    @Test
    fun updateDisplayListRecordsOnceUntilInvalidated() {
        val layer = createLayer()
        layer.resize(IntSize(20, 30))

        layer.updateDisplayList()
        layer.updateDisplayList()

        assertEquals(1, records)

        // A drawn layer that is invalidated is recorded again before the next frame.
        layer.invalidate()

        assertEquals(listOf<OwnedLayer>(layer), manager.dirtyLayers)

        layer.updateDisplayList()

        assertEquals(2, records)
        assertEquals(emptyList<OwnedLayer>(), manager.dirtyLayers)
    }

    @Test
    fun layerPropertiesReachTheGraphicsLayer() {
        val layer = createLayer()
        layer.resize(IntSize(100, 100))

        layer.updateLayerProperties(
            ReusableGraphicsLayerScope().apply {
                alpha = 0.5f
                shadowElevation = 8f
                shape = CircleShape
                clip = true
                size = Size(100f, 100f)
                updateOutline()
            }
        )

        assertEquals(0.5f, layer.graphicsLayer.alpha)
        assertEquals(8f, layer.graphicsLayer.shadowElevation)
        assertTrue(layer.graphicsLayer.clip)
        // Hit testing follows the clip shape, not only its bounds.
        assertTrue(layer.isInLayer(Offset(50f, 50f)))
        assertFalse(layer.isInLayer(Offset(2f, 2f)))
    }

    @Test
    fun transformIsAppliedAroundTheLayerCentre() {
        val layer = createLayer()
        layer.resize(IntSize(100, 40))
        layer.move(IntOffset(300, 200))

        layer.updateLayerProperties(
            ReusableGraphicsLayerScope().apply { rotationZ = 180f }
        )

        // The position in the parent is not part of the layer matrix.
        val mapped = layer.mapOffset(Offset(0f, 0f), inverse = false)
        assertEquals(100f, mapped.x, 0.001f)
        assertEquals(40f, mapped.y, 0.001f)
        val inverse = layer.mapOffset(mapped, inverse = true)
        assertEquals(0f, inverse.x, 0.001f)
        assertEquals(0f, inverse.y, 0.001f)
    }

    @Test
    fun destroyReleasesTheGraphicsLayer() {
        val layer = createLayer()
        val graphicsLayer = layer.graphicsLayer
        layer.resize(IntSize(20, 30))
        layer.updateDisplayList()

        layer.destroy()

        assertTrue(graphicsLayer.isReleased)
        assertEquals(listOf<OwnedLayer>(layer), manager.recycledLayers)
    }

    private object CircleShape : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline = Outline.Rounded(RoundRect(size.toRect(), CornerRadius(size.minDimension / 2)))
    }

    private class TestLayerManager : OwnedLayerManager {
        val dirtyLayers = mutableListOf<OwnedLayer>()
        val recycledLayers = mutableListOf<OwnedLayer>()

        override fun notifyLayerIsDirty(layer: OwnedLayer, isDirty: Boolean) {
            if (isDirty) dirtyLayers += layer else dirtyLayers -= layer
        }

        override fun recycle(layer: OwnedLayer): Boolean {
            recycledLayers += layer
            return false
        }
    }
}
