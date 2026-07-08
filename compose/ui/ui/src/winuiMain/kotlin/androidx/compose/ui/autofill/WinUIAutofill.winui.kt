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

import androidx.collection.MutableIntSet
import androidx.compose.ui.focus.FocusListener
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.requireSemanticsInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsInfo
import androidx.compose.ui.semantics.SemanticsListener
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString

internal class WinUIAutofill(
    private val autofillTree: @Suppress("DEPRECATION") AutofillTree,
    private val semanticsOwner: SemanticsOwner,
) : @Suppress("DEPRECATION") Autofill, SemanticsListener, FocusListener {
    private var activeLegacyAutofillNodeId: Int? = null
    private var activeSemanticsAutofillNodeId: Int? = null
    private val visibleSemanticsIds = MutableIntSet()
    private var requestCount = 0
    private var cancelCount = 0
    private var commitCount = 0
    private var cancelSessionCount = 0
    private var pendingAutofillCommit = false

    val manager: AutofillManager = WinUIAutofillManager(this)

    override fun requestAutofillForNode(autofillNode: @Suppress("DEPRECATION") AutofillNode) {
        activeLegacyAutofillNodeId = autofillNode.id
        requestCount += 1
    }

    override fun cancelAutofillForNode(autofillNode: @Suppress("DEPRECATION") AutofillNode) {
        if (activeLegacyAutofillNodeId == autofillNode.id) {
            activeLegacyAutofillNodeId = null
        }
        cancelCount += 1
    }

    override fun onFocusChanged(
        previous: FocusTargetModifierNode?,
        current: FocusTargetModifierNode?,
    ) {
        previous?.requireSemanticsInfo()?.let(::clearActiveSemanticsAutofillIfMatches)
        current?.requireSemanticsInfo()?.let { semanticsInfo ->
            if (semanticsInfo.semanticsConfiguration?.isAutofillable() == true) {
                requestAutofill(semanticsInfo)
            }
        }
    }

    override fun onSemanticsChanged(
        semanticsInfo: SemanticsInfo,
        previousSemanticsConfiguration: SemanticsConfiguration?,
    ) {
        val semanticsId = semanticsInfo.semanticsId
        val configuration = semanticsInfo.semanticsConfiguration
        val wasRelatedToAutofill = previousSemanticsConfiguration?.isRelatedToAutofill() == true
        val isRelatedToAutofill = configuration?.isRelatedToAutofill() == true

        if (isRelatedToAutofill) {
            visibleSemanticsIds.add(semanticsId)
        } else if (wasRelatedToAutofill || visibleSemanticsIds.contains(semanticsId)) {
            visibleSemanticsIds.remove(semanticsId)
            clearActiveSemanticsAutofillIfMatches(semanticsInfo)
        }

        if (configuration?.getOrNull(SemanticsProperties.ContentDataType) == ContentDataType.None) {
            clearActiveSemanticsAutofillIfMatches(semanticsInfo)
        }
    }

    fun requestAutofill(layoutNode: LayoutNode) {
        requestAutofill(layoutNode as SemanticsInfo)
    }

    private fun requestAutofill(semanticsInfo: SemanticsInfo) {
        if (semanticsInfo.semanticsConfiguration?.isAutofillable() != true) return
        activeSemanticsAutofillNodeId = semanticsInfo.semanticsId
        visibleSemanticsIds.add(semanticsInfo.semanticsId)
        requestCount += 1
    }

    fun onPostAttach(layoutNode: LayoutNode) {
        val semanticsInfo = layoutNode
        if (semanticsInfo.semanticsConfiguration?.isRelatedToAutofill() == true) {
            visibleSemanticsIds.add(semanticsInfo.semanticsId)
        }
    }

    fun onPostLayoutNodeReused(layoutNode: LayoutNode, previousSemanticsId: Int) {
        if (visibleSemanticsIds.remove(previousSemanticsId)) {
            clearActiveSemanticsAutofillIfMatches(previousSemanticsId)
        }
        onPostAttach(layoutNode)
    }

    fun onLayoutNodeDeactivated(layoutNode: LayoutNode) {
        val semanticsId = layoutNode.semanticsId
        if (visibleSemanticsIds.remove(semanticsId)) {
            clearActiveSemanticsAutofillIfMatches(semanticsId)
        }
    }

    fun onDetach(layoutNode: LayoutNode) {
        val semanticsId = layoutNode.semanticsId
        if (visibleSemanticsIds.remove(semanticsId)) {
            clearActiveSemanticsAutofillIfMatches(semanticsId)
        }
    }

    fun onEndApplyChanges() {
        if (visibleSemanticsIds.isEmpty() && pendingAutofillCommit) {
            commit()
            pendingAutofillCommit = false
        } else if (visibleSemanticsIds.isNotEmpty()) {
            pendingAutofillCommit = true
        }
    }

    fun commit() {
        activeLegacyAutofillNodeId = null
        activeSemanticsAutofillNodeId = null
        pendingAutofillCommit = false
        commitCount += 1
    }

    fun cancelSession() {
        activeLegacyAutofillNodeId = null
        activeSemanticsAutofillNodeId = null
        pendingAutofillCommit = false
        cancelSessionCount += 1
    }

    fun dispose() {
        activeLegacyAutofillNodeId = null
        activeSemanticsAutofillNodeId = null
        visibleSemanticsIds.clear()
        pendingAutofillCommit = false
    }

    fun performLegacyAutofill(id: Int, value: String) {
        autofillTree.performAutofill(id, value)
    }

    fun performSemanticsAutofill(id: Int, fillableData: FillableData): Boolean {
        val semanticsConfig = semanticsOwner[id]?.semanticsConfiguration ?: return false
        val fillDataAction = semanticsConfig.getOrNull(SemanticsActions.OnFillData)?.action
        if (fillDataAction != null) {
            return fillDataAction(fillableData)
        }
        @Suppress("DEPRECATION")
        val textAction = semanticsConfig.getOrNull(SemanticsActions.OnAutofillText)?.action
        val textValue = fillableData.textValue ?: return false
        return textAction?.invoke(AnnotatedString(textValue.toString())) ?: false
    }

    fun stateForTest(): WinUIAutofillState = WinUIAutofillState(
        autofillTree = autofillTree,
        activeLegacyAutofillNodeId = activeLegacyAutofillNodeId,
        activeSemanticsAutofillNodeId = activeSemanticsAutofillNodeId,
        visibleSemanticsIds = buildSet {
            visibleSemanticsIds.forEach { add(it) }
        },
        requestCount = requestCount,
        cancelCount = cancelCount,
        commitCount = commitCount,
        cancelSessionCount = cancelSessionCount,
    )

    private fun clearActiveSemanticsAutofillIfMatches(semanticsInfo: SemanticsInfo) {
        clearActiveSemanticsAutofillIfMatches(semanticsInfo.semanticsId)
    }

    private fun clearActiveSemanticsAutofillIfMatches(semanticsId: Int) {
        if (activeSemanticsAutofillNodeId == semanticsId) {
            activeSemanticsAutofillNodeId = null
        }
    }
}

internal data class WinUIAutofillState(
    val autofillTree: @Suppress("DEPRECATION") AutofillTree,
    val activeLegacyAutofillNodeId: Int?,
    val activeSemanticsAutofillNodeId: Int?,
    val visibleSemanticsIds: Set<Int>,
    val requestCount: Int,
    val cancelCount: Int,
    val commitCount: Int,
    val cancelSessionCount: Int,
)

private class WinUIAutofillManager(
    private val autofill: WinUIAutofill,
) : AutofillManager() {
    override fun commit() {
        autofill.commit()
    }

    override fun cancel() {
        autofill.cancelSession()
    }
}

private fun SemanticsConfiguration.isAutofillable(): Boolean {
    if (getOrNull(SemanticsProperties.ContentDataType) == ContentDataType.None) {
        return false
    }
    @Suppress("DEPRECATION")
    return props.contains(SemanticsActions.OnAutofillText) ||
        props.contains(SemanticsActions.OnFillData)
}

private fun SemanticsConfiguration.isRelatedToAutofill(): Boolean {
    @Suppress("DEPRECATION")
    return props.contains(SemanticsActions.OnAutofillText) ||
        props.contains(SemanticsActions.OnFillData) ||
        props.contains(SemanticsProperties.ContentType) ||
        props.contains(SemanticsProperties.ContentDataType) ||
        props.contains(SemanticsProperties.FillableData)
}
