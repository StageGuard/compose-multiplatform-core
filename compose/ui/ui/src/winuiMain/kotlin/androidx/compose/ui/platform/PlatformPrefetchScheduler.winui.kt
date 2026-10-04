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

/**
 * The CompositionLocal providing prefetch scheduler associated with the current WinUI root.
 */
@InternalComposeUiApi
val LocalPlatformPrefetchScheduler = staticCompositionLocalOf<PlatformPrefetchScheduler> {
    error("CompositionLocal LocalPlatformPrefetchScheduler not present")
}

/** Leaves prefetching to the regular measure pass, like the other JVM Skiko platforms. */
@OptIn(InternalComposeUiApi::class)
internal object NoOpPlatformPrefetchScheduler : PlatformPrefetchScheduler {
    override fun scheduleHighPriorityPrefetch(request: PlatformPrefetchRequest) = Unit

    override fun scheduleLowPriorityPrefetch(request: PlatformPrefetchRequest) = Unit
}
