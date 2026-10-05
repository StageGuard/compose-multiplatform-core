# Compose WinUI P1 Runtime Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Complete the P1 design in `docs/superpowers/specs/2026-07-15-compose-winui-p1-design.md` for the WinUI JVM target.

**Architecture:** Reuse `WinUIOwner`, `WinUIComposeView`, the existing transparent Flyout host, and the existing WinUI drag/accessibility adapters. Keep platform behavior in `winuiMain`; use existing Skia `GraphicsLayer` APIs for rendering rather than adding a second rendering root. Add small test seams only where native WinRT objects cannot be constructed in unit tests.

**Tech Stack:** Kotlin Multiplatform, Compose UI/foundation, WinUI XAML/Windows Runtime projections from kotlin-winrt, Skia graphics layer APIs, Kotlin test, Gradle WinUI JVM target.

## Global Constraints

- Work only on the WinUI JVM target; do not enable or validate Mingw.
- Use `--no-configuration-cache` for WinUI Gradle validation.
- Use Windows Runtime/WinUI APIs; do not add shell/process fallbacks.
- Record any kotlin-winrt workaround in `kotlin-winrt-issues.md` with a `KWINRT-###` id.
- Preserve unrelated user changes, `AGENTS.md`, and `.agent_tmp/`; stage only files belonging to a task.
- Production code follows a failing-test-first cycle for every behavior change.
- Commit each verified task with `git.exe`.

---

### Task 1: Establish the WinUI P1 baseline

**Files:**
- Read: `docs/superpowers/specs/2026-07-15-compose-winui-p1-design.md`
- Read: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/node/WinUIOwnerLayerTest.kt`
- Read: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIAccessibilityBridgeTest.kt`
- Read: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/draganddrop/WinUIDragAndDropManagerTest.kt`

**Steps:**

- [ ] Run the existing focused WinUI JVM tests with `--no-configuration-cache` and record the baseline output under `.agent_tmp/`.
- [ ] Confirm the baseline command is the repository's `:compose:ui:ui:winuiJvmTest` task and identify any pre-existing compile/test failures before adding tests.
- [ ] Do not modify production code in this task.
- [ ] Commit only a baseline log if one is needed; logs must remain under `.agent_tmp/` and untracked.

**Verification:** The baseline command must finish, or its existing failures must be recorded before Task 2. A baseline failure is not attributed to this work.

---

### Task 2: Promote WinUI owner layers to real Compose GraphicsLayers

**Files:**
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/node/WinUIOwner.winui.kt`
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/node/WinUIOwnerLayer.winui.kt`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/node/WinUIOwnerLayerTest.kt`

**Interfaces:**
- Consumes: `GraphicsContext`, `GraphicsLayer`, `ReusableGraphicsLayerScope`, `GraphicsLayerScope.Fields`, and `GraphicsLayer.setOutline`.
- Produces: a `WinUIOwnerLayer` that records a display list with `GraphicsLayer.record()` and draws it through `DrawScope.drawLayer()` while preserving external explicit layers.

**RED:**

- [ ] Add a test that sets alpha, shadow elevation/colors, shape/clip, render effect, color filter, blend mode, compositing strategy, camera distance, and outsets on a `ReusableGraphicsLayerScope`, calls `updateLayerProperties`, and asserts the test state exposes the same values and outline.
- [ ] Add a test that uses a rounded outline and asserts `isInLayer()` accepts a point inside the rounded shape and rejects a point in the clipped corner.
- [ ] Run `:compose:ui:ui:winuiJvmTest --no-configuration-cache` for `WinUIOwnerLayerTest`; confirm the new assertions fail because the current layer drops those values.

**GREEN:**

- [ ] Change `WinUIOwner.createLayer()` to use `explicitLayer` when supplied, otherwise create a layer from the owner's `graphicsContext`, and pass ownership information to `WinUIOwnerLayer`.
- [ ] Replace the manual transform-only state in `WinUIOwnerLayer` with the existing Compose `GraphicsLayer` state: copy all mutated scope fields, density/layout direction, outline, and layer outsets; update pivot and matrix state from the same values.
- [ ] Record the draw block using `graphicsLayer.record(density, layoutDirection, size)`, then draw using `CanvasDrawScope.drawLayer(graphicsLayer)` so alpha, effects, compositing, shadows, and clipping use the Skia implementation already used by the rest of Compose.
- [ ] Use `isInOutline(graphicsLayer.outline, x, y)` when clipping is enabled; preserve the current fast path when clipping is disabled.
- [ ] Release internally-created layers through `GraphicsContext`; do not release caller-owned explicit layers. Reset all copied state on reuse.
- [ ] Extend `WinUIOwnerLayerState` only with test-observable values needed by the assertions; do not expose production APIs.

**GREEN verification:** Run `WinUIOwnerLayerTest` again and then the full `:compose:ui:ui:winuiJvmTest --no-configuration-cache` task. The focused tests must pass before moving on.

**Commit:** `git.exe add` only the three task files and commit `fix: apply graphics layers on compose-winui`.

---

### Task 3: Wire Popup dismissal and modal Dialog hosting

**Files:**
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/window/Popup.winui.kt`
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/window/Dialog.winui.kt`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/window/WinUIWindowPropertiesTest.kt`
- Create: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/window/WinUIPopupDialogBehaviorTest.kt`

**Interfaces:**
- Consumes: `PopupProperties`, `DialogProperties`, `PopupPositionProvider`, `LocalWinUIRoot`, `LocalWinUIWindow`, and the existing `WinUIFlyoutPopupHost` lifecycle.
- Produces: a stable dismiss callback path for canvas and window popups plus an idempotent dedicated Dialog host.

**RED:**

- [ ] Add pure host-state tests for back and outside pointer events: enabled properties invoke the latest callback once; disabled properties do not invoke it; replacing the callback uses the latest value.
- [ ] Add a test for `usePlatformDefaultWidth` showing the default width constraint differs from an unconstrained dialog.
- [ ] Run the new test class and confirm it fails because the current Popup/Dialog implementations do not dispatch dismiss events or apply width policy.

**GREEN:**

- [ ] Thread `rememberUpdatedState(onDismissRequest)` into both Popup layout paths and the Flyout host; update the host through `SideEffect` without capturing stale lambdas.
- [ ] For window popups, configure `lightDismissOverlayMode` from `dismissOnClickOutside`, register a closed/focus-loss handler that invokes the callback only for an enabled light-dismiss, and add a key handler for `dismissOnBackPress`.
- [ ] For canvas popups, install the existing layer key and outside-pointer listeners with `dismissOnBackPress`/`dismissOnClickOutside`; ensure non-focusable popups do not consume unrelated outside events.
- [ ] Replace the in-root Dialog layout with an idempotent transparent WinUI host using `WinUIComposeView`, center the measured content, apply the default-width constraint, route back/outside events, and dispose the child Compose view and registrations on removal.
- [ ] Keep all native event registration/removal guarded for XAML roots that are not loaded yet.

**GREEN verification:** Run `WinUIPopupDialogBehaviorTest`, the existing properties tests, and the full WinUI JVM test task.

**Commit:** `git.exe add` only the Popup/Dialog sources and behavior tests and commit `fix: implement compose-winui popup and dialog dismissal`.

---

### Task 4: Implement WinUI content transfer and text-field drops

**Files:**
- Modify: `compose/foundation/foundation/src/skikoMain/kotlin/androidx/compose/foundation/content/MediaType.skiko.kt`
- Modify: `compose/foundation/foundation/src/skikoMain/kotlin/androidx/compose/foundation/content/TransferableContent.skiko.kt`
- Modify: `compose/foundation/foundation/src/skikoMain/kotlin/androidx/compose/foundation/content/internal/ReceiveContentDragAndDropNode.skiko.kt`
- Modify: `compose/foundation/foundation/src/skikoMain/kotlin/androidx/compose/foundation/text/input/internal/TextFieldDragAndDropNode.skiko.kt`
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIDragAndDropAdapter.winui.kt`
- Create: `compose/foundation/foundation/src/winuiJvmTest/kotlin/androidx/compose/foundation/content/WinUITransferableContentTest.kt`
- Create: `compose/foundation/foundation/src/winuiJvmTest/kotlin/androidx/compose/foundation/text/input/internal/WinUITextFieldDragAndDropTest.kt`

**Interfaces:**
- Consumes: `DragAndDropEvent.nativeEvent`, WinUI `DragEventArgs.dataView`, `DataPackageView`, `StandardDataFormats`, `ClipEntry`, and `ReceiveContentConfiguration`.
- Produces: a platform transferable object with actual format metadata and safe asynchronous plain-text reads.

**RED:**

- [ ] Add a `MediaType` test asserting a custom representation is preserved and standard text/plain types compare by representation.
- [ ] Add a transferable-content test with a fake/test data-package adapter asserting `hasMediaType(MediaType.PlainText)` is true and `readPlainText()` returns the supplied text.
- [ ] Add a receive-content target test asserting accepted text calls the listener, unsupported content is rejected, and the reported move position equals the native event position.
- [ ] Run focused foundation/UI tests and confirm the current empty representation, `false`, `null`, thrown `NotImplementedError`, and `Offset.Zero` behavior fails the tests.

**GREEN:**

- [ ] Keep the `MediaType` representation string instead of constructing all types as empty placeholders; define WinUI/Skiko standard representations using `StandardDataFormats` names.
- [ ] Add a small platform adapter around WinUI `DataPackageView` that exposes available formats and reads text with `getTextAsync().await()`; make unavailable/failed reads return `null` without throwing from pointer dispatch.
- [ ] Store the adapter in `ClipEntry.nativeClipEntry` and make `TransferableContent.hasMediaType()` compare the adapter's available formats, honoring `MediaType.All`.
- [ ] Implement `ReceiveContentDragAndDropNode` with a real `DragAndDropTargetModifierNode`: validate hinted media types, call `dragAndDropRequestPermission`, build `TransferableContent(Source.DragAndDrop)`, and forward remaining content to the configuration listener.
- [ ] Implement the text-field node with the same adapter and use `event.positionInRoot` for `onMoved`; call the supplied `onDrop` only for accepted plain text.

**GREEN verification:** Run `:compose:foundation:foundation:winuiJvmTest --no-configuration-cache`, then `:compose:ui:ui:winuiJvmTest --no-configuration-cache`.

**Commit:** `git.exe add` only the content-transfer sources/tests and commit `fix: implement compose-winui content transfer`.

---

### Task 5: Provide a production WinUI drag source

**Files:**
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/draganddrop/WinUIDragAndDropManager.winui.kt`
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIDragAndDropAdapter.winui.kt`
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIComposeView.winui.kt`
- Modify: `compose/ui/ui/build.gradle`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/draganddrop/WinUIDragAndDropManagerTest.kt`

**Interfaces:**
- Consumes: `DragAndDropTransferData`, `DrawScope` decoration callback, WinUI `UIElement`/drag APIs, and the existing test starter seam.
- Produces: a production `WinUIDragAndDropStarter` installed by the root view, with test injection still overriding it.

**RED:**

- [ ] Add a test that constructs a root adapter with an injected starter and verifies `isRequestDragAndDropTransferRequired` is true and the transfer data/decoration reach the starter.
- [ ] Add a test that verifies disposing the root clears the starter and prevents a second native drag request.
- [ ] Run the focused test and confirm the current production manager only works with the test setter.

**GREEN:**

- [ ] Add only the WinRT projection `type(...)` entries required by the actual drag API used; do not enable Mingw.
- [ ] Install the starter from `WinUIComposeView` after the root XAML element is loaded, translate Compose transfer data into a WinRT `DataPackage`, and invoke the projected WinUI drag operation with the decoration callback where supported.
- [ ] Remove the starter on dispose and keep `setStarterForTest` as a test-only override.
- [ ] If the projection requires a workaround, record a stable `KWINRT-###` issue and reference it in the narrow adapter code.

**GREEN verification:** Run the focused drag source tests and the WinUI JVM compile. If the generated projection is unavailable, stop and report the exact missing type rather than adding a shell fallback.

**Commit:** `git.exe add` only the drag-source sources/tests/build entries and commit `fix: start compose-winui drag transfers`.

---

### Task 6: Enable production accessibility update delivery

**Files:**
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIAccessibilityBridge.winui.kt`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIAccessibilityBridgeTest.kt`

**Interfaces:**
- Consumes: existing `WinUIAccessibilityProvider` attachment and `WinUIAccessibilityUpdate` batching.
- Produces: automatic production flushes while a provider is attached, with testing force/cancel controls preserved.

**RED:**

- [ ] Add a test that attaches/enables a production provider, sends a semantics change, and asserts one scheduled flush without calling `forceAccessibilityForTesting(true)`.
- [ ] Add a test that detaches/disposes the bridge before the scheduled callback and asserts no update is delivered.
- [ ] Run the focused test and confirm it fails because scheduling is currently gated by the testing flag.

**GREEN:**

- [ ] Add an explicit provider-attached/production-enabled state to the bridge, set it from the existing render-host provider lifecycle, and make `scheduleFlushIfNeeded()` use that state or the test force flag.
- [ ] Keep event coalescing, interval configuration, pending state, and test-only force disable semantics unchanged.
- [ ] Ensure provider attachment does not trigger a flush when there is no pending semantic/layout/scroll change.

**GREEN verification:** Run `WinUIAccessibilityBridgeTest` and the complete WinUI JVM test task.

**Commit:** `git.exe add` only accessibility sources/tests and commit `fix: deliver compose-winui accessibility updates`.

---

### Task 7: Full WinUI verification and sample smoke test

**Files:**
- Read: all task diffs and `compose/ui/compose-winui-plan.md`
- Write: `.agent_tmp/compose-winui-p1-verification.log` and sample output only.

**Steps:**

- [ ] Run the complete focused WinUI JVM tests with `--no-configuration-cache` and capture output under `.agent_tmp/`.
- [ ] Compile `compose-ui` for the WinUI JVM target with the repository task and `--no-configuration-cache`.
- [ ] Run the repository-local `compose/ui/ui/winui-samples` sample with `--no-configuration-cache`; confirm the existing `WinUIView` content renders and no native host remains undisposed after close.
- [ ] Run `git diff --check`, inspect `git status --short`, and verify no unrelated user files, `AGENTS.md`, or `.agent_tmp/` files are staged.
- [ ] Re-run the static audit patterns for P1 (`NotImplementedError` in receive-content, empty WinUI owner layer state, unused popup dismiss callback, accessibility force-only scheduling) and record any residual gap.

**Commit:** If verification requires a final test-only correction, commit it separately with `test: stabilize compose-winui P1 verification`; otherwise leave the task commits intact.

## Plan self-review

- Graphics, windowing, content transfer, drag source, accessibility, and final validation each have an independently testable task.
- Every production task has a RED test step before GREEN code changes.
- The plan keeps WinUI JVM and WinRT APIs in scope and explicitly excludes Mingw and shell fallbacks.
- Existing user modifications are not reverted or staged implicitly.
