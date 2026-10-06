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

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.InternalComposeUiApi
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.Window
import org.jetbrains.skiko.winui.WinUISkiaLayer

@InternalComposeUiApi
val LocalWinUIRoot = staticCompositionLocalOf<FrameworkElement?> { null }

@InternalComposeUiApi
val LocalWinUIWindow = staticCompositionLocalOf<Window?> { null }

/**
 * The Skiko layer this composition renders through, for libraries that draw their own GPU
 * resources via [WinUISkiaLayer.direct3DInterop] (a video backend, for example). Null outside a
 * WinUI render host.
 */
@InternalComposeUiApi
val LocalWinUISkiaLayer = staticCompositionLocalOf<WinUISkiaLayer?> { null }
