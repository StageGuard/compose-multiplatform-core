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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUISourceSetIsolationTest {
    @Test
    fun winuiSourceSetsDoNotReferenceDesktopAwtSwingOrSkiaLayer() {
        val moduleRoot = findUiModuleRoot()
        val forbiddenReferences = listOf(
            "java.awt.",
            "javax.swing.",
            "androidx.compose.ui.awt.",
            "org.jetbrains.skiko.SkiaLayer",
        )
        val offenders = listOf("winuiMain", "winuiJvmMain").flatMap { sourceSet ->
            kotlinFiles(moduleRoot.resolve("src/$sourceSet/kotlin")).flatMap { file ->
                val text = file.readText()
                forbiddenReferences.mapNotNull { reference ->
                    if (text.contains(reference)) {
                        "${moduleRoot.relativize(file)} references $reference"
                    } else {
                        null
                    }
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "WinUI source sets must stay independent from Desktop/AWT/Swing/SkiaLayer:\n" +
                offenders.joinToString(separator = "\n"),
        )
    }

    @Test
    fun winuiSourceSetsDoNotDependOnDesktopMain() {
        val moduleRoot = findUiModuleRoot()
        val buildScript = moduleRoot.resolve("build.gradle").readText()
        val forbiddenSourceSets = listOf("desktopMain", "desktopJvmMain")

        listOf("winuiMain", "winuiJvmMain").forEach { sourceSet ->
            val block = checkNotNull(sourceSetBlock(buildScript, sourceSet)) {
                "Could not find $sourceSet in compose/ui/ui/build.gradle."
            }
            forbiddenSourceSets.forEach { forbidden ->
                assertFalse(
                    block.contains("dependsOn($forbidden)"),
                    "WinUI source sets must not depend on desktop source sets: " +
                        "$sourceSet depends on $forbidden.",
                )
            }
        }
        assertTrue(
            checkNotNull(sourceSetBlock(buildScript, "winuiMain"))
                .contains("dependsOn(skikoRenderingMain)"),
            "WinUI should depend on the shared Skiko rendering source set.",
        )
        assertFalse(
            checkNotNull(sourceSetBlock(buildScript, "winuiMain"))
                .contains("dependsOn(skikoMain)"),
            "WinUI must not depend on all skikoMain sources because skikoMain also has generic " +
                "or Desktop-backed actuals.",
        )
    }

    @Test
    fun winuiMainDoesNotReferenceTargetSpecificRuntimeDetails() {
        val moduleRoot = findUiModuleRoot()
        val forbiddenReferences = listOf(
            "java.",
            "javax.",
            "java.lang.foreign.",
            "WinRTWindowsAppSdkBootstrap",
            "RuntimeScope",
            "JavaExec",
            "stageWinRT",
            "buildWinRT",
            "System.getProperty",
            "System.load",
            "Class.forName",
        )
        val offenders = kotlinFiles(moduleRoot.resolve("src/winuiMain/kotlin")).flatMap { file ->
            val text = file.readText()
            forbiddenReferences.mapNotNull { reference ->
                if (text.contains(reference)) {
                    "${moduleRoot.relativize(file)} references $reference"
                } else {
                    null
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "winuiMain must keep shared Compose/WinUI behavior only; target-specific " +
                "runtime, JVM, FFM, JavaExec, and host-staging details belong in " +
                "winuiJvmMain or sample launchers:\n" +
                offenders.joinToString(separator = "\n"),
        )
    }

    @Test
    fun winuiUsesSourceSetSplitForSharedSkikoRenderingSource() {
        val moduleRoot = findUiModuleRoot()
        val buildScript = moduleRoot.resolve("build.gradle").readText()
        val sharedSource = moduleRoot.resolve(
            "src/skikoRenderingMain/kotlin/androidx/compose/ui/skiko/" +
                "RecordDrawRectRenderDecorator.skiko.kt"
        )
        val skikoRenderingBlock = checkNotNull(sourceSetBlock(buildScript, "skikoRenderingMain")) {
            "Could not find skikoRenderingMain in compose/ui/ui/build.gradle."
        }
        val winuiMainBlock = checkNotNull(sourceSetBlock(buildScript, "winuiMain")) {
            "Could not find winuiMain in compose/ui/ui/build.gradle."
        }

        assertTrue(
            sharedSource.exists(),
            "Shared Skiko rendering source should live in skikoRenderingMain.",
        )
        assertTrue(
            skikoRenderingBlock.contains("dependsOn(commonMain)") &&
                skikoRenderingBlock.contains("api(libs.skiko)"),
            "skikoRenderingMain should carry shared Skiko API sources and dependencies.",
        )
        assertTrue(
            checkNotNull(sourceSetBlock(buildScript, "skikoMain"))
                .contains("dependsOn(skikoRenderingMain)"),
            "skikoMain should reuse the shared Skiko rendering source set.",
        )
        assertTrue(
            winuiMainBlock.contains("dependsOn(skikoRenderingMain)"),
            "winuiMain should reuse the shared Skiko rendering source set.",
        )
        assertTrue(
            buildScript.contains("generated/kotlin-winrt/src/commonMain/kotlin") &&
                buildScript.contains("generated/kotlin-winrt-authoring/src/commonMain/kotlin") &&
                buildScript.contains("task.dependsOn(\"generateWinRTProjections\")"),
            "Generated WinRT sources should remain wired by the kotlin-winrt plugin and task dependency.",
        )
        assertFalse(
            buildScript.contains("task.setSource(project.files("),
            "WinUI JVM compilation should use source-set dependencies, not a task source override.",
        )
    }

    @Test
    fun winuiJvmRuntimeUsesSkikoWinuiWithoutSkikoAwtNativeRuntime() {
        val classpath = System.getProperty("java.class.path")
            .split(System.getProperty("path.separator"))
            .map { it.lowercase() }

        assertTrue(
            classpath.any { it.contains("skiko-winui") },
            "WinUI JVM runtime classpath should include skiko-winui.",
        )

        // SKIKO-006: the current JVM Skiko API jar is still named skiko-awt,
        // so keep the guard focused on Desktop/AWT native runtime artifacts.
        val forbiddenArtifacts = listOf(
            "skiko-awt-runtime",
        )
        val offenders = classpath.filter { entry ->
            forbiddenArtifacts.any { artifact -> entry.contains(artifact) }
        }

        assertTrue(
            offenders.isEmpty(),
            "WinUI JVM runtime classpath must not include Skiko AWT/Desktop native runtime artifacts:\n" +
                offenders.joinToString(separator = "\n"),
        )
    }

    @Test
    fun winuiJvmWindowHandleUsesGeneratedInteropProjection() {
        val moduleRoot = findUiModuleRoot()
        val source = moduleRoot.resolve(
            "src/winuiJvmMain/kotlin/androidx/compose/ui/window/WinUIWindowNative.winuiJvm.kt"
        ).readText()

        assertTrue(
            source.contains("import winrt.interop.WindowNative") &&
                source.contains("WindowNative.getWindowHandle(window)"),
            "WinUI HWND lookup should use kotlin-winrt generated WindowNative interop.",
        )
        listOf(
            "ComVtableInvoker",
            "IWindowNativeIid",
            "queryInterface(IWindowNative",
        ).forEach { forbidden ->
            assertFalse(
                source.contains(forbidden),
                "WinUI HWND lookup should not manually query IWindowNative: found $forbidden.",
            )
        }
    }

    private fun kotlinFiles(root: Path): List<Path> {
        if (!root.exists()) return emptyList()
        Files.walk(root).use { paths ->
            return paths
                .filter { it.extension == "kt" }
                .toList()
        }
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

    private fun sourceSetBlock(buildScript: String, sourceSet: String): String? {
        val start = buildScript.indexOf("$sourceSet {")
        if (start < 0) return null
        var depth = 0
        for (index in start until buildScript.length) {
            when (buildScript[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return buildScript.substring(start, index + 1)
                }
            }
        }
        return null
    }

}
