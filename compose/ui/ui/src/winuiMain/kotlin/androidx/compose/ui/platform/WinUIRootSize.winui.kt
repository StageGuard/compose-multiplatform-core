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

import androidx.compose.ui.unit.IntSize
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.SizeChangedEventHandler

internal data class WinUIRootSizeSnapshot(
    val xamlSize: WinUIXamlSize,
    val rasterizationScale: Float?,
)

internal interface WinUIRootSizeSource {
    fun register(onChanged: () -> Unit): () -> Unit

    fun readSnapshot(): WinUIRootSizeSnapshot
}

internal class WinUIRootSizeBinding(
    private val source: WinUIRootSizeSource,
    private val onSizeChanged: (IntSize) -> Unit,
) : AutoCloseable {
    constructor(
        root: FrameworkElement,
        onSizeChanged: (IntSize) -> Unit,
    ) : this(FrameworkElementWinUIRootSizeSource(root), onSizeChanged)

    private var isClosed = false
    private var unregisterAction: (() -> Unit)? = runCatching {
        source.register {
            if (!isClosed) refresh()
        }
    }.getOrNull()

    init {
        refresh()
    }

    fun refresh(): Boolean {
        if (isClosed) return false
        val snapshot = runCatching { source.readSnapshot() }.getOrNull() ?: return false
        val xamlSize = snapshot.xamlSize
        val rasterizationScale = snapshot.rasterizationScale
        if (
            !xamlSize.width.isFinite() || xamlSize.width <= 0.0 ||
            !xamlSize.height.isFinite() || xamlSize.height <= 0.0 ||
            rasterizationScale == null ||
            !rasterizationScale.isFinite() || rasterizationScale <= 0f
        ) {
            return false
        }
        val size = xamlSize.toComposePixelSize(rasterizationScale)
        if (size.width <= 0 || size.height <= 0) return false
        onSizeChanged(size)
        return true
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        val unregister = unregisterAction
        unregisterAction = null
        if (unregister != null) {
            runCatching { unregister() }
        }
    }
}

private class FrameworkElementWinUIRootSizeSource(
    private val root: FrameworkElement,
) : WinUIRootSizeSource {
    override fun register(onChanged: () -> Unit): () -> Unit {
        val sizeChangedHandler: SizeChangedEventHandler = { _, _ -> onChanged() }
        val loadedHandler: RoutedEventHandler = { _, _ -> onChanged() }
        val sizeChangedToken = root.sizeChanged.add(sizeChangedHandler)
        val loadedToken = root.loaded.add(loadedHandler)
        return {
            runCatching { root.sizeChanged.remove(sizeChangedToken) }
            runCatching { root.loaded.remove(loadedToken) }
        }
    }

    override fun readSnapshot(): WinUIRootSizeSnapshot = WinUIRootSizeSnapshot(
        xamlSize = WinUIXamlSize(
            width = runCatching { root.actualWidth }.getOrDefault(0.0),
            height = runCatching { root.actualHeight }.getOrDefault(0.0),
        ),
        rasterizationScale = runCatching {
            root.xamlRoot?.rasterizationScale?.toFloat()
        }.getOrNull(),
    )
}
