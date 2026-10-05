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

private const val UiBuildScript = "build-fork.gradle"

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
        val offenders = listOf("winuiMain", "winuiJvmMain", "winuiMingwMain").flatMap { sourceSet ->
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
        val buildScript = uiBuildScript()
        val forbiddenSourceSets = listOf("desktopMain", "desktopJvmMain")

        listOf("winuiMain", "winuiJvmMain").forEach { sourceSet ->
            val block = checkNotNull(sourceSetBlock(buildScript, sourceSet)) {
                "Could not find $sourceSet in $UiBuildScript."
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
            winuiMainBlock.contains("dependsOn(skikoRenderingMain)"),
            "WinUI should depend on the Skiko source set that it shares with skikoMain.",
        )
        assertFalse(
            winuiMainBlock.contains("dependsOn(skikoMain)"),
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
        val buildScript = uiBuildScript()
        val hostSource = moduleRoot.resolve(
            "src/skikoHostMain/kotlin/androidx/compose/ui/skiko/" +
                "RecordDrawRectRenderDecorator.skiko.kt"
        )
        val renderingSource = moduleRoot.resolve(
            "src/skikoRenderingMain/kotlin/androidx/compose/ui/node/" +
                "GraphicsLayerOwnerLayer.skiko.kt"
        )
        val skikoHostBlock = checkNotNull(sourceSetBlock(buildScript, "skikoHostMain")) {
            "Could not find skikoHostMain in $UiBuildScript."
        }
        val skikoRenderingBlock = checkNotNull(sourceSetBlock(buildScript, "skikoRenderingMain")) {
            "Could not find skikoRenderingMain in $UiBuildScript."
        }
        val winuiMainBlock = checkNotNull(sourceSetBlock(buildScript, "winuiMain")) {
            "Could not find winuiMain in $UiBuildScript."
        }

        assertTrue(hostSource.exists(), "Shared Skiko host source should live in skikoHostMain.")
        assertTrue(
            renderingSource.exists(),
            "The owned layer that WinUI shares with Skiko should live in skikoRenderingMain.",
        )
        assertTrue(
            skikoHostBlock.contains("dependsOn(commonMain)") &&
                skikoHostBlock.contains("api(project(\":compose:ui:ui-skiko\"))"),
            "skikoHostMain should carry platform-neutral Skiko host sources and dependencies.",
        )
        assertTrue(
            skikoRenderingBlock.contains("dependsOn(skikoHostMain)"),
            "skikoRenderingMain should reuse the shared Skiko host source set.",
        )
        assertTrue(
            // skikoMain is configured in more than one place of the script.
            Regex("""skikoMain \{\s*dependsOn\(skikoRenderingMain\)""")
                .containsMatchIn(buildScript),
            "skikoMain should reuse the shared Skiko rendering source set.",
        )
        assertTrue(
            winuiMainBlock.contains("dependsOn(skikoRenderingMain)") &&
                !winuiMainBlock.contains("dependsOn(skikoMain)"),
            "winuiMain should reuse the shared Skiko source sets, not skikoMain.",
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
        val buildScript = uiBuildScript()

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
    fun winuiInputPaneUsesSharedWindowInteropAndOwnerRegistration() {
        val moduleRoot = findUiModuleRoot()
        val sharedInteropFile = moduleRoot.resolve(
            "src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIInputPane.winui.kt"
        )
        val jvmInteropFile = moduleRoot.resolve(
            "src/winuiJvmMain/kotlin/androidx/compose/ui/platform/WinUIInputPane.winuiJvm.kt"
        )
        assertFalse(
            jvmInteropFile.exists(),
            "Generated InputPane window interop is shared by WinUI JVM and MinGW.",
        )
        val sharedInteropSource = sharedInteropFile.readText()
        val composeViewSource = moduleRoot.resolve(
            "src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIComposeView.winui.kt"
        ).readText()

        listOf(
            "WindowNative.getWindowHandle(window)",
            "InputPaneInterop.getForWindow(windowHandle)",
        ).forEach { expected ->
            assertTrue(
                sharedInteropSource.contains(expected),
                "Shared InputPane interop should contain $expected.",
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
            "getForCurrentView",
            "java.lang.foreign",
            "ProcessBuilder",
            "rundll32",
            "powershell.exe",
        ).forEach { forbidden ->
            assertFalse(
                sharedInteropSource.contains(forbidden),
                "Compose InputPane acquisition must stay a portable desktop interop: found $forbidden.",
            )
        }
    }

    @Test
    fun skikoOnlyNativeActualsAreOutsideWinuiNativeFragments() {
        val moduleRoot = findUiModuleRoot()
        val buildScript = uiBuildScript()
        val nonJvmActuals = moduleRoot.resolve(
            "src/nonJvmMain/kotlin/androidx/compose/ui/Actuals.nonJvm.kt"
        ).readText()
        val skikoNonJvmActuals = moduleRoot.resolve(
            "src/skikoNonJvmMain/kotlin/androidx/compose/ui/Actuals.skikoNonJvm.kt"
        )
        val nativeThreading = moduleRoot.resolve(
            "src/nativeMain/kotlin/androidx/compose/ui/internal/Threading.native.kt"
        )
        val skikoNativeThreading = moduleRoot.resolve(
            "src/skikoNativeMain/kotlin/androidx/compose/ui/internal/Threading.skikoNative.kt"
        )

        assertFalse(nonJvmActuals.contains("PostDelayedDispatcher"))
        assertTrue(skikoNonJvmActuals.exists())
        assertFalse(nativeThreading.exists())
        assertTrue(skikoNativeThreading.exists())

        listOf("skikoNonJvmMain", "skikoNativeMain").forEach { sourceSet ->
            val block = checkNotNull(sourceSetBlock(buildScript, sourceSet)) {
                "Could not find $sourceSet in $UiBuildScript."
            }
            assertTrue(block.contains("dependsOn(skikoMain)"))
        }
        assertTrue(buildScript.contains("dependsOn(skikoNonJvmMain)"))
        assertTrue(buildScript.contains("dependsOn(skikoNativeMain)"))
    }

    @Test
    fun winuiMingwNativeHooksUseKotlinNativeWindowsApis() {
        val moduleRoot = findUiModuleRoot()
        val filesWithRequiredSymbols = mapOf(
            "src/winuiMingwMain/kotlin/androidx/compose/ui/platform/" +
                "PlatformActuals.winuiMingw.kt" to listOf(
                "kotlinx.atomicfu.locks.SynchronizedObject",
                "kotlinx.atomicfu.locks.synchronized",
            ),
            "src/winuiMingwMain/kotlin/androidx/compose/ui/" +
                "ComposeFeatureFlags.winuiMingw.kt" to listOf(
                "winUIProcessProperty(\"compose.layers.type\")",
            ),
            "src/winuiMingwMain/kotlin/androidx/compose/ui/platform/" +
                "WinUIPlatformProperties.winuiMingw.kt" to listOf(
                "getenv(name)",
                "fopen(logFile, \"a\")",
                "fputs",
            ),
            "src/winuiMingwMain/kotlin/androidx/compose/ui/window/" +
                "WinUIWindowNative.winuiMingw.kt" to listOf(
                "DwmEnableBlurBehindWindow",
                "DwmSetWindowAttribute",
                "CreateRectRgn",
                "DeleteObject",
            ),
            "src/winuiMingwMain/kotlin/androidx/compose/ui/window/" +
                "WindowCaptureProtection.winuiMingw.kt" to listOf(
                "SetWindowDisplayAffinity",
                "WDA_EXCLUDEFROMCAPTURE",
            ),
        )

        filesWithRequiredSymbols.forEach { (relativePath, requiredSymbols) ->
            val file = moduleRoot.resolve(relativePath)
            assertTrue(file.exists(), "Missing WinUI MinGW implementation: $relativePath")
            val source = file.readText()
            requiredSymbols.forEach { symbol ->
                assertTrue(source.contains(symbol), "$relativePath must use $symbol")
            }
            listOf("ProcessBuilder", "rundll32", "powershell.exe", "TODO").forEach { forbidden ->
                assertFalse(source.contains(forbidden), "$relativePath contains $forbidden")
            }
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

    /** The WinUI targets are wired in the build script of the fork. */
    private fun uiBuildScript(): String = findUiModuleRoot().resolve(UiBuildScript).readText()

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
