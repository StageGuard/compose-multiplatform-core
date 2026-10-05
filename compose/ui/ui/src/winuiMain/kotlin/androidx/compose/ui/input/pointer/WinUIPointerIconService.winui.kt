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

package androidx.compose.ui.input.pointer

internal class WinUIPointerIconService(
    private val applyIcon: (PointerIcon) -> Unit = {},
) : PointerIconService {
    private var icon: PointerIcon? = null
    private var stylusHoverIcon: PointerIcon? = null

    override fun getIcon(): PointerIcon = icon ?: PointerIcon.Default

    override fun setIcon(value: PointerIcon?) {
        icon = value
        applyResolvedIcon()
    }

    override fun getStylusHoverIcon(): PointerIcon? = stylusHoverIcon

    override fun setStylusHoverIcon(value: PointerIcon?) {
        stylusHoverIcon = value
        applyResolvedIcon()
    }

    private fun applyResolvedIcon() {
        applyIcon(stylusHoverIcon ?: getIcon())
    }
}
