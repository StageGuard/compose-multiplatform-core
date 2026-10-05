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

package androidx.compose.ui.focus

import androidx.compose.ui.node.Nodes
import androidx.compose.ui.node.ancestors
import androidx.compose.ui.node.setOfAncestors
import androidx.compose.ui.util.fastForEach

internal object IndirectPointerInputFocusListener : FocusListener {
    override fun onFocusChanged(
        previous: FocusTargetModifierNode?,
        current: FocusTargetModifierNode?,
    ) {
        val previousNodes =
            previous?.ancestors(type = Nodes.IndirectPointerInput, includeSelf = true) ?: return
        val currentNodes =
            current?.setOfAncestors(type = Nodes.IndirectPointerInput, includeSelf = true)
        previousNodes.fastForEach { node ->
            if (currentNodes?.contains(node) != true) {
                node.onCancelIndirectPointerInput()
            }
        }
    }
}
