# skiko-winui issues found by compose-winui

This file tracks Skiko / skiko-winui gaps that affect compose-winui. Use stable
`SKIKO-###` ids from compose-winui workaround comments so the workaround can be
removed when the upstream behavior is fixed.

Keep closed issues short. They should state the final outcome and validation
baseline, not every retest attempt.

## Current upstream triage

- **skiko-winui build in use:** `winui_dev` `6e5918d58`, built against
  kotlin-winrt `xaml-support` `3d7855783` and published to Maven Local from the
  local checkout (since 2026-10-04; before that a `skiko/build/repo` built
  against the kotlin-winrt snapshot from `master`).
- **Open upstream/publication coordinates:** `SKIKO-011`.
- **Open upstream/publication/API:** `SKIKO-012`.
- **Open upstream/build:** `SKIKO-013`.
- **Open compose-side integration:** none.
- **Open compose-side workarounds:** `SKIKO-011`, `SKIKO-012`.
- **Closed/fixed or superseded:** `SKIKO-001`, `SKIKO-002`, `SKIKO-003`,
  `SKIKO-004`, `SKIKO-005`, `SKIKO-006`, `SKIKO-007`, `SKIKO-008`,
  `SKIKO-009`, and `SKIKO-010`.

## SKIKO-011: Published skiko-winui snapshot predates the m154 sync

- **Status:** Open.
- **Observed in:** the 2026-10-04 upstream sync. Compose now needs Skiko
  0.153.0 or newer; `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT` on
  Maven Central snapshots is from 2026-07-26, before skiko-winui merged
  upstream `v0.154.0-alpha01+5` (Skia m154) and moved to the kotlin-winrt
  Windows toolkit plugin.
- **compose-winui workaround:** skiko-winui is built from the local checkout
  and published with `publishSkikoWinuiToMavenLocal`.
  `buildSrc-fork/repos.gradle` reads `COMPOSE_WINUI_MAVEN_REPO`, a list of
  local Maven repositories that are consulted first for the group
  `io.github.compose-fluent`. Point it at the Maven Local repository
  (`%USERPROFILE%\.m2\repository`), so that this build, and the kotlin-winrt
  build next to it, win over the published snapshots of the same version.
  Until 2026-10-04 it pointed at `<skiko>/skiko/build/repo`.
- **Remove when:** snapshots of the skiko-winui and kotlin-winrt branches that
  compose-winui tracks are published.

## SKIKO-013: skiko-winui does not build against kotlin-winrt `xaml-support` as is

- **Status:** Open. Worked around with uncommitted changes in the local
  skiko checkout.
- **Observed in:** `publishSkikoWinuiToMavenLocal` of skiko-winui `winui_dev`
  `6e5918d58` on 2026-10-04, with kotlin-winrt `xaml-support` `fa4508d7c` and
  then `4c1a850f4` in Maven Local.
- **Why compose-winui needs it:** skiko-winui carries the projections that its
  kotlin-winrt plugin generated and the WinRT identity that the plugin of a
  consumer reads (`KWINRT-069`), so both have to come from the same
  kotlin-winrt build.
- **Failures:**
  - `skiko/gradle/winui.gradle.kts` resolves the plugin and the runtime modules
    from the Central snapshot repository first and has no Maven Local, so it
    builds against the snapshot published from `master`.
  - `generateWinRTXamlApplicationHeader` fails with `fa4508d7c`
    (`KWINRT-075`). With `4c1a850f4` it passes, and the XAML schema that the
    plugin exports for every library then fails `compileKotlinWinuiJvm` on
    the deprecated `FontRastrSettings`.
- **Local changes in `skiko/gradle/winui.gradle.kts`:** Maven Local first for
  the kotlin-winrt group, in the `buildscript` block and in the project
  repositories; `windows { xaml { exportLibrarySchema.set(false) } }`.
- **Validation:** `publishSkikoWinuiToMavenLocal` with
  `-Pskiko.winui.enabled=true -Pskiko.winui.jvmTarget=25
  -Pskiko.winui.jvmToolchain=25 -Pskiko.winui.mingw.enabled=true` passes and
  publishes `skiko-winui`, `skiko-winui-jvm`, `skiko-winui-windows`,
  `skiko-winui-mingw` and `skiko-winui-mingw-runtime`. The skiko-winui tests
  and samples were not run.

## SKIKO-012: skiko-winui has no Skottie artifact

- **Status:** Open.
- **Observed in:** `compose/mpp/demo`, whose `LottieAnimation.kt` uses
  `org.jetbrains.skia.skottie`. Upstream moved Skottie out of the core Skiko
  artifact into `skiko-skottie`, which has no WinUI targets.
- **compose-winui workaround:** `demo-winui` excludes `LottieAnimation.kt` and
  compiles `WinUILottieAnimation.kt`, a placeholder with the same signature.

## SKIKO-010: WinUI popup window surface resize can native-crash

- **Status:** Closed as no longer reproduced with current snapshots.
- **Observed in:** `:compose:ui:ui:winui-samples:runWinUIWindowPopupSample`
  while adding `compose.layers.type=WINDOW` popup support on 2026-06-10, with
  current `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`.
- **Failure:** creating a separate WinUI `Window` for popup content, opening it
  after composition, and then shrinking the attached `WinUIComposeView` /
  Skiko root from the parent-window-sized layout surface to the measured popup
  content size exited the sample JVM with `NTSTATUS 0xC000027B`.
- **Evidence:** temporary diagnostics showed the process survived popup window
  creation, content assignment, `AppWindow.show(false)`, and the first zero-size
  layout pass, then crashed immediately after the popup content first measured
  to `64 x 32` and before the smoke could dispose the popup. Keeping the popup
  Compose root sized to the parent window while moving/resizing only the native
  popup `AppWindow` to the measured content size makes the same smoke pass.
- **Resolution:** the issue no longer reproduces with `skiko-winui`
  `0.0.0-SNAPSHOT:20260703.142141-28` and `skiko-winui-jvm`
  `0.0.0-SNAPSHOT:20260703.142141-11`. On 2026-07-04,
  `:compose:ui:ui:winui-samples:runWinUIWindowPopupSample` reached
  `compose-winui-sample: window popup` and completed successfully after a
  transient `buildWinRTAuthoringHost` failure was cleared by rerunning that
  task. No fresh related WER dump was found. Keep this entry closed unless the
  popup smoke or a smaller reproducer hits the native crash again.

## SKIKO-009: WinUI JVM tests resolve incompatible Skiko/Skia API shape

- **Status:** Closed as fixed by current dependency alignment.
- **Observed in:** `:compose:ui:ui:winuiJvmTest` on 2026-06-10 after aligning
  WinUI rendering to the upstream `FrameRecomposer` pipeline, with current
  `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT` and Skiko snapshot
  dependencies.
- **Failure:** the WinUI JVM test task compiles and runs 137 tests, but fails
  four tests with `NoSuchMethodError` from Skia/Skiko API calls:
  `TextStyle.setFontEdging(FontEdging)` during paragraph layout, and
  `Canvas.drawPicture(..., Paint)` from the draw-bounds recorder path.
- **Evidence:** `runWinUISkikoSample` and the full
  `runWinUIMppSample` validation both pass in the same workspace, so this is
  currently isolated to test paths that exercise these Skia APIs directly. The
  runtime classpath still loads Skiko classes from snapshot artifacts such as
  `skiko-awt-0.0.0-SNAPSHOT.jar` for JVM API classes, while WinUI rendering
  consumes `skiko-winui`.
- **Resolution:** the issue no longer reproduces with `skiko-winui`
  `0.0.0-SNAPSHOT:20260703.142141-28`, `skiko-winui-jvm`
  `0.0.0-SNAPSHOT:20260703.142141-11`, and Skiko snapshot
  `0.0.0-SNAPSHOT:20260630.121153-*`. On 2026-07-04,
  `:compose:ui:ui:winuiJvmTest` completed successfully, including the
  draw/text diagnostics that previously failed with `NoSuchMethodError`.

## SKIKO-008: MPP sample render diagnostics getters can native-crash after upstream sync

- **Status:** Closed as no longer reproduced with current render diagnostics.
- **Observed in:** `:compose:mpp:demo-winui:smokeWinUIMppSampleRenderOutput`
  after syncing upstream `origin/jb-main` into `winui_dev` on 2026-06-10,
  with current `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`.
- **Failure:** the MPP sample validation process exits with `NTSTATUS
  0xC000027B`. The validation report shows the process can compose content and
  read `GraphicsApi.DIRECT3D`, but repeated retests crash when the smoke reads
  fine-grained render diagnostics such as the last platform render result,
  last rendered state size, or Compose draw bounds from the attached
  `WinUIComposeView` path.
- **Evidence:** cdb analysis of the matching failure class reports a WinUI
  stowed exception around `CoreMessagingXP!DispatcherQueue::DeferInvokeCallback`
  with `HRESULT 0x8007000e`. The MPP smoke report stopped immediately after
  `render-direct3d`, then after avoiding that getter stopped at
  `render-state-size-matched`, which isolated the crash to validation-side
  diagnostics reads rather than normal sample composition.
- **Resolution:** the MPP smoke now reads the attached `WinUIComposeView`
  render diagnostics again and no longer native-crashes with `skiko-winui`
  `0.0.0-SNAPSHOT:20260703.142141-28` and `skiko-winui-jvm`
  `0.0.0-SNAPSHOT:20260703.142141-11`. On 2026-07-04,
  `:compose:mpp:demo-winui:smokeWinUIMppSampleRenderOutput` completed
  successfully and recorded `render-direct3d`, `render-positive-size`,
  `render-state-size-matched`, `non-empty-draw-bounds`, and `frame-observed`.

## SKIKO-007: skiko-winui projection publication was misclassified as a Skiko issue

- **Status:** Closed as compose-winui wiring error.
- **Observed in:** earlier `:compose:mpp:demo-winui:runWinUIMppSample`
  validation while compose-winui was still trying to keep the runtime classpath
  single-owner by filtering `skiko-winui`.
- **Current finding:** publishing WinRT/WinUI projection classes from
  `skiko-winui` is expected when those projections are part of Skiko's authored
  WinUI surface. Compose-winui should not strip those classes or require Skiko
  to stop publishing them.
- **Evidence:** the current `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`
  jar legitimately contains `microsoft/**` and `windows/**` projection classes
  used by Skiko's authored `WinUISkiaHostPanel`. The duplicate class symptom
  was caused by compose-winui importing local WinUI artifacts as anonymous
  `files(...)` jar dependencies and then trying to clean up projection
  ownership at runtime.
- **compose-winui action:** removed the filtered `skiko-winui` runtime-jar
  workaround from the sample run tasks and switched the MPP sample back to
  normal Gradle project dependencies for local WinUI artifacts.

## SKIKO-006: WinUI JVM path used to need the skiko-awt API artifact

- **Status:** Closed.
- **Observed in:** compose-winui dependency isolation while validating
  `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`
  snapshots before `0.0.0-SNAPSHOT:20260704.171250-30`.
- **Failure:** before the fix, excluding `org.jetbrains.skiko:skiko-awt` from WinUI
  configurations removes the JVM API classes needed by compose-winui and
  `skiko-winui`, including `org.jetbrains.skia.Canvas`,
  `org.jetbrains.skiko.GraphicsApi`, and
  `org.jetbrains.skiko.SkikoRenderDelegate`; `compileKotlinWinuiJvm` then
  fails. Dependency insight shows `skiko-awt` arrives through
  `org.jetbrains.skiko:skiko`, including the `skiko-winui -> skiko` path.
- **Why this matters:** compose-winui must remain independent from Desktop/AWT
  runtime behavior. The source code does not use AWT or `SkiaLayer`, but the
  current JVM artifact naming and dependency shape still put core Skia/Skiko
  JVM APIs in an AWT-named artifact.
- **Resolution:** `skiko-winui` commit `ee25d210` keeps the internal
  `skiko-jvm-api` compile dependency as compile-only for `winui-jvm`, so
  `skiko-winui-jvm` no longer publishes `org.jetbrains.skiko:skiko-jvm-api` as
  a consumer dependency. compose-winui now treats `skiko-winui` as the WinUI
  replacement for the regular Skiko distribution by substituting
  `org.jetbrains.skiko:skiko` to
  `io.github.compose-fluent:skiko-winui` only for WinUI JVM configurations.
- **2026-06-03 23:16 +08 snapshot retest:** with `skiko-winui`
  `0.0.0-20260603.150039-4`, Gradle dependency insight for
  `winuiJvmRuntimeClasspath` still resolves `org.jetbrains.skiko:skiko-awt`
  through `org.jetbrains.skiko:skiko:0.0.0-SNAPSHOT`, so the issue was still
  open at that snapshot.
- **2026-07-04 snapshot retest:** with `skiko-winui`
  `0.0.0-SNAPSHOT:20260703.142141-28`, `skiko-winui-jvm`
  `0.0.0-SNAPSHOT:20260703.142141-11`, and Skiko snapshot
  `0.0.0-SNAPSHOT:20260630.121153-*`, Gradle dependency insight for both
  `winuiJvmRuntimeClasspath` and `winuiJvmTestRuntimeClasspath` still resolves
  `org.jetbrains.skiko:skiko-awt` through `org.jetbrains.skiko:skiko` and the
  `skiko-winui-jvm -> skiko-winui` path, so the issue was still open at that
  snapshot.
- **2026-07-05 validation:** GitHub Actions run `28713142890` published
  `skiko-winui` `0.0.0-SNAPSHOT:20260704.171250-30`,
  `skiko-winui-jvm` `0.0.0-SNAPSHOT:20260704.171250-13`, and
  `skiko-winui-windows` `0.0.0-SNAPSHOT:20260704.171250-30`. With JDK 25,
  `:compose:ui:ui:dependencyInsight --configuration winuiJvmTestRuntimeClasspath
  --dependency org.jetbrains.skiko:skiko-jvm-api`,
  `:compose:ui:ui:dependencyInsight --configuration winuiJvmTestRuntimeClasspath
  --dependency org.jetbrains.skiko:skiko-awt`, and
  `:compose:ui:ui:winuiJvmTest` all pass with `--refresh-dependencies
  -PcomposeWinUi.enableJvmTarget=true --no-configuration-cache
  --no-configure-on-demand`. Both dependency insight commands report no
  matching dependencies, and `winuiJvmTest` completes successfully.
- **2026-07-06 local validation:** with current `0.1.0-SNAPSHOT` kotlin-winrt
  Maven artifacts and current `skiko-winui` snapshots, dependency insight for
  `org.jetbrains.skiko:skiko-awt` reports no matching dependencies in both
  `winuiJvmRuntimeClasspath` and `winuiJvmTestRuntimeClasspath`. The full
  compose-winui baseline also passes `compileKotlinWinuiJvm`, `winuiJvmTest`,
  `runWinUIViewSample`, and `runWinUIMppSample`.

## SKIKO-005: published render diagnostics API is still internal

- **Status:** Fixed in `skiko-winui` Maven snapshot
  `0.0.0-20260603.075139-3`.
- **Observed in:** `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`
  timestamped build `0.0.0-20260603.023842-2` while replacing
  compose-winui's reflective render diagnostics bridge with the typed
  `WinUISkiaLayer.renderDiagnostics` API visible in the local
  `compose-fluent/skiko` `winui_dev` branch at `14aee24f`.
- **Failure:** `:compose:ui:ui:compileKotlinWinuiJvm` fails because the
  published artifact still exposes `WinUISkiaLayer.renderDiagnostics` and
  `WinUILayerRenderDiagnostics` / `WinUIPlatformRenderResult` /
  `WinUILayerRenderState` / `WinUILayerRenderFailure` as internal declarations.
- **Resolution:** compose-winui now reads `WinUISkiaLayer.renderDiagnostics`
  directly in `WinUISkikoRenderHost`, removing the reflection bridge while
  still avoiding AWT/Desktop APIs.
- **Validation baseline:** after clearing the targeted Gradle snapshot cache,
  Gradle resolved `skiko-winui` and `skiko-winui-windows` to
  `0.0.0-20260603.075139-3`. With JDK 25,
  `:compose:ui:ui:compileKotlinWinuiJvm`,
  `WinUISkikoRenderHostTest`, and the focused
  `:compose:ui:ui:winui-samples:runWinUISkikoSample` task pass using
  `-PcomposeWinUi.enableJvmTarget=true --no-configuration-cache
  --no-configure-on-demand`.

## SKIKO-004: WinUI Skiko surface can hang in flushAndSubmit

- **Status:** Mitigated locally on 2026-06-10 by async-coalescing
  Compose-origin render requests before delegating to Skiko WinUI.
- **Observed in:** `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`
  while adding render diagnostics to the repository-local
  `runWinUIViewSample` smoke path on 2026-06-03.
- **Failure:** A standalone `WinUIComposeView` created by the sample, sized with
  the test hook, and rendered without first attaching it to a WinUI `Window`
  left the sample JVM alive indefinitely. A `jcmd Thread.print` showed the
  WinUI UI thread inside
  `org.jetbrains.skia.DirectContext.flushAndSubmit`, called through
  `WinUISkiaLayerPlatformInterop.drawAndPresent`,
  `WinUISkiaLayer.renderNow`, and the `WinUIFrameScheduler` timer callback.
- **Why skiko's own sample does not show it:** the skiko sample exercises a
  Skia layer after it is hosted by a real WinUI window/surface. The compose
  smoke that exposed this issue rendered an unattached compose view, which is
  not the same lifetime or presentation path.
- **Current compose-winui action:** do not validate rendered frames from an
  unattached `WinUIComposeView`. `microsoft.ui.xaml.Window.setContent` now
  installs the compose root into the WinUI `Window` before starting composition,
  so that direct window entry point does not briefly start the Skiko frame
  scheduler on an unattached root. `WinUIComposeView` also defers starting the
  Skiko frame scheduler until its root is loaded, and removes the pending
  `Loaded` token on composition/view disposal. Keep render-host diagnostics
  covered by `WinUISkikoRenderHostTest`; the repository-local sample validates
  both that an unattached `WinUIComposeView` does not start the Skiko frame
  scheduler and that an attached-window render diagnostics frame succeeds with
  a positive platform render size whose dimensions match the last rendered
  state, with no pending invalidated render state left after the frame. It also
  verifies both paths report `GraphicsApi.DIRECT3D` from the Skiko WinUI layer.
  The focused sample now records non-empty Compose draw bounds during the
  attached render, verifies `disposeComposition()` clears the recorded bounds
  back to `Rect.Zero`, and validates the Skiko WinUI accessibility provider can
  expose a tagged Compose semantics node from the render surface and dispatch a
  Skiko click action back to Compose semantics. Focused unit coverage also
  validates Skiko accessibility actions dispatching back to Compose semantics
  for focus, click, expand, collapse, set-text, and progress
  increment/decrement. A focused `runWinUISkikoSample` task now runs just those
  Skiko diagnostics and exits successfully. The current upstream diagnostics
  expose render state/result metadata but no pixel-read or surface snapshot API,
  so nonblank frame validation remains blocked on a stable attached-window
  pixel-read path.
- **2026-06-03 23:00 +08 snapshot retest:** after clearing the targeted
  `skiko-winui` and `skiko-winui-windows` Gradle snapshot caches under
  `GRADLE_USER_HOME=F:\Dependencies\gradle`, Gradle resolved both artifacts to
  `0.0.0-20260603.150039-4`. With JDK 25,
  `:compose:ui:ui:compileKotlinWinuiJvm`, focused `WinUIOwnerTest`,
  `WinUISkikoRenderHostTest`, `WinUISourceSetIsolationTest`, and
  `:compose:ui:ui:winui-samples:runWinUISkikoSample` pass. At this historical
  snapshot, the full `runWinUIViewSample` still reached the final smoke log and
  exited with the then-known non-blocking `KWINRT-024` `NTSTATUS 0xC0000005`
  teardown crash, without producing a newer WER dump.
- **2026-06-10 interactive retest:** the full attached
  `:compose:mpp:demo-winui:runWinUIMppSampleInteractive` sample became
  unresponsive after inactive/active window interaction. The actual Java
  process was still alive, but `jcmd Thread.print` showed the WinUI main thread
  runnable inside `org.jetbrains.skia.DirectContext.flushAndSubmit`, through
  `WinUISkiaLayerPlatformInterop.drawAndPresent`,
  `WinUISkiaLayer.renderNow`, and `WinUIRenderDispatcher.needRender`. This was
  not just the earlier unattached smoke path and was unrelated to the old
  context-menu interaction logs still present in the process output.
- **Current compose-winui action:** keep using the real Skiko WinUI rendering
  layer and keep `WinUISkiaLayer.startFrameScheduler()` enabled for attached
  roots. Disabling the continuous scheduler is not a correct fix because Skiko
  may need to drive frames independently of Compose invalidations. Compose-winui
  now has opt-in `compose.winui.render.debug` logging around window activation,
  size changes, render-request coalescing, and draw submission so the next
  interactive reproduction can identify whether the hang is tied to a specific
  activation/resize state or to `flushAndSubmit(surface, true)` itself.
- **2026-06-10 pointer retest:** after delaying render requests raised during
  Skiko draw callbacks, the taskbar click could show the window again, but
  Windows still marked it unresponsive. A fresh `jcmd Thread.print` showed a
  second synchronous entry into `flushAndSubmit(surface, true)`, this time from
  a pointer click/navigation callback:
  `PointerInputAdapter -> ClickableNode.performClick -> NavController.navigate
  -> FrameRecomposer.onNewAwaiters -> WinUIComposeView.requestRender ->
  WinUISkiaLayer.needRender -> flushAndSubmit`. Compose-winui now coalesces all
  Compose-origin render requests through `WinUIDispatchQueue` before delegating
  to Skiko, so pointer, navigation, activation, and other WinRT callbacks do not
  synchronously present from their current native event stack.
- **2026-06-10 mitigation retest:** with Compose-origin render requests
  coalesced through `WinUIDispatchQueue`, the full interactive MPP sample no
  longer becomes unresponsive during the previously failing taskbar activation
  and pointer/navigation path. Keep this entry as upstream evidence because
  `WinUISkiaLayer.needRender()` can synchronously enter
  `flushAndSubmit(surface, true)` when called from a WinUI event callback.

## SKIKO-001: skiko-winui artifact coordinates were not obvious

- **Status:** Closed as local integration discovery on 2026-06-02.
- **Observed in:** attempting to replace the current WinUI JVM compile-source
  bridge with a Skiko WinUI dependency for compose-winui.
- **Initial finding:** `org.jetbrains.skiko:skiko-winui` and
  `org.jetbrains.skiko:skiko-winui-runtime-windows-*` do not exist in Maven
  Central. `org.jetbrains.skiko:skiko:0.148.1` also does not publish a WinUI
  variant.
- **Resolution:** the published snapshot is
  `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`, with runtime artifact
  `io.github.compose-fluent:skiko-winui-windows:0.0.0-SNAPSHOT`, from the
  `compose-fluent/skiko` `winui_dev` branch.
- **Snapshot baseline:** Maven snapshot metadata reports timestamped build
  `0.0.0-20260602.094020-1` for both artifacts. The branch head inspected
  locally was `35bda1c61f856f06e6ee012d8609ae13ce1a5fd7`.
- **Dependency shape:** `skiko-winui` depends on
  `org.jetbrains.skiko:skiko:0.0.0-SNAPSHOT`,
  `io.github.compose-fluent:winrt-runtime-jvm:0.1.0-SNAPSHOT`, and runtime
  `io.github.compose-fluent:skiko-winui-windows:0.0.0-SNAPSHOT`. It does not
  pull `skiko-awt`.
- **Current compose-winui action:** `compose/ui/ui` now depends on
  `skiko-winui` in `winuiMain`, adds `skiko-winui-windows` as `winuiJvmMain`
  runtime, and locks WinUI configurations to the matching Skiko snapshot through
  `composeWinUi.skikoVersion`.
- **Validation:** `:compose:ui:ui:compileKotlinWinuiJvm` passes with
  `-PcomposeWinUi.enableJvmTarget=true --no-configuration-cache
  --no-configure-on-demand`. `runWinUIViewSample` reaches the full current
  smoke path. The known `KWINRT-024` native teardown crash is deferred as
  non-blocking and is not treated as a skiko-winui integration failure.

## SKIKO-002: skiko-winui generic WinRT support is not usable transitively

- **Status:** Fixed upstream in the 2026-06-03 Maven snapshots.
- **Observed in:** `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`
  while constructing `org.jetbrains.skiko.winui.WinUISkiaLayer` from
  compose-winui.
- **Failure:** Older snapshots let `runWinUIViewSample` start the WinUI
  application, then fail during `WinUISkiaHostPanel` construction with:
  `NoSuchMethodError: androidx.compose.ui.winui.samples.WinUIViewSampleKt.kotlinWinRtGenericTypeInstantiationInitializeBySourceType(String)`.
- **Resolution:** After clearing the Gradle snapshot cache, compose-winui
  resolved `skiko-winui` timestamped build `0.0.0-20260603.023842-2`,
  `winrt-gradle-plugin` `0.1.0-20260603.021843-3`, and
  `winrt-compiler-plugin` `0.1.0-20260603.021535-22`. The sample no longer
  throws the generic-support `NoSuchMethodError` and reaches Compose content.
- **Validation baseline:** `:compose:ui:ui:compileKotlinWinuiJvm` passes with
  `-PcomposeWinUi.enableJvmTarget=true --no-configuration-cache
  --no-configure-on-demand`; `:compose:ui:ui:winui-samples:runWinUIViewSample`
  now fails later in compose-winui's local interop root smoke path, tracked as
  `SKIKO-003`.

## SKIKO-003: compose-winui interop root smoke does not install wrapper after Skiko host hookup

- **Status:** Fixed compose-side on 2026-06-03.
- **Observed in:** `runWinUIViewSample` after `WinUISkikoRenderHost` is backed
  by `io.github.compose-fluent:skiko-winui:0.0.0-SNAPSHOT`.
- **Failure:** The sample enters Compose content and then fails in
  `ComposeWinUiSmokeApp.runWinUIViewLifecycleSmoke` with:
  `IllegalStateException: WinUIView lifecycle smoke did not install a wrapper into the root host.`
- **Evidence:** `rootHost.content` is already the expected interop `Canvas`,
  but `rootCanvas.requiredChildren.singleOrNull()` is null. The previous
  `SKIKO-002` generic-support exception is gone.
- **Resolution:** `WinUIComposeView.updateRootContent` now flushes pending
  root-content transactions even when the interop overlay identity has not
  changed, so the Skiko render base child cannot leave queued WinUI child
  updates unapplied. The sample smoke now treats the Skiko render surface as a
  root `Canvas` base child and validates only interop overlay children when
  checking wrapper installation, ordering, and removal.
- **Validation:** With `JAVA_HOME=C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot`,
  `:compose:ui:ui:compileKotlinWinuiJvm` passes using
  `-PcomposeWinUi.enableJvmTarget=true --no-configuration-cache
  --no-configure-on-demand`. `:compose:ui:ui:winui-samples:runWinUIViewSample`
  reaches the full current smoke path, including `text input session
  cancellation`. The later known `KWINRT-024` native process-exit crash is
  tracked separately as non-blocking.
