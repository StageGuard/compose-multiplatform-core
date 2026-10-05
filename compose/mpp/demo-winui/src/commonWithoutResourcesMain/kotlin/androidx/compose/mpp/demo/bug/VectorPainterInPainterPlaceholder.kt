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

package androidx.compose.mpp.demo.bug

import androidx.compose.material.Text
import androidx.compose.mpp.demo.Screen

// The screen of the other targets draws a drawable of the Compose resources library, which has no
// mingwX64 artifact.
val VectorPainterInPainter = Screen.Example(
    "VectorPainter inside another Painter"
) {
    Text("Not available in a build with the native WinUI target: it needs Compose resources.")
}
