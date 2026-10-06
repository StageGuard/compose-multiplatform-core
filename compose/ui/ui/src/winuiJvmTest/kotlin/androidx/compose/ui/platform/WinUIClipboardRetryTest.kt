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

import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.WinRTRuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

// Another process holds the clipboard for the moment of its own read or write: a copy or paste
// right after it fails with CLIPBRD_E_CANT_OPEN, and the operation is repeated.
class WinUIClipboardRetryTest {
    private fun clipboardHeld() = WinRTRuntimeException("OpenClipboard Failed", HResult(WinUIClipboardCannotOpen))

    @Test
    fun anOperationIsRepeatedWhileTheClipboardIsHeld() = runBlocking {
        var attempts = 0
        val result = retryWhileClipboardIsHeld {
            attempts++
            if (attempts < 3) throw clipboardHeld()
            "copied"
        }
        assertEquals("copied", result)
        assertEquals(3, attempts)
    }

    @Test
    fun aClipboardThatStaysHeldFailsAfterTheRetries() = runBlocking {
        var attempts = 0
        assertFailsWith<WinRTRuntimeException> {
            retryWhileClipboardIsHeld<Unit> {
                attempts++
                throw clipboardHeld()
            }
        }
        assertEquals(WinUIClipboardRetries + 1, attempts)
    }

    @Test
    fun anotherFailureIsNotRepeated() = runBlocking {
        var attempts = 0
        assertFailsWith<WinRTRuntimeException> {
            retryWhileClipboardIsHeld<Unit> {
                attempts++
                throw WinRTRuntimeException("Access denied", HResult(0x80070005.toInt()))
            }
        }
        assertEquals(1, attempts)
    }
}
