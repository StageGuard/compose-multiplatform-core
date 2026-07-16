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

import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ReusableGraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIOwnerLayerTest {
    @Test
    fun updateDisplayListClearsDirtyStateUntilInvalidated() {
        var parentInvalidations = 0
        val layer = WinUIOwnerLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = { parentInvalidations++ },
        )

        assertTrue(layer.stateForTest().isDirty)
        assertEquals(0, layer.stateForTest().displayListUpdateCount)

        layer.updateDisplayList()

        assertFalse(layer.stateForTest().isDirty)
        assertEquals(1, layer.stateForTest().displayListUpdateCount)
        assertEquals(0, parentInvalidations)

        layer.updateDisplayList()

        assertEquals(1, layer.stateForTest().displayListUpdateCount)

        layer.invalidate()

        assertTrue(layer.stateForTest().isDirty)
        assertEquals(1, parentInvalidations)

        layer.updateDisplayList()

        assertFalse(layer.stateForTest().isDirty)
        assertEquals(2, layer.stateForTest().displayListUpdateCount)
    }

    @Test
    fun resizeAndPropertyUpdatesDirtyDisplayListButMoveOnlyInvalidatesParent() {
        var parentInvalidations = 0
        val frameRateVotes = mutableListOf<Float>()
        val layer = WinUIOwnerLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = { parentInvalidations++ },
            voteFrameRate = { frameRateVotes += it },
        )
        layer.updateDisplayList()

        layer.resize(IntSize(20, 30))

        assertTrue(layer.stateForTest().isDirty)
        assertEquals(IntSize(20, 30), layer.stateForTest().size)
        assertEquals(1, parentInvalidations)
        assertEquals(listOf(FrameRateCategory.High.value), frameRateVotes)

        layer.updateDisplayList()
        layer.move(IntOffset(4, 5))

        assertFalse(layer.stateForTest().isDirty)
        assertEquals(IntOffset(4, 5), layer.stateForTest().position)
        assertEquals(2, parentInvalidations)
        assertEquals(
            listOf(FrameRateCategory.High.value, FrameRateCategory.High.value),
            frameRateVotes,
        )

        val scope = ReusableGraphicsLayerScope()
        scope.translationX = 12f
        layer.updateLayerProperties(scope)

        assertTrue(layer.stateForTest().isDirty)
        assertEquals(3, parentInvalidations)
        assertEquals(
            listOf(FrameRateCategory.High.value, FrameRateCategory.High.value, 0f),
            frameRateVotes,
        )
    }

    @Test
    fun updateDisplayListVotesNonZeroFrameRate() {
        val frameRateVotes = mutableListOf<Float>()
        val layer = WinUIOwnerLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = {},
            voteFrameRate = { frameRateVotes += it },
        )

        layer.frameRate = 24f
        layer.updateDisplayList()
        layer.updateDisplayList()

        assertEquals(listOf(24f, 24f), frameRateVotes)

        layer.frameRate = 0f
        layer.invalidate()
        layer.updateDisplayList()

        assertEquals(listOf(24f, 24f), frameRateVotes)
    }

    @Test
    fun resizeRecomputesCustomTransformOrigin() {
        val layer = WinUIOwnerLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = {},
        )
        layer.resize(IntSize(100, 200))
        layer.updateLayerProperties(
            ReusableGraphicsLayerScope().apply {
                scaleX = 2f
                scaleY = 2f
                transformOrigin = TransformOrigin(0.25f, 0.75f)
            }
        )

        val initialPivot = Offset(25f, 150f)
        assertEquals(initialPivot, layer.mapOffset(initialPivot, inverse = false))

        layer.resize(IntSize(200, 400))

        val resizedPivot = Offset(50f, 300f)
        assertEquals(resizedPivot, layer.mapOffset(resizedPivot, inverse = false))
    }

    @Test
    fun clippedRoundedOutlineRejectsCornerHit() {
        val layer = WinUIOwnerLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = {},
        )
        layer.resize(IntSize(100, 100))

        val scope = ReusableGraphicsLayerScope()
        scope.clip = true
        scope.outline = Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, 100f, 100f),
                cornerRadius = CornerRadius(20f),
            ),
        )
        layer.updateLayerProperties(scope)

        assertTrue(layer.isInLayer(androidx.compose.ui.geometry.Offset(50f, 50f)))
        assertFalse(layer.isInLayer(androidx.compose.ui.geometry.Offset(0f, 0f)))
    }

    @Test
    fun updateLayerPropertiesPreservesGraphicsLayerValues() {
        val layer = WinUIOwnerLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = {},
        )
        layer.resize(IntSize(100, 100))
        val outline = Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, 100f, 100f),
                cornerRadius = CornerRadius(12f),
            ),
        )
        val colorFilter = ColorFilter.tint(Color.Green)
        val renderEffect = BlurEffect(2f, 3f)
        val scope = ReusableGraphicsLayerScope().apply {
            alpha = 0.5f
            shadowElevation = 4f
            ambientShadowColor = Color.Red
            spotShadowColor = Color.Blue
            cameraDistance = 42f
            clip = true
            blendMode = BlendMode.Multiply
            compositingStrategy = CompositingStrategy.Offscreen
            this.colorFilter = colorFilter
            this.renderEffect = renderEffect
            outsets = LayerOutsets(2.dp, 3.dp, 4.dp, 5.dp)
            this.outline = outline
        }

        layer.updateLayerProperties(scope)

        val state = layer.stateForTest()
        assertEquals(0.5f, state.alpha)
        assertEquals(4f, state.shadowElevation)
        assertEquals(Color.Red, state.ambientShadowColor)
        assertEquals(Color.Blue, state.spotShadowColor)
        assertEquals(42f, state.cameraDistance)
        assertTrue(state.clip)
        assertEquals(BlendMode.Multiply, state.blendMode)
        assertEquals(
            androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen,
            state.compositingStrategy,
        )
        assertEquals(colorFilter, state.colorFilter)
        assertEquals(renderEffect, state.renderEffect)
        assertEquals(LayerOutsets(2.dp, 3.dp, 4.dp, 5.dp), state.outsets)
        assertEquals(outline, state.outline)
    }

    @Test
    fun reuseClearsGraphicsLayerOutsets() {
        val appliedOutsets = mutableListOf<List<Int>>()
        val layer = WinUIOwnerLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = {},
            applyOutsets = { _, left, top, right, bottom ->
                appliedOutsets += listOf(left, top, right, bottom)
            },
        )
        layer.updateLayerProperties(
            ReusableGraphicsLayerScope().apply {
                outsets = LayerOutsets(2.dp, 3.dp, 4.dp, 5.dp)
            }
        )
        layer.destroy()

        layer.reuseLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = {},
        )

        assertEquals(
            listOf(
                listOf(2, 3, 4, 5),
                listOf(0, 0, 0, 0),
            ),
            appliedOutsets,
        )
    }

    @Test
    fun destroySuppressesInvalidationAndReuseResetsLayerState() {
        var oldParentInvalidations = 0
        var newParentInvalidations = 0
        val layer = WinUIOwnerLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = { oldParentInvalidations++ },
        )
        layer.resize(IntSize(20, 30))
        layer.move(IntOffset(4, 5))
        layer.updateDisplayList()

        layer.destroy()

        assertTrue(layer.stateForTest().isDestroyed)
        assertFalse(layer.stateForTest().isDirty)

        layer.invalidate()

        assertFalse(layer.stateForTest().isDirty)
        assertEquals(2, oldParentInvalidations)

        layer.reuseLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = { newParentInvalidations++ },
        )

        val state = layer.stateForTest()
        assertFalse(state.isDestroyed)
        assertTrue(state.isDirty)
        assertEquals(0, state.displayListUpdateCount)
        assertEquals(IntSize.Zero, state.size)
        assertEquals(IntOffset.Zero, state.position)
        assertEquals(1, newParentInvalidations)
    }
}
