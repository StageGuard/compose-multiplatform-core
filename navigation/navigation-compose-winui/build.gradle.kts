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
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")

    sourceSets {
        commonMain {
            kotlin.srcDir("../navigation-compose/src/commonMain/kotlin")
            dependencies {
                api(project(":navigation:navigation-runtime"))
                api(project(":navigation:navigation-common"))
                implementation("androidx.annotation:annotation:1.8.0")
                api("org.jetbrains.compose.animation:animation:1.11.0")
                api("org.jetbrains.compose.runtime:runtime:1.11.0")
                api("org.jetbrains.compose.runtime:runtime-saveable:1.11.0")
                api("org.jetbrains.compose.ui:ui:1.11.0")
                implementation("org.jetbrains.compose.animation:animation-core:1.11.0")
                implementation("org.jetbrains.compose.foundation:foundation-layout:1.11.0")
                implementation("androidx.collection:collection:1.5.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-common:2.11.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
                implementation("androidx.savedstate:savedstate:1.4.0")
                implementation("androidx.savedstate:savedstate-compose:1.4.0")
                implementation("org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.0")
                implementation(libs.kotlinCoroutinesCore)
                implementation(libs.kotlinSerializationCore)
            }
        }

        named("winuiJvmMain") {
            kotlin.srcDir("../navigation-compose/src/nonAndroidMain/kotlin")
            kotlin.srcDir("../navigation-compose/src/desktopMain/kotlin")
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
