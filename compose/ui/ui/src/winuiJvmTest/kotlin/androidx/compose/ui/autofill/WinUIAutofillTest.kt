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

package androidx.compose.ui.autofill

import androidx.compose.runtime.retain.ForgetfulRetainedValuesStore
import androidx.compose.ui.Modifier
import androidx.compose.ui.WinUISkikoTestBase
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.PlatformFocusOwner
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.WinUIOwner
import androidx.compose.ui.platform.WinUITestRuntime
import androidx.compose.ui.semantics.contentDataType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.onAutofillText
import androidx.compose.ui.semantics.onFillData
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WinUIAutofillTest : WinUISkikoTestBase() {
    @Test
    fun ownerProvidesAutofillLocals() {
        val owner = createOwner()
        try {
            assertNotNull(owner.autofill)
            assertNotNull(owner.autofillManager)
            assertSame(owner.autofillTree, owner.winUIAutofillStateForTest().autofillTree)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun legacyAutofillRequestAndCancelTrackActiveNode() {
        val owner = createOwner()
        try {
            val node = @Suppress("DEPRECATION") AutofillNode(
                boundingBox = Rect(1f, 2f, 3f, 4f),
                onFill = {},
            )

            owner.autofill.requestAutofillForNode(node)

            assertEquals(node.id, owner.winUIAutofillStateForTest().activeLegacyAutofillNodeId)
            assertEquals(1, owner.winUIAutofillStateForTest().requestCount)

            owner.autofill.cancelAutofillForNode(node)

            assertNull(owner.winUIAutofillStateForTest().activeLegacyAutofillNodeId)
            assertEquals(1, owner.winUIAutofillStateForTest().cancelCount)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun legacyAutofillCanFillRegisteredNode() {
        val owner = createOwner()
        try {
            var filled: String? = null
            val node = @Suppress("DEPRECATION") AutofillNode(
                boundingBox = Rect(1f, 2f, 3f, 4f),
                onFill = { filled = it },
            )
            owner.autofillTree += node

            owner.winUIAutofillForTest().performLegacyAutofill(node.id, "value")

            assertEquals("value", filled)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun managerCommitAndCancelTrackSessionState() {
        val owner = createOwner()
        try {
            owner.autofillManager.commit()
            owner.autofillManager.cancel()

            val state = owner.winUIAutofillStateForTest()
            assertEquals(1, state.commitCount)
            assertEquals(1, state.cancelSessionCount)
            assertNull(state.activeSemanticsAutofillNodeId)
            assertNull(state.activeLegacyAutofillNodeId)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun requestAutofillRecordsSemanticsNode() {
        val owner = createOwner()
        try {
            val child = LayoutNode().also {
                it.measurePolicy = fixedMeasurePolicy()
                it.modifier = Modifier.semantics {
                    contentType = ContentType.EmailAddress
                    contentDataType = ContentDataType.Text
                    onFillData { true }
                }
            }
            owner.root.insertAt(0, child)
            owner.setWindowContainerSize(IntSize(100, 100))
            owner.measureAndLayout()

            child.requestAutofill()

            val state = owner.winUIAutofillStateForTest()
            assertEquals(child.semanticsId, state.activeSemanticsAutofillNodeId)
            assertEquals(1, state.requestCount)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun performSemanticsAutofillPrefersFillDataAndFallsBackToText() {
        val owner = createOwner()
        try {
            var fillDataValue: FillableData? = null
            var textValue: AnnotatedString? = null
            val fillDataNode = LayoutNode().also {
                it.measurePolicy = fixedMeasurePolicy()
                it.modifier = Modifier.semantics {
                    contentType = ContentType.Username
                    contentDataType = ContentDataType.Text
                    onAutofillText { value ->
                        textValue = value
                        true
                    }
                    onFillData { value ->
                        fillDataValue = value
                        true
                    }
                }
            }
            val legacyTextNode = LayoutNode().also {
                it.measurePolicy = fixedMeasurePolicy()
                it.modifier = Modifier.semantics {
                    contentType = ContentType.EmailAddress
                    contentDataType = ContentDataType.Text
                    onAutofillText { value ->
                        textValue = value
                        true
                    }
                }
            }
            owner.root.insertAt(0, fillDataNode)
            owner.root.insertAt(1, legacyTextNode)

            assertTrue(
                owner.winUIAutofillForTest().performSemanticsAutofill(
                    fillDataNode.semanticsId,
                    FillableData.createFromText("fill-data")!!,
                )
            )
            assertEquals("fill-data", fillDataValue?.textValue.toString())
            assertNull(textValue)

            assertTrue(
                owner.winUIAutofillForTest().performSemanticsAutofill(
                    legacyTextNode.semanticsId,
                    FillableData.createFromText("legacy-text")!!,
                )
            )
            assertEquals(AnnotatedString("legacy-text"), textValue)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun performSemanticsAutofillDoesNotFallbackToTextForNonTextData() {
        val owner = createOwner()
        try {
            var textValue: AnnotatedString? = null
            val legacyTextNode = LayoutNode().also {
                it.measurePolicy = fixedMeasurePolicy()
                it.modifier = Modifier.semantics {
                    contentType = ContentType.Username
                    contentDataType = ContentDataType.Text
                    onAutofillText { value ->
                        textValue = value
                        true
                    }
                }
            }
            owner.root.insertAt(0, legacyTextNode)

            assertFalse(
                owner.winUIAutofillForTest().performSemanticsAutofill(
                    legacyTextNode.semanticsId,
                    FillableData.createFromBoolean(true)!!,
                )
            )
            assertNull(textValue)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun semanticsLifecycleClearsTrackedAutofillState() {
        val owner = createOwner()
        try {
            val child = LayoutNode().also {
                it.measurePolicy = fixedMeasurePolicy()
                it.modifier = Modifier.semantics {
                    contentType = ContentType.Username
                    contentDataType = ContentDataType.Text
                    onFillData { true }
                }
            }
            owner.root.insertAt(0, child)
            child.requestAutofill()

            child.onDeactivate()

            val state = owner.winUIAutofillStateForTest()
            assertNull(state.activeSemanticsAutofillNodeId)
            assertFalse(state.visibleSemanticsIds.contains(child.semanticsId))
        } finally {
            owner.dispose()
        }
    }
}

private fun createOwner(): WinUIOwner {
    WinUITestRuntime.ensureInitialized()
    val root = LayoutNode().also {
        it.measurePolicy = RootMeasurePolicy
    }
    return WinUIOwner(
        root = root,
        platformFocusOwner = TestPlatformFocusOwner,
        retainedValuesStore = ForgetfulRetainedValuesStore,
    )
}

private fun fixedMeasurePolicy(width: Int = 10, height: Int = 10) = MeasurePolicy { _, _ ->
    layout(width, height) {}
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
