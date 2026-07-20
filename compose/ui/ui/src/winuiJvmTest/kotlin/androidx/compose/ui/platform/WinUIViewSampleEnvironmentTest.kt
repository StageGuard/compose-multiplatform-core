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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIViewSampleEnvironmentTest {
    @Test
    fun sampleLocalsAreSafeWhileRootIsAttachingAndCaptureEffectiveEnvironment() {
        val source = sampleSource()

        assertTrue(source.contains("fontScale.isFinite() && fontScale > 0f"))
        assertTrue(source.contains("Lifecycle.State.CREATED"))
        assertFalse(
            source.contains(
                "LocalLifecycleOwner.current.lifecycle.currentState == Lifecycle.State.RESUMED"
            )
        )
        assertFalse(source.contains("LocalLayoutDirection.current == LayoutDirection.Ltr"))
        assertTrue(source.contains("private object WinUIEnvironmentSmokeState"))
        assertTrue(source.contains("LocalSystemTheme.current"))
        assertTrue(source.contains("LocalLayoutDirection.current"))
        assertTrue(source.contains("LocalDensity.current.fontScale"))
    }

    @Test
    fun windowSampleExercisesThemeFlowDirectionAndLifecycleWithConditionWaits() {
        val source = sampleSource()

        listOf(
            "requestedTheme",
            "FlowDirection.RightToLeft",
            "WinUI lifecycle RESUMED",
            "compose-winui-sample: system environment and lifecycle",
            "awaitCondition(",
            "setHostActive(true)",
        ).forEach { required ->
            assertTrue(source.contains(required), "Missing sample environment smoke: $required")
        }
    }

    private fun sampleSource(): String {
        val start = Paths.get("").toAbsolutePath()
        generateSequence(start) { it.parent }.forEach { candidate ->
            val direct = candidate.resolve(
                "winui-samples/src/main/kotlin/androidx/compose/ui/winui/samples/WinUIViewSample.kt"
            )
            if (direct.exists()) return direct.readText()

            val fromRepoRoot = candidate.resolve(
                "compose/ui/ui/winui-samples/src/main/kotlin/" +
                    "androidx/compose/ui/winui/samples/WinUIViewSample.kt"
            )
            if (fromRepoRoot.exists()) return fromRepoRoot.readText()
        }
        error("Could not find WinUIViewSample.kt from $start")
    }
}
