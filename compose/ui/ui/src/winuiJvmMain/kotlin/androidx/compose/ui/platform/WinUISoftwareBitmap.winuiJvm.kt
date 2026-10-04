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

import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.IWinRTObject
import io.github.composefluent.winrt.runtime.PlatformAbi
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import windows.graphics.imaging.BitmapAlphaMode
import windows.graphics.imaging.BitmapBufferAccessMode
import windows.graphics.imaging.BitmapPixelFormat
import windows.graphics.imaging.BitmapPlaneDescription
import windows.graphics.imaging.SoftwareBitmap

internal actual fun createSoftwareBitmap(
    width: Int,
    height: Int,
    pixels: ByteArray,
): SoftwareBitmap? {
    val rowBytes = width * 4
    require(pixels.size >= rowBytes * height) { "Not enough pixels for $width x $height" }
    val bitmap = SoftwareBitmap(
        BitmapPixelFormat.Bgra8,
        width,
        height,
        BitmapAlphaMode.Premultiplied,
    )
    val isCopied = runCatching {
        bitmap.accessBytes(BitmapBufferAccessMode.Write) { bytes, plane ->
            for (row in 0 until height) {
                MemorySegment.copy(
                    pixels,
                    row * rowBytes,
                    bytes,
                    ValueLayout.JAVA_BYTE,
                    plane.startIndex + row.toLong() * plane.stride,
                    rowBytes,
                )
            }
        }
    }.isSuccess
    if (!isCopied) {
        runCatching { bitmap.close() }
        return null
    }
    return bitmap
}

/**
 * Runs [block] with the bytes of the first plane of this bitmap, locked in [mode].
 */
internal fun <T> SoftwareBitmap.accessBytes(
    mode: BitmapBufferAccessMode,
    block: (bytes: MemorySegment, plane: BitmapPlaneDescription) -> T,
): T =
    lockBuffer(mode).use { buffer ->
        val plane = buffer.getPlaneDescription(0)
        buffer.createReference().use { reference ->
            // IMemoryBufferByteAccess is a COM interface that WinRT doesn't project.
            val byteAccess = (reference as IWinRTObject).nativeObject
                .queryInterface(IID.IMemoryBufferByteAccess)
                .getOrThrow()
            byteAccess.use {
                val bytes = PlatformAbi.confinedScope().use { scope ->
                    val dataOut = PlatformAbi.allocatePointerSlot(scope)
                    val capacityOut = PlatformAbi.allocateInt32Slot(scope)
                    HResult(
                        ComVtableInvoker.invokeArgs(
                            instance = byteAccess.pointer,
                            slot = 3,
                            arg0 = dataOut,
                            arg1 = capacityOut,
                        ),
                    ).requireSuccess("IMemoryBufferByteAccess.GetBuffer")
                    val address = PlatformAbi.readPointer(dataOut).value
                    val capacity = PlatformAbi.readInt32(capacityOut).toLong() and 0xFFFFFFFFL
                    MemorySegment.ofAddress(address).reinterpret(capacity)
                }
                // The bytes stay valid while the buffer reference is open.
                block(bytes, plane)
            }
        }
    }
