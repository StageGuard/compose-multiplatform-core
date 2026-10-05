# Compose WinUI Compose-Only Backlog Design

## Goal

Close the remaining compose-winui JVM gaps that can be implemented and
validated entirely in the Compose repository, without requiring a new Skiko
ABI, new Skiko event fields, or WinUI Mingw support.

The work is split into four independently reviewable and testable increments:

1. system environment and lifecycle;
2. direct pointer and keyboard state;
3. native surfaces and text interaction;
4. Material3 platform correctness.

`IndirectPointerInput` is part of the completed baseline. Its Compose mapping,
scene dispatch, focus cancellation, and WinUI host binding already exist; only
real precision-touchpad hardware validation remains as validation debt.

## Repository Constraints

- Keep compose-winui as a standalone WinUI platform target. Do not route new
  behavior through Desktop, AWT, Swing, or Skiko Desktop APIs.
- Keep shared WinUI behavior in `winuiMain`; use `winuiJvmMain` only when a JVM
  implementation is unavoidable.
- Do not enable, compile, or validate the WinUI Mingw application target.
- Declare every new WinRT projection through explicit `type(...)` entries.
- Do not introduce process-shell fallbacks for Windows platform behavior.
- Preserve all unrelated dirty-worktree changes.
- Use `git.exe` for status, staging, and commits.
- Validate WinUI tasks with `--no-configuration-cache`.
- After every verified implementation increment, create a focused local commit.

## Scope

### In scope

#### Increment 1: System environment and lifecycle

- Replace the constant `SystemTheme.Unknown` root behavior with an observable
  effective WinUI theme.
- Initialize the owner layout direction from the effective WinUI/XAML flow
  direction instead of forcing LTR.
- Apply the Windows text scale factor to `Density.fontScale` and react to text
  scale changes.
- Map the Windows animations setting to a mutable Compose
  `MotionDurationScale` used by the root recomposer.
- Replace the permanently resumed lifecycle with a per-view lifecycle derived
  from loaded/visible/activated state.

#### Increment 2: Direct pointer and keyboard state

- Keep the existing XAML routed-event adapters as the production direct-input
  path. This preserves the native `PointerPoint` required by WinUI drag start
  and avoids a Skiko ABI change.
- Aggregate active contacts so Compose receives all active pointers in each
  direct pointer event instead of a one-element list.
- Populate pressure and historical samples from WinUI pointer data.
- Distinguish mouse, touch, touchpad, and stylus behavior when selecting
  `InputMode`, hover state, and pointer type.
- Apply stylus hover icons while a pen is in range and restore the normal
  pointer icon when stylus hover ends.
- Keep `WindowInfo.keyboardModifiers` synchronized from pointer and key input,
  including lock-key state when it can be queried through WinUI input APIs.
- Expand the WinUI virtual-key mapping for punctuation, browser, media, volume,
  navigation, and other Compose `Key` values exposed by Windows.

Key events continue to use `utf16CodePoint == 0`. WinUI reports committed text
through `CharacterReceived` and CoreText after key routing; synthesizing a
layout-dependent character during `KeyDown` would duplicate or corrupt the
existing text/IME path. Text entry remains owned by CharacterReceived/CoreText.

#### Increment 3: Native surfaces and text interaction

- Introduce one tested physical-pixel to XAML-DIP conversion boundary.
- Keep Compose owner constraints and render sizes in physical pixels.
- Convert only native XAML dimensions and positions used by `Popup`, `Dialog`,
  and text toolbar flyouts to DIPs.
- Show `WinUITextToolbar` at the supplied selection rectangle using
  `FlyoutShowOptions.position`.
- Implement the WinUI cursor handle with the existing Skiko `HandlePopup`
  infrastructure and Compose drawing/semantics.
- Replace the deprecated `Font.ResourceLoader` placeholder with the existing
  Skia-backed font loader used by the WinUI text target.

#### Increment 4: Material3 platform correctness

- Correct `ModalWideNavigationRailProperties.equals()` so it compares the
  right type and property.
- Implement precision-pointer component sizing detection from Windows keyboard
  and mouse capabilities, while preserving the existing Material3 opt-in flag.

### Frozen because they require new Skiko capability

- Additional UI Automation actions and request payloads beyond the current
  focus, click, expand, collapse, increment, decrement, and set-text ABI.
- UIA Scroll, Selection, SelectionItem, and richer Text pattern operations that
  require corresponding Skiko provider support.
- Input features that require new fields in Skiko's public WinUI event model.
- Any Compose WinUI Mingw runtime or sample work.

Frozen items are documented but must not acquire provisional Compose-only
stubs or alternate rootless implementations.

### Deferred platform limitations

These items do not enter the four implementation increments:

- Native system Autofill UI: Windows has no general WinUI custom-control
  equivalent of Android's AutofillManager.
- General desktop haptics: Windows does not expose a universal haptic device for
  arbitrary desktop UI feedback.
- System emoji-panel invocation: there is no stable WinUI/Windows Runtime API.
- Deprecated synchronous clipboard reads of external text: Windows clipboard
  text retrieval is asynchronous; the suspend `Clipboard` API remains the
  canonical path.
- CoreText rich `FormatUpdating`: Compose text-field state has no rich-format
  model for the requested color/background/underline ranges.
- Text magnifier and platform overscroll: both require a separate custom visual
  design and are not production-correctness blockers for this batch.
- Material3 accessibility-service state detection: the current UIA surface has
  no reliable observable service-state signal. It remains false until a stable
  signal and update mechanism are designed.

## Architecture

### 1. System environment observer

Add a disposable WinUI environment observer owned by `WinUIComposeView`. It
wraps platform event registration behind a small testable adapter and publishes
four values:

- effective theme;
- effective layout direction;
- text scale factor;
- animations enabled.

The effective theme and flow direction come from the XAML root so embedded
Compose content follows an explicit native host override as well as the default
system setting. Windows `UISettings` supplies text scale and animation state.
All event callbacks are marshalled onto the root dispatcher before mutating
Compose state.

`WinUIOwner` gains focused update functions for density and layout direction.
Physical density remains the XAML rasterization scale; font scale comes from
the environment observer. Updating either value refreshes root constraints,
window DP size, layout, and rendering through the existing owner path.

A mutable WinUI `MotionDurationScale` element is added to the root
`FrameRecomposer` context. Popups and dialogs that inherit the parent
composition context inherit the same scale automatically.

### 2. Lifecycle state machine

`WinUIComposeView` owns a small lifecycle state machine with these mappings:

- disposed: `DESTROYED`;
- not loaded or not visible: `CREATED`;
- loaded and visible but inactive: `STARTED`;
- loaded, visible, and active: `RESUMED`.

Window-owned views receive activation and visibility updates from
`WinUIWindowNode`. Embedded views use their XAML loaded/unloaded state and may
receive host focus/activation updates through the existing view boundary. A
single recompute function applies transitions so event order cannot leave the
lifecycle in a stale state.

### 3. Direct pointer state tracker

Add a pure Kotlin pointer state tracker between `WinUIPointerInputAdapter` and
`WinUIOwner`. The tracker retains the latest state for every active pointer ID.
For press/move/release it creates a `PointerInputEvent` containing:

- every currently active pointer;
- the changed pointer, including its released state for the release event;
- native pressure;
- historical samples returned by `PointerRoutedEventArgs.getIntermediatePoints`;
- device-appropriate hover state.

The changed pointer is removed only after its release event has been
dispatched. Cancel, capture loss, disposal, and window deactivation clear the
tracker and dispatch Compose cancellation exactly once.

The adapter continues retaining the current native `PointerPoint` for the
existing drag-and-drop source path.

### 4. Keyboard state

Refactor modifier tracking into a reusable value-producing component. Pointer
events can replace stale pressed-modifier state with the native routed-event
flags. Key events update pressed state before dispatch and release state after
dispatch. Window focus loss clears pressed keys.

Where supported, `Microsoft.UI.Input.InputKeyboardSource` supplies current
lock-key state. The resulting `PointerKeyboardModifiers` is written to
`WindowInfoImpl.keyboardModifiers` and attached to pointer/key events.

Virtual-key conversion remains a pure mapping function with focused table
tests. Unknown Windows values continue to map to `Key.Unknown`.

### 5. Native coordinate boundary

Create shared conversion helpers that accept a physical value and a validated
rasterization scale. Invalid or non-positive scales fall back to `1f`.

The coordinate contract is:

- Compose layout, owner bounds, input positions, and rendering: physical pixels;
- XAML `Width`, `Height`, and `FlyoutShowOptions.position`: DIPs.

`Popup`, `Dialog`, and `WinUITextToolbar` use only these helpers. Tests at 1x,
1.5x, and 2x scale prevent later code from mixing coordinate spaces again.

### 6. Text interaction and compatibility loader

The cursor handle is a Compose popup anchored by the supplied
`OffsetProvider`. It exposes `SelectionHandleInfoKey`, respects the requested
minimum touch target, and draws with `LocalTextSelectionColors`.

The deprecated owner font loader delegates to the Skia-backed `FontLoader`
rather than returning an unrelated object. Unsupported font implementations
retain the loader's existing explicit exception behavior.

### 7. Material3 platform signals

Precision-pointer detection is initialized only when
`ComposeMaterial3Flags.isPrecisionPointerComponentSizingEnabled` is true.
Windows `MouseCapabilities` and `KeyboardCapabilities` determine whether dense
precision-pointer sizing is appropriate. The composition-local re-entry guard
matches the Android implementation so nested themes do not register duplicate
work.

## Error Handling and Disposal

- Platform event registration failures fall back to the last valid/default
  value without preventing the Compose root from rendering.
- Event registrations are removed before their backing WinRT objects are
  released.
- Environment, input, toolbar, and popup callbacks must ignore events after
  disposal.
- Pointer cancellation clears all retained contacts even if native capture
  release fails.
- No asynchronous Windows operation is synchronously blocked on the UI thread.

## Testing

Each increment starts with focused failing tests and ends with its own verified
commit.

### Unit and component tests

- Fake environment adapter tests for theme, flow direction, text scale,
  animations, event updates, thread dispatch, and disposal.
- Lifecycle transition table tests for loaded/visible/active/disposed inputs.
- Pointer tracker tests for two- and three-contact sequences, release ordering,
  cancellation, pressure, history, hover, and capture loss.
- Keyboard tests for modifier synchronization, lock states, focus reset, and
  expanded key mappings.
- Coordinate tests for 1x, 1.5x, and 2x Popup/Dialog/toolbar conversion.
- Cursor handle semantics and placement tests.
- Font loader compatibility tests using supported and unsupported font values.
- Material3 equality and precision-pointer capability tests.

### Required WinUI validation

For every increment, run the focused tests and then:

```powershell
.\gradlew.bat :compose:ui:ui:compileKotlinWinuiJvm `
  :compose:ui:ui:winuiJvmTest `
  --no-configuration-cache --console=plain
```

Run the repository-local sample relevant to the increment. The final aggregate
validation must include:

```powershell
.\gradlew.bat :compose:ui:ui:winui-samples:runWinUISkikoSample `
  --no-configuration-cache --console=plain
```

Text and InputPane-sensitive changes also run the repository-local WinUI text
input sample. Hardware-only checks such as real multi-touch, pen pressure,
stylus hover, OS text-scale changes, reduced motion, RTL, and precision
touchpad input are recorded as manual pass or skipped; they are never inferred
from a synthetic smoke marker.

## Delivery Order

1. System environment and lifecycle.
2. Direct pointer and keyboard state.
3. Native surfaces and text interaction.
4. Material3 platform correctness.

Each increment receives its own implementation plan, tests, validation, and
one or more focused commits. A failure in a later increment must not require
reverting an earlier completed increment.

## Success Criteria

- Compose content follows effective WinUI theme, flow direction, text scale,
  reduced-motion setting, and window lifecycle.
- Direct touch and pen events preserve all active contacts, pressure, history,
  hover, and current modifiers.
- Mouse/touch/stylus input selects the expected input mode and cursor behavior.
- `WindowInfo.keyboardModifiers` reflects native modifier and lock-key state.
- Popup, Dialog, and text-toolbar XAML coordinates remain correct above 100%
  display scaling.
- Touch text selection displays a functional cursor handle.
- Deprecated font-loader consumers receive a real Skia typeface or an explicit
  unsupported-font error.
- Material3 equality and precision-pointer sizing behave correctly.
- No frozen Skiko-dependent work, WinUI Mingw work, or unrelated dirty-worktree
  change is included.
