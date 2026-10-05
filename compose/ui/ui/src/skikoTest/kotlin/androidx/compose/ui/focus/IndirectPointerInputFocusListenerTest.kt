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

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.indirect.IndirectPointerEvent
import androidx.compose.ui.input.indirect.IndirectPointerInputModifierNode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.PlatformLayersComposeScene
import androidx.compose.ui.unit.IntSize
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class IndirectPointerInputFocusListenerTest {
    @Test
    fun sceneOwnerCancelsPreviousBranchWhenFocusMoves() =
        runTest(StandardTestDispatcher()) {
            createSceneFixture(coroutineContext).use { fixture ->
                val previousChild = CancellationRecorder()
                val currentChild = CancellationRecorder()
                val previousFocusRequester = FocusRequester()
                val currentFocusRequester = FocusRequester()
                fixture.scene.setContent {
                    Row {
                        Box(
                            Modifier.indirectInput(previousChild)
                                .focusRequester(previousFocusRequester)
                                .focusable()
                        )
                        Box(
                            Modifier.indirectInput(currentChild)
                                .focusRequester(currentFocusRequester)
                                .focusable()
                        )
                    }
                }

                previousFocusRequester.requestFocus()
                currentFocusRequester.requestFocus()

                assertEquals(1, previousChild.cancellations)
                assertEquals(0, currentChild.cancellations)
            }
        }

    @Test
    fun movingFocusToSiblingCancelsOnlyPreviousBranch() =
        runTest(StandardTestDispatcher()) {
            createSceneFixture(coroutineContext).use { fixture ->
                val parent = CancellationRecorder()
                val previousChild = CancellationRecorder()
                val currentChild = CancellationRecorder()
                val previousFocusTarget = FocusTargetModifierNode()
                val currentFocusTarget = FocusTargetModifierNode()
                fixture.scene.setContent {
                    Box(Modifier.indirectInput(parent)) {
                        Row {
                            Box(
                                Modifier.indirectInput(previousChild)
                                    .focusTarget(previousFocusTarget)
                            )
                            Box(
                                Modifier.indirectInput(currentChild)
                                    .focusTarget(currentFocusTarget)
                            )
                        }
                    }
                }

                IndirectPointerInputFocusListener.onFocusChanged(
                    previousFocusTarget,
                    currentFocusTarget,
                )

                assertEquals(0, parent.cancellations)
                assertEquals(1, previousChild.cancellations)
                assertEquals(0, currentChild.cancellations)
            }
        }

    @Test
    fun movingFocusFromChildToParentCancelsOnlyChild() =
        runTest(StandardTestDispatcher()) {
            createSceneFixture(coroutineContext).use { fixture ->
                val parent = CancellationRecorder()
                val child = CancellationRecorder()
                val parentFocusTarget = FocusTargetModifierNode()
                val childFocusTarget = FocusTargetModifierNode()
                fixture.scene.setContent {
                    Box(
                        Modifier.indirectInput(parent)
                            .focusTarget(parentFocusTarget)
                    ) {
                        Box(
                            Modifier.indirectInput(child)
                                .focusTarget(childFocusTarget)
                        )
                    }
                }

                IndirectPointerInputFocusListener.onFocusChanged(
                    childFocusTarget,
                    parentFocusTarget,
                )

                assertEquals(0, parent.cancellations)
                assertEquals(1, child.cancellations)
            }
        }

    @Test
    fun clearingFocusCancelsEntirePreviousChain() = runTest(StandardTestDispatcher()) {
        createSceneFixture(coroutineContext).use { fixture ->
            val parent = CancellationRecorder()
            val child = CancellationRecorder()
            val childFocusTarget = FocusTargetModifierNode()
            fixture.scene.setContent {
                Box(Modifier.indirectInput(parent)) {
                    Box(
                        Modifier.indirectInput(child)
                            .focusTarget(childFocusTarget)
                    )
                }
            }

            IndirectPointerInputFocusListener.onFocusChanged(childFocusTarget, null)

            assertEquals(1, parent.cancellations)
            assertEquals(1, child.cancellations)
        }
    }

    @Test
    fun acquiringInitialFocusDoesNotCancelNewChain() = runTest(StandardTestDispatcher()) {
        createSceneFixture(coroutineContext).use { fixture ->
            val parent = CancellationRecorder()
            val child = CancellationRecorder()
            val childFocusTarget = FocusTargetModifierNode()
            fixture.scene.setContent {
                Box(Modifier.indirectInput(parent)) {
                    Box(
                        Modifier.indirectInput(child)
                            .focusTarget(childFocusTarget)
                    )
                }
            }

            IndirectPointerInputFocusListener.onFocusChanged(null, childFocusTarget)

            assertEquals(0, parent.cancellations)
            assertEquals(0, child.cancellations)
        }
    }
}

private class SceneFixture(
    val scene: ComposeScene,
    private val frameRecomposer: FrameRecomposer,
) : AutoCloseable {
    override fun close() {
        scene.close()
        frameRecomposer.close()
    }
}

private fun createSceneFixture(coroutineContext: CoroutineContext): SceneFixture {
    val frameRecomposer = FrameRecomposer(coroutineContext)
    return SceneFixture(
        scene =
            PlatformLayersComposeScene(
                frameRecomposer = frameRecomposer,
                size = IntSize(100, 100),
            ),
        frameRecomposer = frameRecomposer,
    )
}

private fun Modifier.indirectInput(recorder: CancellationRecorder): Modifier =
    this then IndirectInputElement(recorder)

private data class IndirectInputElement(
    val recorder: CancellationRecorder,
) : ModifierNodeElement<IndirectInputNode>() {
    override fun create(): IndirectInputNode = IndirectInputNode(recorder)

    override fun update(node: IndirectInputNode) {
        node.recorder = recorder
    }
}

private class IndirectInputNode(
    var recorder: CancellationRecorder,
) : Modifier.Node(), IndirectPointerInputModifierNode {
    override fun onIndirectPointerEvent(event: IndirectPointerEvent, pass: PointerEventPass) = Unit

    override fun onCancelIndirectPointerInput() {
        recorder.cancellations += 1
    }
}

private fun Modifier.focusTarget(focusTarget: FocusTargetModifierNode): Modifier =
    this then FocusTargetElement(focusTarget)

private data class FocusTargetElement(
    val focusTarget: FocusTargetModifierNode,
) : ModifierNodeElement<DelegatingFocusTargetNode>() {
    override fun create(): DelegatingFocusTargetNode = DelegatingFocusTargetNode(focusTarget)

    override fun update(node: DelegatingFocusTargetNode) = Unit
}

private class DelegatingFocusTargetNode(
    focusTarget: FocusTargetModifierNode,
) : DelegatingNode() {
    init {
        delegate(focusTarget)
    }
}

private class CancellationRecorder {
    var cancellations = 0
}
