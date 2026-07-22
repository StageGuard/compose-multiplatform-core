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
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIRootSizeTest {
    @Test
    fun bindingPublishesActualXamlSizeInPhysicalPixels() {
        val sizes = mutableListOf<IntSize>()
        val source = FakeWinUIRootSizeSource(
            WinUIRootSizeSnapshot(
                xamlSize = WinUIXamlSize(width = 1800.0, height = 900.0),
                rasterizationScale = 2.5f,
            )
        )
        val binding = WinUIRootSizeBinding(source, sizes::add)

        source.current = WinUIRootSizeSnapshot(
            xamlSize = WinUIXamlSize(width = 1830.4, height = 947.2),
            rasterizationScale = 2.5f,
        )
        source.emitChange()

        assertEquals(
            listOf(IntSize(4500, 2250), IntSize(4576, 2368)),
            sizes,
        )

        binding.close()
    }

    @Test
    fun bindingWaitsForUsableXamlRootScaleBeforePublishingSize() {
        val sizes = mutableListOf<IntSize>()
        val source = FakeWinUIRootSizeSource(
            WinUIRootSizeSnapshot(
                xamlSize = WinUIXamlSize(width = 0.0, height = 0.0),
                rasterizationScale = 2f,
            )
        )
        val binding = WinUIRootSizeBinding(source, sizes::add)

        source.current = WinUIRootSizeSnapshot(
            xamlSize = WinUIXamlSize(width = Double.NaN, height = 100.0),
            rasterizationScale = 2f,
        )
        source.emitChange()
        assertTrue(sizes.isEmpty())

        source.current = WinUIRootSizeSnapshot(
            xamlSize = WinUIXamlSize(width = 100.0, height = 60.0),
            rasterizationScale = Float.NaN,
        )
        source.emitChange()
        assertTrue(sizes.isEmpty())

        source.current = source.current.copy(rasterizationScale = 1f)
        source.emitChange()
        assertEquals(listOf(IntSize(100, 60)), sizes)

        binding.close()
    }

    @Test
    fun refreshRecomputesPhysicalSizeWhenRasterizationScaleChanges() {
        val sizes = mutableListOf<IntSize>()
        val source = FakeWinUIRootSizeSource(
            WinUIRootSizeSnapshot(
                xamlSize = WinUIXamlSize(width = 100.0, height = 50.0),
                rasterizationScale = 1f,
            )
        )
        val binding = WinUIRootSizeBinding(source, sizes::add)

        source.current = source.current.copy(rasterizationScale = 1.5f)
        assertTrue(binding.refresh())

        assertEquals(listOf(IntSize(100, 50), IntSize(150, 75)), sizes)

        binding.close()
    }

    @Test
    fun closeUnregistersOnceAndMakesCallbacksInert() {
        val sizes = mutableListOf<IntSize>()
        val source = FakeWinUIRootSizeSource(
            WinUIRootSizeSnapshot(
                xamlSize = WinUIXamlSize(width = 100.0, height = 50.0),
                rasterizationScale = 1f,
            )
        )
        val binding = WinUIRootSizeBinding(source, sizes::add)

        binding.close()
        binding.close()
        source.current = source.current.copy(
            xamlSize = WinUIXamlSize(width = 200.0, height = 100.0),
        )
        source.emitChange()

        assertEquals(listOf(IntSize(100, 50)), sizes)
        assertEquals(1, source.unregisterCount)
        assertFalse(binding.refresh())
    }

    @Test
    fun productionWiringUsesXamlRootSizeInsteadOfAppWindowSize() {
        val moduleRoot = findWinUIUiModuleRoot()
        val source = moduleRoot
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIRootSize.winui.kt")
            .readText()
            .filterNot { it.isWhitespace() }
        val composeViewRaw = moduleRoot
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIComposeView.winui.kt")
            .readText()
        val composeView = composeViewRaw
            .filterNot { it.isWhitespace() }
        val flushPendingRenderRequest = composeViewRaw
            .substringAfter("private fun flushPendingRenderRequest()")
            .substringBefore("private fun scheduleRenderRequestFlush()")
            .filterNot { it.isWhitespace() }
        val setContentWhenWindowReady = composeViewRaw
            .substringAfter("internal fun setContentWhenWindowReady(")
            .substringBefore("internal fun setContent(")
            .filterNot { it.isWhitespace() }
        val setWindowContainerSizeFromRoot = composeViewRaw
            .substringAfter("private fun setWindowContainerSizeFromRoot(")
            .substringBefore("private fun applyWindowContainerSize(")
            .filterNot { it.isWhitespace() }
        val setWindowBootstrapSize = composeViewRaw
            .substringAfter("internal fun setWindowBootstrapSize(size: IntSize)")
            .substringBefore("private fun setWindowContainerSizeFromRoot(")
            .filterNot { it.isWhitespace() }
        val window = moduleRoot
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/window/Window.winui.kt")
            .readText()
        val updateWindowInfo = window
            .substringAfter("private fun updateWindowInfo()")
            .substringBefore("private fun updateCaptureProtection")

        listOf(
            "root.sizeChanged.add(",
            "root.sizeChanged.remove(",
            "root.loaded.add(",
            "root.loaded.remove(",
            "root.actualWidth",
            "root.actualHeight",
            "root.xamlRoot",
        ).forEach { nativeCall ->
            assertTrue(source.contains(nativeCall), "Missing native root size call: $nativeCall")
        }
        assertTrue(composeView.contains("WinUIRootSizeBinding("))
        assertTrue(composeView.contains("rootSizeBinding.refresh()"))
        assertTrue(composeView.contains("rootSizeBinding.close()"))
        assertTrue(composeView.contains("setContentWhenWindowReady("))
        assertTrue(setContentWhenWindowReady.contains("setContent(content)"))
        assertFalse(setContentWhenWindowReady.contains("pendingWindowContent"))
        assertFalse(setWindowContainerSizeFromRoot.contains("consumePendingWindowContent()"))
        assertTrue(setWindowBootstrapSize.contains("if(isDisposed||rootContentControl.isLoaded)return"))
        assertTrue(setWindowBootstrapSize.contains("applyWindowContainerSize(size,requestRender=false)"))
        assertTrue(composeView.contains("if(content!=null){scheduleRootContentSync()requestRender()}"))
        assertTrue(flushPendingRenderRequest.contains("if(!rootContentControl.isLoaded){"))
        assertTrue(window.contains("view.setContentWhenWindowReady"))
        assertTrue(updateWindowInfo.contains("view.setWindowBootstrapSize("))
        assertTrue(
            window.indexOf("view.setContentWhenWindowReady") < window.indexOf("window.activate()"),
            "Window content must be installed before activation begins XAML layout.",
        )
        assertFalse(updateWindowInfo.contains("setWindowContainerSize"))
    }
}

private class FakeWinUIRootSizeSource(
    var current: WinUIRootSizeSnapshot,
) : WinUIRootSizeSource {
    private var listener: (() -> Unit)? = null
    var unregisterCount = 0
        private set

    override fun register(onChanged: () -> Unit): () -> Unit {
        listener = onChanged
        return { unregisterCount += 1 }
    }

    override fun readSnapshot(): WinUIRootSizeSnapshot = current

    fun emitChange() {
        listener?.invoke()
    }
}
