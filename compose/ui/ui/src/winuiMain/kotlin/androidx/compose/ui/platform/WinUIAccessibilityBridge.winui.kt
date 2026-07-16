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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.postDelayed as composePostDelayed
import androidx.compose.ui.removePost as composeRemovePost
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import org.jetbrains.skiko.winui.WinUIAccessibilityAction
import org.jetbrains.skiko.winui.WinUIAccessibilityActionRequest
import org.jetbrains.skiko.winui.WinUIAccessibilityChange
import org.jetbrains.skiko.winui.WinUIAccessibilityChangeType
import org.jetbrains.skiko.winui.WinUIAccessibilityInfo
import org.jetbrains.skiko.winui.WinUIAccessibilityLiveSetting
import org.jetbrains.skiko.winui.WinUIAccessibilityNode
import org.jetbrains.skiko.winui.WinUIAccessibilityProvider
import org.jetbrains.skiko.winui.WinUIAccessibilityRole
import org.jetbrains.skiko.winui.WinUIAccessibilitySnapshot
import org.jetbrains.skiko.winui.WinUIAccessibilityState
import org.jetbrains.skiko.winui.WinUIAccessibilityView
import org.jetbrains.skiko.winui.WinUIRect

internal class WinUIAccessibilityBridge(
    private val postDelayed: (Long, () -> Unit) -> Any = { delayMillis, block ->
        composePostDelayed(delayMillis, block)
    },
    private val removePost: (Any?) -> Unit = { token ->
        composeRemovePost(token)
    },
    private val onUpdate: (WinUIAccessibilityUpdate) -> Unit = {},
) : WinUIAccessibilityProvider {
    private val changedLayoutNodeIds = linkedSetOf<Int>()
    private var pendingPost: Any? = null
    private var currentSemanticsOwner: SemanticsOwner? = null
    private var pendingSemanticsOwner: SemanticsOwner? = null
    private var hasPendingSemanticsChange = false
    private var hasPendingScrollChange = false
    private var pendingScrollDelta = Offset.Zero
    private var isAccessibilityProviderAttached = false
    private var isDisposed = false

    var isAccessibilityForcedForTesting: Boolean = false
        private set

    var accessibilityEventBatchIntervalMillis: Long = DefaultEventBatchIntervalMillis
        private set

    var currentSemanticsNodesInvalidated: Boolean = false
        private set

    fun forceAccessibilityForTesting(enable: Boolean) {
        if (isDisposed || isAccessibilityForcedForTesting == enable) return
        isAccessibilityForcedForTesting = enable
        if (enable) {
            scheduleFlushIfNeeded()
        } else if (!isAccessibilityProviderAttached) {
            cancelPendingFlush()
        }
    }

    fun setAccessibilityEventBatchIntervalMillis(intervalMillis: Long) {
        accessibilityEventBatchIntervalMillis = intervalMillis.coerceAtLeast(0L)
    }

    fun onAccessibilityProviderAttached() {
        if (isDisposed || isAccessibilityProviderAttached) return
        isAccessibilityProviderAttached = true
        scheduleFlushIfNeeded()
    }

    fun onAccessibilityProviderDetached() {
        if (isDisposed || !isAccessibilityProviderAttached) return
        isAccessibilityProviderAttached = false
        if (!isAccessibilityForcedForTesting) {
            cancelPendingFlush()
        }
    }

    fun onSemanticsChange(semanticsOwner: SemanticsOwner) {
        if (isDisposed) return
        currentSemanticsNodesInvalidated = true
        currentSemanticsOwner = semanticsOwner
        hasPendingSemanticsChange = true
        pendingSemanticsOwner = semanticsOwner
        scheduleFlushIfNeeded()
    }

    fun onLayoutChange(semanticsOwner: SemanticsOwner, semanticsId: Int) {
        if (isDisposed) return
        currentSemanticsNodesInvalidated = true
        currentSemanticsOwner = semanticsOwner
        changedLayoutNodeIds += semanticsId
        pendingSemanticsOwner = semanticsOwner
        scheduleFlushIfNeeded()
    }

    fun onScrollChanged(delta: Offset) {
        if (isDisposed) return
        hasPendingScrollChange = true
        pendingScrollDelta += delta
        scheduleFlushIfNeeded()
    }

    fun dispose() {
        isDisposed = true
        cancelPendingFlush()
        changedLayoutNodeIds.clear()
        currentSemanticsOwner = null
        pendingSemanticsOwner = null
        hasPendingSemanticsChange = false
        hasPendingScrollChange = false
        pendingScrollDelta = Offset.Zero
        isAccessibilityProviderAttached = false
    }

    override fun snapshot(): WinUIAccessibilitySnapshot =
        currentSemanticsOwner?.toWinUIAccessibilitySnapshot() ?: EmptySnapshot

    override fun performAction(request: WinUIAccessibilityActionRequest): Boolean {
        val targetNode = currentSemanticsOwner
            ?.getAllSemanticsNodes(mergingEnabled = false)
            ?.firstOrNull { it.id.toLong() == request.nodeId }
            ?: return false
        val config = targetNode.config
        return when (request.action) {
            WinUIAccessibilityAction.FOCUS ->
                config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke() == true
            WinUIAccessibilityAction.CLICK ->
                config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true
            WinUIAccessibilityAction.EXPAND ->
                config.getOrNull(SemanticsActions.Expand)?.action?.invoke() == true
            WinUIAccessibilityAction.COLLAPSE ->
                config.getOrNull(SemanticsActions.Collapse)?.action?.invoke() == true
            WinUIAccessibilityAction.SET_TEXT ->
                config.getOrNull(SemanticsActions.SetText)?.action?.invoke(
                    AnnotatedString(request.text.orEmpty()),
                ) == true
            WinUIAccessibilityAction.INCREMENT ->
                config.performProgressAction(increment = true)
            WinUIAccessibilityAction.DECREMENT ->
                config.performProgressAction(increment = false)
        }
    }

    fun stateForTest(): WinUIAccessibilityBridgeState =
        WinUIAccessibilityBridgeState(
            isAccessibilityForcedForTesting = isAccessibilityForcedForTesting,
            isAccessibilityProviderAttached = isAccessibilityProviderAttached,
            accessibilityEventBatchIntervalMillis = accessibilityEventBatchIntervalMillis,
            currentSemanticsNodesInvalidated = currentSemanticsNodesInvalidated,
            hasPendingFlush = pendingPost != null,
            pendingSemanticsChange = hasPendingSemanticsChange,
            pendingLayoutNodeIds = changedLayoutNodeIds.toList(),
            pendingScrollDelta = pendingScrollDelta,
            hasPendingScrollChange = hasPendingScrollChange,
        )

    private fun scheduleFlushIfNeeded() {
        if (
            !isAccessibilityEnabled() ||
            isDisposed ||
            pendingPost != null ||
            !hasPendingAccessibilityUpdate()
        ) {
            return
        }
        pendingPost = postDelayed(accessibilityEventBatchIntervalMillis, ::flush)
    }

    private fun hasPendingAccessibilityUpdate(): Boolean =
        hasPendingSemanticsChange ||
            changedLayoutNodeIds.isNotEmpty() ||
            hasPendingScrollChange

    private fun flush() {
        pendingPost = null
        if (isDisposed || !isAccessibilityEnabled() || !hasPendingAccessibilityUpdate()) {
            return
        }
        val update = WinUIAccessibilityUpdate(
            semanticsOwner = pendingSemanticsOwner,
            semanticsChanged = hasPendingSemanticsChange,
            layoutChangedSemanticsIds = changedLayoutNodeIds.toList(),
            scrollDelta = pendingScrollDelta.takeIf { hasPendingScrollChange },
            change = pendingWinUIAccessibilityChange(),
        )
        changedLayoutNodeIds.clear()
        pendingSemanticsOwner = null
        hasPendingSemanticsChange = false
        hasPendingScrollChange = false
        pendingScrollDelta = Offset.Zero
        currentSemanticsNodesInvalidated = false
        onUpdate(update)
    }

    private fun cancelPendingFlush() {
        pendingPost?.let(removePost)
        pendingPost = null
    }

    private fun isAccessibilityEnabled(): Boolean =
        isAccessibilityProviderAttached || isAccessibilityForcedForTesting

    private fun pendingWinUIAccessibilityChange(): WinUIAccessibilityChange =
        WinUIAccessibilityChange(
            type = when {
                hasPendingSemanticsChange -> WinUIAccessibilityChangeType.STRUCTURE_CHANGED
                hasPendingScrollChange -> WinUIAccessibilityChangeType.VALUE_CHANGED
                else -> WinUIAccessibilityChangeType.NODE_UPDATED
            },
            nodeId = changedLayoutNodeIds.firstOrNull()?.toLong(),
        )

    private companion object {
        const val DefaultEventBatchIntervalMillis = 100L

        val EmptySnapshot = WinUIAccessibilitySnapshot(
            root = WinUIAccessibilityNode(
                id = 0L,
                bounds = WinUIRect(0f, 0f, 0f, 0f),
                info = WinUIAccessibilityInfo(),
                state = WinUIAccessibilityState(),
                children = emptyList(),
            ),
        )
    }
}

internal data class WinUIAccessibilityUpdate(
    val semanticsOwner: SemanticsOwner?,
    val semanticsChanged: Boolean,
    val layoutChangedSemanticsIds: List<Int>,
    val scrollDelta: Offset?,
    val change: WinUIAccessibilityChange,
)

internal data class WinUIAccessibilityBridgeState(
    val isAccessibilityForcedForTesting: Boolean,
    val isAccessibilityProviderAttached: Boolean,
    val accessibilityEventBatchIntervalMillis: Long,
    val currentSemanticsNodesInvalidated: Boolean,
    val hasPendingFlush: Boolean,
    val pendingSemanticsChange: Boolean,
    val pendingLayoutNodeIds: List<Int>,
    val pendingScrollDelta: Offset,
    val hasPendingScrollChange: Boolean,
)

private fun SemanticsOwner.toWinUIAccessibilitySnapshot(): WinUIAccessibilitySnapshot {
    val root = unmergedRootSemanticsNode.toWinUIAccessibilityNode()
    return WinUIAccessibilitySnapshot(
        root = root,
        focusedNodeId = root.findFocusedNodeId(),
    )
}

private fun WinUIAccessibilityNode.findFocusedNodeId(): Long? {
    if (state.focused) return id
    children.forEach { child ->
        child.findFocusedNodeId()?.let { return it }
    }
    return null
}

private fun SemanticsNode.toWinUIAccessibilityNode(): WinUIAccessibilityNode {
    val nodeConfig = config
    val bounds = boundsInRoot
    return WinUIAccessibilityNode(
        id = id.toLong(),
        bounds = WinUIRect(
            x = bounds.left,
            y = bounds.top,
            width = bounds.width,
            height = bounds.height,
        ),
        info = nodeConfig.toWinUIAccessibilityInfo(),
        state = nodeConfig.toWinUIAccessibilityState(),
        value = nodeConfig.accessibilityValue().orEmpty(),
        actions = nodeConfig.toWinUIAccessibilityActions(),
        children = children
            .filterNot { it.config.isHiddenFromAccessibility() }
            .map { it.toWinUIAccessibilityNode() },
    )
}

private fun SemanticsConfiguration.toWinUIAccessibilityInfo(): WinUIAccessibilityInfo =
    WinUIAccessibilityInfo(
        name = accessibilityName().orEmpty(),
        automationId = getOrNull(SemanticsProperties.TestTag).orEmpty(),
        helpText = (
            getOrNull(SemanticsProperties.StateDescription)
                ?: getOrNull(SemanticsProperties.Error)
            ).orEmpty(),
        view = WinUIAccessibilityView.CONTENT,
        liveSetting = when (getOrNull(SemanticsProperties.LiveRegion)) {
            androidx.compose.ui.semantics.LiveRegionMode.Assertive ->
                WinUIAccessibilityLiveSetting.ASSERTIVE
            androidx.compose.ui.semantics.LiveRegionMode.Polite ->
                WinUIAccessibilityLiveSetting.POLITE
            else -> WinUIAccessibilityLiveSetting.OFF
        },
        role = getOrNull(SemanticsProperties.Role).toWinUIAccessibilityRole(this),
    )

private fun SemanticsConfiguration.toWinUIAccessibilityState(): WinUIAccessibilityState =
    WinUIAccessibilityState(
        enabled = !contains(SemanticsProperties.Disabled),
        focusable = contains(SemanticsActions.RequestFocus),
        focused = getOrNull(SemanticsProperties.Focused) == true,
        selected = getOrNull(SemanticsProperties.Selected) == true,
        checked = getOrNull(SemanticsProperties.ToggleableState)?.let {
            when (it) {
                ToggleableState.On -> true
                ToggleableState.Off -> false
                ToggleableState.Indeterminate -> null
            }
        },
        editable = getOrNull(SemanticsProperties.IsEditable) == true ||
            hasKey(SemanticsActions.SetText),
        password = contains(SemanticsProperties.Password),
    )

private fun SemanticsConfiguration.toWinUIAccessibilityActions(): Set<WinUIAccessibilityAction> =
    buildSet {
        if (hasKey(SemanticsActions.RequestFocus)) add(WinUIAccessibilityAction.FOCUS)
        if (hasKey(SemanticsActions.OnClick)) add(WinUIAccessibilityAction.CLICK)
        if (hasKey(SemanticsActions.Expand)) add(WinUIAccessibilityAction.EXPAND)
        if (hasKey(SemanticsActions.Collapse)) add(WinUIAccessibilityAction.COLLAPSE)
        if (hasKey(SemanticsActions.SetProgress)) {
            add(WinUIAccessibilityAction.INCREMENT)
            add(WinUIAccessibilityAction.DECREMENT)
        }
        if (hasKey(SemanticsActions.SetText)) add(WinUIAccessibilityAction.SET_TEXT)
    }

private fun SemanticsConfiguration.performProgressAction(increment: Boolean): Boolean {
    val rangeInfo = getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        ?: return false
    val setProgressAction = getOrNull(SemanticsActions.SetProgress)?.action
        ?: return false
    val min = rangeInfo.range.start.coerceAtMost(rangeInfo.range.endInclusive)
    val max = rangeInfo.range.endInclusive.coerceAtLeast(rangeInfo.range.start)
    if (min == max) return false
    val step = if (rangeInfo.steps > 0) {
        (max - min) / (rangeInfo.steps + 1)
    } else {
        (max - min) / AccessibilitySliderStepsCount
    }
    val signedStep = if (increment) step else -step
    val target = (rangeInfo.current + signedStep).coerceIn(min, max)
    if (target == rangeInfo.current) return false
    return setProgressAction.invoke(target) == true
}

private fun SemanticsConfiguration.accessibilityName(): String? =
    getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ") ?:
        getOrNull(SemanticsProperties.EditableText)?.text ?:
        getOrNull(SemanticsProperties.Text)?.joinToString(separator = "\n") { it.text } ?:
        getOrNull(SemanticsProperties.PaneTitle)

private fun SemanticsConfiguration.accessibilityValue(): String? =
    getOrNull(SemanticsProperties.EditableText)?.text ?:
        getOrNull(SemanticsProperties.Text)?.joinToString(separator = "\n") { it.text }

private fun SemanticsConfiguration.isHiddenFromAccessibility(): Boolean =
    contains(SemanticsProperties.HideFromAccessibility)

private fun SemanticsConfiguration.hasKey(key: SemanticsPropertyKey<*>): Boolean =
    any { it.key == key }

private const val AccessibilitySliderStepsCount = 20

private fun Role?.toWinUIAccessibilityRole(
    config: SemanticsConfiguration,
): WinUIAccessibilityRole =
    when (this) {
        Role.Button -> WinUIAccessibilityRole.BUTTON
        Role.Checkbox, Role.Switch -> WinUIAccessibilityRole.CHECK_BOX
        Role.DropdownList -> WinUIAccessibilityRole.COMBO_BOX
        Role.Image -> WinUIAccessibilityRole.IMAGE
        Role.RadioButton -> WinUIAccessibilityRole.CHECK_BOX
        Role.Tab -> WinUIAccessibilityRole.LIST_ITEM
        else -> when {
            config.contains(SemanticsProperties.IsEditable) ||
                config.hasKey(SemanticsActions.SetText) -> WinUIAccessibilityRole.EDIT
            config.getOrNull(SemanticsProperties.Text) != null -> WinUIAccessibilityRole.TEXT
            config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null ->
                WinUIAccessibilityRole.SLIDER
            config.contains(SemanticsProperties.IsDialog) -> WinUIAccessibilityRole.WINDOW
            config.contains(SemanticsProperties.PaneTitle) -> WinUIAccessibilityRole.PANE
            else -> WinUIAccessibilityRole.CUSTOM
        }
    }
