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

package androidx.compose.ui.node

import androidx.compose.runtime.retain.ForgetfulRetainedValuesStore
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.PlatformFocusOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.hapticfeedback.WinUIHapticFeedback
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.WinUITestRuntime
import androidx.compose.ui.platform.WinUITextToolbar
import androidx.compose.ui.platform.WinUIPointerSample
import androidx.compose.ui.platform.toXamlPoint
import androidx.compose.ui.sensitiveContent
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.requestFocus
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.skiko.winui.WinUIAccessibilityAction
import org.jetbrains.skiko.winui.WinUIAccessibilityActionRequest
import org.jetbrains.skiko.winui.WinUIAccessibilityLiveSetting
import org.jetbrains.skiko.winui.WinUIAccessibilityRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinUIOwnerTest {
    @Test
    fun indirectPointerFocusListenerIsRegisteredOnlyDuringOwnerLifetime() {
        val source = winUIOwnerSource()
        val registration = "focusOwner.listeners += IndirectPointerInputFocusListener"
        val removal = "focusOwner.listeners -= IndirectPointerInputFocusListener"

        assertTrue(source.contains(registration))
        assertTrue(source.contains(removal))
        assertTrue(source.indexOf(registration) < source.indexOf(removal))
    }

    @Test
    fun indirectPointerDispatchDelegatesToFocusOwner() {
        val source = winUIOwnerSource()

        assertTrue(
            source.contains(
                "internal fun sendIndirectPointerEvent(event: IndirectPointerEvent): Boolean"
            )
        )
        assertTrue(source.contains("return focusOwner.dispatchIndirectPointerEvent(event)"))
        assertTrue(source.contains("internal fun cancelIndirectPointerInput()"))
        assertTrue(source.contains("focusOwner.dispatchIndirectPointerCancel()"))
    }

    @Test
    fun composeViewBindsOnlyOwningWindowAndClosesInputBeforeOwner() {
        val source = findUiModuleRoot()
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIComposeView.winui.kt")
            .readText()
        val ownerIndex = source.indexOf("internal val owner = WinUIOwner(")
        val conditionalBindingIndex = source.indexOf("window?.let { owningWindow ->")
        val closeBindingIndex = source.indexOf("{ renderHost.closeIndirectPointerInput() }")
        val ownerDisposeIndex = source.indexOf("{ owner.dispose() }")

        assertTrue(conditionalBindingIndex > ownerIndex)
        assertTrue(source.contains("renderHost.bindIndirectPointerInput("))
        assertTrue(
            source.contains(
                "owner.sendIndirectPointerEvent(native.toComposeIndirectPointerEvent())"
            )
        )
        assertTrue(source.contains("onCancel = owner::cancelIndirectPointerInput"))
        assertTrue(closeBindingIndex >= 0)
        assertTrue(closeBindingIndex < ownerDisposeIndex)
    }

    @Test
    fun ownerRecordsPlatformStateHooks() {
        val events = OwnerEvents()
        val owner = createOwner(events)
        try {
            assertFalse(owner.ownerStateForTest().accessibility.isAccessibilityForcedForTesting)

            owner.rootForTest.forceAccessibilityForTesting(true)
            owner.rootForTest.setAccessibilityEventBatchIntervalMillis(37L)
            owner.onSemanticsChange()
            owner.onLayoutChange(owner.root)
            owner.voteFrameRate(60f)
            owner.dispatchOnScrollChanged(Offset(3f, 4f))
            owner.invalidateRootLayer()

            val state = owner.ownerStateForTest()
            assertTrue(state.accessibility.isAccessibilityForcedForTesting)
            assertEquals(37L, state.accessibility.accessibilityEventBatchIntervalMillis)
            assertTrue(state.accessibility.currentSemanticsNodesInvalidated)
            assertTrue(state.accessibility.pendingSemanticsChange)
            assertEquals(listOf(owner.root.semanticsId), state.accessibility.pendingLayoutNodeIds)
            assertTrue(state.accessibility.hasPendingScrollChange)
            assertEquals(Offset(3f, 4f), state.accessibility.pendingScrollDelta)
            assertEquals(1, state.semanticsChangeCount)
            assertEquals(1, state.layoutChangeCount)
            assertEquals(owner.root.semanticsId, state.lastLayoutChangedSemanticsId)
            assertEquals(60f, state.lastFrameRateVote)
            assertEquals(1, state.scrollChangeCount)
            assertEquals(Offset(3f, 4f), state.lastScrollDelta)
            assertEquals(1, state.rootInvalidationCount)
            assertEquals(1, events.semanticsChanged)
            assertEquals(owner.root.semanticsId, events.lastLayoutChangedSemanticsId)
            assertEquals(Offset(3f, 4f), events.lastScrollDelta)
            assertEquals(1, events.rootInvalidated)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun rootCoordinatorLayerInvalidationInvalidatesRootLayer() {
        val owner = createOwner()
        try {
            val initialInvalidations = owner.ownerStateForTest().rootInvalidationCount

            owner.root.innerCoordinator.invalidateLayer()

            assertEquals(
                initialInvalidations + 1,
                owner.ownerStateForTest().rootInvalidationCount,
            )
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun childCoordinatorLayerInvalidationInvalidatesRootLayer() {
        val owner = createOwner()
        try {
            val child = LayoutNode().also {
                it.measurePolicy = fixedMeasurePolicy(20, 20)
            }
            owner.root.insertAt(0, child)
            owner.setWindowContainerSize(IntSize(100, 100))
            owner.measureAndLayout()
            val initialInvalidations = owner.ownerStateForTest().rootInvalidationCount

            child.innerCoordinator.invalidateLayer()

            assertEquals(
                initialInvalidations + 1,
                owner.ownerStateForTest().rootInvalidationCount,
            )
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun ownerUsesWinUIHapticFeedback() {
        val owner = createOwner()
        try {
            WinUIHapticFeedback.resetForTest()

            owner.hapticFeedBack.performHapticFeedback(HapticFeedbackType.ContextClick)

            val state = WinUIHapticFeedback.stateForTest()
            assertEquals(HapticFeedbackType.ContextClick, state.lastFeedbackType)
            assertEquals(1, state.feedbackCount)
        } finally {
            WinUIHapticFeedback.resetForTest()
            owner.dispose()
        }
    }

    @Test
    fun ownerLayoutDirectionIsMutableAndSchedulesLayout() {
        val source = winUIOwnerSource()
        assertTrue(
            source.contains(
                "override var layoutDirection: LayoutDirection by mutableStateOf(LayoutDirection.Ltr)"
            )
        )
        assertTrue(source.contains("fun updateLayoutDirection(layoutDirection: LayoutDirection)"))
        assertTrue(source.contains("root.layoutDirection = layoutDirection"))
        assertTrue(source.contains("onMeasureAndLayoutRequested()"))
    }

    @Test
    fun accessibilityBridgeDefersEventsUntilAccessibilityIsForcedForTesting() {
        val owner = createOwner()
        try {
            owner.rootForTest.setAccessibilityEventBatchIntervalMillis(10_000L)
            owner.onSemanticsChange()

            var state = owner.ownerStateForTest().accessibility
            assertFalse(state.isAccessibilityForcedForTesting)
            assertTrue(state.currentSemanticsNodesInvalidated)
            assertTrue(state.pendingSemanticsChange)
            assertFalse(state.hasPendingFlush)

            owner.rootForTest.forceAccessibilityForTesting(true)

            state = owner.ownerStateForTest().accessibility
            assertTrue(state.isAccessibilityForcedForTesting)
            assertTrue(state.hasPendingFlush)

            owner.rootForTest.forceAccessibilityForTesting(false)

            state = owner.ownerStateForTest().accessibility
            assertFalse(state.isAccessibilityForcedForTesting)
            assertFalse(state.hasPendingFlush)
            assertTrue(state.currentSemanticsNodesInvalidated)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun accessibilityProviderExposesComposeSemanticsSnapshot() {
        val owner = createOwner()
        try {
            val node = LayoutNode().also {
                it.modifier = Modifier.semantics {
                    contentDescription = "WinUI action"
                    testTag = "winui-action"
                    role = Role.Button
                    focused = true
                    onClick { true }
                }
                it.measurePolicy = fixedMeasurePolicy(40, 20)
            }
            owner.root.insertAt(0, node)
            owner.setWindowContainerSize(IntSize(100, 80))
            owner.measureAndLayout()
            owner.onSemanticsChange()

            val snapshot = assertNotNull(owner.accessibilityProvider.snapshot())
            val semanticsNode = snapshot.root.children.single()

            assertEquals(node.semanticsId.toLong(), semanticsNode.id)
            assertEquals("WinUI action", semanticsNode.info.name)
            assertEquals("winui-action", semanticsNode.info.automationId)
            assertEquals(WinUIAccessibilityRole.BUTTON, semanticsNode.info.role)
            assertTrue(semanticsNode.state.enabled)
            assertTrue(semanticsNode.state.focused)
            assertTrue(WinUIAccessibilityAction.CLICK in semanticsNode.actions)
            assertEquals(semanticsNode.id, snapshot.focusedNodeId)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun accessibilityProviderMapsComposeSemanticsMetadata() {
        val owner = createOwner()
        try {
            val visibleNode = LayoutNode().also {
                it.modifier = Modifier.semantics {
                    contentDescription = "WinUI metadata"
                    testTag = "winui-metadata"
                    role = Role.Checkbox
                    stateDescription = "Checked"
                    liveRegion = LiveRegionMode.Assertive
                    selected = true
                    toggleableState = ToggleableState.On
                    disabled()
                    requestFocus { true }
                    password()
                    setText { true }
                }
                it.measurePolicy = fixedMeasurePolicy(40, 20)
            }
            val hiddenNode = LayoutNode().also {
                it.modifier = Modifier.semantics {
                    contentDescription = "Hidden from WinUI"
                    testTag = "winui-hidden"
                    hideFromAccessibility()
                }
                it.measurePolicy = fixedMeasurePolicy(10, 10)
            }
            owner.root.insertAt(0, visibleNode)
            owner.root.insertAt(1, hiddenNode)
            owner.setWindowContainerSize(IntSize(100, 80))
            owner.measureAndLayout()
            owner.onSemanticsChange()

            val snapshot = assertNotNull(owner.accessibilityProvider.snapshot())
            val semanticsNode = snapshot.root.children.single()

            assertEquals(visibleNode.semanticsId.toLong(), semanticsNode.id)
            assertEquals("WinUI metadata", semanticsNode.info.name)
            assertEquals("winui-metadata", semanticsNode.info.automationId)
            assertEquals("Checked", semanticsNode.info.helpText)
            assertEquals(WinUIAccessibilityLiveSetting.ASSERTIVE, semanticsNode.info.liveSetting)
            assertEquals(WinUIAccessibilityRole.CHECK_BOX, semanticsNode.info.role)
            assertEquals(0f, semanticsNode.bounds.x)
            assertEquals(0f, semanticsNode.bounds.y)
            assertEquals(40f, semanticsNode.bounds.width)
            assertEquals(20f, semanticsNode.bounds.height)
            assertFalse(semanticsNode.state.enabled)
            assertTrue(semanticsNode.state.focusable)
            assertTrue(semanticsNode.state.selected)
            assertEquals(true, semanticsNode.state.checked)
            assertTrue(semanticsNode.state.editable)
            assertTrue(semanticsNode.state.password)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun accessibilityProviderInvokesComposeClickAction() {
        val owner = createOwner()
        try {
            var clicked = false
            val node = LayoutNode().also {
                it.modifier = Modifier.semantics {
                    testTag = "winui-click"
                    onClick {
                        clicked = true
                        true
                    }
                }
                it.measurePolicy = fixedMeasurePolicy(24, 24)
            }
            owner.root.insertAt(0, node)
            owner.setWindowContainerSize(IntSize(64, 64))
            owner.measureAndLayout()
            owner.onSemanticsChange()

            val actionInvoked = owner.accessibilityProvider.performAction(
                WinUIAccessibilityActionRequest(
                    nodeId = node.semanticsId.toLong(),
                    action = WinUIAccessibilityAction.CLICK,
                    text = "",
                ),
            )

            assertTrue(actionInvoked)
            assertTrue(clicked)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun accessibilityProviderInvokesComposeFocusAction() {
        val owner = createOwner()
        try {
            var focused = false
            val node = LayoutNode().also {
                it.modifier = Modifier.semantics {
                    testTag = "winui-focus"
                    requestFocus {
                        focused = true
                        true
                    }
                }
                it.measurePolicy = fixedMeasurePolicy(24, 24)
            }
            owner.root.insertAt(0, node)
            owner.setWindowContainerSize(IntSize(64, 64))
            owner.measureAndLayout()
            owner.onSemanticsChange()

            val actionInvoked = owner.accessibilityProvider.performAction(
                WinUIAccessibilityActionRequest(
                    nodeId = node.semanticsId.toLong(),
                    action = WinUIAccessibilityAction.FOCUS,
                    text = "",
                ),
            )

            assertTrue(actionInvoked)
            assertTrue(focused)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun accessibilityProviderInvokesComposeExpandAndCollapseActions() {
        val owner = createOwner()
        try {
            var expanded = false
            var collapsed = false
            val node = LayoutNode().also {
                it.modifier = Modifier.semantics {
                    testTag = "winui-expand-collapse"
                    expand {
                        expanded = true
                        true
                    }
                    collapse {
                        collapsed = true
                        true
                    }
                }
                it.measurePolicy = fixedMeasurePolicy(24, 24)
            }
            owner.root.insertAt(0, node)
            owner.setWindowContainerSize(IntSize(64, 64))
            owner.measureAndLayout()
            owner.onSemanticsChange()

            val expandInvoked = owner.accessibilityProvider.performAction(
                WinUIAccessibilityActionRequest(
                    nodeId = node.semanticsId.toLong(),
                    action = WinUIAccessibilityAction.EXPAND,
                    text = "",
                ),
            )
            val collapseInvoked = owner.accessibilityProvider.performAction(
                WinUIAccessibilityActionRequest(
                    nodeId = node.semanticsId.toLong(),
                    action = WinUIAccessibilityAction.COLLAPSE,
                    text = "",
                ),
            )

            assertTrue(expandInvoked)
            assertTrue(collapseInvoked)
            assertTrue(expanded)
            assertTrue(collapsed)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun accessibilityProviderInvokesComposeSetTextAction() {
        val owner = createOwner()
        try {
            var textSet: AnnotatedString? = null
            val node = LayoutNode().also {
                it.modifier = Modifier.semantics {
                    testTag = "winui-set-text"
                    setText {
                        textSet = it
                        true
                    }
                }
                it.measurePolicy = fixedMeasurePolicy(24, 24)
            }
            owner.root.insertAt(0, node)
            owner.setWindowContainerSize(IntSize(64, 64))
            owner.measureAndLayout()
            owner.onSemanticsChange()

            val actionInvoked = owner.accessibilityProvider.performAction(
                WinUIAccessibilityActionRequest(
                    nodeId = node.semanticsId.toLong(),
                    action = WinUIAccessibilityAction.SET_TEXT,
                    text = "WinUI text",
                ),
            )

            assertTrue(actionInvoked)
            assertEquals(AnnotatedString("WinUI text"), textSet)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun accessibilityProviderInvokesComposeProgressActions() {
        val owner = createOwner()
        try {
            val progressValues = mutableListOf<Float>()
            val node = LayoutNode().also {
                it.modifier = Modifier.semantics {
                    testTag = "winui-progress"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = 0.5f,
                        range = 0f..1f,
                        steps = 4,
                    )
                    setProgress {
                        progressValues += it
                        true
                    }
                }
                it.measurePolicy = fixedMeasurePolicy(24, 24)
            }
            owner.root.insertAt(0, node)
            owner.setWindowContainerSize(IntSize(64, 64))
            owner.measureAndLayout()
            owner.onSemanticsChange()

            val semanticsNode = assertNotNull(owner.accessibilityProvider.snapshot())
                .root.children.single()
            assertTrue(WinUIAccessibilityAction.INCREMENT in semanticsNode.actions)
            assertTrue(WinUIAccessibilityAction.DECREMENT in semanticsNode.actions)

            val incrementInvoked = owner.accessibilityProvider.performAction(
                WinUIAccessibilityActionRequest(
                    nodeId = node.semanticsId.toLong(),
                    action = WinUIAccessibilityAction.INCREMENT,
                    text = "",
                ),
            )
            val decrementInvoked = owner.accessibilityProvider.performAction(
                WinUIAccessibilityActionRequest(
                    nodeId = node.semanticsId.toLong(),
                    action = WinUIAccessibilityAction.DECREMENT,
                    text = "",
                ),
            )

            assertTrue(incrementInvoked)
            assertTrue(decrementInvoked)
            assertEquals(listOf(0.7f, 0.3f), progressValues)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun keepScreenOnModifierUpdatesOwnerCount() {
        val events = OwnerEvents()
        val owner = createOwner(events)
        try {
            val node = LayoutNode().also {
                it.modifier = Modifier.keepScreenOn()
                it.measurePolicy = RootMeasurePolicy
            }

            owner.root.insertAt(0, node)

            assertEquals(1, owner.ownerStateForTest().keepScreenOnCount)
            assertEquals(listOf(true), events.keepScreenOnValues)

            owner.root.removeAt(0, 1)

            assertEquals(0, owner.ownerStateForTest().keepScreenOnCount)
            assertEquals(listOf(true, false), events.keepScreenOnValues)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun sensitiveContentModifierUpdatesOwnerCount() {
        val events = OwnerEvents()
        val owner = createOwner(events)
        try {
            val node = LayoutNode().also {
                it.modifier = Modifier.sensitiveContent()
                it.measurePolicy = RootMeasurePolicy
            }

            owner.root.insertAt(0, node)

            assertEquals(1, owner.ownerStateForTest().sensitiveContentCount)
            assertEquals(listOf(true), events.sensitiveContentValues)

            owner.root.removeAt(0, 1)

            assertEquals(0, owner.ownerStateForTest().sensitiveContentCount)
            assertEquals(listOf(true, false), events.sensitiveContentValues)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun detachRequiresPreviouslyAttachedNode() {
        val owner = createOwner()
        try {
            val node = LayoutNode().also {
                it.measurePolicy = RootMeasurePolicy
            }

            owner.root.insertAt(0, node)

            assertEquals(node, owner.layoutNodes[node.semanticsId])

            owner.root.removeAt(0, 1)

            assertNull(owner.layoutNodes[node.semanticsId])

            val unattachedNode = LayoutNode().also {
                it.measurePolicy = RootMeasurePolicy
            }

            assertFailsWith<IllegalStateException> {
                owner.onDetach(unattachedNode)
            }
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun rootResizeInvalidatesRootLayerAfterMeasureAndLayout() {
        val events = OwnerEvents()
        val owner = createOwner(events)
        try {
            owner.root.insertAt(
                0,
                LayoutNode().also {
                    it.measurePolicy = fillMaxConstraintsMeasurePolicy()
                }
            )

            val initialInvalidations = owner.ownerStateForTest().rootInvalidationCount
            val initialPlatformInvalidations = events.rootInvalidated

            owner.setWindowContainerSize(IntSize(10, 20))
            owner.measureAndLayout()

            val firstResizeInvalidations = owner.ownerStateForTest().rootInvalidationCount
            val firstResizePlatformInvalidations = events.rootInvalidated
            assertTrue(firstResizeInvalidations > initialInvalidations)
            assertTrue(firstResizePlatformInvalidations > initialPlatformInvalidations)

            owner.setWindowContainerSize(IntSize(10, 20))
            owner.measureAndLayout()

            assertEquals(firstResizeInvalidations, owner.ownerStateForTest().rootInvalidationCount)
            assertEquals(firstResizePlatformInvalidations, events.rootInvalidated)

            owner.setWindowContainerSize(IntSize(30, 40))
            owner.measureAndLayout()

            assertTrue(owner.ownerStateForTest().rootInvalidationCount > firstResizeInvalidations)
            assertTrue(events.rootInvalidated > firstResizePlatformInvalidations)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun densityUpdatesWindowDpSizeAndRequestsLayout() {
        var measureRequests = 0
        val owner = createOwner(
            onMeasureAndLayoutRequested = { measureRequests += 1 },
        )
        try {
            owner.setWindowContainerSize(IntSize(200, 100))

            assertEquals(Density(1f), owner.density)
            assertEquals(200.dp, owner.windowInfo.containerDpSize.width)
            assertEquals(100.dp, owner.windowInfo.containerDpSize.height)
            assertTrue(measureRequests > 0)
            val requestsAfterResize = measureRequests

            owner.updateDensity(Density(2f))

            assertEquals(Density(2f), owner.density)
            assertEquals(Density(2f), owner.root.density)
            assertEquals(100.dp, owner.windowInfo.containerDpSize.width)
            assertEquals(50.dp, owner.windowInfo.containerDpSize.height)
            assertTrue(measureRequests > requestsAfterResize)
            val requestsAfterDensity = measureRequests

            owner.updateDensity(Density(2f))

            assertEquals(requestsAfterDensity, measureRequests)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun createdLayerVotesFrameRateThroughOwner() {
        val owner = createOwner()
        try {
            val layer = owner.createLayer(
                drawBlock = { _, _ -> },
                invalidateParentLayer = {},
            )

            layer.resize(IntSize(10, 20))

            assertEquals(
                FrameRateCategory.High.value,
                owner.ownerStateForTest().lastFrameRateVote,
            )

            layer.frameRate = 30f
            layer.updateDisplayList()

            assertEquals(30f, owner.ownerStateForTest().lastFrameRateVote)

            layer.destroy()
            assertEquals(0f, layer.frameRate)
            assertFalse(layer.isFrameRateFromParent)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun disposeReleasesActivePlatformStateAndSuppressesFutureStateChanges() {
        val events = OwnerEvents()
        val owner = createOwner(events)

        owner.incrementKeepScreenOnCount()
        owner.incrementKeepScreenOnCount()
        owner.incrementSensitiveComponentCount()
        owner.incrementSensitiveComponentCount()
        owner.voteFrameRate(30f)

        assertEquals(2, owner.ownerStateForTest().keepScreenOnCount)
        assertEquals(2, owner.ownerStateForTest().sensitiveContentCount)
        assertEquals(listOf(true), events.keepScreenOnValues)
        assertEquals(listOf(true), events.sensitiveContentValues)

        owner.dispose()

        assertEquals(0, owner.ownerStateForTest().keepScreenOnCount)
        assertEquals(0, owner.ownerStateForTest().sensitiveContentCount)
        assertEquals(listOf(true, false), events.keepScreenOnValues)
        assertEquals(listOf(true, false), events.sensitiveContentValues)

        owner.incrementKeepScreenOnCount()
        owner.incrementSensitiveComponentCount()
        owner.decrementKeepScreenOnCount()
        owner.decrementSensitiveComponentCount()
        owner.voteFrameRate(120f)

        assertEquals(0, owner.ownerStateForTest().keepScreenOnCount)
        assertEquals(0, owner.ownerStateForTest().sensitiveContentCount)
        assertEquals(30f, owner.ownerStateForTest().lastFrameRateVote)
        assertEquals(listOf(true, false), events.keepScreenOnValues)
        assertEquals(listOf(true, false), events.sensitiveContentValues)
    }

    @Test
    fun outOfFrameExecutorSchedulesAndDrainsForTests() {
        val scheduled = mutableListOf<() -> Unit>()
        val owner = createOwner(
            scheduleOutOfFrame = { scheduled += it }
        )
        try {
            val executor = assertNotNull(owner.outOfFrameExecutor)
            val events = mutableListOf<String>()

            executor.schedule { events += "first" }
            executor.schedule { events += "second" }

            assertEquals(1, scheduled.size)
            assertEquals(emptyList(), events)

            owner.rootForTest.measureAndLayoutForTest()

            assertEquals(listOf("second", "first"), events)

            scheduled.single().invoke()
            assertEquals(listOf("second", "first"), events)
        } finally {
            owner.dispose()
        }

        assertNull(owner.outOfFrameExecutor)
    }

    @Test
    fun disposeDuringEndApplyChangesStopsRemainingListeners() {
        val owner = createOwner()
        var remainingListenerCalled = false

        owner.registerOnEndApplyChangesListener(owner::dispose)
        owner.registerOnEndApplyChangesListener {
            remainingListenerCalled = true
        }

        owner.onEndApplyChanges()

        assertFalse(remainingListenerCalled)
    }

    @Test
    fun disposeSuppressesPendingAndFutureOwnerCallbacks() {
        val events = OwnerEvents()
        var measureRequests = 0
        val scheduledOutOfFrame = mutableListOf<() -> Unit>()
        val owner = createOwner(
            events = events,
            onMeasureAndLayoutRequested = { measureRequests += 1 },
            scheduleOutOfFrame = { scheduledOutOfFrame += it },
        )
        val executor = assertNotNull(owner.outOfFrameExecutor)
        val outOfFrameEvents = mutableListOf<String>()

        executor.schedule { outOfFrameEvents += "late" }
        assertEquals(1, scheduledOutOfFrame.size)
        owner.setWindowFocused(true)
        owner.setWindowContainerSize(IntSize(10, 20))
        owner.setInteropViewFocusRect(Rect(1f, 2f, 3f, 4f))
        owner.setInteropViewBounds("interop", Rect(5f, 6f, 7f, 8f))

        owner.dispose()
        val measureRequestsAfterDispose = measureRequests
        val windowInfoAfterDispose = owner.windowInfo
        val ownerStateAfterDispose = owner.ownerStateForTest()

        scheduledOutOfFrame.single().invoke()
        owner.setWindowFocused(false)
        owner.setWindowContainerSize(IntSize(30, 40))
        owner.setInteropViewFocusRect(Rect.Zero)
        owner.setInteropViewBounds("interop", null)
        owner.registerOnEndApplyChangesListener {
            measureRequests += 100
        }
        owner.onEndApplyChanges()
        owner.onRequestMeasure(
            layoutNode = owner.root,
            affectsLookahead = false,
            forceRequest = true,
            scheduleMeasureAndLayout = true,
        )
        owner.onRequestRelayout(
            layoutNode = owner.root,
            affectsLookahead = false,
            forceRequest = true,
        )
        owner.requestOnPositionedCallback(owner.root)
        owner.registerOnLayoutCompletedListener(
            object : Owner.OnLayoutCompletedListener {
                override fun onLayoutComplete() {
                    measureRequests += 100
                }
            }
        )
        owner.onSemanticsChange()
        owner.onLayoutChange(owner.root)
        owner.dispatchOnScrollChanged(Offset(1f, 2f))
        owner.invalidateRootLayer()
        owner.voteFrameRate(120f)

        assertEquals(emptyList(), outOfFrameEvents)
        assertEquals(measureRequestsAfterDispose, measureRequests)
        assertTrue(windowInfoAfterDispose.isWindowFocused)
        assertEquals(IntSize(10, 20), windowInfoAfterDispose.containerSize)
        assertEquals(Rect(1f, 2f, 3f, 4f), ownerStateAfterDispose.interopViewFocusRect)
        assertEquals(listOf(Rect(5f, 6f, 7f, 8f)), ownerStateAfterDispose.interopViewBounds)
        assertEquals(ownerStateAfterDispose, owner.ownerStateForTest())
        assertEquals(0, events.semanticsChanged)
        assertEquals(0, events.rootInvalidated)
        assertEquals(0, events.interopTreeChanged)
        assertEquals(Offset.Unspecified, events.lastScrollDelta)
        assertNull(owner.outOfFrameExecutor)
    }

    @Test
    fun coordinateMappingDelegatesToMapper() {
        val owner = createOwner(
            coordinateMapper = WinUICoordinateMapper(
                calculatePositionInWindow = { it + Offset(10f, 20f) },
                calculateLocalPosition = { it - Offset(10f, 20f) },
                localToScreen = { it + Offset(30f, 40f) },
                screenToLocal = { it - Offset(30f, 40f) },
            )
        )
        try {
            assertEquals(
                Offset(11f, 22f),
                owner.calculatePositionInWindow(Offset(1f, 2f))
            )
            assertEquals(
                Offset(1f, 2f),
                owner.calculateLocalPosition(Offset(11f, 22f))
            )
            assertEquals(
                Offset(31f, 42f),
                owner.localToScreen(Offset(1f, 2f))
            )
            assertEquals(
                Offset(1f, 2f),
                owner.screenToLocal(Offset(31f, 42f))
            )

            val matrix = Matrix()
            owner.localToScreen(matrix)

            assertEquals(Offset(30f, 40f), matrix.map(Offset.Zero))
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun textToolbarTracksMenuState() {
        val owner = createOwner()
        try {
            val toolbar = owner.textToolbar as WinUITextToolbar
            var copyRequests = 0
            var pasteRequests = 0
            var cutRequests = 0
            var selectAllRequests = 0
            var autofillRequests = 0

            assertEquals(TextToolbarStatus.Hidden, toolbar.status)

            toolbar.showMenu(
                rect = Rect(1f, 2f, 3f, 4f),
                onCopyRequested = { copyRequests += 1 },
                onPasteRequested = { pasteRequests += 1 },
                onCutRequested = { cutRequests += 1 },
                onSelectAllRequested = { selectAllRequests += 1 },
                onAutofillRequested = { autofillRequests += 1 },
            )

            assertEquals(TextToolbarStatus.Shown, toolbar.status)
            assertEquals(Rect(1f, 2f, 3f, 4f), toolbar.menuForTest()?.rect)
            assertEquals(
                listOf("Copy", "Paste", "Cut", "Select all", "Autofill"),
                toolbar.menuForTest()?.itemLabels,
            )

            toolbar.menuForTest()?.onCopyRequested?.invoke()
            toolbar.menuForTest()?.onPasteRequested?.invoke()
            toolbar.menuForTest()?.onCutRequested?.invoke()
            toolbar.menuForTest()?.onSelectAllRequested?.invoke()
            toolbar.menuForTest()?.onAutofillRequested?.invoke()
            assertEquals(1, copyRequests)
            assertEquals(1, pasteRequests)
            assertEquals(1, cutRequests)
            assertEquals(1, selectAllRequests)
            assertEquals(1, autofillRequests)

            toolbar.hide()

            assertEquals(TextToolbarStatus.Hidden, toolbar.status)
            assertNull(toolbar.menuForTest())
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun textToolbarConvertsComposePixelsToXamlDips() {
        val unscaled = Rect(10f, 15f, 30f, 20f).toXamlPoint(Density(1f))
        assertEquals(10f, unscaled.x)
        assertEquals(20f, unscaled.y)

        val scaled = Rect(10f, 15f, 30f, 20f).toXamlPoint(Density(2f))
        assertEquals(5f, scaled.x)
        assertEquals(10f, scaled.y)
    }

    @Test
    fun ownerTracksInteropViewFocusRect() {
        val owner = createOwner()
        try {
            val rect = Rect(1f, 2f, 30f, 40f)

            owner.setInteropViewFocusRect(rect)

            assertEquals(rect, owner.ownerStateForTest().interopViewFocusRect)

            owner.setInteropViewFocusRect(null)

            assertNull(owner.ownerStateForTest().interopViewFocusRect)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun ownerTracksInteropViewBounds() {
        val owner = createOwner()
        try {
            val key = Any()
            val bounds = Rect(1f, 2f, 30f, 40f)

            owner.setInteropViewBounds(key, bounds)

            assertEquals(listOf(bounds), owner.ownerStateForTest().interopViewBounds)

            owner.setInteropViewBounds(key, null)

            assertEquals(emptyList(), owner.ownerStateForTest().interopViewBounds)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun pointerEventsInsideInteropViewBoundsAreNotDispatchedToCompose() {
        val owner = createOwner()
        val events = mutableListOf<PointerEventType>()
        try {
            val pointerNode = LayoutNode().also {
                it.modifier = PointerRecorderElement(events)
                it.measurePolicy = fixedMeasurePolicy(100, 100)
            }
            owner.root.insertAt(0, pointerNode)
            owner.setWindowContainerSize(IntSize(100, 100))
            owner.measureAndLayout()

            assertTrue(
                owner.sendPointerEventForTest(
                    eventType = PointerEventType.Press,
                    position = Offset(60f, 60f),
                    uptimeMillis = 1L,
                    pointerId = 1L,
                    down = true,
                    type = PointerType.Mouse,
                    buttons = PointerButtons(isPrimaryPressed = true),
                    keyboardModifiers = PointerKeyboardModifiers(),
                    button = PointerButton.Primary,
                )
            )
            assertEquals(listOf(PointerEventType.Press), events)

            owner.setInteropViewBounds(Any(), Rect(0f, 0f, 50f, 50f))

            assertFalse(
                owner.sendPointerEventForTest(
                    eventType = PointerEventType.Press,
                    position = Offset(10f, 10f),
                    uptimeMillis = 2L,
                    pointerId = 2L,
                    down = true,
                    type = PointerType.Mouse,
                    buttons = PointerButtons(isPrimaryPressed = true),
                    keyboardModifiers = PointerKeyboardModifiers(),
                    button = PointerButton.Primary,
                )
            )
            assertEquals(listOf(PointerEventType.Press), events)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun mousePressAndReleaseDoNotSynthesizeHoverExitOrEnter() {
        val owner = createOwner()
        val events = mutableListOf<PointerEventType>()
        try {
            val pointerNode = LayoutNode().also {
                it.modifier = PointerRecorderElement(events)
                it.measurePolicy = fixedMeasurePolicy(100, 100)
            }
            owner.root.insertAt(0, pointerNode)
            owner.setWindowContainerSize(IntSize(100, 100))
            owner.measureAndLayout()

            owner.sendPointerEventForTest(
                eventType = PointerEventType.Move,
                position = Offset(150f, 150f),
                uptimeMillis = 1L,
                pointerId = 1L,
                down = false,
                type = PointerType.Mouse,
                buttons = PointerButtons(),
                keyboardModifiers = PointerKeyboardModifiers(),
                button = null,
            )

            assertEquals(emptyList(), events)

            owner.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(10f, 10f),
                uptimeMillis = 2L,
                pointerId = 1L,
                down = true,
                type = PointerType.Mouse,
                buttons = PointerButtons(isPrimaryPressed = true),
                keyboardModifiers = PointerKeyboardModifiers(),
                button = PointerButton.Primary,
            )

            assertEquals(
                listOf(
                    PointerEventType.Press,
                ),
                events,
            )

            owner.sendPointerEventForTest(
                eventType = PointerEventType.Release,
                position = Offset(10f, 10f),
                uptimeMillis = 3L,
                pointerId = 1L,
                down = false,
                type = PointerType.Mouse,
                buttons = PointerButtons(),
                keyboardModifiers = PointerKeyboardModifiers(),
                button = PointerButton.Primary,
            )

            assertEquals(
                listOf(
                    PointerEventType.Press,
                    PointerEventType.Release,
                ),
                events,
            )

            owner.sendPointerEventForTest(
                eventType = PointerEventType.Exit,
                position = Offset(10f, 10f),
                uptimeMillis = 4L,
                pointerId = 1L,
                down = false,
                type = PointerType.Mouse,
                buttons = PointerButtons(),
                keyboardModifiers = PointerKeyboardModifiers(),
                button = null,
                isInBounds = false,
            )

            assertEquals(
                listOf(
                    PointerEventType.Press,
                    PointerEventType.Release,
                ),
                events,
            )
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun measureAndLayoutCanResendLastMousePointerPosition() {
        val owner = createOwner()
        val events = mutableListOf<PointerEventType>()
        try {
            var pointerNodeX = 50
            val pointerNode = LayoutNode().also {
                it.modifier = PointerRecorderElement(events)
                it.measurePolicy = fillMaxConstraintsMeasurePolicy()
            }
            val parentNode = LayoutNode().also {
                it.measurePolicy = singleChildOffsetMeasurePolicy { pointerNodeX }
                it.insertAt(0, pointerNode)
            }
            owner.root.insertAt(0, parentNode)
            owner.setWindowContainerSize(IntSize(100, 100))
            owner.measureAndLayout()

            owner.sendPointerEventForTest(
                eventType = PointerEventType.Move,
                position = Offset(10f, 10f),
                uptimeMillis = 1L,
                pointerId = 1L,
                down = false,
                type = PointerType.Mouse,
                buttons = PointerButtons(),
                keyboardModifiers = PointerKeyboardModifiers(),
                button = null,
            )

            assertEquals(emptyList(), events)
            assertEquals(PointerEventType.Move, owner.ownerStateForTest().lastMousePointerEvent?.eventType)

            pointerNodeX = 0
            owner.onRequestMeasure(
                layoutNode = parentNode,
                affectsLookahead = false,
                forceRequest = true,
                scheduleMeasureAndLayout = false,
            )
            owner.measureAndLayout(sendPointerUpdate = true)

            assertEquals(listOf(PointerEventType.Enter), events)

            pointerNodeX = 50
            owner.onRequestMeasure(
                layoutNode = parentNode,
                affectsLookahead = false,
                forceRequest = true,
                scheduleMeasureAndLayout = false,
            )
            owner.measureAndLayout(sendPointerUpdate = false)

            assertEquals(listOf(PointerEventType.Enter), events)

            owner.sendPointerEventForTest(
                eventType = PointerEventType.Exit,
                position = Offset(10f, 10f),
                uptimeMillis = 2L,
                pointerId = 1L,
                down = false,
                type = PointerType.Mouse,
                buttons = PointerButtons(),
                keyboardModifiers = PointerKeyboardModifiers(),
                button = null,
                isInBounds = false,
            )

            assertNull(owner.ownerStateForTest().lastMousePointerEvent)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun inputModeManagerUpdatesFromRequestsAndOwnerInputEvents() {
        val owner = createOwner()
        try {
            assertEquals(InputMode.Keyboard, owner.inputModeManager.inputMode)

            assertTrue(owner.inputModeManager.requestInputMode(InputMode.Touch))
            assertEquals(InputMode.Touch, owner.inputModeManager.inputMode)

            owner.sendKeyEvent(KeyEvent(key = Key.A, type = KeyEventType.KeyDown))
            assertEquals(InputMode.Keyboard, owner.inputModeManager.inputMode)

            owner.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(1f, 2f),
                uptimeMillis = 3L,
                pointerId = 4L,
                down = true,
                type = PointerType.Mouse,
                buttons = PointerButtons(isPrimaryPressed = true),
                keyboardModifiers = PointerKeyboardModifiers(),
                button = PointerButton.Primary,
            )
            assertEquals(InputMode.Touch, owner.inputModeManager.inputMode)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun mouseHoverDoesNotSwitchInputModeToTouch() {
        val owner = createOwner()
        try {
            assertEquals(InputMode.Keyboard, owner.inputModeManager.inputMode)

            owner.sendPointerEventForTest(
                eventType = PointerEventType.Move,
                position = Offset(1f, 2f),
                uptimeMillis = 3L,
                pointerId = 4L,
                down = false,
                type = PointerType.Mouse,
                buttons = PointerButtons(),
                keyboardModifiers = PointerKeyboardModifiers(),
                button = null,
            )

            assertEquals(InputMode.Keyboard, owner.inputModeManager.inputMode)
        } finally {
            owner.dispose()
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun textInputSessionCancelsPreviousSessionAndActiveInputMethod() = runTest {
        val owner = createOwner()
        val events = mutableListOf<String>()
        try {
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                owner.textInputSession {
                    events += "first-session"
                    try {
                        startInputMethod(WinUITestInputMethodRequest)
                    } finally {
                        events += "first-cancelled"
                    }
                }
            }

            assertEquals(listOf("first-session"), events)

            val second = launch(start = CoroutineStart.UNDISPATCHED) {
                owner.textInputSession {
                    events += "second-session"
                    try {
                        startInputMethod(WinUITestInputMethodRequest)
                    } finally {
                        events += "second-cancelled"
                    }
                }
            }
            runCurrent()

            assertEquals(
                listOf("first-session", "first-cancelled", "second-session"),
                events,
            )

            second.cancelAndJoin()

            assertEquals(
                listOf(
                    "first-session",
                    "first-cancelled",
                    "second-session",
                    "second-cancelled",
                ),
                events,
            )
            assertTrue(first.isCancelled)
        } finally {
            owner.dispose()
        }
    }

    private fun createOwner(
        events: OwnerEvents = OwnerEvents(),
        onMeasureAndLayoutRequested: () -> Unit = {},
        scheduleOutOfFrame: (() -> Unit) -> Unit = { it() },
        coordinateMapper: WinUICoordinateMapper = WinUICoordinateMapper(),
    ): WinUIOwner {
        WinUITestRuntime.ensureInitialized()
        val root = LayoutNode().also {
            it.measurePolicy = RootMeasurePolicy
        }
        return WinUIOwner(
            root = root,
            platformFocusOwner = TestPlatformFocusOwner,
            retainedValuesStore = ForgetfulRetainedValuesStore,
            onMeasureAndLayoutRequested = onMeasureAndLayoutRequested,
            onRootInvalidated = { events.rootInvalidated += 1 },
            onSemanticsChanged = { events.semanticsChanged += 1 },
            onInteropTreeChanged = { events.interopTreeChanged += 1 },
            onLayoutChanged = { _, semanticsId ->
                events.lastLayoutChangedSemanticsId = semanticsId
            },
            onScrollChanged = { events.lastScrollDelta = it },
            onKeepScreenOnChanged = { events.keepScreenOnValues += it },
            onSensitiveContentChanged = { events.sensitiveContentValues += it },
            scheduleOutOfFrame = scheduleOutOfFrame,
            coordinateMapper = coordinateMapper,
        )
    }

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

    @Test
    fun pointerEventDispatchesAllActiveContactsWithPressureHistoryAndHover() {
        val history = androidx.compose.ui.input.pointer.HistoricalChange(
            uptimeMillis = 8L,
            position = Offset(4f, 5f),
        )
        val pointers = listOf(
            WinUIPointerSample(
                id = 11L,
                uptimeMillis = 10L,
                position = Offset(1f, 2f),
                down = true,
                type = PointerType.Touch,
                pressure = 0.25f,
                activeHover = false,
                historical = emptyList(),
            ),
            WinUIPointerSample(
                id = 22L,
                uptimeMillis = 11L,
                position = Offset(3f, 4f),
                down = false,
                type = PointerType.Stylus,
                pressure = 0.75f,
                activeHover = true,
                historical = listOf(history),
            ),
        )

        val eventData = winUIPointerSamplesToEventData(
            samples = pointers,
            changedPointerId = 22L,
            scrollDelta = Offset.Zero,
        )

        assertEquals(
            listOf(
                PointerSnapshot(
                    id = 11L,
                    pressure = 0.25f,
                    activeHover = false,
                    historicalCount = 0,
                ),
                PointerSnapshot(
                    id = 22L,
                    pressure = 0.75f,
                    activeHover = true,
                    historicalCount = 1,
                ),
            ),
            eventData.map { data ->
                PointerSnapshot(
                    id = data.id.value,
                    pressure = data.pressure,
                    activeHover = data.activeHover,
                    historicalCount = data.historical.size,
                )
            },
        )
    }

    @Test
    fun pointerContactInputModeDecisionDistinguishesHoverFromContact() {
        listOf(PointerType.Touch, PointerType.Stylus, PointerType.Eraser).forEach { pointerType ->
            assertTrue(
                winUIShouldRequestTouchInputMode(
                    down = true,
                    type = pointerType,
                    button = null,
                ),
            )
        }
        assertFalse(
            winUIShouldRequestTouchInputMode(
                down = false,
                type = PointerType.Mouse,
                button = null,
            ),
        )
        assertFalse(
            winUIShouldRequestTouchInputMode(
                down = false,
                type = PointerType.Stylus,
                button = null,
            ),
        )
        assertTrue(
            winUIShouldRequestTouchInputMode(
                down = true,
                type = PointerType.Mouse,
                button = PointerButton.Primary,
            ),
        )
    }

    private fun winUIOwnerSource(): String =
        findUiModuleRoot()
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/node/WinUIOwner.winui.kt")
            .readText()
}

private class OwnerEvents {
    var rootInvalidated = 0
    var semanticsChanged = 0
    var interopTreeChanged = 0
    var lastLayoutChangedSemanticsId = -1
    var lastScrollDelta = Offset.Unspecified
    val keepScreenOnValues = mutableListOf<Boolean>()
    val sensitiveContentValues = mutableListOf<Boolean>()
}

private fun fixedMeasurePolicy(width: Int, height: Int) = MeasurePolicy { _, _ ->
    layout(width, height) {}
}

private fun fillMaxConstraintsMeasurePolicy() = MeasurePolicy { _, constraints ->
    layout(constraints.maxWidth, constraints.maxHeight) {}
}

private fun singleChildOffsetMeasurePolicy(offsetX: () -> Int) = MeasurePolicy { measurables, _ ->
    val placeable = measurables.single().measure(Constraints.fixed(20, 20))
    layout(100, 100) {
        placeable.placeRelative(offsetX(), 0)
    }
}

private data class PointerRecorderElement(
    val events: MutableList<PointerEventType>,
) : ModifierNodeElement<PointerRecorderNode>() {
    override fun create(): PointerRecorderNode = PointerRecorderNode(events)

    override fun update(node: PointerRecorderNode) {
        node.events = events
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pointerRecorder"
    }
}

private data class PointerSnapshot(
    val id: Long,
    val pressure: Float,
    val activeHover: Boolean,
    val historicalCount: Int,
)

private class PointerRecorderNode(
    var events: MutableList<PointerEventType>,
) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass == PointerEventPass.Main) {
            events += pointerEvent.type
        }
    }

    override fun onCancelPointerInput() = Unit
}

@OptIn(ExperimentalComposeUiApi::class)
private object WinUITestInputMethodRequest : PlatformTextInputMethodRequest {
    private val textValue = TextFieldValue("")

    override val value: () -> TextFieldValue = { textValue }
    override val state: TextEditorState = object : TextEditorState {
        override val text: String = ""
        override val selection: TextRange = TextRange.Zero
        override val composition: TextRange? = null
        override val length: Int = 0
        override fun get(index: Int): Char = throw IndexOutOfBoundsException(index)
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = ""
        override fun toString(): String = ""
    }
    override val imeOptions: ImeOptions = ImeOptions.Default
    override val onEditCommand: (List<EditCommand>) -> Unit = {}
    override val onImeAction: ((ImeAction) -> Unit)? = null
    override val textLayoutResult: () -> TextLayoutResult? = { null }
    override val focusedRectInRoot: () -> Rect? = { null }
    override val textFieldRectInRoot: () -> Rect? = { null }
    override val textClippingRectInRoot: () -> Rect? = { null }
    override val unclippedTextOffsetInRoot: () -> Offset? = { null }
    override val editText: (TextEditingScope.() -> Unit) -> Unit = {}
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
