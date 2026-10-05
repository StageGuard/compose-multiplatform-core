# Compose WinUI Indirect Pointer Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route Skiko precision-touchpad frames into focused Compose `IndirectPointerInputModifierNode`s through both `ComposeScene` and the standalone `WinUIComposeView` owner.

**Architecture:** A shared Skiko-host event wrapper maps Skiko's device-relative frame one-to-one into Compose changes. `ComposeScene` exposes synchronous send/cancel entry points and selects the focused owner; the standalone WinUI view binds its render layer to the owning `Window` and delegates to `WinUIOwner`. A stateless common focus listener cancels only indirect-input ancestors that lose focus.

**Tech Stack:** Compose UI common/skikoHost/skiko/winui source sets, Kotlin Multiplatform, Skiko WinUI input API, Kotlin Test, WinUI JVM sample.

## Global Constraints

- Consume the Skiko contract produced by `2026-07-17-skiko-winui-indirect-pointer-input.md`.
- Keep shared mapping and focus logic in source sets reusable by future `winui-mingw`; do not add JVM types to `winuiMain`.
- Preserve HIMETRIC positions exactly; do not apply density, content scale, screen, or panel conversion.
- Route through focus, never pointer hit testing.
- Return `FocusOwner.dispatchIndirectPointerEvent` consumption synchronously to Skiko.
- Keep `WinUIComposeView()` without an owning `Window` operational but without HWND indirect input.
- Do not enable or claim full Compose Mingw runtime validation while `KWINRT-061` remains open.
- Preserve the existing dirty worktree, including the ongoing `skikoHostMain` source-set split and InputPane work.
- Keep `AGENTS.md` and `.agent_tmp/` untracked; use `git.exe` for commits.
- Use JDK 25 and `--no-configuration-cache` for WinUI tasks.

---

### Task 1: Map Skiko Frames to Compose Indirect Pointer Events

**Files:**
- Create: `compose/ui/ui/src/skikoHostMain/kotlin/androidx/compose/ui/input/indirect/SkikoIndirectPointerEvent.skiko.kt`
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/input/indirect/WinUIIndirectPointerEvent.winui.kt`
- Create: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/input/indirect/SkikoIndirectPointerEventTest.kt`

**Interfaces:**
- Consumes: Compose indirect pointer types in `skikoHostMain`; Skiko WinUI event types only in `winuiMain`.
- Produces: `SkikoIndirectPointerEvent : PlatformIndirectPointerEvent` shared by ComposeScene and WinUI, plus `WinUIIndirectPointerEvent.toComposeIndirectPointerEvent()` in the WinUI source set.

- [ ] **Step 1: Write mapping tests before production code**

Cover one-contact press/move/release, two contacts with join/release transitions, exact current/previous fields, pressure, pointer IDs, native event retention, and unscaled HIMETRIC coordinates:

```kotlin
val native = WinUIIndirectPointerEvent(
    type = WinUIIndirectPointerEventType.MOVE,
    changes = listOf(
        WinUIIndirectPointerChange(
            pointerId = 9,
            timestampMillis = 30,
            x = 8125f,
            y = 4030f,
            pressed = true,
            pressure = 0.75f,
            previousTimestampMillis = 20,
            previousX = 8000f,
            previousY = 4000f,
            previousPressed = true,
        )
    ),
    primaryDirectionalMotionAxis = WinUIIndirectPointerPrimaryDirectionalMotionAxis.NONE,
    deviceId = 44,
    deviceRect = WinUIIndirectPointerDeviceRect(0, 0, 12000, 7000),
    frameId = 71,
)
val event = native.toComposeIndirectPointerEvent()
assertEquals(Offset(8125f, 4030f), event.changes.single().position)
assertEquals(Offset(8000f, 4000f), event.changes.single().previousPosition)
assertEquals(IndirectPointerEventType.Move, event.type)
assertSame(native, (event as SkikoIndirectPointerEvent).nativeEvent)
```

- [ ] **Step 2: Run the focused WinUI JVM test and verify RED**

Use the locally published Skiko version from the sibling plan:

```powershell
git.exe status --short
.\gradlew.bat :compose:ui:ui:winuiJvmTest `
  --tests androidx.compose.ui.input.indirect.SkikoIndirectPointerEventTest `
  -PcomposeWinUi.skikoWinUiVersion=0.0.0-indirect-pointer-local-SNAPSHOT `
  --no-configuration-cache --max-workers=1 --console=plain
```

Expected: compilation fails because the mapping class/function do not exist.

- [ ] **Step 3: Implement exact one-to-one mapping**

```kotlin
internal class SkikoIndirectPointerEvent(
    override val changes: List<IndirectPointerInputChange>,
    override val type: IndirectPointerEventType,
    override val primaryDirectionalMotionAxis: IndirectPointerEventPrimaryDirectionalMotionAxis,
    internal val nativeEvent: Any?,
) : PlatformIndirectPointerEvent {
    init {
        require(changes.isNotEmpty()) { "changes cannot be empty" }
    }
}

```

Keep that class in `skikoHostMain` without importing `org.jetbrains.skiko.winui`; desktop/skiko targets depend on regular Skiko, not the `skiko-winui` artifact. In the existing `winuiMain` file, add:

```kotlin
internal fun WinUIIndirectPointerEvent.toComposeIndirectPointerEvent(): IndirectPointerEvent =
    SkikoIndirectPointerEvent(
        changes = changes.map { change ->
            IndirectPointerInputChange(
                id = PointerId(change.pointerId),
                uptimeMillis = change.timestampMillis,
                position = Offset(change.x, change.y),
                pressed = change.pressed,
                pressure = change.pressure,
                previousUptimeMillis = change.previousTimestampMillis,
                previousPosition = Offset(change.previousX, change.previousY),
                previousPressed = change.previousPressed,
            )
        },
        type = when (type) {
            WinUIIndirectPointerEventType.PRESS -> IndirectPointerEventType.Press
            WinUIIndirectPointerEventType.MOVE -> IndirectPointerEventType.Move
            WinUIIndirectPointerEventType.RELEASE -> IndirectPointerEventType.Release
        },
        primaryDirectionalMotionAxis = when (primaryDirectionalMotionAxis) {
            WinUIIndirectPointerPrimaryDirectionalMotionAxis.NONE ->
                IndirectPointerEventPrimaryDirectionalMotionAxis.None
            WinUIIndirectPointerPrimaryDirectionalMotionAxis.X ->
                IndirectPointerEventPrimaryDirectionalMotionAxis.X
            WinUIIndirectPointerPrimaryDirectionalMotionAxis.Y ->
                IndirectPointerEventPrimaryDirectionalMotionAxis.Y
        },
        nativeEvent = this,
    )
```

Retain the existing public WinUI test factory in `WinUIIndirectPointerEvent.winui.kt`, but make it construct `SkikoIndirectPointerEvent(nativeEvent = null)` so existing sample/test callers remain source-compatible.

- [ ] **Step 4: Run mapping tests and verify GREEN**

Run the command from Step 2. Expected: all mapping assertions pass.

- [ ] **Step 5: Commit only the mapping slice**

```powershell
git.exe add -- compose/ui/ui/src/skikoHostMain/kotlin/androidx/compose/ui/input/indirect/SkikoIndirectPointerEvent.skiko.kt `
  compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/input/indirect/WinUIIndirectPointerEvent.winui.kt `
  compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/input/indirect/SkikoIndirectPointerEventTest.kt
git.exe commit -m "feat: map Skiko indirect pointer events"
```

### Task 2: Add ComposeScene Send/Cancel Entry Points and Focused-Owner Routing

**Files:**
- Modify: `compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/scene/ComposeScene.skiko.kt`
- Modify: `compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/scene/BaseComposeScene.skiko.kt`
- Modify: `compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/scene/CanvasLayersComposeScene.skiko.kt`
- Modify: `compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/scene/PlatformLayersComposeScene.skiko.kt`
- Modify: `compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/node/RootNodeOwner.skiko.kt`
- Create: `compose/ui/ui/src/skikoTest/kotlin/androidx/compose/ui/scene/IndirectPointerComposeSceneTest.kt`

**Interfaces:**
- Consumes: `IndirectPointerEvent`, `FocusOwner.dispatchIndirectPointerEvent`, and `dispatchIndirectPointerCancel`.
- Produces: `ComposeScene.sendIndirectPointerEvent`, `cancelIndirectPointerInput`, and focused layer selection.

- [ ] **Step 1: Write failing scene tests**

Build focused indirect-input nodes in main content and a canvas layer. Assert Initial/Main/Final pass ordering, only focused ancestors receive the event, consumption returns `true`, the focused canvas layer wins over main content, fallback to main occurs when no layer is focused, explicit cancel reaches the last event owner, and release with no pressed changes ends the active owner.

- [ ] **Step 2: Run the focused desktop/skiko test and verify RED**

```powershell
.\gradlew.bat :compose:ui:ui:desktopTest `
  --tests androidx.compose.ui.scene.IndirectPointerComposeSceneTest `
  --no-configuration-cache --max-workers=1 --console=plain
```

Expected: compilation fails because `ComposeScene` does not expose the indirect-input functions.

- [ ] **Step 3: Add the scene API and BaseComposeScene boundary**

Add to `ComposeScene`:

```kotlin
fun sendIndirectPointerEvent(event: IndirectPointerEvent): Boolean
fun cancelIndirectPointerInput()
```

Implement in `BaseComposeScene` with `postponeInvalidation` and `frameRecomposer.performScheduledEffects()` exactly like key/rotary input, delegating to two new abstract methods:

```kotlin
protected abstract fun processIndirectPointerEvent(event: IndirectPointerEvent): Boolean
protected abstract fun processCancelIndirectPointerInput()
```

- [ ] **Step 4: Route through RootNodeOwner and focused scene owner**

Add `RootNodeOwner.onIndirectPointerEvent` and `onCancelIndirectPointerInput`. `PlatformLayersComposeScene` always uses `mainOwner`. `CanvasLayersComposeScene` chooses `focusedLayer?.owner ?: mainOwner` on every frame, stores the last receiving owner while any change remains pressed, and sends explicit cancel to that stored owner before clearing it.

- [ ] **Step 5: Run scene tests and verify GREEN**

Run the Step 2 command. Expected: all routing, pass, consumption, and cancellation assertions pass.

- [ ] **Step 6: Commit the scene boundary**

Stage only the six production files and the new test, then commit:

```powershell
git.exe commit -m "feat: dispatch indirect pointer events through ComposeScene"
```

### Task 3: Share Focus-Change Cancellation Across Skiko and WinUI Owners

**Files:**
- Create: `compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/IndirectPointerInputFocusListener.kt`
- Modify: `compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/node/RootNodeOwner.skiko.kt`
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/node/WinUIOwner.winui.kt`
- Modify: `compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeView.android.kt`
- Create: `compose/ui/ui/src/skikoTest/kotlin/androidx/compose/ui/focus/IndirectPointerInputFocusListenerTest.kt`

**Interfaces:**
- Consumes: `FocusListener`, `Nodes.IndirectPointerInput`, ancestor traversal, and `onCancelIndirectPointerInput`.
- Produces: one stateless `IndirectPointerInputFocusListener` used by Skiko/WinUI and delegated to by Android.

- [ ] **Step 1: Write failing focus-chain tests**

Cover old child to sibling (old child canceled, shared parent retained), child to parent (only child canceled), focused node to null (entire old chain canceled), and null to new node (nothing canceled).

- [ ] **Step 2: Verify RED**

Run the focused `desktopTest`; expected: compilation fails because the shared listener does not exist.

- [ ] **Step 3: Implement the common listener**

```kotlin
internal object IndirectPointerInputFocusListener : FocusListener {
    override fun onFocusChanged(
        previous: FocusTargetModifierNode?,
        current: FocusTargetModifierNode?,
    ) {
        val previousNodes =
            previous?.ancestors(type = Nodes.IndirectPointerInput, includeSelf = true) ?: return
        val currentNodes =
            current?.setOfAncestors(type = Nodes.IndirectPointerInput, includeSelf = true)
        previousNodes.fastForEach { node ->
            if (currentNodes?.contains(node) != true) {
                node.onCancelIndirectPointerInput()
            }
        }
    }
}
```

Register/unregister this listener in `RootNodeOwner` and `WinUIOwner` lifecycle. Replace Android's duplicated body with delegation to the shared object without changing Android behavior.

- [ ] **Step 4: Run common/skiko and WinUI owner tests**

Run the focused focus test and `WinUIOwnerTest`; expected: pass, including disposal removing the listener.

- [ ] **Step 5: Commit focus cancellation**

```powershell
git.exe commit -m "feat: cancel indirect input when Compose focus changes"
```

### Task 4: Bind Standalone WinUIComposeView to Skiko HWND Input

**Files:**
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/node/WinUIOwner.winui.kt`
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUISkikoRenderHost.winui.kt`
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIComposeView.winui.kt`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUISkikoRenderHostTest.kt`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/node/WinUIOwnerTest.kt`

**Interfaces:**
- Consumes: Skiko `Window.bindWinUIIndirectPointerInput`, shared event mapping, and the focused `WinUIOwner`.
- Produces: direct standalone event delivery, cancellation, consumption propagation, and safe disposal.

- [ ] **Step 1: Add fake-layer/render-host tests and owner dispatch tests**

Extend the fake `WinUISkikoLayerAdapter` with `var inputHandler` and `bindIndirectPointerInput(window)`. Assert the render host installs exactly one handler, returns native consumption unchanged, forwards cancel, closes the binding before the layer, clears its handler, and leaves the binding absent until an owning `Window` is supplied. Add an owner test proving focused modifier consumption and cancel dispatch.

- [ ] **Step 2: Run focused WinUI JVM tests and verify RED**

```powershell
.\gradlew.bat :compose:ui:ui:winuiJvmTest `
  --tests androidx.compose.ui.platform.WinUISkikoRenderHostTest `
  --tests androidx.compose.ui.node.WinUIOwnerTest `
  -PcomposeWinUi.skikoWinUiVersion=0.0.0-indirect-pointer-local-SNAPSHOT `
  --no-configuration-cache --max-workers=1 --console=plain
```

Expected: compilation fails because binding and owner dispatch methods are absent.

- [ ] **Step 3: Add WinUIOwner production entry points**

```kotlin
internal fun sendIndirectPointerEvent(event: IndirectPointerEvent): Boolean {
    if (isShuttingDown) return false
    return focusOwner.dispatchIndirectPointerEvent(event)
}

internal fun cancelIndirectPointerInput() {
    if (!isShuttingDown) focusOwner.dispatchIndirectPointerCancel()
}
```

- [ ] **Step 4: Add render-host binding ownership**

Add `inputHandler` and `bindIndirectPointerInput` to `WinUISkikoLayerAdapter`. The default adapter delegates to its `WinUISkiaLayer` and `Window.bindWinUIIndirectPointerInput`. `WinUISkikoRenderHost.bindIndirectPointerInput` installs a dedicated handler that maps events and invokes supplied callbacks and retains the sole binding reference. Add `closeIndirectPointerInput()`; `WinUISkikoRenderHost.close()` calls it before closing the frame scheduler/layer.

- [ ] **Step 5: Bind only Window-owned compose views**

In `WinUIComposeView`, after `owner` exists, call:

```kotlin
window?.let { owningWindow ->
    renderHost.bindIndirectPointerInput(
        window = owningWindow,
        onEvent = { native ->
            owner.sendIndirectPointerEvent(native.toComposeIndirectPointerEvent())
        },
        onCancel = owner::cancelIndirectPointerInput,
    )
}
```

Call `renderHost.closeIndirectPointerInput()` in the disposal sequence before `owner.dispose()`. The no-window constructor never calls the bind method, so the render host retains no HWND binding.

- [ ] **Step 6: Run focused tests and verify GREEN**

Run the Step 2 command. Expected: all fake binding, owner routing, synchronous consumption, and disposal assertions pass.

- [ ] **Step 7: Commit the standalone WinUI bridge**

```powershell
git.exe commit -m "feat: route Skiko touchpad input into WinUI Compose"
```

### Task 5: Validate Compose Integration Against the Local Skiko Build

**Files:**
- Modify: none unless a source-set isolation assertion is needed for the new shared file.

**Interfaces:**
- Consumes: Tasks 1-4 and the locally published Skiko artifact.
- Produces: compile/test/runtime evidence without claiming Compose Mingw runtime support.

- [ ] **Step 1: Run focused and full tests**

Run mapping, focus, scene, render-host, and owner tests; then the full `:compose:ui:ui:winuiJvmTest` and `:compose:ui:ui:compileKotlinWinuiJvm` with `-PcomposeWinUi.skikoWinUiVersion=0.0.0-indirect-pointer-local-SNAPSHOT`.

- [ ] **Step 2: Run the repository-local WinUI sample**

```powershell
.\gradlew.bat :compose:ui:ui:winui-samples:runWinUISkikoSample `
  -PcomposeWinUi.skikoWinUiVersion=0.0.0-indirect-pointer-local-SNAPSHOT `
  --no-configuration-cache --max-workers=1 --console=plain
```

Expected: the existing render/input smoke markers complete. On a precision-touchpad machine, manually focus the sample's indirect-input probe, verify press/move/release callbacks with unchanged HIMETRIC coordinates, verify consumed input suppresses Windows fallback, and verify an unconsumed gesture scrolls/zooms exactly once. If hardware interaction cannot be automated, record it as skipped rather than passed.

- [ ] **Step 3: Compile the shared future-Mingw source boundary only**

Run the available source-set/isolation checks that prove mapping and adapters remain in `commonMain`/`skikoHostMain`/`winuiMain`. Do not enable the full WinUI Mingw application target until `KWINRT-061` is resolved.

- [ ] **Step 4: Final diff and scope review**

```powershell
git.exe diff --check
git.exe status --short
git.exe log --oneline -6
```

Expected: all pre-existing dirty files and untracked `AGENTS.md` remain preserved; only the indirect pointer commits are newly added.
