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

package androidx.compose.ui.text.font

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.platform.SystemFont
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.jetbrains.skia.Typeface as SkTypeface

@OptIn(ExperimentalTextApi::class)
@Suppress("DEPRECATION")
class WinUIFontResourceLoaderTest {
    @Test
    fun supportedPlatformFontLoadsSkiaTypeface() {
        assertIs<SkTypeface>(WinUIFontResourceLoader.load(SystemFont("Segoe UI")))
    }

    @Test
    fun unsupportedFontRetainsSkiaLoaderFailure() {
        val failure = assertFailsWith<IllegalArgumentException> {
            WinUIFontResourceLoader.load(UnsupportedFont)
        }

        assertTrue(failure.message.orEmpty().contains("Unsupported font type"))
    }

    private object UnsupportedFont : Font {
        override val weight: FontWeight = FontWeight.Normal
        override val style: FontStyle = FontStyle.Normal
    }
}
