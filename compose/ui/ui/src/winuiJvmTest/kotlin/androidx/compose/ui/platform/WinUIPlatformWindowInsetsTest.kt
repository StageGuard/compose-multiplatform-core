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
    fun titleBarInsetsAreExposedAsCaptionAndSystemBars() {
        val insets = WinUIPlatformWindowInsets(
            captionBarHeight = 32,
            captionBarLeftPadding = 96,
            captionBarRightPadding = 140,
        )

        insets.captionBar.assertInsets(left = 96, top = 32, right = 140, bottom = 0)
        assertEquals(96, insets.captionBarLeftPadding)
        assertEquals(140, insets.captionBarRightPadding)
        insets.systemBars.assertInsets(left = 96, top = 32, right = 140, bottom = 0)
        insets.safeDrawing.assertInsets(left = 96, top = 32, right = 140, bottom = 0)
        insets.safeContent.assertInsets(left = 96, top = 32, right = 140, bottom = 0)
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
    fun excludingSafeInsetsRemovesCaptionBarFromSafeAndSystemInsets() {
        val insets = WinUIPlatformWindowInsets(
            captionBarHeight = 32,
            captionBarLeftPadding = 96,
            captionBarRightPadding = 140,
        )

        val excluded = insets.excluding(safeInsets = true, ime = false)

        excluded.captionBar.assertInsets()
        assertEquals(0, excluded.captionBarLeftPadding)
        assertEquals(0, excluded.captionBarRightPadding)
        excluded.systemBars.assertInsets()
        excluded.safeDrawing.assertInsets()
        excluded.safeContent.assertInsets()
        assertSame(excluded, excluded.excluding(safeInsets = false, ime = false))
    }

    @Test
    fun excludingImeIsStableWhenWinUIDesktopImeInsetsAreZero() {
        val insets = WinUIPlatformWindowInsets(captionBarHeight = 32)

        val excluded = insets.excluding(safeInsets = false, ime = true)

        assertSame(insets, excluded)
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
