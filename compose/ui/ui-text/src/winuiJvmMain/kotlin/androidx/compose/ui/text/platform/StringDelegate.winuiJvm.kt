/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.ui.text.platform

import androidx.compose.ui.text.PlatformStringDelegate
import androidx.compose.ui.text.intl.Locale
import java.util.Locale as JavaLocale

private class WinUIStringDelegate : PlatformStringDelegate {
    override fun toUpperCase(string: String, locale: Locale): String =
        string.uppercase(locale.toJavaLocale())

    override fun toLowerCase(string: String, locale: Locale): String =
        string.lowercase(locale.toJavaLocale())

    override fun capitalize(string: String, locale: Locale): String =
        string.replaceFirstChar {
            if (it.isLowerCase()) {
                it.titlecase(locale.toJavaLocale())
            } else {
                it.toString()
            }
        }

    override fun decapitalize(string: String, locale: Locale): String =
        string.replaceFirstChar { it.lowercase(locale.toJavaLocale()) }
}

private fun Locale.toJavaLocale(): JavaLocale = JavaLocale.forLanguageTag(toLanguageTag())

internal actual fun ActualStringDelegate(): PlatformStringDelegate =
    WinUIStringDelegate()
