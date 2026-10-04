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

package androidx.compose.ui.text

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class WinUIParagraphTest {
    // The Skiko implementation of ui-graphics and ui-text is registered at runtime.
    init {
        registerSkikoComposeImplementation()
    }

    @Test
    fun paragraphMeasuresAndDrawsTextIntoImageBitmap() {
        val paragraph = Paragraph(
            text = "WinUI text",
            style = TextStyle(fontSize = 24.sp, color = Color.Black),
            constraints = Constraints(maxWidth = 200),
            density = Density(1f),
            fontFamilyResolver = createFontFamilyResolver(),
        )

        assertTrue(paragraph.minIntrinsicWidth > 0f)
        assertTrue(paragraph.height > 0f)
        assertTrue(paragraph.firstBaseline > 0f)

        val bitmap = ImageBitmap(220, 80)
        paragraph.paint(Canvas(bitmap), Color.Black, null, null)

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels)
        assertFalse(pixels.all { it == 0 })
    }

    @Test
    fun paragraphReportsLineAndSelectionGeometry() {
        val paragraph = Paragraph(
            text = "WinUI\ntext",
            style = TextStyle(fontSize = 20.sp, color = Color.Black),
            constraints = Constraints(maxWidth = 200),
            density = Density(1f),
            fontFamilyResolver = createFontFamilyResolver(),
        )

        assertEquals(2, paragraph.lineCount)
        assertEquals(0, paragraph.getLineStart(0))
        assertEquals(5, paragraph.getLineEnd(0, visibleEnd = true))
        assertEquals(6, paragraph.getLineStart(1))
        assertEquals(10, paragraph.getLineEnd(1, visibleEnd = true))
        assertTrue(paragraph.getLineBottom(1) > paragraph.getLineBottom(0))

        val firstCursor = paragraph.getCursorRect(0)
        val secondCursor = paragraph.getCursorRect(1)
        val firstGlyphBounds = paragraph.getBoundingBox(0)
        val selectionPath = paragraph.getPathForRange(0, 3)

        assertTrue(secondCursor.left > firstCursor.left)
        assertTrue(firstGlyphBounds.right > firstGlyphBounds.left)
        assertFalse(selectionPath.isEmpty)
    }

    @Test
    fun paragraphDrawsBrushTextIntoImageBitmap() {
        val paragraph = Paragraph(
            text = "Brush",
            style = TextStyle(fontSize = 32.sp, color = Color.Black),
            constraints = Constraints(maxWidth = 200),
            density = Density(1f),
            fontFamilyResolver = createFontFamilyResolver(),
        )
        val bitmap = ImageBitmap(220, 80)

        paragraph.paint(
            canvas = Canvas(bitmap),
            brush = Brush.linearGradient(listOf(Color.Green, Color.Green)),
            alpha = 1f,
            shadow = null,
            textDecoration = null,
            drawStyle = null,
            blendMode = BlendMode.SrcOver,
        )

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels)
        assertTrue(pixels.any { pixel -> green(pixel) > red(pixel) && green(pixel) > blue(pixel) })
    }

    @Test
    fun paragraphAppliesBlendMode() {
        val paragraph = Paragraph(
            text = "Clear",
            style = TextStyle(fontSize = 32.sp, color = Color.Black),
            constraints = Constraints(maxWidth = 200),
            density = Density(1f),
            fontFamilyResolver = createFontFamilyResolver(),
        )
        val bitmap = ImageBitmap(220, 80)
        val canvas = Canvas(bitmap)

        canvas.drawRect(
            0f,
            0f,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            androidx.compose.ui.graphics.Paint().apply { color = Color.Red },
        )
        paragraph.paint(
            canvas = canvas,
            color = Color.Black,
            shadow = null,
            textDecoration = null,
            drawStyle = null,
            blendMode = BlendMode.Clear,
        )

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels)
        assertTrue(pixels.any { alpha(it) == 0 })
    }

    @Test
    fun paragraphResolvesCommonWinUIFontFamilies() {
        listOf(
            FontFamily.Default,
            FontFamily.Serif,
            FontFamily.Monospace,
        ).forEach { fontFamily ->
            val paragraph = Paragraph(
                text = "WinUI font",
                style = TextStyle(
                    fontSize = 18.sp,
                    color = Color.Black,
                    fontFamily = fontFamily,
                ),
                constraints = Constraints(maxWidth = 240),
                density = Density(1f),
                fontFamilyResolver = createFontFamilyResolver(),
            )

            assertTrue(paragraph.minIntrinsicWidth > 0f)
            assertTrue(paragraph.height > 0f)
        }
    }

    private fun alpha(pixel: Int): Int = pixel ushr 24 and 0xff
    private fun red(pixel: Int): Int = pixel ushr 16 and 0xff
    private fun green(pixel: Int): Int = pixel ushr 8 and 0xff
    private fun blue(pixel: Int): Int = pixel and 0xff
}
