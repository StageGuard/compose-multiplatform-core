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

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class WinUIComposeViewDisposalTest {
    @Test
    fun accessibilityProviderLifecycleIsDrivenByComposeView() {
        val source = winUIComposeViewSource()
        val providerInstallation =
            source
                .substringAfter("renderHost.setAccessibilityProvider(owner.accessibilityProvider)")
                .substringBefore("WinUIPlatformTextInputService.registerRootToScreenMapper")

        assertTrue(
            providerInstallation.contains("owner.onAccessibilityProviderAttached()"),
            "Installing the production provider must attach accessibility without a snapshot read.",
        )

        val disposeBody =
            source
                .substringAfter("fun dispose() {")
                .substringBefore("\n    internal fun setWindowFocused")
        val detachIndex = disposeBody.indexOf("owner.onAccessibilityProviderDetached()")
        val ownerDisposeIndex = disposeBody.indexOf("owner.dispose()")
        assertTrue(
            detachIndex >= 0 && detachIndex < ownerDisposeIndex,
            "WinUIComposeView must detach the provider before disposing its owner.",
        )
    }

    @Test
    fun disposeAggregatesEveryCleanupStep() {
        val source = winUIComposeViewSource()
        val disposeBody =
            source
                .substringAfter("fun dispose() {")
                .substringBefore("\n    internal fun setWindowFocused")
        val cleanupBody =
            disposeBody
                .substringAfter("runWinUIDragAndDropCleanup(", missingDelimiterValue = "")
                .substringBefore("\n        )")

        assertTrue(
            cleanupBody.isNotEmpty(),
            "WinUIComposeView.dispose must aggregate cleanup failures so later resources release.",
        )

        val cleanupSteps =
            listOf(
                "disposeComposition()",
                "keyInputAdapter.dispose()",
                "pointerInputAdapter.dispose()",
                "dragAndDropAdapter.dispose()",
                "pointerCursorAdapter.dispose()",
                "retainedValuesStore.dispose()",
                "architectureComponentsOwner.setLifecycleState(Lifecycle.State.DESTROYED)",
                "clearXamlRootDensityObserver()",
                "WinUIPlatformTextInputService.unregisterRootToScreenMapper(this)",
                "owner.onAccessibilityProviderDetached()",
                "owner.dispose()",
                "clearLoadedRenderSchedulerRequest()",
                "renderHost.close()",
            )
        var previousIndex = -1
        cleanupSteps.forEach { step ->
            val index = cleanupBody.indexOf(step)
            assertTrue(index > previousIndex, "Missing or reordered aggregated cleanup step: $step")
            previousIndex = index
        }
    }

    private fun winUIComposeViewSource(): String =
        findUiModuleRoot()
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIComposeView.winui.kt")
            .readText()

    private fun findUiModuleRoot(): Path {
        val start = Paths.get("").toAbsolutePath()
        generateSequence(start) { it.parent }.forEach { candidate ->
            val direct = candidate.resolve("src/winuiMain/kotlin")
            if (direct.exists() && candidate.name == "ui") return candidate

            val fromRepoRoot = candidate.resolve("compose/ui/ui/src/winuiMain/kotlin")
            if (fromRepoRoot.exists()) return candidate.resolve("compose/ui/ui")
        }
        error("Could not find compose/ui/ui module root from $start.")
    }
}
