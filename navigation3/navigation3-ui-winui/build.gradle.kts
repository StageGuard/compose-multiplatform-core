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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("AndroidXComposePlugin")
    id("kotlin-multiplatform")
}

// The artifact-redirection versions of the fork (see redirectversions.toml in the repo root).
fun redirectVersion(group: String): String =
    Regex("^\"${Regex.escape(group)}\"\\s*=\\s*\"([^\"]+)\"", RegexOption.MULTILINE)
        .find(rootProject.file("redirectversions.toml").readText())
        ?.groupValues?.get(1)
        ?: error("No redirect version for $group in redirectversions.toml")

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")

    sourceSets {
        commonMain {
            kotlin.srcDir("../navigation3-ui/src/commonMain/kotlin")
            dependencies {
                val navigation3Version = redirectVersion("androidx.navigation3")

                api("androidx.navigation3:navigation3-runtime:$navigation3Version")
                api("org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.0")
                api("org.jetbrains.compose.animation:animation:1.10.0")
                api("org.jetbrains.compose.runtime:runtime:1.10.0")
                api("org.jetbrains.compose.runtime:runtime-saveable:1.10.0")
                api("org.jetbrains.compose.ui:ui:1.10.0")
                api("androidx.savedstate:savedstate:1.4.0")
                api("androidx.savedstate:savedstate-compose:1.4.0")
                implementation("androidx.annotation:annotation:1.9.1")
                implementation("androidx.collection:collection:1.5.0")
                implementation("androidx.lifecycle:lifecycle-runtime:2.10.0")
                implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
            }
        }

        named("winuiJvmMain") {
            kotlin.srcDir("../navigation3-ui/src/desktopMain/kotlin")
        }
    }
}

configurations.configureEach {
    if (name.lowercase().contains("winui")) {
        attributes.attribute(
            org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            25,
        )
    }
}

tasks.withType<KotlinCompile>().configureEach {
    if (name.contains("Winui")) {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
            freeCompilerArgs.add("-Xjdk-release=25")
        }
    }
}
