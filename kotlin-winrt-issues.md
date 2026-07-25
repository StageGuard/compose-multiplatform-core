# kotlin-winrt issues found by compose-winui

This file tracks kotlin-winrt gaps that affect compose-winui. Use stable
`KWINRT-###` ids from compose-winui workaround comments so the workaround can be
removed when the upstream behavior is fixed.

Keep closed issues short. They should state the final outcome and validation
baseline, not every retest attempt.

## Current upstream triage

- **Open local runtime/Compose lifetime fix:** `KWINRT-064`. The pointer event
  path creates owned WinRT projections whose lazy default interfaces and
  transient wrappers were not deterministically released. The local
  kotlin-winrt submodule commit `f5f90d75` makes interfaces returned by
  `acquireInterfaceReference(parent, iid)` owned by `parent`; compose-winui
  still needs narrowly scoped pointer-event and retained drag-point cleanup
  before the issue can be closed with JVM and MinGW memory evidence.
- **Closed desktop interop projection gap:** `KWINRT-063`. Snapshot
  `0.1.0-20260719.013720-104` generates the C#/WinRT-style
  `InputPaneInterop.getForWindow(RawAddress)` source addition, and compose-winui
  compiles and passes its focused InputPane tests against the republished Skiko
  WinUI snapshot.
- **Closed Compose-side misdiagnosis:** `KWINRT-061`. The broad Native
  `overrides nothing` failures came from a disconnected Compose fragment
  graph, not kotlin-winrt projection ownership. Selective Skiko source-set
  isolation restores one fragment root, and `compileKotlinWinuiMingw` passes
  with the refreshed kotlin-winrt and Skiko MinGW artifacts.
- **Closed upstream projection gap:** `KWINRT-062`. Snapshot
  `0.1.0-20260715.042347-81` resolves the duplicate interface IID failure and
  the repository-local `runWinUISkikoSample` passes end to end.
- **Closed upstream/plugin modeling gap:** `KWINRT-060`. Snapshot
  `0.1.0-20260714.234543-80` fixes the normal library-mode task graph for
  `compose/foundation/foundation`; it now receives runtime injection through
  the normal plugin without the previous identity/projection cycle.
- **Closed compose-side run-task workarounds:** `KWINRT-056` and
  `KWINRT-059`. The repository-local WinUI sample run tasks now use the typed
  kotlin-winrt run-task API.
- **Compose/application policy, not kotlin-winrt helpers:** `KWINRT-012`
  clipboard synchronization and `KWINRT-019` focus timing.
- **Closed/fixed or superseded:** `KWINRT-001`, `KWINRT-002`, `KWINRT-003`,
  `KWINRT-005`, `KWINRT-006`, `KWINRT-007`, `KWINRT-009`,
  `KWINRT-010`, `KWINRT-011`, `KWINRT-013`, `KWINRT-014`, `KWINRT-015`,
  `KWINRT-016`, `KWINRT-017`, `KWINRT-018`, `KWINRT-020`, `KWINRT-021`,
  `KWINRT-022`, `KWINRT-023`, `KWINRT-026`, `KWINRT-004`, `KWINRT-008`,
  `KWINRT-028`, `KWINRT-029`, `KWINRT-034`, `KWINRT-035`, `KWINRT-036`,
  `KWINRT-037`, `KWINRT-025`, `KWINRT-033`, `KWINRT-039`, `KWINRT-042`,
  `KWINRT-030`, `KWINRT-031`, `KWINRT-032`, `KWINRT-038`, `KWINRT-043`,
  `KWINRT-044`, `KWINRT-045`, `KWINRT-046`, `KWINRT-047`, `KWINRT-041`,
  `KWINRT-048`, `KWINRT-050`, `KWINRT-040`, `KWINRT-049`, `KWINRT-051`,
  `KWINRT-052`, `KWINRT-053`, `KWINRT-054`, `KWINRT-055`, `KWINRT-056`,
  `KWINRT-057`, `KWINRT-058`, `KWINRT-059`, `KWINRT-060`, `KWINRT-062`, and
  `KWINRT-063`.

## KWINRT-064: Pointer projections retain native references on every mouse move

- **Status:** Runtime ownership fixed locally in kotlin-winrt submodule commit
  `f5f90d75`; compose-winui pointer cleanup and end-to-end memory validation are
  in progress.
- **Observed in:** `:compose:mpp:demo-winui:runWinUIMppSampleInteractive` on the
  JVM and MinGW targets while continuously moving the mouse over Compose
  content.
- **Symptom:** on the JVM target, 3,907 effective pointer inputs increased
  process private bytes from 845.9 MB after full GC to 868.6 MB after a second
  full GC and 15 seconds idle. The managed heap returned to approximately
  22 MB, leaving 22.7 MB of native/private growth. MinGW grows more slowly but
  continues to rise with pointer input.
- **Root cause:** each routed pointer callback creates a
  `PointerRoutedEventArgs`, current `PointerPoint`, `PointerPointProperties`,
  intermediate-point collection, and historical `PointerPoint` wrappers.
  Generated wrappers own both `_inner` and a lazy `_defaultInterface`, but
  `acquireInterfaceReference()` previously detached the queried interface from
  its parent. Closing `nativeObject` therefore released `_inner` without
  releasing that independently owned query-interface reference. The drag
  adapter also replaced and cleared its retained current point without closing
  it.
- **Runtime resolution:** `ComObjectReference` now owns a lock-protected child
  registry. `acquireInterfaceReference(parent, iid)` registers the returned
  interface before it escapes; parent close closes all children and then the
  parent pointer, attempts every close, preserves the first failure, and
  suppresses later failures. Registration that loses a race with parent close
  closes the incoming reference and reports the parent as disposed.
- **Compose action:** deterministically close synchronous pointer callback
  projections, point properties, intermediate collections, and fetched
  historical points. Transfer only the current press/move point into a
  one-value drag owner that closes on replacement, cancel, drag completion, or
  disposal. Do not introduce a global delegate callback lease: projected event
  arguments outside this Compose boundary must remain retainable until their
  owner is explicitly closed.
- **Validation so far:** focused ownership tests and the complete
  `:winrt-runtime:jvmTest :winrt-runtime:mingwX64Test` suites pass with
  `--no-configuration-cache`. Final compose-ui sample and JVM/MinGW memory
  plateau measurements remain required before closing this issue.

## KWINRT-063: Desktop InputPane interop helper was not generated

- **Status:** Closed in kotlin-winrt snapshot
  `0.1.0-20260719.013720-104` on 2026-07-19.
- **Observed in:** compose-winui software-keyboard integration for a WinUI 3
  desktop `Microsoft.UI.Xaml.Window`.
- **Symptom:** explicit projection of
  `Windows.UI.ViewManagement.InputPane` correctly generates `TryShow`,
  `TryHide`, `Showing`, `Hiding`, `OccludedRect`, and
  `GetForCurrentView`. Microsoft documents `GetForCurrentView` for Store/UWP
  views; desktop apps must query the InputPane activation factory for
  `IInputPaneInterop` and call `GetForWindow(HWND, REFIID)`.
- **Resolution:** kotlin-winrt now mirrors C#/WinRT's
  `Windows.UI.ViewManagement.InputPaneInterop` source addition. Its generated
  `getForWindow(RawAddress)` helper owns the activation-factory query,
  `IInputPaneInterop::GetForWindow` ABI call, and projected `InputPane` return
  wrapping. Compose now retrieves the HWND with `WindowNative` and calls that
  helper; the JVM-only activation-factory/vtable workaround and incorrect
  `GetForCurrentView` desktop fallback were removed.
- **Validation:** kotlin-winrt metadata, generator, suppression, and dependency
  identity tests pass. With republished `skiko-winui`
  `0.0.0-20260718.172102-37` / JVM build `-20`, compose-ui regenerates
  `windows/ui/viewmanagement/InputPaneInterop.kt`, compiles the WinUI JVM
  target, and passes the focused InputPane, text-input service, and source-set
  architecture tests.

## KWINRT-062: Refreshed sample projection has conflicting interface IIDs

- **Status:** Closed in kotlin-winrt snapshot
  `0.1.0-20260715.042347-81` on 2026-07-15.
- **Historical observation:** snapshot `0.1.0-20260714.234543-80` rejected two
  IIDs for `Microsoft.Windows.Management.Deployment.IPackageVolume` while
  generating the repository sample projections.
- **Resolution:** the refreshed generator resolves the projection metadata
  conflict without compose-side projection filters or IID overrides.
- **Validation:**
  `:compose:ui:ui:winui-samples:runWinUISkikoSample
  -PcomposeWinUi.enableJvmTarget=true --refresh-dependencies --no-daemon
  --no-configuration-cache --no-configure-on-demand` passes end to end,
  including compose-ui and foundation projection generation and compilation,
  sample authoring validation, application-host build, staging, and launch.

## KWINRT-061: Native KMP ownership failure was a Compose fragment-graph misdiagnosis

- **Status:** Closed as a Compose-side issue on 2026-07-23. No upstream
  kotlin-winrt ownership fix is required for this failure.
- **Root cause:** the original compilation had disconnected
  `winuiMingwMain` and `nativeMain` fragment roots. An ordinary same-file
  interface reproduced `overrides nothing`, proving generated projection
  ownership was not the distinguishing factor. Forcing one root reduced the
  override diagnostics from 794 to 0; reconnecting `nativeMain` kept them at
  0 and exposed 51 duplicate actual declarations instead.
- **Resolution:** keep the default Native hierarchy connected, add
  `winuiMingwMain -> winuiMain`, and in WinUI mode prevent `nonJvmMain` and
  `nativeMain` from reaching Skiko-only actuals through the conditional
  `skikoNonJvmMain` and `skikoNativeMain` source sets. Shared projected
  InputPane acquisition lives in `winuiMain`, and the Compose Native compiler
  plugin appends to the kotlin-winrt compiler-plugin classpath.
- **Validation:** `:compose:ui:ui:compileKotlinWinuiMingw` passes on JDK 25
  with the refreshed kotlin-winrt and Skiko MinGW artifacts, with no
  `overrides nothing`, duplicate-actual workaround, projection stub, or
  dependency exclusion. The generated WinRT projections remain consumed
  normally.

## KWINRT-060: Library-mode runtime injection wires projection tasks too broadly

- **Status:** Closed in kotlin-winrt snapshot
  `0.1.0-20260714.234543-80` on 2026-07-15.
- **Historical observation:** `:compose:foundation:foundation:compileKotlinWinuiJvm`
  previously formed an identity/projection task cycle when foundation applied
  the normal plugin in library mode.
- **Runtime auto-injection finding:** `compose-ui`, `compose/ui/ui/winui-samples`, and
  `compose/mpp/demo-winui` compile and run after removing their explicit
  `winrt-runtime` / `winrt-runtime-jvm` dependencies; dependency insight still
  shows `winrt-runtime` and `winrt-runtime-jvm` on
  `:compose:ui:ui:winuiJvmCompileClasspath`, supplied by the normal
  `io.github.compose-fluent.winrt` plugin. This confirms runtime injection
  belongs to the normal plugin path, not to a separate runtime-only plugin.
- **Library-mode symptom:** `compose/foundation/foundation` uses WinRT
  projected types from `winuiMain`, but it is a layered Compose library module
  with no `application {}` block and no local WinRT projection declarations.
  Removing its explicit `winrt-runtime` dependency without applying the plugin makes
  `compileKotlinWinuiJvm` fail with unresolved
  `io.github.composefluent.winrt.runtime` APIs such as
  `EventRegistrationToken`, `EventHandlerCallback`, and
  `WinRTComposableObject`.
- **Expected behavior:** a KMP module with a WinUI source set that consumes
  WinRT projected types should be able to apply the normal
  `io.github.compose-fluent.winrt` plugin in library mode and receive matching
  runtime dependencies without hand-declaring `winrt-runtime`. Library mode
  must not wire projection, identity, compiler-support merge, or
  authored-candidate tasks into the compile graph for modules that do not
  declare WinRT projection inputs. Application-host and packaging tasks should
  remain gated by `application {}`.
- **Current compose-winui action:** `compose/foundation/foundation` applies the
  normal `io.github.compose-fluent.winrt` plugin. Do not introduce or rely on
  `io.github.compose-fluent.winrt.runtime`, and do not hand-declare
  `winrt-runtime` in this module; runtime injection is expected to be owned by
  the normal winrt plugin.
- **Resolution:** library-mode consumers now receive runtime injection through
  the normal plugin while local projection/compiler-support/authored validation
  tasks are skipped when the combined local projection output is empty.
- **Validation:** with `-PcomposeWinUi.enableJvmTarget=true`,
  `:compose:foundation:foundation:compileKotlinWinuiJvm` and
  `:compose:ui:ui:compileKotlinWinuiJvm` both pass with
  `--refresh-dependencies --no-daemon --no-configuration-cache
  --no-configure-on-demand`. Snapshot `0.1.0-20260715.042347-81` additionally
  passes the repository-local `runWinUISkikoSample` end to end after the
  separate `KWINRT-062` fix.

## KWINRT-059: Application run task skips despite environment-selected sample mode

- **Status:** Closed in the typed application-host run task consumed by
  compose-winui on 2026-07-09.
- **Observed in:** `:compose:mpp:demo-winui:runWinUIMppSample` after
  refreshing kotlin-winrt and skiko snapshots.
- **Symptom:** multiple repository-local smoke tasks invoke the same generated
  `runWinRTApplicationHost` task with different `KOTLIN_WINRT_JVM_OPTIONS`
  values for sample mode and validation report path. The first smoke run writes
  its report, but later smoke tasks can see nested
  `:...:runWinRTApplicationHost UP-TO-DATE`, so the native host is not launched
  and the expected report is missing.
- **Expected behavior:** a generated run task should either always execute, or
  declare JVM options, environment, args, working directory, and other launch
  inputs so Gradle does not reuse an incompatible previous execution.
- **Resolution:** repository-local WinUI smoke tasks now register distinct
  typed `RunWinRTApplicationHostTask` instances through
  `WinRTApplicationOptions.runTask(...)` and
  `Project.registerWinRTApplicationHostRunTask(...)`. Each task declares its
  own `jvmArgs`, output log, validation report, working directory, and other
  launch inputs, so Gradle state belongs to the specific smoke mode instead of
  a shared generated `runWinRTApplicationHost` invocation.
- **Validation:** `:compose:ui:ui:winui-samples:runWinUISkikoSample
  --rerun-tasks` launches the Skiko smoke host and writes the expected
  `mode=skiko` diagnostics. `:compose:mpp:demo-winui:runWinUIMppSample` runs
  the focused MPP smoke tasks and the aggregate run task, with independent
  event reports for launch/window, render output, input/focus, resource
  loading, shutdown/disposal, auto-traverse, and the aggregate title-bar path.

## KWINRT-058: Project dependency WinRT identity/support artifacts require app-side validation

- **Status:** Closed in the refreshed kotlin-winrt/skiko snapshot validation
  path consumed on 2026-07-08.
- **Observed in:** `:compose:mpp:demo-winui:runWinUIMppSample` while consuming
  `:compose:ui:ui` and other local WinUI JVM artifacts through normal
  `project(...)` dependencies.
- **Symptom:** the sample carries repository-local validation for
  `generated/kotlin-winrt/identity/kotlin-winrt.json`,
  `kotlin-winrt/type-index.tsv`,
  `kotlin-winrt-authoring/*.host.json`,
  `kotlin-winrt-authoring/*.winmd`, and compiler-support classes in dependency
  jars. It also validates that local WinUI artifacts are project dependencies
  rather than anonymous `files(...)` jars, because bypassing identity/support
  metadata previously broke projection ownership and authoring support.
- **Expected behavior:** kotlin-winrt should make project-dependency identity,
  type indexes, authoring metadata, WinMDs, and compiler-support artifacts a
  guaranteed part of the plugin contract. A normal app should be able to depend
  on a WinRT-authoring library and build/run without app-local checks that
  inspect dependency jars for kotlin-winrt internals.
- **Resolution:** project-dependency identity, type indexes, authoring metadata,
  WinMDs, and compiler-support artifacts are present through the current
  project dependency graph. Keep `validateWinUiKotlinWinRtKmpGraphBaseline` as
  repository regression coverage rather than an active workaround.
- **Validation:** with refreshed kotlin-winrt and skiko snapshots,
  `:compose:ui:ui:compileKotlinWinuiJvm --refresh-dependencies`,
  `:compose:ui:ui:winuiJvmTest`,
  `:compose:ui:ui:winui-samples:runWinUISkikoSample`, and
  `:compose:mpp:demo-winui:runWinUIMppSample` pass.

## KWINRT-057: Runtime assets and PRI staging still need app-local lifecycle checks

- **Status:** Closed in the refreshed kotlin-winrt/skiko snapshot validation
  path consumed on 2026-07-08.
- **Observed in:** `:compose:mpp:demo-winui:runWinUIMppSample` while staging
  Windows App SDK runtime assets, application PRI inputs, WinUI component PRI
  files, manifest data, and sample resources.
- **Symptom:** the sample has to depend on `stageWinRTRuntimeAssets` and then
  verify implementation details such as `resources.pri`, `Microsoft.UI.pri`,
  `Microsoft.UI.Xaml.Controls.pri`, staged `Appx/...` inputs, default language
  MUI assets, lifted WinRT registration XML, and duplicate default PRI resource
  scanning. These checks are valuable as regression coverage, but they are also
  guarding behavior an application plugin should own.
- **Expected behavior:** kotlin-winrt should provide a stable app packaging
  lifecycle for unpackaged WinUI apps: declared Windows App SDK packages and
  app PRI inputs should stage into the application host layout automatically
  before run tasks execute. Users should not need to manually wire staging task
  dependencies or know the internal runtime-assets directory layout just to run
  a sample.
- **Resolution:** current runtime-asset and PRI staging produces the expected
  unpackaged WinUI application layout. Keep the packaging validation task as
  focused regression coverage, not an active workaround.
- **Validation:** with refreshed kotlin-winrt and skiko snapshots,
  `:compose:mpp:demo-winui:runWinUIMppSample` passes and the packaging report
  validates the staged Windows App SDK runtime assets and PRI inputs.

## KWINRT-056: WinRT run host should be a first-class Gradle task

- **Status:** Closed in the typed application-host run task consumed by
  compose-winui on 2026-07-09.
- **Observed in:** `compose/ui/ui/winui-samples/build.gradle` and
  `compose/mpp/demo-winui/build.gradle.kts`.
- **Symptom:** repository-local sample tasks are `Exec` tasks that launch a
  nested Gradle invocation of `:...:runWinRTApplicationHost`, then parse a log
  file to decide whether the sample passed. They also have to pass JVM options
  through `KOTLIN_WINRT_JVM_OPTIONS` and manually set smoke-mode environment
  variables. On memory-constrained Windows hosts, a nested smoke run without
  explicit application JVM heap options can let the generated host JVM choose a
  large ergonomic G1 heap, causing native memory reservation failure before the
  sample starts.
- **Expected behavior:** kotlin-winrt should expose a configurable run task for
  the generated application host. A sample should be able to configure main
  class, target/source set, args, JVM args, environment, working directory, and
  output log directly on a typed task, without invoking Gradle from Gradle.
- **Resolution:** `compose/ui/ui/winui-samples` uses
  `winRT.application.runTask(...)`, and `compose/mpp/demo-winui` uses
  `registerWinRTApplicationHostRunTask(...)`. The sample tasks configure
  application JVM options through typed `jvmArgs` and logs through
  `outputLog`; they no longer invoke Gradle from Gradle or rely on
  `KOTLIN_WINRT_JVM_OPTIONS` inheritance. The remaining MPP navigation
  compile-only validation is also a normal task dependency, not a nested
  Gradle `Exec`.
- **Validation:** `:compose:ui:ui:winui-samples:runWinUISkikoSample
  --rerun-tasks` runs the host and logs `application starting mode=skiko`,
  `skiko runtime classpath isolated`, `skiko unattached scheduler deferred`,
  and `skiko render diagnostics`. `:compose:mpp:demo-winui:runWinUIMppSample`
  passes with title-bar inset validation, auto-traverse, render diagnostics,
  packaging validation, and shutdown events.

## KWINRT-055: Application-host task graph must wire generation, support merge, staging, and host build

- **Status:** Closed in the refreshed kotlin-winrt/skiko snapshot validation
  path consumed on 2026-07-08.
- **Observed in:** `:compose:mpp:demo-winui:runWinUIMppSample` and
  `:compose:ui:ui:winui-samples:runWinUIViewSample`.
- **Symptom:** app/sample build files manually add dependencies among
  `generateWinRTProjections`, `mergeWinRTCompilerSupport`,
  `stageWinRTRuntimeAssets`, `buildWinRTApplicationHost`, target compilation,
  resources, and sample validation tasks. The wiring works, but it means a user
  has to know kotlin-winrt's internal task ordering to run a WinUI application.
- **Expected behavior:** requesting a generated WinRT application host or run
  host should automatically depend on all required projection generation,
  compiler-support merge, target compilation, resource processing, runtime
  asset staging, and authoring-host generation tasks.
- **Resolution:** generated application-host builds now run through the required
  projection generation, compiler-support merge, target compilation, resource
  processing, runtime staging, and authoring-host generation path in the
  current validation baseline. Keep the sample-side packaging and graph checks
  as focused regression coverage, not active run-task wiring workarounds.
- **Validation:** with refreshed kotlin-winrt and skiko snapshots,
  `:compose:ui:ui:winui-samples:runWinUISkikoSample` and
  `:compose:mpp:demo-winui:runWinUIMppSample` pass.

## KWINRT-054: Application-host runtime classpath should be inferred from the target

- **Status:** Closed in the refreshed kotlin-winrt/skiko snapshot validation
  path consumed on 2026-07-08.
- **Observed in:** `:compose:mpp:demo-winui:buildWinRTApplicationHost`.
- **Symptom:** the sample configures `BuildWinRTApplicationHostTask` manually:
  it adds `winuiJvmRuntimeClasspath`, adds the current `winuiJvmJar`, and
  depends on that jar task. A normal app should not have to know how to assemble
  the runtime classpath for the generated native host.
- **Expected behavior:** kotlin-winrt should let the build author select a JVM
  target or source set, then infer the target runtime classpath and target jar
  for application-host generation. The task should work for both application
  projects and sample projects consuming project dependencies that publish
  WinRT authoring metadata.
- **Resolution:** current application-host generation consumes the WinUI JVM
  target runtime classpath and target jar correctly in the validated sample
  path. Keep the local classpath assertions as regression coverage, not active
  runtime-classpath assembly workarounds.
- **Validation:** with refreshed kotlin-winrt and skiko snapshots,
  `:compose:mpp:demo-winui:runWinUIMppSample` passes while consuming
  `:compose:ui:ui` and other local WinUI JVM artifacts through project
  dependencies.

## KWINRT-053: Authoring scanner classpath should not be configured by applications

- **Status:** Closed in the refreshed kotlin-winrt/skiko snapshot validation
  path consumed on 2026-07-08.
- **Observed in:** `compose/ui/ui/build.gradle` and
  `compose/ui/ui/winui-samples/build.gradle`.
- **Symptom:** build files declare a `winRtAuthoringScannerClasspath`
  configuration, add `kotlin-compiler-embeddable`, and then pass that
  configuration to `generateWinRTProjections.authoringScannerClasspath`.
  Without this, authoring scans are not self-contained for compose-winui's
  authored WinUI types.
- **Expected behavior:** kotlin-winrt should configure the authoring scanner
  classpath internally. If an override is still needed, it should be an
  advanced escape hatch, not required boilerplate in normal app/library builds.
- **Resolution:** authoring scans complete in the current validation baseline
  without scanner classpath failures. Keep the generated-output checks as
  regression coverage, not active scanner classpath workarounds.
- **Validation:** with refreshed kotlin-winrt and skiko snapshots,
  `:compose:ui:ui:compileKotlinWinuiJvm --refresh-dependencies`,
  `:compose:ui:ui:winuiJvmTest`,
  `:compose:ui:ui:winui-samples:runWinUISkikoSample`, and
  `:compose:mpp:demo-winui:runWinUIMppSample` pass.

## KWINRT-052: Application manifest uses wildcard processorArchitecture

- **Status:** Closed in the kotlin-winrt application manifest generation and
  WinUI target runtime-asset staging consumed by the 2026-07-06 build 67 Maven
  snapshot validation path.
- **Observed in:** `:compose:mpp:demo-winui:runWinRTApplicationHost` after
  refreshing to the 2026-07-05 `skiko-winui-jvm` snapshot and current
  kotlin-winrt runtime/plugin snapshots.
- **Symptom:** the generated unpackaged application manifest contains
  `<assemblyIdentity ... processorArchitecture='*'/>`. `CreateActCtxW` fails
  while activating that manifest, and direct `mt.exe -validate_manifest`
  reports `Attribute "processorArchitecture" is wildcarded in the definition
  identity.` After patching that value, SxS activation still fails because the
  generated manifest contains a `Microsoft.Web.WebView2.Core.dll` file entry
  while the sample application layout does not stage that DLL, and because both
  `skiko-winui.dll` and `skiko.dll` register
  `org.jetbrains.skiko.winui.WinUISkiaHostPanel`.
- **Expected behavior:** kotlin-winrt should emit the processor architecture
  required by the generated application host, such as `amd64`, `arm64`, or
  `x86`, rather than a wildcard value that the activation context rejects.
- **Resolution:** compose-winui removed the repository-local sample-host
  manifest patch tasks. The generated application manifests are now consumed
  directly.
- **Validation:** with the published `winrt-gradle-plugin`
  `0.1.0-20260706.013242-67`, `stageWinRTRuntimeAssets` handles WinUI targets
  wildcard imports and generated application manifests no longer need local
  processor-architecture, missing-file, or duplicate-activatable-class edits
  before `runWinRTApplicationHost`.

## KWINRT-049: WinRT async cancellation upcall can crash clipboard text retrieval

- **Status:** Closed in the kotlin-winrt async await runtime consumed by the
  2026-07-05 compose-winui validation path.
- **Observed in:** compose-winui text input and context menu flows that end up
  reading clipboard text through `DataPackageView.getTextAsync()`.
- **Symptom:** the JVM crashes in
  `io.github.composefluent.winrt.runtime.WinRtAsyncAwaitState.installCancellation$lambda$0`
  from a native COM vtable upcall while the clipboard read coroutine is being
  cancelled.
- **Expected behavior:** cancelling a clipboard read should be a normal
  coroutine cancellation, not a native access violation.
- **Resolution:** compose-winui removed the `NonCancellable` wrapper around
  `getTextAsync().await()` and now uses the generated await path directly.
- **Validation:** with JDK 25, `:compose:ui:ui:compileKotlinWinuiJvm`,
  `:compose:ui:ui:winuiJvmTest`, and the MPP sample validation path pass with
  the direct await call.

## KWINRT-051: VirtualKey projection throws for invalid RDP key values

- **Status:** Closed in the kotlin-winrt value-class enum projection and the
  republished `skiko-winui-jvm` snapshot available on 2026-07-05.
- **Observed in:** RDP Chinese input routed through WinUI XAML `KeyDown`
  callbacks while compose-winui has a focused text input.
- **Symptom:** `KeyRoutedEventArgs.key` surfaced an invalid ABI value such as
  `0xff` (`VK__none_` / reserved), and the old generated
  `windows.system.VirtualKey.Metadata.fromAbi` enum path threw
  `IllegalStateException: Unknown Windows.System.VirtualKey ABI value` through
  the WinRT callback.
- **Resolution:** `VirtualKey` is now projected as a value class, so
  `Metadata.fromAbi` wraps the raw integer value instead of rejecting unknown
  values. The compose-side `isInvalidVirtualKeyProjectionFailure()` workaround
  and its focused tests were removed.
- **Validation:** direct `javap` inspection of the refreshed
  `skiko-winui-jvm-0.0.0-SNAPSHOT.jar` shows `windows.system.VirtualKey` is no
  longer a JVM enum and `VirtualKey$Metadata.fromAbi` calls
  `VirtualKey.constructor-impl`. A forced
  `:compose:ui:ui:compileKotlinWinuiJvm --rerun-tasks --no-configuration-cache`
  run passes with the refreshed dependency, `:compose:ui:ui:winuiJvmTest`
  passes, and manual RDP Chinese input validation on the interactive MPP sample
  is normal.

## KWINRT-050: CoreText struct ABI workaround was misattributed to kotlin-winrt

- **Status:** Closed as not a current kotlin-winrt upstream issue.
- **Finding:** compose-ui declares the CoreText `type(...)` surface, but the
  current generated compose-ui sources do not own the `windows.ui.text.core`
  runtime classes. Those CoreText runtime classes are currently resolved from
  the `skiko-winui-jvm` dependency, while compose-ui only generates support
  helpers that reference them.
- **Historical symptom:** after assigning `CoreTextRange(1991, 1991)` to a
  selection request, reading it back returned pointer-like values such as
  `CoreTextRange(start=465861664, end=529)`. The MPP `BasicTextField2` sample
  then failfasted inside
  `Windows.UI.Core.TextInput.dll!Windows::UI::Text::Core::CLayoutRequest::GetTranslatedLayoutBounds`
  while TSF queried layout.
- **Resolution:** compose-winui removed `WinUICoreTextStructInterop.winuiJvm.kt`
  and now uses the generated CoreText projection path directly for
  `CoreTextEditContext.notifySelectionChanged`,
  `CoreTextSelectionRequest.selection`, and `CoreTextLayoutBounds`
  `textBounds` / `controlBounds`. The stale source-set isolation test was also
  updated to the current kotlin-winrt `src/commonMain/kotlin` generated source
  layout.
- **Validation:** with current `skiko-winui-jvm`, JDK 25, and
  `--no-configuration-cache`,
  `:compose:ui:ui:compileKotlinWinuiJvm` and
  `:compose:ui:ui:winuiJvmTest` pass without the ABI helper. The repository
  WinUI smoke path `:compose:ui:ui:winui-samples:runWinUISkikoSample` with
  `--rerun-tasks` and the MPP demo path
  `:compose:mpp:demo-winui:runWinUIMppSample` also pass.

## KWINRT-048: Generated XAML notifier override used the event handler projection type

- **Status:** Closed/stale; not reproducible with the current projection
  output.
- **Historical symptom:** generated WinUI sources used
  `override fun addPropertyChanged(handler: PropertyChangedEventHandler)` and
  `removePropertyChanged(handler: PropertyChangedEventHandler)` for
  `microsoft.ui.xaml.controls.ItemsControl`, while the projected
  `INotifyPropertyChanged` contract expected
  `(Any?, microsoft.ui.xaml.data.PropertyChangedEventArgs?) -> Unit`.
- **Resolution:** compose-winui removed `patchWinRtGeneratedKwinrt048` and the
  obsolete `WinRTCompatibilityAliases.winui.kt` facade. Current generated
  sources use `windows.foundation.EventRegistrationToken` and
  `INotifyPropertyChangedProjection` directly.
- **Validation:** after deleting
  `out/compose-multiplatform-core/compose/ui/ui/build/generated/kotlin-winrt`,
  fresh `:compose:ui:ui:compileKotlinWinuiJvm` and
  `:compose:foundation:foundation:compileKotlinWinuiJvm` pass without the patch.

## KWINRT-046: Dependency authored activatable classes are omitted from app manifest

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot `0.1.0-SNAPSHOT`
  as of the 2026-06-21 retest.
- **Observed in:** `:compose:ui:ui:winui-samples:runWinRtApplicationHost` and
  `:compose:mpp:demo-winui:runWinRtApplicationHost` after switching to the
  latest generated application layout path.
- **Symptom:** the final `*.exe.manifest` stages the dependency authored host
  DLL as `ui.dll`, but the `<asmv3:file name='ui.dll' ...>` node contains no
  `winrtv1:activatableClass` entry for
  `androidx.compose.ui.window.WinUIXamlApplication`. `Application.start { ... }`
  enters the application factory, then WinUI failfasts from
  `Microsoft.UI.Xaml.dll` with stowed `E_INVALIDARG` before
  `WinUIXamlApplication.onLaunched` is invoked.
- **Expected behavior:** final application manifests should aggregate
  activatable classes from dependency-owned authored host manifests when the
  dependency host DLL is staged into the application layout.
- **Resolution:** compose-winui removed the `patchWinRtApplicationManifest`
  tasks from `compose/ui/ui/winui-samples` and `compose/mpp/demo-winui`.
- **Validation:** a clean
  `:compose:ui:ui:winui-samples:buildWinRtApplicationHost --rerun-tasks`
  emits `androidx.compose.ui.window.WinUIXamlApplication` under the generated
  `ui.dll` manifest node without the compose-side patch.

## KWINRT-047: Dependency-authored WinUI Application subclass failfasts during construction

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot `0.1.0-SNAPSHOT`
  as of the 2026-06-21 retest.
- **Observed in:** `:compose:ui:ui:winui-samples:runWinRtApplicationHost`
  after `patchWinRtApplicationManifest` inserts the missing
  `androidx.compose.ui.window.WinUIXamlApplication` activatable class under
  `ui.dll`.
- **Symptom:** `Application.start { WinUIXamlApplication() }` enters the
  callback and native-failfasts before `WinUIXamlApplication.onLaunched` is
  invoked. The generated bytecode registers
  `WinRTAuthoringTypeDetailsRegistrar_ui` and then calls the generated
  `microsoft.ui.xaml.Application` constructor; the process exits with
  `NTSTATUS 0xC000027B`, and WER writes dumps such as
  `%LOCALAPPDATA%\CrashDumps\winui-samples.exe.*.dmp`.
- **Native evidence:** CDB analysis of
  `%LOCALAPPDATA%\CrashDumps\winui-samples.exe.34552.dmp` shows
  `STOWED_EXCEPTION`, `Microsoft.UI.Xaml.dll!FailFastWithStowedExceptions`,
  stowed `HRESULT 0x80070057 (E_INVALIDARG)`, with the XAML stack in
  `DirectUI::FrameworkApplication::InvokeOnLaunchActivated` and
  `DirectUI::FrameworkApplicationFactory::Start`.
- **Expected behavior:** a Kotlin-authored `Application` subclass supplied by a
  dependency jar should construct through the generated application host in the
  same way as kotlin-winrt's README sample `class DemoApp : Application()` in
  a final app module.
- **compose-winui workaround:** none. Keep `WinUIXamlApplication` in
  `compose-ui`; do not move the application subclass into samples or bypass the
  authored WinRT construction path, because the public `Application { ... }`
  API must work from the library target.
- **Resolution:** no compose-side workaround was needed.
- **Validation:** `:compose:ui:ui:winui-samples:runWinRtApplicationHost`
  enters `WinUIXamlApplication.onLaunched` and logs the first sample smoke
  checkpoints. The later dependency projection ownership failure formerly
  tracked as `KWINRT-041` is no longer reproducible with the current dependency
  graph.

## KWINRT-001: Generated event source registry ABI mismatch

- **Status:** Fixed upstream in `external/kotlin-winrt` `56c9267c`.
- **Observed in:** generated WinRT event accessors such as `Window.closed`,
  `AppWindow.changed`, `AppWindow.closing`, pointer/key events, and text toolbar
  menu events.
- **Resolution:** compose-winui removed manual event registration workarounds
  and uses generated `WinRtEvent.add/remove`.
- **Validation:** `runWinUIViewSample` completes while using generated event
  accessors; `:compose:ui:ui` generated `event-sources.tsv` contains the WinUI
  event descriptors.

## KWINRT-002: Interface projection registry is not reliably initialized

- **Status:** Fixed upstream after `efc6cc8714ec59812ffb817cc29e424d1ff60d6f`.
- **Observed in:** `DispatcherQueue.hasThreadAccess`.
- **Resolution:** compose-winui removed its manual interface projection registry
  bootstrap.
- **Validation:** `compileKotlinWinuiJvm`, `winuiJvmTest`, and
  `runWinUIViewSample` pass without the compose-side bootstrap.

## KWINRT-003: Nullable XAML content properties throw on null ABI returns

- **Status:** Fixed upstream and retested in compose-winui with
  `external/kotlin-winrt` `97f15295`.
- **Observed in:** `ContentControl.content` after setting it to `null`.
- **Resolution:** Generated `ContentControl.content` is nullable in the compose
  projection, and no compose-side `WINRT_E_NULL_ABI_RETURN` workaround remains.
- **Validation:** `compileKotlinWinuiJvm` and `runWinUIViewSample` pass; the
  sample reads generated `ContentControl.content` for button content checks.

## KWINRT-004: Nullable XAML runtime-class properties are generated as non-null setters

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-02.
- **Observed in:** `Window.systemBackdrop`.
- **Resolution:** compose-winui removed the manual
  `IWindow2.SYSTEMBACKDROP_SETTER_SLOT` ABI path and now uses the generated
  nullable `Window.systemBackdrop` setter for both setting and clearing.
- **Validation:** `compileKotlinWinuiJvm` passes with the Maven snapshot.

## KWINRT-005: Windows.System.Launcher projection pulls invalid Package wrappers

- **Status:** Fixed upstream and retested in compose-winui with
  `external/kotlin-winrt` `97f15295`.
- **Observed in:** `Windows.System.Launcher` and dependent
  `Windows.ApplicationModel.Package` projections.
- **Resolution:** compose-winui uses the natural generated
  `Launcher.launchUriAsync(uri)` path.
- **Validation:** `compileKotlinWinuiJvm` and `runWinUIViewSample` pass with the
  generated one-argument overload.

## KWINRT-006: Nullable UIElement.Clip setter is generated as non-null

- **Status:** Fixed upstream after `efc6cc8714ec59812ffb817cc29e424d1ff60d6f`.
- **Observed in:** `Microsoft.UI.Xaml.UIElement.clip`.
- **Resolution:** compose-winui removed the workaround and uses the generated
  nullable `UIElement.clip` property for both setting and clearing.
- **Validation:** WinUI interop clipping tests and `runWinUIViewSample` pass.

## KWINRT-007: Canvas attached property setters can crash the WinUI runtime

- **Status:** Not reproduced with current kotlin-winrt; no upstream action
  without fresh native crash evidence.
- **Observed in:** `Canvas.setLeft(UIElement, Double)`,
  `Canvas.setTop(UIElement, Double)`, and `DependencyObject.setValue(...)` for
  `Canvas.LeftProperty` / `Canvas.TopProperty`.
- **Resolution:** compose-winui removed the margin fallback and now uses
  generated `Canvas.setLeft` / `Canvas.setTop` for WinUIView placement.
- **Validation:** `runWinUIViewSample` passes and verifies initial and updated
  `Canvas.getLeft` / `Canvas.getTop` wrapper positions.
- **Next evidence needed:** If this reproduces, attach `hs_err`, WER, or dump
  analysis before changing kotlin-winrt.

## KWINRT-008: Generated JVM interface projection members are incomplete for WinUI

- **Status:** Fixed for the compose-winui integration surface in kotlin-winrt
  Maven snapshot `0.1.0-SNAPSHOT` as of 2026-06-02.
- **Observed in:** the real compose-winui `Application { Window { ... } }` path
  after authored `Application` CCW registration and resource staging.
- **Earlier finding:** This was deeper than the earlier
  `XamlControlsResources`/PRI symptom. Previous kotlin-winrt builds could stage
  enough WinUI resources for the sample and no longer needed a repository-local
  `App.xaml`, but the JVM artifact runtime rejected many generated public
  interface projection members needed by normal WinUI integration.
- **Managed evidence:** compose-winui hit
  `WinRtUnsupportedOperationException: Generated interface projection member
  ... is not supported by the JVM artifact runtime` for:
  `IApplication.getResources`, `IWindow.getDispatcherQueue`,
  `IWindow.getCompositor`, `IWindow.setContent`, `IWindow2.getAppWindow`,
  `IWindow2.getSystemBackdrop`, `IWindow2.setSystemBackdrop`, and
  `IRectangleGeometry.setRect`. `Panel.children` also needed a public metadata
  slot fallback rather than relying on the generated getter.
- **Projection registration evidence:** several generated interface factories
  were also missing from the lowercase runtime lookup until compose-winui
  registered aliases locally, including `microsoft.ui.xaml.IWindow`,
  `microsoft.ui.xaml.media.IRectangleGeometry`,
  `microsoft.ui.xaml.controls.IPanel`, and
  `windows.system.display.IDisplayRequest`.
- **Resolution:** compose-winui removed the local interface alias registration
  and the ABI slot fallbacks for `Window.Content`, `Window.Compositor`,
  `Window.AppWindow`, `Window.SystemBackdrop`, and `Panel.Children`. These paths
  now use generated projection members.
- **Validation:** `compileKotlinWinuiJvm` passes with the Maven snapshot.

## KWINRT-009: Collection-returned XAML base wrappers cannot be rewrapped publicly

- **Status:** Fixed upstream for compose-winui wrapper paths.
- **Observed in:** XAML collections returning base `IInspectable`/`UIElement`
  wrappers that need public rewrap into generated runtime classes.
- **Resolution:** compose-winui removed repository-local rewrap helpers for the
  covered paths.
- **Validation:** `runWinUIViewSample` covers window content, interop insertion,
  and removal with generated wrappers.

## KWINRT-010: Static runtime class shells do not expose callable static members

- **Status:** Fixed upstream.
- **Observed in:** static WinRT helpers used by clipboard and other platform
  services.
- **Resolution:** compose-winui removed manual static activation/vtable
  workarounds where generated static members are now callable.
- **Validation:** `PlatformClipboard.winui.kt` compiles against generated
  clipboard APIs and `winuiJvmTest` passes.

## KWINRT-011: Repeated projection generation creates incompatible internal wrappers

- **Status:** Fixed upstream for the current compose-winui graph baseline.
- **Observed in:** base library -> winui library -> app graphs that generate
  the same WinRT type identity in multiple modules.
- **Resolution:** kotlin-winrt now merges the generated compiler-support
  artifacts and loads generated registries from caller classloaders.
- **Validation:** `compileKotlinWinuiJvm` passes with the compose-winui
  multi-module projection graph after syncing `external/kotlin-winrt`
  `5d35f2f9`.

## KWINRT-012: Clipboard async needs a dispatcher-safe helper

- **Status:** Closed as a kotlin-winrt issue.
- **Observed in:** synchronous clipboard reads in compose/application policy.
- **Resolution:** kotlin-winrt should provide generated APIs and async runtime
  lifetime handling, not a clipboard-specific synchronous cache.
- **compose-winui target:** Keep clipboard synchronization policy inside
  compose-winui/application code.

## KWINRT-013: JVM FFM upcall can crash while WinRT callbacks race shutdown

- **Status:** Fixed upstream; exact fixing commit not identified from
  compose-winui.
- **Observed in:** JVM shutdown with possible late WinRT callbacks.
- **Resolution:** After syncing current kotlin-winrt, compose-winui no longer
  reproduces the shutdown/upcall crash during sample validation. If a native
  crash returns, preserve and analyze `hs_err_pid*.log`, `replay_pid*.log`, WER,
  or dump files before reopening.
- **Validation:** The only crash log seen during this retest was a Gradle JVM
  native-memory failure during configuration/compilation, with no WinRT/upcall
  frames; it was not a `KWINRT-013` recurrence.

## KWINRT-014: Generated attached dependency property getters can rewrap with module-mangled internals

- **Status:** Fixed upstream and retested in compose-winui with
  `external/kotlin-winrt` `97f15295`.
- **Observed in:** attached dependency property getters such as automation
  properties.
- **Resolution:** compose-winui uses generated
  `AutomationProperties.accessibilityViewProperty`,
  `AutomationProperties.getAccessibilityView`, and
  `DependencyObject.clearValue`.
- **Validation:** `runWinUIViewSample` passes while applying
  `WinUIInteropProperties.isNativeAccessibilityEnabled=false` and reading back
  `AccessibilityView.Raw`.

## KWINRT-015: AutomationProperties.SetAccessibilityView can native-crash detached XAML elements

- **Status:** Not reproduced with current kotlin-winrt; no upstream action
  without fresh native crash evidence.
- **Observed in:** `AutomationProperties.SetAccessibilityView(...)` on detached
  or offscreen XAML elements.
- **Resolution:** compose-winui now wires
  `WinUIInteropProperties.isNativeAccessibilityEnabled` through generated
  `AutomationProperties.setAccessibilityView` and clears the override on
  release.
- **Validation:** `runWinUIViewSample` passes with native accessibility disabled
  for hosted WinUI buttons.
- **Next evidence needed:** If the crash reproduces, inspect native logs/dumps
  first. If the dump shows a WinUI detached/offscreen precondition, compose-winui
  should defer until attached/loaded.

## KWINRT-016: Generated Application.Start intrinsic is not lowered for compose-winui

- **Status:** Fixed upstream in local kotlin-winrt `ec8c5a52` and later
  verified in the compose graph.
- **Observed in:** generated `Application.start`.
- **Resolution:** compose-winui uses generated `Application.start` for the
  application entry path.
- **Validation:** `runWinUIViewSample` enters and exits the
  `Application { Window { ... } }` path successfully.

## KWINRT-017: WinUI Application access native-failfasts inside Application.Start callback

- **Status:** Superseded by the fixed generated `Application.start` path and
  resource bootstrap.
- **Observed in:** early `Application.current` / resource access during
  startup.
- **Resolution:** No active compose-winui workaround remains for this id.
- **Validation:** Covered by `runWinUIViewSample`.

## KWINRT-018: ContentControl.Content string getter did not round-trip assigned strings

- **Status:** Fixed upstream.
- **Observed in:** `ContentControl.content` when assigned a string.
- **Resolution:** compose-winui sample reads generated `ContentControl.content`
  directly for button content checks.
- **Validation:** `runWinUIViewSample` logs both initial and updated compose
  button content values.

## KWINRT-019: Live WinUI controls reject programmatic focus transfer from Compose

- **Status:** Closed as a kotlin-winrt blocker unless new ABI evidence appears.
- **Observed in:** `UIElement.focus(...)` timing on live WinUI controls.
- **Current finding:** Generated projection focus calls can return true once the
  element is loaded/layout-ready.
- **compose-winui target:** Move focus requests to loaded/layout-ready points
  rather than adding a kotlin-winrt helper or ABI workaround.
- **Validation:** `WinUIPlatformFocusOwnerTest` covers current focus behavior;
  `runWinUIViewSample` now covers compose-winui deferring native focus transfer
  until the embedded WinUI control is loaded/layout-ready.

## KWINRT-020: DisplayRequest default interface projection is not registered

- **Status:** Fixed upstream in `external/kotlin-winrt` `5d35f2f9`.
- **Observed in:** `DisplayRequest.requestActive()` /
  `DisplayRequest.requestRelease()` from downstream sample classpaths.
- **Resolution:** kotlin-winrt now loads generated registries from caller
  classloaders and uses direct interface projection registry tokens.
  compose-winui removed the keep-screen-on ABI fallback and now calls generated
  `DisplayRequest.requestActive()` / `requestRelease()` directly.
- **Validation:** with `external/kotlin-winrt` `1bd45755`,
  `compileKotlinWinuiJvm` passes and `runWinUIViewSample` reaches the generated
  `DisplayRequest.requestActive()` / `requestRelease()` path without projection
  registration failures.

## KWINRT-021: UIElement.ProtectedCursor requires a subclass access path

- **Status:** Closed as a kotlin-winrt bug. This is expected WinUI API shape.
- **Observed in:** `Microsoft.UI.Xaml.UIElement.ProtectedCursor`.
- **Resolution:** compose-winui removed the direct `IUIElementProtected` slot
  call. `WinUIRootContentHost` now uses a compose-owned `ContentControl`
  subclass, `WinUIRootContentControl`, which sets the protected
  `UIElement.ProtectedCursor` property through normal Kotlin protected access.
  `WinUIPointerCursorAdapter` maps Compose pointer icons to
  `InputSystemCursor` instances.
- **Validation:** `compileKotlinWinuiJvm`, `winuiJvmTest`, and
  `runWinUIViewSample` pass with the root subclass path.

## KWINRT-022: WinRT flags project as enum values instead of bitmasks

- **Status:** Fixed upstream in `external/kotlin-winrt` `56c9267c`.
- **Observed in:** `Windows.System.VirtualKeyModifiers`, read from
  `PointerRoutedEventArgs.keyModifiers`.
- **Resolution:** Generated flags are now value-class bitmasks; compose-winui
  removed manual modifier decoding.
- **Validation:** `WinUIPointerKeyboardModifiersTest` covers individual flags,
  combined flags, and unknown bits.

## KWINRT-023: WinUI authored Application did not register IApplicationOverrides

- **Status:** Fixed for compose-winui wiring after syncing
  `external/kotlin-winrt` `ad2b9df4`.
- **Observed in:** `:compose:ui:ui:winui-samples:runWinUIViewSample`, where
  WinUI failed fast with `E_NOINTERFACE` for
  `Microsoft.UI.Xaml.IApplicationOverrides`.
- **Resolution:** compose-winui now compiles kotlin-winrt's
  `generated/kotlin-winrt-authoring/src/main/kotlin` output into
  `compileKotlinWinuiJvm`, so the compiler plugin can lower authored
  construction sites to `WinRTAuthoringTypeDetailsRegistrar.register()`.
  The earlier hand-written `WinUIXamlApplication` registration workaround was
  removed.
- **Validation:** `compileKotlinWinuiJvm` succeeds and bytecode for
  `WinUIRootContentHost` contains a registrar call before constructing
  `WinUIRootContentControl`. `runWinUIViewSample` reaches the full current
  smoke path, including `IApplicationOverrides.onLaunched`, before hitting the
  separate teardown crash tracked as `KWINRT-024`.

## KWINRT-024: WinUI authored/runtime lifetime crash after full smoke

- **Status:** Closed in the kotlin-winrt Maven snapshots consumed by the
  2026-07-06 compose-winui validation baseline.
- **Observed in:** `:compose:ui:ui:winui-samples:runWinUIViewSample` with
  `external/kotlin-winrt` `1bd45755`, still reproduced after syncing
  `6b1ce387` (`Remove stale interface support merging`). The sample passes
  application startup, Compose content update, owner/input smoke paths,
  generated event cleanup, saveable/retained state restore, and text input
  session cancellation before crashing.
- **Symptom:** the sample process exits with `NTSTATUS 0xC0000005` after logging
  `compose-winui-sample: text input session cancellation`.
- **Resolution:** current kotlin-winrt `0.1.0-SNAPSHOT` Maven artifacts no
  longer reproduce the teardown crash in compose-winui. With JDK 25 and
  `-PcomposeWinUi.enableJvmTarget=true --no-configuration-cache
  --no-configure-on-demand`, `:compose:ui:ui:compileKotlinWinuiJvm`,
  `:compose:ui:ui:winuiJvmTest`,
  `:compose:ui:ui:winui-samples:runWinUIViewSample`, and
  `:compose:mpp:demo-winui:runWinUIMppSample` pass on 2026-07-06. No fresh
  related `java.exe` / WinUI sample WER dump was present after the run.
- **Native evidence:** latest dumps after `ad2b9df4` are
  `%LOCALAPPDATA%\CrashDumps\java.exe.46428.dmp` and
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).46428.dmp`. WER reports an initial
  `APPCRASH` in `Microsoft.UI.Xaml.dll` 3.1.8.0 with exception `0xc0000005` at
  offset `0x5c5ce`, followed by `BEX64` against
  `Microsoft.UI.Xaml.dll_unloaded` at offset `0x287490`. Local minidump parsing
  maps the first exception address to the staged `Microsoft.UI.Xaml.dll`
  (`0x5c5ce`) and the second to unloaded XAML code, consistent with the earlier
  teardown bucket.
- **Latest native evidence:** after `6b1ce387`, the latest dump is
  `%LOCALAPPDATA%\CrashDumps\java.exe.43136.dmp`. WER reports
  `APPCRASH java.exe`, faulting module `Microsoft.UI.Xaml.dll` 3.1.8.0,
  exception `0xc0000005`, offset `0x2a62a0`, report id
  `fae61182-415c-435c-9310-65d39e767f6b`. Local minidump parsing maps the
  exception address to the staged XAML DLL at `+0x2a62a0`; the failing access is
  a read from `0xffffffffffffffff`. The candidate native stack is now a
  XAML/CoreMessaging/UI-thread path rather than the earlier unloaded-DLL
  teardown stack. This matches the post-`402eb4d8` and post-`32f6af88` dumps
  except for process/report ids.
- **Stack evidence:** exception thread is in XAML DLL teardown, not an FFM upcall
  stub:
  `Microsoft_UI_Xaml!ctl::ComPtr<ABI::Microsoft::UI::Xaml::IFrameworkElement>::InternalRelease`,
  `Microsoft_UI_Xaml!CCustomDependencyProperty::~CCustomDependencyProperty`,
  `Microsoft_UI_Xaml!DirectUI::DynamicMetadataStorage::~DynamicMetadataStorage`,
  `Microsoft_UI_Xaml!DirectUI::DynamicMetadataStorage::Destroy`,
  `Microsoft_UI_Xaml!DeinitializeDll`, then `combase!CoUninitialize` /
  `KERNELBASE!FreeLibraryAndExitThread`.
- **Current assessment:** this points at authored/custom dependency property
  metadata or UI-thread lifetime ordering in the kotlin-winrt authoring/runtime
  path. Do not treat it as a `KWINRT-013` shutdown/upcall recurrence unless a
  future dump shows a callback/upcall frame; the latest local parse still does
  not show an FFM upcall stub as the faulting frame.
- **Maven snapshot validation:** with kotlin-winrt `0.1.0-SNAPSHOT` from Maven
  snapshots on 2026-06-02, `runWinUIViewSample` again reaches
  `compose-winui-sample: text input session cancellation` and exits with
  `NTSTATUS 0xC0000005`. No newer `java.exe*.dmp` was present in
  `%LOCALAPPDATA%\CrashDumps` after this run.
- **2026-06-03 skiko-winui snapshot validation:** after consuming
  `skiko-winui` `0.0.0-20260603.023842-2`,
  `winrt-gradle-plugin` `0.1.0-20260603.021843-3`, and
  `winrt-compiler-plugin` `0.1.0-20260603.021535-22`,
  `runWinUIViewSample` reaches the same final smoke log
  `compose-winui-sample: text input session cancellation` and exits with
  `NTSTATUS 0xC0000005`.
  - **2026-06-03 WinDbg evidence:** Store WinDbg
  `10.0.29547.1002` analyzed
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).39844.dmp`; the log is
  `out/compose-multiplatform-core/windbg-java-39844.log`. The failure bucket is
  `SOFTWARE_NX_FAULT_INVALID_POINTER_EXECUTE_c0000005_Microsoft.UI.Xaml.dll!Unloaded`,
  with `ExceptionAddress` `<Unloaded_Microsoft.UI.Xaml.dll>+0x287490` and
  `AV.Type=Execute`. The stack is in thread teardown through
  `ntdll!RtlpFlsDataCleanup`, `ntdll!LdrShutdownThread`,
  `ntdll!RtlExitUserThread`, `KERNELBASE!FreeLibraryAndExitThread`, and
  `ucrtbase!common_end_thread`. This keeps the latest evidence in the
  unloaded-XAML teardown bucket and still does not show a Java/Kotlin managed
  exception or FFM upcall frame.
- **2026-06-03 initial dump evidence:** Store WinDbg also analyzed the earlier
  same-PID dump `%LOCALAPPDATA%\CrashDumps\java.exe.39844.dmp`; the log is
  `out/compose-multiplatform-core/windbg-java-39844-initial.log`. This first
  dump has the more useful pre-unload bucket
  `INVALID_POINTER_READ_c0000005_Microsoft.UI.Xaml.dll!ctl::ComPtr_ABI::Microsoft::UI::Xaml::IFrameworkElement_::InternalRelease`.
  The exception address is
  `Microsoft_UI_Xaml!ctl::ComPtr<ABI::Microsoft::UI::Xaml::IFrameworkElement>::InternalRelease+0x1e`
  (`Microsoft.UI.Xaml.dll` `+0x5c5ce`), attempting to read from
  `0x00007ff9e06e57f8`. The native stack runs through
  `Microsoft_UI_Xaml!CCustomDependencyProperty::~CCustomDependencyProperty`,
  `Microsoft_UI_Xaml!DirectUI::DynamicMetadataStorage::~DynamicMetadataStorage`,
  `Microsoft_UI_Xaml!DirectUI::DynamicMetadataStorage::Destroy`,
  `Microsoft_UI_Xaml!DeinitializeDll`, then `ntdll!LdrpProcessDetachNode`,
  `KERNELBASE!FreeLibrary`, `combase!CClassCache::CleanUpDllsForProcess`,
  `combase!CoUninitialize`, and `combase!FlsThreadCleanupCallback`. This points
  specifically at XAML dynamic metadata / custom dependency property lifetime
  during COM thread cleanup.
- **2026-06-03 compose-side isolation:** a diagnostic build replaced the
  authored `WinUIRootContentControl : ContentControl` with a plain
  `ContentControl` typealias and disabled root cursor assignment. The WinUI JVM
  compile still passed and the sample still reached
  `compose-winui-sample: text input session cancellation` before exiting with
  `NTSTATUS 0xC0000005`. This rules out the compose-winui root content control
  authored subclass as the sole trigger; the remaining authored XAML type in
  that diagnostic path is `WinUIXamlApplication`.
- **2026-06-03 upstream control validation:** local `external/kotlin-winrt` at
  `6b1ce387` runs
  `:winrt-samples:winui-kmp-app:runWinuiKmpSample` to
  `winui-kmp-app: finished` and exits successfully. That sample covers authored
  controls, a custom dependency property, callbacks, unload, and WinUI teardown,
  so this crash is not reproduced by kotlin-winrt's own control sample.
- **2026-06-03 Maven snapshot retest:** Maven snapshot metadata and Gradle
  dependency insight still resolve compose-winui to `skiko-winui`
  `0.0.0-20260603.023842-2`, `skiko-winui-windows`
  `0.0.0-20260603.023842-2`, `winrt-runtime` /
  `winrt-runtime-jvm` / `winrt-authoring` `0.1.0-20260603.021535-22`,
  `winrt-gradle-plugin` `0.1.0-20260603.021843-3`, and
  `winrt-compiler-plugin` `0.1.0-20260603.021535-22`.
  `:compose:ui:ui:compileKotlinWinuiJvm` passes, and
  `:compose:ui:ui:winui-samples:runWinUIViewSample` reaches the full current
  smoke path before the same `NTSTATUS 0xC0000005` process exit.
- **2026-06-03 compose-side diagnostic:** deferring
  `WinUIApplicationRuntime.dispose()` until after `Application.start` returned
  did not change the result. The sample still reached the final smoke log and
  exited with `NTSTATUS 0xC0000005`, so the crash is not explained solely by
  compose-winui disposing its root before calling `Application.exit()`.
- **2026-06-03 latest Maven snapshot retest:** after clearing Gradle
  `files-2.1`, descriptor, and `resources-2.1` snapshot metadata for
  `io.github.compose-fluent`, Gradle resolved kotlin-winrt runtime/authoring
  metadata to `0.1.0-20260603.042831-23` and `winrt-gradle-plugin` to
  `0.1.0-20260603.043142-4`; `skiko-winui` remained
  `0.0.0-20260603.023842-2`. `:compose:ui:ui:compileKotlinWinuiJvm` passes,
  and `:compose:ui:ui:winui-samples:runWinUIViewSample` again reaches
  `compose-winui-sample: text input session cancellation` before failing with
  `NTSTATUS 0xC0000005`. No newer WER dump was produced in
  `%LOCALAPPDATA%\CrashDumps` after that run; Store WinDbg/CDB logs are kept at
  `out/compose-multiplatform-core/cdb-runWinUIViewSample-latest*.log`.
- **2026-06-03 attached Skiko diagnostics retest:** after adding an
  attached-window Skiko render diagnostics smoke, the sample reaches
  `compose-winui-sample: skiko render diagnostics` and still reaches the final
  `compose-winui-sample: text input session cancellation` log before the same
  `NTSTATUS 0xC0000005` process exit. No newer WER dump was produced in
  `%LOCALAPPDATA%\CrashDumps`.
- **2026-06-03 attach-aware Skiko scheduler retest:** after deferring
  `WinUIComposeView` Skiko frame-scheduler startup until root `Loaded`, the
  sample still reaches `compose-winui-sample: skiko render diagnostics` and
  `compose-winui-sample: text input session cancellation` before failing with
  `NTSTATUS 0xC0000005`. This run produced
  `%LOCALAPPDATA%\CrashDumps\java.exe.55780.dmp`; Store CDB/WinDbg
  `10.0.29547.1002` analysis is saved at
  `out/compose-multiplatform-core/windbg-java-55780.log`. The bucket is
  `STOWED_EXCEPTION_c000027b_CoreMessagingXP.dll!Microsoft::UI::Dispatching::DispatcherQueue::DeferInvokeCallback`;
  the stack goes through `KERNELBASE!RaiseFailFastException`,
  `combase!RoFailFastWithErrorContextInternal2`,
  `CoreMessagingXP!Microsoft::UI::Dispatching::DispatcherQueue::DeferInvokeCallback`,
  CoreMessaging dispatch/deferred-call frames, and
  `Microsoft_UI_Xaml!DirectUI::FrameworkApplication::StartDesktop`. This again
  points at WinUI/CoreMessaging application lifetime or dispatcher teardown
  after the full compose-winui smoke path, not a Skiko render failure.
- **2026-06-03 unattached scheduler smoke retest:** after adding explicit
  sample coverage for the deferred unattached Skiko scheduler path, the sample
  reaches `compose-winui-sample: skiko unattached scheduler deferred`,
  `compose-winui-sample: skiko render diagnostics`, and the final
  `compose-winui-sample: text input session cancellation` log before the same
  `NTSTATUS 0xC0000005` process exit. No newer WER dump was produced after this
  run; the latest dump remains `java.exe.55780.dmp`.
- **2026-06-03 render-size diagnostics retest:** tightening the sample to wait
  for Skiko diagnostics to report exactly the test-injected `160x96` render
  size timed out in the attached-window smoke. This was reverted to the stable
  check that the attached host renders, reports no Skiko failure, and reports a
  non-negative size when the upstream diagnostics expose one. The failed
  strict-size probe is not currently treated as a skiko-winui or kotlin-winrt
  bug because the reported Skiko platform size is not guaranteed to equal the
  Compose `WindowInfo` test size.
- **2026-06-03 Store CDB latest dump retest:** Store CDB
  `10.0.29547.1002` analyzed
  `%LOCALAPPDATA%\CrashDumps\java.exe.51408.dmp`; the log is
  `out/compose-multiplatform-core/windbg-java-51408.log`. The bucket is again
  `STOWED_EXCEPTION_c000027b_CoreMessagingXP.dll!Microsoft::UI::Dispatching::DispatcherQueue::DeferInvokeCallback`.
  The native stack goes through `KERNELBASE!RaiseFailFastException`,
  `combase!RoFailFastWithErrorContextInternal2`,
  `CoreMessagingXP!Microsoft::UI::Dispatching::DispatcherQueue::DeferInvokeCallback`,
  CoreMessaging deferred-call dispatch, and
  `Microsoft_UI_Xaml!DirectUI::FrameworkApplication::StartDesktop`. The loaded
  `Microsoft.UI.Xaml.dll` is Windows App SDK `3.1.8.2604` from the staged
  kotlin-winrt application package. This is the same application
  lifetime/dispatcher teardown failure as the previous Store CDB evidence.
- **2026-06-03 cached snapshot retest:** with the currently cached Maven
  snapshots, Gradle dependency insight resolves `skiko-winui` to
  `0.0.0-20260603.023842-2` and kotlin-winrt runtime/authoring artifacts to
  `0.1.0-20260603.042831-23`. Direct Sonatype snapshot metadata reads confirm
  these are still the latest published `io.github.compose-fluent` snapshots as
  of this retest; Gradle `--refresh-dependencies` remains blocked by external
  Maven/Google repository TLS handshake failures while resolving buildSrc
  dependencies. `:compose:ui:ui:compileKotlinWinuiJvm` and
  `WinUISkikoRenderHostTest` pass. The repository-local WinUI sample reaches
  `compose-winui-sample: skiko render diagnostics`,
  `compose-winui-sample: saveable state restored`,
  `compose-winui-sample: retained value restored`, and the final
  `compose-winui-sample: text input session cancellation` log before the same
  `NTSTATUS 0xC0000005` process exit.
- **2026-06-03 focused Skiko sample split:** added a focused
  `:compose:ui:ui:winui-samples:runWinUISkikoSample` task that runs only the
  unattached Skiko scheduler deferral and attached render diagnostics smokes.
  This focused task reaches both Skiko smoke logs and exits successfully without
  producing a new WER dump. The default full `runWinUIViewSample` entrypoint
  still reaches `compose-winui-sample: text input session cancellation` and then
  exits with the same `NTSTATUS 0xC0000005`; no newer dump was produced, so the
  latest native evidence remains `java.exe.51408.dmp`.
- **2026-06-03 kt-winrt fix-claim retest:** direct Sonatype snapshot metadata
  and Gradle dependency insight still resolve `winrt-runtime` /
  `winrt-runtime-jvm` to `0.1.0-20260603.042831-23`,
  `winrt-gradle-plugin` to `0.1.0-20260603.043142-4`, and
  `winrt-compiler-plugin` to `0.1.0-20260603.042831-23`.
  `:compose:ui:ui:winui-samples:runWinUIViewSample` still reaches the final
  `compose-winui-sample: text input session cancellation` log and fails with
  process exit `NTSTATUS 0xC0000005`. This run did not produce a newer WER dump,
  so no fresh native bucket was available to analyze.
- **2026-06-03 skiko-winui typed-diagnostics retest:** after resolving
  `skiko-winui` / `skiko-winui-windows` to
  `0.0.0-20260603.075139-3` and removing the compose-side reflection bridge for
  render diagnostics, `:compose:ui:ui:winui-samples:runWinUISkikoSample` passes.
  The full `:compose:ui:ui:winui-samples:runWinUIViewSample` still reaches
  `compose-winui-sample: text input session cancellation` and exits with
  `NTSTATUS 0xC0000005`. No newer `java*.dmp` appeared in
  `%LOCALAPPDATA%\CrashDumps`, so there was no new native dump to analyze.
- **2026-06-03 Store WinDbg retest evidence:** Store WinDbg
  `10.0.29547.1002` analyzed
  `%LOCALAPPDATA%\CrashDumps\javaw.exe.55656.dmp`; the log is
  `out/compose-multiplatform-core/windbg-javaw-55656.log`. The bucket is
  `STOWED_EXCEPTION_c000027b_Microsoft.UI.Xaml.dll!FailFastWithStowedExceptions`;
  the stack goes through `KERNELBASE!RaiseFailFastException`,
  `combase!RoFailFastWithErrorContextInternal2`,
  `Microsoft_UI_Xaml!FailFastWithStowedExceptions`, and
  `Microsoft_UI_Xaml!DirectUI::FrameworkApplication::StartDesktop`. The stowed
  exception parameters include `0x8007000e`. This differs from the older
  unloaded-XAML AV dump but still points at WinUI/XAML runtime teardown or
  application lifetime, not a Java/Kotlin managed exception.
- **2026-06-03 kotlin-winrt fix-claim retest after cache clear:** stopped
  Gradle/Kotlin daemons, cleared the targeted kotlin-winrt `0.1.0-SNAPSHOT`
  Gradle artifact and descriptor cache directories, and reran the full sample.
  Direct Sonatype snapshot metadata still reports `winrt-runtime` and
  `winrt-compiler-plugin` `0.1.0-20260603.042831-23`, with
  `winrt-gradle-plugin` `0.1.0-20260603.043142-4`; Gradle downloaded fresh
  kotlin-winrt artifacts at 2026-06-03 16:26-16:27 local time. The full
  `:compose:ui:ui:winui-samples:runWinUIViewSample` again reached
  `compose-winui-sample: text input session cancellation` and exited with
  `NTSTATUS 0xC0000005`.
- **2026-06-03 cache-clear dump evidence:** Store CDB
  `10.0.29547.1002` analyzed the new dumps
  `%LOCALAPPDATA%\CrashDumps\java.exe.41020.dmp` and
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).41020.dmp`; logs are
  `out/compose-multiplatform-core/windbg-java-41020.log` and
  `out/compose-multiplatform-core/windbg-java-41020-1.log`. The first dump is
  back in the XAML dynamic metadata teardown bucket
  `INVALID_POINTER_READ_c0000005_Microsoft.UI.Xaml.dll!ctl::ComPtr_ABI::Microsoft::UI::Xaml::IFrameworkElement_::InternalRelease`,
  through
  `Microsoft_UI_Xaml!CCustomDependencyProperty::~CCustomDependencyProperty`,
  `Microsoft_UI_Xaml!DirectUI::DynamicMetadataStorage::~DynamicMetadataStorage`,
  `Microsoft_UI_Xaml!DeinitializeDll`, `ntdll!LdrUnloadDll`, and
  `combase!CoUninitialize`. The second dump is the paired unloaded-XAML execute
  bucket
  `SOFTWARE_NX_FAULT_INVALID_POINTER_EXECUTE_c0000005_Microsoft.UI.Xaml.dll!Unloaded`
  through `ntdll!RtlpFlsDataCleanup` / `ntdll!LdrShutdownThread`. This confirms
  the latest reproduced failure is still teardown/lifetime related and not a
  managed exception or a Skiko render failure.
- **2026-06-03 Skiko cache-clear retest:** after clearing the targeted
  `io.github.compose-fluent` Skiko snapshot Gradle cache, Gradle attempted to
  redownload `skiko-winui` `0.0.0-20260603.075139-3`; external Gradle TLS
  handshakes blocked that direct download, so the same timestamped
  `skiko-winui` / `skiko-winui-windows` jars were fetched with PowerShell and
  installed into Maven local. The full sample still reached
  `compose-winui-sample: text input session cancellation` and exited with
  `NTSTATUS 0xC0000005`. No newer WER dump was produced after this run, so the
  latest native evidence remains the paired `java.exe.41020.dmp` dumps above.
- **2026-06-03 17:06 +08 kt-winrt fix-claim retest:** direct Sonatype snapshot
  metadata still reports `winrt-runtime`, `winrt-runtime-jvm`,
  `winrt-authoring`, and `winrt-compiler-plugin`
  `0.1.0-20260603.042831-23`, with `winrt-gradle-plugin`
  `0.1.0-20260603.043142-4`. Gradle dependency insight resolves the sample
  runtime classpath to the same kotlin-winrt coordinates and `skiko-winui`
  `0.0.0-20260603.075139-3`. A normal full sample run and a forced
  `--rerun-tasks` run both reach
  `compose-winui-sample: text input session cancellation` and exit with
  `NTSTATUS 0xC0000005`. No newer WER dump was produced; the latest native
  evidence remains `%LOCALAPPDATA%\CrashDumps\java.exe.41020.dmp` /
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).41020.dmp`.
- **2026-06-03 18:49 +08 kt-winrt fix-claim retest:** direct Sonatype snapshot
  metadata still reports `winrt-runtime` and `winrt-compiler-plugin`
  `0.1.0-20260603.042831-23`; transient SSL handshake failures prevented
  reading every metadata file directly, and Gradle `--refresh-dependencies`
  remains blocked by external Maven/Google TLS handshake failures while
  resolving settings/buildSrc dependencies. A cached Gradle dependency insight
  still resolves the sample runtime classpath to `winrt-runtime`,
  `winrt-runtime-jvm`, and `winrt-authoring` `0.1.0-20260603.042831-23`,
  with `skiko-winui` `0.0.0-20260603.075139-3`. The full
  `:compose:ui:ui:winui-samples:runWinUIViewSample` again reaches the final
  `compose-winui-sample: text input session cancellation` log and exits with
  `NTSTATUS 0xC0000005`. No newer WER dump was produced; the latest native
  evidence remains `%LOCALAPPDATA%\CrashDumps\java.exe.41020.dmp` /
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).41020.dmp`.
- **2026-06-03 19:21 +08 kt-winrt fix-claim cache-clear retest:** direct
  Sonatype snapshot metadata still reports `winrt-runtime`,
  `winrt-runtime-jvm`, `winrt-authoring`, and `winrt-compiler-plugin`
  `0.1.0-20260603.042831-23`, with `winrt-gradle-plugin`
  `0.1.0-20260603.043142-4`. After stopping Gradle daemons and clearing the
  targeted `io.github.compose-fluent` snapshot artifact and descriptor caches
  under `GRADLE_USER_HOME=F:\Dependencies\gradle`, Gradle dependency insight
  re-resolved the sample runtime classpath to the same kotlin-winrt coordinates
  and `skiko-winui` `0.0.0-20260603.075139-3`. The full
  `:compose:ui:ui:winui-samples:runWinUIViewSample` still reaches the final
  `compose-winui-sample: text input session cancellation` log and exits with
  `NTSTATUS 0xC0000005`. Store CDB analyzed the fresh dumps
  `%LOCALAPPDATA%\CrashDumps\java.exe.58444.dmp` and
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).58444.dmp`; logs are
  `out/compose-multiplatform-core/windbg-java-58444.log` and
  `out/compose-multiplatform-core/windbg-java-58444-1.log`. The first dump is
  again
  `INVALID_POINTER_READ_c0000005_Microsoft.UI.Xaml.dll!ctl::ComPtr_ABI::Microsoft::UI::Xaml::IFrameworkElement_::InternalRelease`
  through `Microsoft_UI_Xaml!CCustomDependencyProperty::~CCustomDependencyProperty`,
  `Microsoft_UI_Xaml!DirectUI::DynamicMetadataStorage::~DynamicMetadataStorage`,
  `Microsoft_UI_Xaml!DeinitializeDll`, and `combase!CoUninitialize`. The paired
  dump is again
  `SOFTWARE_NX_FAULT_INVALID_POINTER_EXECUTE_c0000005_Microsoft.UI.Xaml.dll!Unloaded`
  through `ntdll!RtlpFlsDataCleanup` / `ntdll!LdrShutdownThread`. This retest
  does not resolve `KWINRT-024`; it confirms the currently published Maven
  snapshot is still in the same XAML dynamic-metadata / unloaded-XAML teardown
  bucket.
- **2026-06-03 23:00 +08 skiko snapshot retest:** after clearing targeted
  Skiko Gradle snapshot caches, Gradle resolved `skiko-winui` and
  `skiko-winui-windows` to `0.0.0-20260603.150039-4` while kotlin-winrt
  remained at `0.1.0-20260603.042831-23`. The focused Skiko sample passes, but
  the full `:compose:ui:ui:winui-samples:runWinUIViewSample` still reaches the
  final `compose-winui-sample: text input session cancellation` log and exits
  with `NTSTATUS 0xC0000005`. No newer WER dump was produced, so the latest
  native evidence remains `%LOCALAPPDATA%\CrashDumps\java.exe.58444.dmp` /
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).58444.dmp`.
  - **Current compose-winui policy:** do not block skiko-winui integration or
    follow-on compose-winui work on this teardown crash for now. Treat the sample
    reaching `compose-winui-sample: text input session cancellation` as successful
    validation of the current skiko-winui integration path, and revisit this issue
    only when teardown correctness becomes the active focus again.
  - **2026-06-04 local fix validation:** CDB confirmed the final crash was XAML
    worker-thread FLS cleanup entering `Microsoft.UI.Xaml.dll` dynamic metadata
    teardown and releasing a `Controls::IPanel` / `IFrameworkElement` vtable from
    `Microsoft.UI.Xaml.Controls.dll` after that module was already on an unload
    path. The local `kotlin-winrt` fix keeps real `CoUninitialize`, clears
    JVM-held XAML metadata/activation/composable caches before COM uninitialization,
    wraps generated `Application.Start` in a runtime-owned native module lifetime
    guard, and uses the generic `NativeModulePins` adapter to keep
    `Microsoft.UI.Xaml.dll` plus `Microsoft.UI.Xaml.Controls.dll` mapped for the
    XAML application lifetime. With the fixed local Maven artifacts,
    `:compose:ui:ui:winui-samples:runWinUIViewSample` reaches
    `compose-winui-sample: text input session cancellation` and exits successfully.
- **2026-06-04 Maven snapshot retest:** direct Sonatype metadata and Gradle
  dependency insight resolve `winrt-runtime`, `winrt-runtime-jvm`,
  `winrt-authoring`, and `winrt-compiler-plugin` to
  `0.1.0-20260604.125452-24`, with `winrt-gradle-plugin`
  `0.1.0-20260604.125740-5`. compose-winui also updated its custom sample
  `JavaExec` tasks to follow the README guidance by depending on both
  `stageWinRtRuntimeAssets` and `buildWinRtAuthoringHost`.
- **2026-06-04 Kotlin 2.4 retest:** after upgrading this repository to Kotlin
  `2.4.0`, `:compose:ui:ui:compileKotlinWinuiJvm`, the focused WinUI JVM tests,
  and `:compose:ui:ui:winui-samples:runWinUISkikoSample` pass with the
  published kotlin-winrt `0.1.0-20260604.125452-24` runtime/compiler artifacts.
  The full `:compose:ui:ui:winui-samples:runWinUIViewSample` still reaches
  `compose-winui-sample: text input session cancellation` and exits with
  `NTSTATUS 0xC0000005`. Fresh WER dumps were produced at
  `%LOCALAPPDATA%\CrashDumps\java.exe.5868.dmp` and
  `%LOCALAPPDATA%\CrashDumps\java.exe(1).5868.dmp`; CDB confirmed the dump
  stores an access violation but timed out before producing a useful stack.
- **2026-06-10 upstream merge / MPP sample retest:** after merging
  `origin/jb-main` into `winui_dev` and fixing WinUI source-set/API drift,
  `:compose:mpp:demo-winui:smokeWinUIMppSampleAutoTraverse` compiles through
  the WinUI variants for `compose-ui`, `foundation`, `material`, `material3`,
  `navigation-compose`, `navigation3-runtime`, `navigation3-ui`, adaptive
  material3, and the MPP sample. Runtime validation starts the WinUI window,
  logs `autorun-start`, `autorun-count:99`, `render-direct3d`, and
  `app-content-composed`, then the sample process exits with
  `NTSTATUS 0xC000027B`. Windows Event Log reports `APPCRASH java.exe` with
  faulting module `CoreMessagingXP.dll`, exception `0xc000027b`, and WER
  signature `P7: 8007000e`; the paired Windows Error Reporting event records a
  later `combase.dll` bucket with the same `8007000e` signature. No JVM
  `hs_err_pid` was produced for this run. This keeps the current MPP failure in
  the existing CoreMessaging/XAML application lifetime bucket rather than a
  managed Compose exception or Skiko render failure.

## KWINRT-025: Authored TypeDetails validation compares formatting differences

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot `0.1.0-SNAPSHOT`
  as of 2026-06-09.
- **Observed in:** `validateCompileKotlinWinuiJvmWinRtAuthoredCandidates` after
  clean regeneration with `--no-build-cache --rerun-tasks`.
- **Symptom:** scanner and compiler IR authored TypeDetails handoff files are
  reported as changed for
  `WinRT_WinUIRootContentControl_TypeDetails.kt` and
  `WinRT_WinUIXamlApplication_TypeDetails.kt`, even though the generated files
  differ only by KotlinPoet line wrapping/formatting.
- **Resolution:** compose-winui removed the validation normalization workaround;
  scanner and compiler TypeDetails now match without compose-side rewriting.
- **Validation:** `:compose:ui:ui:compileKotlinWinuiJvm --rerun-tasks` passes
  with `skiko-winui` `0.0.0-20260609.030224-9` and `skiko` `0.148.0` after the
  workaround is removed.

## KWINRT-026: Core text input focus registration fail-fast after full smoke

- **Status:** Fixed for the current compose-winui validation path with Maven
  snapshots on 2026-06-08.
- **Observed in:** `:compose:ui:ui:winui-samples:runWinUIViewSample` after the
  latest 2026-06-03 Maven snapshot retest. The full sample reaches
  `compose-winui-sample: text input session cancellation` before the process
  exits with `NTSTATUS 0xC0000005`. The focused
  `:compose:ui:ui:winui-samples:runWinUISkikoSample` task reaches
  `compose-winui-sample: skiko unattached scheduler deferred` and
  `compose-winui-sample: skiko render diagnostics`, then exits successfully
  without producing a new WER dump.
- **Snapshot baseline:** `skiko-winui` / `skiko-winui-windows`
  `0.0.0-20260603.023842-2`, `winrt-runtime` / `winrt-authoring`
  `0.1.0-20260603.042831-23`, `winrt-gradle-plugin`
  `0.1.0-20260603.043142-4`, and `winrt-compiler-plugin`
  `0.1.0-20260603.042831-23`.
- **Native evidence:** Store CDB `10.0.29547.1002` analyzed
  `%LOCALAPPDATA%\CrashDumps\java.exe.11048.dmp`; the log is
  `out/compose-multiplatform-core/windbg-java-11048.log`. The failure bucket is
  `APPLICATION_FAULT_675_textinputframework.dll!FailFastWithHR`; exception
  parameter 1 is `HRESULT 0x80004005`. The stack goes through
  `KERNELBASE!RaiseFailFastException`, `textinputframework!FailFastWithHR`,
  `textinputframework!TextboxRegistration::SelectionChanged`,
  `textinputframework!TextInputClient::NotifySelectionChanged`, `msctf`, and
  `Windows_UI_Core_TextInput!Windows::UI::Text::Core::CEditContext::NotifyFocusEnter`.
  The loaded `Microsoft.UI.Xaml.dll` is Windows App SDK `3.1.8.2604` from the
  staged kotlin-winrt/skiko WinUI application package.
- **Assessment:** this was a separate bucket from the `KWINRT-024`
  CoreMessaging/XAML teardown dumps. It pointed at WinUI core text input focus
  registration or lifetime during the full sample's text input path, not a
  Skiko render failure and not a Java/Kotlin managed exception.
- **2026-06-05 SkiaWinUISample dump:** Store WinDbg
  `Microsoft.WinDbg_1.2603.20001.0` was available but its `WinDbgX.exe`
  command-line launch did not produce a headless log; the same dump was then
  analyzed with local CDB and symbols. The latest
  `%LOCALAPPDATA%\CrashDumps\SkiaWinUISample.exe.13612.dmp` is the same
  fail-fast bucket: exception code `0x675` at
  `KERNELBASE!RaiseFailFastException`, stack through
  `textinputframework!FailFastWithHR` with `HRESULT 0x80004005`,
  `TextboxRegistration::SelectionChanged`,
  `TextInputClient::NotifySelectionChanged`,
  `TextInputClient::EditControlRegister`, `msctf`, and
  `Windows_UI_Core_TextInput!Windows::UI::Text::Core::CEditContext::NotifyFocusEnter`.
  The process was the upstream skiko sample host
  `compose-fluent-skiko\samples\SkiaWinUISample`, loaded Windows App SDK
  `Microsoft.UI.Xaml.dll` `3.1.8.2604`, and is evidence that this native
  text-input fail-fast is not specific to compose-winui's full smoke sample.
- **Resolution:** subsequent CoreText session ownership, focus, and state
  synchronization fixes made the current compose-winui path stable enough to
  attach CoreText by default whenever an input session and root-to-screen mapper
  are available. `compose.winui.textInput.coreText.disabled=true` remains an
  explicit diagnostic escape hatch; there is no current
  `compose.winui.textInput.coreText.enabled` opt-in gate. `CharacterReceived`
  remains the committed-text fallback when CoreText is unavailable or is not
  composing.
- **Validation:** validate the default-enabled policy with JDK 25, focused WinUI
  input tests, `:compose:ui:ui:compileKotlinWinuiJvm`, and the repository-local
  WinUI text-input/sample tasks without setting either CoreText system property.
  Reopen only with fresh native crash evidence from the current snapshots.

## KWINRT-027: Maven compiler plugin snapshot requires Kotlin 2.4 compiler APIs

- **Status:** Resolved for compose-winui by upgrading this repository to Kotlin
  `2.4.0`.
- **Observed in:** `:compose:ui:ui:compileKotlinWinuiJvm` after clearing the
  targeted Gradle snapshot caches and resolving kotlin-winrt artifacts to
  `0.1.0-20260604.125452-24` / Gradle plugin `0.1.0-20260604.125740-5`.
- **Symptom:** the Kotlin compiler plugin fails to load before WinUI runtime
  validation can run:
  `NoClassDefFoundError: org/jetbrains/kotlin/extensions/ExtensionPointDescriptor`
  from
  `io.github.composefluent.winrt.compiler.KotlinWinRtCompilerPluginRegistrar.registerExtensions`.
- **Evidence:** the current repository uses Kotlin `2.3.20`; its
  `kotlin-compiler-embeddable-2.3.20.jar` does not contain
  `org/jetbrains/kotlin/extensions/ExtensionPointDescriptor.class`. The freshly
  resolved `winrt-compiler-plugin` POM declares `kotlin-stdlib` and
  `kotlin-compiler-embeddable` `2.4.0`, and local inspection confirms
  `kotlin-compiler-embeddable-2.4.0.jar` does contain the missing class.
- **Resolution:** compose-winui now uses Kotlin `2.4.0`, adds a `KOTLIN_2_4`
  build target, and applies the small Kotlin 2.4 compatibility fixes needed for
  `buildSrc`, `compose-ui`, and `ui-text`. `compileKotlinWinuiJvm` now passes and
  the runtime validation proceeds to the separate `KWINRT-024` full-sample
  native crash.

## KWINRT-028: Prebuilt WinUI projection Application.start callback hang

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot `0.1.0-SNAPSHOT` as
  of 2026-06-07.
- **Observed in:** `:compose:ui:ui:winui-samples:runWinRtApplicationHost` and
  `:compose:ui:ui:winui-samples:runWinUISkikoSample` after switching
  compose-winui from local Windows App SDK NuGet projection generation to
  prebuilt projection artifacts.
- **Snapshot baseline:** `skiko-winui` / `skiko-winui-windows`
  `0.0.0-20260606.031302-6`, `winrt-runtime-jvm`
  `0.1.0-20260605.202352-37`, `winrt-gradle-plugin`
  `0.1.0-20260605.202635-18`,
  `winrt-projections-windows-sdk`
  `10.0.26100.0-kotlin-winrt-0.1.0-20260605.210602-1`, and
  `winrt-projections-windows-app-sdk`
  `2.1.3-kotlin-winrt-0.1.0-20260605.211043-1`.
- **Symptom:** the process does not throw the former
  `InputSystemCursor cannot inherit from final InputCursor`
  `IncompatibleClassChangeError`; instead it remains alive until killed. The
  JavaExec thread dump for the focused Skiko sample shows the main thread
  running inside `microsoft.ui.xaml.Application$Metadata.start`
  (`microsoft_ui_xaml.kt:561`) through the FFM downcall, with no Compose
  sample progress beyond the call into `Application.start`.
- **Evidence:** this issue belonged to the old prebuilt full-projection
  experiment. Later `skiko-winui` snapshots again publish their own WinUI
  projection classes, which is legitimate for Skiko's authored WinUI surface.
  The later duplicate-projection symptom was a compose-winui wiring error:
  the MPP sample imported local WinUI artifacts through `files(...)`, which
  bypassed Gradle/kotlin-winrt dependency identity propagation. A temporary
  compose-winui change that retained the created `WinUIXamlApplication` in a
  module-level variable did not change the hang; the callback still did not
  reach the sample launch path.
- **Expected behavior:** `Application.start { ... }` should invoke the
  initialization callback, create the authored `Application` subclass, and then
  dispatch `onLaunched`, matching the current kotlin-winrt README and upstream
  samples.
- **Resolution:** compose-winui now validates the generated `Application.start`
  path with explicit `type(...)` projection declarations and no prebuilt full
  projection dependencies.
- **Validation:** `runWinUISkikoSample` logs
  `compose-winui-sample: application starting mode=skiko` and exits
  successfully. `runWinUIViewSample` logs
  `compose-winui-sample: application starting mode=full`,
  `compose-winui-sample: application created`, window creation, focus/input,
  generated event cleanup, and Skiko diagnostics before exiting successfully.

## KWINRT-029: IContentCoordinateConverter single-point overload renders array path

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot `0.1.0-SNAPSHOT` as
  of 2026-06-07.
- **Observed in:** `:compose:ui:ui:generateWinRtProjections` and
  `:compose:ui:ui:compileKotlinWinuiJvm` after switching compose-winui away
  from prebuilt full projection artifacts and back to explicit `type(...)`
  declarations.
- **Snapshot baseline:** `skiko-winui` / `skiko-winui-windows`
  `0.0.0-20260606.141637-7`, `winrt-runtime-jvm`
  `0.1.0-SNAPSHOT`, and `winrt-gradle-plugin` `0.1.0-SNAPSHOT` resolved from
  Maven snapshots on 2026-06-06.
- **Symptom:** compose-winui declares explicit `type(...)` entries for the
  Windows SDK and Windows App SDK types it uses, including
  `Microsoft.UI.Xaml.Controls.Canvas`,
  `Microsoft.UI.Xaml.Controls.ContentControl`,
  `Microsoft.UI.Xaml.Controls.MenuFlyout`,
  `Microsoft.UI.Xaml.Input.FocusManager`,
  `Microsoft.UI.Input.InputSystemCursor`, and
  `Microsoft.UI.Xaml.Media.DesktopAcrylicBackdrop`. The generator emits the
  requested reduced projection surface, but the Windows App SDK dependency
  closure also includes `Microsoft.UI.Content.IContentCoordinateConverter`.
  The generated Kotlin for that interface does not compile.
- **Evidence:** generation with filtered Windows App SDK metadata emits the
  requested types such as `Windows.System.Launcher`,
  `Windows.UI.ViewManagement.UISettings`,
  `Microsoft.UI.Input.InputSystemCursor`,
  `Microsoft.UI.Xaml.Controls.Canvas`, `ContentControl`, and `MenuFlyout`.
  Compilation then fails in generated
  `microsoft/ui/content/microsoft_ui_content.kt` because
  `IContentCoordinateConverter.convertLocalToScreen(localPoint: Point)` is
  rendered with the unrelated `localPoints` array path and returns
  `Array<PointInt32>` where `PointInt32` is expected. The WinMD dependency
  chain appears legitimate; compose-winui should not exclude
  `IContentCoordinateConverter`, `ContentCoordinateConverter`,
  `ContentIsland`, `IXamlRoot3`, or `IXamlRoot4` to hide this bug.
- **Expected behavior:** the single-point
  `convertLocalToScreen(localPoint: Windows.Foundation.Point)` overload should
  use the `localPoint` parameter and return `Windows.Graphics.PointInt32`.
  The array overload should remain the only overload that uses `localPoints`
  and returns `Array<PointInt32>`.
- **Resolution:** the latest generated
  `microsoft/ui/content/microsoft_ui_content.kt` keeps
  `IContentCoordinateConverter`, `ContentCoordinateConverter`, and
  `ContentIsland` in the dependency closure. The single-point
  `convertLocalToScreen(localPoint: Point)` overload now has an independent
  `PointInt32` return path, while the array overloads remain separate.
- **Validation:** `:compose:ui:ui:compileKotlinWinuiJvm` and
  `:compose:ui:ui:winuiJvmTest` pass with 70 generated projection files in the
  reduced explicit-type surface.

## KWINRT-030: KMP partial dependency checker resolves kotlin-winrt identity early

- **Status:** Closed/stale with the current kotlin-winrt Maven snapshots
  consumed by the 2026-07-06 compose-winui validation baseline.
- **Observed in:** `:compose:ui:ui:kmpPartiallyResolvedDependenciesChecker`
  when running `:compose:ui:ui:compileKotlinWinuiJvm` together with
  `:compose:ui:ui:winuiJvmTest` after switching compose-winui to explicit
  `type(...)` projection declarations.
- **Snapshot baseline:** `winrt-runtime-jvm`
  `0.1.0-20260607.100854-44`, `winrt-gradle-plugin`
  `0.1.0-20260607.101153-25`, and `skiko-winui`
  `0.0.0-20260607.101016-8`.
- **Symptom:** the checker fails before projection compilation with
  `Cannot mutate the dependencies of configuration
  ':compose:ui:ui:kotlinWinRtLibraryDependencyIdentity' after the
  configuration was resolved`. The stacktrace shows Kotlin's
  `KmpPartiallyResolvedDependenciesChecker` resolving the graph, then
  `addKotlinDomApiDependency` firing while Gradle is resolving dependencies.
- **Resolution:** current compose-winui build scripts no longer carry the
  `kmpPartiallyResolvedDependenciesChecker` workaround, and the current
  kotlin-winrt `0.1.0-SNAPSHOT` Maven artifacts no longer reproduce the
  early-resolution failure. On 2026-07-06,
  `:compose:ui:ui:compileKotlinWinuiJvm`,
  `:compose:ui:ui:winuiJvmTest`,
  `:compose:ui:ui:winui-samples:runWinUIViewSample`, and
  `:compose:mpp:demo-winui:runWinUIMppSample` pass with configuration cache
  disabled.
- **Expected behavior:** kotlin-winrt should finish configuring
  `kotlinWinRtLibraryDependencyIdentity` before other Gradle/Kotlin validation
  tasks can observe or resolve it, or should use a lazy provider path that does
  not mutate the configuration after resolution begins.
- **compose-winui workaround:** disable
  `kmpPartiallyResolvedDependenciesChecker` only when the WinUI JVM target and
  kotlin-winrt Gradle plugin are enabled. This is a narrow validation unblocker;
  normal projection shape remains explicit `type(...)` declarations.
- **2026-06-08 navigation WinUI variant retest:** after adding repository-local
  WinUI JVM recompile variants for `navigation-compose` and `navigation3-ui`,
  `:compose:ui:ui:compileKotlinWinuiJvm` still passes, but
  `:compose:ui:ui:winui-samples:runWinUISkikoSample` can also trigger the same
  early-resolution failure while resolving the sample `runtimeClasspath` for its
  `project(:compose:ui:ui)` dependency. The symptom remains
  `Cannot mutate the dependencies of configuration
  ':compose:ui:ui:kotlinWinRtLibraryDependencyIdentity' after the configuration
  was resolved`; this extends `KWINRT-030` beyond the partially-resolved
  dependency checker into WinUI sample classpath resolution order.
- **2026-06-08 WinUI MPP PRI bootstrap retest:** adding app-owned PRI inputs to
  `:compose:mpp:demo-winui` made
  `:compose:mpp:demo-winui:stageWinRtRuntimeAssets` fail during task graph
  construction with the same mutation class, this time on
  `:compose:mpp:demo-winui:kotlinWinRtIdentity`. The trigger was Kotlin's
  late stdlib dependency addition while Gradle was already visiting task
  dependencies for the resolved identity file collection. The narrow
  compose-winui workaround is to declare `implementation(kotlin("stdlib"))`
  explicitly in the WinUI MPP sample so Kotlin does not add it during task
  dependency resolution. With that workaround, PRI staging, packaging
  validation, `runWinUIMppSample`, and `:compose:ui:ui:compileKotlinWinuiJvm`
  pass.
- **2026-06-09 Maven snapshot retest:** removing the compose-ui
  `kmpPartiallyResolvedDependenciesChecker` workaround and running
  `:compose:ui:ui:compileKotlinWinuiJvm :compose:ui:ui:winuiJvmTest` with
  `skiko-winui` `0.0.0-20260609.030224-9` and `skiko` `0.148.0` still fails in
  `:compose:ui:ui:kmpPartiallyResolvedDependenciesChecker` with
  `Cannot mutate the dependencies of configuration
  ':compose:ui:ui:kotlinWinRtLibraryDependencyIdentity' after the configuration
  was resolved`. The compose-ui checker-disable workaround remains required.

## KWINRT-031: Generated authoring TypeDetails use projection-unsafe runtime casts

- **Status:** Closed/stale with the current kotlin-winrt Maven snapshots
  consumed by the 2026-07-06 compose-winui validation baseline.
- **Observed in:** `:compose:ui:ui:compileKotlinWinuiJvm` after removing
  compose-winui's handwritten projection-unsafe casts to WinRT runtime classes.
- **Snapshot baseline:** `winrt-runtime-jvm`
  `0.1.0-20260607.100854-44`, `winrt-gradle-plugin`
  `0.1.0-20260607.101153-25`, and `skiko-winui`
  `0.0.0-20260607.101016-8`.
- **Symptom:** compilation still reports warnings such as
  `WinRT runtime class cast to Microsoft.UI.Xaml.Controls.ContentControl is not
  projection-safe; use WinRT projection cast helpers instead`, plus equivalent
  warnings for `Control`, `FrameworkElement`, `UIElement`, and `Application`.
- **Resolution:** the old projection-safe cast diagnostics no longer appear in
  the 2026-07-06 compose-winui validation logs. `compileKotlinWinuiJvm`,
  `winuiJvmTest`, the full WinUI sample, and the WinUI MPP sample all complete
  successfully with current generated authoring sources.
- **Evidence:** direct source search no longer finds handwritten
  `as` / `as?` casts to those WinRT runtime classes in the WinUI source or
  sample. The remaining casts are generated under
  `build/generated/kotlin-winrt-compiler-authoring/.../WinRT_*_TypeDetails.kt`,
  for example `(value as ContentControl)`,
  `(value as Control)`, `(value as FrameworkElement)`,
  `(value as UIElement)`, and `(value as Application)` before invoking
  generated `__winrtAuthoringInvoke...` methods.
- **Expected behavior:** generated authoring TypeDetails should use a
  projection-safe rewrap/query path or otherwise avoid emitting runtime-class
  Kotlin casts that kotlin-winrt's own compiler diagnostics flag as unsafe.
- **compose-winui workaround:** none. Handwritten compose-winui casts were
  replaced with narrow QueryInterface + runtime-class wrapper helpers, but the
  generated authoring diagnostics remain until kotlin-winrt changes the
  compiler generator output.

## KWINRT-032: Generated WinUI projections are final when WinMD supports subclassing

- **Status:** Closed/stale for the current compose-winui projection surface and
  dependency graph.
- **Observed in:** earlier `:compose:mpp:demo-winui:runWinUIMppSample` and
  `:compose:ui:ui:winui-samples:runWinRtApplicationHost` validation when
  compose-ui generated projection classes that overlapped types also generated
  by `skiko-winui`.
- **Symptom:** adding `type("Microsoft.UI.Xaml.Controls.Grid")` to the
  compose-ui explicit projection surface generates
  `public final class microsoft.ui.xaml.controls.Grid`. The skiko-winui authored
  class `org.jetbrains.skiko.winui.WinUISkiaHostPanel` extends `Grid`, so class
  loading fails with `IncompatibleClassChangeError: class
  org.jetbrains.skiko.winui.WinUISkiaHostPanel cannot inherit from final class
  microsoft.ui.xaml.controls.Grid`. The native host path also fails when
  generated `Microsoft.UI.Input.InputSystemCursor` tries to inherit from final
  generated `Microsoft.UI.Input.InputCursor`.
- **Resolution:** current compose-winui no longer reproduces the projection
  finality/class-shadowing failure. The current dependency graph keeps
  projection ownership compatible with `skiko-winui`, and the 2026-07-06
  validation baseline passes `compileKotlinWinuiJvm`, `winuiJvmTest`, the full
  WinUI sample, and the WinUI MPP sample without
  `IncompatibleClassChangeError`. Keep `Grid` out of the compose-ui explicit
  projection surface until a deliberate full-projection validation proves it is
  safe.
- **Evidence:** `javap` on the compose-ui generated `ui-winuijvm` jar shows
  `public final class microsoft.ui.xaml.controls.Grid`, while `javap` on the
  current published `skiko-winui` jar shows its generated `Grid` as non-final
  and implementing `WinRtComposableObject`. `Panel` generated by compose-ui is
  also non-final and composable, so the failure is specific to the generated
  `Grid` shape.
- **Expected behavior:** generated projections for WinUI classes that can be
  subclassed from authored Kotlin classes or by other WinRT runtime classes,
  including `Microsoft.UI.Xaml.Controls.Grid` and
  `Microsoft.UI.Input.InputCursor`, should be non-final and expose the
  composable/inheritable construction path expected by kotlin-winrt authoring
  and projection inheritance.
- **compose-winui workaround:** do not add `Grid` to the compose-ui projection
  surface yet, and do not strip `skiko-winui` projection classes. Keep
  compose-winui dependencies on normal Maven/project coordinates so
  kotlin-winrt can consume their identity and compiler-support metadata; do
  not reintroduce local `files(...)` jar dependencies for WinUI projection
  owners.

## KWINRT-033: Groovy DSL cannot consume explicit WinUI type declarations without generateProjection

- **Status:** Fixed for compose-winui with kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-09.
- **Observed in:** `compose/ui/ui/build.gradle` and
  `compose/ui/ui/winui-samples/build.gradle` after trying to follow the current
  `compose-fluent/kotlin-winrt` README WinUI setup, which uses
  `windowsSdk(...)`, `nugetPackage(...)`, and explicit `type(...)` declarations
  without `generateProjection = true`.
- **Symptom:** the Groovy DSL does not expose the two-argument
  `windowsSdk(version, includeExtensions)` overload used by the Kotlin DSL
  samples. Using the legacy three-argument form with
  `windowsSdk(version, false, false)` configures successfully, but
  `:compose:ui:ui:compileKotlinWinuiJvm` then fails because explicitly declared
  WinUI and Windows SDK types such as `FocusManager`, `Clipboard`, `Canvas`,
  `ContentControl`, `Launcher`, `UISettings`, and `InputSystemCursor` are not on
  the compile classpath.
- **Expected behavior:** Groovy builds should be able to use the same
  preprojection/explicit-type model as the README and Kotlin DSL samples:
  declare WinMD sources and `type(...)` entries without enabling full NuGet or
  Windows SDK projection generation, while still exposing the declared types to
  compilation.
- **Resolution:** compose-ui now uses Groovy `winmd(...)` metadata inputs plus
  explicit `type(...)` declarations and no `generateProjection` setting. The
  repository-local WinUI samples also no longer enable NuGet full projection
  generation.
- **Validation:** repository search finds no `generateProjection` usage in
  `compose/ui/ui/build.gradle`, `compose/ui/ui/winui-samples/build.gradle`, or
  `compose/mpp/demo-winui/build.gradle.kts`.
  `:compose:ui:ui:winui-samples:runWinUISkikoSample` and
  `:compose:ui:ui:winui-samples:runWinUIViewSample` pass with `skiko-winui`
  `0.0.0-20260609.030224-9` and `skiko` `0.148.0`.

## KWINRT-034: WinUI direct WinMD inputs leave CompositionTarget unsupported

- **Status:** Closed/superseded on 2026-06-08. This failure was caused by
  compose-winui still passing `generateWindowsSdkProjection=true` to the
  snapshot three-argument `windowsSdk(...)` DSL while moving to the requested
  explicit `type(...)` surface.
- **Observed in:** `:compose:ui:ui:generateWinRtProjections` after removing the
  temporary `winrt-projections-windows-app-sdk` dependency and supplying the
  Windows App SDK WinMDs directly through `winmd(...)` metadata inputs while
  keeping compose-ui's explicit `type(...)` projection surface.
- **Symptom:** projection generation fails with
  `Generator requires runtime class Windows.UI.Composition.Compositor ABI
  binding CREATETARGETFORCURRENTVIEW_SLOT return to use supported ABI metadata
  before projection rendering; found
  Unsupported(Microsoft.UI.Composition.CompositionTarget)`. Adding
  `type("Microsoft.UI.Composition.CompositionTarget")` does not change the
  failure.
- **Expected behavior:** when `Microsoft.UI.winmd` is present and
  `Microsoft.UI.Composition.CompositionTarget` is explicitly declared, the
  generator should classify the WinUI composition return type with supported
  ABI metadata instead of leaving it unsupported through the
  `Windows.UI.Composition.Compositor` binding path.
- **compose-winui fix:** use `windowsSdk(version, false, false)` so Windows SDK
  metadata remains available for explicitly requested types without generating
  the broad Windows SDK projection surface.

## KWINRT-035: ItemCollection VectorChanged event binding is not planned

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-09.
- **Observed in:** `:compose:ui:ui:generateWinRtProjections` with
  `windowsSdk(version, false, false)`, direct Windows App SDK WinMD inputs, and
  compose-ui's explicit `type(...)` projection surface.
- **Symptom:** projection generation fails with
  `Generator requires runtime class Microsoft.UI.Xaml.Controls.ItemCollection
  event VectorChanged add binding VECTORCHANGED_ADD_SLOT to be present before
  projection rendering.` Adding the Windows Foundation collection types and the
  WinUI XAML bindable collection/event types does not change the failure.
- **Expected behavior:** `ItemCollection` should either receive a valid
  `VectorChanged` event accessor binding from its WinUI collection interface
  metadata, or the generator should recognize the mapped collection runtime
  class shape and avoid requiring an event binding it cannot render.
- **Resolution:** latest Maven snapshot allows
  `:compose:ui:ui:generateWinRtProjections` to complete with the real WinUI
  `MenuFlyout` context-menu/text-toolbar path still projected.

## KWINRT-036: Windows.UI.Composition projections bind Microsoft.UI.Composition interfaces

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot
  `0.1.0-SNAPSHOT` as of 2026-06-09.
- **Observed in:** `:compose:ui:ui:compileKotlinWinuiJvm` after KWINRT-035 was
  fixed and projection generation completed.
- **Symptom:** generated `windows.ui.composition` runtime classes import and
  implement `microsoft.ui.composition` interfaces. For example,
  `windows.ui.composition.CompositionObject` emits `override val dispatcher`
  while implementing a Microsoft composition interface that does not declare
  that property, and `dispatcherQueue` has a `windows.system.DispatcherQueue?`
  return type that does not match the Microsoft interface return type. Other
  generated files import unresolved Microsoft composition symbols such as
  `microsoft.ui.composition.CompositionTarget` for Windows composition APIs.
- **Expected behavior:** Windows SDK `Windows.UI.Composition` projections should
  consistently reference `windows.ui.composition` interfaces and runtime
  classes, while Windows App SDK `Microsoft.UI.Composition` projections should
  consistently reference `microsoft.ui.composition`.
- **Validation:** after refreshing Maven snapshots on 2026-06-09 and cleaning
  generated output, `:compose:ui:ui:compileKotlinWinuiJvm` completes with
  compose-ui's explicit projection surface and no generated-interface
  exclusions.
- **compose-winui workaround:** none. Compose-winui keeps the real input
  projection surface and does not exclude generated interfaces to hide the
  generator namespace mapping issue.

## KWINRT-037: Authored override parameters using Windows.Foundation.Size have no metadata

- **Status:** Closed/not reproducible after completing the Maven snapshot
  download/cache population on 2026-06-09.
- **Observed in:** `:compose:ui:ui:generateWinRtProjections` after refreshing to
  current kotlin-winrt runtime `0.1.0-SNAPSHOT:20260609.020911-47` and trying
  skiko-winui `0.0.0-20260609.030224-9`.
- **Symptom:** projection generation fails before Kotlin compilation with
  `Authored WinRT override parameter 'availableSize' of type
  'Windows.Foundation.Size' has no metadata.`
- **Resolution:** this was most likely caused by an incomplete Gradle
  artifact/cache state after transient Maven snapshot download failures. After
  retrying the dependency fetch and confirming `skiko-winui`
  `0.0.0-20260609.030224-9` was fully available in the Gradle cache,
  `:compose:ui:ui:compileKotlinWinuiJvm` and `:compose:ui:ui:winuiJvmTest`
  completed successfully with the explicit Windows SDK type declarations.
- **compose-winui workaround:** none. Do not exclude authored classes or replace
  real XAML measure/arrange participation with no-op wrappers to hide the
  generator path.

## KWINRT-038: KeyboardAccelerator setters are public in bytecode but not callable from Kotlin

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot `0.1.0-SNAPSHOT`
  as of the 2026-06-25 retest.
- **Observed in:** `:compose:foundation:foundation:compileKotlinWinuiJvm` after
  adding native WinUI `KeyboardAccelerator` entries for text context-menu
  `MenuFlyoutItem`s.
- **Symptom:** generated `microsoft.ui.xaml.input.KeyboardAccelerator` exposes
  public JVM methods `setKey(windows.system.VirtualKey)` and the mangled
  `setModifiers-flDks54(int)` in bytecode, but Kotlin source cannot call
  `key = ...`, `setKey(...)`, `modifiers = ...`, or `setModifiers(...)`.
  `key = ...` reports `val cannot be reassigned`; the explicit setter names are
  unresolved.
- **Expected behavior:** generated mutable WinRT properties should be callable
  from Kotlin source, including flag/value-class properties such as
  `VirtualKeyModifiers`.
- **Resolution:** compose-winui removed the reflection helper and now sets
  `KeyboardAccelerator.key` and `KeyboardAccelerator.modifiers` directly while
  configuring `Ctrl+X`, `Ctrl+C`, `Ctrl+V`, and `Ctrl+A` native menu
  accelerators.

## KWINRT-039: Local jar wiring bypassed kotlin-winrt dependency identity

- **Status:** Closed as compose-winui wiring error.
- **Observed in:** `:compose:mpp:demo-winui:runWinUIMppSample` while the sample
  imported local WinUI modules with
  `implementation(files(localWinUiJarProjects.map(::localWinUiJar)))`.
- **Symptom:** local compose-winui artifacts were placed on the KMP graph as
  anonymous jar files instead of Gradle project/Maven components. That bypassed
  the kotlin-winrt identity and compiler-support dependency path, so downstream
  projection generation could not reliably see which WinRT types were already
  owned by upstream WinUI artifacts.
- **Evidence:** the same classpath also had a compose-side filtered
  `skiko-winui-projection-free.jar` workaround. Both were symptoms of treating
  projection ownership as a runtime classpath cleanup problem instead of a
  Gradle component identity problem.
- **Failed compose-side experiment:** generating a local identity JSON from a
  dependency jar's projection class names and feeding it to
  `generateWinRtProjections`/`mergeWinRtCompilerSupport` is not sufficient. It
  can suppress base type generation without providing matching compiler-support
  and inheritable runtime-class shape, leading to final/inaccessible-constructor
  errors for generated subclasses.
- **Resolution:** the WinUI MPP sample now depends on local WinUI artifacts
  through normal `project(...)` dependencies and consumes `skiko-winui` from
  Maven snapshots. The filtered Skiko jar workaround was removed. Do not
  reintroduce `files(...)` dependencies for projection-owning WinUI artifacts.

## KWINRT-040: KMP library authoring manifest targets the wrong JVM artifact

- **Status:** Closed in the kotlin-winrt Maven snapshot consumed by the
  2026-07-05 compose-winui validation path.
- **Observed in:** `:compose:ui:ui:winui-samples:buildWinRtApplicationHost`
  after `WinUIXamlApplication` became an authored `Application` subclass.
- **Symptom:** the reusable KMP `:compose:ui:ui` module generates
  `kotlin-winrt-authoring/ui.host.json` with `targetArtifact` set to `ui.jar`,
  but the staged WinUI JVM artifact is
  `ui-winuijvm-9999.0.0-SNAPSHOT.jar`. The generated application host starts,
  enters Kotlin `main`, and then `Application.start { ... }` returns without
  `onLaunched` because the runtime authoring config maps
  `androidx.compose.ui.window.WinUIXamlApplication` to a jar that is not on the
  staged runtime classpath.
- **Expected behavior:** for Kotlin Multiplatform JVM artifacts, kotlin-winrt
  should use the target JVM jar archive file that will be staged for runtime
  instead of falling back to `project.name.jar`.
- **Resolution:** compose-winui no longer carries a target-artifact override in
  `compose/ui/ui/build.gradle`; kotlin-winrt now derives the WinUI JVM jar name
  for the generated authoring host metadata.
- **Validation:** the generated
  `build/classes/kotlin/winuiJvm/main/kotlin-winrt-authoring/ui.host.json` and
  staged sample `runtime-assets/ui.host.json` both use
  `targetArtifact: "ui-winuijvm-9999.0.0-SNAPSHOT.jar"` and route
  `androidx.compose.ui.window.WinUIXamlApplication` to the same target artifact.
  The WinUI sample host and MPP sample host reach the current validation paths
  with this metadata.

## KWINRT-041: Dependency-owned WinRT types could link with incompatible generated shape

- **Status:** Closed/stale for the current compose-winui dependency graph.
- **Historical symptom:** older `skiko-winui` snapshots published overlapping
  `microsoft/**` and `windows/**` projection classes that could shadow
  compose-ui generated classes at application-host runtime, producing
  `IncompatibleClassChangeError` failures such as `InputSystemCursor` extending
  a final `InputCursor`.
- **Resolution:** compose-winui no longer needs to force compose-owned WinRT
  projection jars before `skiko-winui` in the repository-local WinUI sample
  hosts. The `KWINRT-041` classpath-order comments and runtime classpath
  front-loading were removed from `compose/ui/ui/winui-samples` and
  `compose/mpp/demo-winui`.
- **Validation:** with JDK 25 and `--no-configuration-cache`, both
  `:compose:ui:ui:winui-samples:buildWinRTApplicationHost` and
  `:compose:mpp:demo-winui:buildWinRTApplicationHost` pass without the
  classpath-order workaround.

## KWINRT-042: Authored SystemBackdrop override bridge wraps interface arguments as IInspectable

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot `0.1.0-SNAPSHOT`
  as of the 2026-06-18 retest.
- **Observed in:** `:compose:ui:ui:compileKotlinWinuiJvm` after adding
  `WinUITransparentBackdrop : Microsoft.UI.Xaml.Media.SystemBackdrop` for
  WinUI window-popup transparency.
- **Symptom:** generated
  `WinRT_WinUITransparentBackdrop_TypeDetails.kt` does not compile because the
  authoring bridge calls
  `ICompositionSupportsSystemBackdrop.Metadata.wrap(IInspectableReference(...))`,
  while that generated `wrap` overload expects `IUnknownReference`.
- **Expected behavior:** generated authoring bridges should wrap interface
  override arguments with the reference type expected by the target interface
  projection, or the interface projection should expose a matching inspectable
  wrapper overload.
- **Resolution:** the generated
  `WinRT_WinUITransparentBackdrop_TypeDetails.kt` now wraps
  `ICompositionSupportsSystemBackdrop` override arguments with
  `IUnknownReference` directly; the compose-side generated-source patch has
  been removed.
- **Validation:** `:compose:ui:ui:compileKotlinWinuiJvm` passes with
  `--refresh-dependencies --no-configuration-cache --no-configure-on-demand`
  and the generated TypeDetails source contains `IUnknownReference` for the
  backdrop target argument.

## KWINRT-043: Nullable struct projection setter fails for FlyoutShowOptions.Position

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot `0.1.0-SNAPSHOT`
  as of the 2026-06-18 retest.
- **Observed in:** diagnostic WinUI Flyout popup host while calling
  `FlyoutShowOptions.position = Windows.Foundation.Point(...)`.
- **Symptom:** setting the nullable struct property throws
  `WinRtUnsupportedOperationException: Managed COM object does not implement
  interface 'BA5CFAD3-43D4-5FF2-94B0-5223818BDF04'` from
  `WinRtReferenceProjectionInterop.setReferenceValue(...)`, before
  `FlyoutBase.showAt(target, options)` can be called.
- **Expected behavior:** generated nullable value/struct property setters
  should box authored JVM values such as `Windows.Foundation.Point` into the
  WinRT reference type accepted by the ABI setter.
- **Resolution:** the diagnostic Flyout popup workaround was removed from the
  active issue triage; reopen only with fresh native validation evidence.

## KWINRT-044: Non-WinUI authored-candidate validation checks WinUI authored types

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot `0.1.0-SNAPSHOT`
  as of the 2026-06-18 retest.
- **Observed in:** `:compose:ui:ui:winui-samples:buildWinRtApplicationHost`
  after kotlin-winrt moved generated projection sources to
  `generated/kotlin-winrt*/src/commonMain/kotlin`.
- **Symptom:** `validateCompileKotlin*WinRtAuthoredCandidates` tasks for
  non-WinUI targets compare authored WinUI candidates scanned from
  `src/winuiMain/kotlin` against compiler outputs for targets such as iOS and
  macOS, where those WinUI-only authored types are intentionally absent.
- **Expected behavior:** authored-candidate validation should be scoped to
  compilations that actually receive the scanned WinUI authored source roots,
  or the plugin should expose a target-scoped scanner/validation model for KMP
  source sets.
- **Resolution:** compose-winui no longer carries the non-WinUI authored
  candidate validation disablement.

## KWINRT-045: Downstream projection regenerates dependency-owned authored Application subclass

- **Status:** Fixed upstream in kotlin-winrt Maven snapshot `0.1.0-SNAPSHOT`
  as of the 2026-06-18 retest.
- **Observed in:** `:compose:ui:ui:winui-samples:buildWinRtApplicationHost`
  while the sample depends on `:compose:ui:ui`, whose WinUI artifact already
  owns the authored `androidx.compose.ui.window.WinUIXamlApplication` runtime
  class.
- **Symptom:** the downstream sample's generated projection includes
  `androidx.compose.ui.window.WinUIXamlApplication : microsoft.ui.xaml.Application`.
  That generated source does not compile because it tries to call
  `Application(_inner, Unit)`, whose WinRT wrapper constructor is internal to
  the upstream `microsoft.ui.xaml` projection package/module.
- **Expected behavior:** dependency-owned authored runtime classes should be
  consumed through kotlin-winrt dependency identity/compiler-support metadata,
  not regenerated in downstream applications. If regeneration is required, the
  generated subclass path must use a public dependency-safe wrapper/activation
  shape.
- **Resolution:** the downstream sample no longer regenerates
  `androidx.compose.ui.window.WinUIXamlApplication`; it consumes the class from
  `:compose:ui:ui`. Remaining startup failures are tracked separately as
  `KWINRT-046` and `KWINRT-047`.
