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

package androidx.compose.ui.text.intl

import androidx.compose.runtime.Immutable
import windows.globalization.ApplicationLanguages
import windows.globalization.Language
import windows.globalization.LanguageLayoutDirection

internal actual fun createPlatformLocaleDelegate() = object : PlatformLocaleDelegate {
    override val current: LocaleList
        get() = LocaleList(
            ApplicationLanguages.languages
                .ifEmpty { listOf(DefaultLanguageTag) }
                .map(::Locale),
        )
}

@Immutable
actual class Locale actual constructor(languageTag: String) {
    internal val platformLanguage = Language(languageTag.ifBlank { DefaultLanguageTag })
    private val normalizedTag = platformLanguage.languageTag
    private val subtags = normalizedTag.split('-')

    actual val language: String = subtags.firstOrNull().orEmpty().lowercase()
    actual val script: String = platformLanguage.script
    actual val region: String = subtags
        .drop(1)
        .firstOrNull { part ->
            part.length == 2 && part.all(Char::isLetter) ||
                part.length == 3 && part.all(Char::isDigit)
        }
        .orEmpty()
        .uppercase()

    actual fun toLanguageTag(): String = normalizedTag

    actual override fun equals(other: Any?): Boolean =
        other is Locale && normalizedTag.equals(other.normalizedTag, ignoreCase = true)

    actual override fun hashCode(): Int = normalizedTag.lowercase().hashCode()

    actual override fun toString(): String = normalizedTag

    actual companion object {
        actual val current: Locale
            get() = platformLocaleDelegate.current[0]
    }
}

internal actual fun Locale.isRtl(): Boolean =
    platformLanguage.layoutDirection == LanguageLayoutDirection.Rtl

private const val DefaultLanguageTag = "en-US"
