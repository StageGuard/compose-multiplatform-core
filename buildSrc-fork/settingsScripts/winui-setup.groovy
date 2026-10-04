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

import org.gradle.api.initialization.Settings

class WinUISetup {
    /**
     * KWINRT-052: the Kotlin version that the kotlin-winrt compiler plugin is built against.
     * Kotlin compiler plugins are tied to the compiler they were compiled with: on a newer
     * compiler the kotlin-winrt plugin fails with NoSuchMethodError in the IR API.
     */
    static final String KOTLIN_VERSION = "2.4.0"

    static final String ENABLE_JVM_TARGET_PROPERTY = "composeWinUi.enableJvmTarget"

    static boolean isJvmTargetEnabled(Settings settings) {
        def value = settings.providers.gradleProperty(ENABLE_JVM_TARGET_PROPERTY).orNull
        if (value == null) {
            // buildSrc is a build of its own, which does not see the properties of the main build.
            value = settings.gradle.parent?.startParameter?.projectProperties
                    ?.get(ENABLE_JVM_TARGET_PROPERTY)
        }
        return value != null && value.toBoolean()
    }

    /**
     * Builds with the Kotlin version of kotlin-winrt when the WinUI JVM target is enabled. Builds
     * without that target keep the Kotlin version of the version catalog.
     *
     * @param settings The settings instance of the build (the main build or buildSrc)
     */
    static void pinKotlinVersionInVersionCatalog(Settings settings) {
        if (!isJvmTargetEnabled(settings)) return
        settings.dependencyResolutionManagement {
            versionCatalogs {
                libs {
                    version('kotlin', KOTLIN_VERSION)
                    version('kotlin24', KOTLIN_VERSION)
                    version('composeCompilerPlugin', KOTLIN_VERSION)
                }
            }
        }
    }
}

ext.winUISetup = new WinUISetup()
