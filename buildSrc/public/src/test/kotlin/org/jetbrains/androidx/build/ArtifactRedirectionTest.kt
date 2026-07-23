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
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactRedirectionTest {
    @Test
    fun winuiModeKeepsAndroidOwnerAndAddsCmpOwners() {
        val redirection = checkNotNull(
            composeProject(
                "composeWinUi.enableMingwTarget" to "true",
                "composeWinUi.cmpVersion" to "1.10.0",
            ).readArtifactRedirection()
        )

        assertEquals(
            ArtifactRedirectCoordinates("androidx.compose.ui", "1.12.0-alpha03"),
            redirection.coordinatesForTarget("android"),
        )
        listOf("desktop", "iosArm64", "js", "wasmJs", "linuxX64", "mingwX64")
            .forEach { target ->
                assertEquals(
                    ArtifactRedirectCoordinates("org.jetbrains.compose.ui", "1.10.0"),
                    redirection.coordinatesForTarget(target),
                )
            }
        assertEquals(
            ArtifactRedirectCoordinates("org.jetbrains.compose.ui", "1.10.0"),
            redirection.coordinatesForConfiguration("iosArm64MetadataElements"),
        )
        assertNull(redirection.coordinatesForTarget("winuiJvm"))
        assertNull(redirection.coordinatesForTarget("winuiMingw"))
        assertNull(redirection.coordinatesForConfiguration("winuiJvmApiElements"))
        assertNull(redirection.coordinatesForConfiguration("winuiMingwApiElements"))
    }

    @Test
    fun normalModeKeepsOnlyConfiguredRedirections() {
        val redirection = checkNotNull(composeProject().readArtifactRedirection())

        assertEquals(
            ArtifactRedirectCoordinates("androidx.compose.ui", "1.12.0-alpha03"),
            redirection.coordinatesForTarget("android"),
        )
        assertEquals(setOf("android"), redirection.targetNames)
    }

    @Test
    fun winuiModeRequiresCmpVersion() {
        val error = assertThrows(IllegalStateException::class.java) {
            composeProject("composeWinUi.enableJvmTarget" to "true")
                .readArtifactRedirection()
        }

        assertTrue(error.message.orEmpty().contains("composeWinUi.cmpVersion"))
    }

    private fun composeProject(vararg properties: Pair<String, String>): Project =
        ProjectBuilder.builder().withName("ui").build().also { project ->
            project.group = "org.jetbrains.compose.ui"
            project.extensions.extraProperties.apply {
                set("artifactRedirection.targetNames", "android")
                set(
                    "artifactRedirection.groupIdReplacement",
                    "org.jetbrains.compose->androidx.compose",
                )
                set("artifactRedirection.version.androidx.compose", "1.12.0-alpha03")
                properties.forEach { (name, value) -> set(name, value) }
            }
        }
}
