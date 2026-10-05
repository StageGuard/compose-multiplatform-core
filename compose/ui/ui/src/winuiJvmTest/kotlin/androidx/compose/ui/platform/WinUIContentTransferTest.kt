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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIContentTransferTest {
    private val formats =
        WinUIStandardDataFormatIds(
            text = "Text",
            html = "HTML Format",
            rtf = "Rich Text Format",
            bitmap = "Bitmap",
        )

    @Test
    fun winUIHtmlMatchesHtmlAndTextMediaTypes() {
        val availableFormats = setOf(formats.html)

        assertTrue(winUIMatchesMediaType(availableFormats, "text/html", formats))
        assertTrue(winUIMatchesMediaType(availableFormats, "text/*", formats))
        assertFalse(winUIMatchesMediaType(availableFormats, "text/plain", formats))
    }

    @Test
    fun winUIRtfMatchesTextButNotPlainOrHtml() {
        val availableFormats = setOf(formats.rtf)

        assertTrue(winUIMatchesMediaType(availableFormats, "text/*", formats))
        assertFalse(winUIMatchesMediaType(availableFormats, "text/plain", formats))
        assertFalse(winUIMatchesMediaType(availableFormats, "text/html", formats))
    }

    @Test
    fun winUIStandardAndCustomFormatsMatchTheirMediaTypes() {
        assertTrue(winUIMatchesMediaType(setOf(formats.text), "text/plain", formats))
        assertTrue(winUIMatchesMediaType(setOf(formats.bitmap), "image/*", formats))
        assertTrue(winUIMatchesMediaType(setOf("custom/format"), "custom/format", formats))
        assertFalse(winUIMatchesMediaType(emptySet(), "text/*", formats))
    }
}
