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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class WinUIPlatformWindowInsetsTest {
    @Test
    fun titleBarButtonInsetsRemainSeparateFromCaptionAndSystemBars() {
        val insets = WinUIPlatformWindowInsets(
            captionBarHeight = 32,
            titleBarLeftInset = PlatformInsets(left = 96),
            titleBarRightInset = PlatformInsets(right = 140),
        )

        insets.captionBar.assertInsets(top = 32)
        insets.titleBarLeftInset.assertInsets(left = 96)
        insets.titleBarRightInset.assertInsets(right = 140)
        insets.systemBars.assertInsets(top = 32)
        insets.safeDrawing.assertInsets(top = 32)
        insets.safeContent.assertInsets(top = 32)
    }

    @Test
    fun desktopOnlyInsetsRemainZeroUntilBackedByPlatformSignals() {
        val insets = WinUIPlatformWindowInsets(captionBarHeight = 28)

        insets.ime.assertInsets()
        insets.displayCutout.assertInsets()
        insets.navigationBars.assertInsets()
        insets.statusBars.assertInsets()
        insets.systemGestures.assertInsets()
        insets.mandatorySystemGestures.assertInsets()
        insets.tappableElement.assertInsets()
        insets.waterfall.assertInsets()
        assertEquals(emptyList(), insets.displayCutouts)
        assertEquals(null, insets.cutoutPath)
    }

    @Test
    fun imeBottomInsetContributesToSafeDrawingAndSafeContent() {
        val insets = WinUIPlatformWindowInsets(
            captionBarHeight = 32,
            imeBottomInset = 420,
        )

        insets.ime.assertInsets(bottom = 420)
        insets.safeDrawing.assertInsets(top = 32, bottom = 420)
        insets.safeContent.assertInsets(top = 32, bottom = 420)
    }

    @Test
    fun excludingSafeInsetsRemovesCaptionBarFromSafeAndSystemInsets() {
        val insets = WinUIPlatformWindowInsets(
            captionBarHeight = 32,
            titleBarLeftInset = PlatformInsets(left = 96),
            titleBarRightInset = PlatformInsets(right = 140),
            imeBottomInset = 360,
        )

        val excluded = insets.excluding(safeInsets = true, ime = false)

        excluded.captionBar.assertInsets()
        excluded.titleBarLeftInset.assertInsets()
        excluded.titleBarRightInset.assertInsets()
        excluded.systemBars.assertInsets()
        excluded.ime.assertInsets(bottom = 360)
        excluded.safeDrawing.assertInsets(bottom = 360)
        excluded.safeContent.assertInsets(bottom = 360)
        assertSame(excluded, excluded.excluding(safeInsets = false, ime = false))
    }

    @Test
    fun excludingImePreservesCaptionAndTitleBarInsets() {
        val insets = WinUIPlatformWindowInsets(
            captionBarHeight = 32,
            titleBarLeftInset = PlatformInsets(left = 96),
            titleBarRightInset = PlatformInsets(right = 140),
            imeBottomInset = 360,
        )

        val excluded = insets.excluding(safeInsets = false, ime = true)

        excluded.captionBar.assertInsets(top = 32)
        excluded.titleBarLeftInset.assertInsets(left = 96)
        excluded.titleBarRightInset.assertInsets(right = 140)
        excluded.systemBars.assertInsets(top = 32)
        excluded.ime.assertInsets()
        excluded.safeDrawing.assertInsets(top = 32)
        excluded.safeContent.assertInsets(top = 32)
    }

    private fun PlatformInsets.assertInsets(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ) {
        assertEquals(left, this.left)
        assertEquals(top, this.top)
        assertEquals(right, this.right)
        assertEquals(bottom, this.bottom)
    }
}
