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

package androidx.compose.ui.window

import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.RawAddress
import microsoft.ui.xaml.Window as XamlWindow

private val IWindowNativeIid = Guid("EECDBF0E-BAE9-4CB6-A68E-9598E1CB57BB")

/**
 * The HWND of [window], from `IWindowNative.WindowHandle`.
 *
 * KWINRT-077: kotlin-winrt has a generated helper for this, `winrt.interop.WindowNative`, but
 * does not generate it for a module that projects a NuGet package or authors WinRT classes, as
 * this one does. This is the call that the helper makes.
 */
internal fun winuiWindowHandle(window: XamlWindow): RawAddress =
    window.nativeObject.queryInterface(IWindowNativeIid).getOrThrow().use { windowNative ->
        PlatformAbi.confinedScope().use { scope ->
            val handleOut = PlatformAbi.allocatePointerSlot(scope)
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = windowNative.pointer,
                    slot = 3,
                    arg0 = handleOut,
                ),
            ).requireSuccess("IWindowNative.WindowHandle")
            PlatformAbi.readPointer(handleOut)
        }
    }

internal fun winuiWindowHwnd(window: XamlWindow): Long = winuiWindowHandle(window).value
