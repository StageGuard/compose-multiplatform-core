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
    fun winuiUsesKotlinWinrtCompositeSubmodule() {
        val repositoryRoot = findRepositoryRoot()
        val gitmodules = repositoryRoot.resolve(".gitmodules")
        val settings = repositoryRoot.resolve("settings.gradle").readText()

        assertTrue(gitmodules.exists(), "kotlin-winrt must be a repository submodule.")
        assertTrue(
            gitmodules.readText().contains("path = external/kotlin-winrt"),
            "The kotlin-winrt submodule must live at external/kotlin-winrt.",
        )
        assertTrue(
            settings.contains("external/kotlin-winrt/winrt-gradle-plugin"),
            "Plugin resolution must prefer the local kotlin-winrt composite.",
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
                sharedInteropSource.contains(forbidden),
                "Compose InputPane acquisition must use the generated interop helper: found $forbidden.",
            )
        }
    }

    @Test
    fun winuiMingwKeepsDefaultNativeHierarchyWithoutNativeSkikoAncestry() {
        val repositoryRoot = findRepositoryRoot()
        val sourceSetBuildScripts = listOf("compose/ui/ui/build.gradle")

        sourceSetBuildScripts.forEach { relativePath ->
            val buildScript = repositoryRoot.resolve(relativePath).readText()
            val nonJvmMainBlock = checkNotNull(sourceSetBlock(buildScript, "nonJvmMain")) {
                "Could not find nonJvmMain in $relativePath."
            }
            assertTrue(
                nonJvmMainBlock.contains("if (!composeWinUiTargetEnabled)") &&
                    nonJvmMainBlock.contains("dependsOn(skikoNonJvmMain)"),
                "$relativePath must keep nonJvmMain -> skikoNonJvmMain only outside WinUI mode.",
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
    fun winuiMingwDependencyModulesExposeNativeVariantsWithoutSkikoSources() {
        val repositoryRoot = findRepositoryRoot()
        val animationCoreBuild = repositoryRoot.resolve("compose/animation/animation-core/build.gradle")
        val animationBuild = repositoryRoot.resolve("compose/animation/animation/build.gradle")
        val foundationBuild = repositoryRoot.resolve("compose/foundation/foundation/build.gradle")
        val settingsGradle = repositoryRoot.resolve("settings.gradle").readText()

        assertTrue(settingsGradle.contains("composeWinUi.enableMingwTarget"))
        mapOf(
            ":navigation:navigation-common" to
                "navigation/navigation-common-compatibility-stub",
            ":navigation:navigation-runtime" to
                "navigation/navigation-runtime-compatibility-stub",
        ).forEach { (projectPath, compatibilityStub) ->
            val conditionalMapping = Regex(
                """includeProject\(\s*"$projectPath",\s*""" +
                    """composeWinUiMingwTargetEnabled \? null :\s*""" +
                    """"$compatibilityStub"\s*\)""",
            )
            val projectDeclarations = Regex(
                """includeProject\(\s*"$projectPath"""",
            ).findAll(settingsGradle).count()
            assertTrue(
                conditionalMapping.containsMatchIn(settingsGradle) &&
                    projectDeclarations == 1,
                "settings.gradle must declare $projectPath once and use its real source " +
                    "directory instead of $compatibilityStub when WinUI MinGW is enabled.",
            )
        }

        mapOf(
            animationCoreBuild to "jbMain",
            animationBuild to "nonAndroidMain",
        ).forEach { (buildScript, platformNeutralSourceSet) ->
            val source = buildScript.readText()
            assertTrue(
                source.contains("composeWinUiMingwTargetEnabled") &&
                    source.contains("mingwX64(\"winuiMingw\")"),
                "${repositoryRoot.relativize(buildScript)} must define the WinUI MinGW target.",
            )
            val nonJvmMain = checkNotNull(sourceSetBlock(source, "nonJvmMain"))
            val nativeMain = checkNotNull(sourceSetBlock(source, "nativeMain"))
            assertTrue(
                nonJvmMain.contains("dependsOn($platformNeutralSourceSet)") &&
                    nativeMain.contains("dependsOn(nonJvmMain)"),
                "${repositoryRoot.relativize(buildScript)} must expose its platform-neutral " +
                    "source set through the default Native hierarchy.",
            )
        }

        val remainingNativeVariantBuilds = listOf(
            "compose/material/material/build.gradle",
            "compose/material/material-ripple/build.gradle",
            "compose/material3/material3/build.gradle",
            "compose/material3/material3-window-size-class/build.gradle",
            "compose/material3/adaptive/adaptive/build.gradle",
            "compose/material3/adaptive/adaptive-layout/build.gradle",
            "compose/material3/adaptive/adaptive-navigation/build.gradle",
            "navigation/navigation-common/build.gradle",
            "navigation/navigation-compose/build.gradle",
            "navigation/navigation-runtime/build.gradle",
        )
        remainingNativeVariantBuilds.forEach { relativePath ->
            val source = repositoryRoot.resolve(relativePath).readText()
            val isPlainMingwTarget =
                relativePath == "navigation/navigation-common/build.gradle" ||
                relativePath == "navigation/navigation-runtime/build.gradle"
            val targetDeclaration = if (isPlainMingwTarget) {
                "mingwX64()"
            } else {
                "mingwX64(\"winuiMingw\")"
            }
            assertTrue(
                source.contains("composeWinUiMingwTargetEnabled") &&
                    source.contains(targetDeclaration),
                "$relativePath must define the WinUI MinGW target.",
            )
        }

        val navigationCommonSource = repositoryRoot
            .resolve("navigation/navigation-common/build.gradle")
            .readText()
        val navigationCommonDependencies = navigationCommonSource
            .substringAfter("commonMain.dependencies {")
            .substringBefore("commonTest.dependencies {")
        listOf(
            ":lifecycle:lifecycle-common",
            ":lifecycle:lifecycle-runtime",
            ":lifecycle:lifecycle-viewmodel",
            ":lifecycle:lifecycle-viewmodel-savedstate",
            ":savedstate:savedstate",
        ).forEach { projectPath ->
            assertTrue(
                navigationCommonDependencies.contains("api(project(\"$projectPath\"))"),
                "navigation-common must use $projectPath for WinUI MinGW so Gradle selects " +
                    "the CMP-backed Native variant.",
            )
        }
        assertTrue(
            navigationCommonDependencies.contains("if (composeWinUiMingwTargetEnabled)") &&
                navigationCommonDependencies.contains(
                    "api(\"androidx.savedstate:savedstate:1.5.0\")",
                ),
            "navigation-common must keep its upstream dependencies outside WinUI MinGW mode.",
        )
        val navigationCommonRoot = repositoryRoot.resolve("navigation/navigation-common")
        val synchronizedObjectPath =
            "kotlin/androidx/navigation/internal/SynchronizedObject.native.kt"
        assertFalse(
            navigationCommonRoot.resolve("src/nativeMain/$synchronizedObjectPath").exists(),
            "navigation-common nativeMain must not expose its POSIX mutex actual to MinGW.",
        )
        assertTrue(
            navigationCommonRoot.resolve("src/posixMain/$synchronizedObjectPath").exists(),
            "navigation-common must keep its pthread mutex actual in posixMain.",
        )
        assertTrue(
            navigationCommonSource.contains("create(\"posixMain\").dependsOn(nativeMain)") &&
                navigationCommonSource.contains("appleMain.dependsOn(posixMain)") &&
                navigationCommonSource.contains("linuxMain.dependsOn(posixMain)"),
            "navigation-common must attach its POSIX mutex actual only to Apple and Linux.",
        )
        val mingwSynchronizedObject = navigationCommonRoot.resolve(
            "src/mingwX64Main/kotlin/androidx/navigation/internal/" +
                "SynchronizedObject.mingwX64.kt",
        )
        assertTrue(mingwSynchronizedObject.exists())
        val mingwSynchronizedObjectSource = mingwSynchronizedObject.readText()
        listOf(
            "CRITICAL_SECTION",
            "InitializeCriticalSection",
            "EnterCriticalSection",
            "LeaveCriticalSection",
            "DeleteCriticalSection",
        ).forEach { requiredApi ->
            assertTrue(
                mingwSynchronizedObjectSource.contains(requiredApi),
                "navigation-common MinGW synchronization must use $requiredApi.",
            )
        }

        val uiBackhandlerBuild = repositoryRoot
            .resolve("compose/ui/ui-backhandler/build.gradle")
            .readText()
        assertTrue(
            uiBackhandlerBuild.contains("composeWinUiMingwTargetEnabled") &&
                uiBackhandlerBuild.contains("mingwX64()"),
            "ui-backhandler must publish a standard MinGW variant for Material3.",
        )
        val material3Build = repositoryRoot.resolve("compose/material3/material3/build.gradle")
        val material3WinuiSkikoMain = checkNotNull(
            sourceSetBlock(material3Build.readText(), "winuiSkikoMain"),
        )
        val winuiMaterial3Actuals = listOf(
            "androidx/compose/material3/ModalBottomSheet.winui.kt" to
                "ModalBottomSheet.skiko.kt",
            "androidx/compose/material3/WideNavigationRail.winui.kt" to
                "WideNavigationRail.skiko.kt",
            "androidx/compose/material3/internal/BasicEdgeToEdgeDialog.winui.kt" to
                "BasicEdgeToEdgeDialog.skiko.kt",
        )
        winuiMaterial3Actuals.forEach { (winuiPath, skikoFile) ->
            assertTrue(
                material3WinuiSkikoMain.contains("kotlin.exclude(\"**/$skikoFile\")"),
                "Material3 WinUI must exclude the Skiko-specific $skikoFile actual.",
            )
            val winuiActual = repositoryRoot.resolve(
                "compose/material3/material3/src/winuiMain/kotlin/$winuiPath",
            )
            assertTrue(winuiActual.exists(), "Missing Material3 WinUI actual: $winuiPath")
            val winuiJvmActual = repositoryRoot.resolve(
                "compose/material3/material3/src/winuiJvmMain/kotlin/$winuiPath",
            )
            assertFalse(
                winuiJvmActual.exists(),
                "Shared Material3 WinUI actual must not also remain in winuiJvmMain: $winuiPath",
            )
            val winuiActualSource = winuiActual.readText()
            listOf(
                "usePlatformInsets",
                "useSoftwareKeyboardInset",
                "scrimColor",
                "animateTransition",
            ).forEach { skikoDialogProperty ->
                assertFalse(
                    winuiActualSource.contains(skikoDialogProperty),
                    "$winuiPath must use WinUI DialogProperties rather than " +
                        "$skikoDialogProperty.",
                )
            }
        }

        val navigationComposeRoot = repositoryRoot.resolve("navigation/navigation-compose")
        val defaultNavTransitionsPath =
            "kotlin/androidx/navigation/compose/DefaultNavTransitions.nonAndroid.kt"
        assertFalse(
            navigationComposeRoot.resolve(
                "src/desktopMain/kotlin/androidx/navigation/compose/" +
                    "DefaultNavTransitions.desktop.kt",
            ).exists(),
            "Platform-neutral default Navigation transitions must not remain desktop-only.",
        )
        val nonAndroidDefaultNavTransitions = navigationComposeRoot.resolve(
            "src/nonAndroidMain/$defaultNavTransitionsPath",
        )
        assertTrue(
            nonAndroidDefaultNavTransitions.exists(),
            "Navigation Compose must expose default transitions to all non-Android targets.",
        )
        val nonAndroidDefaultNavTransitionsSource = nonAndroidDefaultNavTransitions.readText()
        assertTrue(
            nonAndroidDefaultNavTransitionsSource.contains("EnterTransition.None") &&
                nonAndroidDefaultNavTransitionsSource.contains("ExitTransition.None"),
            "Non-Android default Navigation transitions must retain the Desktop behavior.",
        )

        listOf(
            "navigation/navigation-common-compatibility-stub/build.gradle",
            "navigation/navigation-runtime-compatibility-stub/build.gradle",
        ).forEach { relativePath ->
            val source = repositoryRoot.resolve(relativePath).readText()
            assertFalse(
                source.contains("mingwX64()"),
                "$relativePath must not publish a fake MinGW variant because its upstream " +
                    "AndroidX dependency does not publish one.",
            )
        }

        mapOf(
            "compose/material/material/build.gradle" to "nonJvmMain",
            "compose/material3/material3/build.gradle" to "nonJvmMain",
            "compose/material3/material3-window-size-class/build.gradle" to "nonJvmMain",
            "compose/material3/adaptive/adaptive-layout/build.gradle" to "nativeMain",
        ).forEach { (relativePath, nativeSourceSet) ->
            val source = repositoryRoot.resolve(relativePath).readText()
            val block = checkNotNull(sourceSetBlock(source, nativeSourceSet))
            assertTrue(
                block.contains("if (!composeWinUiTargetEnabled)"),
                "$relativePath must keep skikoMain out of the WinUI Native hierarchy.",
            )
        }

        val foundationSource = foundationBuild.readText()
        val foundationNonJvmMain = checkNotNull(sourceSetBlock(foundationSource, "nonJvmMain"))
        val foundationNativeMain = checkNotNull(sourceSetBlock(foundationSource, "nativeMain"))
        val foundationWinuiMain = checkNotNull(sourceSetBlock(foundationSource, "winuiMain"))
        assertTrue(
            foundationNonJvmMain.contains("if (!composeWinUiTargetEnabled)") &&
                foundationNonJvmMain.contains("dependsOn(skikoNonJvmMain)"),
            "Foundation nonJvmMain must keep Skiko ancestry out of WinUI mode.",
        )
        assertTrue(
            foundationNativeMain.contains("if (!composeWinUiTargetEnabled)") &&
                foundationNativeMain.contains("dependsOn(skikoNativeMain)"),
            "Foundation nativeMain must keep generic Native actuals out of WinUI mode.",
        )
        assertFalse(
            foundationWinuiMain.contains("dependsOn(skikoMain)"),
            "Foundation winuiMain must not import conflicting skikoMain sources.",
        )

        val foundationRoot = repositoryRoot.resolve("compose/foundation/foundation")
        listOf(
            "androidx/compose/foundation/text/NativeCursorHandle.native.kt",
            "androidx/compose/foundation/text/KeyEventHelpers.native.kt",
            "androidx/compose/foundation/text/TextFieldCursor.native.kt",
            "androidx/compose/foundation/text/contextmenu/internal/" +
                "ProvideDefaultPlatformTextContextMenuProviders.native.kt",
        ).forEach { relativePath ->
            assertFalse(foundationRoot.resolve("src/nativeMain/kotlin/$relativePath").exists())
            assertTrue(foundationRoot.resolve("src/skikoNativeMain/kotlin/$relativePath").exists())
        }
        listOf(
            "androidx/compose/foundation/lazy/layout/Lazy.winui.kt",
            "androidx/compose/foundation/v2/Actuals.winui.kt",
        ).forEach { relativePath ->
            assertFalse(foundationRoot.resolve("src/winuiMain/kotlin/$relativePath").exists())
            assertTrue(foundationRoot.resolve("src/winuiJvmMain/kotlin/$relativePath").exists())
        }
        val sharedSelectionManager = foundationRoot.resolve(
            "src/winuiMain/kotlin/androidx/compose/foundation/text/selection/" +
                "TextFieldSelectionManager.winui.kt"
        ).readText()
        assertFalse(sharedSelectionManager.contains("hasAvailableTextToPaste"))
        assertTrue(
            foundationRoot.resolve(
                "src/winuiJvmMain/kotlin/androidx/compose/foundation/text/selection/" +
                    "TextFieldSelectionManager.winuiJvm.kt"
            ).exists()
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

    @Test
    fun skikoOnlyNativeActualsAreOutsideWinuiNativeFragments() {
        val moduleRoot = findUiModuleRoot()
        val buildScript = moduleRoot.resolve("build.gradle").readText()
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
        val nativeSnapshots = moduleRoot.resolve(
            "src/nativeMain/kotlin/androidx/compose/ui/platform/GlobalSnapshotManager.native.kt"
        )
        val skikoNativeSnapshots = moduleRoot.resolve(
            "src/skikoNativeMain/kotlin/androidx/compose/ui/platform/" +
                "GlobalSnapshotManager.skikoNative.kt"
        )

        assertFalse(nonJvmActuals.contains("PostDelayedDispatcher"))
        assertTrue(skikoNonJvmActuals.exists())
        assertFalse(nativeThreading.exists())
        assertTrue(skikoNativeThreading.exists())
        assertFalse(nativeSnapshots.exists())
        assertTrue(skikoNativeSnapshots.exists())

        listOf("skikoNonJvmMain", "skikoNativeMain").forEach { sourceSet ->
            val block = checkNotNull(sourceSetBlock(buildScript, sourceSet)) {
                "Could not find $sourceSet in compose/ui/ui/build.gradle."
            }
            assertTrue(block.contains("dependsOn(skikoMain)"))
        }
        val nonJvmMainBlock = checkNotNull(sourceSetBlock(buildScript, "nonJvmMain"))
        val nativeMainBlock = checkNotNull(sourceSetBlock(buildScript, "nativeMain"))
        assertTrue(
            nonJvmMainBlock.contains("dependsOn(skikoNonJvmMain)") &&
                nonJvmMainBlock.contains("if (!composeWinUiTargetEnabled)"),
        )
        assertTrue(
            nativeMainBlock.contains("dependsOn(skikoNativeMain)") &&
                nativeMainBlock.contains("if (!composeWinUiTargetEnabled)"),
        )
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
