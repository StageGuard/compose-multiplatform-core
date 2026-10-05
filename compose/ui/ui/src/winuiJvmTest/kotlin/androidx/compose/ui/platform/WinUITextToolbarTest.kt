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

import androidx.compose.ui.geometry.Rect
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinUITextToolbarTest {
    @Test
    fun closeHidesMenuAndMakesCallbacksInert() {
        var copyRequests = 0
        val toolbar = WinUITextToolbar()
        toolbar.showMenu(
            rect = Rect(1f, 2f, 3f, 4f),
            onCopyRequested = { copyRequests++ },
            onPasteRequested = null,
            onCutRequested = null,
            onSelectAllRequested = null,
            onAutofillRequested = null,
        )
        val shownMenu = checkNotNull(toolbar.menuForTest())

        toolbar.close()
        toolbar.close()

        shownMenu.onCopyRequested?.invoke()
        assertEquals(0, copyRequests)
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
        assertNull(toolbar.menuForTest())

        toolbar.showMenu(
            rect = Rect.Zero,
            onCopyRequested = { copyRequests++ },
            onPasteRequested = null,
            onCutRequested = null,
            onSelectAllRequested = null,
            onAutofillRequested = null,
        )
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
    }

    @Test
    fun ownerClosesWinUITextToolbarDuringDisposal() {
        val ownerSource = findWinUIUiModuleRoot()
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/node/WinUIOwner.winui.kt")
            .readText()

        assertTrue(ownerSource.contains("(textToolbar as? WinUITextToolbar)?.close()"))
    }
}
