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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.AnnotatedString
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.await
import windows.applicationmodel.datatransfer.DataPackage
import windows.applicationmodel.datatransfer.DataPackageView
import windows.applicationmodel.datatransfer.Clipboard as WinRTClipboardClass
import windows.applicationmodel.datatransfer.StandardDataFormats as WinRTStandardDataFormats

actual typealias NativeClipboard = IUnknownReference

@Suppress("DEPRECATION")
internal class WinUIClipboardManager(
    private val clipboard: WinUIClipboard,
) : ClipboardManager {
    override fun getText(): AnnotatedString? =
        clipboard.getTextBlocking()?.let { AnnotatedString(it) }

    override fun setText(annotatedString: AnnotatedString) {
        clipboard.setText(annotatedString.text)
    }

    override fun hasText(): Boolean =
        clipboard.hasText()

    override fun getClip(): ClipEntry? =
        clipboard.getClipEntryBlocking()

    @Suppress("GetterSetterNames")
    override fun setClip(clipEntry: ClipEntry?) {
        clipboard.setClipEntryBlocking(clipEntry)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override val nativeClipboard: NativeClipboard
        get() = winUIClipboardStatics
}

internal class WinUIClipboard : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? =
        readClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        setClipEntryBlocking(clipEntry)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override val nativeClipboard: NativeClipboard
        get() = winUIClipboardStatics

    internal fun hasText(): Boolean =
        lastPlainText != null || runCatching { getWinUIContent().contains(winUITextFormat) }
            .getOrElse {
                logClipboardReadFailure(it)
                false
            }

    internal fun getTextBlocking(): String? {
        // The deprecated ClipboardManager API is synchronous. Preserve Android-like
        // setText/getText round-trips for in-process writes without blocking WinRT async work.
        lastPlainText?.let { return it }
        val content = runCatching { getWinUIContent() }
            .getOrElse {
                logClipboardReadFailure(it)
                return null
            }
        if (!runCatching { content.contains(winUITextFormat) }.getOrDefault(false)) {
            return null
        }
        return null
    }

    internal fun setText(text: String) {
        lastPlainText = text
        // Windows clipboard ownership can be transiently locked by another process. Keep Compose
        // API state coherent and treat the native clipboard write as best effort.
        runCatching { setWinUIContent(DataPackage().apply { setText(text) }) }
    }

    internal fun getClipEntryBlocking(): ClipEntry? {
        lastPlainText?.let { return ClipEntry(it) }
        val content = runCatching { getWinUIContent() }
            .getOrElse {
                logClipboardReadFailure(it)
                return null
            }
        if (runCatching { content.availableFormats.isEmpty() }.getOrDefault(true)) return null
        return ClipEntry(content)
    }

    private suspend fun readClipEntry(): ClipEntry? {
        lastPlainText?.let { return ClipEntry(it) }
        val content = runCatching { getWinUIContent() }
            .getOrElse {
                logClipboardReadFailure(it)
                return null
            }
        if (runCatching { content.availableFormats.isEmpty() }.getOrDefault(true)) return null
        if (runCatching { content.contains(winUITextFormat) }.getOrDefault(false)) {
            return runCatching {
                ClipEntry(content.getTextAsync().await())
            }.getOrElse {
                logClipboardReadFailure(it)
                null
            }
        }
        return ClipEntry(content)
    }

    internal fun setClipEntryBlocking(clipEntry: ClipEntry?) {
        val dataPackage = when (val nativeClipEntry = clipEntry?.nativeClipEntry) {
            null -> {
                lastPlainText = null
                clearWinUIContent()
                return
            }
            is DataPackage -> nativeClipEntry
            is DataPackageView -> DataPackage().also { dataPackage ->
                if (nativeClipEntry.contains(winUITextFormat)) {
                    lastPlainText?.let(dataPackage::setText)
                }
            }
            is String -> DataPackage().apply {
                lastPlainText = nativeClipEntry
                setText(nativeClipEntry)
            }
            else -> return
        }
        if (clipEntry.nativeClipEntry !is String) {
            lastPlainText = null
        }
        runCatching { setWinUIContent(dataPackage) }
    }

    private fun clearWinUIContent() {
        runCatching { WinRTClipboardClass.clear() }
    }

    private fun setWinUIContent(dataPackage: DataPackage) {
        WinRTClipboardClass.setContent(dataPackage)
    }
}

actual class ClipEntry constructor(val nativeClipEntry: Any?) {
    actual val clipMetadata: ClipMetadata
        get() = ClipMetadata()

    @ExperimentalComposeUiApi
    fun getPlainText(): String? = when (val nativeClipEntry = nativeClipEntry) {
        is String -> nativeClipEntry
        else -> null
    }

    companion object {
        @ExperimentalComposeUiApi
        fun withPlainText(text: String): ClipEntry =
            ClipEntry(text)
    }
}

actual class ClipMetadata

private var lastPlainText: String? = null

private val winUIClipboardStatics: IUnknownReference
    get() = WinRTClipboardClass.StaticInterfaces.iClipboardStatics()

private val winUITextFormat: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
    WinRTStandardDataFormats.text
}

private fun getWinUIContent(): DataPackageView =
    WinRTClipboardClass.getContent()

private fun logClipboardReadFailure(throwable: Throwable) {
    System.err.println(
        "WinUIClipboard read failed: ${throwable::class.qualifiedName}: ${throwable.message}"
    )
}
