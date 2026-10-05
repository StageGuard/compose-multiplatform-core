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

package androidx.compose.ui.winui.samples

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.retain.ForgetfulRetainedValuesStore
import androidx.compose.runtime.retain.LocalRetainedValuesStore
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.FillableData
import androidx.compose.ui.autofill.createFromBoolean
import androidx.compose.ui.autofill.createFromDateMillis
import androidx.compose.ui.autofill.createFromListIndex
import androidx.compose.ui.autofill.createFromText
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.indirect.IndirectPointerEvent
import androidx.compose.ui.input.indirect.IndirectPointerEventPrimaryDirectionalMotionAxis
import androidx.compose.ui.input.indirect.IndirectPointerEventType
import androidx.compose.ui.input.indirect.IndirectPointerInputChange
import androidx.compose.ui.input.indirect.IndirectPointerInputModifierNode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.OnPlacedModifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.WinUIComposeView
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.platform.sendPointerEventForTest
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.requestFocus
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.sensitiveContent
import androidx.compose.ui.spatial.RelativeLayoutBounds
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.WinUIInteropProperties
import androidx.compose.ui.viewinterop.WinUIView
import androidx.compose.ui.window.Application
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowBackdrop
import androidx.compose.ui.window.currentComposeViewForTest
import androidx.compose.foundation.text.BasicText
import windows.foundation.EventRegistrationToken
import io.github.composefluent.winrt.runtime.asWinRT
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.automation.peers.AccessibilityView
import microsoft.ui.xaml.ElementTheme
import microsoft.ui.xaml.FlowDirection
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.Canvas
import microsoft.ui.xaml.controls.ContentControl
import microsoft.ui.xaml.controls.TextBox
import microsoft.ui.xaml.controls.ToggleSwitch
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.media.RectangleGeometry
import microsoft.ui.xaml.Window as XamlWindow
import windows.foundation.Rect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.winui.WinUIAccessibilityAction
import org.jetbrains.skiko.winui.WinUIAccessibilityActionRequest
import org.jetbrains.skiko.winui.WinUIAccessibilityLiveSetting
import org.jetbrains.skiko.winui.WinUIAccessibilityNode

private val LocalWindowPopupSmokeMarker = staticCompositionLocalOf { "missing" }

private object WinUIEnvironmentSmokeState {
    var theme: SystemTheme = SystemTheme.Unknown
    var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    var fontScale: Float = 1f
    var lifecycleState: Lifecycle.State = Lifecycle.State.INITIALIZED
}

@Composable
fun WinUIViewSampleContent(
    modifier: Modifier = Modifier,
    content: String = "Hello from Compose WinUI",
    isNativeAccessibilityEnabled: Boolean = true,
    isUserInteractionEnabled: Boolean = true,
    clipToBounds: Boolean = false,
    expectWindowFocus: Boolean = false,
    lifecycleProbe: WinUIViewLifecycleProbe? = null,
    onUpdated: ((Button) -> Unit)? = null,
) {
    ValidateWinUICompositionLocals(expectWindowFocus)
    WinUIView(
        modifier = modifier,
        properties = WinUIInteropProperties(
            isNativeAccessibilityEnabled = isNativeAccessibilityEnabled,
            isUserInteractionEnabled = isUserInteractionEnabled,
            clipToBounds = clipToBounds,
        ),
        factory = {
            lifecycleProbe?.let { it.factoryCount += 1 }
            Button().apply {
                this.content = "Hello from WinUIView"
            }
        },
        update = { button ->
            lifecycleProbe?.let {
                it.updateCount += 1
                it.lastButton = button
            }
            button.content = content
            lifecycleProbe?.lastContent = button.content as? String
            onUpdated?.invoke(button)
        },
        onReset = { button ->
            lifecycleProbe?.let { it.resetCount += 1 }
            button.content = "Reset WinUIView"
        },
        onRelease = { button ->
            lifecycleProbe?.let { it.releaseCount += 1 }
            lifecycleProbe?.lastButton = button
        },
    )
}

@OptIn(InternalComposeUiApi::class)
@Composable
private fun ValidateWinUICompositionLocals(
    expectWindowFocus: Boolean,
    allowWindowFocusChanges: Boolean = false,
) {
    val density = LocalDensity.current
    check(density.density.isFinite() && density.density > 0f) {
        "WinUI LocalDensity was not provided by WinUIComposeView."
    }
    val fontScale = density.fontScale
    check(fontScale.isFinite() && fontScale > 0f) {
        "WinUI LocalDensity.fontScale was not provided by WinUIComposeView."
    }
    val layoutDirection = LocalLayoutDirection.current
    check(layoutDirection == LayoutDirection.Ltr || layoutDirection == LayoutDirection.Rtl) {
        "WinUI LocalLayoutDirection was not provided by WinUIComposeView."
    }
    check(LocalViewConfiguration.current.longPressTimeoutMillis > 0L) {
        "WinUI LocalViewConfiguration was not provided by WinUIComposeView."
    }
    check(LocalFontFamilyResolver.current.resolve().value != null) {
        "WinUI LocalFontFamilyResolver was not provided by WinUIComposeView."
    }
    check(
        LocalLifecycleOwner.current.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)
    ) {
        "WinUI LocalLifecycleOwner was not provided in an attachable lifecycle state."
    }
    check(LocalSavedStateRegistryOwner.current != null) {
        "WinUI LocalSavedStateRegistryOwner was not provided."
    }
    check(LocalViewModelStoreOwner.current != null) {
        "WinUI LocalViewModelStoreOwner was not provided."
    }
    check(LocalSaveableStateRegistry.current != null) {
        "WinUI LocalSaveableStateRegistry was not provided."
    }
    check(LocalRetainedValuesStore.current !== ForgetfulRetainedValuesStore) {
        "WinUI LocalRetainedValuesStore was not provided."
    }
    val uriHandler = checkNotNull(LocalUriHandler.current) {
        "WinUI LocalUriHandler was not provided by WinUIComposeView."
    }
    check(runCatching { uriHandler.openUri("missing-scheme") }.isFailure) {
        "WinUI LocalUriHandler did not reject a URI without a scheme."
    }
    ValidateWinUIClipboard()
    ValidateWinUIAutofill()
    ValidateWinUIFillableData()
    if (expectWindowFocus) {
        val windowInfo = LocalWindowInfo.current
        if (!allowWindowFocusChanges) {
            check(windowInfo.isWindowFocused) {
                "WinUI LocalWindowInfo did not reflect the active WinUI window."
            }
        }
        check(windowInfo.containerSize.width > 0) {
            "WinUI LocalWindowInfo did not expose a positive container width."
        }
        check(windowInfo.containerDpSize.width.value > 0f) {
            "WinUI LocalWindowInfo did not expose a positive container dp width."
        }
    }
}

@OptIn(InternalComposeUiApi::class)
@Composable
private fun CaptureWinUIEnvironment() {
    WinUIEnvironmentSmokeState.theme = LocalSystemTheme.current
    WinUIEnvironmentSmokeState.layoutDirection = LocalLayoutDirection.current
    WinUIEnvironmentSmokeState.fontScale = LocalDensity.current.fontScale

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    WinUIEnvironmentSmokeState.lifecycleState = lifecycle.currentState
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            WinUIEnvironmentSmokeState.lifecycleState = lifecycle.currentState
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun ValidateWinUIAutofill() {
    val autofill = checkNotNull(LocalAutofill.current) {
        "WinUI LocalAutofill was not provided by WinUIComposeView."
    }
    val autofillManager = checkNotNull(LocalAutofillManager.current) {
        "WinUI LocalAutofillManager was not provided by WinUIComposeView."
    }
    val autofillTree = LocalAutofillTree.current
    val autofillNode = remember {
        AutofillNode(
            boundingBox = ComposeRect(0f, 0f, 1f, 1f),
            onFill = { value ->
                WinUIAutofillSmokeState.filledValue = value
            },
        )
    }
    LaunchedEffect(autofill, autofillManager, autofillTree, autofillNode) {
        if (WinUIAutofillSmokeState.autofillPassed) return@LaunchedEffect
        WinUIAutofillSmokeState.autofillPassed = true
        autofillTree += autofillNode
        try {
            autofill.requestAutofillForNode(autofillNode)
            autofillTree.performAutofill(autofillNode.id, "compose-winui autofill local")
            check(WinUIAutofillSmokeState.filledValue == "compose-winui autofill local") {
                "WinUI LocalAutofillTree did not route a legacy autofill callback."
            }
            autofill.cancelAutofillForNode(autofillNode)
            autofillManager.commit()
            autofillManager.cancel()
        } finally {
            autofillTree.children.remove(autofillNode.id)
        }
    }
}

private object WinUIAutofillSmokeState {
    var autofillPassed: Boolean = false
    var filledValue: String? = null
}

private fun ValidateWinUIFillableData() {
    val textData = checkNotNull(FillableData.createFromText("compose-winui autofill")) {
        "WinUI FillableData did not create text data."
    }
    check(textData.textValue?.toString() == "compose-winui autofill") {
        "WinUI FillableData did not expose text value."
    }
    val booleanData = checkNotNull(FillableData.createFromBoolean(true)) {
        "WinUI FillableData did not create boolean data."
    }
    check(booleanData.booleanValue == true) {
        "WinUI FillableData did not expose boolean value."
    }
    val listData = checkNotNull(FillableData.createFromListIndex(3)) {
        "WinUI FillableData did not create list index data."
    }
    check(listData.getListIndexOrDefault(-1) == 3) {
        "WinUI FillableData did not expose list index value."
    }
    val dateData = checkNotNull(FillableData.createFromDateMillis(1234L)) {
        "WinUI FillableData did not create date millis data."
    }
    check(dateData.getDateMillisOrDefault(-1L) == 1234L) {
        "WinUI FillableData did not expose date millis value."
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ValidateWinUIClipboard() {
    val clipboard = LocalClipboard.current
    if (!WinUIClipboardSmokeState.clipboardPassed) {
        LaunchedEffect(clipboard) {
            if (WinUIClipboardSmokeState.clipboardPassed) return@LaunchedEffect
            // Keep the WinRT clipboard smoke to one write per sample run; clipboard ownership can
            // be transiently locked by another process while still validating the Compose locals.
            WinUIClipboardSmokeState.clipboardPassed = true
            clipboard.setClipEntry(ClipEntry.withPlainText("compose-winui clipboard"))
            val clipboardText = clipboard.getClipEntry()?.getPlainText()
            check(clipboardText == "compose-winui clipboard") {
                "WinUI LocalClipboard did not round-trip plain text: actual=$clipboardText."
            }
        }
    }
}

private object WinUIClipboardSmokeState {
    var clipboardPassed: Boolean = false
}

class WinUIViewLifecycleProbe {
    var factoryCount: Int = 0
    var updateCount: Int = 0
    var resetCount: Int = 0
    var releaseCount: Int = 0
    var lastButton: Button? = null
    var lastContent: String? = null
}

@Composable
private fun WinUIViewWindowIntegrationContent(
    buttonContent: String,
    toggleOn: Boolean,
    expectWindowFocus: Boolean,
    lifecycleProbe: WinUIViewLifecycleProbe,
    buttonModifier: Modifier = Modifier,
    onButtonUpdated: (Button) -> Unit,
    onTextBoxUpdated: (TextBox) -> Unit,
    onToggleSwitchUpdated: (ToggleSwitch) -> Unit,
    onWindowFocusChanged: ((Boolean) -> Unit)? = null,
) {
    ValidateWinUICompositionLocals(
        expectWindowFocus = expectWindowFocus,
        allowWindowFocusChanges = onWindowFocusChanged != null,
    )
    CaptureWinUIEnvironment()
    if (expectWindowFocus && onWindowFocusChanged != null) {
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        DisposableEffect(windowFocused) {
            onWindowFocusChanged(windowFocused)
            onDispose {}
        }
    }
    ValidateWinUIOwnerFocus()
    Layout(
        modifier = Modifier
            .keepScreenOn()
            .sensitiveContent(),
        content = {},
    ) { _, _ ->
        layout(1, 1) {}
    }
    WinUIView(
        modifier = fixedSizeAndPositionModifier(
            width = 160,
            height = 40,
            x = 0,
            y = 0,
        ).then(buttonModifier),
        factory = {
            lifecycleProbe.factoryCount += 1
            Button()
        },
        update = { button ->
            lifecycleProbe.updateCount += 1
            lifecycleProbe.lastButton = button
            button.content = buttonContent
            lifecycleProbe.lastContent = button.content as? String
            onButtonUpdated(button)
        },
        onReset = { button ->
            lifecycleProbe.resetCount += 1
            button.content = "Reset WinUIView"
        },
        onRelease = { button ->
            lifecycleProbe.releaseCount += 1
            lifecycleProbe.lastButton = button
        },
    )
    WinUIView(
        modifier = fixedSizeAndPositionModifier(
            width = 220,
            height = 40,
            x = 0,
            y = 48,
        ),
        factory = { TextBox() },
        update = { textBox ->
            textBox.text = buttonContent
            onTextBoxUpdated(textBox)
        },
    )
    WinUIView(
        modifier = fixedSizeAndPositionModifier(
            width = 220,
            height = 40,
            x = 0,
            y = 96,
        ),
        factory = { ToggleSwitch() },
        update = { toggleSwitch ->
            toggleSwitch.isOn = toggleOn
            onToggleSwitchUpdated(toggleSwitch)
        },
    )
}

@Composable
private fun ValidateWinUIOwnerFocus() {
    val focusRequester = remember { FocusRequester() }
    var focusObserved by remember { mutableStateOf(false) }
    Layout(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusObserved = it.isFocused }
            .focusTarget(),
        content = {},
    ) { _, _ ->
        layout(1, 1) {}
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        check(focusRequester.requestFocus()) {
            "WinUI Compose focus requester could not accept owner focus request."
        }
        withFrameNanos { }
        check(focusObserved) {
            "WinUI Compose focus requester did not observe focused state."
        }
        println("compose-winui-sample: owner focus request accepted")
    }
}

fun main(args: Array<String>) {
    val sampleMode = WinUIViewSampleMode.fromArgs(args)
    println("compose-winui-sample: application starting mode=${sampleMode.id}")
    Application {
        ComposeWinUiSmokeApp.launch(this, sampleMode)
    }
}

private enum class WinUIViewSampleMode(
    val id: String,
) {
    Full("full"),
    Accessibility("accessibility"),
    Window("window"),
    Interop("interop"),
    Owner("owner"),
    Pointer("pointer"),
    Rendering("rendering"),
    Shutdown("shutdown"),
    TextInput("text-input"),
    WindowPopup("window-popup"),
    Skiko("skiko");

    companion object {
        fun fromArgs(args: Array<String>): WinUIViewSampleMode {
            val id = args.firstOrNull()
                ?: System.getProperty("compose.winui.sample.mode")
                ?: Full.id
            return entries.firstOrNull { it.id == id } ?: error(
                "Unknown compose-winui sample mode '$id'. " +
                    "Expected one of ${entries.joinToString { it.id }}."
            )
        }
    }
}

private object ComposeWinUiSmokeApp {
    private var composeView: Any? = null

    @Composable
    fun launch(
        applicationScope: ApplicationScope,
        sampleMode: WinUIViewSampleMode,
    ) {
        if (sampleMode == WinUIViewSampleMode.Skiko) {
            LaunchedEffect(Unit) {
                runSmoke(applicationScope) {
                    runWinUISkikoRuntimeClasspathSmoke()
                    runWinUISkikoUnattachedSchedulerSmoke()
                    runWinUISkikoRenderDiagnosticsSmoke()
                    if (java.lang.Boolean.getBoolean("compose.winui.sample.autoExit")) {
                        applicationScope.exitApplication()
                    }
                }
            }
            return
        }
        if (sampleMode == WinUIViewSampleMode.WindowPopup) {
            RunWindowPopupSmoke(applicationScope)
            return
        }
        if (sampleMode != WinUIViewSampleMode.Full && sampleMode != WinUIViewSampleMode.Window) {
            LaunchedEffect(Unit) {
                runSmoke(applicationScope) {
                    when (sampleMode) {
                        WinUIViewSampleMode.Accessibility -> runAccessibilitySmokeSuite()
                        WinUIViewSampleMode.Interop -> runInteropSmokeSuite()
                        WinUIViewSampleMode.Owner -> runOwnerSmokeSuite()
                        WinUIViewSampleMode.Pointer -> runPointerSmokeSuite()
                        WinUIViewSampleMode.Rendering -> runRenderingSmokeSuite()
                        WinUIViewSampleMode.Shutdown -> runShutdownSmokeSuite()
                        WinUIViewSampleMode.TextInput -> runTextInputSmokeSuite()
                        WinUIViewSampleMode.Full,
                        WinUIViewSampleMode.Window,
                        WinUIViewSampleMode.WindowPopup,
                        WinUIViewSampleMode.Skiko -> error("Unexpected focused sample mode $sampleMode.")
                    }
                    if (java.lang.Boolean.getBoolean("compose.winui.sample.autoExit")) {
                        applicationScope.exitApplication()
                    }
                }
            }
            return
        }

        var reuseSmokePassed by remember { mutableStateOf(sampleMode == WinUIViewSampleMode.Window) }
        var windowSmokePassed by remember { mutableStateOf(false) }
        var secondaryWindowVisible by remember { mutableStateOf(true) }
        var secondaryWindowClosePassed by remember { mutableStateOf(false) }
        var secondaryWindowCloseRequested by remember { mutableStateOf(false) }
        var secondaryWindowCloseReturnedWhileComposed by remember { mutableStateOf(false) }
        var secondaryWindowCloseRequestCount by remember { mutableStateOf(0) }
        var mainWindowFocusedOnce by remember { mutableStateOf(false) }
        var mainWindowDeactivatedPassed by remember { mutableStateOf(false) }
        val skipWindowSmoke = java.lang.Boolean.getBoolean("compose.winui.sample.skipWindowSmoke")
        val skipSecondaryWindowSmoke = java.lang.Boolean.getBoolean("compose.winui.sample.skipSecondaryWindowSmoke")
        if (sampleMode == WinUIViewSampleMode.Full) {
            remember {
                println("compose-winui-sample: application created")
                runWinUILifecycleOwnerSmoke()
                runWinUIViewModelOwnerSmoke()
                runWinUINavigationEventOwnerSmoke()
                runWinUIViewLifecycleSmoke()
                runWinUIViewZOrderSmoke()
                runWinUIViewUnclippedBoundsSmoke()
                if (!java.lang.Boolean.getBoolean("compose.winui.sample.skipControlVarietySmoke")) {
                    runWinUIViewControlVarietySmoke()
                }
                true
            }
        }
        if (skipWindowSmoke) {
            LaunchedEffect(Unit) {
                println("compose-winui-sample: window smoke skipped")
                if (java.lang.Boolean.getBoolean("compose.winui.sample.autoExit")) {
                    applicationScope.exitApplication()
                }
            }
        } else {
            LaunchedEffect(Unit) {
                runSmoke(applicationScope) {
                    if (
                        sampleMode == WinUIViewSampleMode.Full &&
                        !java.lang.Boolean.getBoolean("compose.winui.sample.skipLaunchedSmoke")
                    ) {
                        runWinUIViewPlacementSmoke()
                        runWinUIViewDensitySmoke()
                        runWinUIViewReuseSmoke()
                        runWinUIViewStateUpdateSmoke()
                        runWinUILayoutSnapshotInvalidationSmoke()
                        runWinUILayoutCompletedListenerSmoke()
                        runWinUILayoutRectChangedSmoke()
                        runWinUIOwnerLayerTransformSmoke()
                        runWinUIRootKeyEventSmoke()
                        runWinUIRootFocusTraversalKeySmoke()
                        runWinUIViewFocusInputIntegrationSmoke()
                        runWinUIRootSemanticsSmoke()
                        runWinUIOwnerEndApplyChangesSmoke()
                        runWinUIRootUncaughtExceptionHandlerSmoke()
                        runWinUIRootIndirectPointerSmoke()
                        runWinUIPointerInputSmoke()
                        runWinUIPointerMoveSmoke()
                        runWinUIPointerEnterExitSmoke()
                        runWinUIPointerScrollSmoke()
                        runWinUIViewPointerInteropSmoke()
                        runWinUIPointerCancelOnDisposeSmoke()
                        runWinUIViewRelayoutSmoke()
                        runWinUIViewPropertiesUpdateSmoke()
                        runWinUIViewContainerSyncSmoke()
                        runWinUIViewGeneratedEventCleanupSmoke()
                        runWinUISkikoRuntimeClasspathSmoke()
                        runWinUISkikoUnattachedSchedulerSmoke()
                        runWinUISkikoRenderDiagnosticsSmoke()
                        runWinUISaveableStateSmoke()
                        runWinUIRetainedValuesSmoke()
                        if (!java.lang.Boolean.getBoolean("compose.winui.sample.skipTextInputSmoke")) {
                            runWinUITextInputSessionSmoke()
                        }
                    }
                    reuseSmokePassed = true
                }
            }
        }
        LaunchedEffect(reuseSmokePassed, windowSmokePassed, secondaryWindowClosePassed) {
            if (
                reuseSmokePassed &&
                windowSmokePassed &&
                secondaryWindowClosePassed &&
                java.lang.Boolean.getBoolean("compose.winui.sample.autoExit")
            ) {
                check(secondaryWindowCloseRequestCount == 1) {
                    "Secondary WinUI window close request count was " +
                        "$secondaryWindowCloseRequestCount."
                }
                applicationScope.exitApplication()
            }
            if (
                skipSecondaryWindowSmoke &&
                windowSmokePassed &&
                java.lang.Boolean.getBoolean("compose.winui.sample.autoExit")
            ) {
                applicationScope.exitApplication()
            }
        }
        LaunchedEffect(secondaryWindowCloseRequested) {
            if (secondaryWindowCloseRequested) {
                awaitCondition("secondary WinUI pre-close cancellation") {
                    secondaryWindowCloseReturnedWhileComposed
                }
                secondaryWindowClosePassed = true
                secondaryWindowVisible = false
            }
        }
        if (!skipWindowSmoke) {
            val windowProbe = remember {
                println("compose-winui-sample: window created")
                WinUIViewLifecycleProbe()
            }
            var title by remember { mutableStateOf("compose-winui sample") }
            var extendsContentIntoTitleBar by remember { mutableStateOf(false) }
            var backdrop: WindowBackdrop by remember { mutableStateOf(WindowBackdrop.Mica) }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                title = "compose-winui sample updated"
                extendsContentIntoTitleBar = true
                backdrop = WindowBackdrop.DesktopAcrylic
            }
            with(applicationScope) {
                Window(
                    title = title,
                    extendsContentIntoTitleBar = extendsContentIntoTitleBar,
                    backdrop = backdrop,
                ) {
                var content by remember { mutableStateOf("Hello from Compose WinUI") }
                var backdropSmokePassed by remember { mutableStateOf(false) }
                var backdropClearSmokePassed by remember { mutableStateOf(false) }
                var environmentSmokePassed by remember { mutableStateOf(false) }
                var lastButton by remember { mutableStateOf<Button?>(null) }
                var lastTextBox by remember { mutableStateOf<TextBox?>(null) }
                var lastToggleSwitch by remember { mutableStateOf<ToggleSwitch?>(null) }
                val nativeFocusRequester = remember { FocusRequester() }
                var nativeFocusSmokePassed by remember {
                    mutableStateOf(java.lang.Boolean.getBoolean("compose.winui.sample.skipNativeFocusSmoke"))
                }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    content = "Hello from Compose WinUI updated"
                }
                LaunchedEffect(Unit) {
                    // The content of the window is the root of the Compose view. It is taken from
                    // the view: read back from Window.Content it is a UIElement wrapper, which
                    // the kotlin-winrt build in use does not cast to the authored ContentControl.
                    @OptIn(InternalComposeUiApi::class)
                    val composeView = currentComposeViewForTest
                    val root = checkNotNull(composeView?.root.asWinRTContentControl()) {
                        "WinUI environment smoke could not find the Compose root ContentControl."
                    }
                    awaitCondition("WinUI root loaded") { root.isLoaded }
                    awaitCondition("WinUI lifecycle RESUMED") {
                        WinUIEnvironmentSmokeState.lifecycleState == Lifecycle.State.RESUMED
                    }
                    awaitCondition("WinUI effective theme") {
                        WinUIEnvironmentSmokeState.theme == SystemTheme.Light ||
                            WinUIEnvironmentSmokeState.theme == SystemTheme.Dark
                    }
                    val originalRequestedTheme = root.requestedTheme
                    val originalFlowDirection = root.flowDirection
                    val originalTheme = WinUIEnvironmentSmokeState.theme
                    val originalLayoutDirection = WinUIEnvironmentSmokeState.layoutDirection
                    check(originalTheme == SystemTheme.Light || originalTheme == SystemTheme.Dark) {
                        "WinUI environment smoke did not observe an effective Light/Dark theme."
                    }
                    check(
                        WinUIEnvironmentSmokeState.fontScale.isFinite() &&
                            WinUIEnvironmentSmokeState.fontScale > 0f
                    ) {
                        "WinUI environment smoke observed an invalid font scale."
                    }
                    val requestedTheme = if (originalTheme == SystemTheme.Dark) {
                        ElementTheme.Light
                    } else {
                        ElementTheme.Dark
                    }
                    val expectedTheme = if (requestedTheme == ElementTheme.Dark) {
                        SystemTheme.Dark
                    } else {
                        SystemTheme.Light
                    }
                    try {
                        root.requestedTheme = requestedTheme
                        awaitCondition("WinUI requested theme") {
                            WinUIEnvironmentSmokeState.theme == expectedTheme
                        }
                        root.flowDirection = FlowDirection.RightToLeft
                        awaitCondition("WinUI RTL flow direction") {
                            WinUIEnvironmentSmokeState.layoutDirection == LayoutDirection.Rtl
                        }
                    } finally {
                        root.requestedTheme = originalRequestedTheme
                        root.flowDirection = originalFlowDirection
                    }
                    awaitCondition("WinUI requested theme restore") {
                        WinUIEnvironmentSmokeState.theme == originalTheme
                    }
                    awaitCondition("WinUI flow direction restore") {
                        WinUIEnvironmentSmokeState.layoutDirection == originalLayoutDirection
                    }
                    environmentSmokePassed = true
                    println("compose-winui-sample: system environment and lifecycle")
                }
                LaunchedEffect(backdrop) {
                    if (backdrop == WindowBackdrop.DesktopAcrylic) {
                        withFrameNanos { }
                        backdropSmokePassed = true
                        backdrop = WindowBackdrop.None
                    } else if (backdrop == WindowBackdrop.None && backdropSmokePassed) {
                        withFrameNanos { }
                        backdropClearSmokePassed = true
                        println("compose-winui-sample: window backdrop cleared")
                    }
                }
                LaunchedEffect(
                    title,
                    extendsContentIntoTitleBar,
                    backdrop,
                    backdropSmokePassed,
                    backdropClearSmokePassed,
                    environmentSmokePassed,
                    lastButton,
                    lastTextBox,
                    lastToggleSwitch,
                    nativeFocusSmokePassed,
                ) {
                    lastButton ?: return@LaunchedEffect
                    val textBox = lastTextBox ?: return@LaunchedEffect
                    val toggleSwitch = lastToggleSwitch ?: return@LaunchedEffect
                    if (
                        windowProbe.updateCount >= 2 &&
                        windowProbe.lastContent == "Hello from Compose WinUI updated" &&
                        textBox.text == "Hello from Compose WinUI updated" &&
                        toggleSwitch.isOn &&
                        title == "compose-winui sample updated" &&
                        extendsContentIntoTitleBar &&
                        backdrop == WindowBackdrop.None &&
                        backdropSmokePassed &&
                        backdropClearSmokePassed &&
                        environmentSmokePassed &&
                        nativeFocusSmokePassed
                    ) {
                        windowSmokePassed = true
                    }
                }
                LaunchedEffect(lastButton, mainWindowFocusedOnce) {
                    val button = lastButton ?: return@LaunchedEffect
                    if (!mainWindowFocusedOnce || nativeFocusSmokePassed) return@LaunchedEffect
                    withFrameNanos { }
                    check(nativeFocusRequester.requestFocus()) {
                        "WinUIView native focus smoke could not focus the interop host."
                    }
                    awaitCondition("WinUIView native focus transfer") {
                        button.focusState != microsoft.ui.xaml.FocusState.Unfocused
                    }
                    nativeFocusSmokePassed = true
                    println("compose-winui-sample: native focus transfer")
                }
                WinUIViewWindowIntegrationContent(
                    buttonContent = content,
                    toggleOn = content.endsWith("updated"),
                    expectWindowFocus = true,
                    lifecycleProbe = windowProbe,
                    buttonModifier = Modifier.focusRequester(nativeFocusRequester),
                    onButtonUpdated = { button ->
                        composeView = button
                        lastButton = button
                        println(
                            "compose-winui-sample: compose button content=" +
                                (windowProbe.lastContent ?: "not-found")
                        )
                        println("compose-winui-sample: window title=${window.title}")
                        println("compose-winui-sample: window content set")
                        println("compose-winui-sample: window activated")
                    },
                    onTextBoxUpdated = { textBox ->
                        lastTextBox = textBox
                    },
                    onToggleSwitchUpdated = { toggleSwitch ->
                        lastToggleSwitch = toggleSwitch
                    },
                    onWindowFocusChanged = { focused ->
                        if (focused && !mainWindowFocusedOnce) {
                            mainWindowFocusedOnce = true
                            println("compose-winui-sample: main window activated")
                        }
                        if (!focused && !mainWindowDeactivatedPassed) {
                            mainWindowDeactivatedPassed = true
                            println("compose-winui-sample: main window deactivated")
                        }
                    },
                )
            }
                if (secondaryWindowVisible && !skipSecondaryWindowSmoke) {
                    Window(
                        onCloseRequest = {
                            secondaryWindowCloseRequestCount += 1
                            check(mainWindowFocusedOnce) {
                                "Primary WinUI window did not report an activated state."
                            }
                            check(mainWindowDeactivatedPassed) {
                                "Primary WinUI window did not report deactivation for secondary activation."
                            }
                            secondaryWindowCloseRequested = true
                            println("compose-winui-sample: secondary window close request")
                        },
                        title = "compose-winui secondary",
                    ) {
                        check(window.title == "compose-winui secondary") {
                            "Secondary WinUI window title was not applied."
                        }
                        LaunchedEffect(Unit) {
                            withFrameNanos { }
                            awaitCondition("primary WinUI window activation") {
                                mainWindowFocusedOnce
                            }
                            awaitCondition("primary WinUI window deactivation") {
                                mainWindowDeactivatedPassed
                            }
                            window.close()
                            secondaryWindowCloseReturnedWhileComposed = true
                            println("compose-winui-sample: secondary window pre-close canceled")
                        }
                    }
                }
            }
        }
    }

    private suspend fun runAccessibilitySmokeSuite() {
        runWinUIViewLifecycleSmoke()
        runWinUIRootSemanticsSmoke()
        runWinUIAccessibilityProviderSmoke()
        println("compose-winui-sample: accessibility suite")
    }

    private suspend fun runInteropSmokeSuite() {
        runWinUIViewLifecycleSmoke()
        runWinUIViewZOrderSmoke()
        runWinUIViewUnclippedBoundsSmoke()
        if (!java.lang.Boolean.getBoolean("compose.winui.sample.skipControlVarietySmoke")) {
            runWinUIViewControlVarietySmoke()
        }
        runWinUIViewPlacementSmoke()
        runWinUIViewDensitySmoke()
        runWinUIViewReuseSmoke()
        runWinUIViewStateUpdateSmoke()
        runWinUIViewRelayoutSmoke()
        runWinUIViewPropertiesUpdateSmoke()
        runWinUIViewContainerSyncSmoke()
        runWinUIViewGeneratedEventCleanupSmoke()
    }

    private suspend fun runOwnerSmokeSuite() {
        runWinUILifecycleOwnerSmoke()
        runWinUIViewModelOwnerSmoke()
        runWinUINavigationEventOwnerSmoke()
        runWinUISaveableStateSmoke()
        runWinUIRetainedValuesSmoke()
        runWinUILayoutSnapshotInvalidationSmoke()
        runWinUILayoutCompletedListenerSmoke()
        runWinUILayoutRectChangedSmoke()
        runWinUIOwnerLayerTransformSmoke()
        runWinUIRootKeyEventSmoke()
        runWinUIRootFocusTraversalKeySmoke()
        runWinUIRootSemanticsSmoke()
        runWinUIOwnerEndApplyChangesSmoke()
        runWinUIRootUncaughtExceptionHandlerSmoke()
        runWinUIRootIndirectPointerSmoke()
    }

    private suspend fun runPointerSmokeSuite() {
        runWinUIPointerInputSmoke()
        runWinUIPointerMoveSmoke()
        runWinUIPointerEnterExitSmoke()
        runWinUIPointerScrollSmoke()
        runWinUIViewPointerInteropSmoke()
        runWinUIPointerCancelOnDisposeSmoke()
    }

    private suspend fun runRenderingSmokeSuite() {
        runWinUILayoutCompletedListenerSmoke()
        runWinUIOwnerLayerTransformSmoke()
        runWinUIViewContainerSyncSmoke()
        runWinUISkikoRuntimeClasspathSmoke()
        runWinUISkikoUnattachedSchedulerSmoke()
        runWinUISkikoRenderDiagnosticsSmoke()
        println("compose-winui-sample: rendering suite")
    }

    private suspend fun runShutdownSmokeSuite() {
        runWinUIViewLifecycleSmoke()
        runWinUIViewGeneratedEventCleanupSmoke()
        runWinUICompositionShutdownSmoke()
        runWinUISkikoUnattachedSchedulerSmoke()
        println("compose-winui-sample: shutdown suite")
    }

    private suspend fun runTextInputSmokeSuite() {
        if (!java.lang.Boolean.getBoolean("compose.winui.sample.skipTextInputSmoke")) {
            runWinUITextInputSessionSmoke()
        }
    }

    private suspend fun runSmoke(
        applicationScope: ApplicationScope,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            applicationScope.exitApplication()
            throw throwable
        }
    }

    private fun runWinUIViewLifecycleSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        var showInterop = true
        currentComposeView.setContent {
            if (showInterop) {
                WinUIViewSampleContent(
                    modifier = fixedSizeAndPositionModifier(
                        width = 123,
                        height = 45,
                        x = 17,
                        y = 23,
                    ),
                    isNativeAccessibilityEnabled = false,
                    isUserInteractionEnabled = false,
                    clipToBounds = true,
                    lifecycleProbe = lifecycleProbe,
                )
            }
        }
        val rootCanvas = checkNotNull(rootHost.content.asWinRTCanvas()) {
            "WinUIView lifecycle smoke did not install an interop Canvas into the root host."
        }
        val wrapper = checkNotNull(rootCanvas.requiredInteropChildren.singleOrNull()) {
            "WinUIView lifecycle smoke did not install a wrapper into the root host."
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView lifecycle smoke did not install a Button into the wrapper."
        }
        check(!wrapper.isHitTestVisible) {
            "WinUIView did not apply isUserInteractionEnabled=false to the native wrapper."
        }
        check(!button.isHitTestVisible) {
            "WinUIView did not apply isUserInteractionEnabled=false to the native Button."
        }
        check(!button.isTabStop) {
            "WinUIView did not remove the native Button from Tab focus when disabled."
        }
        check(!button.isEnabled) {
            "WinUIView did not disable the native Button control when interaction was disabled."
        }
        check(AutomationProperties.getAccessibilityView(button) == AccessibilityView.Raw) {
            "WinUIView did not apply isNativeAccessibilityEnabled=false to the native Button."
        }
        check(button.width == 123.0 && button.height == 45.0) {
            "WinUIView did not apply Compose size to the native Button: " +
                "${button.width}x${button.height}."
        }
        val wrapperClip = checkNotNull(wrapper.readClipRectOrNull()) {
            "WinUIView did not apply clipToBounds to the native wrapper."
        }
        check(wrapperClip.width == 123f && wrapperClip.height == 45f) {
            "WinUIView did not apply clipToBounds to the native wrapper: " +
                "${wrapperClip.width}x${wrapperClip.height}."
        }
        check(lifecycleProbe.factoryCount == 1) {
            "Expected one WinUIView factory call, got ${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.updateCount == 1) {
            "Expected one WinUIView update call, got ${lifecycleProbe.updateCount}."
        }
        check(lifecycleProbe.resetCount == 0) {
            "Expected no WinUIView reset calls before reuse/deactivation, got " +
                "${lifecycleProbe.resetCount}."
        }
        println(
            "compose-winui-sample: lifecycle before release factory=" +
                "${lifecycleProbe.factoryCount} update=${lifecycleProbe.updateCount} " +
                "reset=${lifecycleProbe.resetCount} release=${lifecycleProbe.releaseCount}"
        )
        showInterop = false
        currentComposeView.setContent {
            if (showInterop) {
                WinUIViewSampleContent(
                    isNativeAccessibilityEnabled = false,
                    isUserInteractionEnabled = false,
                    lifecycleProbe = lifecycleProbe,
                )
            }
        }
        check((rootHost.content.asWinRTCanvas())?.requiredInteropChildren.orEmpty().isEmpty()) {
            "WinUIComposeView did not clear WinUIView content after it left composition."
        }
        check(lifecycleProbe.releaseCount == 1) {
            "Expected one WinUIView release call, got ${lifecycleProbe.releaseCount}."
        }
        check(wrapper.isHitTestVisible) {
            "WinUIView did not restore wrapper hit testing on release."
        }
        check(button.isHitTestVisible && button.isTabStop && button.isEnabled) {
            "WinUIView did not restore native Button interaction state on release."
        }
        check(wrapper.readClipRectOrNull() == null) {
            "WinUIView did not clear the native wrapper clip on release."
        }
        showInterop = true
        currentComposeView.setContent {
            if (showInterop) {
                WinUIViewSampleContent(
                    isUserInteractionEnabled = false,
                    lifecycleProbe = lifecycleProbe,
                )
            }
        }
        val recreatedRootCanvas = checkNotNull(rootHost.content.asWinRTCanvas()) {
            "WinUIView lifecycle smoke did not reinstall an interop Canvas after re-entering composition."
        }
        val recreatedWrapper = checkNotNull(recreatedRootCanvas.requiredInteropChildren.singleOrNull()) {
            "WinUIView lifecycle smoke did not recreate a wrapper after re-entering composition."
        }
        val recreatedButton = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView lifecycle smoke did not recreate a Button after re-entering composition."
        }
        check(!recreatedWrapper.nativeObject.sameIdentity(wrapper.nativeObject)) {
            "WinUIView reused a released wrapper without an explicit reusable group."
        }
        check(recreatedButton !== button) {
            "WinUIView reused a released Button without an explicit reusable group."
        }
        check(lifecycleProbe.factoryCount == 2) {
            "Expected a second WinUIView factory call after re-entering composition, got " +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.updateCount == 2) {
            "Expected a second WinUIView update call after re-entering composition, got " +
                "${lifecycleProbe.updateCount}."
        }
        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 2) {
            "Expected the recreated WinUIView to release during host disposal, got " +
                "${lifecycleProbe.releaseCount}."
        }
        println(
            "compose-winui-sample: lifecycle after release factory=" +
                "${lifecycleProbe.factoryCount} update=${lifecycleProbe.updateCount} " +
                "reset=${lifecycleProbe.resetCount} release=${lifecycleProbe.releaseCount}"
        )
    }

    private fun runWinUILifecycleOwnerSmoke() {
        val currentComposeView = WinUIComposeView()
        var lifecycle: Lifecycle? = null
        currentComposeView.setContent {
            lifecycle = LocalLifecycleOwner.current.lifecycle
        }
        val currentLifecycle = checkNotNull(lifecycle) {
            "WinUI lifecycle owner was not provided."
        }
        check(currentLifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            "WinUI lifecycle owner did not expose an attachable initial state."
        }
        currentComposeView.setHostActive(true)
        check(currentLifecycle.currentState == Lifecycle.State.CREATED) {
            "Unattached WinUI lifecycle owner advanced without a loaded root."
        }
        currentComposeView.dispose()
        check(currentLifecycle.currentState == Lifecycle.State.DESTROYED) {
            "WinUI lifecycle owner did not enter DESTROYED state on dispose."
        }
        println("compose-winui-sample: lifecycle owner created and destroyed")
    }

    private fun runWinUIViewModelOwnerSmoke() {
        val currentComposeView = WinUIComposeView()
        var viewModelStoreOwnerProvided = false
        var firstViewModel: WinUISavedStateViewModel? = null
        currentComposeView.setContent {
            viewModelStoreOwnerProvided = LocalViewModelStoreOwner.current != null
            firstViewModel = viewModel(key = "winui-saved-state-view-model") {
                WinUISavedStateViewModel(createSavedStateHandle())
            }
        }
        val initialViewModel = checkNotNull(firstViewModel) {
            "WinUI viewModel() did not create a ViewModel."
        }
        check(viewModelStoreOwnerProvided) {
            "WinUI LocalViewModelStoreOwner was not provided through host defaults."
        }
        check(initialViewModel.savedStateHandle.get<String>("value") == "initial") {
            "WinUI SavedStateHandle did not expose its initial value."
        }
        initialViewModel.savedStateHandle["value"] = "updated"
        currentComposeView.disposeComposition()

        var restoredViewModel: WinUISavedStateViewModel? = null
        currentComposeView.setContent {
            restoredViewModel = viewModel(key = "winui-saved-state-view-model") {
                WinUISavedStateViewModel(createSavedStateHandle())
            }
        }
        check(restoredViewModel === initialViewModel) {
            "WinUI ViewModelStore did not retain the ViewModel across disposeComposition."
        }
        check(restoredViewModel?.savedStateHandle?.get<String>("value") == "updated") {
            "WinUI SavedStateHandle did not preserve the updated value."
        }
        currentComposeView.dispose()
        check(initialViewModel.cleared) {
            "WinUI ViewModelStore was not cleared when the lifecycle reached DESTROYED."
        }
        println("compose-winui-sample: viewmodel owner saved state and cleared")
    }

    private fun runWinUINavigationEventOwnerSmoke() {
        val currentComposeView = WinUIComposeView()
        var dispatcherOwnerProvided = false
        var dispatcherEnabled = false
        currentComposeView.setContent {
            val dispatcherOwner = LocalNavigationEventDispatcherOwner.current
            dispatcherOwnerProvided = dispatcherOwner != null
            dispatcherEnabled = dispatcherOwner?.navigationEventDispatcher?.isEnabled == true
        }
        currentComposeView.dispose()
        check(dispatcherOwnerProvided) {
            "WinUI LocalNavigationEventDispatcherOwner was not provided through host defaults."
        }
        check(dispatcherEnabled) {
            "WinUI NavigationEventDispatcher was not enabled by default."
        }
        println("compose-winui-sample: navigation event dispatcher owner provided")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUISaveableStateSmoke() {
        val currentComposeView = WinUIComposeView()
        var savedState: MutableState<String>? = null
        var observedValue: String? = null
        currentComposeView.setSaveableStateSmokeContent { state ->
            savedState = state
            observedValue = state.value
        }
        check(savedState?.value == "initial") {
            "WinUI rememberSaveable did not create its initial value."
        }
        savedState?.value = "updated"
        currentComposeView.performFrameForTest()
        awaitCondition("WinUI rememberSaveable state update") {
            observedValue == "updated"
        }
        currentComposeView.disposeComposition()

        var restoredValue: String? = null
        currentComposeView.setSaveableStateSmokeContent { state ->
            restoredValue = state.value
        }
        currentComposeView.performFrameForTest()
        check(restoredValue == "updated") {
            "WinUI rememberSaveable did not restore across disposeComposition: $restoredValue."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: saveable state restored")
    }

    private fun runWinUIRetainedValuesSmoke() {
        val currentComposeView = WinUIComposeView()
        var factoryCalls = 0
        var firstValue: Any? = null
        currentComposeView.setRetainedValuesSmokeContent(
            createValue = { Any().also { factoryCalls++ } },
            onValue = { firstValue = it },
        )
        check(firstValue != null) {
            "WinUI retain did not create its initial value."
        }
        check(factoryCalls == 1) {
            "WinUI retain factory was called $factoryCalls times for initial content."
        }
        currentComposeView.disposeComposition()

        var restoredValue: Any? = null
        currentComposeView.setRetainedValuesSmokeContent(
            createValue = { Any().also { factoryCalls++ } },
            onValue = { restoredValue = it },
        )
        check(restoredValue === firstValue) {
            "WinUI retain did not restore across disposeComposition."
        }
        check(factoryCalls == 1) {
            "WinUI retain factory was called again while restoring."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: retained value restored")
    }

    private fun runWinUIViewZOrderSmoke() {
        val firstProbe = WinUIViewLifecycleProbe()
        val secondProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        currentComposeView.setContent {
            WinUIViewSampleContent(
                content = "first",
                lifecycleProbe = firstProbe,
            )
            WinUIViewSampleContent(
                content = "second",
                lifecycleProbe = secondProbe,
            )
        }
        val rootCanvas = checkNotNull(rootHost.content.asWinRTCanvas()) {
            "WinUIView z-order smoke did not install an interop Canvas into the root host."
        }
        check(rootCanvas.requiredInteropChildren.size == 2) {
            "WinUIView z-order smoke expected two native children, got ${rootCanvas.requiredInteropChildren.size}."
        }
        val firstButton = checkNotNull(firstProbe.lastButton) {
            "WinUIView z-order smoke first factory did not create a Button."
        }
        val secondButton = checkNotNull(secondProbe.lastButton) {
            "WinUIView z-order smoke second factory did not create a Button."
        }
        check(firstProbe.lastContent == "first" && secondProbe.lastContent == "second") {
            "WinUIView z-order smoke did not update both Buttons: " +
                "first=${firstProbe.lastContent} second=${secondProbe.lastContent}."
        }
        currentComposeView.dispose()
        check(firstProbe.releaseCount == 1 && secondProbe.releaseCount == 1) {
            "WinUIView z-order smoke did not release both children: " +
                "first=${firstProbe.releaseCount} second=${secondProbe.releaseCount}."
        }
    }

    private fun runWinUIViewUnclippedBoundsSmoke() {
        var lastNativeCanvas: Canvas? = null
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        currentComposeView.setContent {
            WinUIView(
                modifier = fixedSizeAndPositionModifier(
                    width = 80,
                    height = 30,
                    x = 0,
                    y = 0,
                ),
                properties = WinUIInteropProperties(clipToBounds = true),
                factory = {
                    Canvas().apply {
                        width = 200.0
                        height = 90.0
                    }
                },
                update = {
                    lastNativeCanvas = it
                },
            )
        }
        val rootCanvas = checkNotNull(rootHost.content.asWinRTCanvas()) {
            "WinUIView unclipped bounds smoke did not install an interop Canvas."
        }
        val wrapper = checkNotNull(rootCanvas.requiredInteropChildren.singleOrNull()) {
            "WinUIView unclipped bounds smoke did not install a wrapper."
        }
        val nativeCanvas = checkNotNull(lastNativeCanvas) {
            "WinUIView unclipped bounds smoke did not update the native Canvas."
        }
        check(nativeCanvas.width == 200.0 && nativeCanvas.height == 90.0) {
            "WinUIView child did not keep an unclipped native width: " +
                "${nativeCanvas.width}x${nativeCanvas.height}."
        }
        val wrapperClip = checkNotNull(wrapper.readClipRectOrNull()) {
            "WinUIView wrapper clip was not set."
        }
        check(wrapperClip.width == 80f && wrapperClip.height == 30f) {
            "WinUIView wrapper clip did not match clipped Compose bounds: " +
                "${wrapperClip.width}x${wrapperClip.height}."
        }
        currentComposeView.dispose()
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIViewContainerSyncSmoke() {
        val firstProbe = WinUIViewLifecycleProbe()
        val secondProbe = WinUIViewLifecycleProbe()
        val thirdProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        val includeSecond: MutableState<Boolean> = mutableStateOf(true)

        currentComposeView.setContent {
            WinUIViewSampleContent(
                content = "first",
                lifecycleProbe = firstProbe,
            )
            if (includeSecond.value) {
                WinUIViewSampleContent(
                    content = "second",
                    lifecycleProbe = secondProbe,
                )
            }
            WinUIViewSampleContent(
                content = "third",
                lifecycleProbe = thirdProbe,
            )
        }
        awaitCondition("WinUIView container initial order") {
            (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.size == 3
        }
        val rootCanvas = checkNotNull(rootHost.content.asWinRTCanvas()) {
            "WinUIView container sync smoke did not install an interop Canvas."
        }
        val firstWrapper = rootCanvas.requiredInteropChildren[0]
        val secondWrapper = rootCanvas.requiredInteropChildren[1]
        val thirdWrapper = rootCanvas.requiredInteropChildren[2]
        assertInteropRootOrder(
            rootCanvas = rootCanvas,
            expected = listOf(firstWrapper, secondWrapper, thirdWrapper),
            label = "initial order",
        )

        includeSecond.value = false
        currentComposeView.performFrameForTest()
        awaitCondition("WinUIView container middle removal") {
            secondProbe.releaseCount == 1 &&
                hasInteropRootOrder(rootCanvas, listOf(firstWrapper, thirdWrapper))
        }
        assertInteropRootOrder(
            rootCanvas = rootCanvas,
            expected = listOf(firstWrapper, thirdWrapper),
            label = "middle removal order",
        )
        check(secondProbe.releaseCount == 1) {
            "WinUIView container sync smoke did not release the removed child, got " +
                "${secondProbe.releaseCount}."
        }

        includeSecond.value = true
        currentComposeView.performFrameForTest()
        awaitCondition("WinUIView container middle insertion") {
            rootCanvas.requiredInteropChildren.size == 3 &&
                secondProbe.factoryCount == 2 &&
                secondProbe.releaseCount == 1
        }
        val reinsertedSecondWrapper = rootCanvas.requiredInteropChildren[1]
        assertInteropRootOrder(
            rootCanvas = rootCanvas,
            expected = listOf(firstWrapper, reinsertedSecondWrapper, thirdWrapper),
            label = "middle insertion order",
        )
        check(!reinsertedSecondWrapper.nativeObject.sameIdentity(secondWrapper.nativeObject)) {
            "WinUIView container sync smoke reused a released middle wrapper."
        }

        currentComposeView.dispose()
        check(
            firstProbe.releaseCount == 1 &&
                secondProbe.releaseCount == 2 &&
                thirdProbe.releaseCount == 1
        ) {
            "WinUIView container sync smoke did not release remaining children: " +
                "first=${firstProbe.releaseCount} second=${secondProbe.releaseCount} " +
                "third=${thirdProbe.releaseCount}."
        }
    }

    private suspend fun runWinUIViewGeneratedEventCleanupSmoke() {
        var token: EventRegistrationToken? = null
        var registered = false
        var removed = false
        var clickCount = 0
        val currentComposeView = WinUIComposeView()
        currentComposeView.setContent {
            WinUIView(
                factory = { Button() },
                update = { button ->
                    if (!registered) {
                        token = button.click.add { _, _ ->
                            clickCount += 1
                        }
                        registered = true
                    }
                },
                onRelease = { button ->
                    button.click.remove(checkNotNull(token) {
                        "WinUIView generated event cleanup smoke did not register a click token."
                    })
                    removed = true
                },
            )
        }
        awaitCondition("WinUIView generated event token registration") {
            registered && token != null
        }
        currentComposeView.dispose()
        check(removed) {
            "WinUIView generated event cleanup smoke did not remove the click token on release."
        }
        check(clickCount == 0) {
            "WinUIView generated event cleanup smoke unexpectedly handled $clickCount clicks."
        }
        println("compose-winui-sample: generated event cleanup")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUICompositionShutdownSmoke() {
        val disposeEvents = mutableListOf<String>()
        var token: EventRegistrationToken? = null
        var eventRegistered = false
        var eventRemoved = false
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()

        check(currentComposeView.root.allowDrop) {
            "WinUIComposeView did not enable root drag/drop registration."
        }
        check(currentComposeView.isDragAndDropTransferRequiredForTest) {
            "WinUIComposeView did not install its production drag source starter."
        }
        check(currentComposeView.isDragAndDropSourceBoundToRenderSurfaceForTest) {
            "WinUIComposeView did not bind its drag source to the Compose render surface."
        }
        currentComposeView.setContent {
            DisposableEffect(Unit) {
                disposeEvents += "entered"
                onDispose {
                    disposeEvents += "disposed"
                }
            }
            Layout(
                content = {
                    WinUIView(
                        factory = { Button() },
                        update = { button ->
                            if (!eventRegistered) {
                                token = button.click.add { _, _ -> }
                                eventRegistered = true
                            }
                        },
                        onRelease = { button ->
                            button.click.remove(checkNotNull(token) {
                                "WinUI shutdown smoke did not register a click token."
                            })
                            eventRemoved = true
                        },
                    )
                },
            ) { measurables, _ ->
                val placeable = measurables.single().measure(Constraints.fixed(32, 24))
                layout(32, 24) {
                    placeable.place(0, 0)
                }
            }
        }
        currentComposeView.setWindowContainerSizeForTest(IntSize(64, 48))
        awaitCondition("WinUI shutdown composition entered") {
            disposeEvents == listOf("entered") &&
                eventRegistered &&
                (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull() != null
        }
        check(currentComposeView.isLoadedRenderSchedulerRegistrationPendingForTest) {
            "WinUIComposeView did not keep a loaded render-scheduler registration before disposal."
        }

        currentComposeView.dispose()

        check(disposeEvents == listOf("entered", "disposed")) {
            "WinUI shutdown smoke did not dispose composition content: $disposeEvents."
        }
        check(eventRemoved) {
            "WinUI shutdown smoke did not remove the native event token on disposal."
        }
        check((rootHost.content.asWinRTCanvas())?.requiredInteropChildren.orEmpty().isEmpty()) {
            "WinUI shutdown smoke did not clear interop children on disposal."
        }
        check(!currentComposeView.root.allowDrop) {
            "WinUIComposeView did not clear root drag/drop runtime state on disposal."
        }
        check(!currentComposeView.isDragAndDropTransferRequiredForTest) {
            "WinUIComposeView did not clear its production drag source starter on disposal."
        }
        check(!currentComposeView.isLoadedRenderSchedulerRegistrationPendingForTest) {
            "WinUIComposeView did not clear the loaded render-scheduler registration on disposal."
        }
        check(!currentComposeView.isRenderSchedulerStartedForTest) {
            "WinUIComposeView left the render scheduler running after disposal."
        }
        check(
            runCatching {
                currentComposeView.setContent {}
            }.exceptionOrNull() is IllegalStateException
        ) {
            "Disposed WinUIComposeView accepted new content after shutdown."
        }
        println("compose-winui-sample: composition shutdown")
    }

    private fun runWinUIViewControlVarietySmoke() {
        var buttonFactoryCount = 0
        var textBoxFactoryCount = 0
        var toggleFactoryCount = 0
        var releaseCount = 0
        var lastButton: Button? = null
        var lastButtonContent: String? = null
        var lastTextBox: TextBox? = null
        var lastToggleSwitch: ToggleSwitch? = null
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        currentComposeView.setContent {
            WinUIView(
                modifier = fixedSizeAndPositionModifier(
                    width = 120,
                    height = 40,
                    x = 0,
                    y = 0,
                ),
                factory = {
                    buttonFactoryCount += 1
                    Button()
                },
                onRelease = {
                    releaseCount += 1
                },
                update = {
                    val content = "button"
                    it.content = content
                    lastButtonContent = it.content as? String
                    lastButton = it
                },
            )
            WinUIView(
                modifier = fixedSizeAndPositionModifier(
                    width = 180,
                    height = 40,
                    x = 0,
                    y = 48,
                ),
                factory = {
                    textBoxFactoryCount += 1
                    TextBox()
                },
                onRelease = {
                    releaseCount += 1
                },
                update = {
                    it.text = "text box"
                    lastTextBox = it
                },
            )
            WinUIView(
                modifier = fixedSizeAndPositionModifier(
                    width = 180,
                    height = 40,
                    x = 0,
                    y = 96,
                ),
                factory = {
                    toggleFactoryCount += 1
                    ToggleSwitch()
                },
                onRelease = {
                    releaseCount += 1
                },
                update = {
                    it.isOn = true
                    lastToggleSwitch = it
                },
            )
        }
        val rootCanvas = checkNotNull(rootHost.content.asWinRTCanvas()) {
            "WinUIView control variety smoke did not install an interop Canvas into the root host."
        }
        check(rootCanvas.requiredInteropChildren.size == 3) {
            "WinUIView control variety smoke expected three native children, got " +
                "${rootCanvas.requiredInteropChildren.size}."
        }
        check(buttonFactoryCount == 1 && textBoxFactoryCount == 1 && toggleFactoryCount == 1) {
            "WinUIView control variety smoke did not create each WinUI control exactly once: " +
                "button=$buttonFactoryCount textBox=$textBoxFactoryCount toggle=$toggleFactoryCount."
        }
        check(lastButton != null && lastButtonContent == "button") {
            "WinUIView control variety smoke did not update Button content."
        }
        check(lastTextBox?.text == "text box") {
            "WinUIView control variety smoke did not update TextBox text."
        }
        check(lastToggleSwitch?.isOn == true) {
            "WinUIView control variety smoke did not update ToggleSwitch isOn."
        }
        currentComposeView.dispose()
        check(releaseCount == 3) {
            "WinUIView control variety smoke did not release all controls, got $releaseCount."
        }
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIViewReuseSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        val active: MutableState<Boolean> = mutableStateOf(true)
        currentComposeView.setContent {
            ReusableContentHost(active.value) {
                WinUIViewSampleContent(
                    isUserInteractionEnabled = false,
                    lifecycleProbe = lifecycleProbe,
                )
            }
        }
        val rootCanvas = checkNotNull(rootHost.content.asWinRTCanvas()) {
            "WinUIView reuse smoke did not install an interop Canvas into the root host."
        }
        val wrapper = checkNotNull(rootCanvas.requiredInteropChildren.singleOrNull()) {
            "WinUIView reuse smoke did not install a wrapper into the root host."
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView reuse smoke did not install a Button into the wrapper."
        }
        check(lifecycleProbe.factoryCount == 1) {
            "Expected one reusable WinUIView factory call, got ${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.updateCount == 1) {
            "Expected one reusable WinUIView update call, got ${lifecycleProbe.updateCount}."
        }
        active.value = false
        currentComposeView.performFrameForTest()
        awaitCondition("reusable WinUIView deactivation") {
            lifecycleProbe.resetCount == 1 &&
                (rootHost.content.asWinRTCanvas())?.requiredInteropChildren.orEmpty().isEmpty()
        }
        check(lifecycleProbe.factoryCount == 1) {
            "Reusable WinUIView should not recreate while deactivating, got " +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.resetCount == 1) {
            "Expected one reusable WinUIView reset while deactivating, got " +
                "${lifecycleProbe.resetCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "Reusable WinUIView should not release while merely inactive, got " +
                "${lifecycleProbe.releaseCount}."
        }
        active.value = true
        currentComposeView.performFrameForTest()
        awaitCondition("reusable WinUIView reactivation") {
            lifecycleProbe.updateCount == 2
        }
        check(
            (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull()?.nativeObject
                ?.sameIdentity(wrapper.nativeObject) == true
        ) {
            "Reusable WinUIView did not keep its wrapper across deactivation."
        }
        val reattachedButton = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView reuse smoke did not reattach the Button after becoming active."
        }
        check(reattachedButton === button) {
            "Reusable WinUIView recreated the Button instead of reusing it."
        }
        check(lifecycleProbe.factoryCount == 1) {
            "Reusable WinUIView factory should still be one after reactivation, got " +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.updateCount == 2) {
            "Expected reusable WinUIView update after reactivation, got " +
                "${lifecycleProbe.updateCount}."
        }
        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 1) {
            "Expected reusable WinUIView release during host disposal, got " +
                "${lifecycleProbe.releaseCount}."
        }
        println(
            "compose-winui-sample: reuse after release factory=" +
                "${lifecycleProbe.factoryCount} update=${lifecycleProbe.updateCount} " +
                "reset=${lifecycleProbe.resetCount} release=${lifecycleProbe.releaseCount}"
        )
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIViewStateUpdateSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        val content: MutableState<String> = mutableStateOf("state update initial")
        currentComposeView.setContent {
            WinUIViewSampleContent(
                content = content.value,
                lifecycleProbe = lifecycleProbe,
            )
        }
        awaitCondition("WinUIView initial state update") {
            lifecycleProbe.updateCount == 1 &&
                lifecycleProbe.lastContent == "state update initial" &&
                (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.size == 1
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView state update smoke did not install a Button."
        }

        content.value = "state update changed"
        currentComposeView.performFrameForTest()
        awaitCondition("WinUIView repeated state update") {
            lifecycleProbe.updateCount == 2 &&
                lifecycleProbe.lastButton === button &&
                lifecycleProbe.lastContent == "state update changed"
        }
        check(lifecycleProbe.factoryCount == 1) {
            "WinUIView state update smoke recreated the Button, factory=" +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "WinUIView state update smoke released the Button during recomposition, release=" +
                "${lifecycleProbe.releaseCount}."
        }

        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 1) {
            "WinUIView state update smoke did not release on host disposal, release=" +
                "${lifecycleProbe.releaseCount}."
        }
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIViewRelayoutSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        val width: MutableState<Int> = mutableStateOf(80)
        val height: MutableState<Int> = mutableStateOf(30)
        val x: MutableState<Int> = mutableStateOf(4)
        val y: MutableState<Int> = mutableStateOf(6)
        currentComposeView.setContent {
            WinUIViewSampleContent(
                modifier = fixedSizeAndPositionModifier(
                    width = width.value,
                    height = height.value,
                    x = x.value,
                    y = y.value,
                ),
                clipToBounds = true,
                lifecycleProbe = lifecycleProbe,
            )
        }
        awaitCondition("WinUIView initial relayout bounds") {
            val rootCanvas = rootHost.content.asWinRTCanvas()
            val wrapper = rootCanvas?.requiredInteropChildren?.singleOrNull().asWinRTCanvas()
            val clip = wrapper?.readClipRectOrNull()
            clip?.width == 80f &&
                clip.height == 30f &&
                wrapper.width == 80.0 &&
                wrapper.height == 30.0 &&
                Canvas.getLeft(wrapper) == 4.0 &&
                Canvas.getTop(wrapper) == 6.0 &&
                lifecycleProbe.lastButton?.width == 80.0 &&
                lifecycleProbe.lastButton?.height == 30.0
        }
        val rootCanvas = checkNotNull(rootHost.content.asWinRTCanvas()) {
            "WinUIView relayout smoke did not install an interop Canvas."
        }
        val wrapper = checkNotNull(rootCanvas.requiredInteropChildren.singleOrNull()) {
            "WinUIView relayout smoke did not install a wrapper."
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView relayout smoke did not install a Button."
        }

        width.value = 140
        height.value = 55
        x.value = 11
        y.value = 17
        currentComposeView.performFrameForTest()
        awaitCondition("WinUIView updated relayout bounds") {
            val currentWrapper = (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull().asWinRTCanvas()
            val clip = currentWrapper?.readClipRectOrNull()
            currentWrapper?.nativeObject?.sameIdentity(wrapper.nativeObject) == true &&
                lifecycleProbe.lastButton === button &&
                clip?.width == 140f &&
                clip.height == 55f &&
                currentWrapper.width == 140.0 &&
                currentWrapper.height == 55.0 &&
                Canvas.getLeft(currentWrapper) == 11.0 &&
                Canvas.getTop(currentWrapper) == 17.0 &&
                button.width == 140.0 &&
                button.height == 55.0
        }
        check(lifecycleProbe.factoryCount == 1) {
            "WinUIView relayout smoke recreated the Button, factory=" +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "WinUIView relayout smoke released the Button during relayout, release=" +
                "${lifecycleProbe.releaseCount}."
        }

        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 1) {
            "WinUIView relayout smoke did not release on host disposal, release=" +
                "${lifecycleProbe.releaseCount}."
        }
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUILayoutSnapshotInvalidationSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        val width: MutableState<Int> = mutableStateOf(70)
        val height: MutableState<Int> = mutableStateOf(25)
        currentComposeView.setContent {
            WinUIViewSampleContent(
                modifier = snapshotDrivenSizeModifier(width, height),
                clipToBounds = true,
                lifecycleProbe = lifecycleProbe,
            )
        }
        awaitCondition("WinUI layout snapshot initial bounds") {
            val wrapper = (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull()
            val clip = wrapper?.readClipRectOrNull()
            clip?.width == 70f &&
                clip.height == 25f &&
                lifecycleProbe.lastButton?.width == 70.0 &&
                lifecycleProbe.lastButton?.height == 25.0
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUI layout snapshot smoke did not install a Button."
        }
        check(lifecycleProbe.updateCount == 1) {
            "WinUI layout snapshot smoke unexpectedly recomposed before state change."
        }

        width.value = 115
        height.value = 45
        currentComposeView.performFrameForTest()
        awaitCondition("WinUI layout snapshot updated bounds") {
            val wrapper = (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull()
            val clip = wrapper?.readClipRectOrNull()
            clip?.width == 115f &&
                clip.height == 45f &&
                lifecycleProbe.lastButton === button &&
                button.width == 115.0 &&
                button.height == 45.0
        }
        check(lifecycleProbe.updateCount == 1) {
            "WinUI layout snapshot invalidation should relayout without recomposition, " +
                "update=${lifecycleProbe.updateCount}."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: layout snapshot invalidation")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUILayoutCompletedListenerSmoke() {
        val currentComposeView = WinUIComposeView()
        val includeOnPlaced: MutableState<Boolean> = mutableStateOf(false)
        var onPlacedCount = 0
        currentComposeView.setContent {
            Layout(
                modifier = if (includeOnPlaced.value) {
                    legacyOnPlacedModifier { onPlacedCount += 1 }
                } else {
                    Modifier
                },
                content = {},
            ) { _, _ ->
                layout(1, 1) {}
            }
        }
        withFrameNanos { }
        check(onPlacedCount == 0) {
            "WinUI layout completed listener smoke fired before onPlaced was installed."
        }
        includeOnPlaced.value = true
        currentComposeView.performFrameForTest()
        awaitCondition("WinUI layout completed listener") {
            onPlacedCount >= 1
        }
        currentComposeView.dispose()
        println("compose-winui-sample: layout completed listener")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUILayoutRectChangedSmoke() {
        val currentComposeView = WinUIComposeView()
        val showTarget: MutableState<Boolean> = mutableStateOf(true)
        var targetBounds: RelativeLayoutBounds? = null
        currentComposeView.setContent {
            Layout(
                content = {
                    if (showTarget.value) {
                        Layout(
                            modifier = Modifier.onLayoutRectChanged(
                                throttleMillis = 0,
                                debounceMillis = 0,
                            ) {
                                targetBounds = it
                            },
                            content = {},
                        ) { _, _ ->
                            layout(40, 40) {}
                        }
                    }
                    Layout(content = {}) { _, _ ->
                        layout(40, 40) {}
                    }
                },
            ) { measurables, _ ->
                val placeables = measurables.map {
                    it.measure(Constraints.fixed(40, 40))
                }
                layout(40, 40) {
                    placeables.forEach { it.place(0, 0) }
                }
            }
        }
        awaitCondition("WinUI layout rect changed occlusion") {
            targetBounds?.calculateOcclusions()?.isNotEmpty() == true
        }
        val removedBounds = checkNotNull(targetBounds) {
            "WinUI layout rect changed smoke did not receive bounds."
        }
        showTarget.value = false
        currentComposeView.performFrameForTest()
        awaitCondition("WinUI layout rect changed detach cleanup") {
            runCatching {
                removedBounds.calculateOcclusions().isEmpty()
            }.getOrDefault(false)
        }
        currentComposeView.dispose()
        println("compose-winui-sample: layout rect changed")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIOwnerLayerTransformSmoke() {
        val currentComposeView = WinUIComposeView()
        val translationX: MutableState<Float> = mutableStateOf(0f)
        var bounds: RelativeLayoutBounds? = null
        currentComposeView.setContent {
            Layout(
                modifier = Modifier
                    .graphicsLayer {
                        this.translationX = translationX.value
                        clip = true
                    }
                    .onLayoutRectChanged(
                        throttleMillis = 0,
                        debounceMillis = 0,
                    ) {
                        bounds = it
                    },
                content = {},
            ) { _, _ ->
                layout(20, 10) {}
            }
        }
        awaitCondition("WinUI owner layer initial transform bounds") {
            val currentBounds = bounds
            currentBounds?.positionInRoot?.x == 0 &&
                currentBounds.positionInRoot.y == 0 &&
                currentBounds.width == 20 &&
                currentBounds.height == 10
        }
        translationX.value = 12f
        currentComposeView.performFrameForTest()
        awaitCondition("WinUI owner layer updated transform bounds") {
            val currentBounds = bounds
            currentBounds?.positionInRoot?.x == 12 &&
                currentBounds.positionInRoot.y == 0 &&
                currentBounds.width == 20 &&
                currentBounds.height == 10
        }
        currentComposeView.dispose()
        println("compose-winui-sample: owner layer transform")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIRootKeyEventSmoke() {
        val currentComposeView = WinUIComposeView()
        val focusRequester = FocusRequester()
        var focused = false
        val receivedEvents = mutableListOf<String>()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        focused = it.isFocused
                    }
                    .focusTarget()
                    .onKeyEvent {
                        receivedEvents += "key:${it.key.keyCode}"
                        true
                    }
                    .onPreviewKeyEvent {
                        receivedEvents += "preview:${it.key.keyCode}"
                        false
                    },
                content = {},
            ) { _, _ ->
                layout(1, 1) {}
            }
        }
        withFrameNanos { }
        check(focusRequester.requestFocus()) {
            "WinUI root key event smoke could not focus the target."
        }
        awaitCondition("WinUI root key event target focused") {
            focused
        }
        val consumed = currentComposeView.rootForTest().sendKeyEvent(
            KeyEvent(
                key = Key.A,
                type = KeyEventType.KeyDown,
                codePoint = 'a'.code,
            )
        )
        check(consumed) {
            "WinUI RootForTest did not report the key event as consumed."
        }
        check(receivedEvents == listOf("preview:${Key.A.keyCode}", "key:${Key.A.keyCode}")) {
            "WinUI RootForTest dispatched key events out of order: $receivedEvents."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: root key event")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIRootFocusTraversalKeySmoke() {
        val currentComposeView = WinUIComposeView()
        val firstRequester = FocusRequester()
        val secondRequester = FocusRequester()
        var firstFocused = false
        var secondFocused = false
        currentComposeView.setContent {
            Layout(
                content = {
                    Layout(
                        modifier = Modifier
                            .focusRequester(firstRequester)
                            .onFocusChanged {
                                firstFocused = it.isFocused
                            }
                            .focusTarget(),
                        content = {},
                    ) { _, _ ->
                        layout(1, 1) {}
                    }
                    Layout(
                        modifier = Modifier
                            .focusRequester(secondRequester)
                            .onFocusChanged {
                                secondFocused = it.isFocused
                            }
                            .focusTarget(),
                        content = {},
                    ) { _, _ ->
                        layout(1, 1) {}
                    }
                },
            ) { measurables, _ ->
                val first = measurables[0].measure(Constraints.fixed(1, 1))
                val second = measurables[1].measure(Constraints.fixed(1, 1))
                layout(2, 1) {
                    first.place(0, 0)
                    second.place(1, 0)
                }
            }
        }
        withFrameNanos { }
        check(firstRequester.requestFocus()) {
            "WinUI root focus traversal smoke could not focus the first target."
        }
        awaitCondition("WinUI root focus traversal first target focused") {
            firstFocused && !secondFocused
        }
        check(
            currentComposeView.rootForTest().sendKeyEvent(
                KeyEvent(key = Key.Tab, type = KeyEventType.KeyDown)
            )
        ) {
            "WinUI RootForTest did not consume Tab focus traversal."
        }
        awaitCondition("WinUI root focus traversal second target focused") {
            !firstFocused && secondFocused
        }
        check(
            currentComposeView.rootForTest().sendKeyEvent(
                KeyEvent(key = Key.Tab, type = KeyEventType.KeyDown, isShiftPressed = true)
            )
        ) {
            "WinUI RootForTest did not consume Shift+Tab focus traversal."
        }
        awaitCondition("WinUI root focus traversal returned to first target") {
            firstFocused && !secondFocused
        }
        currentComposeView.dispose()
        println("compose-winui-sample: root focus traversal key event")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIViewFocusInputIntegrationSmoke() {
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        val firstRequester = FocusRequester()
        val secondRequester = FocusRequester()
        val pointerProbe = WinUIPointerInputSmokeProbe()
        var firstFocused = false
        var secondFocused = false
        var button: Button? = null
        val secondKeyEvents = mutableListOf<Long>()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIPointerInputSmoke(pointerProbe),
                content = {
                    Layout(
                        modifier = Modifier
                            .focusRequester(firstRequester)
                            .onFocusChanged {
                                firstFocused = it.isFocused
                            }
                            .focusTarget(),
                        content = {},
                    ) { _, _ ->
                        layout(10, 20) {}
                    }
                    WinUIView(
                        modifier = fixedSizeAndPositionModifier(
                            width = 20,
                            height = 20,
                            x = 10,
                            y = 0,
                        ),
                        factory = { Button() },
                        update = {
                            button = it
                        },
                    )
                    Layout(
                        modifier = Modifier
                            .focusRequester(secondRequester)
                            .onFocusChanged {
                                secondFocused = it.isFocused
                            }
                            .focusTarget()
                            .onKeyEvent {
                                secondKeyEvents += it.key.keyCode
                                true
                            },
                        content = {},
                    ) { _, _ ->
                        layout(10, 20) {}
                    }
                },
            ) { measurables, _ ->
                val first = measurables[0].measure(Constraints.fixed(10, 20))
                val native = measurables[1].measure(Constraints.fixed(20, 20))
                val second = measurables[2].measure(Constraints.fixed(10, 20))
                layout(40, 20) {
                    first.place(0, 0)
                    native.place(10, 0)
                    second.place(30, 0)
                }
            }
        }
        awaitCondition("WinUIView focus input integration bounds installed") {
            (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull() != null && button != null
        }
        check(firstRequester.requestFocus()) {
            "WinUIView focus input integration smoke could not focus first Compose target."
        }
        awaitCondition("WinUIView focus input integration first focused") {
            firstFocused && !secondFocused
        }
        check(secondRequester.requestFocus()) {
            "WinUIView focus input integration smoke could not focus second Compose target."
        }
        awaitCondition("WinUIView focus input integration second focused") {
            !firstFocused && secondFocused &&
                button?.focusState == microsoft.ui.xaml.FocusState.Unfocused
        }
        check(
            currentComposeView.rootForTest().sendKeyEvent(
                KeyEvent(key = Key.B, type = KeyEventType.KeyDown)
            )
        ) {
            "WinUIView focus input integration smoke did not dispatch keyboard input."
        }
        check(secondKeyEvents == listOf(Key.B.keyCode)) {
            "WinUIView focus input integration smoke dispatched unexpected keys: $secondKeyEvents."
        }
        check(firstRequester.requestFocus()) {
            "WinUIView focus input integration smoke could not return focus to Compose."
        }
        awaitCondition("WinUIView focus input integration returned to compose") {
            firstFocused && !secondFocused &&
                button?.focusState == microsoft.ui.xaml.FocusState.Unfocused
        }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(5f, 5f),
                uptimeMillis = 1L,
                pointerId = 1L,
                down = true,
            )
        ) {
            "WinUIView focus input integration smoke did not dispatch outside pointer input."
        }
        awaitCondition("WinUIView focus input integration outside pointer") {
            pointerProbe.pressCount == 1
        }
        check(
            !currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(15f, 5f),
                uptimeMillis = 2L,
                pointerId = 2L,
                down = true,
            )
        ) {
            "WinUIView focus input integration smoke dispatched native-bounds pointer input."
        }
        check(pointerProbe.pressCount == 1) {
            "WinUIView focus input integration smoke delivered native-bounds pointer input."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: focus input integration")
    }

    private fun runWinUIRootSemanticsSmoke() {
        val currentComposeView = WinUIComposeView()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.semantics {
                    testTag = "winui-semantics"
                    contentDescription = "WinUI semantics node"
                },
                content = {},
            ) { _, _ ->
                layout(1, 1) {}
            }
        }
        currentComposeView.rootForTest().measureAndLayoutForTest()
        val semanticsNode = currentComposeView.rootForTest()
            .semanticsOwner
            .getAllSemanticsNodes(mergingEnabled = false)
            .singleOrNull {
                it.config.getOrNull(SemanticsProperties.TestTag) == "winui-semantics"
            } ?: error("WinUI RootForTest did not expose the test semantics node.")
        check(
            semanticsNode.config.getOrNull(SemanticsProperties.ContentDescription) ==
                listOf("WinUI semantics node")
        ) {
            "WinUI RootForTest exposed incorrect semantics content: ${semanticsNode.config}."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: root semantics")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIAccessibilityProviderSmoke() {
        val currentComposeView = WinUIComposeView()
        var accessibilityName by mutableStateOf("WinUI accessible node")
        var clicked = false
        var focusRequested = false
        var expanded = false
        var collapsed = false
        var textSet: AnnotatedString? = null
        val progressValues = mutableListOf<Float>()
        currentComposeView.rootForTest().setAccessibilityEventBatchIntervalMillis(0L)
        currentComposeView.setWindowContainerSizeForTest(IntSize(96, 64))
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.semantics {
                    testTag = "winui-accessibility"
                    contentDescription = accessibilityName
                    liveRegion = LiveRegionMode.Polite
                    focused = true
                    editableText = AnnotatedString("initial")
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = 0.5f,
                        range = 0f..1f,
                        steps = 4,
                    )
                    requestFocus {
                        focusRequested = true
                        true
                    }
                    onClick {
                        clicked = true
                        true
                    }
                    expand {
                        expanded = true
                        true
                    }
                    collapse {
                        collapsed = true
                        true
                    }
                    setText {
                        textSet = it
                        true
                    }
                    setProgress {
                        progressValues += it
                        true
                    }
                },
                content = {},
            ) { _, _ ->
                layout(48, 32) {}
            }
        }
        currentComposeView.rootForTest().measureAndLayoutForTest()
        awaitCondition("WinUI accessibility provider node") {
            currentComposeView.accessibilitySnapshotForTest
                ?.root
                ?.findAccessibilityNode("winui-accessibility") != null
        }
        val accessibilitySnapshot = checkNotNull(currentComposeView.accessibilitySnapshotForTest) {
            "WinUI accessibility provider did not expose a semantics snapshot."
        }
        val accessibilityNode = checkNotNull(
            accessibilitySnapshot.root.findAccessibilityNode("winui-accessibility")
        ) {
            "WinUI accessibility provider did not expose the accessibility smoke node."
        }
        check(accessibilityNode.info.name == "WinUI accessible node") {
            "WinUI accessibility provider exposed incorrect name: ${accessibilityNode.info.name}."
        }
        check(accessibilityNode.info.liveSetting == WinUIAccessibilityLiveSetting.POLITE) {
            "WinUI accessibility provider exposed incorrect live-region metadata: " +
                accessibilityNode.info.liveSetting
        }
        check(accessibilityNode.bounds.width == 48f && accessibilityNode.bounds.height == 32f) {
            "WinUI accessibility provider exposed incorrect bounds: ${accessibilityNode.bounds}."
        }
        check(accessibilityNode.state.focusable && accessibilityNode.state.focused) {
            "WinUI accessibility provider exposed incorrect focus state: ${accessibilityNode.state}."
        }
        check(accessibilityNode.state.editable) {
            "WinUI accessibility provider did not expose editable text state: ${accessibilityNode.state}."
        }
        val expectedActions = setOf(
            WinUIAccessibilityAction.FOCUS,
            WinUIAccessibilityAction.CLICK,
            WinUIAccessibilityAction.EXPAND,
            WinUIAccessibilityAction.COLLAPSE,
            WinUIAccessibilityAction.SET_TEXT,
            WinUIAccessibilityAction.INCREMENT,
            WinUIAccessibilityAction.DECREMENT,
        )
        check(accessibilityNode.actions.containsAll(expectedActions)) {
            "WinUI accessibility provider did not expose expected actions $expectedActions: " +
                accessibilityNode.actions
        }
        fun perform(action: WinUIAccessibilityAction, text: String = ""): Boolean =
            currentComposeView.performAccessibilityActionForTest(
                WinUIAccessibilityActionRequest(
                    nodeId = accessibilityNode.id,
                    action = action,
                    text = text,
                ),
            )

        check(perform(WinUIAccessibilityAction.FOCUS) && focusRequested) {
            "WinUI accessibility provider focus action did not dispatch."
        }
        check(perform(WinUIAccessibilityAction.CLICK) && clicked) {
            "WinUI accessibility provider click action did not dispatch."
        }
        check(perform(WinUIAccessibilityAction.EXPAND) && expanded) {
            "WinUI accessibility provider expand action did not dispatch."
        }
        check(perform(WinUIAccessibilityAction.COLLAPSE) && collapsed) {
            "WinUI accessibility provider collapse action did not dispatch."
        }
        check(perform(WinUIAccessibilityAction.SET_TEXT, "WinUI text") &&
            textSet == AnnotatedString("WinUI text")
        ) {
            "WinUI accessibility provider set-text action did not dispatch: $textSet."
        }
        check(perform(WinUIAccessibilityAction.INCREMENT)) {
            "WinUI accessibility provider increment action did not dispatch."
        }
        check(perform(WinUIAccessibilityAction.DECREMENT)) {
            "WinUI accessibility provider decrement action did not dispatch."
        }
        check(progressValues == listOf(0.7f, 0.3f)) {
            "WinUI accessibility provider progress actions produced $progressValues."
        }
        awaitCondition("WinUI accessibility initial production update") {
            currentComposeView.accessibilityUpdateCountForTest > 0
        }
        val initialAccessibilityUpdateCount = currentComposeView.accessibilityUpdateCountForTest
        accessibilityName = "WinUI accessible node updated"
        awaitCondition("WinUI accessibility changed production update") {
            currentComposeView.accessibilityUpdateCountForTest > initialAccessibilityUpdateCount
        }
        val updatedNode = checkNotNull(
            currentComposeView.accessibilitySnapshotForTest
                ?.root
                ?.findAccessibilityNode("winui-accessibility")
        ) {
            "WinUI accessibility provider lost the updated semantics node."
        }
        check(updatedNode.info.name == "WinUI accessible node updated") {
            "WinUI accessibility production update exposed ${updatedNode.info.name}."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: accessibility provider")
    }

    private suspend fun runWinUIOwnerEndApplyChangesSmoke() {
        val currentComposeView = WinUIComposeView()
        val probe = WinUIOwnerEndApplyChangesProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIOwnerEndApplyChangesSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(1, 1) {}
            }
        }
        awaitCondition("WinUI owner end-apply listener drain") {
            probe.events == listOf("repeat", "first", "second")
        }
        currentComposeView.dispose()
        println("compose-winui-sample: owner end apply changes")
    }

    private suspend fun runWinUIRootUncaughtExceptionHandlerSmoke() {
        val currentComposeView = WinUIComposeView()
        val shouldThrow = mutableStateOf(false)
        val expected = IllegalStateException("WinUI layout exception handler smoke")
        var captured: Throwable? = null
        currentComposeView.setContent {
            Layout(content = {}) { _, _ ->
                if (shouldThrow.value) throw expected
                layout(1, 1) {}
            }
        }
        currentComposeView.rootForTest().setUncaughtExceptionHandler(
            object : RootForTest.UncaughtExceptionHandler {
                override fun onUncaughtException(t: Throwable) {
                    captured = t
                }
            }
        )
        shouldThrow.value = true
        currentComposeView.rootForTest().measureAndLayoutForTest()
        awaitCondition("WinUI root uncaught exception handler") {
            captured === expected
        }
        currentComposeView.rootForTest().setUncaughtExceptionHandler(null)
        currentComposeView.dispose()
        println("compose-winui-sample: root uncaught exception handler")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIRootIndirectPointerSmoke() {
        val currentComposeView = WinUIComposeView()
        val focusRequester = FocusRequester()
        val probe = WinUIIndirectPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        probe.focused = it.isFocused
                    }
                    .winUIIndirectPointerInputSmoke(probe)
                    .focusTarget(),
                content = {},
            ) { _, _ ->
                layout(1, 1) {}
            }
        }
        withFrameNanos { }
        check(focusRequester.requestFocus()) {
            "WinUI root indirect pointer smoke could not focus the target."
        }
        awaitCondition("WinUI root indirect pointer target focused") {
            probe.focused
        }
        val event = IndirectPointerEvent(
            changes = listOf(
                IndirectPointerInputChange(
                    id = PointerId(0),
                    uptimeMillis = 2L,
                    position = Offset(4f, 6f),
                    pressed = true,
                    pressure = 1f,
                    previousUptimeMillis = 1L,
                    previousPosition = Offset(3f, 5f),
                    previousPressed = true,
                )
            ),
            type = IndirectPointerEventType.Move,
            primaryDirectionalMotionAxis = IndirectPointerEventPrimaryDirectionalMotionAxis.X,
        )
        check(currentComposeView.rootForTest().sendIndirectPointerEvent(event)) {
            "WinUI RootForTest did not report the indirect pointer event as consumed."
        }
        check(probe.events == listOf("Initial:Move", "Main:Move", "Final:Move")) {
            "WinUI RootForTest dispatched indirect pointer events out of order: " +
                "${probe.events}."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: root indirect pointer event")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIPointerInputSmoke() {
        val currentComposeView = WinUIComposeView()
        val probe = WinUIPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIPointerInputSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(20, 20) {}
            }
        }
        withFrameNanos { }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(5f, 5f),
                uptimeMillis = 1L,
                down = true,
            )
        ) {
            "WinUI pointer input smoke did not dispatch the press event."
        }
        awaitCondition("WinUI pointer input press received") {
            probe.pressCount == 1 && probe.releaseCount == 0
        }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Release,
                position = Offset(5f, 5f),
                uptimeMillis = 2L,
                down = false,
            )
        ) {
            "WinUI pointer input smoke did not dispatch the release event."
        }
        awaitCondition("WinUI pointer input release received") {
            probe.pressCount == 1 && probe.releaseCount == 1
        }
        currentComposeView.dispose()
        println("compose-winui-sample: pointer input")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIPointerMoveSmoke() {
        val currentComposeView = WinUIComposeView()
        val probe = WinUIPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIPointerInputSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(30, 30) {}
            }
        }
        withFrameNanos { }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(4f, 4f),
                uptimeMillis = 1L,
                down = true,
            )
        ) {
            "WinUI pointer input move smoke did not dispatch the press event."
        }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Move,
                position = Offset(12f, 9f),
                uptimeMillis = 2L,
                down = true,
            )
        ) {
            "WinUI pointer input move smoke did not dispatch the move event."
        }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Release,
                position = Offset(12f, 9f),
                uptimeMillis = 3L,
                down = false,
            )
        ) {
            "WinUI pointer input move smoke did not dispatch the release event."
        }
        awaitCondition("WinUI pointer input move sequence received") {
            probe.pressCount == 1 &&
                probe.moveCount >= 1 &&
                probe.releaseCount == 1 &&
                probe.lastPosition == Offset(12f, 9f)
        }
        currentComposeView.dispose()
        println("compose-winui-sample: pointer input move")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIPointerEnterExitSmoke() {
        val currentComposeView = WinUIComposeView()
        val probe = WinUIPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIPointerInputSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(30, 30) {}
            }
        }
        withFrameNanos { }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Enter,
                position = Offset(6f, 8f),
                uptimeMillis = 1L,
                down = false,
                type = PointerType.Mouse,
            )
        ) {
            "WinUI pointer input enter smoke did not dispatch the enter event."
        }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Exit,
                position = Offset(6f, 8f),
                uptimeMillis = 2L,
                down = false,
                type = PointerType.Mouse,
            )
        ) {
            "WinUI pointer input exit smoke did not dispatch the exit event."
        }
        awaitCondition("WinUI pointer input enter/exit received") {
            probe.enterCount == 1 &&
                probe.exitCount == 1 &&
                probe.lastPosition == Offset(6f, 8f)
        }
        currentComposeView.dispose()
        println("compose-winui-sample: pointer input enter exit")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIPointerScrollSmoke() {
        val currentComposeView = WinUIComposeView()
        val probe = WinUIPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIPointerInputSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(30, 30) {}
            }
        }
        withFrameNanos { }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Scroll,
                position = Offset(12f, 9f),
                uptimeMillis = 1L,
                down = false,
                type = PointerType.Mouse,
                scrollDelta = Offset(0f, -1f),
            )
        ) {
            "WinUI pointer input scroll smoke did not dispatch the scroll event."
        }
        awaitCondition("WinUI pointer input scroll received") {
            probe.scrollCount == 1 &&
                probe.lastPosition == Offset(12f, 9f) &&
                probe.lastScrollDelta == Offset(0f, -1f)
        }
        currentComposeView.dispose()
        println("compose-winui-sample: pointer input scroll")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIViewPointerInteropSmoke() {
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        val probe = WinUIPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIPointerInputSmoke(probe),
                content = {
                    WinUIView(
                        modifier = fixedSizeAndPositionModifier(
                            width = 20,
                            height = 20,
                            x = 10,
                            y = 10,
                        ),
                        factory = { Button() },
                    )
                },
            ) { measurables, _ ->
                val placeables = measurables.map { measurable ->
                    measurable.measure(Constraints.fixed(30, 30))
                }
                layout(80, 40) {
                    placeables.forEach { placeable ->
                        placeable.place(0, 0)
                    }
                }
            }
        }
        awaitCondition("WinUIView pointer interop bounds installed") {
            (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull() != null
        }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(5f, 5f),
                uptimeMillis = 1L,
                pointerId = 1L,
                down = true,
            )
        ) {
            "WinUIView pointer interop smoke did not dispatch the outside press event."
        }
        awaitCondition("WinUIView pointer interop outside event received") {
            probe.pressCount == 1
        }
        check(
            !currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(15f, 15f),
                uptimeMillis = 2L,
                pointerId = 2L,
                down = true,
            )
        ) {
            "WinUIView pointer interop smoke dispatched an event inside native bounds."
        }
        check(probe.pressCount == 1) {
            "WinUIView pointer interop smoke delivered native-bounds input to Compose."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: pointer input interop")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIPointerCancelOnDisposeSmoke() {
        val currentComposeView = WinUIComposeView()
        val probe = WinUIPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIPointerInputSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(20, 20) {}
            }
        }
        withFrameNanos { }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(5f, 5f),
                uptimeMillis = 1L,
                down = true,
            )
        ) {
            "WinUI pointer input cancel smoke did not dispatch the press event."
        }
        awaitCondition("WinUI pointer input cancel smoke press received") {
            probe.pressCount == 1
        }
        currentComposeView.dispose()
        check(probe.cancelCount >= 1) {
            "WinUI pointer input did not receive cancellation on owner disposal."
        }
        println("compose-winui-sample: pointer input cancel on dispose")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIViewPlacementSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        val isPlaced: MutableState<Boolean> = mutableStateOf(true)
        currentComposeView.setContent {
            ConditionalPlacement(isPlaced.value) {
                WinUIViewSampleContent(
                    lifecycleProbe = lifecycleProbe,
                )
            }
        }
        awaitCondition("WinUIView initial placement") {
            lifecycleProbe.lastButton != null &&
                (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.size == 1
        }
        val rootCanvas = checkNotNull(rootHost.content.asWinRTCanvas()) {
            "WinUIView placement smoke did not install an interop Canvas."
        }
        val wrapper = checkNotNull(rootCanvas.requiredInteropChildren.singleOrNull()) {
            "WinUIView placement smoke did not install a wrapper."
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView placement smoke did not install a Button."
        }

        isPlaced.value = false
        currentComposeView.performFrameForTest()
        awaitCondition("WinUIView unplacement") {
            (rootHost.content.asWinRTCanvas())?.requiredInteropChildren.orEmpty().isEmpty()
        }
        check(lifecycleProbe.factoryCount == 1) {
            "WinUIView placement smoke recreated the Button during unplacement, factory=" +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "WinUIView placement smoke released the Button during unplacement, release=" +
                "${lifecycleProbe.releaseCount}."
        }

        isPlaced.value = true
        currentComposeView.performFrameForTest()
        awaitCondition("WinUIView replacement") {
            (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull()?.nativeObject
                ?.sameIdentity(wrapper.nativeObject) == true &&
                lifecycleProbe.lastButton === button
        }
        check(lifecycleProbe.factoryCount == 1) {
            "WinUIView placement smoke recreated the Button during replacement, factory=" +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "WinUIView placement smoke released the Button before disposal, release=" +
                "${lifecycleProbe.releaseCount}."
        }

        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 1) {
            "WinUIView placement smoke did not release on host disposal, release=" +
                "${lifecycleProbe.releaseCount}."
        }
        println(
            "compose-winui-sample: placement toggle factory=" +
                "${lifecycleProbe.factoryCount} update=${lifecycleProbe.updateCount} " +
                "release=${lifecycleProbe.releaseCount}"
        )
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIViewDensitySmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        val localDensity: MutableState<Float> = mutableStateOf(1f)
        var observedDensity = 0f
        var observedInteropSize = IntSize.Zero
        currentComposeView.setContent {
            CompositionLocalProvider(LocalDensity provides Density(localDensity.value)) {
                WinUIView(
                    modifier = Modifier
                        .layout { measurable, _ ->
                            observedDensity = this.density
                            val placeable = measurable.measure(
                                Constraints.fixed(
                                    width = maxOf(1, (40f * this.density).toInt()),
                                    height = maxOf(1, (20f * this.density).toInt()),
                                )
                            )
                            layout(placeable.width, placeable.height) {
                                placeable.place(0, 0)
                            }
                        }
                        .onPlaced { observedInteropSize = it.size },
                    properties = WinUIInteropProperties(clipToBounds = true),
                    factory = {
                        lifecycleProbe.factoryCount += 1
                        Button().apply {
                            width = 1.0
                            height = 1.0
                        }
                    },
                    update = { button ->
                        lifecycleProbe.updateCount += 1
                        lifecycleProbe.lastButton = button
                    },
                    onReset = { lifecycleProbe.resetCount += 1 },
                    onRelease = { button ->
                        lifecycleProbe.releaseCount += 1
                        lifecycleProbe.lastButton = button
                    },
                )
            }
        }
        awaitCondition("WinUIView initial density") {
            val wrapper = (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull()
            val clip = wrapper?.readClipRectOrNull()
            observedDensity == 1f &&
                observedInteropSize == IntSize(40, 20) &&
                lifecycleProbe.lastButton != null &&
                clip?.width == 40f &&
                clip?.height == 20f &&
                lifecycleProbe.lastButton?.width == 40.0 &&
                lifecycleProbe.lastButton?.height == 20.0
        }
        val wrapper = checkNotNull((rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull()) {
            "WinUIView density smoke did not install a wrapper."
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView density smoke did not install a Button."
        }

        localDensity.value = 2f
        currentComposeView.performFrameForTest()
        awaitCondition("WinUIView updated density") {
            val currentWrapper = (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull()
            val clip = currentWrapper?.readClipRectOrNull()
            currentWrapper?.nativeObject?.sameIdentity(wrapper.nativeObject) == true &&
                observedDensity == 2f &&
                observedInteropSize == IntSize(80, 40) &&
                lifecycleProbe.lastButton === button &&
                clip?.width == 40f &&
                clip?.height == 20f &&
                button.width == 40.0 &&
                button.height == 20.0
        }
        check(lifecycleProbe.factoryCount == 1) {
            "WinUIView density smoke recreated the Button, factory=" +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "WinUIView density smoke released the Button during density update, release=" +
                "${lifecycleProbe.releaseCount}."
        }

        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 1) {
            "WinUIView density smoke did not release on host disposal, release=" +
                "${lifecycleProbe.releaseCount}."
        }
        println(
            "compose-winui-sample: density update factory=" +
                "${lifecycleProbe.factoryCount} update=${lifecycleProbe.updateCount} " +
                "release=${lifecycleProbe.releaseCount}"
        )
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIViewPropertiesUpdateSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.requiredRootHost()
        val clipToBounds: MutableState<Boolean> = mutableStateOf(true)
        val isUserInteractionEnabled: MutableState<Boolean> = mutableStateOf(false)
        currentComposeView.setContent {
            WinUIViewSampleContent(
                modifier = fixedSizeAndPositionModifier(
                    width = 90,
                    height = 35,
                    x = 0,
                    y = 0,
                ),
                isUserInteractionEnabled = isUserInteractionEnabled.value,
                clipToBounds = clipToBounds.value,
                lifecycleProbe = lifecycleProbe,
            )
        }
        awaitCondition("WinUIView initial properties") {
            val wrapper = (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull()
            val clip = wrapper?.readClipRectOrNull()
            wrapper?.isHitTestVisible == false &&
                lifecycleProbe.lastButton?.isHitTestVisible == false &&
                lifecycleProbe.lastButton?.isTabStop == false &&
                lifecycleProbe.lastButton?.isEnabled == false &&
                clip?.width == 90f &&
                clip.height == 35f
        }
        val wrapper = checkNotNull((rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull()) {
            "WinUIView properties smoke did not install a wrapper."
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView properties smoke did not install a Button."
        }

        clipToBounds.value = false
        isUserInteractionEnabled.value = true
        currentComposeView.performFrameForTest()
        awaitCondition("WinUIView updated properties") {
            val currentWrapper = (rootHost.content.asWinRTCanvas())?.requiredInteropChildren?.singleOrNull()
            currentWrapper?.nativeObject?.sameIdentity(wrapper.nativeObject) == true &&
                lifecycleProbe.lastButton === button &&
                currentWrapper.isHitTestVisible &&
                button.isHitTestVisible &&
                button.isTabStop &&
                button.isEnabled &&
                currentWrapper.readClipRectOrNull() == null
        }
        check(lifecycleProbe.factoryCount == 1) {
            "WinUIView properties smoke recreated the Button, factory=" +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "WinUIView properties smoke released the Button during property update, release=" +
                "${lifecycleProbe.releaseCount}."
        }

        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 1) {
            "WinUIView properties smoke did not release on host disposal, release=" +
                "${lifecycleProbe.releaseCount}."
        }
    }

    private suspend fun runWinUITextInputSessionSmoke() {
        val probe = WinUITextInputSessionSmokeProbe()
        val currentComposeView = WinUIComposeView()
        currentComposeView.setContent {
            val softwareKeyboardController = checkNotNull(LocalSoftwareKeyboardController.current) {
                "WinUI text input smoke did not receive a software keyboard controller."
            }
            LaunchedEffect(softwareKeyboardController) {
                while (!probe.secondInputStarted) {
                    delay(1)
                }
                softwareKeyboardController.show()
                probe.softwareKeyboardShowRequested = true
                softwareKeyboardController.hide()
                probe.softwareKeyboardHideRequested = true
            }
            Layout(
                modifier = Modifier.winUITextInputSessionSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(1, 1) {}
            }
        }
        awaitCondition("WinUI text input first session cancellation") {
            probe.firstInputStarted &&
                probe.firstInputCancelled &&
                probe.secondInputStarted &&
                !probe.secondInputCancelled &&
                probe.softwareKeyboardShowRequested &&
                probe.softwareKeyboardHideRequested
        }
        println("compose-winui-sample: software keyboard show/hide requests")
        currentComposeView.dispose()
        awaitCondition("WinUI text input disposal cancellation") {
            probe.secondInputCancelled
        }
        println("compose-winui-sample: text input session cancellation")
    }

    private fun runWinUISkikoRuntimeClasspathSmoke() {
        val classpath = System.getProperty("java.class.path")
            .split(System.getProperty("path.separator"))
            .map { it.lowercase() }
        check(classpath.any { it.contains("skiko-winui") }) {
            "WinUI sample runtime classpath did not include skiko-winui."
        }

        // Keep this runtime guard focused on Desktop/AWT native runtime artifacts.
        val offenders = classpath.filter { entry ->
            entry.contains("skiko-awt-runtime")
        }
        check(offenders.isEmpty()) {
            "WinUI sample runtime classpath included Skiko AWT/Desktop native runtime artifacts:\n" +
                offenders.joinToString(separator = "\n")
        }
        println("compose-winui-sample: skiko runtime classpath isolated")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUISkikoUnattachedSchedulerSmoke() {
        val currentComposeView = WinUIComposeView()
        try {
            currentComposeView.setContent {
                Layout(
                    content = {},
                ) { _, _ ->
                    layout(48, 32) {}
                }
            }
            currentComposeView.setWindowContainerSizeForTest(IntSize(160, 96))
            delay(100)
            check(currentComposeView.renderApiForTest == GraphicsApi.DIRECT3D) {
                "Unattached WinUIComposeView did not use the Skiko Direct3D render API: " +
                    currentComposeView.renderApiForTest
            }
            check(!currentComposeView.isRenderSchedulerStartedForTest) {
                "Unattached WinUIComposeView started the Skiko frame scheduler."
            }
            check(currentComposeView.renderVersionForTest == 0L) {
                "Unattached WinUIComposeView rendered before its root was loaded."
            }
            check(currentComposeView.lastRenderSizeForTest == null) {
                "Unattached WinUIComposeView reported a render size before loading: " +
                    currentComposeView.lastRenderSizeForTest
            }
            check(currentComposeView.lastRenderedStateSizeForTest == null) {
                "Unattached WinUIComposeView reported a rendered state before loading: " +
                    currentComposeView.lastRenderedStateSizeForTest
            }
            check(currentComposeView.pendingRenderStateSizeForTest == null) {
                "Unattached WinUIComposeView reported a pending render state before loading: " +
                    currentComposeView.pendingRenderStateSizeForTest
            }
            check(currentComposeView.renderFailureForTest == null) {
                "Unattached WinUIComposeView reported a Skiko render failure: " +
                    currentComposeView.renderFailureForTest
            }
        } finally {
            currentComposeView.dispose()
        }
        println("compose-winui-sample: skiko unattached scheduler deferred")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUISkikoRenderDiagnosticsSmoke() {
        val window = XamlWindow()
        var accessibilityClickInvoked = false
        val currentComposeView = window.setContent {
            Layout(
                modifier = Modifier
                    .semantics {
                        contentDescription = "WinUI Skiko rendered content"
                        testTag = "winui-skiko-render"
                        onClick {
                            accessibilityClickInvoked = true
                            true
                        }
                    }
                    .drawBehind {
                        drawRect(Color.Red)
                    },
                content = {},
            ) { _, _ ->
                layout(48, 32) {}
            }
        }
        currentComposeView.setWindowContainerSizeForTest(IntSize(160, 96))
        window.activate()
        try {
            check(currentComposeView.renderApiForTest == GraphicsApi.DIRECT3D) {
                "Attached WinUIComposeView did not use the Skiko Direct3D render API: " +
                    currentComposeView.renderApiForTest
            }
            awaitCondition("WinUI Skiko attached render diagnostics") {
                currentComposeView.renderVersionForTest > 0L ||
                    currentComposeView.renderFailureForTest != null
            }
            check(currentComposeView.isRenderSchedulerStartedForTest) {
                "Attached WinUIComposeView did not start the Skiko frame scheduler."
            }
            val failure = currentComposeView.renderFailureForTest
            check(failure == null) {
                "WinUI Skiko render failed after window attachment: $failure"
            }
            val size = checkNotNull(currentComposeView.lastRenderSizeForTest) {
                "WinUI Skiko render diagnostics did not report a platform render size."
            }
            check(size.width > 0 && size.height > 0) {
                "WinUI Skiko render diagnostics reported a non-positive size: $size."
            }
            val renderedStateSize = checkNotNull(currentComposeView.lastRenderedStateSizeForTest) {
                "WinUI Skiko render diagnostics did not report a rendered state size."
            }
            check(renderedStateSize == size) {
                "WinUI Skiko render diagnostics reported rendered state size " +
                    "$renderedStateSize but platform render size $size."
            }
            check(currentComposeView.pendingRenderStateSizeForTest == null) {
                "WinUI Skiko render diagnostics left a pending render state after rendering: " +
                    currentComposeView.pendingRenderStateSizeForTest
            }
            val accessibilitySnapshot = checkNotNull(currentComposeView.accessibilitySnapshotForTest) {
                "WinUI Skiko accessibility provider did not expose a semantics snapshot."
            }
            val accessibilityNode = accessibilitySnapshot.root.findAccessibilityNode("winui-skiko-render")
            check(accessibilityNode?.info?.name == "WinUI Skiko rendered content") {
                "WinUI Skiko accessibility provider did not expose Compose semantics: " +
                    accessibilitySnapshot.root
            }
            check(WinUIAccessibilityAction.CLICK in accessibilityNode.actions) {
                "WinUI Skiko accessibility provider did not expose the Compose click action: " +
                    accessibilityNode.actions
            }
            val actionInvoked = currentComposeView.performAccessibilityActionForTest(
                WinUIAccessibilityActionRequest(
                    nodeId = accessibilityNode.id,
                    action = WinUIAccessibilityAction.CLICK,
                    text = "",
                ),
            )
            check(actionInvoked && accessibilityClickInvoked) {
                "WinUI Skiko accessibility click did not dispatch back to Compose: " +
                    "actionInvoked=$actionInvoked clicked=$accessibilityClickInvoked"
            }
            val drawRect = currentComposeView.lastDrawRectForTest
            check(drawRect.width > 0f && drawRect.height > 0f) {
                "WinUI Skiko render diagnostics did not record non-empty Compose draw bounds: " +
                    drawRect
            }
            currentComposeView.disposeComposition()
            awaitCondition("WinUI Skiko cleared draw bounds") {
                currentComposeView.lastDrawRectForTest == ComposeRect.Zero ||
                    currentComposeView.renderFailureForTest != null
            }
            val clearFailure = currentComposeView.renderFailureForTest
            check(clearFailure == null) {
                "WinUI Skiko render failed after clearing Compose content: $clearFailure"
            }
            check(currentComposeView.lastDrawRectForTest == ComposeRect.Zero) {
                "WinUI Skiko render diagnostics kept stale draw bounds after clearing content: " +
                    currentComposeView.lastDrawRectForTest
            }
        } finally {
            currentComposeView.dispose()
            window.close()
        }
        println("compose-winui-sample: skiko render diagnostics")
    }

    private fun WinUIAccessibilityNode.findAccessibilityNode(
        automationId: String,
    ): WinUIAccessibilityNode? {
        if (info.automationId == automationId) return this
        children.forEach { child ->
            child.findAccessibilityNode(automationId)?.let { return it }
        }
        return null
    }

    private suspend fun awaitCondition(label: String, condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            delay(10)
        }
        check(condition()) {
            "Timed out waiting for $label."
        }
    }
}

@Composable
private fun RunWindowPopupSmoke(applicationScope: ApplicationScope) {
    val autoExit = java.lang.Boolean.getBoolean("compose.winui.sample.autoExit")
    var popupMeasured by remember { mutableStateOf(false) }
    var popupCompositionLocalObserved by remember { mutableStateOf(false) }
    var popupClosed by remember { mutableStateOf(false) }
    var dialogMeasured by remember { mutableStateOf(false) }
    var dialogCompositionLocalObserved by remember { mutableStateOf(false) }
    var dialogClosed by remember { mutableStateOf(false) }
    with(applicationScope) {
        Window(
            title = "compose-winui window popup smoke",
            backdrop = WindowBackdrop.Mica,
            onCloseRequest = { exitApplication() },
        ) {
            Layout(
                content = {},
                modifier = Modifier.drawBehind {
                    drawRect(
                        color = Color(0xFF2563EB),
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(128f, 48f),
                    )
                },
            ) { _, _ ->
                layout(360, 240) {}
            }
            CompositionLocalProvider(LocalWindowPopupSmokeMarker provides "parent") {
                if (!popupClosed) {
                    Popup {
                        val marker = LocalWindowPopupSmokeMarker.current
                        check(marker == "parent") {
                            "WinUI window popup did not inherit its parent composition context."
                        }
                        LaunchedEffect(marker) {
                            popupCompositionLocalObserved = true
                        }
                        BasicText(
                            text = "Popup",
                            modifier = Modifier.onPlaced {
                                popupMeasured = true
                            },
                        )
                    }
                } else if (!dialogClosed) {
                    Dialog(onDismissRequest = {}) {
                        val marker = LocalWindowPopupSmokeMarker.current
                        check(marker == "parent") {
                            "WinUI dialog did not inherit its parent composition context."
                        }
                        LaunchedEffect(marker) {
                            dialogCompositionLocalObserved = true
                        }
                        BasicText(
                            text = "Dialog",
                            modifier = Modifier.onPlaced {
                                dialogMeasured = true
                            },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(popupMeasured, popupCompositionLocalObserved) {
        if (popupMeasured && popupCompositionLocalObserved) {
            if (autoExit) {
                withFrameNanos { }
                popupClosed = true
            } else {
                println("compose-winui-sample: window popup")
            }
        }
    }
    LaunchedEffect(dialogMeasured, dialogCompositionLocalObserved) {
        if (dialogMeasured && dialogCompositionLocalObserved && autoExit) {
            withFrameNanos { }
            dialogClosed = true
            withFrameNanos { }
            delay(100)
            println("compose-winui-sample: window popup")
            applicationScope.exitApplication()
        }
    }
}

@Composable
private fun WinUISaveableStateSmokeContent(
    onState: (MutableState<String>) -> Unit,
) {
    val state = rememberSaveable(stateSaver = autoSaver()) { mutableStateOf("initial") }
    onState(state)
}

private fun WinUIComposeView.setSaveableStateSmokeContent(
    onState: (MutableState<String>) -> Unit,
) {
    setContent {
        WinUISaveableStateSmokeContent(onState)
    }
}

@Composable
private fun WinUIRetainedValuesSmokeContent(
    createValue: () -> Any,
    onValue: (Any) -> Unit,
) {
    val value = retain { createValue() }
    onValue(value)
}

private fun WinUIComposeView.setRetainedValuesSmokeContent(
    createValue: () -> Any,
    onValue: (Any) -> Unit,
) {
    setContent {
        WinUIRetainedValuesSmokeContent(createValue, onValue)
    }
}

private class WinUITextInputSessionSmokeProbe {
    var firstInputStarted: Boolean = false
    var firstInputCancelled: Boolean = false
    var secondInputStarted: Boolean = false
    var secondInputCancelled: Boolean = false
    var softwareKeyboardShowRequested: Boolean = false
    var softwareKeyboardHideRequested: Boolean = false
}

@OptIn(ExperimentalComposeUiApi::class)
private object WinUITextInputSmokeRequest : PlatformTextInputMethodRequest {
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
    override val focusedRectInRoot: () -> ComposeRect? = { null }
    override val textFieldRectInRoot: () -> ComposeRect? = { null }
    override val textClippingRectInRoot: () -> ComposeRect? = { null }
    override val unclippedTextOffsetInRoot: () -> Offset? = { null }
    override val editText: (TextEditingScope.() -> Unit) -> Unit = {}
}

private class WinUISavedStateViewModel(
    val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var cleared = false
        private set

    init {
        if (!savedStateHandle.contains("value")) {
            savedStateHandle["value"] = "initial"
        }
    }

    override fun onCleared() {
        cleared = true
    }
}

private class WinUIPointerInputSmokeProbe {
    var pressCount = 0
    var enterCount = 0
    var moveCount = 0
    var exitCount = 0
    var releaseCount = 0
    var scrollCount = 0
    var cancelCount = 0
    var lastPosition: Offset? = null
    var lastScrollDelta: Offset? = null
}

private class WinUIOwnerEndApplyChangesProbe {
    val events = mutableListOf<String>()
}

private fun Modifier.winUIOwnerEndApplyChangesSmoke(
    probe: WinUIOwnerEndApplyChangesProbe
): Modifier = this then WinUIOwnerEndApplyChangesElement(probe)

private data class WinUIOwnerEndApplyChangesElement(
    private val probe: WinUIOwnerEndApplyChangesProbe,
) : ModifierNodeElement<WinUIOwnerEndApplyChangesNode>() {
    override fun create(): WinUIOwnerEndApplyChangesNode =
        WinUIOwnerEndApplyChangesNode(probe)

    override fun update(node: WinUIOwnerEndApplyChangesNode) {
        node.probe = probe
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "winUIOwnerEndApplyChangesSmoke"
    }
}

private class WinUIOwnerEndApplyChangesNode(
    var probe: WinUIOwnerEndApplyChangesProbe,
) : Modifier.Node() {
    private val repeatedEffect: () -> Unit = {
        probe.events += "repeat"
    }

    override fun onAttach() {
        sideEffect(repeatedEffect)
        sideEffect(repeatedEffect)
        sideEffect {
            probe.events += "first"
            sideEffect {
                probe.events += "second"
            }
        }
    }
}

private class WinUIIndirectPointerInputSmokeProbe {
    val events = mutableListOf<String>()
    var focused = false
}

private fun Modifier.winUIIndirectPointerInputSmoke(
    probe: WinUIIndirectPointerInputSmokeProbe
): Modifier = this then WinUIIndirectPointerInputSmokeElement(probe)

private data class WinUIIndirectPointerInputSmokeElement(
    private val probe: WinUIIndirectPointerInputSmokeProbe,
) : ModifierNodeElement<WinUIIndirectPointerInputSmokeNode>() {
    override fun create(): WinUIIndirectPointerInputSmokeNode =
        WinUIIndirectPointerInputSmokeNode(probe)

    override fun update(node: WinUIIndirectPointerInputSmokeNode) {
        node.probe = probe
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "winUIIndirectPointerInputSmoke"
    }
}

private class WinUIIndirectPointerInputSmokeNode(
    var probe: WinUIIndirectPointerInputSmokeProbe,
) : Modifier.Node(), IndirectPointerInputModifierNode {
    override fun onIndirectPointerEvent(
        event: IndirectPointerEvent,
        pass: PointerEventPass,
    ) {
        probe.events += "$pass:${event.type}"
        if (pass == PointerEventPass.Main) {
            event.changes.forEach { it.consume() }
        }
    }

    override fun onCancelIndirectPointerInput() = Unit
}

private fun Modifier.winUIPointerInputSmoke(
    probe: WinUIPointerInputSmokeProbe
): Modifier = this then WinUIPointerInputSmokeElement(probe)

private data class WinUIPointerInputSmokeElement(
    private val probe: WinUIPointerInputSmokeProbe,
) : ModifierNodeElement<WinUIPointerInputSmokeNode>() {
    override fun create(): WinUIPointerInputSmokeNode =
        WinUIPointerInputSmokeNode(probe)

    override fun update(node: WinUIPointerInputSmokeNode) {
        node.probe = probe
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "winUIPointerInputSmoke"
    }
}

private class WinUIPointerInputSmokeNode(
    var probe: WinUIPointerInputSmokeProbe,
) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass != PointerEventPass.Main) return
        val change = pointerEvent.changes.firstOrNull()
        change?.let {
            probe.lastPosition = it.position
            probe.lastScrollDelta = it.scrollDelta
        }
        when (pointerEvent.type) {
            PointerEventType.Press -> probe.pressCount += 1
            PointerEventType.Enter -> probe.enterCount += 1
            PointerEventType.Move -> probe.moveCount += 1
            PointerEventType.Exit -> probe.exitCount += 1
            PointerEventType.Release -> probe.releaseCount += 1
            PointerEventType.Scroll -> probe.scrollCount += 1
        }
        change?.consume()
    }

    override fun onCancelPointerInput() {
        probe.cancelCount += 1
    }
}

private fun Modifier.winUITextInputSessionSmoke(
    probe: WinUITextInputSessionSmokeProbe
): Modifier = this then WinUITextInputSessionSmokeElement(probe)

private data class WinUITextInputSessionSmokeElement(
    private val probe: WinUITextInputSessionSmokeProbe,
) : ModifierNodeElement<WinUITextInputSessionSmokeNode>() {
    override fun create(): WinUITextInputSessionSmokeNode =
        WinUITextInputSessionSmokeNode(probe)

    override fun update(node: WinUITextInputSessionSmokeNode) {
        node.probe = probe
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "winUITextInputSessionSmoke"
    }
}

private class WinUITextInputSessionSmokeNode(
    var probe: WinUITextInputSessionSmokeProbe,
) : Modifier.Node(), PlatformTextInputModifierNode {
    override fun onAttach() {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runTextInputSession(isFirstSession = true)
        }
        coroutineScope.launch {
            while (!probe.firstInputStarted) {
                delay(1)
            }
            runTextInputSession(isFirstSession = false)
        }
    }

    private suspend fun runTextInputSession(isFirstSession: Boolean) {
        try {
            establishTextInputSession {
                if (isFirstSession) {
                    probe.firstInputStarted = true
                } else {
                    probe.secondInputStarted = true
                }
                try {
                    startInputMethod(WinUITextInputSmokeRequest)
                } finally {
                    if (isFirstSession) {
                        probe.firstInputCancelled = true
                    } else {
                        probe.secondInputCancelled = true
                    }
                }
            }
        } catch (_: CancellationException) {
        }
    }
}

private fun assertInteropRootOrder(
    rootCanvas: Canvas,
    expected: List<UIElement>,
    label: String,
) {
    check(rootCanvas.requiredInteropChildren.size == expected.size) {
        "WinUIView container sync smoke expected ${expected.size} children for $label, got " +
            "${rootCanvas.requiredInteropChildren.size}."
    }
    check(hasInteropRootOrder(rootCanvas, expected)) {
        "WinUIView container sync smoke had incorrect child order for $label."
    }
}

private fun hasInteropRootOrder(rootCanvas: Canvas, expected: List<UIElement>): Boolean {
    if (rootCanvas.requiredInteropChildren.size != expected.size) return false
    return expected.indices.all { index ->
        rootCanvas.requiredInteropChildren[index].nativeObject.sameIdentity(expected[index].nativeObject)
    }
}

private fun WinUIComposeView.requiredRootHost(): ContentControl =
    checkNotNull(root.asWinRTContentControl()) {
        "WinUIComposeView root is not a ContentControl."
    }

private fun Any?.asWinRTContentControl(): ContentControl? =
    asExistingInstance(ContentControl::class.java)
        ?: runCatching { this?.asWinRT<ContentControl>() }.getOrNull()

private fun Any?.asWinRTCanvas(): Canvas? =
    asExistingInstance(Canvas::class.java) ?: runCatching { this?.asWinRT<Canvas>() }.getOrNull()

private fun Any?.asWinRTRectangleGeometry(): RectangleGeometry? =
    asExistingInstance(RectangleGeometry::class.java)
        ?: runCatching { this?.asWinRT<RectangleGeometry>() }.getOrNull()

private fun <T> Any?.asExistingInstance(type: Class<T>): T? =
    if (this != null && type.isInstance(this)) type.cast(this) else null

private val Canvas.requiredInteropChildren: List<UIElement>
    get() {
        val rootChildren = checkNotNull(children) {
            "WinUI Canvas children collection is not available."
        }
        if (rootChildren.size == 0) return emptyList()
        return List(rootChildren.size - 1) { index ->
            rootChildren[index + 1]
        }
    }

private fun UIElement.readClipRectOrNull(): Rect? =
    runCatching {
        val clipProperty = UIElement.clipProperty
        val clip = getValue(clipProperty).asWinRTRectangleGeometry() ?: return null
        val rectProperty = RectangleGeometry.rectProperty
        clip.getValue(rectProperty) as? Rect
    }.getOrNull()

private fun WinUIComposeView.rootForTest(): RootForTest {
    val owner = javaClass.getDeclaredField("owner").also {
        it.isAccessible = true
    }.get(this)
    return owner.javaClass.getMethod("getRootForTest").invoke(owner) as RootForTest
}

private fun fixedSizeAndPositionModifier(width: Int, height: Int, x: Int, y: Int): Modifier =
    Modifier.layout { measurable, _ ->
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(x + width, y + height) {
            placeable.place(x, y)
        }
    }

private fun snapshotDrivenSizeModifier(
    width: MutableState<Int>,
    height: MutableState<Int>,
): Modifier =
    Modifier.layout { measurable, _ ->
        val currentWidth = width.value
        val currentHeight = height.value
        val placeable = measurable.measure(Constraints.fixed(currentWidth, currentHeight))
        layout(currentWidth, currentHeight) {
            placeable.place(0, 0)
        }
    }

@Suppress("DEPRECATION")
private fun legacyOnPlacedModifier(onPlaced: () -> Unit): Modifier =
    Modifier.then(
        object : OnPlacedModifier {
            override fun onPlaced(coordinates: LayoutCoordinates) {
                onPlaced()
            }
        }
    )

@Composable
private fun ConditionalPlacement(
    isPlaced: Boolean,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints)
        layout(placeable.width, placeable.height) {
            if (isPlaced) {
                placeable.place(0, 0)
            }
        }
    }
}
