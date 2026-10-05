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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package androidx.compose.mpp.demo.components.text

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

actual suspend fun loadResource(file: String): ByteArray? =
    readResourceBytes(file) ?: readResourceBytes("Appx/$file")

private fun readResourceBytes(path: String): ByteArray? {
    val stream = fopen(path, "rb") ?: return null
    return try {
        if (fseek(stream, 0, SEEK_END) != 0) return null
        val size = ftell(stream)
        if (size < 0) return null
        if (fseek(stream, 0, SEEK_SET) != 0) return null

        val bytes = ByteArray(size)
        if (bytes.isEmpty()) return bytes
        val bytesRead = bytes.usePinned { pinned ->
            fread(pinned.addressOf(0), 1.convert(), bytes.size.convert(), stream)
        }
        bytes.takeIf { bytesRead.toInt() == size }
    } finally {
        fclose(stream)
    }
}
