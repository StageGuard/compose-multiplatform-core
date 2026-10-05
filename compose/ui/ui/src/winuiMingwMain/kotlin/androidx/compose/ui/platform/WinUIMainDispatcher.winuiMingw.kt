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

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package androidx.compose.ui.platform

import kotlinx.coroutines.Dispatchers

// kotlinx.coroutines has no main dispatcher on Windows and no service loader on Kotlin/Native:
// the dispatcher is injected the way kotlinx-coroutines-test does it.
internal actual fun installWinUIMainDispatcher() {
    Dispatchers.injectMain(
        WinUIMainDispatcher(isImmediate = false, rejectedWorkDispatcher = Dispatchers.Default)
    )
}
