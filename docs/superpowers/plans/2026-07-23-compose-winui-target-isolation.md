# Compose WinUI Target Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `winuiJvm` and `winuiMingw` the only locally owned Compose platform variants in WinUI mode, keep the WinUI Native source-set graph connected without importing `skikoMain` through `nativeMain`, and redirect every other published target to its Jetpack Compose or Compose Multiplatform owner.

**Architecture:** Extend artifact redirection from one owner per module to one owner per target, preserving Android-to-AndroidX redirection and adding CMP ownership for non-WinUI targets and root metadata at `composeWinUi.cmpVersion=1.10.0`. In normal mode, route Skiko-only actuals through `skikoNonJvmMain` and `skikoNativeMain`. In WinUI mode, retain Kotlin's connected `winuiMingwMain -> mingwMain -> nativeMain -> nonJvmMain` hierarchy, omit only the two edges into those Skiko-only source sets, and join WinUI behavior through `winuiMingwMain -> winuiMain`. Compose compiler configuration appends to the Native compiler-plugin classpath so kotlin-winrt remains active.

**Tech Stack:** Gradle/Kotlin build logic, Kotlin Multiplatform source sets, Kotlin/Native MinGW, Compose compiler plugin, kotlin-winrt generated projections, Windows App SDK/WinUI 3, JUnit 4, Kotlin test.

## Global Constraints

- `composeWinUi.cmpVersion` is exactly `1.10.0` for this implementation.
- Android target variants continue redirecting to `androidx.compose.*` with the existing `artifactRedirection.version.androidx.compose` family properties.
- Desktop, JVM, JS, Wasm, Apple, Linux, and standard MinGW variants redirect to the matching `org.jetbrains.compose.*` module at `composeWinUi.cmpVersion` only while a WinUI target is enabled.
- `winuiJvm` and `winuiMingw` always remain local and must never match a redirection rule.
- Normal non-WinUI mode preserves `nonJvmMain -> skikoNonJvmMain -> skikoMain` and `nativeMain -> skikoNativeMain -> skikoMain`.
- WinUI mode preserves one connected Native graph and omits only `nonJvmMain -> skikoNonJvmMain` and `nativeMain -> skikoNativeMain`; it must not mutate `dependsOn` collections after hierarchy creation.
- Keep `winuiMain` independent from Desktop, AWT, Swing, and `SkiaLayer`.
- Use generated kotlin-winrt projections and Kotlin/Native Windows APIs; do not add process-shell fallbacks, projection stubs, or no-op Native actuals.
- Use JDK 25, `$env:USERPROFILE\.konan`, direct Maven access, and `--no-configuration-cache --no-configure-on-demand` for WinUI validation.
- Use `git.exe` for status, staging, and commits; keep `AGENTS.md`, `.agent_tmp/`, `.codex/config.toml`, and `.dotnet/` untracked.

---

### Task 1: Add target-owned artifact redirection

**Files:**
- Create: `buildSrc/public/src/test/kotlin/org/jetbrains/androidx/build/ArtifactRedirectionTest.kt`
- Modify: `buildSrc/public/src/main/kotlin/org/jetbrains/androidx/build/ArtifactRedirection.kt`
- Modify: `buildSrc/private/src/main/kotlin/org/jetbrains/androidx/build/JetBrainsAndroidXImplPlugin.kt`
- Modify: `buildSrc/private/src/main/kotlin/org/jetbrains/androidx/build/JetBrainsCapabilityRule.kt`
- Modify: `buildSrc/private/src/main/kotlin/org/jetbrains/androidx/build/JetBrainsAndroidXRedirectingPublicationHelpers.kt`
- Modify: `gradle.properties`

**Interfaces:**
- Produces: `ArtifactRedirectCoordinates(groupId: String, version: String)`.
- Produces: `ArtifactRedirection.coordinatesForTarget(targetName: String): ArtifactRedirectCoordinates?`.
- Produces: `ArtifactRedirection.coordinatesForConfiguration(configurationName: String): ArtifactRedirectCoordinates?`.
- Consumes: existing `artifactRedirection.targetNames`, group replacement, default version, and per-target version properties.
- Consumes: `composeWinUi.enableJvmTarget`, `composeWinUi.enableMingwTarget`, and `composeWinUi.cmpVersion`.

- [x] **Step 1: Write failing multi-owner tests**

Create JUnit tests with `ProjectBuilder` that assert:

```kotlin
@Test
fun winuiModeKeepsAndroidOwnerAndAddsCmpOwners() {
    val project = composeProject(
        "artifactRedirection.targetNames" to "android",
        "artifactRedirection.groupIdReplacement" to
            "org.jetbrains.compose->androidx.compose",
        "artifactRedirection.version.androidx.compose" to "1.12.0-alpha03",
        "composeWinUi.enableMingwTarget" to "true",
        "composeWinUi.cmpVersion" to "1.10.0",
    )

    val redirection = checkNotNull(project.readArtifactRedirection())
    assertEquals(
        ArtifactRedirectCoordinates("androidx.compose.ui", "1.12.0-alpha03"),
        redirection.coordinatesForTarget("android"),
    )
    assertEquals(
        ArtifactRedirectCoordinates("org.jetbrains.compose.ui", "1.10.0"),
        redirection.coordinatesForTarget("desktop"),
    )
    assertEquals(
        ArtifactRedirectCoordinates("org.jetbrains.compose.ui", "1.10.0"),
        redirection.coordinatesForConfiguration("iosArm64MetadataElements"),
    )
    assertNull(redirection.coordinatesForTarget("winuiJvm"))
    assertNull(redirection.coordinatesForTarget("winuiMingw"))
}

@Test
fun normalModeKeepsOnlyConfiguredRedirections() {
    val redirection = checkNotNull(normalComposeProject().readArtifactRedirection())
    assertNotNull(redirection.coordinatesForTarget("android"))
    assertNull(redirection.coordinatesForTarget("desktop"))
}

@Test
fun winuiModeRequiresCmpVersion() {
    val error = assertFailsWith<GradleException> {
        winuiComposeProjectWithoutCmpVersion().readArtifactRedirection()
    }
    assertTrue(error.message.orEmpty().contains("composeWinUi.cmpVersion"))
}
```

- [x] **Step 2: Run the focused tests and verify RED**

Run:

```powershell
.\gradlew.bat -p buildSrc :public:test `
  --tests org.jetbrains.androidx.build.ArtifactRedirectionTest `
  --no-configuration-cache --no-configure-on-demand
```

Expected: test compilation fails because target-specific coordinate APIs do not exist.

- [x] **Step 3: Implement the target-to-owner model**

Replace the single group/default-version data model with:

```kotlin
data class ArtifactRedirectCoordinates(
    val groupId: String,
    val version: String,
)

data class ArtifactRedirection(
    private val coordinatesByTarget: Map<String, ArtifactRedirectCoordinates>,
) {
    val targetNames: Set<String> = coordinatesByTarget.keys

    fun coordinatesForTarget(targetName: String): ArtifactRedirectCoordinates? =
        coordinatesByTarget[targetName.lowercase()]

    fun coordinatesForConfiguration(
        configurationName: String,
    ): ArtifactRedirectCoordinates? =
        targetNames
            .sortedByDescending(String::length)
            .firstOrNull { configurationName.startsWith(it, ignoreCase = true) }
            ?.let(coordinatesByTarget::getValue)

    fun allCoordinates(): Set<ArtifactRedirectCoordinates> =
        coordinatesByTarget.values.toSet()
}
```

Parse existing Android redirection properties into this map. When either WinUI property is true and the project group starts with `org.jetbrains.compose`, add CMP owners for `desktop`, `jvm`, `js`, `wasmjs`, macOS, iOS, tvOS, watchOS, Linux, and regular `mingwx64`; never add `winuijvm` or `winuimingw`. Require `composeWinUi.cmpVersion` at that point.

- [x] **Step 4: Make every publication and capability consumer target-aware**

Use `coordinatesForTarget` in Native dependency substitution and POM redirection. Use `coordinatesForConfiguration` when creating the redirect dependency and outgoing capability. If a consumable configuration has no redirected target prefix, add only the local capability; this is the guard that keeps WinUI configurations local.

- [x] **Step 5: Add the centralized CMP baseline**

Add to root `gradle.properties` beside the artifact-redirection versions:

```properties
# Upstream Compose Multiplatform owner for non-WinUI variants in compose-winui mode.
composeWinUi.cmpVersion=1.10.0
```

- [x] **Step 6: Run tests, formatting checks, and commit**

Run the focused `-p buildSrc :public:test` test plus `:public:check :private:check`. Expected: PASS. Commit with `git.exe` using `build: support target-owned compose redirection`.

### Task 2: Restore the connected WinUI Native hierarchy and compose compiler plugins

**Files:**
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUISourceSetIsolationTest.kt`
- Modify: `compose/ui/ui/build.gradle`
- Modify: `compose/ui/ui-graphics/build.gradle`
- Modify: `compose/ui/ui-text/build.gradle`
- Modify: `compose/foundation/foundation/build.gradle`
- Modify: `compose/foundation/foundation-layout/build.gradle`
- Modify: `buildSrc/private/src/main/kotlin/androidx/build/AndroidXComposeImplPlugin.kt`
- Create for diagnostic execution only: `.agent_tmp/verify-winui-target-isolation.init.gradle`

**Interfaces:**
- Produces in normal mode: Skiko-only actual ancestry through `skikoNonJvmMain` and `skikoNativeMain`.
- Produces in WinUI mode: `nonJvmMain` and `nativeMain` without those Skiko-only parents.
- Produces: implicit `winuiMingwMain -> mingwMain -> nativeMain -> nonJvmMain` plus explicit `winuiMingwMain -> winuiMain`.
- Produces: additive Native `compilerPluginClasspath.from(plugins)`.

- [x] **Step 1: Add failing source-wiring guards**

Extend `WinUISourceSetIsolationTest` to assert that the detach closure/string is absent, `nonJvmMain` and `nativeMain` gate their `skikoNonJvmMain` / `skikoNativeMain` parents with `if (!composeWinUiTargetEnabled)`, `winuiMingwMain` depends on `winuiMain`, and `AndroidXComposeImplPlugin.kt` contains `compilerPluginClasspath.from(plugins)` without `compilerPluginClasspath = plugins`.

- [x] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :compose:ui:ui:winuiJvmTest `
  --tests androidx.compose.ui.platform.WinUISourceSetIsolationTest `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand
```

Expected: FAIL on the detach helper, unconditional Skiko edge, and replacing plugin assignment.

- [x] **Step 3: Remove hierarchy mutation and gate Skiko ancestry**

Delete `detachWinUiMingwFromDefaultNativeHierarchy`. In `compose/ui/ui/build.gradle`, route normal-mode actuals through dedicated source sets:

```groovy
skikoNonJvmMain {
    dependsOn(skikoMain)
}

skikoNativeMain {
    dependsOn(skikoMain)
}

nonJvmMain {
    if (!composeWinUiTargetEnabled) {
        dependsOn(skikoNonJvmMain)
    }
}

nativeMain {
    dependsOn(nonJvmMain)
    if (!composeWinUiTargetEnabled) {
        dependsOn(skikoNativeMain)
    }
}
```

Remove explicit `mingwMain -> commonMain` and every detach call. Retain only:

```groovy
winuiMingwMain {
    dependsOn(winuiMain)
}
```

Keep the ordinary `nonJvmMain -> skikoMain` edges in the other Compose modules; the selective actual split is required only in the root `compose-ui` module where WinUI owns the conflicting platform actuals.

- [x] **Step 4: Append the Compose Native compiler plugin**

Change the Native branch to:

```kotlin
is AbstractKotlinNativeCompile<*, *> -> compilerPluginClasspath.from(plugins)
```

- [x] **Step 5: Verify the realized graph and plugin files**

Use the ignored init script to fail unless `winuiMingwMain` has one compiled fragment root, reaches both `nativeMain` and `winuiMain`, `nativeMain` cannot reach `skikoMain`, and the task classpath filenames include both `kotlin-compose-compiler-plugin-embeddable` and `winrt-compiler-plugin`.

Run `:compose:ui:ui:tasks` with `-PcomposeWinUi.enableMingwTarget=true` and the init script. Then run the focused JVM test again. Expected: PASS.

- [x] **Step 6: Commit the structural fix**

Commit tracked graph, test, and compiler-plugin changes with `git.exe` using `build: isolate winui native source sets`.

### Task 3: Move projected InputPane acquisition into shared WinUI code

**Files:**
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIInputPane.winui.kt`
- Delete: `compose/ui/ui/src/winuiJvmMain/kotlin/androidx/compose/ui/platform/WinUIInputPane.winuiJvm.kt`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUISourceSetIsolationTest.kt`

**Interfaces:**
- Produces: shared `acquireWinUIInputPane(window: Window): InputPane?` using `WindowNative.getWindowHandle` and `InputPaneInterop.getForWindow`.
- Removes: target-specific `expect`/`actual` pair for projected APIs available to JVM and Native.

- [x] **Step 1: Change the isolation test to require shared acquisition and verify RED**

Require the generated interop calls in the `winuiMain` file and require the JVM acquisition file to be absent. Run the focused test; expected FAIL.

- [x] **Step 2: Implement shared projected acquisition**

Import `RawAddress`, `InputPaneInterop`, and `WindowNative` in `WinUIInputPane.winui.kt`, replace the `expect` declaration with the existing guarded acquisition body, and delete the JVM actual file.

- [x] **Step 3: Compile both targets and commit**

Run `compileKotlinWinuiJvm`, then `compileKotlinWinuiMingw`. The MinGW compile may now stop only on the three target-native actual groups from Task 4; it must have zero `overrides nothing` and zero duplicate-actual diagnostics. Commit with `git.exe` using `refactor: share WinUI InputPane projection`.

### Task 4: Implement the WinUI MinGW native actuals

**Files:**
- Create: `compose/ui/ui/src/winuiMingwMain/kotlin/androidx/compose/ui/platform/WinUIPlatformProperties.winuiMingw.kt`
- Create: `compose/ui/ui/src/winuiMingwMain/kotlin/androidx/compose/ui/window/WinUIWindowNative.winuiMingw.kt`
- Create: `compose/ui/ui/src/winuiMingwMain/kotlin/androidx/compose/ui/window/WindowCaptureProtection.winuiMingw.kt`

**Interfaces:**
- Implements: `winUISystemBooleanProperty(name: String): Boolean`.
- Implements: `winUIDebugLog(tag: String, message: String)`.
- Implements: `setWindowTransparentBackdrop`, `setWindowTransparentBackdropDirect`, and `setWindowCaptureProtection`.
- Consumes: shared `winuiWindowHwnd(window): Long` and Kotlin/Native `platform.windows`/`platform.posix` APIs.

- [x] **Step 1: Run MinGW compilation and preserve the exact RED diagnostics**

Run `compileKotlinWinuiMingw`, tee output to `.agent_tmp/compile-winui-mingw-isolated-red.log`, and verify the remaining missing actuals are limited to these files/signatures.

- [x] **Step 2: Implement process property and logging behavior**

Read the named value from the Native process environment, parse only strict `true`/`false`, print `[compose-winui:<tag>] <message>`, and append to the configured debug log path when present. Use Native file APIs directly; do not launch a shell.

- [x] **Step 3: Implement DWM transparency and corner preference**

Use `winuiWindowHwnd`, `CreateRectRgn`, `DwmEnableBlurBehindWindow`, `DwmSetWindowAttribute(DWMWA_WINDOW_CORNER_PREFERENCE, DWMWCP_DONOTROUND)`, `DeleteObject`, and `memScoped`. Return `false` for a null HWND and return Win32/DWM success values without swallowing resource cleanup.

- [x] **Step 4: Implement capture protection**

Call `SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE)` when protected and `SetWindowDisplayAffinity(hwnd, WDA_NONE)` otherwise; return the native Boolean result.

- [x] **Step 5: Compile MinGW to GREEN and commit**

Run `compileKotlinWinuiMingw` again and save output under `.agent_tmp/compile-winui-mingw-isolated-green.log`. Expected: PASS with both compiler plugins and no source stubs. Commit with `git.exe` using `feat: implement WinUI MinGW native hooks`.

### Task 5: Validate redirection metadata and update ownership documentation

**Files:**
- Modify: `kotlin-winrt-issues.md`
- Modify: `compose/ui/compose-winui-plan.md`
- Modify if integration assertions require it: `buildSrc/public/src/test/kotlin/org/jetbrains/androidx/build/ArtifactRedirectionTest.kt`

**Interfaces:**
- Produces: final `KWINRT-061` record closed as a Compose fragment-graph misdiagnosis.
- Produces: current compose-winui plan state for MinGW, CMP fallback ownership, and additive compiler plugins.

- [x] **Step 1: Generate representative publication metadata**

Generate module metadata for `:compose:ui:ui` in both WinUI modes. Inspect Android, configured iOS/macOS/JS/Wasm variants, root `metadataApiElements`, `winuiJvm`, and `winuiMingw` usages. Assert Android points to AndroidX, non-WinUI variants and dependency-only root metadata point to `org.jetbrains.compose.ui:ui:1.10.0`, and WinUI targets retain local JAR/KLIB publications and versions without compiling redirected Web/Apple metadata.

- [x] **Step 2: Verify normal mode remains unchanged**

Configure the same project without either WinUI property and use the graph diagnostic to assert `nonJvmMain -> skikoNonJvmMain -> skikoMain` and `nativeMain -> skikoNativeMain -> skikoMain`. Run a representative normal configuration task; expected: PASS and no CMP fallback owners are injected.

- [x] **Step 3: Close KWINRT-061 accurately**

Replace the upstream ownership diagnosis with the confirmed disconnected-fragment evidence: ordinary same-file overrides failed with two roots; one root reduced 794 override errors to zero; reconnecting Native kept override errors at zero and exposed 51 duplicate actual declarations; selective hierarchy isolation fixed the Compose graph. State that refreshed kotlin-winrt and Skiko MinGW artifacts compile and remain consumed normally.

- [x] **Step 4: Update the compose-winui plan and commit**

Mark the MinGW target/source set/backend items complete only after the compile is green, and record CMP redirection plus compiler-plugin composition. Commit with `git.exe` using `docs: close compose winui mingw isolation issue`.

### Task 6: Run final WinUI JVM and MinGW validation

**Files:**
- No production files expected; store all logs and screenshots under `.agent_tmp/`.

**Interfaces:**
- Verifies every success criterion from the approved design.

- [ ] **Step 1: Set the validated environment**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:KONAN_DATA_DIR = "$env:USERPROFILE\.konan"
$env:HTTP_PROXY = $null
$env:HTTPS_PROXY = $null
```

- [ ] **Step 2: Validate the MinGW target**

Run:

```powershell
.\gradlew.bat :compose:ui:ui:compileKotlinWinuiMingw `
  '-PcomposeWinUi.enableMingwTarget=true' `
  '-PcomposeWinUi.cmpVersion=1.10.0' `
  '-Dhttp.proxyHost=' '-Dhttp.proxyPort=0' `
  '-Dhttps.proxyHost=' '-Dhttps.proxyPort=0' `
  -Djava.net.useSystemProxies=false `
  --no-configuration-cache --no-configure-on-demand
```

Expected: PASS.

- [ ] **Step 3: Validate the established JVM target**

Run:

```powershell
.\gradlew.bat `
  :compose:ui:ui:compileKotlinWinuiJvm `
  :compose:ui:ui:winuiJvmTest `
  '-PcomposeWinUi.enableJvmTarget=true' `
  '-PcomposeWinUi.cmpVersion=1.10.0' `
  '-Dhttp.proxyHost=' '-Dhttp.proxyPort=0' `
  '-Dhttps.proxyHost=' '-Dhttps.proxyPort=0' `
  -Djava.net.useSystemProxies=false `
  --no-configuration-cache --no-configure-on-demand
```

Expected: PASS.

- [ ] **Step 4: Run the repository-local WinUI sample**

Run `:compose:ui:ui:winui-samples:runWinUISkikoSample` with the same JVM property and flags. Expected: the application launches, renders the established Skiko/Compose smoke path, and exits through its normal completion marker.

- [ ] **Step 5: Review the final diff and commit any verification-only tracked updates**

Use `git.exe status --short`, `git.exe diff --check`, and focused `git.exe diff` review. Confirm no untracked local notes or `.agent_tmp` artifacts are staged. If documentation/test expectation adjustments were required by final evidence, commit them with a narrow message; otherwise leave the verified implementation commits unchanged.
