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

package androidx.compose.ui.window

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIPopupDialogBehaviorTest {
    @Test
    fun backPressUsesLatestCallbackForEachEvent() {
        var calls = emptyList<String>()
        val state = WinUIPopupDismissState(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            onDismissRequest = { calls += "first" },
        )
        state.update(onDismissRequest = { calls += "latest" })

        assertTrue(state.onBackPress())
        assertTrue(state.onBackPress())
        assertEquals(listOf("latest", "latest"), calls)
    }

    @Test
    fun missingCallbackDoesNotConsumeFutureDismissRequest() {
        var calls = 0
        val state = WinUIPopupDismissState(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            onDismissRequest = null,
        )

        assertFalse(state.onBackPress())

        state.update(onDismissRequest = { calls++ })

        assertTrue(state.onBackPress())
        assertEquals(1, calls)
    }

    @Test
    fun flyoutBackKeyIsHandledEvenWhenDismissIsDisabled() {
        var calls = 0
        val state = WinUIPopupDismissState(
            dismissOnBackPress = false,
            dismissOnClickOutside = true,
            onDismissRequest = { calls++ },
        )

        assertTrue(state.onFlyoutBackKey())
        assertEquals(0, calls)

        state.update(dismissOnBackPress = true)

        assertTrue(state.onFlyoutBackKey())
        assertEquals(1, calls)
    }

    @Test
    fun nativeFlyoutCloseIsCancelledWhilePopupRemainsDeclared() {
        var calls = 0
        val state = WinUIPopupDismissState(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            onDismissRequest = { calls++ },
        )

        assertTrue(state.onNativeClosing(shouldBeOpen = true))
        assertEquals(0, calls)

        state.update(dismissOnClickOutside = true)

        assertTrue(state.onNativeClosing(shouldBeOpen = true))
        assertEquals(1, calls)
        assertFalse(state.onNativeClosing(shouldBeOpen = false))
        assertEquals(1, calls)
    }

    @Test
    fun disabledDismissPropertiesDoNotDispatch() {
        var calls = 0
        val state = WinUIPopupDismissState(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            onDismissRequest = { calls++ },
        )

        assertFalse(state.onBackPress())
        assertFalse(state.onOutsidePointer(Offset(200f, 200f), IntRect(0, 0, 100, 100)))
        assertEquals(0, calls)
    }

    @Test
    fun outsidePointerOnlyDismissesOutsideBounds() {
        var calls = 0
        val state = WinUIPopupDismissState(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            onDismissRequest = { calls++ },
        )

        assertFalse(state.onOutsidePointer(Offset(50f, 50f), IntRect(0, 0, 100, 100)))
        assertTrue(state.onOutsidePointer(Offset(200f, 200f), IntRect(0, 0, 100, 100)))
        assertEquals(1, calls)
    }

    @Test
    fun dialogOutsidePointerUsesCenteredContentBounds() {
        var calls = 0
        val state = WinUIPopupDismissState(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            onDismissRequest = { calls++ },
        )
        val dialogContentBounds = IntRect(300, 200, 700, 600)

        assertFalse(state.onOutsidePointer(Offset(500f, 400f), dialogContentBounds))
        assertTrue(state.onOutsidePointer(Offset(100f, 100f), dialogContentBounds))
        assertEquals(1, calls)
    }

    @Test
    fun dialogHostRoutesPointerPressesAgainstContentBounds() {
        val source = winUISource("Dialog.winui.kt")

        assertTrue(
            source.contains("dialogContentBoundsInRoot") &&
                source.contains("pointerPressed.add") &&
                source.contains("winUIPositionToComposeOffset") &&
                source.contains("onOutsidePointer"),
            "WinUI Dialog must route scaled pointer presses against dialog content bounds.",
        )
    }

    @Test
    fun bothPopupHostsApplyPlatformDefaultWidthPolicy() {
        val source = winUISource("Popup.winui.kt")

        assertTrue(
            source.split("winUIPopupMaxWidth(").size - 1 >= 3,
            "WinUI canvas and window popups must both apply the platform default width policy.",
        )
    }

    @Test
    fun dialogDefaultWidthIsConstrained() {
        val unconstrained = winUIDialogMaxWidth(
            windowWidth = 1200,
            availableWidth = 1200,
            usePlatformDefaultWidth = false,
        )
        val constrained = winUIDialogMaxWidth(
            windowWidth = 1200,
            availableWidth = 1200,
            usePlatformDefaultWidth = true,
        )

        assertEquals(1200, unconstrained)
        assertTrue(constrained < unconstrained)
        assertEquals(constrained, winUIDialogMaxWidth(1200, 1200, true))
    }

    private fun winUISource(fileName: String): String =
        findUiModuleRoot()
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/window/$fileName")
            .readText()

    private fun findUiModuleRoot(): Path {
        val start = Paths.get("").toAbsolutePath()
        generateSequence(start) { it.parent }.forEach { candidate ->
            val direct = candidate.resolve("src/winuiMain/kotlin")
            if (direct.exists() && candidate.name == "ui") return candidate

            val fromRepoRoot = candidate.resolve("compose/ui/ui/src/winuiMain/kotlin")
            if (fromRepoRoot.exists()) return candidate.resolve("compose/ui/ui")
        }
        error("Could not find compose/ui/ui module root from $start.")
    }
}
