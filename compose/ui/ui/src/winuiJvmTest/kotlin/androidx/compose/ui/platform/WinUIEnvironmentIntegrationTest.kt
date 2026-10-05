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

import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class WinUIEnvironmentIntegrationTest {
    @Test
    fun composeRootProvidesEnvironmentAndMotionScale() {
        val source = winUIComposeViewSource()

        assertTrue(source.contains("LocalSystemTheme provides systemEnvironment.systemTheme"))
        assertTrue(source.contains("owner.updateLayoutDirection(environment.layoutDirection)"))
        assertTrue(source.contains("Density(owner.density.density, environment.fontScale)"))
        assertTrue(source.contains("motionDurationScale.updateAnimationsEnabled"))
        assertTrue(source.contains("FrameRecomposer(dispatcher + motionDurationScale"))
    }

    @Test
    fun composeRootDrivesLifecycleFromBindingFocusAndDispose() {
        val source = winUIComposeViewSource()

        assertTrue(source.contains("WinUIViewLifecycleController("))
        assertTrue(source.contains("WinUIRootLifecycleBinding("))
        assertTrue(source.contains("WinUIWindowActivationBinding("))
        assertTrue(source.contains("fun setHostActive(isActive: Boolean)"))
        assertTrue(source.contains("lifecycleController.setActive(isActive)"))
        assertTrue(source.contains("windowActivationBinding?.close()"))
        assertTrue(source.contains("lifecycleBinding.close()"))
    }

    @Test
    fun windowBackedViewOwnsActivationBindingAndRawSetContentUsesIt() {
        val source = winUIComposeViewSource()
        val rawSetContent = source.substringAfter(
            "fun Window.setContent(content: @Composable () -> Unit): WinUIComposeView",
        )

        assertTrue(source.contains("window?.let { WinUIWindowActivationBinding(it, ::setHostActive) }"))
        assertTrue(rawSetContent.contains("window = this"))
    }

    @Test
    fun environmentObserverIsInitializedAfterCallbackState() {
        val source = winUIComposeViewSource()
        val observerIndex = source.indexOf("private val environmentObserver")

        assertTrue(observerIndex > source.indexOf("private var isRootContentSyncScheduled"))
        assertTrue(observerIndex > source.indexOf("private var hasPendingRenderRequest"))
        assertTrue(observerIndex > source.indexOf("private var isDisposed"))
    }

    private fun winUIComposeViewSource(): String =
        findWinUIUiModuleRoot()
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIComposeView.winui.kt")
            .readText()
}
