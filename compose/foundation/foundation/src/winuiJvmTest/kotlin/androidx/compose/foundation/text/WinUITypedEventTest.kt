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

package androidx.compose.foundation.text

import androidx.compose.foundation.InternalFoundationApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class, InternalFoundationApi::class)
class WinUITypedEventTest {
    @Test
    fun pressedAndReleasedKeysAreNotTypedEvents() {
        // The key events carry the character of the key, but the typed character arrives on its
        // own: a text field that took both would insert it two or three times.
        assertFalse(KeyEvent(Key.X, KeyEventType.KeyDown, codePoint = 'x'.code).isTypedEvent)
        assertFalse(KeyEvent(Key.X, KeyEventType.KeyUp, codePoint = 'x'.code).isTypedEvent)
    }

    @Test
    fun eventOfUnknownTypeWithAPrintableCharacterIsATypedEvent() {
        assertTrue(KeyEvent(Key.Unknown, KeyEventType.Unknown, codePoint = 'x'.code).isTypedEvent)
        assertFalse(KeyEvent(Key.Unknown, KeyEventType.Unknown, codePoint = 0x1B).isTypedEvent)
        assertFalse(KeyEvent(Key.Unknown, KeyEventType.Unknown).isTypedEvent)
    }
}
