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

@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package androidx.compose.ui.platform

import io.github.composefluent.winrt.runtime.await
import kotlinx.coroutines.CancellationException
import windows.applicationmodel.datatransfer.DataPackage
import windows.applicationmodel.datatransfer.DataPackageView
import windows.applicationmodel.datatransfer.StandardDataFormats

internal class WinUIDataPackageViewClipMetadata(private val dataView: DataPackageView) :
    PlatformClipMetadata {
    override val availableFormats: Set<String> =
        runCatching { dataView.availableFormats.toSet() }.getOrDefault(emptySet())

    private var isPlainTextLoaded = false
    private var plainText: String? = null

    internal val containsPlainText: Boolean
        get() = winUIStandardDataFormatIds.text in availableFormats

    override fun hasMediaType(representation: String): Boolean =
        winUIMatchesMediaType(availableFormats, representation, winUIStandardDataFormatIds)

    override fun readPlainText(): String? = plainText

    suspend fun loadPlainText(): String? {
        if (isPlainTextLoaded) return plainText
        val loadedText =
            if (containsPlainText) {
                try {
                    dataView.getTextAsync().await()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
        plainText = loadedText
        isPlainTextLoaded = true
        return plainText
    }
}

internal fun platformClipMetadataFor(nativeClipEntry: Any?): PlatformClipMetadata =
    when (nativeClipEntry) {
        is PlatformClipMetadata -> nativeClipEntry
        is String -> PlainTextPlatformClipMetadata(nativeClipEntry)
        is DataPackageView -> WinUIDataPackageViewClipMetadata(nativeClipEntry)
        is DataPackage ->
            runCatching { WinUIDataPackageViewClipMetadata(nativeClipEntry.getView()) }
                .getOrDefault(EmptyPlatformClipMetadata)
        else -> EmptyPlatformClipMetadata
    }

private class PlainTextPlatformClipMetadata(private val text: String) : PlatformClipMetadata {
    override val availableFormats: Set<String> = setOf(winUIStandardDataFormatIds.text)

    override fun hasMediaType(representation: String): Boolean =
        winUIMatchesMediaType(availableFormats, representation, winUIStandardDataFormatIds)

    override fun readPlainText(): String = text
}

private object EmptyPlatformClipMetadata : PlatformClipMetadata {
    override val availableFormats: Set<String> = emptySet()

    override fun hasMediaType(representation: String): Boolean = false

    override fun readPlainText(): String? = null
}

internal data class WinUIStandardDataFormatIds(
    val text: String,
    val html: String,
    val rtf: String,
    val bitmap: String,
)

internal fun winUIMatchesMediaType(
    availableFormats: Set<String>,
    representation: String,
    standardFormats: WinUIStandardDataFormatIds,
): Boolean {
    if (availableFormats.isEmpty()) return false
    return when (representation) {
        "*/*" -> true
        "text/*" ->
            availableFormats.any { format ->
                format == standardFormats.text ||
                    format == standardFormats.html ||
                    format == standardFormats.rtf ||
                    format.startsWith("text/", ignoreCase = true)
            }
        "text/plain" ->
            standardFormats.text in availableFormats || representation in availableFormats
        "text/html" ->
            standardFormats.html in availableFormats || representation in availableFormats
        "text/rtf" -> standardFormats.rtf in availableFormats || representation in availableFormats
        "image/*" ->
            standardFormats.bitmap in availableFormats ||
                availableFormats.any { format -> format.startsWith("image/", ignoreCase = true) }
        else -> representation in availableFormats
    }
}

private val winUIStandardDataFormatIds: WinUIStandardDataFormatIds by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
        WinUIStandardDataFormatIds(
            text = runCatching { StandardDataFormats.text }.getOrDefault("Text"),
            html = runCatching { StandardDataFormats.html }.getOrDefault("HTML Format"),
            rtf = runCatching { StandardDataFormats.rtf }.getOrDefault("Rich Text Format"),
            bitmap = runCatching { StandardDataFormats.bitmap }.getOrDefault("Bitmap"),
        )
    }
