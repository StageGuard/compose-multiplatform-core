# compose-winui implementation plan

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
  (`KWINRT-052`), includes `demo-winui`, `winui-samples` and the two
  `*-winui` navigation modules, and replaces `desktop()` with `winuiJvm` in
  the modules that have a WinUI target. Without the flag the build is
  upstream's and runs on JDK 21.
- [x] Moved to the kotlin-winrt Windows toolkit plugin
  (`io.github.compose-fluent.windows-toolkit`, `windows { packageReferences { }
  application { } }`), Windows App SDK 2.2.0, and `RunWinAppHostTask` run
  tasks instead of nested Gradle builds. Workarounds for the plugin published
  from `master` were `KWINRT-052` to `KWINRT-061`; see the next section for
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
  (`KWINRT-055`) and the explicit additions to the projection compilation
  classpath (`KWINRT-056`). `winui-samples` no longer filters its host
  classpath (`KWINRT-057` only affects `demo-winui`).
- [x] New with `xaml-support`: `:compose:ui:ui` switches off the XAML schema
  export that the plugin runs for every library, with
  `windows { xaml { exportLibrarySchema = false } }`. The export failed on
  `fa4508d7c` (`KWINRT-062`, fixed in kotlin-winrt PR #15). The
  `demo-winui` graph validation expects the authoring registrar
  `WinRTAuthoringTypeDetailsRegistrar_ui_winuiMain`, which the plugin now names
  after the source set.
- [x] Removed with kotlin-winrt PR #16: the workarounds for `KWINRT-054`
  (disabled Android projection compilation), `KWINRT-057` (`demo-winui` host
  class path filter), `KWINRT-058` (`runtime\bin` first on `PATH`) and
  `KWINRT-060` (`duplicatesStrategy` in `winui-samples`). `KWINRT-053` is
  fixed too: WinUI builds no longer need configuration on demand.
- [x] Still there: the toolkit is applied after `androidXMultiplatform`, and
  `buildSrc-fork` tells the toolkit's `KotlinBaseApiPlugin` apart from AGP
  built-in Kotlin (see `KWINRT-054`). `KWINRT-052` (Kotlin 2.4.0),
  `KWINRT-059` (`vswhere.exe` on `PATH`) and `KWINRT-061` (JDK 25) are
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
  as well (`KWINRT-054`).
- [x] Without the WinUI flag, on JDK 21: `compileKotlinDesktop` of `ui`,
  `ui-skiko`, `foundation`, `material`, `material3`, `navigation-compose`,
  `navigation3-ui` and the MPP demo passes.
- [ ] Not run: Android, iOS/macOS, web and desktop test tasks; the skiko-winui
  test suite; the kotlin-winrt suites other than the tests added with the
  fixes.

## Architecture
- [x] Implement compose-winui as a standalone `compose-ui` platform target, comparable in responsibility to `androidMain`.
- [x] Keep compose-winui independent from `desktopMain`, AWT, Swing, and Skiko AWT/desktop runtime behavior.
- [x] Place `winuiMain` under `skikoMain` in the source-set graph because the upstream `uiMain` inheritance graph must remain unchanged.
- [x] Preserve WinUI/XAML actuals where `skikoMain` uses generic or Win32-backed behavior; use a WinUI-first compile bridge until `skiko-winui` can supply the rendering sources that should be shared.
- [x] Use `kotlin-winrt` as the WinRT and WinUI projection/runtime foundation instead of duplicating COM or Windows App SDK bootstrap code in `compose-ui`.
- [x] Share WinUI-specific Compose semantics in `winuiMain`, with `winuiJvmMain` providing current JVM runtime details and `winuiMingwMain` deferred until `kotlin-winrt` provides mingw support.
- [x] Treat Android `AndroidView` interop as the behavioral reference for factory, update, reuse, detach, release, layout, focus, and input behavior.

## UIKit target parity gap
- [ ] Treat the existing UIKit target as the near-term architecture reference for production readiness, not just Android/Desktop. The WinUI target currently has a real `Owner`, recomposer, application/window domain, and basic `WinUIView` interop, but it is still well behind UIKit in rendering, accessibility, text input, native interop synchronization, and test depth.
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
- [ ] Close the platform-dependency gap by removing direct ABI event/property workarounds as `kotlin-winrt` generated event sources, interface registries, nullable WinRT properties, and resource/application lifecycle support become reliable in compose-winui.
- [x] Close the test architecture gap with UIKit's `iosTest` / `uikitInstrumentedTest` coverage by splitting the current large WinUI sample smoke into focused tests for scene/rendering, interop, accessibility, keyboard/text input, window/lifecycle, pointer/scroll, resource loading, memory/disposal, and integration launch.
- [ ] Use the following rough maturity target when prioritizing work: Owner/composition/window is around 60-70% of the UIKit architecture shape, WinUIView interop basics are around 50-60%, and overall WinUI target maturity is around 35-45% until rendering host, UI Automation, real IME, transaction-based interop, and broader tests are implemented.

## Gradle targets and source sets
- [x] Add a JVM target for WinUI, for example `jvm("winuiJvm")`, configured for JDK 22 or newer because `kotlin-winrt` JVM support uses the Java Foreign Function and Memory API.
- [ ] Add a Windows native target, `mingwX64("winuiMingw")`, after `kotlin-winrt` implements mingw support.
- [x] Add `winuiMain` under `skikoMain` while keeping WinUI-specific actuals selected for the current WinUI JVM compile path.
- [x] Add `winuiJvmMain` as a dependent of `winuiMain`.
- [ ] Add `winuiMingwMain` as a dependent of `winuiMain` after the mingw target is enabled.
- [x] Add matching test source sets for shared WinUI behavior and target-specific JVM behavior.
- [x] Do not make `winuiMain`, `winuiJvmMain`, or `winuiMingwMain` depend on `desktopMain`, AWT, Swing, or `org.jetbrains.skiko.SkiaLayer`.
- [x] Replace the temporary WinUI JVM compile-source bridge with a principled source-set split now that `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT` is resolvable, so shared Skiko scene/rendering code can be reused without compiling conflicting Skiko generic actuals. Initial Maven dependency wiring, a narrow `WinUISkikoRenderHost` adapter, and the first `WinUIComposeView` render-surface connection compile on 2026-06-02. `SKIKO-002` and `SKIKO-003` are fixed as of 2026-06-03; runtime sample validation reaches the full current smoke path. Latest kotlin-winrt snapshots `0.1.0-20260603.042831-23` / plugin `0.1.0-20260603.043142-4` still leave only the deferred `KWINRT-024` native process-exit crash, which is not a current skiko-winui integration blocker.
- [x] Wire `winuiMain` to the local `kotlin-winrt` runtime without depending on checked-in `winrt-projections`.
- [x] Apply the local `kotlin-winrt` Gradle plugin for WinUI projection generation when running on JDK 22 or newer.
- [x] Declare the Windows App SDK NuGet package through the `winRt` DSL instead of directly depending on projection modules.
- [x] Explicitly declare the minimal WinRT projection `type(...)` entries required by `compose-ui` and repository-local samples for now. The current compose-winui configuration no longer uses prebuilt full projection artifacts and keeps the requested surface in explicit `type(...)` entries. Validation passes with kotlin-winrt snapshots from 2026-06-07 after `KWINRT-029` was fixed upstream; `KWINRT-030` remains as a narrow Gradle checker workaround.

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
- [x] Provide initial WinUI clipboard hooks for `LocalClipboardManager`, `LocalClipboard`, `ClipEntry`, `ClipMetadata`, and `NativeClipboard`; the synchronous `ClipboardManager` text cache is compose-winui policy, not a kotlin-winrt helper requirement.
- [x] Provide initial WinUI text input and IME integration hooks, with minimal stubs only where behavior is explicitly deferred.
- [x] Provide initial WinUI text-toolbar state tracking for copy/paste/cut/select-all/autofill menu requests.
- [x] Provide WinUI accessibility integration hooks that can later map Compose semantics to UI Automation.
- [x] Ensure lifecycle, retained values, and saveable state behavior have WinUI equivalents instead of relying on Android `ViewTree*Owner` APIs.

## WinUI rendering host
- [x] Implement a WinUI-native rendering host that does not require an AWT component or Skiko AWT layer. The first `skiko-winui` surface is installed under the WinUI root content on 2026-06-02. Runtime sample validation now reaches the full smoke path after `SKIKO-003` root-content sync fixes; the known `KWINRT-024` native teardown crash is tracked separately as non-blocking.
- [x] Define the shared `winuiMain` rendering-facing abstraction used by `WinUIComposeView` to request frames, resize, and submit drawing work.
- [x] Add initial unit coverage for the `WinUISkikoRenderHost` adapter lifecycle: render invalidation forwarding, resize forwarding, frame-scheduler reuse, close ordering, idempotent close, suppression of post-close render/resize requests, and render diagnostics exposure.
- [x] Implement the JVM backend in `winuiJvmMain` using `kotlin-winrt`, Windows App SDK bootstrap, DispatcherQueue, and the JVM native interop path.
- [ ] Implement the mingwX64 backend in `winuiMingwMain` after `kotlin-winrt` provides mingw runtime actuals, using Kotlin/Native interop, COM/WinRT initialization, and native Windows APIs.
- [x] Bind the Compose render output to a WinUI-hostable native surface or composition-backed surface owned by the WinUI target. `WinUIComposeView` now draws its root `LayoutNode` into a `skiko-winui` Skia canvas through a WinUI `Canvas` root layer; fuller graphics actuals remain to be implemented, while the existing `KWINRT-024` teardown crash is deferred as non-blocking.
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
  platforms.
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
- [x] Keep target-specific native interop code inside `winuiJvmMain`, and later `winuiMingwMain`; keep shared Compose/WinUI behavior in `winuiMain`.

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
- [ ] Add compile validation for the new WinUI mingwX64 source set after `kotlin-winrt` supports mingw.
- [x] Add tests proving WinUI source sets do not depend on `desktopMain`, AWT, Swing, or Skiko AWT classes, and that WinUI keeps its own XAML/WinRT actuals where Skiko has generic or Win32-backed behavior.
- [x] Add a WinUI JVM compile-source bridge isolation test that keeps the
  temporary `compileKotlinWinuiJvm` source override limited to common,
  jvm/Android, WinUI, generated WinRT sources, and explicitly selected shared
  Skiko sources, without compiling `desktopMain` or the whole generic
  `skikoMain` source tree.
- [x] Add a WinUI JVM runtime classpath guard that rejects Skiko AWT/Desktop
  native runtime artifacts while `SKIKO-006` tracks the temporary need for the
  AWT-named Skiko JVM API jar.
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
- [ ] Add Windows mingwX64 integration smoke test for the same shared `WinUIView` sample after the mingw target is enabled.
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

## kotlin-winrt blockers
- `KWINRT-024`: Still open in the latest published Maven snapshot retest. After
  upgrading compose-winui to Kotlin `2.4.0`, `compileKotlinWinuiJvm`, focused
  WinUI JVM tests, and `runWinUISkikoSample` pass with kotlin-winrt
  `0.1.0-20260604.125452-24`, but the full `runWinUIViewSample` still reaches
  `compose-winui-sample: text input session cancellation` and exits with
  `NTSTATUS 0xC0000005`. Fresh WER dumps were produced at
  `%LOCALAPPDATA%\CrashDumps\java.exe.5868.dmp` and
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).5868.dmp`.
- `KWINRT-027`: Resolved for compose-winui by upgrading this repository to
  Kotlin `2.4.0`; the kotlin-winrt compiler plugin no longer fails with missing
  `org.jetbrains.kotlin.extensions.ExtensionPointDescriptor`, and WinUI JVM
  compilation now reaches runtime validation.
- The local README-required application initialization chain is still
  `WinRtWindowsAppSdkBootstrap.initialize()` -> `RuntimeScope.initializeSingleThreaded()`
  -> `Application.start { ... }`; custom sample `JavaExec` tasks now depend on
  both `stageWinRtRuntimeAssets` and `buildWinRtAuthoringHost`.
- Latest cache-clear retest still resolves Sonatype metadata to kotlin-winrt
  runtime/compiler `0.1.0-20260603.042831-23` and Gradle plugin
  `0.1.0-20260603.043142-4`; the full sample still fails at process teardown
  with fresh XAML dynamic-metadata / unloaded-XAML CDB buckets.
- The 2026-06-03 17:06 +08 fix-claim retest still resolves the same
  kotlin-winrt snapshots; normal and forced `--rerun-tasks` full sample runs
  still reach the final smoke log and exit with `NTSTATUS 0xC0000005`, with no
  newer WER dump.
- The 2026-06-03 18:49 +08 fix-claim retest also resolves the same cached
  kotlin-winrt snapshots; direct metadata for runtime/compiler is unchanged,
  `--refresh-dependencies` is blocked by external TLS handshakes, and the full
  sample still reaches the final smoke log before `NTSTATUS 0xC0000005` without
  producing a newer WER dump.
- The 2026-06-03 19:21 +08 cache-clear fix-claim retest uses the correct
  `GRADLE_USER_HOME=F:\Dependencies\gradle`, re-resolves Gradle to the same
  kotlin-winrt runtime/compiler `0.1.0-20260603.042831-23` and plugin
  `0.1.0-20260603.043142-4`, and the full sample still reaches the final smoke
  log before `NTSTATUS 0xC0000005`; fresh Store CDB evidence is back in the XAML
  dynamic-metadata / unloaded-XAML teardown buckets.
- `KWINRT-023`: Fixed for compose-winui by compiling kotlin-winrt generated
  authoring sources into the WinUI JVM target; the hand-written
  `WinUIXamlApplication` authoring registration workaround has been removed.
- `KWINRT-008`: Mostly fixed for the compose-winui validation path; `App.xaml`
  is no longer required. Keep removing interface/property workarounds only after
  exact compose graph retests prove the generated projection path works.
- `KWINRT-013`: Treated as fixed upstream. The exact fixing commit was not
  identified from compose-winui; reopen only with fresh native crash evidence
  and log/dump analysis.
- `KWINRT-020`: Fixed by the current registry/classloader changes. compose-winui
  removed the keep-screen-on ABI fallback and now uses generated
  `DisplayRequest.requestActive()` / `requestRelease()` directly.
- KMP graph baseline: Keep testing customized source sets, transitive identity,
  and support artifact merging. Do not regress to a single-module JVM sample as
  the only kotlin-winrt validation shape.

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
- `SKIKO-006`: Open upstream/publication/API. The current WinUI JVM path still
  needs `org.jetbrains.skiko:skiko-awt` for core Skia/Skiko JVM API classes, so
  compose-winui guards against `skiko-awt-runtime-*` native runtime artifacts
  while waiting for an AWT-free JVM API artifact. Still observed with
  `skiko-winui` `0.0.0-20260603.150039-4`.
- `SKIKO-005`: Closed in `skiko-winui` `0.0.0-20260603.075139-3`.
  `WinUISkikoRenderHost` now consumes the public typed render diagnostics API
  directly instead of reflecting `getRenderDiagnostics$skiko_winui`.
- `SKIKO-003`: Closed on 2026-06-03. `WinUIComposeView.updateRootContent` now
  flushes pending root-content transactions even when the interop overlay
  identity is unchanged, and the sample validates WinUIView overlay children
  separately from the Skiko render base child. With JDK 25,
  `:compose:ui:ui:compileKotlinWinuiJvm` passes and
  `:compose:ui:ui:winui-samples:runWinUIViewSample` reaches the full current
  smoke path. The known `KWINRT-024` native teardown crash is deferred as
  non-blocking.
- `SKIKO-002`: Closed in the 2026-06-03 Maven snapshots. The published
  skiko-winui and kotlin-winrt snapshots carry the generic WinRT support needed
  by `WinUISkiaLayer`; the sample no longer fails with the generic-support
  `NoSuchMethodError`.
- `SKIKO-001`: Closed. The Maven snapshot coordinates are
  `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT` plus
  `io.github.compose-fluent:skiko-winui-windows:0.0.0-SNAPSHOT`.
