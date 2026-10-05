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

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * The view configuration of the desktop target (`PlatformContext.DefaultViewConfiguration` with
 * a density dependent touch slop), so that gestures are recognized the same way on both targets.
 */
internal class DefaultWinUIViewConfiguration(
    private val density: () -> Density = { Density(1f) },
) : ViewConfiguration {
    override val longPressTimeoutMillis: Long = 500L

    override val doubleTapTimeoutMillis: Long = 300L

    override val doubleTapMinTimeMillis: Long = 40L

    override val touchSlop: Float
        get() = with(density()) { 18.dp.toPx() }
}
