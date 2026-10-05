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

package androidx.compose.ui.scene

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.indirect.IndirectPointerEventPrimaryDirectionalMotionAxis
import androidx.compose.ui.input.indirect.IndirectPointerEventType
import androidx.compose.ui.input.indirect.IndirectPointerInputChange
import androidx.compose.ui.input.indirect.IndirectPointerInputModifierNode
import androidx.compose.ui.input.indirect.SkikoIndirectPointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class IndirectPointerComposeSceneTest {
    @Test
    fun dispatchesPassesToFocusedAncestorsAndReturnsConsumption() =
        runTest(StandardTestDispatcher()) {
            createSceneFixtures(coroutineContext).forEach { fixture ->
                fixture.use { activeFixture ->
                    val scene = activeFixture.scene
                    val timeline = mutableListOf<String>()
                    val parent = Recorder("parent", timeline = timeline)
                    val child = Recorder("child", consume = true, timeline = timeline)
                    val sibling = Recorder("sibling", timeline = timeline)
                    val focusRequester = FocusRequester()
                    scene.setContent {
                        Box(Modifier.fillMaxSize().recordIndirectInput(parent)) {
                            Box(
                                Modifier.fillMaxSize()
                                    .recordIndirectInput(child)
                                    .focusRequester(focusRequester)
                                    .focusable()
                            )
                            Box(Modifier.fillMaxSize().recordIndirectInput(sibling).focusable())
                        }
                    }
                    focusRequester.requestFocus()

                    assertTrue(scene.sendIndirectPointerEvent(indirectEvent()))
                    assertEquals(
                        listOf(
                            PointerEventPass.Initial,
                            PointerEventPass.Main,
                            PointerEventPass.Final,
                        ),
                        parent.passes,
                    )
                    assertEquals(
                        listOf(
                            PointerEventPass.Initial,
                            PointerEventPass.Main,
                            PointerEventPass.Final,
                        ),
                        child.passes,
                    )
                    assertEquals(emptyList(), sibling.passes)
                    assertEquals(
                        listOf(
                            "parent:Initial",
                            "child:Initial",
                            "child:Main",
                            "parent:Main",
                            "parent:Final",
                            "child:Final",
                        ),
                        timeline,
                    )
                }
            }
        }

    @Test
    fun focusedCanvasLayerWinsOverMainContent() = runTest(StandardTestDispatcher()) {
        createCanvasSceneFixture(coroutineContext).use { fixture ->
            val scene = fixture.scene
            val main = Recorder("main")
            val layer = Recorder("layer")
            val mainFocusRequester = FocusRequester()
            val layerFocusRequester = FocusRequester()
            lateinit var sceneContext: ComposeSceneContext
            lateinit var parentCompositionContext: CompositionContext
            scene.setContent {
                sceneContext = LocalComposeSceneContext.requireCurrent()
                parentCompositionContext = rememberCompositionContext()
                Box(
                    Modifier.fillMaxSize()
                        .recordIndirectInput(main)
                        .focusRequester(mainFocusRequester)
                        .focusable()
                )
            }
            mainFocusRequester.requestFocus()
            val canvasLayer =
                sceneContext.createLayer(
                    density = Density(1f),
                    layoutDirection = LayoutDirection.Ltr,
                    focusable = true,
                )
            canvasLayer.setContent(parentCompositionContext) {
                Box(
                    Modifier.fillMaxSize()
                        .recordIndirectInput(layer)
                        .focusRequester(layerFocusRequester)
                        .focusable()
                )
            }
            layerFocusRequester.requestFocus()

            assertFalse(scene.sendIndirectPointerEvent(indirectEvent()))
            assertEquals(emptyList(), main.passes)
            assertEquals(
                listOf(
                    PointerEventPass.Initial,
                    PointerEventPass.Main,
                    PointerEventPass.Final,
                ),
                layer.passes,
            )
        }
    }

    @Test
    fun canvasSceneFallsBackToMainWhenNoLayerIsFocused() =
        runTest(StandardTestDispatcher()) {
            createCanvasSceneFixture(coroutineContext).use { fixture ->
                val scene = fixture.scene
                val main = Recorder("main")
                val focusRequester = FocusRequester()
                scene.setContent {
                    Box(
                        Modifier.fillMaxSize()
                            .recordIndirectInput(main)
                            .focusRequester(focusRequester)
                            .focusable()
                    )
                }
                focusRequester.requestFocus()

                assertFalse(scene.sendIndirectPointerEvent(indirectEvent()))
                assertEquals(3, main.passes.size)
            }
        }

    @Test
    fun explicitCancelUsesOwnerOfLastPressedFrame() = runTest(StandardTestDispatcher()) {
        createCanvasSceneFixture(coroutineContext).use { fixture ->
            val scene = fixture.scene
            val first = Recorder("first")
            val second = Recorder("second")
            val firstFocusRequester = FocusRequester()
            val secondFocusRequester = FocusRequester()
            lateinit var sceneContext: ComposeSceneContext
            lateinit var parentCompositionContext: CompositionContext
            scene.setContent {
                sceneContext = LocalComposeSceneContext.requireCurrent()
                parentCompositionContext = rememberCompositionContext()
            }
            val firstLayer =
                sceneContext.createLayer(
                    density = Density(1f),
                    layoutDirection = LayoutDirection.Ltr,
                    focusable = true,
                )
            firstLayer.setContent(parentCompositionContext) {
                Box(
                    Modifier.fillMaxSize()
                        .recordIndirectInput(first)
                        .focusRequester(firstFocusRequester)
                        .focusable()
                )
            }
            firstFocusRequester.requestFocus()
            scene.sendIndirectPointerEvent(indirectEvent(type = IndirectPointerEventType.Press))

            val secondLayer =
                sceneContext.createLayer(
                    density = Density(1f),
                    layoutDirection = LayoutDirection.Ltr,
                    focusable = true,
                )
            secondLayer.setContent(parentCompositionContext) {
                Box(
                    Modifier.fillMaxSize()
                        .recordIndirectInput(second)
                        .focusRequester(secondFocusRequester)
                        .focusable()
                )
            }
            secondFocusRequester.requestFocus()

            scene.cancelIndirectPointerInput()

            assertEquals(1, first.cancellations)
            assertEquals(0, second.cancellations)
        }
    }

    @Test
    fun releaseWithoutPressedChangesEndsActiveOwner() = runTest(StandardTestDispatcher()) {
        createCanvasSceneFixture(coroutineContext).use { fixture ->
            val scene = fixture.scene
            val recorder = Recorder("layer")
            val focusRequester = FocusRequester()
            lateinit var sceneContext: ComposeSceneContext
            lateinit var parentCompositionContext: CompositionContext
            scene.setContent {
                sceneContext = LocalComposeSceneContext.requireCurrent()
                parentCompositionContext = rememberCompositionContext()
            }
            val layer =
                sceneContext.createLayer(
                    density = Density(1f),
                    layoutDirection = LayoutDirection.Ltr,
                    focusable = true,
                )
            layer.setContent(parentCompositionContext) {
                Box(
                    Modifier.fillMaxSize()
                        .recordIndirectInput(recorder)
                        .focusRequester(focusRequester)
                        .focusable()
                )
            }
            focusRequester.requestFocus()

            scene.sendIndirectPointerEvent(indirectEvent(type = IndirectPointerEventType.Press))
            scene.sendIndirectPointerEvent(
                indirectEvent(
                    type = IndirectPointerEventType.Release,
                    pressed = false,
                    previousPressed = true,
                )
            )
            scene.cancelIndirectPointerInput()

            assertEquals(0, recorder.cancellations)
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

private fun createSceneFixtures(coroutineContext: kotlin.coroutines.CoroutineContext) =
    listOf(
        createPlatformSceneFixture(coroutineContext),
        createCanvasSceneFixture(coroutineContext),
    )

private fun createPlatformSceneFixture(
    coroutineContext: kotlin.coroutines.CoroutineContext
): SceneFixture {
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

private fun createCanvasSceneFixture(
    coroutineContext: kotlin.coroutines.CoroutineContext
): SceneFixture {
    val frameRecomposer = FrameRecomposer(coroutineContext)
    return SceneFixture(
        scene =
            CanvasLayersComposeScene(
                frameRecomposer = frameRecomposer,
                size = IntSize(100, 100),
            ),
        frameRecomposer = frameRecomposer,
    )
}

private fun indirectEvent(
    type: IndirectPointerEventType = IndirectPointerEventType.Move,
    pressed: Boolean = true,
    previousPressed: Boolean = true,
) =
    SkikoIndirectPointerEvent(
        changes =
            listOf(
                IndirectPointerInputChange(
                    id = PointerId(1),
                    uptimeMillis = 20,
                    position = Offset(20f, 30f),
                    pressed = pressed,
                    pressure = if (pressed) 0.5f else 0f,
                    previousUptimeMillis = 10,
                    previousPosition = Offset(10f, 20f),
                    previousPressed = previousPressed,
                )
            ),
        type = type,
        primaryDirectionalMotionAxis =
            IndirectPointerEventPrimaryDirectionalMotionAxis.None,
        nativeEvent = null,
    )

private fun Modifier.recordIndirectInput(recorder: Recorder): Modifier =
    this then IndirectRecorderElement(recorder)

private data class IndirectRecorderElement(
    val recorder: Recorder,
) : ModifierNodeElement<IndirectRecorderNode>() {
    override fun create(): IndirectRecorderNode = IndirectRecorderNode(recorder)

    override fun update(node: IndirectRecorderNode) {
        node.recorder = recorder
    }
}

private class IndirectRecorderNode(
    var recorder: Recorder,
) : Modifier.Node(), IndirectPointerInputModifierNode {
    override fun onIndirectPointerEvent(
        event: androidx.compose.ui.input.indirect.IndirectPointerEvent,
        pass: PointerEventPass,
    ) {
        recorder.passes += pass
        recorder.timeline += "${recorder.name}:$pass"
        if (recorder.consume && pass == PointerEventPass.Main) {
            event.changes.forEach { it.consume() }
        }
    }

    override fun onCancelIndirectPointerInput() {
        recorder.cancellations += 1
    }
}

private class Recorder(
    val name: String,
    val consume: Boolean = false,
    val timeline: MutableList<String> = mutableListOf(),
) {
    val passes = mutableListOf<PointerEventPass>()
    var cancellations = 0
}
