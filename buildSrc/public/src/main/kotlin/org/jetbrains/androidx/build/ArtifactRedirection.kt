/*
 * Copyright 2024 The Android Open Source Project
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

data class ArtifactRedirectCoordinates(
    val groupId: String,
    val version: String,
)

data class ArtifactRedirection(
    private val coordinatesByTarget: Map<String, ArtifactRedirectCoordinates>,
) {
    val targetNames: Set<String> = coordinatesByTarget.keys

    fun coordinatesForTarget(targetName: String): ArtifactRedirectCoordinates? =
        coordinatesByTarget[targetName.lowercase()]

    fun coordinatesForConfiguration(
        configurationName: String,
    ): ArtifactRedirectCoordinates? {
        // Configuration names are target-prefixed in Kotlin KMP publications, for example:
        // "desktopApiElements" or "iosArm64MetadataElements".
        return targetNames
            .sortedByDescending(String::length)
            .firstOrNull { configurationName.startsWith(it, ignoreCase = true) }
            ?.let(coordinatesByTarget::getValue)
    }

    fun allCoordinates(): Set<ArtifactRedirectCoordinates> = coordinatesByTarget.values.toSet()
}

private val redirectionCache = mutableMapOf<Project, ArtifactRedirection?>()

fun Project.artifactRedirection(): ArtifactRedirection? =
    redirectionCache.getOrPut(project) { project.readArtifactRedirection() }

private fun Project.replacedGroupId(replacement: String) =
    group.toString().replace(
        replacement.substringBefore("->"),
        replacement.substringAfter("->")
    )

fun Project.readArtifactRedirection(): ArtifactRedirection? {
    val targetNames = strProperty("artifactRedirection.targetNames")
        ?.takeIf { it.isNotEmpty() }
        ?.split(",")
        ?.map { it.lowercase() }
        ?.toSet()
        .orEmpty()

    val coordinatesByTarget = mutableMapOf<String, ArtifactRedirectCoordinates>()
    if (targetNames.isNotEmpty()) {
        val groupId = strProperty("artifactRedirection.groupId")
            ?: strProperty("artifactRedirection.groupIdReplacement")?.let(::replacedGroupId)
            ?: error(
                "Please add `artifactRedirection.groupId` or " +
                    "`artifactRedirection.groupIdReplacement` to " +
                    "`${projectDir.resolve("gradle.properties")}` or any parent project"
            )

        // Example - for library "androidx.annotation:annotation" possible properties:
        // artifactRedirection.version.androidx.annotation.annotation,
        // artifactRedirection.version.androidx.annotation,
        // artifactRedirection.version.androidx
        val propertyNames = run {
            val parts = groupId.split(".") + name
            val idVariations = (parts.size downTo 1).map { i -> parts.take(i).joinToString(".") }
            idVariations.map { "artifactRedirection.version.$it" }
        }

        var defaultVersion: String =
            propertyNames.firstNotNullOfOrNull(::strProperty)
                ?: error(
                    """
                        Please specify any of these properties in the root `gradle.properties`:
                        ${propertyNames.joinToString(", ")}
                        Or disable redirection by overriding `artifactRedirection.targetNames=` in
                        `${projectDir.resolve("gradle.properties")}`
                    """.trimIndent()
                )

        val targetVersions = mutableMapOf<String, String>()

        // for a case when some targets have different redirecting version
        val redirectTargetVersions = strProperty("artifactRedirection.${groupId}.targetVersions")
        if (redirectTargetVersions != null) {
            // for example: jvm=1.7.1,default=1.8.0-alpha01
            val versionsMap = redirectTargetVersions.split(",").map {
                val values = it.split("=")
                values[0].lowercase() to values[1]
            }.associate { it }

            defaultVersion = versionsMap["default"] ?: defaultVersion
            targetVersions.putAll(versionsMap.filterKeys { it != "default" })
        }

        targetNames.forEach { targetName ->
            coordinatesByTarget[targetName] = ArtifactRedirectCoordinates(
                groupId = groupId,
                version = targetVersions[targetName] ?: defaultVersion,
            )
        }
    }

    if (isComposeWinUiTargetMode() && group.toString().startsWith("org.jetbrains.compose")) {
        val cmpVersion = strProperty("composeWinUi.cmpVersion")
            ?.takeIf(String::isNotBlank)
            ?: error(
                "composeWinUi.cmpVersion must be set when a compose-winui target is enabled " +
                    "so non-WinUI variants can redirect to Compose Multiplatform."
            )
        val cmpCoordinates = ArtifactRedirectCoordinates(group.toString(), cmpVersion)
        composeWinUiCmpTargetNames.forEach { targetName ->
            coordinatesByTarget.putIfAbsent(targetName, cmpCoordinates)
        }
    }

    return coordinatesByTarget
        .takeIf(Map<*, *>::isNotEmpty)
        ?.let(::ArtifactRedirection)
}

private fun Project.strProperty(name: String): String? = findProperty(name)?.toString()

private fun Project.isComposeWinUiTargetMode(): Boolean =
    strProperty("composeWinUi.enableJvmTarget").toBoolean() ||
        strProperty("composeWinUi.enableMingwTarget").toBoolean()

private val composeWinUiCmpTargetNames = setOf(
    "desktop",
    "jvm",
    "js",
    "wasmjs",
    "macosx64",
    "macosarm64",
    "iosx64",
    "iosarm64",
    "iossimulatorarm64",
    "tvosx64",
    "tvosarm64",
    "tvossimulatorarm64",
    "watchosx64",
    "watchosarm32",
    "watchosarm64",
    "watchosdevicearm64",
    "watchossimulatorarm64",
    "linuxx64",
    "linuxarm64",
    "mingwx64",
)
