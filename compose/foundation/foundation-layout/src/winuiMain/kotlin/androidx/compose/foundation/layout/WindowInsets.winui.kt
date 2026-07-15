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

@file:OptIn(InternalComposeUiApi::class)

package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalPlatformWindowInsets

/** The WinUI title-bar inset reserved on the left side of the content. */
val WindowInsets.Companion.titleBarLeftInset: WindowInsets
    @Composable get() = LocalPlatformWindowInsets.current.titleBarLeftInset.toWindowInsets()

/** The WinUI title-bar inset reserved on the right side of the content. */
val WindowInsets.Companion.titleBarRightInset: WindowInsets
    @Composable get() = LocalPlatformWindowInsets.current.titleBarRightInset.toWindowInsets()
