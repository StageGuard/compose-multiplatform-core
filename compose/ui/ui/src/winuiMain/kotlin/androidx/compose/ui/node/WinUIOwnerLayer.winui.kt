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
import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Fields
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ReusableGraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.WinUIGraphicsContext
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.isIdentity
import androidx.compose.ui.graphics.layer.CompositingStrategy as LayerCompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.setOutline
import androidx.compose.ui.graphics.prepareTransformationMatrix
import androidx.compose.ui.platform.invertTo
import androidx.compose.ui.platform.isInOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/** A WinUI-owned Compose layer backed by the regular Skia GraphicsLayer implementation. */
internal class WinUIOwnerLayer(
    private var graphicsLayer: GraphicsLayer,
    private val graphicsContext: GraphicsContext?,
    private var drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
    private var invalidateParentLayer: () -> Unit,
    private var voteFrameRate: (Float) -> Unit = {},
) : OwnedLayer {
    internal constructor(
        drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
        invalidateParentLayer: () -> Unit,
        voteFrameRate: (Float) -> Unit = {},
    ) : this(
        graphicsLayer = WinUIGraphicsContext.createGraphicsLayer(),
        graphicsContext = WinUIGraphicsContext,
        drawBlock = drawBlock,
        invalidateParentLayer = invalidateParentLayer,
        voteFrameRate = voteFrameRate,
    )

    private val matrix = Matrix()
    private val inverseMatrix = Matrix()
    private var isInverseMatrixDirty = true
    private var isInverseMatrixValid = true
    private var isIdentity = true
    private var isDestroyed = false
    private var position = IntOffset.Zero
    private var size = IntSize.Zero
    private var density = Density(1f)
    private var layoutDirection = LayoutDirection.Ltr
    private var transformOrigin = TransformOrigin.Center
    private var layerOutsets = LayerOutsets.Zero
    private var mutatedFields = 0
    private var isDirty = true
    private var displayListUpdateCount = 0
    private val drawScope = CanvasDrawScope()

    override fun updateLayerProperties(scope: ReusableGraphicsLayerScope) {
        val maybeChangedFields = scope.mutatedFields or mutatedFields
        density = scope.graphicsDensity
        layoutDirection = scope.layoutDirection

        if (maybeChangedFields and Fields.ScaleX != 0) {
            graphicsLayer.scaleX = scope.scaleX
        }
        if (maybeChangedFields and Fields.ScaleY != 0) {
            graphicsLayer.scaleY = scope.scaleY
        }
        if (maybeChangedFields and Fields.Alpha != 0) {
            graphicsLayer.alpha = scope.alpha
        }
        if (maybeChangedFields and Fields.TranslationX != 0) {
            graphicsLayer.translationX = scope.translationX
        }
        if (maybeChangedFields and Fields.TranslationY != 0) {
            graphicsLayer.translationY = scope.translationY
        }
        if (maybeChangedFields and Fields.ShadowElevation != 0) {
            graphicsLayer.shadowElevation = scope.shadowElevation
        }
        if (maybeChangedFields and Fields.AmbientShadowColor != 0) {
            graphicsLayer.ambientShadowColor = scope.ambientShadowColor
        }
        if (maybeChangedFields and Fields.SpotShadowColor != 0) {
            graphicsLayer.spotShadowColor = scope.spotShadowColor
        }
        if (maybeChangedFields and Fields.RotationX != 0) {
            graphicsLayer.rotationX = scope.rotationX
        }
        if (maybeChangedFields and Fields.RotationY != 0) {
            graphicsLayer.rotationY = scope.rotationY
        }
        if (maybeChangedFields and Fields.RotationZ != 0) {
            graphicsLayer.rotationZ = scope.rotationZ
        }
        if (maybeChangedFields and Fields.CameraDistance != 0) {
            graphicsLayer.cameraDistance = scope.cameraDistance
        }
        if (maybeChangedFields and Fields.TransformOrigin != 0) {
            transformOrigin = scope.transformOrigin
            graphicsLayer.pivotOffset = if (transformOrigin == TransformOrigin.Center) {
                Offset.Unspecified
            } else {
                Offset(
                    transformOrigin.pivotFractionX * size.width,
                    transformOrigin.pivotFractionY * size.height,
                )
            }
        }
        if (maybeChangedFields and Fields.Clip != 0) {
            graphicsLayer.clip = scope.clip
        }
        if (maybeChangedFields and Fields.RenderEffect != 0) {
            graphicsLayer.renderEffect = scope.renderEffect
        }
        if (maybeChangedFields and Fields.ColorFilter != 0) {
            graphicsLayer.colorFilter = scope.colorFilter
        }
        if (maybeChangedFields and Fields.BlendMode != 0) {
            graphicsLayer.blendMode = scope.blendMode
        }
        if (maybeChangedFields and Fields.CompositingStrategy != 0) {
            graphicsLayer.compositingStrategy = when (scope.compositingStrategy) {
                CompositingStrategy.Auto -> LayerCompositingStrategy.Auto
                CompositingStrategy.Offscreen -> LayerCompositingStrategy.Offscreen
                CompositingStrategy.ModulateAlpha -> LayerCompositingStrategy.ModulateAlpha
                else -> error("Unsupported compositing strategy")
            }
        }
        if (maybeChangedFields and Fields.Outsets != 0) {
            layerOutsets = scope.outsets
            with(density) {
                graphicsLayer.setOutsets(
                    left = layerOutsets.left.toPx().roundToInt(),
                    top = layerOutsets.top.toPx().roundToInt(),
                    right = layerOutsets.right.toPx().roundToInt(),
                    bottom = layerOutsets.bottom.toPx().roundToInt(),
                )
            }
        }

        val outline = scope.outline
        val outlineChanged = outline != null && graphicsLayer.outline != outline
        if (outlineChanged) {
            graphicsLayer.setOutline(outline!!)
        }

        if (maybeChangedFields and Fields.MatrixAffectingFields != 0) {
            updateMatrix()
        }
        mutatedFields = scope.mutatedFields
        if (maybeChangedFields != 0 || outlineChanged) {
            voteFrameRate(frameRate)
            invalidate()
        }
    }

    override fun isInLayer(position: Offset): Boolean {
        if (!graphicsLayer.clip) return true
        return isInOutline(graphicsLayer.outline, position.x, position.y)
    }

    override fun move(position: IntOffset) {
        if (position == this.position) return
        this.position = position
        graphicsLayer.topLeft = position
        voteFrameRate(FrameRateCategory.High.value)
        invalidateParentLayer()
    }

    override fun resize(size: IntSize) {
        if (size == this.size) return
        this.size = size
        voteFrameRate(FrameRateCategory.High.value)
        updateMatrix()
        invalidate()
    }

    override fun drawLayer(canvas: Canvas, parentLayer: GraphicsLayer?) {
        updateDisplayList()
        drawScope.drawContext.canvas = canvas
        drawScope.drawContext.graphicsLayer = parentLayer
        drawScope.drawLayer(graphicsLayer)
    }

    override fun updateDisplayList() {
        if (frameRate != 0f) {
            voteFrameRate(frameRate)
        }
        if (!isDirty) return
        displayListUpdateCount++
        graphicsLayer.record(density, layoutDirection, size, recordBlock)
        isDirty = false
    }

    private val recordBlock: DrawScope.() -> Unit = {
        drawIntoCanvas { canvas ->
            drawBlock(canvas, drawContext.graphicsLayer)
        }
    }

    override fun invalidate() {
        if (isDestroyed) return
        isDirty = true
        invalidateParentLayer()
    }

    override fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        isDirty = false
        frameRate = 0f
        isFrameRateFromParent = false
        if (graphicsContext != null) {
            graphicsContext.releaseGraphicsLayer(graphicsLayer)
        }
    }

    override fun mapOffset(point: Offset, inverse: Boolean): Offset {
        val targetMatrix = if (inverse) {
            getInverseMatrix() ?: return Offset.Infinite
        } else {
            getMatrix()
        }
        return if (isIdentity) point else targetMatrix.map(point)
    }

    override fun mapBounds(rect: MutableRect, inverse: Boolean) {
        val targetMatrix = if (inverse) getInverseMatrix() else getMatrix()
        if (!isIdentity) {
            if (targetMatrix == null) {
                rect.set(0f, 0f, 0f, 0f)
            } else {
                targetMatrix.map(rect)
            }
        }
    }

    override fun reuseLayer(
        drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
        invalidateParentLayer: () -> Unit,
    ) {
        if (graphicsContext != null && graphicsLayer.isReleased) {
            graphicsLayer = graphicsContext.createGraphicsLayer()
        }
        this.drawBlock = drawBlock
        this.invalidateParentLayer = invalidateParentLayer
        isDestroyed = false
        resetLayerState()
        resetGraphicsLayer()
        invalidate()
    }

    override fun transform(matrix: Matrix) {
        matrix.timesAssign(getMatrix())
    }

    override val underlyingMatrix: Matrix
        get() = getMatrix()

    override var frameRate: Float = 0f

    override var isFrameRateFromParent: Boolean = false

    override fun inverseTransform(matrix: Matrix) {
        getInverseMatrix()?.let { matrix.timesAssign(it) }
    }

    private fun getMatrix(): Matrix {
        updateMatrix()
        return matrix
    }

    private fun getInverseMatrix(): Matrix? {
        if (!isInverseMatrixDirty) {
            return if (isInverseMatrixValid) inverseMatrix else null
        }
        isInverseMatrixDirty = false
        if (isIdentity) {
            inverseMatrix.reset()
            isInverseMatrixValid = true
            return inverseMatrix
        }
        isInverseMatrixValid = matrix.invertTo(inverseMatrix)
        return if (isInverseMatrixValid) inverseMatrix else null
    }

    private fun updateMatrix() {
        val pivotX: Float
        val pivotY: Float
        if (graphicsLayer.pivotOffset.isUnspecified) {
            pivotX = size.width / 2f
            pivotY = size.height / 2f
        } else {
            pivotX = graphicsLayer.pivotOffset.x
            pivotY = graphicsLayer.pivotOffset.y
        }
        prepareTransformationMatrix(
            matrix = matrix,
            pivotX = pivotX,
            pivotY = pivotY,
            translationX = graphicsLayer.translationX,
            translationY = graphicsLayer.translationY,
            rotationX = graphicsLayer.rotationX,
            rotationY = graphicsLayer.rotationY,
            rotationZ = graphicsLayer.rotationZ,
            scaleX = graphicsLayer.scaleX,
            scaleY = graphicsLayer.scaleY,
            cameraDistance = graphicsLayer.cameraDistance,
        )
        isIdentity = matrix.isIdentity()
        isInverseMatrixDirty = true
    }

    private fun resetLayerState() {
        position = IntOffset.Zero
        size = IntSize.Zero
        density = Density(1f)
        layoutDirection = LayoutDirection.Ltr
        transformOrigin = TransformOrigin.Center
        layerOutsets = LayerOutsets.Zero
        mutatedFields = 0
        frameRate = 0f
        isFrameRateFromParent = false
        matrix.reset()
        inverseMatrix.reset()
        isIdentity = true
        isInverseMatrixDirty = false
        isInverseMatrixValid = true
        isDirty = true
        displayListUpdateCount = 0
    }

    private fun resetGraphicsLayer() {
        graphicsLayer.topLeft = IntOffset.Zero
        graphicsLayer.pivotOffset = Offset.Unspecified
        graphicsLayer.alpha = 1f
        graphicsLayer.scaleX = 1f
        graphicsLayer.scaleY = 1f
        graphicsLayer.translationX = 0f
        graphicsLayer.translationY = 0f
        graphicsLayer.shadowElevation = 0f
        graphicsLayer.ambientShadowColor = Color.Black
        graphicsLayer.spotShadowColor = Color.Black
        graphicsLayer.rotationX = 0f
        graphicsLayer.rotationY = 0f
        graphicsLayer.rotationZ = 0f
        graphicsLayer.cameraDistance = androidx.compose.ui.graphics.DefaultCameraDistance
        graphicsLayer.clip = false
        graphicsLayer.renderEffect = null
        graphicsLayer.colorFilter = null
        graphicsLayer.blendMode = BlendMode.SrcOver
        graphicsLayer.compositingStrategy = LayerCompositingStrategy.Auto
        graphicsLayer.setRectOutline()
    }

    internal fun stateForTest(): WinUIOwnerLayerState =
        WinUIOwnerLayerState(
            isDirty = isDirty,
            isDestroyed = isDestroyed,
            displayListUpdateCount = displayListUpdateCount,
            position = position,
            size = size,
            alpha = graphicsLayer.alpha,
            shadowElevation = graphicsLayer.shadowElevation,
            ambientShadowColor = graphicsLayer.ambientShadowColor,
            spotShadowColor = graphicsLayer.spotShadowColor,
            clip = graphicsLayer.clip,
            cameraDistance = graphicsLayer.cameraDistance,
            renderEffect = graphicsLayer.renderEffect,
            colorFilter = graphicsLayer.colorFilter,
            blendMode = graphicsLayer.blendMode,
            compositingStrategy = graphicsLayer.compositingStrategy,
            outline = graphicsLayer.outline,
            outsets = layerOutsets,
        )
}

internal data class WinUIOwnerLayerState(
    val isDirty: Boolean,
    val isDestroyed: Boolean,
    val displayListUpdateCount: Int,
    val position: IntOffset,
    val size: IntSize,
    val alpha: Float = 1f,
    val shadowElevation: Float = 0f,
    val ambientShadowColor: Color = Color.Black,
    val spotShadowColor: Color = Color.Black,
    val clip: Boolean = false,
    val cameraDistance: Float = androidx.compose.ui.graphics.DefaultCameraDistance,
    val renderEffect: androidx.compose.ui.graphics.RenderEffect? = null,
    val colorFilter: ColorFilter? = null,
    val blendMode: BlendMode = BlendMode.SrcOver,
    val compositingStrategy: LayerCompositingStrategy = LayerCompositingStrategy.Auto,
    val outline: Outline? = null,
    val outsets: LayerOutsets = LayerOutsets.Zero,
)
