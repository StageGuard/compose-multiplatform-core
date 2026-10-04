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

package androidx.compose.ui.graphics

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class WinUIGraphicsPaintTest {
    // The Skiko implementation of ui-graphics and ui-text is registered at runtime.
    init {
        registerSkikoComposeImplementation()
    }

    @Test
    fun dashPathEffectChangesDrawnLinePixels() {
        val image = ImageBitmap(24, 5)
        val canvas = Canvas(image)
        val paint = Paint().apply {
            color = Color.Red
            style = PaintingStyle.Stroke
            strokeWidth = 1f
            isAntiAlias = false
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        }

        canvas.drawLine(Offset(0f, 2f), Offset(24f, 2f), paint)

        assertTrue(pixelAt(image, 1, 2).alpha > 0)
        assertEquals(0, pixelAt(image, 6, 2).alpha)
        assertTrue(pixelAt(image, 9, 2).alpha > 0)
    }

    @Test
    fun colorFilterTintIsAppliedToPaint() {
        val image = ImageBitmap(4, 4)
        val canvas = Canvas(image)
        val paint = Paint().apply {
            color = Color.White
            colorFilter = ColorFilter.tint(Color.Red, BlendMode.SrcIn)
        }

        canvas.drawRect(0f, 0f, 4f, 4f, paint)

        val pixel = pixelAt(image, 1, 1)
        assertTrue(pixel.alpha > 0)
        assertTrue(pixel.red > 200)
        assertTrue(pixel.green < 50)
        assertTrue(pixel.blue < 50)
    }

    @Test
    fun blendModeIsAppliedToPaint() {
        val image = ImageBitmap(4, 4)
        val canvas = Canvas(image)
        canvas.drawRect(
            0f,
            0f,
            4f,
            4f,
            Paint().apply {
                color = Color.Red
            },
        )

        canvas.drawRect(
            0f,
            0f,
            4f,
            4f,
            Paint().apply {
                color = Color.Blue
                blendMode = BlendMode.Clear
            },
        )

        assertEquals(0, pixelAt(image, 1, 1).alpha)
    }

    @Test
    fun pathIteratorReturnsSkiaBackedSegments() {
        val path = Path().apply {
            moveTo(1f, 2f)
            lineTo(3f, 4f)
            quadraticTo(5f, 6f, 7f, 8f)
            cubicTo(9f, 10f, 11f, 12f, 13f, 14f)
            close()
        }
        val iterator = PathIterator(path)

        assertEquals(5, iterator.calculateSize(includeConvertedConics = false))
        assertTrue(iterator.hasNext())
        assertEquals(PathSegment.Type.Move, iterator.next().type)
        assertEquals(PathSegment.Type.Line, iterator.next().type)

        val points = FloatArray(8)
        assertEquals(PathSegment.Type.Quadratic, iterator.next(points))
        assertEquals(3f, points[0])
        assertEquals(4f, points[1])
        assertEquals(5f, points[2])
        assertEquals(6f, points[3])
        assertEquals(7f, points[4])
        assertEquals(8f, points[5])

        assertEquals(PathSegment.Type.Cubic, iterator.next().type)
        assertEquals(PathSegment.Type.Line, iterator.next().type)
        assertEquals(PathSegment.Type.Close, iterator.next().type)
        assertEquals(PathSegment.Type.Done, iterator.next().type)
    }

    @Test
    @OptIn(InternalComposeUiApi::class)
    fun graphicsLayerAppliesRenderEffect() {
        val graphicsContext = SkiaGraphicsContext()
        val layer = graphicsContext.createGraphicsLayer()
        try {
            layer.apply {
                renderEffect = BlurEffect(
                    renderEffect = null,
                    radiusX = 4f,
                    radiusY = 4f,
                    edgeTreatment = TileMode.Decal,
                )
                record(
                    density = Density(1f),
                    layoutDirection = LayoutDirection.Ltr,
                    size = IntSize(8, 8),
                ) {
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(3f, 3f),
                        size = Size(2f, 2f),
                    )
                }
            }
            val image = ImageBitmap(8, 8)

            layer.draw(Canvas(image), parentLayer = null)

            assertTrue(pixelAt(image, 4, 4).alpha > 0)
            assertTrue(pixelAt(image, 2, 4).alpha > 0)
            assertTrue(pixelAt(image, 4, 2).alpha > 0)
        } finally {
            graphicsContext.releaseGraphicsLayer(layer)
            graphicsContext.close()
        }
    }

    private fun pixelAt(image: ImageBitmap, x: Int, y: Int): Pixel {
        val pixels = IntArray(1)
        image.readPixels(
            buffer = pixels,
            startX = x,
            startY = y,
            width = 1,
            height = 1,
        )
        return Pixel(pixels[0])
    }

    private class Pixel(private val argb: Int) {
        val alpha: Int get() = argb ushr 24 and 0xff
        val red: Int get() = argb ushr 16 and 0xff
        val green: Int get() = argb ushr 8 and 0xff
        val blue: Int get() = argb and 0xff
    }
}
