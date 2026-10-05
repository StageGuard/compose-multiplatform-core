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

package androidx.compose.material3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import windows.devices.input.KeyboardCapabilities
import windows.devices.input.MouseCapabilities

internal data class WinUIPrecisionPointerCapabilities(
    val keyboardPresent: Boolean,
    val mousePresent: Boolean,
)

internal fun winUIShouldUsePrecisionPointerComponentSizing(
    optIn: Boolean,
    capabilities: WinUIPrecisionPointerCapabilities,
): Boolean = optIn && capabilities.keyboardPresent && capabilities.mousePresent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun EnsurePrecisionPointerListenersRegistered(
    content: @Composable (() -> Unit),
) {
    val shouldRegisterListeners =
        ComposeMaterial3Flags.isPrecisionPointerComponentSizingEnabled &&
            !LocalIsPrecisionPointerListenerRegistered.current
    if (shouldRegisterListeners) {
        val capabilities = remember { winUICurrentPrecisionPointerCapabilities() }
        shouldUsePrecisionPointerComponentSizing.value =
            winUIShouldUsePrecisionPointerComponentSizing(
                optIn = ComposeMaterial3Flags.isPrecisionPointerComponentSizingEnabled,
                capabilities = capabilities,
            )
        CompositionLocalProvider(LocalIsPrecisionPointerListenerRegistered provides true, content)
    } else {
        content()
    }
}

private fun winUICurrentPrecisionPointerCapabilities(): WinUIPrecisionPointerCapabilities =
    runCatching {
        WinUIPrecisionPointerCapabilities(
            keyboardPresent = KeyboardCapabilities().keyboardPresent > 0,
            mousePresent = MouseCapabilities().mousePresent > 0,
        )
    }.getOrDefault(
        WinUIPrecisionPointerCapabilities(
            keyboardPresent = false,
            mousePresent = false,
        )
    )

private val LocalIsPrecisionPointerListenerRegistered = staticCompositionLocalOf { false }
