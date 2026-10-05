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

package androidx.compose.ui

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIDelayedPostSourceSetTest {
    @Test
    fun delayedPostUsesSharedWinUIDispatcherQueueTimer() {
        val moduleRoot = findUiModuleRoot()
        val sharedActual = moduleRoot.resolve(
            "src/winuiMain/kotlin/androidx/compose/ui/Actuals.winui.kt"
        )
        val jvmActual = moduleRoot.resolve(
            "src/winuiJvmMain/kotlin/androidx/compose/ui/Actuals.winuiJvm.kt"
        )
        val dispatchQueue = moduleRoot.resolve(
            "src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIDispatchQueue.winui.kt"
        ).readText()

        assertTrue(sharedActual.exists(), "WinUI delayed-post actuals should live in winuiMain.")
        assertFalse(jvmActual.exists(), "WinUI delayed-post actuals should not require the JVM.")
        assertTrue(
            dispatchQueue.contains("DispatcherQueueTimer") &&
                dispatchQueue.contains("fun postDelayed("),
            "WinUI delayed posts should use DispatcherQueueTimer.",
        )
        assertFalse(
            sharedActual.readText().contains("java.util.concurrent"),
            "Shared WinUI delayed posts must not depend on JVM executors.",
        )
    }

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
