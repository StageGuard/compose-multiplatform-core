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

import androidx.build.multiplatformExtension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

private const val ConfiguredMarker = "composeWinUiMetadataDependenciesConfigured"

/**
 * The modules replace `org.jetbrains.skiko:skiko` by skiko-winui in the configurations of their
 * WinUI targets (the `winui*` configurations). The Kotlin plugin does not resolve the metadata of a
 * shared source set that way: it reads the dependencies that the source sets of a library declare
 * (its project structure metadata) and looks for them in the resolved graph, so a substituted Skiko
 * is never found there and the source set loses it. A source set that only the WinUI targets
 * compile (`skikoMain` when the Darwin and web targets are disabled) therefore declares skiko-winui
 * in place of Skiko. The root publication needs those source sets.
 */
internal fun Project.declareSkikoWinUiInWinUiOnlySourceSets() {
    if (extensions.extraProperties.has(ConfiguredMarker)) return
    extensions.extraProperties.set(ConfiguredMarker, true)
    val skikoWinUiVersion = providers.gradleProperty("composeWinUi.skikoWinUiVersion")
        .getOrElse("0.0.0-SNAPSHOT")

    afterEvaluate {
        val kotlin = multiplatformExtension ?: return@afterEvaluate
        val skikoWinUi = dependencies.create("io.github.compose-fluent:skiko-winui:$skikoWinUiVersion")
        for (sourceSet in kotlin.sourceSets.filter { isCompiledOnlyByWinUiTargets(it) }) {
            for (scope in listOf("Api", "Implementation", "CompileOnly", "RuntimeOnly")) {
                val configuration = configurations.findByName("${sourceSet.name}$scope") ?: continue
                val skiko = configuration.dependencies.filter {
                    it.group == "org.jetbrains.skiko" && it.name == "skiko"
                }
                if (skiko.isEmpty()) continue
                configuration.dependencies.removeAll(skiko)
                configuration.dependencies.add(skikoWinUi)
            }
        }
    }
}

private fun Project.isCompiledOnlyByWinUiTargets(sourceSet: KotlinSourceSet): Boolean {
    val compiledBy = multiplatformExtension!!.targets
        .filter { it.platformType != KotlinPlatformType.common }
        .flatMap { it.compilations }
        .filter { sourceSet in it.allKotlinSourceSets }
    return compiledBy.isNotEmpty() && compiledBy.all { it.target.name.startsWith("winui") }
}
