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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

internal expect fun Char.isWinUIPrintable(): Boolean

// A pressed or released key has the character of that key in the current layout as its
// utf16CodePoint, as on desktop, but it is no typed event: what the user types arrives on its own,
// after dead keys and input methods are applied (CharacterReceived or the text input session), and
// is committed to the text input session. A typed event has an unknown type, as KEY_TYPED on
// desktop.
@InternalFoundationApi
actual val KeyEvent.isTypedEvent: Boolean
    get() = type == KeyEventType.Unknown && utf16CodePoint.toChar().isWinUIPrintable()
