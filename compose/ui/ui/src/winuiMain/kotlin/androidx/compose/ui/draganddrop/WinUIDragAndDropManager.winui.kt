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

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

internal class WinUIDragAndDropManager : DragAndDropManager {
    private val rootDragAndDropNode = DragAndDropNode()
    private val interestedTargets = mutableSetOf<DragAndDropTarget>()
    private var installedStarter: WinUIDragAndDropStarter? = null
    private var testStarter: WinUIDragAndDropStarter? = null
    private var hasTestStarterOverride = false

    private val starter: WinUIDragAndDropStarter?
        get() = if (hasTestStarterOverride) testStarter else installedStarter

    override val modifier: Modifier = RootWinUIDragAndDropElement(rootDragAndDropNode)
    override val isRequestDragAndDropTransferRequired: Boolean
        get() = starter != null

    /**
     * Whether the last drag event was over a target that accepted the session.
     */
    internal val hasEligibleDropTarget: Boolean
        get() = rootDragAndDropNode.hasEligibleDropTarget

    override fun requestDragAndDropTransfer(node: DragAndDropNode, offset: Offset) {
        val currentStarter = starter ?: return
        var isTransferStarted = false
        val dragAndDropSourceScope = object : DragAndDropStartTransferScope {
            override fun startDragAndDropTransfer(
                transferData: DragAndDropTransferData,
                decorationSize: Size,
                drawDragDecoration: DrawScope.() -> Unit,
            ): Boolean {
                isTransferStarted = currentStarter.startDragAndDropTransfer(
                    transferData = transferData,
                    decorationSize = decorationSize,
                    drawDragDecoration = drawDragDecoration,
                )
                return isTransferStarted
            }
        }
        with(node) {
            dragAndDropSourceScope.startDragAndDropTransfer(offset) { isTransferStarted }
        }
    }

    override fun registerTargetInterest(target: DragAndDropTarget) {
        interestedTargets.add(target)
    }

    override fun isInterestedTarget(target: DragAndDropTarget): Boolean =
        interestedTargets.contains(target)

    internal fun onDragStarted(event: DragAndDropEvent): Boolean {
        val accepted = rootDragAndDropNode.acceptDragAndDropTransfer(event)
        interestedTargets.forEach { it.onStarted(event) }
        return accepted
    }

    internal fun onDragEntered(event: DragAndDropEvent) {
        rootDragAndDropNode.onEntered(event)
    }

    internal fun onDragMoved(event: DragAndDropEvent) {
        rootDragAndDropNode.onMoved(event)
    }

    internal fun onDragChanged(event: DragAndDropEvent) {
        rootDragAndDropNode.onChanged(event)
    }

    internal fun onDragExited(event: DragAndDropEvent) {
        rootDragAndDropNode.onExited(event)
    }

    internal fun onDrop(event: DragAndDropEvent): Boolean =
        rootDragAndDropNode.onDrop(event)

    internal fun onDragEnded(event: DragAndDropEvent) {
        rootDragAndDropNode.onEnded(event)
        interestedTargets.clear()
    }

    internal fun clearTargetInterestForTest() {
        interestedTargets.clear()
    }

    internal fun setStarter(starter: WinUIDragAndDropStarter) {
        installedStarter = starter
    }

    internal fun clearStarter(starter: WinUIDragAndDropStarter) {
        if (installedStarter === starter) {
            installedStarter = null
        }
    }

    internal fun setStarterForTest(starter: WinUIDragAndDropStarter?) {
        hasTestStarterOverride = true
        testStarter = starter
    }

    internal fun clearStarterOverrideForTest() {
        hasTestStarterOverride = false
        testStarter = null
    }
}
internal fun interface WinUIDragAndDropStarter {
    fun startDragAndDropTransfer(
        transferData: DragAndDropTransferData,
        decorationSize: Size,
        drawDragDecoration: DrawScope.() -> Unit,
    ): Boolean
}

private class RootWinUIDragAndDropElement(
    private val dragAndDropNode: DragAndDropNode,
) : ModifierNodeElement<DragAndDropNode>() {
    override fun create(): DragAndDropNode = dragAndDropNode

    override fun update(node: DragAndDropNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "RootWinUIDragAndDropNode"
    }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = dragAndDropNode.hashCode()
}
