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

import io.github.composefluent.winrt.runtime.RawAddress
import microsoft.ui.xaml.Window
import windows.ui.viewmanagement.InputPane
import windows.ui.viewmanagement.InputPaneInterop
import winrt.interop.WindowNative

internal actual fun acquireWinUIInputPane(window: Window): InputPane? =
    runCatching {
        val windowHandle = WindowNative.getWindowHandle(window)
        check(windowHandle != RawAddress.Null) { "WinUI window does not have an HWND." }
        InputPaneInterop.getForWindow(windowHandle)
    }.getOrNull()
