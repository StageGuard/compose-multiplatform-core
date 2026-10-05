# compose-winui implementation plan

## Native demo against the desktop demo 2026-10-05

The MPP demo of the native WinUI target (`winuiMingw`) was compared with the
desktop (AWT) demo and with the WinUI JVM demo, which had been aligned with the
desktop one before (see "Desktop parity").

Method: every leaf screen of the demo (105) is opened by its name in a
1280 x 900 px client area at 150 % scale and captured with `PrintWindow`, on
each target; the captures are compared pixel by pixel.

Found and fixed:

- [x] The native entry was not the demo that the other targets run: it wrapped
  the content in a dark or light `MaterialTheme`, extended it into the title
  bar, had the default window size and ignored the command line. It is the
  entry of the desktop demo now (`Main.winuiMingw.kt`); the two fonts of the
  demo are staged next to the executable, and the emoji font is preloaded.
- [x] `Dispatchers.Main` did not exist in a native application
  (kotlinx.coroutines has none on Windows), so `collectAsStateWithLifecycle`
  and `repeatOnLifecycle` threw and ended the process: the WindowFocusDemo
  screen. `WinUIMainDispatcher` is shared now and injected on the native
  target when the dispatcher queue is registered.
- [x] Material 3 date picker on the native target: the weekday header showed
  the shortest WinRT abbreviation ("Su", "Mo") instead of the narrow name ("S",
  "M"), and formatted dates carried the direction marks of the WinRT formatter
  (U+200E), which made "October 2026" a pixel wider.

Result:

- [x] Native against WinUI JVM: no differing pixel on 95 of the 105 screens.
  Nine of the others differ on every pair of runs of one target as well (random
  colours of the lazy lists and pagers, the mesh gradient and the progress
  indicator, which are animations, a counter that runs with time), and one is
  the resource placeholder (`VectorPainter inside another Painter`).
- [x] Native against desktop: the same differences as WinUI JVM against
  desktop, which "Desktop parity" lists (text rasterization of AWT, the
  screens that the WinUI demo replaces).
- [x] Release executable (`linkReleaseExecutableWinuiMingw`, 15 minutes) against
  the debug one: the same captures.
- [x] Window: the initial size (1024 x 850 dp), maximize, restore, minimize and
  a size change give the same client sizes and the same captures on the three
  targets. Closing the window ends the three processes with exit code 0.
- [x] Idle on the main screen for ten seconds: 16 ms of CPU time on desktop,
  31 ms on WinUI JVM, 47 ms native; working set 291 MB, 384 MB and 200 MB.
- [x] `winuiJvmTest` of `ui` after the move of the dispatcher: 360 tests pass.

Real input (2026-10-06). The same scripted mouse and key input (`mouse_event`,
`keybd_event`) was sent to the native demo (debug and release), the WinUI JVM
demo and the desktop demo, and the captures after every step were compared.

- [x] Identical captures on native and WinUI JVM, and the same result as on
  desktop: drag and drop between the two boxes (drag image, drop), modal
  bottom sheet, navigation drawer, dropdown menu with the arrow keys and
  Escape, the wheel over a scrolling column (one notch, three more, two back),
  typing into a text field, select all, Ctrl+Shift+Left, copy, paste, undo,
  paste of text from another application, Home, Delete, End, Backspace, cut,
  double click on a word and a mouse drag over static text with Ctrl+C,
  focus and typing in the two boxes of a dialog with Tab and Escape, the
  window focus state, the date picker (a day, the next month, the year list,
  a year, the input mode, a typed date).
- [x] The clipboard has the same text after every copy and cut on the three
  targets.
- [x] Cursors over the areas of the PointerIcon screen and over a text field:
  arrow, cross, I-beam, hand on the three targets (`GetCursorInfo`).
- [x] The context menu of a text field opens on the right click. On WinUI it
  is a `MenuFlyout` of the system in its own window, on both WinUI targets,
  where desktop draws a Compose menu; that is the design of "Desktop parity".
- [ ] Not exercised: the pagers did not turn a page with the scripted wheel
  or drag on any of the three targets, so they say nothing; touch, pen and IME
  input, which cannot be injected this way.

Found and fixed with real input:

- [x] The process ended with `IndexOutOfBoundsException` when the year list of
  the date picker opened after a date had been selected.
  `WinUIOwner.onEndApplyChanges` removed as many listeners as it had counted,
  and a listener that measures a lazy layout applies changes on the same owner
  and has removed them already. Shared WinUI code; the native target met it.
  Test: `WinUIOwnerTest.endApplyChangesInsideAListenerRunsEveryListenerOnce`.
- [x] The headline of a selected date was "14-Oct-26" on the native target and
  "Oct 14, 2026" on the JVM: a WinRT formatter applies the date formats of the
  regional settings of the user. The native Material 3 date format takes the
  patterns of the locale without those (`GetLocaleInfoEx`).
- [x] A date typed in the input mode of the date picker was always rejected on
  the native target: the parser took a pattern without delimiters for one
  field.
- [x] After a copy, a native application whose window was closed within a few
  seconds never ended: `KWINRT-081` in `kotlin-winrt-issues.md`, a deadlock of
  the finalizer drain of the native kotlin-winrt runtime with the UI thread.
  Compose releases its clipboard projections on the UI thread now, which
  avoids it for the clipboard; the runtime problem is open.

Differences that stay:

- `KWINRT-081`: any other object whose last release calls into the UI thread
  can still keep a native process from ending.
- Both WinUI targets against desktop, seen in these checks: `Key.toString()`
  is "Key(25)" where desktop says "Key: B" (the FocusAndKeyInput screen prints
  it), and the drag image has the "Copy" badge of the system.
- An exception that nothing catches (a route that does not exist, for example)
  ends a native process; the JVM targets print it and go on.
- The screen `VectorPainter inside another Painter` is a placeholder in a build
  with the MinGW flag (no Compose resources artifact for mingwX64).
- The demo entry of the JVM target has the validation hooks of the sample tasks
  (`compose.winui.mpp.sample.*`); the native entry has none.
- No test covers the fixes in native-only sources (the entry, the main
  dispatcher, the date format): the native target has no test compilation
  (`winuiMingwTest` is not wired, `:compose:ui:ui-test` has no MinGW target).

## WinUI MinGW target 2026-10-05

The fork build has the native WinUI target of `winui_dev` now: with
`-PcomposeWinUi.enableMingwTarget=true` (next to
`-PcomposeWinUi.enableJvmTarget=true`) the modules that the demo needs get a
`mingwX64` target, and `:compose:mpp:demo-winui` gets a native application.
`winuiMain` is the source set that the JVM and the native target share, as
`winui_dev` designed it; no file of it had to move.

Build:

- [x] Plain `mingwX64()` in `ui-util`, `ui-geometry`, `ui-unit`,
  `ui-backhandler`; `mingwX64("winuiMingw")` on the native source sets in
  `ui-graphics`, `ui-skiko`, `animation-core`, `animation`, `material-ripple`,
  `material`, `material3-ripple`, `material3-window-size-class`, the three
  `adaptive` modules and `navigation-compose`; with `winuiMain` as well in
  `ui-text`, `ui`, `foundation-layout`, `foundation` and `material3`.
- [x] `ui`, `foundation` and `material3` keep the Skiko actuals of the other
  non-JVM targets (`skikoNonJvmMain`, `skikoNativeMain`) apart from `nonJvmMain`
  and `nativeMain`, which the WinUI native target shares. One source set cannot
  both have and not have them, so a build with the flag leaves them out, as
  `winui_dev` does: the Darwin and web targets of these three modules do not
  compile in that build.
- [x] `navigation-common` and `navigation-runtime` redirect every other target
  to the published artifact. No published navigation artifact has a mingwX64
  variant, so this one target is compiled from the sources, with the source sets
  and dependencies of `build.gradle`. `nativeMain` has the POSIX mutex (in a
  `posixMain` on `winui_dev`); it is excluded from this compilation, which has
  the one of `mingwX64Main`.
- [x] Dependencies without a mingwX64 variant, handled for the configurations of
  a MinGW target in `buildSrc-fork` (`WinUiMingwDependencies.kt`): the JetBrains
  `navigationevent`, `navigationevent-compose` and `window-core` are replaced by
  the androidx artifacts that they redirect to, and a pinned published Compose
  module (`org.jetbrains.compose.ui:ui:1.10.0`) by its project.
- [x] Two problems of the fork build logic that only a native target of a
  kotlin-winrt module meets: the Compose compiler plugin replaced the compiler
  plugins of a native compilation instead of joining them, which dropped the
  kotlin-winrt one (`AndroidXComposeImplPlugin`); and the license step expected
  an unpacked klib, while the projection compilation of kotlin-winrt packs its
  klib (`AddLicenses`).
- [x] Demo: `mingwX64("winuiMingw")` with an executable, `winuiMain` as a real
  shared source set, the native main function for the generated entry, and the
  Skia bridge DLLs and ICU data of skiko-winui (`skiko-winui-mingw-runtime`,
  `skiko-winui-windows`) as runtime assets of the application. The Material
  icons and Compose resources have no mingwX64 artifact, and common sources
  cannot use what one target lacks: in a build with the flag both applications
  compile the icons from the sources jar (as on `winui_dev`) and show a
  placeholder for the one screen that draws a resource
  (`VectorPainterInPainter`). A JVM-only build is unchanged.

Sources:

- [x] Actuals that only the JVM target had, added for MinGW with the Windows
  bindings of Kotlin/Native: `getCurrentThreadId`, `clipboardSequenceNumber`,
  `winUIKeyCodePoint`, `isWindowMinimized`, `windowDpiScale` (`ui`),
  `systemWheelScrollLines` (`foundation`) and `DefaultNavTransitions`
  (`navigation-compose`). `GetDpiForWindow` is not in those bindings and is
  looked up in user32 at run time.
- [x] `WinUIScheduler` imports `kotlin.concurrent.Volatile`; the JVM resolved the
  annotation without it.
- [x] Material 3 projects the WinRT calendar, date and number formatting types
  for the native target only; its JVM target keeps the JVM ones.
- [x] `PointerIconExample.winui.kt` of the demo is in `winuiMain`.

Verification:

- [x] `compileKotlinWinuiMingw` / `compileKotlinMingwX64` of all of the modules
  above and of the demo; `linkDebugExecutableWinuiMingw` of the demo.
- [x] The staged native demo (`stageWinAppPackageWinuiMingwMainDebugExecutable`,
  `stageWindowsPackageRuntimeAssetsWinuiMingwMainDebugExecutable`) starts and
  draws its first screen.
- [x] Without the flag: the WinUI JVM compilation, `winuiJvmTest` (`ui` 360,
  `ui-graphics` 173, `foundation` 12, `ui-text` 6, `material3` 6), the eleven
  sample run tasks and `runWinUIMppSample`; in the default mode (JDK 21)
  `compileKotlinDesktop` of `ui`, `ui-text`, `foundation`, `material3` and
  `navigation-compose`. With the flag: `compileKotlinWinuiJvm` of the demo.
- [ ] Not verified: anything in the native demo beyond its first screen (no
  input, no traversal of the screens, no comparison with the JVM demo); the
  release executable; the native tests (`winuiMingwTest` is not wired, and
  `:compose:ui:ui-test` has no MinGW target); `navigation3` and the WinUI
  samples of `ui`, which have no native variant; the Darwin and web targets,
  which cannot be built on this machine.
- [x] The native entry (`Main.winuiMingw.kt`, from `winui_dev`) wrapped the
  demo in a dark or light `MaterialTheme` and extended the content into the
  title bar; it is the entry of the desktop demo now (see above).

## kotlin-winrt fixes after the merge 2026-10-05

The four kotlin-winrt problems that the merge of `winui_dev` worked around
(`KWINRT-077` to `KWINRT-080`) are fixed in kotlin-winrt (https://github.com/compose-fluent/kotlin-winrt/pull/18), and the
workarounds are gone. compose-winui is at the code of `winui_dev` in these
places again.

- [x] Builds against kotlin-winrt `xaml-support` `cf66ec5ba` in Maven Local:
  `origin/xaml-support` `9d93cbfb9` with the fix of PR #17 and the four fixes.
  skiko-winui (`fix/winui-post-m154-sync`, the tree of `winui_dev`) was
  rebuilt against it, its mingwX64 projection included, without a change.
  Its projection now owns `winrt.interop.WindowNative` and
  `InitializeWithWindow`, which the generation task used to leave out
  (`KWINRT-077`); `ui` takes them from there.
- [x] `KWINRT-077`: `winuiWindowHwnd` and `acquireWinUIInputPane` use the
  generated `WindowNative.getWindowHandle` and `InputPaneInterop.getForWindow`.
  The `IWindowNative` and `IInputPaneInterop` calls that `ui` made itself are
  removed, and `WinUISourceSetIsolationTest` asserts the generated helpers
  again.
- [x] `KWINRT-078`: `ui-text` applies the projection plugin. The WinRT `Locale`
  of `winui_dev` is in `winuiMain` for both WinUI targets; the JVM `Locale`
  that the JVM target had kept (`PlatformLocale.winuiJvm.kt`) is removed.
  Material 3 is not changed: its JVM target keeps the JVM calendar locale and
  date format, which was a parity decision of the merge and not a workaround.
- [x] `KWINRT-079`: `WinUITestRuntime` no longer halts the test worker.
- [x] `KWINRT-080`: the view sample reads the root from `window.content`.

Verification (JDK 25, `-PcomposeWinUi.enableJvmTarget=true`):

- [x] `compileKotlinWinuiJvm` of `ui`, `ui-text`, `foundation` and
  `material3`; `compileTestKotlinWinuiJvm` of `ui` and `ui-text`, the task
  graph that `KWINRT-078` broke.
- [x] `winuiJvmTest`: `ui` 360 tests, `ui-graphics` 173 (4 skipped),
  `foundation` 12, `ui-text` 6, `material3` 6. The worker of `ui` exits by
  itself.
- [x] The eleven run tasks of `:compose:ui:ui:winui-samples` and
  `:compose:mpp:demo-winui:runWinUIMppSample`.
- [x] Default mode (JDK 21): `compileKotlinDesktop` of `ui-text` and `ui`
  with the changed `compose/ui/ui-text/build-fork.gradle`, whose WinUI block
  is behind the WinUI flag. No other changed file belongs to that mode.
- [ ] Not verified then: the MinGW target, which the fork build did not wire
  yet (see above; the shared `winuiMain` locale compiles for it); the
  `winuiJvmTest` task of skiko-winui, which fails here with
  `UnsatisfiedLinkError` for the Skia natives in 266 of 345 tests (not
  examined; whether it did so before was not checked).

## Merge of `winui_dev` 2026-10-05

`winui_dev` (`49ecfa1c091`, 92 commits since the base of the `jb-main` sync)
was merged into the sync. Both sides had changed the same WinUI code: the sync
for desktop parity (below), `winui_dev` for new platform features and a
MinGW target. Where both had a fix for the same thing, the merge kept the one
that does more, or both when they cover different cases.

Build:

- [x] `winui_dev` wires WinUI in the files of the old build layout
  (`build.gradle`, `settings.gradle`, `buildSrc`). Those files are `jb-main`'s
  in the sync and stay so; the JVM-target changes of `winui_dev` are ported to
  `build-fork.gradle`: the `skikoHostMain`, `skikoNativeMain` and
  `skikoNonJvmMain` source sets, `winuiMain` source sets in
  `foundation-layout` and `material3`, the projection of the WinRT input
  device types in `material3`, the staged Windows App SDK runtime for
  `:compose:ui:ui:winuiJvmTest`, the title bar options and the JVM profile of
  the sample hosts.
- [x] kotlin-winrt stays the `xaml-support` build from Maven
  (`COMPOSE_WINUI_MAVEN_REPO`) with the toolkit plugin. The
  `external/kotlin-winrt` submodule of `winui_dev` pins a commit that is not in
  `compose-fluent/kotlin-winrt` and is not merged.
- [ ] **The MinGW target of `winui_dev` is not wired in the fork build.** Its
  sources are merged (`winuiMingwMain` directories,
  `navigation-common/src/mingwX64Main`, `ui-util/src/mingwX64Main`) and its
  source moves are kept (`skikoNativeMain`, `skikoNonJvmMain`), but no
  `build-fork.gradle` declares `mingwX64("winuiMingw")`. What is missing:
  the target and its `winuiMingwMain` source sets in every module; Compose
  runtime, lifecycle, savedstate and navigation for `mingwX64` (`winui_dev`
  redirects to other artifacts per target in the old `buildSrc`, the fork
  redirects per module through `redirectversions.toml`); and actuals for the
  JVM-only expects that the desktop parity work added (the code point of a
  key, the wheel scroll lines, the DPI scale and the minimized state of a
  window, the clipboard sequence number, the main dispatcher). The
  properties `composeWinUi.cmpVersion` and
  `kotlin.mpp.applyDefaultHierarchyTemplate=false` of `winui_dev` belong to
  that wiring and are not merged; the second one changes every module of the
  fork build.
- [x] Three kotlin-winrt problems came up with the code of `winui_dev` on
  `xaml-support`: `KWINRT-077` (the generation task does not generate
  `WindowNative` and `InputPaneInterop`; `ui` makes the two COM calls itself),
  `KWINRT-078` (the plugin cannot be applied to `ui-text` on the `jb-main`
  module graph; its JVM target keeps the JVM `Locale`) and `KWINRT-079` (the
  shutdown hook blocks the exit of the test worker; the tests end it). All of
  them, and `KWINRT-080`, were fixed in kotlin-winrt later (see above). The
  issue numbers of the sync moved to `KWINRT-065` to `KWINRT-076`; see
  `kotlin-winrt-issues.md`.
- [x] The sample hosts keep the low-footprint JVM profile of `winui_dev` with
  bounds measured on this demo (64 MB heap, 96 MB metaspace, 32 MB code
  cache). With the bounds of `winui_dev` (32, 44 and 8 MB) the demo of the sync
  ends with `OutOfMemoryError: Metaspace`: its 105 screens use 63 MB of
  metaspace.
- [x] `runWinUIMppSample` compares the title bar insets with the title bar of
  the `AppWindow` instead of with 276 pixels, which is the width of the
  caption buttons at 200 % display scale only.

Sources, by area (kept side and why):

- [x] Owned layers: the sync. `WinUIOwner` uses the Skiko
  `GraphicsLayerOwnerLayer`; `WinUIOwnerLayer` of `winui_dev` is a second
  implementation of it without the layer manager. `skikoHostMain` of
  `winui_dev` (frame recomposer, dispatchers, snapshot manager) is below
  `skikoRenderingMain` of the sync (owned layers), and `winuiMain` depends on
  the latter.
- [x] Popup and Dialog: the sync for the default (canvas) path, a layer above
  the content like the Skiko scene layers, which is what makes z-order,
  focus, outside presses, the scrim and the animations match the desktop
  target; `winui_dev` for `LayerType.OnWindow`, a native flyout, with its
  dismiss state, DIP conversion and parent composition context.
- [x] Pointer input: `winui_dev` as the base. Its pointer state tracker reports
  every contact of an event, historical points, the eraser, pen hover and
  capture, and closes the projections of a callback. Added from the sync: the
  host hooks around an event, horizontal scrolling with Shift, a scroll that
  does not end a press, pressure `1` for a pointer without pressure, and hover
  of a pressed mouse as on Skiko. The contact tracking of the sync is replaced.
- [x] Keys: `winui_dev` as the base (shared modifier state, reset on focus
  loss, media and browser keys). Added from the sync: the hook that lets a
  popup layer and back handling see a key first, `NumPadEnter`, and the code
  point of a key. A pressed key with a code point is no typed event: typed
  characters come from `CharacterReceived` or the text input session, as on
  `winui_dev`, after dead keys and input methods. With the key events of the
  sync and the typed event rule of the base, a text field inserted a character
  for the key down and again for the key up.
- [x] Drag and drop: `winui_dev` as the base (content transfer through
  `PlatformDragAndDropData`, asynchronous text with a deferral, session
  cleanup, a drag image written with `DataWriter`, which is portable). Added
  from the sync: the transfer actions (`supportedActions`, the action from the
  modifier keys, the action of the drop as the result), the decoration offset
  and scale, `onTransferCompleted`, and offering a drop only over a target that
  takes it. The FFM bitmap writer of the sync and its test are gone.
- [x] Compose view: both. From `winui_dev` the environment (theme, layout
  direction, font scale, animations), the view lifecycle, the root size
  binding, the indirect pointer bridge, the input pane and the title bar
  insets; from the sync the layer host, back handling, hover after layout,
  the frame background and `Window` with `WindowState`. A minimized window
  moves the lifecycle of `winui_dev` to `CREATED`, as the sync did. The layout
  direction is right to left for a right-to-left XAML tree (`winui_dev`) and
  for a right-to-left locale (the sync, as on the desktop target).
- [x] Input pane: `winui_dev`, with one change. Every Compose view of a window
  gets the same projection of the input pane of that window, so a view does
  not close its reference when it is disposed; a window popup closing it broke
  the handlers of the window.
- [x] Demo: the validation events and title bar options of `winui_dev` with
  the entry of the sync, which has no theme around the content, like the
  desktop demo. The `MaterialTheme` that `winui_dev` puts around it (dark with
  a dark system) changes the colors and the text sizes of every screen.
- [x] Coordinates: `winui_dev` (the same DIP conversion with shared helpers,
  and `WinUIView` is measured again when the density changes).
- [x] Dispatcher: `winui_dev` (always dispatches to the queue).
- [x] Text: the JVM `Locale` of the sync on the JVM target (`KWINRT-078`);
  the WinRT `Locale` of `winui_dev` since the fix (see above).
- [x] Material 3: the Skiko menu, bottom sheet, navigation rail and dialog are
  compiled for WinUI as in the sync, without the copies of `winui_dev`. The
  JVM target keeps the JVM calendar locale and date format of the sync, which
  are those of the desktop target; the WinRT ones of `winui_dev` (which write
  the weekdays of the date picker as "Su", "Mo") are kept for the native
  target in `winuiMingwMain`. The precision pointer of `winui_dev` (WinRT
  keyboard and mouse capabilities) is kept.
- [x] Navigation: `winui_dev` moved the default transitions to
  `nonAndroidMain` and the POSIX mutex of `navigation-common` to `posixMain`.
  `jb-main` has per-platform transitions and its `build.gradle` has no
  `posixMain`, so both files stay where `jb-main` has them.
- [x] Shared Skiko code changed by `winui_dev` and merged: the indirect
  pointer events of `ComposeScene` and `RootNodeOwner` (adapted to the frame
  recomposer of `jb-main`), `IndirectPointerInputFocusListener` and
  `PlatformContentTransfer` in `commonMain`, the content transfer of
  `foundation`, the `equals` of `ModalWideNavigationRailProperties` and the
  localized tooltip strings.
- [x] Tests: the structure tests of `winui_dev` read the fork build files and
  the merged sources; the ones about the submodule, the MinGW wiring and the
  WinRT locale and date format of the JVM target are gone.
  `WinUIFontResourceLoaderTest` is gone with the loader object, which the sync
  replaced by the Skiko `FontLoader`.

Found while verifying the merged tree:

- [x] A layer that is attached while the root is measured was never measured,
  so a popup or dialog composed in a `Scaffold` at start-up stayed invisible
  until the window was resized. The layer host of the sync is the same code
  and showed such a popup; why it did was not examined (with `winui_dev` the
  window has its size before the content is composed, so nothing measures the
  root a second time). The layer host asks for a measure pass after the one
  that is running
  (`WinUIOwnerTest.layerAttachedWhileTheRootIsMeasuredIsMeasuredAfterwards`).
- [x] A frame whose work closes its own window (an effect that calls
  `Window.close()`) disposed the view and with it the picture recorder and
  surface that the frame was still drawing to: `runWinUIViewSample` crashed in
  Skia in 3 of 7 runs. The render host of a view that is disposed during its
  frame is closed after the frame; 14 of 14 runs pass. Whether the sync
  crashed the same way was not checked.

Verification:

- [x] WinUI JVM target (JDK 25, `-PcomposeWinUi.enableJvmTarget=true`):
  `compileKotlinWinuiJvm` of `ui`, `ui-text`, `ui-graphics`, `ui-skiko`,
  `animation`, `foundation`, `foundation-layout`, `material`, `material3`,
  `demo-winui` and the `winui-samples` classes.
- [x] `winuiJvmTest`: `ui` 360 tests, `ui-graphics` 173 (4 skipped),
  `foundation` 12, `ui-text` 6, `material3` 6.
- [x] Default mode (JDK 21): `compileKotlinDesktop` of `ui`, `ui-text`,
  `foundation`, `foundation-layout`, `material`, `material3`,
  `navigation-compose` and the demo; `compileKotlinJs` and
  `compileKotlinWasmJs` of `ui`, `foundation` and `material3`;
  `desktopTestClasses` of `ui` and `foundation`; of `:compose:ui:ui:desktopTest`
  the scene, node, drag and drop, indirect pointer, snapshot manager and flush
  dispatcher tests (94 in 16 classes) and of
  `:compose:foundation:foundation:desktopTest` `TransferableContentDesktopTest`.
- [x] `:compose:mpp:demo-winui:runWinUIMppSample` (its six smoke runs and the
  run that validates the title bar insets) and the eleven run tasks of
  `:compose:ui:ui:winui-samples`.
- [x] The 105 screens of the demo, captured on WinUI before and after the
  merge with the desktop shots as the reference: 93 are pixel-identical to
  before the merge. The other 12 show content that changes with time or from
  run to run (`LazyColumn`, `LazyGrid`, `StaggeredGrid`, `Pager`, `Resize In
  LazyList`, `MeshGradient`, `Blending`, `ImageViewer`, `Drag and Drop`,
  `WindowFocusDemo`, `Android TextBrushDemo`, `Linear Progress Indicator`);
  their differences were not examined one by one.
- [x] With real mouse and key input on the demo: the drag and drop screen
  (drag, over the target, drop), the open modal bottom sheet and navigation
  drawer are pixel-identical to before the merge; with the dropdown menu of
  the demo made focusable, Down, Up and Esc act as on desktop (0.01 % of the
  pixels differ after a shift of one pixel); typing into `BasicTextField`
  gives the same text as on desktop; the wheel scrolls a list.
- [ ] Not verified: the iOS and macOS targets (not buildable on this machine),
  the AOSP mode, pen and touch input with a device, an input method with the
  text input session, the input pane on a touch device, a right-to-left
  locale, and the effect of the pointer projection cleanup on memory
  (`KWINRT-064`), which was not measured here.

## Desktop parity 2026-10-05

The MPP demo looked and behaved differently on WinUI than on the AWT desktop
target with the same common code. The WinUI platform code was compared with
the desktop and Skiko implementations it stands in for (`desktopMain`,
`skikoMain`) and changed to behave the same way. Apart from moving three Skiko
files to `skikoRenderingMain` (below), only fork-only WinUI code and fork-only
build files changed.

Rendering:

- [x] Owned layers are the Skiko `GraphicsLayerOwnerLayer`: alpha, elevation
  shadows, outline clips and hit testing, render effects, color filters, blend
  modes, compositing strategy, camera distance and outsets work, and the layer
  matrix is applied in the order hit testing uses. `GraphicsLayerOwnerLayer`,
  `OwnedLayerManager` and `Matrices` moved from `skikoMain` to
  `skikoRenderingMain`, which the WinUI `ui` shares, instead of a WinUI copy;
  the code is unchanged. `WinUIOwner` implements `OwnedLayerManager`, records
  dirty layers before each frame and sets the shadow light from the window
  size.
- [x] The content gets the client area of the window (`AppWindow.ClientSize`)
  instead of the outer window size, so nothing is cut off at the right and
  bottom edges.
- [x] A window without a system backdrop has the background of a desktop
  window (`#EEEEEE`): the frame is cleared with it, because the swap chain
  ignores alpha and a transparent frame showed black.
- [x] Render requests always reach the Skia layer, which coalesces them. The
  render host held them back until the next draw, so a request the layer did
  not draw for (no surface size yet) stopped rendering until the next input
  event, for example a dialog shown with the screen did not appear.
- [x] `isSystemInDarkTheme()` follows the theme of the XAML root
  (`FrameworkElement.ActualTheme`).
- [x] The deprecated `LocalFontLoader` loads Skia typefaces, as on Skiko.

Input:

- [x] Pointer events use local positions for the previous position too, so a
  drag no longer jumps by the window origin on the screen (sliders, drags,
  scroll bars).
- [x] Hover follows the content after layout, scrolling and navigation: the
  frame loop sends the synthetic move after layout, as the Skiko scenes do.
- [x] A wheel notch scrolls the lines of the system setting
  (`SPI_GETWHEELSCROLLLINES`, page scroll included) instead of one line;
  Shift turns the vertical wheel into a horizontal one.
- [x] A wheel turn or leaving the surface with a pressed button no longer ends
  the drag; touchpad input is mouse input.
- [x] Layout runs before hit testing, and the work scheduled by an input event
  handler runs right after the event.
- [x] Key events reach Compose after a click into the content: the key
  adapter accepts events from the swap chain panel, which has the XAML focus
  then. Before, Esc, Tab and the arrow keys were dropped.
- [x] Unconsumed Esc is a back event (dialogs, focusable popups, `BackHandler`,
  navigation); punctuation keys and NumPad Enter are mapped;
  `utf16CodePoint` is the character of the keyboard layout (`ToUnicodeEx`);
  modifiers are reset when the window loses focus;
  `LocalWindowInfo.keyboardModifiers` is updated; only focus navigation keys
  switch to keyboard input mode.
- [x] `ViewConfiguration` has the desktop values: double tap 300 ms, touch
  slop 18 dp, minimum touch target 48 dp.
- [x] Touch and pen contacts that are down are in every pointer event, so
  multi-touch gestures (pinch to zoom, two-finger pan) work; WinUI reports one
  contact per event. Pen events carry the pen pressure.
- [x] Coordinates passed to XAML are DIPs: `positionOnScreen()`,
  `localToScreen`, the IME bounds, and the size, position and clip of
  `WinUIView` at scales other than 100 %. `RectManager` gets the window size
  and screen offset, so `onVisibilityChanged` and `onLayoutRectChanged` work.

Windows, popups and dialogs:

- [x] `Popup` and `Dialog` are layers above the content of the window, as the
  Skiko `ComposeSceneLayer`s on the same canvas: positioned and measured
  against the window, dismissed by a press outside (popups) or a release
  outside (dialogs) and by Esc when focusable, keeping pointer input from the
  content below when focusable, with the dialog scrim, centring, platform
  default width and appearance / disappearance animation.
  `PopupProperties.layerType = OnWindow` keeps the native flyout.
- [x] `Popup(..., onPreviewKeyEvent, onKeyEvent, content)` overloads, as on
  the Skiko targets, so the Material and Material 3 `DropdownMenu` move focus
  with the arrow keys. The WinUI copies of the Material menus, exposed
  dropdown popup, modal bottom sheet, wide navigation rail and edge-to-edge
  dialog are removed: the Skiko versions compile for WinUI now.
  `PopupProperties` and `DialogProperties` have the constructors and
  properties of the Skiko targets (`usePlatformInsets`,
  `consumePointerInputOutside`, `useSoftwareKeyboardInset`, `scrimColor`,
  `animateTransition`), so the Material sheets and drawers draw one scrim.
- [x] `Window(state = rememberWindowState(...))`: `WindowState` with size,
  position (`PlatformDefault`, `Aligned`, `Absolute`), placement (`Floating`,
  `Maximized`, `Fullscreen`) and `isMinimized`, applied to the `AppWindow` and
  updated when the user moves, resizes, maximizes or minimizes the window. As
  on desktop, the size is the outer size of the window.
- [x] Drag and drop from Compose: `Modifier.dragAndDropSource` starts a WinUI
  drag (`UIElement.StartDragAsync`) with the drag decoration as its image.
  `DragAndDropTransferData` has the desktop `supportedActions`,
  `dragDecorationOffset` and `onTransferCompleted`; `nativeTransferData` is a
  `String` (text) or a function that fills the `DataPackage`. Drops into
  Compose are accepted only over a target that takes them, with the action of
  the modifier keys (Ctrl copies, Shift moves, Ctrl+Shift links), which
  `DragAndDropEvent.action` reports. Without keys a drag copies when the
  source allows it; AWT prefers moving. Writing the drag image into the
  `SoftwareBitmap` needs the kotlin-winrt fix of `KWINRT-076`.
- [x] `Dispatchers.Main` is the UI thread of the WinUI application
  (`WinUIMainDispatcherFactory`), so `collectAsStateWithLifecycle`,
  `repeatOnLifecycle` and `viewModelScope` work.
- [x] The lifecycle is `RESUMED` while the window has focus, `STARTED` without
  it and `CREATED` while minimized.
- [x] The application ends when its content has no window left.
- [x] The clipboard no longer returns its own last text after another
  application copied something (clipboard sequence number).
- [x] The layout direction follows the default locale.
- [x] `navigation-compose` and `navigation3-ui` compile their WinUI target
  against the WinUI `ui`. Against the published desktop `ui`, dialog
  destinations failed with `NoClassDefFoundError: Dialog_skikoKt`.
- [x] The WinUI demo opens at the size of the desktop demo through
  `rememberWindowState(width = 1024.dp, height = 850.dp)`, its drag and drop
  screen is the desktop one (a source and a target), and its
  `TestInteropView` fills the native view with the color, as the desktop one
  does.

New public API of the WinUI `ui` (WinUI target only):

- `Popup(alignment, offset, onDismissRequest, properties, onPreviewKeyEvent,
  onKeyEvent, content)` and `Popup(popupPositionProvider, onDismissRequest,
  properties, onPreviewKeyEvent, onKeyEvent, content)`.
- `PopupProperties` and `DialogProperties` constructors and properties of the
  Skiko targets (experimental ones marked `@ExperimentalComposeUiApi`).
- `Window(onCloseRequest, state, ...)`, `WindowState`, `rememberWindowState`,
  `WindowPosition`, `WindowPlacement`.
- `DragAndDropTransferData(nativeTransferData, supportedActions,
  dragDecorationOffset, onTransferCompleted)`, `DragAndDropTransferAction`,
  `DragAndDropEvent.action` (experimental).

Validation on 2026-10-05 (Windows x64, 150 % scale, dark app mode):

- [x] `winuiJvmTest` of `ui`: 202 tests pass (multi-touch contacts, pen
  pressure, drag actions, the drag image bitmap and the shared owned layer
  included).
- [x] Default mode: `ui` compiles for desktop, wasmJs and js, and the 100
  desktop layer tests (`*GraphicsLayer*`, `*OwnedLayer*`, `*Layer*Test*`)
  pass after the move to `skikoRenderingMain`.
- [x] The WinUI and desktop demo windows open with the same client
  (1514 × 1219 px) and outer (1536 × 1275 px) size at 150 %.
- [x] With focusable menus, the arrow keys move the focus through the
  `DropdownMenu` items on WinUI as on desktop, and Esc closes the menu.
- [x] The open `ModalBottomSheet` and `ModalNavigationDrawer` match desktop,
  with one scrim.
- [x] Drag and drop screen, dragged with real mouse input: the drag image
  follows the pointer at the same offset as on desktop, WinUI shows "no drop"
  outside the target and "Copy" over it, the target shows "Hello, DnD!" after
  the drop, and the source gets `Copy` (desktop: `Move`). WinUI draws the drag
  image opaque; the AWT one is translucent.
- [x] `:compose:mpp:demo-winui:runWinUIMppSample` passes; the auto traverse
  enters 105 of 105 screens.
- [x] `compileKotlinWinuiJvm` of `navigation-compose`, `navigation3-ui`, the
  two `*-winui` navigation modules, and the `winui-samples` classes.
- [x] The 105 demo screens captured on desktop and on WinUI at the same client
  size (1280 × 900 px) and compared pixel by pixel. Apart from a one pixel
  horizontal offset of the AWT content, the screens match except where the
  demo draws random colors or animates, and where the WinUI demo has its own
  screen (drag and drop, pointer icons, font rasterization, configurable
  popup, dialog, Lottie). Wheel scrolling scrolls the same distance, and the
  open dropdown menu and dialog match desktop; on WinUI a slider drag follows
  the pointer and outside clicks and Esc dismiss the menu and the dialog.

Not changed:

- [ ] The text context menu is the native `MenuFlyout` on WinUI and the
  Compose context menu on desktop, on purpose.
- [ ] `foundation` `winuiJvmTest` cannot resolve `:compose:ui:ui-test` in the
  WinUI build (no desktop variant of `ui-skiko` there).

## Upstream sync 2026-10-04

- [x] Merged JetBrains `jb-main` `56d0128a85c` (#3477; 387 commits) into
  `winui_dev`: Kotlin 2.4.20, Skiko 0.153.0, Compose 1.13.0-alpha03, the
  separate fork build (`build-fork.gradle`, `buildSrc-fork`,
  `settings-fork.gradle`, `libs-fork.versions.toml`, `redirectversions.toml`),
  the `ui-skiko` module, and published lifecycle / savedstate /
  navigationevent artifacts instead of projects.
- [x] The WinUI wiring lives in the fork build files now. The AOSP-side
  `build.gradle`, `settings.gradle`, `gradle/libs.versions.toml` and
  `buildSrc/` are identical to upstream.
- [x] `-PcomposeWinUi.enableJvmTarget=true` selects the WinUI build: it adds
  the kotlin-winrt plugin to the build classpath, pins Kotlin to 2.4.0
  (`KWINRT-065`), includes `demo-winui`, `winui-samples` and the two
  `*-winui` navigation modules, and replaces `desktop()` with `winuiJvm` in
  the modules that have a WinUI target. Without the flag the build is
  upstream's and runs on JDK 21.
- [x] Moved to the kotlin-winrt Windows toolkit plugin
  (`io.github.compose-fluent.windows-toolkit`, `windows { packageReferences { }
  application { } }`), Windows App SDK 2.2.0, and `RunWinAppHostTask` run
  tasks instead of nested Gradle builds. Workarounds for the plugin published
  from `master` were `KWINRT-065` to `KWINRT-074`; see the next section for
  the ones that remain.
- [x] `org.jetbrains.skiko:skiko` is substituted with `skiko-winui` in every
  WinUI configuration. At the sync the current skiko-winui came from
  `skiko/build/repo` through `COMPOSE_WINUI_MAVEN_REPO`; the variable points
  at Maven Local now (`SKIKO-011`, next section).
- [x] `ui-skiko` has a `winuiJvm` target with the Skia-backed actuals that
  used to be in `ui-graphics` / `ui-text`. `ui-text` keeps the Skia-free ones.
- [x] `skikoRenderingMain` additionally shares `GlobalSnapshotManager`,
  `getCurrentThreadId` and the `PlatformPrefetchScheduler` types with WinUI.
  WinUI registers the Skiko implementation in `WinUIComposeView` and the
  application runtime, passes a non-immediate dispatcher to `FrameRecomposer`,
  and no longer has its own `GlobalSnapshotManager`, mesh gradient renderer or
  `PlatformWindowInsetsProviderNode`.
- [x] `material3-ripple` has a WinUI target; the WinUI `DropdownMenuPopup`
  actual is gone because the function is common now.
- [x] `demo-winui` excludes `LottieAnimation.kt` (`SKIKO-012`) and has no
  platform pointer icons.

Validation on 2026-10-04 (Windows x64, JDK 25, Kotlin 2.4.0 in WinUI mode,
skiko-winui from `skiko/build/repo`, `--no-configuration-cache` for the
application tasks):

- [x] `compileKotlinWinuiJvm` of `ui-graphics`, `ui-text`, `ui-skiko`, `ui`,
  `foundation-layout`, `foundation`, `animation-core`, `animation`,
  `material-ripple`, `material`, `material3-ripple`, `material3`,
  `material3-window-size-class`, the three `adaptive` modules,
  `navigation-compose`, `navigation3-ui`, and the two `*-winui` navigation
  modules.
- [x] `winuiJvmTest`: `ui` 183, `ui-graphics` 173, `ui-text` 5 tests pass.
- [x] `:compose:mpp:demo-winui:runWinUIMppSample`: all validation and smoke
  tasks pass; the auto traverse enters 105 of 105 screens.
- [x] `:compose:ui:ui:winui-samples`: `runWinUIViewSample` and the ten focused
  samples pass.
- [x] Without the WinUI flag: `compileKotlinDesktop` of `ui`, `foundation`,
  `material`, `material3`, `navigation-compose` and `navigation3-ui` passes.
- [ ] Not run: Android, iOS/macOS, web and desktop test tasks.

## Local kotlin-winrt and skiko-winui builds 2026-10-04

- [x] compose-winui builds against the local checkouts of both dependencies,
  published to Maven Local: kotlin-winrt `xaml-support` `3d7855783`
  (`0.1.0-SNAPSHOT`; `fa4508d7c` plus the fixes of kotlin-winrt PR #15 and
  PR #16) and skiko-winui `winui_dev` `6e5918d58` (`0.0.0-SNAPSHOT`), the
  latter built against that kotlin-winrt (`SKIKO-013`).
- [x] `COMPOSE_WINUI_MAVEN_REPO` points at the Maven Local repository
  (`%USERPROFILE%\.m2\repository`). `buildSrc-fork/repos.gradle` reads the
  repositories of that variable first for the group
  `io.github.compose-fluent`, so these builds win over the published snapshots
  of the same version.
- [x] Removed with `xaml-support`: the LF copy of the authoring sources
  (`KWINRT-068`) and the explicit additions to the projection compilation
  classpath (`KWINRT-069`). `winui-samples` no longer filters its host
  classpath (`KWINRT-070` only affects `demo-winui`).
- [x] New with `xaml-support`: `:compose:ui:ui` switches off the XAML schema
  export that the plugin runs for every library, with
  `windows { xaml { exportLibrarySchema = false } }`. The export failed on
  `fa4508d7c` (`KWINRT-075`, fixed in kotlin-winrt PR #15). The
  `demo-winui` graph validation expects the authoring registrar
  `WinRTAuthoringTypeDetailsRegistrar_ui_winuiMain`, which the plugin now names
  after the source set.
- [x] Removed with kotlin-winrt PR #16: the workarounds for `KWINRT-067`
  (disabled Android projection compilation), `KWINRT-070` (`demo-winui` host
  class path filter), `KWINRT-071` (`runtime\bin` first on `PATH`) and
  `KWINRT-073` (`duplicatesStrategy` in `winui-samples`). `KWINRT-066` is
  fixed too: WinUI builds no longer need configuration on demand.
- [x] Still there: the toolkit is applied after `androidXMultiplatform`, and
  `buildSrc-fork` tells the toolkit's `KotlinBaseApiPlugin` apart from AGP
  built-in Kotlin (see `KWINRT-067`). `KWINRT-065` (Kotlin 2.4.0),
  `KWINRT-072` (`vswhere.exe` on `PATH`) and `KWINRT-074` (JDK 25) are
  unchanged.

Validation on 2026-10-04 (Windows x64, JDK 25, Kotlin 2.4.0 in WinUI mode, a
CRLF checkout, both dependencies from Maven Local; the jars staged into the
demo host have the SHA-1 of the Maven Local files):

- [x] `compileKotlinWinuiJvm` of the same modules as above and of
  `demo-winui`.
- [x] `winuiJvmTest`: `ui` 183, `ui-graphics` 173 (4 of them skipped),
  `ui-text` 5 tests, no failures.
- [x] `:compose:mpp:demo-winui:runWinUIMppSample`: all validation and smoke
  tasks pass; the auto traverse enters 105 of 105 screens.
- [x] `:compose:ui:ui:winui-samples`: `runWinUIViewSample` and the ten focused
  samples pass.
- [x] `:compose:ui:ui:help --no-configure-on-demand` configures.
- [x] `:compose:ui:ui:compileAndroidMain` passes with the WinUI flag. The
  toolkit used to put its compiler plugin options on the Android compilation
  as well (`KWINRT-067`).
- [x] Without the WinUI flag, on JDK 21: `compileKotlinDesktop` of `ui`,
  `ui-skiko`, `foundation`, `material`, `material3`, `navigation-compose`,
  `navigation3-ui` and the MPP demo passes.
- [ ] Not run: Android, iOS/macOS, web and desktop test tasks; the skiko-winui
  test suite; the kotlin-winrt suites other than the tests added with the
  fixes.

## Architecture
- [x] Implement compose-winui as a standalone `compose-ui` platform target, comparable in responsibility to `androidMain`.
- [x] Keep compose-winui independent from `desktopMain`, AWT, Swing, and Skiko AWT/desktop runtime behavior.
- [x] Keep `winuiMain` on the selected `skikoHostMain` rendering surface rather than inheriting the full `skikoMain` actual set.
- [x] Preserve the normal non-WinUI Skiko graph through `skikoNonJvmMain` and `skikoNativeMain`, while conditionally excluding those Skiko-only actual sets from WinUI compilations.
- [x] Use `kotlin-winrt` as the WinRT and WinUI projection/runtime foundation instead of duplicating COM or Windows App SDK bootstrap code in `compose-ui`.
- [x] Share WinUI-specific Compose semantics in `winuiMain`, with current JVM details in `winuiJvmMain` and Kotlin/Native Windows hooks in `winuiMingwMain`.
- [x] Treat Android `AndroidView` interop as the behavioral reference for factory, update, reuse, detach, release, layout, focus, and input behavior.

## UIKit target parity gap
- [ ] Treat the existing UIKit target as the near-term architecture reference for production readiness, not just Android/Desktop. The WinUI target now has a real `Owner`, recomposer, application/window domain, Skiko-backed rendering, UI Automation coverage, text input, native interop synchronization, and focused test/smoke coverage. Keep using UIKit as the parity reference for ongoing polish, edge cases, and future upstream syncs.
- [x] Close the rendering architecture gap with UIKit's `ComposeSceneMediator` + `MetalView` / `MetalRedrawer` stack by adding a WinUI-native scene/rendering host that owns frame scheduling, surface resize, drawing submission, interop synchronization, and disposal as one coherent layer.
- [x] Close the accessibility architecture gap with UIKit's `AccessibilityMediator` by mapping Compose `SemanticsOwner` changes to UI Automation peers/elements, including focus, actions, scroll state, live-region-like notifications, and interop/native accessibility participation.
- [x] Add initial Skiko WinUI accessibility-provider hookup so the WinUI render
  surface can expose a Compose semantics snapshot through
  `WinUIAccessibilityProvider`, with focused unit coverage and the
  `runWinUISkikoSample` smoke validating a tagged Compose semantics node.
- [x] Route initial Skiko WinUI accessibility actions back to Compose
  semantics for focus, click, expand, collapse, and set-text requests, with
  focused unit coverage for the click path.
- [x] Close the text input architecture gap with UIKit's `NativeTextInputView` / `ComposeTextInputView` stack by replacing the current WinUI text-input lifecycle stubs with a real IME/editing bridge, including selection, composition, keyboard visibility, software keyboard control where available, and text-toolbar coordination.
- [x] Close the interop transaction gap with UIKit's `UIKitInteropContainer` by moving WinUI native child insertion, removal, z-order, layout, clipping, and native property updates into a render-synchronized transaction model instead of ad hoc root-content sync callbacks.
- [x] Close the interop input/focus gap with UIKit's cooperative/non-cooperative interaction modes by supporting WinUIView native focus transfer, native pointer/keyboard handling inside hosted controls, Compose event delivery outside hosted controls, and predictable Tab / Shift+Tab traversal across Compose and WinUI controls.
- [x] Close the platform-dependency gap by removing direct ABI event/property workarounds as `kotlin-winrt` generated event sources, interface registries, nullable WinRT properties, and resource/application lifecycle support become reliable in compose-winui.
- [x] Close the test architecture gap with UIKit's `iosTest` / `uikitInstrumentedTest` coverage by splitting the current large WinUI sample smoke into focused tests for scene/rendering, interop, accessibility, keyboard/text input, window/lifecycle, pointer/scroll, resource loading, memory/disposal, and integration launch.
- [x] Use the following rough maturity target when prioritizing work: the JVM WinUI path is past the original architecture bring-up target, and the MinGW target now compiles through the same shared platform layer. Remaining work is production hardening, upstream sync hygiene, pixel/readback-level render validation, and Native runtime/sample coverage.

## Gradle targets and source sets
- [x] Add a JVM target for WinUI, for example `jvm("winuiJvm")`, configured for JDK 22 or newer because `kotlin-winrt` JVM support uses the Java Foreign Function and Memory API.
- [x] Add the Windows native target `mingwX64("winuiMingw")`; the refreshed
  kotlin-winrt/Skiko graph now passes `compileKotlinWinuiMingw` on JDK 25.
- [x] Keep `winuiMain` dependent on `skikoHostMain`, not the full `skikoMain`
  actual set, so the JVM and MinGW WinUI targets share rendering-facing code
  without importing generic Skiko platform actuals.
- [x] Add `winuiJvmMain` as a dependent of `winuiMain`.
- [x] Add `winuiMingwMain` as a dependent of `winuiMain` while retaining the
  connected default `winuiMingwMain -> nativeMain -> nonJvmMain` hierarchy.
- [x] Add matching test source sets for shared WinUI behavior and target-specific JVM behavior.
- [x] Do not make `winuiMain`, `winuiJvmMain`, or `winuiMingwMain` depend on `desktopMain`, AWT, Swing, or `org.jetbrains.skiko.SkiaLayer`.
- [x] Preserve normal mode through `nonJvmMain -> skikoNonJvmMain -> skikoMain`
  and `nativeMain -> skikoNativeMain -> skikoMain`; in WinUI mode remove only
  those two Skiko-only ancestry edges, without mutating `dependsOn` after the
  hierarchy is created.
- [x] Append the Compose compiler plugin to the Native compiler-plugin
  classpath so the kotlin-winrt compiler plugin remains active.
- [x] In WinUI mode, redirect Android publications to AndroidX and other
  configured non-WinUI targets plus root metadata to
  `org.jetbrains.compose.*:1.10.0`; keep `winuiJvm` and `winuiMingw` local via
  their target publications.
- [x] Replace the temporary WinUI JVM compile-source bridge with a principled source-set split now that `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT` is resolvable, so shared Skiko scene/rendering code can be reused without compiling conflicting Skiko generic actuals. Initial Maven dependency wiring, a narrow `WinUISkikoRenderHost` adapter, and the first `WinUIComposeView` render-surface connection compile on 2026-06-02. `SKIKO-002` and `SKIKO-003` are fixed as of 2026-06-03; the 2026-07-06 validation baseline passes `compileKotlinWinuiJvm`, `winuiJvmTest`, the full repository WinUI sample, and the WinUI MPP sample.
- [x] Wire `winuiMain` to the local `kotlin-winrt` runtime without depending on checked-in `winrt-projections`.
- [x] Apply the local `kotlin-winrt` Gradle plugin for WinUI projection generation when running on JDK 22 or newer.
- [x] Declare the Windows App SDK NuGet package through the `winRt` DSL instead of directly depending on projection modules.
- [x] Explicitly declare the minimal WinRT projection `type(...)` entries required by `compose-ui` and repository-local samples for now. The current compose-winui configuration no longer uses prebuilt full projection artifacts and keeps the requested surface in explicit `type(...)` entries. Validation passes with the current `0.1.0-SNAPSHOT` kotlin-winrt Maven artifacts; the old `KWINRT-030` early identity-resolution failure is no longer reproduced.

## compose-ui platform abstractions
- [x] Add initial `WinUIComposeView` in `winuiMain`, mirroring the role of Android's root owner rather than desktop's Skiko scene layer.
- [x] Replace the initial `WinUIComposeView` placeholder with a real WinUI `Owner`, recomposer, and frame scheduler.
- [x] Implement the `Owner` contract for WinUI: root `LayoutNode`, measure/layout scheduling, drawing invalidation, snapshot observation, semantics owner, focus owner, pointer processing, and test root support.
- [x] Wire the initial WinUI `Owner` to Compose `MeasureAndLayoutDelegate`, root constraints, root measure policy, and positioned-callback dispatch so layout work is no longer a no-op.
- [x] Schedule WinUI `Owner` measure/layout passes from measure, relayout, positioned-callback, and snapshot-observed layout invalidations.
- [x] Schedule WinUI layout-completed listener delivery even when listener registration is the only pending layout work.
- [x] Wire WinUI `Owner` layout-node tracking into `RectManager` and remove stale rect entries on detach.
- [x] Wire initial WinUI `Owner` layer invalidation, size, position, clipping, and transform matrix state so layout bounds observers can see layer translations.
- [x] Attach the WinUI root `LayoutNode` to an initial `WinUIOwner` so reusable node lifecycle goes through common `LayoutNode.onReuse()` instead of a WinUI-only rootless path.
- [x] Implement a WinUI `setContent` entry point that creates a `Composition` with `UiApplier` and provides WinUI composition locals.
- [x] Add an initial `Window.setContent` entry point that creates a `WinUIComposeView`, installs its root into the WinUI `Window`, and returns the view for lifecycle management.
- [x] Add initial `Application { Window { ... } }` domains for WinUI JVM, including Windows App SDK bootstrap, RuntimeScope initialization, WinUI resource manager registration, and a window scope backed by `WinUIComposeView`.
- [x] Keep the WinUI application domain in `Application.winui.kt` and the WinUI window domain in `Window.winui.kt`.
- [x] Expose initial WinUI window capabilities from the generated `microsoft.ui.xaml.Window`: `extendsContentIntoTitleBar`, `WindowBackdrop`, and `WindowScope` access to `window`, `appWindow`, `compositor`, and `dispatcherQueue`.
- [x] Add concrete backdrop projection types for Mica and Desktop Acrylic through explicit Windows App SDK `type(...)` entries and validate Mica-to-DesktopAcrylic updates in the sample.
- [x] Add WinUI `WindowBackdrop.None` clearing through the `IWindow2.SystemBackdrop` ABI setter while `kotlin-winrt` generates a non-null setter.
- [x] Add an initial `Window(onCloseRequest = ...)` close-request hook and close the native WinUI window when its Compose node is released.
- [x] Keep WinUI `Window` node release idempotent after a native close so Compose state removal still completes the node lifecycle without a second close request.
- [x] Move initial WinUI application/window disposal onto the WinUI UI thread via `DispatcherQueue` for `exitApplication` and window close/removal paths.
- [x] Broaden the initial close/removal work into full declarative multi-window lifetime semantics, including cancelable close policy if WinUI exposes a suitable pre-close event.
- [x] Provide WinUI actuals for common platform hooks such as time, delayed posting, view configuration, window info, URI handling, haptics, semantics region, focusability, and platform velocity tracking.
- [x] Route initial WinUI JVM delayed posting back through the registered WinUI `DispatcherQueue` instead of running callbacks directly on the scheduler thread.
- [x] Provide initial WinUI composition locals for density, layout direction, view configuration, font resolution, URI handling, active-window focus state, and AppWindow-backed container size.
- [x] Observe the effective XAML theme and flow direction plus Windows text scale and animation settings, then update `LocalSystemTheme`, owner layout direction, `Density.fontScale`, and the root recomposer `MotionDurationScale` without recreating the Compose root.
- [x] Drive each `WinUIComposeView` lifecycle from its loaded, visible, and active state: unattached or hidden roots remain `CREATED`, inactive loaded roots are `STARTED`, active loaded roots are `RESUMED`, and disposal is terminal `DESTROYED`.
- [x] Route WinUI caption-bar/title-bar insets from `AppWindow.titleBar`
  (`height`, `leftInset`, and `rightInset`) into `WindowInsets.captionBar`,
  `WindowInsets.systemBars`, `safeDrawing`, and `safeContent`; keep the other
  desktop-only insets at zero until backed by a concrete WinUI/Windows signal.
  The MPP sample now validates this through the common `SelectionTopBar`
  `TopAppBar(contentPadding = WindowInsets.systemBars...)` path with
  `extendsContentIntoTitleBar=true`, so titlebar inset propagation is checked
  against real sample layout rather than a standalone WinUI-only sample.
- [x] Provide initial WinUI clipboard hooks for `LocalClipboardManager`, `LocalClipboard`, `ClipEntry`, `ClipMetadata`, and `NativeClipboard`; the synchronous `ClipboardManager` text cache is compose-winui policy, not a kotlin-winrt helper requirement.
- [x] Provide initial WinUI Autofill hooks for `LocalAutofill`,
  `LocalAutofillManager`, and `LocalAutofillTree`, including legacy
  `AutofillNode` fill callbacks, semantics-based `requestAutofill`, focus /
  semantics lifecycle tracking, `commit` / `cancel` session state, and focused
  WinUI JVM coverage. This is the Compose platform integration layer only:
  WinUI desktop does not currently expose an Android-style system Autofill UI
  surface here, so compose-winui does not fake a credential suggestion popup.
- [x] Provide initial WinUI text input and IME integration hooks, with minimal stubs only where behavior is explicitly deferred.
- [x] Provide initial WinUI text-toolbar state tracking for copy/paste/cut/select-all/autofill menu requests.
- [x] Provide WinUI accessibility integration hooks that can later map Compose semantics to UI Automation.
- [x] Ensure lifecycle, retained values, and saveable state behavior have WinUI equivalents instead of relying on Android `ViewTree*Owner` APIs.

## WinUI rendering host
- [x] Implement a WinUI-native rendering host that does not require an AWT component or Skiko AWT layer. The first `skiko-winui` surface is installed under the WinUI root content on 2026-06-02. The full repository WinUI sample and WinUI MPP sample pass with the current `0.1.0-SNAPSHOT` kotlin-winrt Maven artifacts as of 2026-07-06.
- [x] Define the shared `winuiMain` rendering-facing abstraction used by `WinUIComposeView` to request frames, resize, and submit drawing work.
- [x] Add initial unit coverage for the `WinUISkikoRenderHost` adapter lifecycle: render invalidation forwarding, resize forwarding, frame-scheduler reuse, close ordering, idempotent close, suppression of post-close render/resize requests, and render diagnostics exposure.
- [x] Implement the JVM backend in `winuiJvmMain` using `kotlin-winrt`, Windows App SDK bootstrap, DispatcherQueue, and the JVM native interop path.
- [x] Implement the mingwX64 backend in `winuiMingwMain`, including Native
  process-property/debug logging, DWM transparency/corner handling, capture
  protection, and shared projected InputPane acquisition. The target compiles
  with the generated kotlin-winrt projections and `skiko-winui-mingw`.
- [x] Bind the Compose render output to a WinUI-hostable native surface or composition-backed surface owned by the WinUI target. `WinUIComposeView` now draws its root `LayoutNode` into a `skiko-winui` Skia canvas through a WinUI `Canvas` root layer, and the 2026-07-06 validation baseline covers attached Direct3D rendering, positive render sizes, matching render state size, non-empty draw bounds, and observed frames.
- [x] Keep frame scheduling on the WinUI UI thread and ensure rendering invalidations are coalesced with Compose measure/layout work. `WinUIComposeView` now starts the Skiko frame scheduler only after the WinUI root is loaded, so unattached roots can compose and run owner/interops tests without starting presentation callbacks.
- [x] Release native rendering resources, DispatcherQueue handles, COM references, and Windows App SDK registrations when the host is disposed.

## Skiko full integration and MPP sample readiness
- [x] Add a repository-local WinUI JVM variant of the original MPP sample instead
  of relying only on `compose/ui/ui/winui-samples`; keep the original sample
  source shared as much as possible and isolate only the app entry/window
  bootstrap behind WinUI-specific source sets.
- [x] Define the exact original MPP sample scope that must run on WinUI,
  including its modules, resources, image/font assets, navigation paths, and
  desktop-specific APIs that need WinUI equivalents or source-set guards.
- [x] Add Gradle wiring for a `runWinUIMppSample` task that uses the same
  kotlin-winrt initialization chain as `runWinUIViewSample`, stages WinRT
  runtime assets, builds the authoring host, and keeps explicit `type(...)`
  declarations rather than full projection dependencies.
- [x] Replace the temporary `compileKotlinWinuiJvm` source override with a
  principled source-set split before treating the MPP sample as representative;
  shared Skiko rendering sources should come from normal source-set
  dependencies, not from an ad hoc file list.
- [x] Inventory the original MPP sample's Compose UI API surface against WinUI
  actuals and add missing implementations or guarded fallbacks for graphics,
  text, pointer, keyboard, clipboard, URI, window info, density, focus, popup,
  dialog, drag-and-drop, and accessibility hooks.
- [x] Replace current `ui-graphics` WinUI stubs that affect visible sample
  output with real Skia-backed implementations or explicit tracked gaps,
  including path/effect/image/brush/layer behavior used by the sample.
- [x] Replace current `ui-text` WinUI stubs that affect visible sample output
  with real text measurement/rendering/font behavior or explicit tracked gaps,
  including font resolution, paragraph layout, selection geometry, and text
  input integration used by the sample.
- [x] Expand `WinUISkikoRenderHost` from the current narrow adapter into the
  production rendering host shape needed by a real sample: surface lifecycle,
  resize, invalidation coalescing, frame pacing, draw submission, render
  diagnostics, interop transaction ordering, and deterministic disposal.
- [x] Add visible-output validation for the WinUI MPP sample, not just smoke
  logs: verify attached Direct3D rendering, positive render sizes, non-empty
  draw bounds, and at least one nonblank rendered frame or equivalent
  skiko-winui pixel/readback diagnostic once available.
- [x] Move WinUI native child insertion/removal/z-order/layout/clipping updates
  fully behind the render-synchronized interop transaction queue before using
  the original sample as an interop correctness baseline.
- [x] Complete enough native text input for sample use: focus entry, session
  replacement, composing text, committed text, selection updates, edit menu
  actions, and keyboard-driven focus order. Keep software keyboard behavior
  tracked separately if WinUI desktop cannot expose it directly.
- [x] Complete enough WinUI accessibility for sample use: semantics tree
  projection, bounds updates, focus, click/custom actions, scroll actions,
  live-region-like notifications, and native interop accessibility inclusion
  or exclusion.
- [x] Add resource and packaging validation for the original MPP sample:
  bundled images, fonts, strings, Windows App SDK PRI/resource staging, default
  language, and unpackaged app runtime assets must all load from the WinUI run
  task.
- [x] Remove or guard desktop/AWT/Swing-only sample code paths, including
  desktop window APIs, Skiko AWT layer assumptions, file/dialog helpers, tray or
  menu APIs, and any JVM desktop dependencies that would pull AWT runtime
  artifacts into the WinUI classpath.
- [x] Add classpath assertions to the MPP sample run task matching the existing
  WinUI smoke guards: require `skiko-winui`, reject Skiko AWT/Desktop native
  runtime artifacts, and reject duplicated `microsoft/**` or `windows/**`
  projection classes from third-party jars.
- [x] Split the first MPP sample validation into focused tasks: compile-only
  sample, launch/window smoke, render-output smoke, input/focus smoke,
  resource-loading smoke, and shutdown/disposal smoke.
- [ ] Re-run Android/Desktop versions of the original MPP sample after sharing
  code with WinUI to ensure source-set guards did not regress existing sample
  platforms. The 2026-07-06 local validation covered the original sample's
  Desktop `desktopJar`; no Android compile/run task is currently exposed by the
  original MPP sample project.
- [x] Keep unresolved kotlin-winrt/skiko blockers tied to this sample as stable
  `KWINRT-###` or `SKIKO-###` entries, and do not hide generator/runtime issues
  by excluding legitimate WinMD dependency-chain types.

## WinUIView interop
- [x] Add public `WinUIView` composable API for embedding a WinUI `UIElement` in Compose UI.
- [x] Match Android `AndroidView` lifecycle semantics: `factory` creates the view, `update` runs after creation and on recomposition, `onReset` opts into reuse, and `onRelease` runs once when the instance is permanently discarded.
- [x] Add initial `WinUIInteropProperties` for interaction, native accessibility participation, clipping/overlay behavior, and future WinUI-specific interop switches.
- [x] Add `InteropView` actual for WinUI `UIElement`.
- [x] Add initial `InteropViewGroup` for the WinUI wrapper used to host interop children.
- [x] Implement an initial WinUI view holder that owns the user `UIElement`, wrapper element, modifier updates, density updates, lifecycle callbacks, and native release cleanup hooks for event tokens.
- [x] Add initial WinUIView measure policy plus size and root-position propagation from Compose layout to the WinUI wrapper and `FrameworkElement` child.
- [x] Wire initial `WinUIInteropProperties.clipToBounds` support to a WinUI `RectangleGeometry` clip on the native wrapper.
- [x] Wire initial `WinUIInteropProperties.isUserInteractionEnabled` support to WinUI hit testing, user-element Tab focus, and `Control.isEnabled`.
- [x] Wire `WinUIInteropProperties.isNativeAccessibilityEnabled` support to `AutomationProperties.AccessibilityView`.
- [x] Use an initial Canvas-backed WinUI interop root container so wrapper position and z-order are controlled by Compose tree order instead of Grid layout behavior.
- [x] Implement a WinUI views handler/container that manages insertion, removal, z-order, clipping, and draw-order synchronization with the Compose tree.
- [x] Map Compose layout coordinates to WinUI bounds using unclipped bounds for the user element and clipped bounds for the wrapper.
- [x] Support focus transfer between Compose focus targets and WinUI controls, including Tab and Shift+Tab traversal.
- [x] Support basic pointer cooperation so native WinUI controls handle pointer input inside `WinUIView` bounds while Compose receives pointer input outside interop views.
- [x] Apply `Modifier.pointerHoverIcon(...)` to the WinUI root through a projected root element subclass that can set `UIElement.ProtectedCursor` through normal protected-member access; do not use direct protected-interface slot calls.
- [x] Support keyboard/native-focus cooperation so embedded WinUI controls handle their own focused keyboard input while Compose keeps predictable key dispatch outside interop views.
- [x] Defer full nested scroll parity until after basic AndroidView-equivalent lifecycle, layout, focus, and input behavior is stable.

## kotlin-winrt dependencies
- [x] Consume `kotlin-winrt` from Maven Central snapshots for WinRT runtime, authoring, generated projection support, and the Gradle projection plugin.
- [ ] Verify `kotlin-winrt` full projection generation includes required WinUI types: `Application`, `Window`, `UIElement`, `FrameworkElement`, `Panel`, `Grid`, `Canvas`, `ContentControl`, `XamlControlsResources`, DispatcherQueue, focus/input event types, and required collection types.
- [x] Reuse `kotlin-winrt` Windows App SDK bootstrap and resource manager support for unpackaged WinUI applications.
- [x] Reuse `kotlin-winrt` COM reference management, event-token management, activation factory lookup, and XAML metadata provider support, including generated WinUI event sources after `KWINRT-016` is resolved.
- [ ] Add missing projection/runtime capabilities to `kotlin-winrt` first when compose-winui requires WinUI APIs that are not yet projected.
- [x] Keep kotlin-winrt's KMP graph baseline covered with repository-local validation for customized source sets, transitive WinRT identity, support artifact merging, and multi-module generated projection ownership.
- [x] Follow kotlin-winrt's WinUI resource bootstrap with full Windows SDK PRI pipeline alignment: `Page`, `ApplicationDefinition`, `PRIResource`, manifest default language, `ProjectPriIndexName`, `AppxPriInitialPath`, duplicate filtering, and `WinAppSdkExpandPriContent` behavior.
- [x] Keep target-specific native interop code inside `winuiJvmMain` and `winuiMingwMain`; keep shared Compose/WinUI behavior in `winuiMain`.

## Tests and validation
- [x] Add a repository-local compose-winui sample that compiles against `:compose:ui:ui` and calls `WinUIView { Button() }`.
- [x] Add a runnable Windows App SDK/WinUI smoke task for the compose-winui sample so projection/runtime wiring is validated outside `kotlin-winrt`'s own samples.
- [x] Add repository-local WinUIView lifecycle smoke validation for factory, initial update, leaving composition, re-entering composition, and final release.
- [x] Add repository-local window smoke validation for `Application { Window { ... } }`, title propagation, `WindowScope.window`, and `WindowBackdrop.Mica`.
- [x] Add repository-local secondary `Window` close/removal smoke validation for native pre-close cancellation triggering `onCloseRequest` and Compose state removal while another WinUI window remains active.
- [x] Add repository-local application-domain recomposition smoke validation for state-driven `Window` title, titlebar, and backdrop parameter updates.
- [x] Upgrade the compose-winui sample to render Compose content inside `WinUIComposeView` once the WinUI Owner, recomposer, and frame scheduler are implemented.
- [x] Add compile validation for the new WinUI JVM source set.
- [x] Add repository-local reusable `WinUIView` smoke validation for reset on deactivation, reactivation without recreation, and final release on disposal.
- [x] Add repository-local composition-local smoke validation for WinUI density, layout direction, view configuration, font resolver, and URI handler.
- [x] Add repository-local environment/lifecycle tests and window smoke coverage for effective Light/Dark theme changes, RTL flow-direction changes, positive text scale, attach-safe lifecycle locals, activated `RESUMED`, and disposal to `DESTROYED`.
- [x] Add repository-local architecture-owner composition-local smoke validation for WinUI lifecycle, saved-state registry, and ViewModel store owners.
- [x] Add repository-local architecture-owner smoke validation for WinUI `viewModel()` creation with `SavedStateHandle`, retention across `disposeComposition()`, and `ViewModelStore` clearing on `dispose()`.
- [x] Add repository-local architecture-owner smoke validation for WinUI `LocalNavigationEventDispatcherOwner` host-default provisioning.
- [x] Add repository-local composition-local smoke validation for WinUI `LocalClipboardManager` and `LocalClipboard` plain-text round-trips.
- [x] Add repository-local FillableData smoke validation for WinUI text, boolean, list-index, and date-millis values.
- [x] Add repository-local `WindowInfo` smoke validation for active window focus plus positive container px/dp size.
- [x] Add repository-local `WindowInfo` smoke validation for `Window.Activated` focus loss when a secondary WinUI window is activated and closed.
- [x] Add repository-local owner disposal smoke coverage by disposing `WinUIComposeView` after lifecycle/reuse validation and using `dispose()` from the WinUI window release path.
- [x] Add repository-local WinUI text input session lifecycle smoke validation for session replacement and disposal cancellation while the native IME connection remains deferred.
- [x] Add repository-local WinUI owner unit validation for text input session replacement canceling the previous platform input method session.
- [x] Add repository-local WinUI `LocalLifecycleOwner` smoke validation for resumed composition state and destroyed state after `WinUIComposeView.dispose()`.
- [x] Add repository-local WinUI `rememberSaveable` smoke validation for restoring state across `WinUIComposeView.disposeComposition()` and subsequent `setContent()`.
- [x] Add repository-local WinUI `retain` smoke validation for restoring retained values across `WinUIComposeView.disposeComposition()` and subsequent `setContent()`.
- [x] Add compile validation for the WinUI mingwX64 source set with generated kotlin-winrt projections, both compiler plugins, and no duplicate actuals.
- [x] Add tests proving WinUI source sets do not depend on `desktopMain`, AWT, Swing, or Skiko AWT classes, and that WinUI keeps its own XAML/WinRT actuals where Skiko has generic or Win32-backed behavior.
- [x] Add a WinUI JVM compile-source bridge isolation test that keeps the
  temporary `compileKotlinWinuiJvm` source override limited to common,
  jvm/Android, WinUI, generated WinRT sources, and explicitly selected shared
  Skiko sources, without compiling `desktopMain` or the whole generic
  `skikoMain` source tree.
- [x] Add a WinUI JVM runtime classpath guard that rejects Skiko AWT/Desktop
  native runtime artifacts while requiring the WinUI runtime to resolve through
  `skiko-winui`.
- [x] Add the same Skiko runtime classpath isolation guard to the repository-local
  WinUI sample and focused `runWinUISkikoSample` smoke path, so the runnable
  validation rejects `skiko-awt-runtime-*` artifacts while requiring
  `skiko-winui`.
- [x] Add lifecycle tests for `WinUIView`: factory once, update after creation, repeated update on state changes, reset on reuse, release on final disposal.
- [x] Add layout tests for bounds, clipping, z-order, placement, unplacement, and relayout after density or size changes.
- [x] Add repository-local WinUIView smoke validation for fixed Compose size and position propagation to the native WinUI wrapper and child element.
- [x] Add repository-local WinUIView smoke validation for installing a native clip rectangle from `clipToBounds=true`.
- [x] Add repository-local WinUIView smoke validation for updating interop properties and clearing native clip.
- [x] Add repository-local WinUIView smoke validation for updating native interaction state across hit testing, Tab focus, and `Control.isEnabled`.
- [x] Add repository-local WinUIView smoke validation for restoring native interaction state and clearing native clip on release.
- [x] Add repository-local WinUIView smoke validation for native WinUI event-token registration and release cleanup after generated WinUI event sources are usable from compose-winui (`KWINRT-016`).
- [x] Add repository-local WinUIView smoke validation for toggling native accessibility participation through `AutomationProperties.AccessibilityView`.
- [x] Add repository-local WinUIView smoke validation for unclipped native child bounds inside clipped Compose wrapper bounds.
- [x] Add repository-local WinUIView smoke validation for relayout after Compose size and position state changes.
- [x] Add repository-local WinUI owner smoke validation for snapshot-observed layout state invalidating measure/layout without recomposition.
- [x] Add repository-local WinUI owner smoke validation for layout-completed listener dispatch after installing an `OnPlacedModifier`.
- [x] Add repository-local WinUI owner smoke validation for `onLayoutRectChanged` occlusion and detach cleanup.
- [x] Add repository-local WinUI owner smoke validation for graphics-layer translation propagating through `onLayoutRectChanged` bounds.
- [x] Add repository-local WinUI owner smoke validation for `RootForTest.sendKeyEvent` dispatching through preview and key handlers.
- [x] Add repository-local WinUI owner smoke validation for Tab and Shift+Tab focus traversal through `RootForTest.sendKeyEvent`.
- [x] Add repository-local WinUI owner smoke validation for `RootForTest.semanticsOwner` exposing semantics nodes and properties.
- [x] Add repository-local WinUI owner smoke validation for end-apply listener de-duplication and same-cycle draining.
- [x] Add repository-local WinUI owner smoke validation for `RootForTest.setUncaughtExceptionHandler` routing layout exceptions.
- [x] Add repository-local WinUI owner test validation for keep-screen-on, accessibility testing controls, frame-rate vote, scroll-change dispatch, and root invalidation state hooks.
- [x] Add repository-local WinUI owner test validation for semantics/layout event routing, root invalidation callbacks, scroll callbacks, and sensitive-content state hooks.
- [x] Add repository-local WinUI owner test validation for out-of-frame executor scheduling and `RootForTest.measureAndLayoutForTest()` draining.
- [x] Add repository-local WinUI owner test validation for window/screen coordinate mapping delegation.
- [x] Add repository-local WinUI owner test validation for text-toolbar shown/hidden state and request callbacks.
- [x] Add repository-local WinUI owner test validation that disposal suppresses pending out-of-frame work, window/interop updates, end-apply listeners, and future owner scheduling callbacks.
- [x] Add repository-local WinUI owner test validation that disposal releases active keep-screen-on and sensitive-content platform state and suppresses future state changes.
- [x] Add repository-local WinUI platform hook unit validation for URI scheme handling and WinUI view-configuration defaults.
- [x] Wire WinUI keep-screen-on to `Windows.System.Display.DisplayRequest` and sensitive content to top-level window capture protection.
- [x] Add repository-local WinUI accessibility bridge test validation for semantics/layout/scroll invalidation batching and test-forced accessibility enablement.
- [x] Add repository-local WinUI owner smoke validation for `RootForTest.sendIndirectPointerEvent` dispatching through the focused indirect pointer input node.
- [x] Add repository-local WinUI owner smoke validation for basic pointer press/release dispatch through `PointerInputModifierNode`.
- [x] Add repository-local WinUI owner smoke validation for pointer move dispatch and coordinate propagation through `PointerInputModifierNode`.
- [x] Add repository-local WinUI owner smoke validation for root pointer enter/exit dispatch through `PointerInputModifierNode`.
- [x] Add repository-local WinUI owner smoke validation for root pointer-wheel scroll dispatch and `scrollDelta` propagation through `PointerInputModifierNode`.
- [x] Add repository-local WinUI owner smoke validation for canceling active pointer input when the owner is disposed.
- [x] Add repository-local WinUIView smoke validation that pointer events inside native interop bounds are left for WinUI while outside events still dispatch to Compose.
- [x] Route WinUI interop view layout changes through `Owner.onInteropViewLayoutChange` so native root synchronization is scheduled when hosted views move or resize.
- [x] Add repository-local WinUI interop transaction queue unit coverage for dropped frames, late completions, empty transactions, merge behavior, and ring-buffer overflow.
- [x] Add repository-local WinUIView smoke validation for placement/unplacement without native recreation or release.
- [x] Add repository-local WinUIView smoke validation for interop insertion, middle removal, and z-order preservation.
- [x] Add repository-local WinUIView smoke validation for multiple WinUI control types: `Button`, `TextBox`, and `ToggleSwitch`.
- [x] Add repository-local WinUI owner focus smoke validation for Compose `FocusRequester` requests accepted while attempting native WinUI root focus.
- [x] Add repository-local WinUI platform focus owner unit validation for native root focus attempt, exception handling, and clear-focus delegation.
- [x] Route focused embedded WinUI view bounds into `PlatformFocusOwner.getEmbeddedViewFocusRect()` so focus search can use native interop geometry once embedded focus succeeds.
- [x] Add repository-local WinUIView smoke validation for Compose `FocusRequester` focus transfer to a native WinUI control after compose-winui moves the native focus request to a loaded/layout-ready point.
- [x] Add focus and input smoke coverage for Compose focus targets around hosted WinUI controls, keyboard events, Compose pointer delivery outside hosted controls, and hosted-control pointer exclusion. Tab traversal and native focus transfer are covered by the adjacent root traversal and loaded/layout-ready native focus smokes.
- [x] Add repository-local WinUI key input processor coverage proving native-child key events are left to WinUI and do not update Compose modifier state.
- [x] Add repository-local WinUI drag-and-drop manager coverage for platform drag session start, move, changed, drop, exit, end, and target-interest cleanup.
- [x] Wire WinUI root XAML drag/drop events into Compose drag-and-drop target dispatch with event-token cleanup on `WinUIComposeView.dispose()`.
- [x] Align WinUI `PopupProperties` value equality with other Compose platforms and cover all public fields in WinUI JVM tests.
- [x] Replace the initial WinUI inline `Popup` placeholder with a zero-size popup host layout that applies popup semantics and position-provider placement without depending on skiko rendering layers.
- [x] Replace the initial WinUI inline `Dialog` placeholder with a zero-size centered dialog host layout, dialog semantics, and `DialogProperties` value equality coverage.
- [x] Add Windows JVM integration smoke test that shows Compose content with embedded WinUI `Button` and `ToggleSwitch`.
- [x] Retest shutdown/upcall validation after current kotlin-winrt sync: `KWINRT-013` is treated as fixed upstream, though compose-winui did not identify the exact fixing commit. If `upcallLinker.cpp:66` returns, preserve and analyze `hs_err`, WER, or dump output before reopening it.
- [x] Extend the Windows JVM integration smoke to a live WinUI `TextBox` after `KWINRT-008` is resolved.
- [ ] Add a Windows mingwX64 runtime integration smoke test for the same shared `WinUIView` sample; compilation is green, but a Native application launch is not yet covered.
- [x] Add shutdown tests that verify composition disposal releases WinUI event tokens, COM references, rendering resources, and runtime registrations.
- [x] Split the current monolithic `runWinUIViewSample` smoke into focused WinUI JVM test/smoke suites, mirroring UIKit's split between unit/instrumented coverage: scene/rendering, interop lifecycle/layout/input, accessibility, text input/keyboard, window/lifecycle, pointer/scroll, resource loading, disposal/leaks, and launch integration.
- [x] Add WinUI rendering-host tests comparable to UIKit `MetalRedrawer` and layer tests: resize, invalidation coalescing, frame pacing, render/interop transaction ordering, disposal after pending frame callbacks, and nonblank surface output once drawing is implemented.
- [x] Add initial WinUI rendering-host adapter unit tests for resize, render requests, frame-scheduler reuse, close ordering, idempotent close, post-close render/resize/accessibility suppression, and render diagnostics exposure.
- [x] Harden WinUI Skiko rendering-host release so layer cleanup still runs if
  frame-scheduler cleanup throws, preserving the first close failure and
  suppressing the second.
- [x] Keep AutoCloseable Skiko render delegates owned by `WinUISkikoRenderHost`
  so draw-bounds recorder cleanup follows the same scheduler/layer/delegate
  close ordering and failure-preservation rules.
- [x] Cover WinUI Skiko render-host cleanup when scheduler, layer, and
  AutoCloseable render delegate cleanup all fail, preserving the scheduler
  failure while suppressing later cleanup failures.
- [x] Cover WinUI Skiko frame-scheduler startup failure so a failed start is not
  cached as a started scheduler and the host can retry startup.
- [x] Add repository-local Skiko scheduler/render diagnostics smoke coverage that verifies an unattached `WinUIComposeView` does not start the frame scheduler, and a window-owned `WinUIComposeView` reaches a render frame without `WinUISkikoRenderHost` reporting a render failure.
- [x] Add a focused repository-local `runWinUISkikoSample` task so Skiko scheduler/render diagnostics and Skiko accessibility action dispatch can be retested without running the full WinUIView/window/interops smoke suite.
- [x] Add repository-local Skiko diagnostics coverage that checks attached
  render state size and platform render result size are both reported and
  consistent, and that no pending invalidated render state remains after the
  attached frame is consumed.
- [x] Replace the temporary reflective Skiko render diagnostics bridge with the
  typed `WinUISkiaLayer.renderDiagnostics` API once `skiko-winui`
  `0.0.0-20260603.075139-3` published it publicly.
- [x] Add repository-local Skiko diagnostics coverage that verifies
  `WinUIComposeView` is backed by the Skiko WinUI Direct3D render API in both
  unattached scheduler-deferral and attached-window render paths.
- [x] Add WinUI Skiko draw-bounds diagnostics by wrapping the WinUI render
  delegate with the shared Skiko draw-rect recorder and validating the focused
  `runWinUISkikoSample` path records non-empty Compose draw bounds after an
  attached render and clears those bounds after Compose content is disposed.
- [x] Add focused WinUI JVM unit coverage for the draw-bounds recorder:
  non-empty draws update bounds, empty subsequent renders report zero bounds,
  negative-area draws retain their negative bounds, and a closed recorder still
  forwards rendering without updating diagnostics.
- [x] Add WinUI UI Automation tests comparable to UIKit accessibility tests: semantics tree projection, accessibility focus, custom actions, scroll actions, live-region notifications, interop native accessibility inclusion/exclusion, and geometry updates after layout.
- [x] Add initial WinUI Skiko accessibility-provider tests for semantics
  snapshot projection, render-host provider/change forwarding, and basic
  action dispatch from the Skiko provider back to Compose semantics, including
  focus, click, expand, collapse, set-text, and progress increment/decrement.
  Snapshot coverage now also validates projected bounds, hidden-node filtering,
  live-region metadata, help text, role, and enabled/focusable/selected/
  checked/editable/password state. Change-notification coverage validates
  structure, node-updated, and value-changed events before they are forwarded
  to the Skiko WinUI layer.
- [x] Add WinUI text input and keyboard tests comparable to UIKit keyboard/text-field tests: focus entry, IME session lifecycle, composing text, selection updates, clipboard/edit menu interaction, software keyboard show/hide behavior where available, and keyboard-driven focus order.
- [x] Re-run existing Android, desktop, and iOS compose-ui interop tests to confirm the new WinUI target does not regress existing targets.

## kotlin-winrt status

- `KWINRT-060` is closed in kotlin-winrt snapshot
  `0.1.0-20260714.234543-80`: `compose/foundation/foundation` applies the
  normal library-mode plugin without the former identity/projection task cycle.
- `KWINRT-062` is closed in kotlin-winrt snapshot
  `0.1.0-20260715.042347-81`. The repository-local `runWinUISkikoSample`
  passes projection generation, compilation, authoring validation,
  application-host build, staging, and launch without compose-side IID
  filters or projection overrides.
- `KWINRT-056` and `KWINRT-059` are closed in the 2026-07-09 compose-winui
  validation baseline. Repository-local WinUI sample tasks now use typed
  kotlin-winrt application-host run tasks with declared `jvmArgs`, output logs,
  and per-smoke report files.
- `KWINRT-060` is closed. The normal `io.github.compose-fluent.winrt` plugin
  injects runtime dependencies in library mode while skipping local
  projection/compiler-support/authored validation work when the combined local
  projection output is empty. `foundation` no longer declares
  `winrt-runtime` manually and does not call `application {}`.
- `KWINRT-061` is closed as a Compose fragment-graph misdiagnosis. A single
  connected Native fragment root removes all 794 `overrides nothing`
  diagnostics; selectively excluding Skiko-only actual ancestry removes the
  51 duplicate actuals. `compileKotlinWinuiMingw` now passes with normal
  kotlin-winrt and Skiko MinGW consumption.
- `KWINRT-053`, `KWINRT-054`, `KWINRT-055`, `KWINRT-057`, and `KWINRT-058`
  are closed in the refreshed 2026-07-08 kotlin-winrt/skiko validation
  baseline. Keep the existing scanner, task graph, packaging, and transitive
  artifact checks as regression coverage rather than active upstream
  workarounds.
- The required application initialization chain remains
  `WinRtWindowsAppSdkBootstrap.initialize()` -> `RuntimeScope.initializeSingleThreaded()`
  -> `Application.start { ... }`; repository-local sample tasks now launch it
  through kotlin-winrt typed run tasks rather than nested Gradle wrappers.
- `KWINRT-024`, `KWINRT-030`, `KWINRT-031`, and `KWINRT-032` are no longer
  reproduced by the current compose-winui validation baseline. On 2026-07-06,
  `:compose:ui:ui:compileKotlinWinuiJvm`, `:compose:ui:ui:winuiJvmTest`,
  `:compose:ui:ui:winui-samples:runWinUIViewSample`, and
  `:compose:mpp:demo-winui:runWinUIMppSample` pass with
  `-PcomposeWinUi.enableJvmTarget=true --no-configuration-cache
  --no-configure-on-demand`. The 2026-07-09 baseline additionally validates
  `:compose:ui:ui:winui-samples:runWinUISkikoSample --rerun-tasks`, and
  `:compose:mpp:demo-winui:runWinUIMppSample` after the typed run-task
  migration. The 2026-07-15 snapshot 80 validation additionally passes
  `:compose:foundation:foundation:compileKotlinWinuiJvm` and
  `:compose:ui:ui:compileKotlinWinuiJvm`. Snapshot 81 additionally passes the
  repository-local `runWinUISkikoSample` end to end with refreshed
  dependencies, closing `KWINRT-062`.
- KMP graph baseline remains important: keep testing customized source sets,
  transitive identity, support artifact merging, authored application hosts,
  and multi-module sample consumption. Do not regress to a single-module JVM
  sample as the only kotlin-winrt validation shape.

## skiko-winui status

- `SKIKO-007`: Closed in `skiko-winui` `0.0.0-20260606.031302-6`.
  The latest `skiko-winui` jar no longer publishes `microsoft/**` or
  `windows/**` projection classes; direct jar inspection reports zero entries
  under those package roots, including no
  `microsoft/ui/input/InputCursor`, `InputSystemCursor`, `UIElement`, or
  `Grid` classes. compose-winui now uses explicit `type(...)` declarations for
  the required Windows SDK and Windows App SDK projection surface rather than
  consuming prebuilt full projection artifacts. With kotlin-winrt snapshots from
  2026-06-07, `:compose:ui:ui:compileKotlinWinuiJvm`,
  `:compose:ui:ui:winuiJvmTest`, `runWinUISkikoSample`, and
  `runWinUIViewSample` pass, so the original overlapping-projection classpath
  shadowing issue and the later `Application.start` callback hang are fixed for
  the current compose-winui path.

  Historical context: `skiko-winui`
  `0.0.0-20260605.111531-5` still publishes `microsoft/**` and `windows/**`
  projection classes in the same packages as compose-winui's generated
  kotlin-winrt projections. The native host classpath loads `skiko-winui`
  before `ui-winuijvm`, so these bundled projections can shadow the local
  generated projections. Observed failures include
  `InputSystemCursor cannot inherit from final InputCursor`, `UIElement` missing
  Kotlin `$stable`, `XamlControlsResources` inheriting from a final
  `ResourceDictionary`, and `WinUISkiaHostPanel` inheriting from a final
  `Grid` when compose-ui owns the generated `Grid`. Direct WinMD inspection
  shows `Microsoft.UI.Input.InputCursor` is not sealed while
  `InputSystemCursor` is sealed and extends `InputCursor`; `Grid` is also not
  sealed and extends `Panel`. This points to incompatible projection ownership
  or stale generator shape in the published `skiko-winui` artifact, not WinMD
  defining the base classes as final. A staged-jar stripping experiment proved
  this is broader than one class: removing all bundled projections avoids some
  shadowing but breaks skiko's authored support graph with missing classes such
  as `microsoft/ui/input/IInputObject`. Upstream should either publish
  `skiko-winui` without shared WinRT/WinUI projection classes, or republish it
  with the exact kotlin-winrt snapshot/shape rules used by compose-winui. Do
  not keep class-by-class stripping as the compose-winui integration path.
  Validation after cleanup with JDK 25 and
  `:compose:ui:ui:compileKotlinWinuiJvm`
  `:compose:ui:ui:winui-samples:runWinRtApplicationHost`
  (`-PcomposeWinUi.enableJvmTarget=true --no-configuration-cache
  --no-configure-on-demand`) compiles `compose-ui`, starts the native host via
  `Application.start`, logs `compose-winui-sample: application created`, and
  then fails with the same `InputSystemCursor` / final `InputCursor`
  `IncompatibleClassChangeError`. Dependency insight confirms this validation
  uses current kotlin-winrt snapshots (`winrt-runtime` / `winrt-runtime-jvm` /
  `winrt-authoring` `0.1.0-20260605.031133-28`) while `skiko-winui` remains
  `0.0.0-20260605.111531-5`. A non-destructive staged classpath experiment
  copied `ui-winuijvm` to a lexically earlier jar name, making compose-ui's
  generated projection classes load before the overlapping `skiko-winui`
  classes; the full native host sample then passed through Skiko render
  diagnostics and the current smoke path. The same projection-owner ordering is
  required for repository-local JavaExec sample tasks such as
  `runWinUISkikoSample`, where the default runtime classpath can otherwise fail
  before the focused Skiko render path starts. compose-winui keeps this as a
  narrow sample-host/JavaExec classpath workaround until `skiko-winui` stops
  publishing overlapping projections or kotlin-winrt exposes explicit classpath
  ownership/order. Retest on 2026-06-06 after refreshing to kotlin-winrt
  snapshots `winrt-runtime-jvm` `0.1.0-20260605.202352-37` and
  `winrt-gradle-plugin` `0.1.0-20260605.202635-18` still resolves
  `skiko-winui` / `skiko-winui-windows` to `0.0.0-20260605.111531-5`.
  The new plugin DSL requires
  `windowsSdk(version, includeExtensions, generateProjection)` and
  `nugetPackage("Microsoft.WindowsAppSDK", version) { generateProjection =
  true }` for the existing explicit WinUI type projection path. With that
  shape fixed, `:compose:ui:ui:compileKotlinWinuiJvm` passes. Running
  `:compose:ui:ui:winui-samples:runWinRtApplicationHost` with
  `--no-configuration-cache --no-configure-on-demand
  -x :compose:ui:ui:winui-samples:prioritizeComposeWinUiProjectionJarForHost`
  still fails after `compose-winui-sample: application created` with the same
  `IncompatibleClassChangeError: class microsoft.ui.input.InputSystemCursor
  cannot inherit from final class microsoft.ui.input.InputCursor`, so the
  projection shadowing issue is not fixed by the latest available snapshots.
  The normal native host path with the staged `00-ui-winuijvm` workaround still
  passes the full smoke path.
- `SKIKO-004`: Mitigated locally; not an active upstream/open issue for the
  current compose-winui path. An unattached
  `WinUIComposeView` render smoke can hang in `DirectContext.flushAndSubmit`;
  this differs from skiko's own sample because the skiko sample renders through
  a layer already hosted by a real WinUI window. Compose-winui now attaches the
  direct `microsoft.ui.xaml.Window.setContent` root before starting composition,
  defers `WinUIComposeView` frame-scheduler startup until the root is loaded,
  keeps diagnostics unit-tested, and validates both an unattached scheduler
  deferral path and an attached-window render diagnostics frame with a positive
  platform render size, matching rendered-state size, and no pending invalidated
  render state in the repository-local full sample and focused
  `runWinUISkikoSample` task. The same focused sample now verifies both paths
  report the Skiko WinUI Direct3D render API and records non-empty Compose draw
  bounds after an attached render, and dispatches a Skiko click action back to
  Compose semantics. The 2026-06-03 23:00 +08 cache-clear retest resolves
  `skiko-winui` / `skiko-winui-windows` to `0.0.0-20260603.150039-4`; WinUI JVM
  compile, focused Skiko/owner/isolation tests, and `runWinUISkikoSample` pass.
  This should not be treated as proof that latest upstream still hangs; reopen
  only with a deliberate upstream-style unattached scheduler repro on the
  current snapshot. Pixel-read nonblank frame validation is still deferred until
  there is a stable attached-window pixel-read path.
- `SKIKO-006`: Closed. Current WinUI JVM runtime and test classpaths no longer
  resolve `org.jetbrains.skiko:skiko-awt` or `org.jetbrains.skiko:skiko-jvm-api`
  as consumer dependencies; dependency insight reports no matching dependencies
  for `winuiJvmRuntimeClasspath` or `winuiJvmTestRuntimeClasspath` as of the
  2026-07-06 validation baseline.
- `SKIKO-005`: Closed in `skiko-winui` `0.0.0-20260603.075139-3`.
  `WinUISkikoRenderHost` now consumes the public typed render diagnostics API
  directly instead of reflecting `getRenderDiagnostics$skiko_winui`.
- `SKIKO-003`: Closed on 2026-06-03. `WinUIComposeView.updateRootContent` now
  flushes pending root-content transactions even when the interop overlay
  identity is unchanged, and the sample validates WinUIView overlay children
  separately from the Skiko render base child. With JDK 25,
  `:compose:ui:ui:compileKotlinWinuiJvm` passes and
  `:compose:ui:ui:winui-samples:runWinUIViewSample` reaches the full current
  smoke path. The later `KWINRT-024` native teardown crash no longer reproduces
  with current kotlin-winrt Maven snapshots.
- `SKIKO-002`: Closed in the 2026-06-03 Maven snapshots. The published
  skiko-winui and kotlin-winrt snapshots carry the generic WinRT support needed
  by `WinUISkiaLayer`; the sample no longer fails with the generic-support
  `NoSuchMethodError`.
- `SKIKO-001`: Closed. The Maven snapshot coordinates are
  `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT` plus
  `io.github.compose-fluent:skiko-winui-windows:0.0.0-SNAPSHOT`.
