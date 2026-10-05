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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIInputPaneTest {
    @Test
    fun dockedOcclusionConvertsClientDipsToBottomPixels() {
        val bottomInset = calculateWinUIImeBottomInset(
            rootHeightDp = 800f,
            occludedRect = WinUIInputPaneOccludedRect(
                x = 0f,
                y = 500f,
                width = 1200f,
                height = 300f,
            ),
            density = 1.25f,
        )

        assertEquals(375, bottomInset)
    }

    @Test
    fun occlusionExtendingPastClientBottomUsesOnlyVisibleOverlap() {
        val bottomInset = calculateWinUIImeBottomInset(
            rootHeightDp = 800f,
            occludedRect = WinUIInputPaneOccludedRect(
                x = 0f,
                y = 600f,
                width = 1200f,
                height = 300f,
            ),
            density = 1.5f,
        )

        assertEquals(300, bottomInset)
    }

    @Test
    fun floatingAndInvalidOcclusionDoNotProduceImeInsets() {
        val floating = WinUIInputPaneOccludedRect(
            x = 200f,
            y = 400f,
            width = 600f,
            height = 200f,
        )
        val empty = floating.copy(y = 800f, height = 0f)
        val invalid = floating.copy(y = Float.NaN)

        assertEquals(0, calculateWinUIImeBottomInset(800f, floating, 1.25f))
        assertEquals(0, calculateWinUIImeBottomInset(800f, empty, 1.25f))
        assertEquals(0, calculateWinUIImeBottomInset(800f, invalid, 1.25f))
        assertEquals(0, calculateWinUIImeBottomInset(-1f, floating, 1.25f))
        assertEquals(0, calculateWinUIImeBottomInset(800f, floating, 0f))
    }

    @Test
    fun controllerDelegatesShowAndHideToInputPane() {
        val pane = FakeWinUIInputPaneAdapter(
            tryShowResult = true,
            tryHideResult = false,
        )
        val controller = WinUIInputPaneController(pane)

        assertTrue(controller.show())
        assertFalse(controller.hide())
        assertEquals(1, pane.tryShowCount)
        assertEquals(1, pane.tryHideCount)
    }

    @Test
    fun disposeRemovesBothEventRegistrationsExactlyOnce() {
        val pane = FakeWinUIInputPaneAdapter()
        val controller = WinUIInputPaneController(pane)

        controller.dispose()
        controller.dispose()

        assertEquals(1, pane.showingRemovalCount)
        assertEquals(1, pane.hidingRemovalCount)
        assertEquals(1, pane.disposeCount)
        assertFalse(controller.show())
        assertFalse(controller.hide())
        assertEquals(0, pane.tryShowCount)
        assertEquals(0, pane.tryHideCount)
    }

    @Test
    fun showingAndHidingPublishOcclusionChanges() {
        val pane = FakeWinUIInputPaneAdapter()
        val changes = mutableListOf<WinUIInputPaneOccludedRect?>()
        WinUIInputPaneController(pane, changes::add)
        val occludedRect = WinUIInputPaneOccludedRect(
            x = 0f,
            y = 500f,
            width = 800f,
            height = 300f,
        )

        pane.fireShowing(occludedRect)
        pane.fireHiding()

        assertEquals(listOf(occludedRect, null), changes)
    }

    @Test
    fun controllerPublishesCurrentOcclusionWhenInputPaneIsAlreadyVisible() {
        val occludedRect = WinUIInputPaneOccludedRect(
            x = 0f,
            y = 500f,
            width = 800f,
            height = 300f,
        )
        val pane = FakeWinUIInputPaneAdapter(currentOcclusion = occludedRect)
        val changes = mutableListOf<WinUIInputPaneOccludedRect?>()

        WinUIInputPaneController(pane, changes::add)

        assertEquals(listOf<WinUIInputPaneOccludedRect?>(occludedRect), changes)
    }
}

internal class FakeWinUIInputPaneAdapter(
    private val tryShowResult: Boolean = true,
    private val tryHideResult: Boolean = true,
    private val currentOcclusion: WinUIInputPaneOccludedRect? = null,
) : WinUIInputPaneAdapter {
    var tryShowCount: Int = 0
        private set
    var tryHideCount: Int = 0
        private set
    var showingRemovalCount: Int = 0
        private set
    var hidingRemovalCount: Int = 0
        private set
    var disposeCount: Int = 0
        private set
    private var showingHandler: ((WinUIInputPaneOccludedRect) -> Unit)? = null
    private var hidingHandler: (() -> Unit)? = null

    override fun tryShow(): Boolean {
        tryShowCount += 1
        return tryShowResult
    }

    override fun tryHide(): Boolean {
        tryHideCount += 1
        return tryHideResult
    }

    override fun currentOccludedRect(): WinUIInputPaneOccludedRect? = currentOcclusion

    override fun addShowing(
        handler: (WinUIInputPaneOccludedRect) -> Unit,
    ): WinUIInputPaneEventRegistration {
        showingHandler = handler
        return WinUIInputPaneEventRegistration {
            showingRemovalCount += 1
            showingHandler = null
        }
    }

    override fun addHiding(
        handler: () -> Unit,
    ): WinUIInputPaneEventRegistration {
        hidingHandler = handler
        return WinUIInputPaneEventRegistration {
            hidingRemovalCount += 1
            hidingHandler = null
        }
    }

    override fun dispose() {
        disposeCount += 1
    }

    fun fireShowing(rect: WinUIInputPaneOccludedRect) {
        showingHandler?.invoke(rect)
    }

    fun fireHiding() {
        hidingHandler?.invoke()
    }
}
