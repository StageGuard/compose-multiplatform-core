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

import androidx.compose.ui.input.key.KeyEventType
import windows.system.VirtualKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WinUICharacterInputProcessorTest {
    @Test
    fun commitsPrintableCharactersToActiveInputSession() {
        val processor = WinUICharacterInputProcessor()
        val committed = mutableListOf<String>()

        val handled = processor.process(
            codePoint = '中'.code,
            isHandled = false,
            commitText = { text ->
                committed += text
                true
            },
        )

        assertEquals(true, handled)
        assertEquals(listOf("中"), committed)
    }

    @Test
    fun skipsAlreadyHandledNativeCharacterEvents() {
        val processor = WinUICharacterInputProcessor()
        var dispatchCount = 0

        val handled = processor.process(
            codePoint = 'a'.code,
            isHandled = true,
            commitText = {
                dispatchCount += 1
                true
            },
        )

        assertNull(handled)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun skipsControlCharacters() {
        val processor = WinUICharacterInputProcessor()
        var dispatchCount = 0

        val handled = processor.process(
            codePoint = '\n'.code,
            isHandled = false,
            commitText = {
                dispatchCount += 1
                true
            },
        )

        assertNull(handled)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun commitsCharacterFallbackWhenCoreTextIsActive() {
        val processor = WinUICharacterInputProcessor()
        val committed = mutableListOf<String>()

        val handled = processor.process(
            codePoint = 'a'.code,
            isHandled = false,
            isCoreTextInputActive = true,
            commitText = { text ->
                committed += text
                true
            },
        )

        assertEquals(true, handled)
        assertEquals(listOf("a"), committed)
    }

    @Test
    fun suppressesCharacterFallbackDuringCoreTextComposition() {
        val processor = WinUICharacterInputProcessor()
        var dispatchCount = 0

        val handled = processor.process(
            codePoint = 'a'.code,
            isHandled = false,
            isCoreTextInputActive = true,
            isCoreTextCompositionActive = true,
            commitText = {
                dispatchCount += 1
                true
            },
        )

        assertEquals(true, handled)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun skipsNextCharacterWhenPrintableKeyDownWasAlreadyHandled() {
        val processor = WinUICharacterInputProcessor()
        val committed = mutableListOf<String>()

        processor.onKeyEventProcessed(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.A,
            wasHandled = true,
        )
        val skipped = processor.process(
            codePoint = 'a'.code,
            isHandled = false,
            commitText = { text ->
                committed += text
                true
            },
        )
        val next = processor.process(
            codePoint = 'b'.code,
            isHandled = false,
            commitText = { text ->
                committed += text
                true
            },
        )

        assertNull(skipped)
        assertEquals(true, next)
        assertEquals(listOf("b"), committed)
    }

    @Test
    fun doesNotSkipCharacterWhenKeyDownWasUnhandled() {
        val processor = WinUICharacterInputProcessor()
        val committed = mutableListOf<String>()

        processor.onKeyEventProcessed(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.A,
            wasHandled = false,
        )
        val handled = processor.process(
            codePoint = 'a'.code,
            isHandled = false,
            commitText = { text ->
                committed += text
                true
            },
        )

        assertEquals(true, handled)
        assertEquals(listOf("a"), committed)
    }

    @Test
    fun handledNonCharacterKeyDoesNotSuppressCharacter() {
        val processor = WinUICharacterInputProcessor()
        val committed = mutableListOf<String>()

        processor.onKeyEventProcessed(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.Left,
            wasHandled = true,
        )
        val handled = processor.process(
            codePoint = 'a'.code,
            isHandled = false,
            commitText = { text ->
                committed += text
                true
            },
        )

        assertEquals(true, handled)
        assertEquals(listOf("a"), committed)
    }

    @Test
    fun nextKeyDownClearsStaleCharacterSuppression() {
        val processor = WinUICharacterInputProcessor()
        val committed = mutableListOf<String>()

        processor.onKeyEventProcessed(KeyEventType.KeyDown, VirtualKey.A, wasHandled = true)
        processor.onKeyEventProcessed(KeyEventType.KeyDown, VirtualKey.B, wasHandled = false)
        val handled = processor.process(
            codePoint = 'b'.code,
            isHandled = false,
            commitText = { text ->
                committed += text
                true
            },
        )

        assertEquals(true, handled)
        assertEquals(listOf("b"), committed)
    }

    @Test
    fun keyUpClearsPendingCharacterSuppression() {
        val processor = WinUICharacterInputProcessor()
        val committed = mutableListOf<String>()

        processor.onKeyEventProcessed(KeyEventType.KeyDown, VirtualKey.A, wasHandled = true)
        processor.onKeyEventProcessed(KeyEventType.KeyUp, VirtualKey.A, wasHandled = true)
        val handled = processor.process(
            codePoint = 'a'.code,
            isHandled = false,
            commitText = { text ->
                committed += text
                true
            },
        )

        assertEquals(true, handled)
        assertEquals(listOf("a"), committed)
    }

    @Test
    fun handledCharacterReceivedClearsPendingSuppression() {
        val processor = WinUICharacterInputProcessor()
        val committed = mutableListOf<String>()

        processor.onKeyEventProcessed(KeyEventType.KeyDown, VirtualKey.A, wasHandled = true)
        assertNull(
            processor.process(
                codePoint = 'a'.code,
                isHandled = true,
                commitText = { error("handled character must not be committed") },
            )
        )
        val handled = processor.process(
            codePoint = 'b'.code,
            isHandled = false,
            commitText = { text ->
                committed += text
                true
            },
        )

        assertEquals(true, handled)
        assertEquals(listOf("b"), committed)
    }

    @Test
    fun returnsFalseWhenNoInputSessionAcceptsCharacter() {
        val processor = WinUICharacterInputProcessor()

        val handled = processor.process(
            codePoint = 'a'.code,
            isHandled = false,
            commitText = { false },
        )

        assertEquals(false, handled)
    }
}
