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
        val winuiMainBlock = checkNotNull(sourceSetBlock(buildScript, "winuiMain"))
        assertTrue(
            winuiMainBlock.contains("dependsOn(skikoHostMain)"),
            "WinUI should depend only on the shared Skiko host source set.",
        )
        assertFalse(
            winuiMainBlock.contains("dependsOn(skikoMain)") ||
                winuiMainBlock.contains("dependsOn(skikoRenderingMain)"),
            "WinUI must not depend on all skikoMain sources because skikoMain also has generic " +
                "or Desktop-backed actuals, and must not inherit skikoRenderingMain generic " +
                "platform actuals.",
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
            "src/skikoHostMain/kotlin/androidx/compose/ui/skiko/" +
                "RecordDrawRectRenderDecorator.skiko.kt"
        )
        val skikoHostBlock = checkNotNull(sourceSetBlock(buildScript, "skikoHostMain")) {
            "Could not find skikoHostMain in compose/ui/ui/build.gradle."
        }
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
            skikoHostBlock.contains("dependsOn(commonMain)") &&
                skikoHostBlock.contains("api(libs.skiko)"),
            "skikoHostMain should carry platform-neutral Skiko host sources and dependencies.",
        )
        assertTrue(
            skikoRenderingBlock.contains("dependsOn(skikoHostMain)"),
            "skikoRenderingMain should reuse the shared Skiko host source set.",
        )
        assertTrue(
            checkNotNull(sourceSetBlock(buildScript, "skikoMain"))
                .contains("dependsOn(skikoRenderingMain)"),
            "skikoMain should reuse the shared Skiko rendering source set.",
        )
        assertTrue(
            winuiMainBlock.contains("dependsOn(skikoHostMain)") &&
                !winuiMainBlock.contains("dependsOn(skikoRenderingMain)"),
            "winuiMain should reuse only the platform-neutral Skiko host source set.",
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

        // Keep the guard focused on Desktop/AWT native runtime artifacts.
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
    fun winuiWindowHandleUsesSharedGeneratedInteropProjection() {
        val moduleRoot = findUiModuleRoot()
        val sharedSource = moduleRoot.resolve(
            "src/winuiMain/kotlin/androidx/compose/ui/window/WinUIWindowInterop.winui.kt"
        ).readText()
        val jvmSource = moduleRoot.resolve(
            "src/winuiJvmMain/kotlin/androidx/compose/ui/window/WinUIWindowNative.winuiJvm.kt"
        ).readText()

        assertTrue(
            sharedSource.contains("import winrt.interop.WindowNative") &&
                sharedSource.contains("WindowNative.getWindowHandle(window)"),
            "Shared WinUI HWND lookup should use kotlin-winrt generated WindowNative interop.",
        )
        assertFalse(
            jvmSource.contains("winrt.interop.WindowNative") ||
                jvmSource.contains("WindowNative.getWindowHandle(window)"),
            "WinUI JVM native calls should reuse the shared HWND lookup.",
        )
        listOf(
            "ComVtableInvoker",
            "IWindowNativeIid",
            "queryInterface(IWindowNative",
        ).forEach { forbidden ->
            assertFalse(
                sharedSource.contains(forbidden),
                "WinUI HWND lookup should not manually query IWindowNative: found $forbidden.",
            )
        }
    }

    @Test
    fun winuiExplicitlyProjectsInputPaneSurface() {
        val buildScript = findUiModuleRoot().resolve("build.gradle").readText()

        listOf(
            "type(\"Windows.Foundation.Rect\")",
            "type(\"Windows.UI.ViewManagement.InputPane\")",
            "type(\"Windows.UI.ViewManagement.InputPaneVisibilityEventArgs\")",
        ).forEach { declaration ->
            assertTrue(
                buildScript.contains(declaration),
                "Missing WinRT projection: $declaration",
            )
        }
    }

    @Test
    fun winuiInputPaneUsesDesktopWindowInteropAndOwnerRegistration() {
        val moduleRoot = findUiModuleRoot()
        val jvmInteropFile = moduleRoot.resolve(
            "src/winuiJvmMain/kotlin/androidx/compose/ui/platform/WinUIInputPane.winuiJvm.kt"
        )
        assertTrue(jvmInteropFile.exists(), "WinUI JVM InputPane interop source is missing.")
        val jvmInteropSource = jvmInteropFile.readText()
        val composeViewSource = moduleRoot.resolve(
            "src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIComposeView.winui.kt"
        ).readText()

        listOf(
            "WindowNative.getWindowHandle(window)",
            "InputPaneInterop.getForWindow(windowHandle)",
        ).forEach { expected ->
            assertTrue(
                jvmInteropSource.contains(expected),
                "Desktop InputPane interop should contain $expected.",
            )
        }
        listOf(
            "createWinUIInputPaneController(",
            "registerInputPaneController(this, controller)",
            "unregisterInputPaneController(this)",
        ).forEach { expected ->
            assertTrue(
                composeViewSource.contains(expected),
                "Window-backed WinUIComposeView should contain $expected.",
            )
        }
        listOf(
            "ActivationFactory",
            "WinRTProjectionIntrinsic",
            "I_INPUT_PANE_INTEROP_IID",
            "InputPane.Metadata.DEFAULT_INTERFACE_IID",
            "getForCurrentView",
            "KWINRT-063",
            "ProcessBuilder",
            "rundll32",
            "powershell.exe",
        ).forEach { forbidden ->
            assertFalse(
                jvmInteropSource.contains(forbidden),
                "Compose InputPane acquisition must use the generated interop helper: found $forbidden.",
            )
        }
    }

    @Test
    fun winuiMingwKeepsDefaultNativeHierarchyWithoutNativeSkikoAncestry() {
        val repositoryRoot = findRepositoryRoot()
        val sourceSetBuildScripts = listOf(
            "compose/ui/ui/build.gradle",
            "compose/ui/ui-graphics/build.gradle",
            "compose/ui/ui-text/build.gradle",
            "compose/foundation/foundation/build.gradle",
            "compose/foundation/foundation-layout/build.gradle",
        )

        sourceSetBuildScripts.forEach { relativePath ->
            val buildScript = repositoryRoot.resolve(relativePath).readText()
            val nonJvmMainBlock = checkNotNull(sourceSetBlock(buildScript, "nonJvmMain")) {
                "Could not find nonJvmMain in $relativePath."
            }
            assertTrue(
                nonJvmMainBlock.contains("if (!composeWinUiTargetEnabled)") &&
                    nonJvmMainBlock.contains("dependsOn(skikoMain)"),
                "$relativePath must keep nonJvmMain -> skikoMain only outside WinUI mode.",
            )
        }

        val uiBuildScript = repositoryRoot.resolve("compose/ui/ui/build.gradle").readText()
        assertFalse(
            uiBuildScript.contains("detachWinUiMingwFromDefaultNativeHierarchy"),
            "WinUI MinGW must keep Kotlin's connected default Native hierarchy.",
        )
        val winuiMingwMainBlock = checkNotNull(
            sourceSetBlock(uiBuildScript, "winuiMingwMain"),
        )
        assertTrue(
            winuiMingwMainBlock.contains("dependsOn(winuiMain)"),
            "winuiMingwMain must add WinUI behavior to the default Native hierarchy.",
        )
        assertFalse(
            winuiMingwMainBlock.contains("dependsOn(commonMain)") ||
                winuiMingwMainBlock.contains("dependsOn(nativeMain)"),
            "winuiMingwMain should receive common/native ancestry from the hierarchy template.",
        )
    }

    @Test
    fun nativeComposeCompilerPluginClasspathIsAdditive() {
        val pluginSource = findRepositoryRoot().resolve(
            "buildSrc/private/src/main/kotlin/androidx/build/AndroidXComposeImplPlugin.kt"
        ).readText()

        assertTrue(
            pluginSource.contains("compilerPluginClasspath?.plus(plugins) ?: plugins"),
            "Compose must append its plugin to an existing Kotlin/Native plugin classpath.",
        )
        assertFalse(
            pluginSource.contains("compilerPluginClasspath = plugins"),
            "Compose must not replace kotlin-winrt or other Kotlin/Native compiler plugins.",
        )
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

    private fun findRepositoryRoot(): Path {
        val start = Paths.get("").toAbsolutePath()
        generateSequence(start) { it.parent }.forEach { candidate ->
            if (
                candidate.resolve("buildSrc").exists() &&
                candidate.resolve("compose/ui/ui/build.gradle").exists()
            ) {
                return candidate
            }
        }
        error("Could not find repository root from $start.")
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
