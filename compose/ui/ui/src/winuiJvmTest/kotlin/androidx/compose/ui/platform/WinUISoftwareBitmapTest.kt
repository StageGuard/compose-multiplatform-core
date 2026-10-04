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

import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import windows.graphics.imaging.BitmapAlphaMode
import windows.graphics.imaging.BitmapBufferAccessMode
import windows.graphics.imaging.BitmapPixelFormat

class WinUISoftwareBitmapTest {
    @Test
    fun drawnPixelsAreCopiedIntoTheBitmapRowByRow() {
        val width = 3
        val height = 2
        val pixels = ByteArray(width * height * 4) { it.toByte() }

        val bitmap = assertNotNull(createSoftwareBitmap(width, height, pixels))
        try {
            assertEquals(width, bitmap.pixelWidth)
            assertEquals(height, bitmap.pixelHeight)
            assertEquals(BitmapPixelFormat.Bgra8, bitmap.bitmapPixelFormat)
            assertEquals(BitmapAlphaMode.Premultiplied, bitmap.bitmapAlphaMode)
            val copied = bitmap.accessBytes(BitmapBufferAccessMode.Read) { bytes, plane ->
                ByteArray(width * height * 4).also { out ->
                    for (row in 0 until height) {
                        val rowStart = plane.startIndex + row.toLong() * plane.stride
                        for (column in 0 until width * 4) {
                            out[row * width * 4 + column] =
                                bytes.get(ValueLayout.JAVA_BYTE, rowStart + column)
                        }
                    }
                }
            }
            assertContentEquals(pixels, copied)
        } finally {
            bitmap.close()
        }
    }
}
