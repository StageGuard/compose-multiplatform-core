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

@file:OptIn(ExperimentalForeignApi::class)

package androidx.compose.mpp.demo

import androidx.compose.mpp.demo.components.text.loadResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.value
import platform.windows.CommandLineToArgvW
import platform.windows.GetCommandLineW
import platform.windows.LocalFree

// The same window and content as the desktop demo (Main.desktop.kt): the two are compared
// screen by screen. The first command line argument names the screen to open.
fun main() {
    val args = commandLineArguments()
    Application {
        Window(
            title = "Compose MPP demo",
            state = rememberWindowState(width = 1024.dp, height = 850.dp),
            onCloseRequest = { exitApplication() },
        ) {
            val app = remember { App(initialScreenName = args.getOrNull(0)) }
            val fontFamilyResolver = LocalFontFamilyResolver.current
            val fontsLoaded = remember { mutableStateOf(false) }

            if (fontsLoaded.value) {
                app.Content()
            }

            LaunchedEffect(Unit) {
                val fontBytes = loadResource("NotoColorEmoji.ttf")
                if (fontBytes != null) {
                    val fontFamily = FontFamily(listOf(Font("NotoColorEmoji", fontBytes)))
                    fontFamilyResolver.preload(fontFamily)
                }
                fontsLoaded.value = true
            }
        }
    }
}

// The generated application entry calls main without the arguments of the process.
private fun commandLineArguments(): List<String> = memScoped {
    val count = alloc<IntVar>()
    val arguments = CommandLineToArgvW(GetCommandLineW()?.toKStringFromUtf16() ?: "", count.ptr)
        ?: return emptyList()
    try {
        (1 until count.value).mapNotNull { index -> arguments[index]?.toKStringFromUtf16() }
    } finally {
        LocalFree(arguments)
    }
}
