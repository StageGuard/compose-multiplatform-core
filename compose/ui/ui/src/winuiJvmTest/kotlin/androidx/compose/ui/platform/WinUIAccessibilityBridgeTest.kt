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

import androidx.collection.mutableIntObjectMapOf
import androidx.compose.ui.WinUISkikoTestBase
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.semantics.EmptySemanticsModifier
import androidx.compose.ui.semantics.SemanticsOwner
import org.jetbrains.skiko.winui.WinUIAccessibilityChangeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WinUIAccessibilityBridgeTest : WinUISkikoTestBase() {
    @Test
    fun recordsInvalidationWithoutSchedulingWhenAccessibilityIsDisabled() {
        val scheduled = mutableListOf<() -> Unit>()
        val bridge = createBridge(scheduled = scheduled)
        val owner = createSemanticsOwner()

        bridge.onSemanticsChange(owner)

        val state = bridge.stateForTest()
        assertTrue(state.currentSemanticsNodesInvalidated)
        assertTrue(state.pendingSemanticsChange)
        assertFalse(state.hasPendingFlush)
        assertEquals(0, scheduled.size)
    }

    @Test
    fun productionProviderAttachmentEnablesAutomaticFlushesWithoutSnapshot() {
        val scheduled = mutableListOf<() -> Unit>()
        val updates = mutableListOf<WinUIAccessibilityUpdate>()
        val bridge = createBridge(scheduled = scheduled, updates = updates)
        val owner = createSemanticsOwner()

        bridge.onAccessibilityProviderAttached()
        bridge.onSemanticsChange(owner)

        assertTrue(bridge.stateForTest().isAccessibilityProviderAttached)
        assertEquals(1, scheduled.size)

        scheduled.single().invoke()

        assertEquals(1, updates.size)
        assertSame(owner, updates.single().semanticsOwner)
    }

    @Test
    fun productionProviderAttachmentWithoutPendingChangesDoesNotSchedule() {
        val scheduled = mutableListOf<() -> Unit>()
        val bridge = createBridge(scheduled = scheduled)

        bridge.onAccessibilityProviderAttached()

        assertTrue(bridge.stateForTest().isAccessibilityProviderAttached)
        assertFalse(bridge.stateForTest().hasPendingFlush)
        assertEquals(0, scheduled.size)
    }

    @Test
    fun pendingChangeSchedulesWhenProductionProviderAttaches() {
        val scheduled = mutableListOf<() -> Unit>()
        val updates = mutableListOf<WinUIAccessibilityUpdate>()
        val bridge = createBridge(scheduled = scheduled, updates = updates)
        val owner = createSemanticsOwner()

        bridge.onSemanticsChange(owner)
        assertEquals(0, scheduled.size)

        bridge.onAccessibilityProviderAttached()
        assertEquals(1, scheduled.size)

        scheduled.single().invoke()
        assertSame(owner, updates.single().semanticsOwner)
    }

    @Test
    fun snapshotDoesNotAttachProductionProvider() {
        val scheduled = mutableListOf<() -> Unit>()
        val bridge = createBridge(scheduled = scheduled)

        bridge.snapshot()

        assertFalse(bridge.stateForTest().isAccessibilityProviderAttached)
        assertEquals(0, scheduled.size)
    }

    @Test
    fun disposingBeforeProductionFlushSuppressesDelivery() {
        val scheduled = mutableListOf<() -> Unit>()
        val canceled = mutableListOf<Any?>()
        val updates = mutableListOf<WinUIAccessibilityUpdate>()
        val bridge =
            createBridge(
                scheduled = scheduled,
                canceled = canceled,
                updates = updates,
            )
        val owner = createSemanticsOwner()

        bridge.onAccessibilityProviderAttached()
        bridge.onSemanticsChange(owner)
        bridge.dispose()

        assertEquals(1, canceled.size)
        scheduled.single().invoke()
        assertEquals(0, updates.size)
    }

    @Test
    fun detachingProductionProviderCancelsFlushButKeepsPendingChange() {
        val scheduled = mutableListOf<() -> Unit>()
        val canceled = mutableListOf<Any?>()
        val updates = mutableListOf<WinUIAccessibilityUpdate>()
        val bridge =
            createBridge(
                scheduled = scheduled,
                canceled = canceled,
                updates = updates,
            )
        val owner = createSemanticsOwner()

        bridge.onAccessibilityProviderAttached()
        bridge.onSemanticsChange(owner)
        bridge.onAccessibilityProviderDetached()

        assertFalse(bridge.stateForTest().isAccessibilityProviderAttached)
        assertFalse(bridge.stateForTest().hasPendingFlush)
        assertTrue(bridge.stateForTest().pendingSemanticsChange)
        assertEquals(1, canceled.size)

        scheduled.single().invoke()
        assertEquals(0, updates.size)

        bridge.onAccessibilityProviderAttached()
        scheduled.last().invoke()
        assertSame(owner, updates.single().semanticsOwner)
    }

    @Test
    fun batchesSemanticsLayoutAndScrollChanges() {
        val scheduled = mutableListOf<() -> Unit>()
        val updates = mutableListOf<WinUIAccessibilityUpdate>()
        val bridge = createBridge(
            scheduled = scheduled,
            updates = updates,
        )
        val owner = createSemanticsOwner()

        bridge.setAccessibilityEventBatchIntervalMillis(250L)
        bridge.forceAccessibilityForTesting(true)
        bridge.onSemanticsChange(owner)
        bridge.onLayoutChange(owner, semanticsId = 11)
        bridge.onLayoutChange(owner, semanticsId = 11)
        bridge.onLayoutChange(owner, semanticsId = 12)
        bridge.onScrollChanged(Offset(1f, 2f))
        bridge.onScrollChanged(Offset(3f, 4f))

        assertEquals(1, scheduled.size)
        assertTrue(bridge.stateForTest().hasPendingFlush)

        scheduled.single().invoke()

        val update = updates.single()
        assertSame(owner, update.semanticsOwner)
        assertTrue(update.semanticsChanged)
        assertEquals(listOf(11, 12), update.layoutChangedSemanticsIds)
        assertEquals(Offset(4f, 6f), update.scrollDelta)
        assertEquals(WinUIAccessibilityChangeType.STRUCTURE_CHANGED, update.change.type)
        assertEquals(11L, update.change.nodeId)
        assertFalse(bridge.stateForTest().currentSemanticsNodesInvalidated)
        assertFalse(bridge.stateForTest().hasPendingFlush)
    }

    @Test
    fun emitsNodeUpdatedChangeForLayoutOnlyFlush() {
        val scheduled = mutableListOf<() -> Unit>()
        val updates = mutableListOf<WinUIAccessibilityUpdate>()
        val bridge = createBridge(
            scheduled = scheduled,
            updates = updates,
        )
        val owner = createSemanticsOwner()

        bridge.forceAccessibilityForTesting(true)
        bridge.onLayoutChange(owner, semanticsId = 21)

        scheduled.single().invoke()

        val update = updates.single()
        assertFalse(update.semanticsChanged)
        assertEquals(listOf(21), update.layoutChangedSemanticsIds)
        assertEquals(null, update.scrollDelta)
        assertEquals(WinUIAccessibilityChangeType.NODE_UPDATED, update.change.type)
        assertEquals(21L, update.change.nodeId)
    }

    @Test
    fun emitsValueChangedChangeForScrollOnlyFlush() {
        val scheduled = mutableListOf<() -> Unit>()
        val updates = mutableListOf<WinUIAccessibilityUpdate>()
        val bridge = createBridge(
            scheduled = scheduled,
            updates = updates,
        )

        bridge.forceAccessibilityForTesting(true)
        bridge.onScrollChanged(Offset(5f, 7f))

        scheduled.single().invoke()

        val update = updates.single()
        assertSame(null, update.semanticsOwner)
        assertFalse(update.semanticsChanged)
        assertEquals(emptyList(), update.layoutChangedSemanticsIds)
        assertEquals(Offset(5f, 7f), update.scrollDelta)
        assertEquals(WinUIAccessibilityChangeType.VALUE_CHANGED, update.change.type)
        assertEquals(null, update.change.nodeId)
    }

    @Test
    fun disablingAccessibilityCancelsPendingFlushButKeepsInvalidation() {
        val scheduled = mutableListOf<() -> Unit>()
        val canceled = mutableListOf<Any?>()
        val bridge = createBridge(
            scheduled = scheduled,
            canceled = canceled,
        )
        val owner = createSemanticsOwner()

        bridge.forceAccessibilityForTesting(true)
        bridge.onSemanticsChange(owner)
        bridge.forceAccessibilityForTesting(false)

        assertEquals(1, canceled.size)
        assertFalse(bridge.stateForTest().hasPendingFlush)
        assertTrue(bridge.stateForTest().currentSemanticsNodesInvalidated)
    }

    private fun createBridge(
        scheduled: MutableList<() -> Unit>,
        canceled: MutableList<Any?> = mutableListOf(),
        updates: MutableList<WinUIAccessibilityUpdate> = mutableListOf(),
    ): WinUIAccessibilityBridge =
        WinUIAccessibilityBridge(
            postDelayed = { _, block ->
                scheduled += block
                block
            },
            removePost = { canceled += it },
            onUpdate = { updates += it },
        )

    private fun createSemanticsOwner(): SemanticsOwner {
        val root = LayoutNode().also {
            it.measurePolicy = RootMeasurePolicy
        }
        return SemanticsOwner(root, EmptySemanticsModifier(), mutableIntObjectMapOf())
    }
}
