# Compose WinUI P1 Runtime Gaps Design

## Goal

Make the WinUI JVM target behave like a real Compose platform for the four
highest-impact gaps found in the static audit: graphics layers, dialogs and
popups, drag-and-drop/content reception, and accessibility update delivery.

This work must preserve the existing `WinUIOwner`/`WinUIComposeView` ownership
model and the local `compose/ui/ui/winui-samples` smoke path.

## Scope

### In scope

- Apply the graphics-layer state used by `Modifier.graphicsLayer` to the
  WinUI owner layer, including alpha, clipping shape, color/filter/blend
  state, render effects, shadow state, and layer outsets where the existing
  Canvas/Skia abstractions support them.
- Make `Dialog` and `Popup` honor their dismiss callbacks and supported
  properties. Dialog content must be hosted separately from the parent root
  so it is modal and does not remain an ordinary child layout. Popup must
  route back and outside-dismiss events through the supplied callback and use
  the requested width/clipping policy.
- Convert WinUI drag data into Compose `ClipEntry`, `ClipMetadata`, and
  `TransferableContent` values using Windows Runtime data-package APIs. Accept
  plain-text drops in text fields and expose a production drag-source starter
  through the existing WinUI drag manager boundary.
- Deliver accessibility structure/layout/scroll changes to the WinUI/Skiko
  accessibility host in production while retaining the existing batched test
  controls.
- Add or extend WinUI JVM tests for each behavior before implementation.

### Out of scope for this increment

- WinUI Mingw support or full kotlin-winrt projection generation.
- Native Autofill service integration.
- The separate P2 backlog: full system/IME insets, dynamic RTL/theme,
  InputPane show/hide, resource-font loading, haptics, touch-mode/emoji UI,
  selection magnifier/cursor handles, Skia paragraph geometry gaps, and
  Material3 polish items.

## Architecture

### Graphics layers

`WinUIOwner.createLayer()` continues to return `WinUIOwnerLayer`, but the layer
will retain the explicit `GraphicsLayer` when supplied and copy all supported
`ReusableGraphicsLayerScope` values into a single layer-state object. Drawing
will apply transforms, alpha, clipping shape, color/filter/blend state, render
effect and shadow state in the existing Canvas path. Hit testing will use the
same outline/clip geometry as drawing. Unsupported native effects must degrade
to the closest Compose/Skia behavior without changing layout or input bounds.

### Dialogs and popups

Popup keeps its current two-host strategy: canvas-layer popups remain in the
owner, while `LayerType.OnWindow` uses the existing transparent WinUI Flyout
host. Both paths receive a stable `onDismissRequest` callback and update their
properties through `SideEffect`. The Flyout host will use light-dismiss and
key/focus handlers only when the corresponding property is enabled.

Dialog will use a dedicated transparent WinUI popup/flyout host built on the
same `WinUIComposeView` ownership primitives. It will size content according
to `usePlatformDefaultWidth`, center it in the owner window, block pointer/key
events from the parent while open, and invoke `onDismissRequest` for enabled
back/outside actions. Disposal must close the host and dispose its Compose
view exactly once.

### Drag and drop/content

The WinUI adapter remains the only native event boundary. A WinUI data-package
adapter will expose the available MIME-like formats and asynchronously read
plain text through `DataPackageView.getTextAsync()`. `MediaType` retains the
requested representation string, and `hasMediaType()` compares the actual
transfer metadata. Text-field drag nodes will reject unsupported formats,
request permission through the existing callback, report the event position,
and call the supplied drop handler for accepted text.

Outbound drag operations will be injected into `WinUIDragAndDropManager` from
`WinUIComposeView`/the root adapter. The implementation must use the WinRT
drag API already projected by kotlin-winrt; no process-shell fallback is
allowed. Test-only starter injection remains available for deterministic unit
tests.

### Accessibility

The existing `WinUIAccessibilityBridge` remains the semantics snapshot and
action source. Its scheduler will flush production updates when a provider is
attached, not only when the testing force flag is set. Batching interval,
pending-change coalescing, disposal, and test state inspection remain
unchanged. The render host receives the resulting change notification so UI
Automation can refresh structure, layout, and scroll state.

## Error handling and lifecycle

- WinRT calls that can fail during teardown or before a XAML root is loaded
  stay guarded with `runCatching`, but failures must not silently invoke a
  dismiss callback or leave a retained Compose view.
- Native hosts remove every event registration before disposal and are
  idempotent on repeated close/dispose calls.
- Data-package reads return an empty/unsupported transfer result when the
  format is unavailable; they must not throw from pointer event dispatch.
- Every workaround for kotlin-winrt behavior must receive a `KWINRT-###`
  entry in `kotlin-winrt-issues.md` and a reference at the narrow workaround
  site.

## Testing and verification

1. Add failing WinUI JVM tests for graphics-layer state/hit testing, dialog and
   popup dismiss/property routing, plain-text data-package conversion and
   text-field drop handling, and production accessibility flush behavior.
2. Run each focused test task and confirm the tests fail for the missing
   behavior before adding production code.
3. Implement the smallest code change that makes the focused tests pass, then
   run the affected test classes again.
4. Compile `compose-ui` for the WinUI JVM target with `--no-configuration-cache`.
5. Run the repository-local `compose/ui/ui/winui-samples` sample with
   `--no-configuration-cache` and confirm the existing `WinUIView` smoke path
   still renders.
6. Inspect `git diff` and keep `AGENTS.md`, `.agent_tmp/`, and unrelated user
   changes out of commits.

## Success criteria

- `Modifier.graphicsLayer` no longer silently drops the supported visual
  properties on WinUI.
- Dialogs and popups invoke their callbacks for enabled dismissal paths and do
  not leave parent content interactive through a modal dialog.
- Plain-text drag-and-drop reaches Compose content and text fields can accept
  it; outbound drag requests reach a real WinRT starter when configured.
- A semantics/layout/scroll change reaches the WinUI accessibility host in a
  normal production composition.
- Focused tests, the WinUI JVM `compose-ui` compile, and the local WinUI sample
  all pass under the repository's no-configuration-cache requirement.
