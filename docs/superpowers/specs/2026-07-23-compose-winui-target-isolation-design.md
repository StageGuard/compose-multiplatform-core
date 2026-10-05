# Compose WinUI Target Isolation Design

## Goal

Compile and publish compose-winui as the only locally implemented Compose UI
platform family when a WinUI target is enabled. The local variants are
`winuiJvm` and `winuiMingw`; existing Android, Desktop, Web, and Apple variants
must come from their corresponding upstream artifacts instead of forcing the
WinUI fork to compile those implementations from source.

This removes the invalid Kotlin/Native fragment graph that currently places
`nativeMain` and `winuiMingwMain` in the same compilation as unrelated roots,
without making the normal Compose Multiplatform build lose its established
`skikoMain` hierarchy.

## Confirmed Failure Model

The current `compileKotlinWinuiMingw` compilation contains these source sets:

```text
commonMain, skikoHostMain, skikoRenderingMain, skikoMain, nonJvmMain,
nativeMain, mingwMain, winuiMain, winuiMingwMain
```

The current WinUI workaround detaches `mingwMain` and `winuiMingwMain` from the
default Native hierarchy. Kotlin/Native therefore sees `nativeMain` and
`winuiMingwMain` as separate compiled fragment roots. Ordinary same-file
interface overrides fail in that graph, producing the broad
`overrides nothing` diagnostics previously attributed to `KWINRT-061`.

Restoring a single root removes every `overrides nothing` diagnostic. Directly
restoring the old ancestry also imports all of `skikoMain` through
`nativeMain -> nonJvmMain -> skikoMain`, which conflicts with WinUI platform
actuals. This is a Compose source-set modeling problem, not a kotlin-winrt
projection ownership problem.

The same validation also confirms that
`AndroidXComposeImplPlugin.applyPlugin` replaces the complete Native
`compilerPluginClasspath`. The final task classpath contains the Compose
compiler only and drops kotlin-winrt's compiler plugin.

## Build Modes

### Normal Compose Multiplatform mode

When neither `composeWinUi.enableJvmTarget` nor
`composeWinUi.enableMingwTarget` is enabled, preserve the existing source-set
and publication behavior. In particular:

```text
nativeMain -> nonJvmMain -> skikoMain
```

continues to serve UIKit, macOS, Web, Desktop, and the normal CMP publication
graph.

### WinUI target mode

Enabling either WinUI target switches affected Compose modules to WinUI target
mode. In this mode:

- locally compiled platform variants are `winuiJvm` and/or `winuiMingw` only;
- `nonJvmMain` does not depend on `skikoMain`;
- `nativeMain` retains its platform-neutral Kotlin/Native actuals through
  `nonJvmMain`;
- the default `winuiMingwMain -> mingwMain -> nativeMain` hierarchy remains
  intact;
- `winuiMingwMain` additionally depends on `winuiMain`;
- no source-set edge is removed after Kotlin's hierarchy model has been built.

The resulting WinUI Native graph is:

```text
commonMain
|- nonJvmMain
|  `- nativeMain
|     `- mingwMain
|        `- winuiMingwMain
`- skikoHostMain
   `- winuiMain
      `- winuiMingwMain
```

`winuiMingwMain` is the sole compiled fragment root. `skikoMain` is not part of
the WinUI Native compilation.

## Upstream Variant Redirection

WinUI target mode must not republish locally compiled substitutes for platforms
that the fork does not build. Publication and dependency metadata use target
redirection in the same style as the repository's existing CMP-to-AndroidX
redirection:

- Android variants continue redirecting to the matching Jetpack Compose
  artifact and `artifactRedirection.version.androidx.compose` version.
- Desktop, Web, Apple, Linux, and their target metadata variants redirect to
  the matching `org.jetbrains.compose.*` artifact at a configured CMP version.
- `winuiJvm` and `winuiMingw` variants remain local and are never redirected.

The CMP fallback version is supplied by one root property:

```properties
composeWinUi.cmpVersion=1.10.0
```

`1.10.0` is the repository's current matching CMP baseline. This one property
must be updated deliberately when the fork syncs to a different CMP release.

WinUI target mode fails configuration with a clear message if a non-WinUI
redirect is required and this version is absent. It must not silently use the
fork's publication version because a fork snapshot can differ from the upstream
CMP version it extends.

The redirection implementation extends the existing target-aware publication
model rather than adding per-module dependency substitutions. One published
module can therefore expose three ownership classes:

1. Android redirect variants owned by Jetpack Compose;
2. non-WinUI multiplatform redirect variants owned by CMP;
3. locally built WinUI variants owned by this fork.

Root metadata and capabilities must continue selecting a local project for
WinUI configurations while selecting the redirected external artifact for all
other target configurations. A redirect must never substitute a WinUI
configuration back to the same module coordinates.

## WinUI Actual Ownership

`winuiMain` remains a complete WinUI platform source set. It does not inherit
`skikoMain` in WinUI target mode.

The conflicting Skiko/WinUI actuals are handled as follows:

- Keep platform-specific WinUI scheduling (`postDelayed` and `removePost`).
- Keep the WinUI `UiApplier` and `Dialog` host behavior.
- Keep native Popup/Flyout host, focus, dismissal, XAML-root, rendering, input,
  and UI Automation behavior in `winuiMain`; generic Popup forwarding wrappers
  may be shared later but are not required to unblock the target.
- Keep the current WinUI implementations for all other actuals during initial
  target isolation. Moving generic Autofill, mesh-gradient, semantics-region,
  key, pointer, lifecycle, or inset logic into a new shared source set is a
  separate upstream-sync optimization, not part of the first compiling MinGW
  step.

This deliberately favors a small Gradle/source-set fix over moving high-churn
upstream files merely to remove source duplication.

## Target-Specific WinUI Native Actuals

After the fragment graph is corrected, `winuiMingwMain` supplies only behavior
that cannot live in shared `winuiMain`:

- Native process-property/debug-log access;
- DWM transparent-backdrop and window-corner calls;
- window display-affinity capture protection.

Projected WinRT/WinUI behavior that works on both runtimes, including HWND and
InputPane acquisition through generated kotlin-winrt APIs, belongs in
`winuiMain` rather than being duplicated in `winuiJvmMain` and
`winuiMingwMain`.

Native platform implementations use Kotlin/Native Windows APIs and generated
kotlin-winrt projections. They must not use process-shell fallbacks or
unverified no-op stubs.

## Compiler Plugin Composition

Compose compiler configuration must append its plugin files to an existing
Native task classpath:

```kotlin
compilerPluginClasspath.from(plugins)
```

It must not assign a replacement `FileCollection`. The verified final
`compileKotlinWinuiMingw` classpath must contain both:

- `kotlin-compose-compiler-plugin-embeddable`;
- `winrt-compiler-plugin`.

The same additive behavior applies to every Kotlin/Native task configured by
`AndroidXComposeImplPlugin`, not only the WinUI task.

## Testing

Implementation follows test-first increments.

### Structural tests

- WinUI target mode has one `winuiMingwMain` fragment root.
- `nativeMain` does not reach `skikoMain` in WinUI target mode.
- normal CMP mode preserves `nativeMain -> nonJvmMain -> skikoMain`.
- the old detach helper is absent.
- Native Compose compiler configuration appends instead of replacing plugin
  files.
- WinUI Native compiler plugin diagnostics contain both Compose and WinRT
  compiler plugins.

### Redirection tests

- Android target metadata still redirects to the configured Jetpack Compose
  version.
- representative Desktop, iOS, JS/Wasm, and standard Native variants redirect
  to `org.jetbrains.compose.*` at `composeWinUi.cmpVersion`.
- `winuiJvm` and `winuiMingw` resolve local variants.
- missing `composeWinUi.cmpVersion` fails only when WinUI target mode needs CMP
  fallback metadata.
- no redirected dependency points back to the fork's own publication version.

### Required build validation

Use JDK 25, the user `.konan` directory, direct Maven access, and
`--no-configuration-cache`.

```powershell
.\gradlew.bat :compose:ui:ui:compileKotlinWinuiMingw `
  '-PcomposeWinUi.enableMingwTarget=true' `
  '-PcomposeWinUi.cmpVersion=1.10.0' `
  --no-configuration-cache --no-configure-on-demand
```

Then validate the established JVM path:

```powershell
.\gradlew.bat :compose:ui:ui:compileKotlinWinuiJvm `
  :compose:ui:ui:winuiJvmTest `
  '-PcomposeWinUi.enableJvmTarget=true' `
  '-PcomposeWinUi.cmpVersion=1.10.0' `
  --no-configuration-cache --no-configure-on-demand

.\gradlew.bat :compose:ui:ui:winui-samples:runWinUISkikoSample `
  '-PcomposeWinUi.enableJvmTarget=true' `
  '-PcomposeWinUi.cmpVersion=1.10.0' `
  --no-configuration-cache --no-configure-on-demand
```

Normal non-WinUI configuration tests must also prove that the unchanged CMP
source-set graph still configures successfully.

## Documentation and Issue Status

`KWINRT-061` is closed as a misattributed upstream issue. Its final record must
state that the refreshed kotlin-winrt and Skiko MinGW artifacts work, while the
Compose fragment graph caused the broad override diagnostics.

The compose-winui plan records target isolation, CMP fallback redirection, and
Native compiler-plugin composition as Compose-owned implementation work.

## Success Criteria

- `compileKotlinWinuiMingw` contains one connected source-set graph and reports
  no `overrides nothing` or duplicate actual diagnostics.
- the Native compiler runs both Compose and kotlin-winrt compiler plugins;
- `compileKotlinWinuiMingw` completes without projection/source stubs;
- WinUI JVM compilation, focused tests, and repository sample remain green;
- normal CMP mode retains its existing UIKit, Desktop, Web, and Android source
  hierarchy;
- non-WinUI published variants resolve to the explicitly configured upstream
  Jetpack Compose or CMP owner instead of being rebuilt by the WinUI fork.
