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

package org.jetbrains.androidx.build

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentSelector

// The androidx artifacts that the JetBrains artifacts below redirect to on the targets that
// both publish, by the Gradle module metadata of the JetBrains versions the fork depends on.
private val mingwRedirectedModules = mapOf(
    "org.jetbrains.androidx.navigationevent:navigationevent" to
        "androidx.navigationevent:navigationevent:1.1.1",
    "org.jetbrains.androidx.navigationevent:navigationevent-compose" to
        "androidx.navigationevent:navigationevent-compose:1.1.1",
    "org.jetbrains.androidx.window:window-core" to
        "androidx.window:window-core:1.5.0",
)

private const val ComposeGroupPrefix = "org.jetbrains.compose."
private const val ConfiguredMarker = "composeWinUiMingwDependenciesConfigured"

/**
 * Lets the WinUI native target (`composeWinUi.enableMingwTarget`) resolve the dependencies that
 * have no published mingwX64 variant, in the configurations of a MinGW target:
 *
 * - a JetBrains artifact that redirects to an androidx one is replaced by that one, which has
 *   the variant;
 * - a published Compose module that a library pins (`org.jetbrains.compose.ui:ui:1.10.0`) is
 *   replaced by its project in this build, the only place where the variant exists.
 */
internal fun Project.configureWinUiMingwDependencies() {
    declareSkikoWinUiInWinUiOnlySourceSets()
    val mingwTargetEnabled = providers.gradleProperty("composeWinUi.enableMingwTarget")
        .map { it.toBoolean() }
        .getOrElse(false)
    if (!mingwTargetEnabled) return
    // The library plugin and the Compose plugin both ask for this; an application has the second.
    if (extensions.extraProperties.has(ConfiguredMarker)) return
    extensions.extraProperties.set(ConfiguredMarker, true)

    configurations.configureEach { configuration ->
        if (!configuration.name.contains("mingw", ignoreCase = true)) return@configureEach
        configuration.resolutionStrategy.dependencySubstitution { substitutions ->
            mingwRedirectedModules.forEach { (jetBrainsModule, androidxModule) ->
                substitutions.substitute(substitutions.module(jetBrainsModule))
                    .using(substitutions.module(androidxModule))
                    .because("The JetBrains artifact has no mingwX64 variant.")
            }
            substitutions.all { dependency ->
                val requested = dependency.requested as? ModuleComponentSelector ?: return@all
                if (!requested.group.startsWith(ComposeGroupPrefix)) return@all
                val projectPath = ":compose:" +
                    requested.group.removePrefix(ComposeGroupPrefix).replace('.', ':') +
                    ":" + requested.module
                if (rootProject.findProject(projectPath) != null) {
                    dependency.useTarget(
                        substitutions.project(projectPath),
                        "The published Compose module has no mingwX64 variant.",
                    )
                }
            }
        }
    }
}
