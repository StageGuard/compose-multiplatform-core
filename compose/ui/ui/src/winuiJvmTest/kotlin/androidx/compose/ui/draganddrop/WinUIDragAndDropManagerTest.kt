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

package androidx.compose.ui.draganddrop

import androidx.compose.runtime.retain.ForgetfulRetainedValuesStore
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.WinUISkikoTestBase
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.PlatformFocusOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.WinUIOwner
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinUIDragAndDropManagerTest : WinUISkikoTestBase() {
    private lateinit var manager: WinUIDragAndDropManager

    @BeforeTest
    fun setUp() {
        manager = WinUIDragAndDropManager()
    }

    @AfterTest
    fun tearDown() {
        if (::manager.isInitialized) {
            manager.clearTargetInterestForTest()
            manager.setStarterForTest(null)
        }
    }

    @Test
    fun exposesRootDragAndDropModifier() {
        assertNotEquals(Modifier, manager.modifier)
    }

    @Test
    fun tracksInterestedTargetsForCurrentSession() {
        val target = TestDragAndDropTarget()

        assertFalse(manager.isInterestedTarget(target))

        manager.registerTargetInterest(target)

        assertTrue(manager.isInterestedTarget(target))

        manager.clearTargetInterestForTest()

        assertFalse(manager.isInterestedTarget(target))
    }

    @Test
    @OptIn(ExperimentalComposeUiApi::class)
    fun requestTransferRunsAttachedSourceThroughStarter() {
        var sourceOffset: Offset? = null
        var starterTransferData: DragAndDropTransferData? = null
        var starterDecorationSize: Size? = null
        var starterCalls = 0
        val sourceNode = DragAndDropNode(
            onStartTransfer = { offset ->
                sourceOffset = offset
                startDragAndDropTransfer(
                    transferData = DragAndDropTransferData("payload"),
                    decorationSize = Size(10f, 20f),
                    drawDragDecoration = {},
                )
            }
        )
        val owner = createOwner(manager)
        try {
            val child = LayoutNode().also {
                it.modifier = SourceDragAndDropElement(sourceNode)
                it.measurePolicy = RootMeasurePolicy
            }
            owner.root.insertAt(0, child)
            owner.setWindowContainerSize(IntSize(100, 100))
            owner.measureAndLayout()

            assertFalse(manager.isRequestDragAndDropTransferRequired)

            sourceNode.requestDragAndDropTransfer(Offset.Unspecified)

            assertNull(sourceOffset)
            assertEquals(0, starterCalls)

            manager.setStarterForTest(
                WinUIDragAndDropStarter { transferData, decorationSize, _ ->
                    starterCalls += 1
                    starterTransferData = transferData
                    starterDecorationSize = decorationSize
                    true
                }
            )

            assertTrue(manager.isRequestDragAndDropTransferRequired)

            sourceNode.requestDragAndDropTransfer(Offset.Unspecified)

            assertEquals(Offset.Unspecified, sourceOffset)
            assertEquals("payload", starterTransferData?.nativeTransferData)
            assertEquals(Size(10f, 20f), starterDecorationSize)
            assertEquals(1, starterCalls)
        } finally {
            owner.dispose()
        }
    }

    @Test
    @OptIn(ExperimentalComposeUiApi::class)
    fun dispatchesDragSessionEventsToInterestedTarget() {
        val target = TestDragAndDropTarget()
        val owner = createOwner(manager)
        try {
            val targetNode = DragAndDropNode(
                onDropTargetValidate = { target }
            )
            val child = LayoutNode().also {
                it.modifier = TargetDragAndDropElement(targetNode)
                it.measurePolicy = fixedMeasurePolicy(100, 100)
            }
            owner.root.insertAt(0, child)
            owner.setWindowContainerSize(IntSize(200, 200))
            owner.measureAndLayout()

            val startEvent = DragAndDropEvent(nativeEvent = "start")
            val moveEvent = DragAndDropEvent(
                nativeEvent = "move",
                positionInRootImpl = Offset(10f, 10f),
            )
            val changedEvent = DragAndDropEvent(nativeEvent = "changed")
            val dropEvent = DragAndDropEvent(nativeEvent = "drop")
            val endEvent = DragAndDropEvent(nativeEvent = "end")

            assertTrue(manager.onDragStarted(startEvent))
            assertTrue(manager.isInterestedTarget(targetNode))

            manager.onDragMoved(moveEvent)
            manager.onDragChanged(changedEvent)

            assertTrue(manager.onDrop(dropEvent))

            manager.onDragEnded(endEvent)

            assertEquals(
                listOf("started", "entered", "moved", "changed", "drop", "ended"),
                target.events,
            )
            assertFalse(manager.isInterestedTarget(target))
            assertFalse(manager.isInterestedTarget(targetNode))
        } finally {
            owner.dispose()
        }
    }

    @Test
    @OptIn(ExperimentalComposeUiApi::class)
    fun dragExitDispatchesExitedToCurrentTarget() {
        val target = TestDragAndDropTarget()
        val owner = createOwner(manager)
        try {
            val child = LayoutNode().also {
                it.modifier = TargetDragAndDropElement(
                    DragAndDropNode(
                        onDropTargetValidate = { target }
                    )
                )
                it.measurePolicy = fixedMeasurePolicy(100, 100)
            }
            owner.root.insertAt(0, child)
            owner.setWindowContainerSize(IntSize(200, 200))
            owner.measureAndLayout()

            assertTrue(manager.onDragStarted(DragAndDropEvent()))
            manager.onDragMoved(
                DragAndDropEvent(positionInRootImpl = Offset(10f, 10f))
            )
            manager.onDragExited(DragAndDropEvent())

            assertEquals(listOf("started", "entered", "moved", "exited"), target.events)
        } finally {
            owner.dispose()
        }
    }
}

private class TestDragAndDropTarget : DragAndDropTarget {
    val events = mutableListOf<String>()

    override fun onStarted(event: DragAndDropEvent) {
        events += "started"
    }

    override fun onEntered(event: DragAndDropEvent) {
        events += "entered"
    }

    override fun onMoved(event: DragAndDropEvent) {
        events += "moved"
    }

    override fun onChanged(event: DragAndDropEvent) {
        events += "changed"
    }

    override fun onExited(event: DragAndDropEvent) {
        events += "exited"
    }

    override fun onDrop(event: DragAndDropEvent): Boolean {
        events += "drop"
        return true
    }

    override fun onEnded(event: DragAndDropEvent) {
        events += "ended"
    }
}

private class SourceDragAndDropElement(
    private val node: DragAndDropNode,
) : ModifierNodeElement<DragAndDropNode>() {
    override fun create(): DragAndDropNode = node

    override fun update(node: DragAndDropNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "SourceDragAndDropNode"
    }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = node.hashCode()
}

private class TargetDragAndDropElement(
    private val node: DragAndDropNode,
) : ModifierNodeElement<DragAndDropNode>() {
    override fun create(): DragAndDropNode = node

    override fun update(node: DragAndDropNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "TargetDragAndDropNode"
    }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = node.hashCode()
}

private fun createOwner(manager: WinUIDragAndDropManager): WinUIOwner {
    val root = LayoutNode().also {
        it.measurePolicy = RootMeasurePolicy
    }
    return WinUIOwner(
        root = root,
        platformFocusOwner = TestPlatformFocusOwner,
        retainedValuesStore = ForgetfulRetainedValuesStore,
        winUIDragAndDropManager = manager,
    )
}

private object TestPlatformFocusOwner : PlatformFocusOwner {
    override fun requestOwnerFocus(
        focusDirection: FocusDirection?,
        previouslyFocusedRect: Rect?,
    ): Boolean = true

    override fun clearOwnerFocus() = Unit

    override fun moveFocusInChildren(focusDirection: FocusDirection): Boolean = false

    override fun getEmbeddedViewFocusRect(): Rect? = null
}

private fun fixedMeasurePolicy(width: Int, height: Int) = MeasurePolicy { _, _ ->
    layout(width, height) {}
}
