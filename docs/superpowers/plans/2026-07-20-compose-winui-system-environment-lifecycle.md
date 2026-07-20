# Compose WinUI System Environment and Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a WinUI Compose root observe effective theme, flow direction, Windows text scale, reduced-motion state, and loaded/visible/activated lifecycle state.

**Architecture:** `WinUIComposeView` owns two disposable adapters. A system-environment adapter reads XAML plus `UISettings`, publishes immutable snapshots, and updates `LocalSystemTheme`, `WinUIOwner`, and a mutable `MotionDurationScale`; a lifecycle adapter reduces loaded/visible/active/disposed inputs to one `Lifecycle.State`. Platform event registration is isolated from pure reducers so the JVM tests do not require a live Windows App SDK activation context.

**Tech Stack:** Kotlin Multiplatform, Compose Runtime/UI, WinUI 3 XAML, Windows Runtime `UISettings`, kotlin-winrt projections, AndroidX Lifecycle, Gradle JVM tests.

## Global Constraints

- Keep compose-winui a standalone WinUI platform target; do not use Desktop, AWT, Swing, or Skiko Desktop APIs.
- Put shared implementation in `winuiMain`; do not add or validate WinUI Mingw work in this increment.
- Reuse the existing explicit `Windows.UI.ViewManagement.UISettings` and `Microsoft.UI.Xaml.FrameworkElement` projections; do not broaden full-projection generation.
- Do not touch or commit the current unrelated Gradle/source-set/Mingw dirty changes.
- Preserve `Density.density` as the XAML rasterization scale and change only `Density.fontScale` from `UISettings.textScaleFactor`.
- Marshal native change callbacks through the root `DispatcherQueue` before mutating Compose state.
- Remove event registrations before releasing the corresponding WinRT wrappers.
- Use test-first RED/GREEN cycles and `git.exe` for every focused commit.
- Use Java 25 and `--no-configuration-cache` for WinUI validation.
- Keep generated diagnostics and crash artifacts under `.agent_tmp/`.

## Baseline Note

The 2026-07-20 dirty worktree compiles `compileKotlinWinuiJvm`, but its full
`winuiJvmTest` baseline currently has 42 failures. All 42 share this pre-existing
stack root:

```text
WinUIScheduler.postDelayed
DispatcherQueue.getForCurrentThread
WinRTIllegalStateException: class not registered (0x80040154)
```

That failure comes from the uncommitted move of JVM `postDelayed` into shared
`winuiMain` for provisional Mingw work. This increment must not revert or commit
that work. Focused pure/source tests remain authoritative during implementation;
the final increment validation must run after the JVM scheduler baseline is made
test-safe or in a clean JVM-only checkout.

---

### Task 1: Pure System Environment Model and Observer

**Files:**
- Create: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIEnvironment.winui.kt`
- Create: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIEnvironmentTest.kt`

**Interfaces:**
- Produces: `WinUIEnvironment(systemTheme, layoutDirection, fontScale, animationsEnabled)`.
- Produces: `WinUIEnvironmentSource.snapshot(): WinUIEnvironment` and `setChangeListener((() -> Unit)?)`.
- Produces: `WinUIEnvironmentObserver(source, dispatch, onChanged)`.
- Produces: `WinUIMotionDurationScale.updateAnimationsEnabled(Boolean)`.

- [ ] **Step 1: Write the failing observer and motion-scale tests**

```kotlin
class WinUIEnvironmentTest {
    @Test
    fun observerPublishesInitialAndDispatchedUpdates() {
        val source = FakeEnvironmentSource(
            WinUIEnvironment(
                systemTheme = SystemTheme.Light,
                layoutDirection = LayoutDirection.Ltr,
                fontScale = 1f,
                animationsEnabled = true,
            )
        )
        val pending = ArrayDeque<() -> Unit>()
        val observed = mutableListOf<WinUIEnvironment>()
        val observer = WinUIEnvironmentObserver(
            source = source,
            dispatch = { block -> pending.addLast(block); true },
            onChanged = observed::add,
        )

        assertEquals(listOf(source.snapshot()), observed)

        source.current = source.current.copy(
            systemTheme = SystemTheme.Dark,
            layoutDirection = LayoutDirection.Rtl,
            fontScale = 1.5f,
            animationsEnabled = false,
        )
        source.emitChange()
        assertEquals(1, observed.size)

        pending.removeFirst().invoke()
        assertEquals(source.snapshot(), observed.last())

        observer.close()
    }

    @Test
    fun observerIgnoresPendingCallbacksAfterCloseAndClosesSourceOnce() {
        val source = FakeEnvironmentSource(WinUIEnvironment())
        val pending = ArrayDeque<() -> Unit>()
        var updateCount = 0
        val observer = WinUIEnvironmentObserver(
            source = source,
            dispatch = { block -> pending.addLast(block); true },
            onChanged = { updateCount += 1 },
        )

        source.emitChange()
        observer.close()
        observer.close()
        pending.removeFirst().invoke()

        assertEquals(1, updateCount)
        assertEquals(1, source.closeCount)
        assertNull(source.listener)
    }

    @Test
    fun motionDurationScaleTracksAnimationsEnabled() {
        val scale = WinUIMotionDurationScale()
        assertEquals(1f, scale.scaleFactor)

        scale.updateAnimationsEnabled(false)
        assertEquals(0f, scale.scaleFactor)

        scale.updateAnimationsEnabled(true)
        assertEquals(1f, scale.scaleFactor)
    }
}

private class FakeEnvironmentSource(
    initial: WinUIEnvironment,
) : WinUIEnvironmentSource {
    var current = initial
    var listener: (() -> Unit)? = null
    var closeCount = 0

    override fun snapshot(): WinUIEnvironment = current

    override fun setChangeListener(listener: (() -> Unit)?) {
        this.listener = listener
    }

    fun emitChange() = listener?.invoke() ?: Unit

    override fun close() {
        closeCount += 1
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot'
$winuiJvmProperty='-PcomposeWinUi.enableJvmTarget=true'
.\gradlew.bat $winuiJvmProperty :compose:ui:ui:winuiJvmTest `
  --tests "androidx.compose.ui.platform.WinUIEnvironmentTest" `
  --no-configuration-cache --no-configure-on-demand --console=plain
```

Expected: compilation fails because `WinUIEnvironment`,
`WinUIEnvironmentObserver`, and `WinUIMotionDurationScale` do not exist.

- [ ] **Step 3: Implement the pure model, observer, and motion scale**

```kotlin
internal data class WinUIEnvironment(
    val systemTheme: SystemTheme = SystemTheme.Unknown,
    val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    val fontScale: Float = 1f,
    val animationsEnabled: Boolean = true,
)

internal interface WinUIEnvironmentSource : AutoCloseable {
    fun snapshot(): WinUIEnvironment
    fun setChangeListener(listener: (() -> Unit)?)
}

internal class WinUIEnvironmentObserver(
    private val source: WinUIEnvironmentSource,
    private val dispatch: ((() -> Unit) -> Boolean),
    private val onChanged: (WinUIEnvironment) -> Unit,
) : AutoCloseable {
    private var isClosed = false

    init {
        source.setChangeListener(::handleSourceChange)
        onChanged(source.snapshot())
    }

    private fun handleSourceChange() {
        if (isClosed) return
        val next = source.snapshot()
        dispatch {
            if (!isClosed) onChanged(next)
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        source.setChangeListener(null)
        source.close()
    }
}

internal class WinUIMotionDurationScale : MotionDurationScale {
    override var scaleFactor: Float by mutableStateOf(1f)
        private set

    fun updateAnimationsEnabled(animationsEnabled: Boolean) {
        scaleFactor = if (animationsEnabled) 1f else 0f
    }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2.

Expected: all `WinUIEnvironmentTest` tests pass.

- [ ] **Step 5: Commit the pure environment layer**

```powershell
git.exe add -- `
  compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIEnvironment.winui.kt `
  compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIEnvironmentTest.kt
git.exe commit -m "feat: model WinUI system environment state"
```

### Task 2: WinUI/XAML Environment Source

**Files:**
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIEnvironment.winui.kt`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIEnvironmentTest.kt`

**Interfaces:**
- Produces: `WinUIXamlEnvironmentSource(root: FrameworkElement)`.
- Produces: `normalizeWinUITextScaleFactor(Double?): Float`.
- Produces: `ElementTheme.toComposeSystemTheme()` and `FlowDirection.toComposeLayoutDirection()`.

- [ ] **Step 1: Write failing mapping and event-wiring tests**

```kotlin
@Test
fun xamlValuesMapToComposeEnvironmentValues() {
    assertEquals(SystemTheme.Dark, ElementTheme.Dark.toComposeSystemTheme())
    assertEquals(SystemTheme.Light, ElementTheme.Light.toComposeSystemTheme())
    assertEquals(SystemTheme.Unknown, ElementTheme.Default.toComposeSystemTheme())
    assertEquals(LayoutDirection.Ltr, FlowDirection.LeftToRight.toComposeLayoutDirection())
    assertEquals(LayoutDirection.Rtl, FlowDirection.RightToLeft.toComposeLayoutDirection())
}

@Test
fun textScaleUsesOnlyFinitePositiveValues() {
    assertEquals(1f, normalizeWinUITextScaleFactor(null))
    assertEquals(1f, normalizeWinUITextScaleFactor(Double.NaN))
    assertEquals(1f, normalizeWinUITextScaleFactor(0.0))
    assertEquals(1.25f, normalizeWinUITextScaleFactor(1.25))
}

@Test
fun productionSourceRegistersAndRemovesEveryEnvironmentSignal() {
    val source = findWinUIUiModuleRoot()
        .resolve("src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIEnvironment.winui.kt")
        .readText()

    listOf(
        "root.actualThemeChanged.add",
        "root.loaded.add",
        "root.registerPropertyChangedCallback(FrameworkElement.flowDirectionProperty",
        "uiSettings.textScaleFactorChanged.add",
        "uiSettings.animationsEnabledChanged.add",
        "root.actualThemeChanged.remove",
        "root.loaded.remove",
        "root.unregisterPropertyChangedCallback(FrameworkElement.flowDirectionProperty",
        "uiSettings.textScaleFactorChanged.remove",
        "uiSettings.animationsEnabledChanged.remove",
    ).forEach { required ->
        assertTrue(source.contains(required), "Missing environment wiring: $required")
    }
}

internal fun findWinUIUiModuleRoot(): Path {
    val start = Paths.get("").toAbsolutePath()
    generateSequence(start) { it.parent }.forEach { candidate ->
        val direct = candidate.resolve("src/winuiMain/kotlin")
        if (direct.exists() && candidate.name == "ui") return candidate

        val fromRepoRoot = candidate.resolve("compose/ui/ui/src/winuiMain/kotlin")
        if (fromRepoRoot.exists()) return candidate.resolve("compose/ui/ui")
    }
    error("Could not find compose/ui/ui module root from $start.")
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run the Task 1 focused test command.

Expected: mapping symbols and production event wiring are missing.

- [ ] **Step 3: Implement the production source with narrow fallbacks**

Add `WinUIXamlEnvironmentSource` that:

```kotlin
internal class WinUIXamlEnvironmentSource(
    private val root: FrameworkElement,
    private val uiSettings: UISettings? = runCatching { UISettings() }.getOrNull(),
) : WinUIEnvironmentSource {
    private var listener: (() -> Unit)? = null
    private var isClosed = false

    private val themeHandler: TypedEventHandler<FrameworkElement, Any?> = { _, _ -> notifyChanged() }
    private val loadedHandler: RoutedEventHandler = { _, _ -> notifyChanged() }
    private val flowDirectionHandler = DependencyPropertyChangedCallback { _, _ -> notifyChanged() }
    private val textScaleHandler: TypedEventHandler<UISettings, Any?> = { _, _ -> notifyChanged() }
    private val animationsHandler:
        TypedEventHandler<UISettings, UISettingsAnimationsEnabledChangedEventArgs> =
        { _, _ -> notifyChanged() }

    private val themeToken = runCatching { root.actualThemeChanged.add(themeHandler) }.getOrNull()
    private val loadedToken = runCatching { root.loaded.add(loadedHandler) }.getOrNull()
    private val flowDirectionToken = runCatching {
        root.registerPropertyChangedCallback(
            FrameworkElement.flowDirectionProperty,
            flowDirectionHandler,
        )
    }.getOrNull()
    private val textScaleToken = uiSettings?.let { settings ->
        runCatching { settings.textScaleFactorChanged.add(textScaleHandler) }.getOrNull()
    }
    private val animationsToken = uiSettings?.let { settings ->
        runCatching { settings.animationsEnabledChanged.add(animationsHandler) }.getOrNull()
    }

    override fun snapshot(): WinUIEnvironment = WinUIEnvironment(
        systemTheme = runCatching { root.actualTheme.toComposeSystemTheme() }
            .getOrDefault(SystemTheme.Unknown),
        layoutDirection = runCatching { root.flowDirection.toComposeLayoutDirection() }
            .getOrDefault(LayoutDirection.Ltr),
        fontScale = normalizeWinUITextScaleFactor(
            runCatching { uiSettings?.textScaleFactor }.getOrNull()
        ),
        animationsEnabled = runCatching { uiSettings?.animationsEnabled }
            .getOrNull() ?: true,
    )

    override fun setChangeListener(listener: (() -> Unit)?) {
        this.listener = listener
    }

    private fun notifyChanged() {
        if (!isClosed) listener?.invoke()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        listener = null
        themeToken?.let { runCatching { root.actualThemeChanged.remove(it) } }
        loadedToken?.let { runCatching { root.loaded.remove(it) } }
        flowDirectionToken?.let {
            runCatching {
                root.unregisterPropertyChangedCallback(FrameworkElement.flowDirectionProperty, it)
            }
        }
        if (uiSettings != null) {
            textScaleToken?.let { runCatching { uiSettings.textScaleFactorChanged.remove(it) } }
            animationsToken?.let { runCatching { uiSettings.animationsEnabledChanged.remove(it) } }
        }
    }
}
```

Implement the mapping helpers with exhaustive WinUI value handling and a `1f`
fallback for invalid text scale.

- [ ] **Step 4: Run focused tests and compile WinUI JVM**

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot'
$winuiJvmProperty='-PcomposeWinUi.enableJvmTarget=true'
.\gradlew.bat $winuiJvmProperty `
  :compose:ui:ui:winuiJvmTest `
  :compose:ui:ui:compileKotlinWinuiJvm `
  --tests "androidx.compose.ui.platform.WinUIEnvironmentTest" `
  --no-configuration-cache --no-configure-on-demand --console=plain
```

Expected: focused tests and WinUI compilation pass.

- [ ] **Step 5: Commit the WinRT/XAML source adapter**

```powershell
git.exe add -- `
  compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIEnvironment.winui.kt `
  compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIEnvironmentTest.kt
git.exe commit -m "feat: observe WinUI system environment"
```

### Task 3: Lifecycle Reducer and XAML Binding

**Files:**
- Create: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIViewLifecycle.winui.kt`
- Create: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIViewLifecycleTest.kt`

**Interfaces:**
- Produces: `calculateWinUIViewLifecycleState(isLoaded, isVisible, isActive, isDisposed)`.
- Produces: `WinUIViewLifecycleController.setLoaded/setVisible/setActive/dispose`.
- Produces: `WinUIRootLifecycleBinding(root, controller)`.

- [ ] **Step 1: Write the failing lifecycle transition tests**

```kotlin
@Test
fun lifecycleStateMatchesLoadedVisibleActiveTable() {
    assertEquals(
        Lifecycle.State.CREATED,
        calculateWinUIViewLifecycleState(false, true, true, false),
    )
    assertEquals(
        Lifecycle.State.CREATED,
        calculateWinUIViewLifecycleState(true, false, true, false),
    )
    assertEquals(
        Lifecycle.State.STARTED,
        calculateWinUIViewLifecycleState(true, true, false, false),
    )
    assertEquals(
        Lifecycle.State.RESUMED,
        calculateWinUIViewLifecycleState(true, true, true, false),
    )
    assertEquals(
        Lifecycle.State.DESTROYED,
        calculateWinUIViewLifecycleState(true, true, true, true),
    )
}

@Test
fun controllerPublishesOnlyDistinctTransitionsAndDestroyIsTerminal() {
    val states = mutableListOf<Lifecycle.State>()
    val controller = WinUIViewLifecycleController(states::add)

    controller.setVisible(true)
    controller.setLoaded(true)
    controller.setActive(true)
    controller.setActive(true)
    controller.setVisible(false)
    controller.dispose()
    controller.setVisible(true)

    assertEquals(
        listOf(
            Lifecycle.State.CREATED,
            Lifecycle.State.STARTED,
            Lifecycle.State.RESUMED,
            Lifecycle.State.CREATED,
            Lifecycle.State.DESTROYED,
        ),
        states,
    )
}
```

- [ ] **Step 2: Run the focused lifecycle test and verify RED**

Run `winuiJvmTest --tests "androidx.compose.ui.platform.WinUIViewLifecycleTest"`.

Expected: lifecycle reducer/controller symbols do not exist.

- [ ] **Step 3: Implement the reducer, controller, and root binding**

```kotlin
internal fun calculateWinUIViewLifecycleState(
    isLoaded: Boolean,
    isVisible: Boolean,
    isActive: Boolean,
    isDisposed: Boolean,
): Lifecycle.State = when {
    isDisposed -> Lifecycle.State.DESTROYED
    !isLoaded || !isVisible -> Lifecycle.State.CREATED
    !isActive -> Lifecycle.State.STARTED
    else -> Lifecycle.State.RESUMED
}

internal class WinUIViewLifecycleController(
    private val onStateChanged: (Lifecycle.State) -> Unit,
) {
    private var isLoaded = false
    private var isVisible = true
    private var isActive = false
    private var isDisposed = false
    private var lastState: Lifecycle.State? = null

    init { publishState() }

    fun setLoaded(value: Boolean) { if (!isDisposed) { isLoaded = value; publishState() } }
    fun setVisible(value: Boolean) { if (!isDisposed) { isVisible = value; publishState() } }
    fun setActive(value: Boolean) { if (!isDisposed) { isActive = value; publishState() } }
    fun dispose() { if (!isDisposed) { isDisposed = true; publishState() } }

    private fun publishState() {
        val next = calculateWinUIViewLifecycleState(
            isLoaded,
            isVisible,
            isActive,
            isDisposed,
        )
        if (next != lastState) {
            lastState = next
            onStateChanged(next)
        }
    }
}
```

`WinUIRootLifecycleBinding` must initialize from `root.isLoaded` and
`root.visibility`, subscribe to `loaded`, `unloaded`, and
`UIElement.visibilityProperty`, and remove all three registrations before
calling `controller.dispose()`.

- [ ] **Step 4: Run focused lifecycle tests and compile**

Expected: lifecycle tests and `compileKotlinWinuiJvm` pass.

- [ ] **Step 5: Commit the lifecycle layer**

```powershell
git.exe add -- `
  compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIViewLifecycle.winui.kt `
  compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIViewLifecycleTest.kt
git.exe commit -m "feat: model WinUI view lifecycle"
```

### Task 4: Owner and Compose Root Integration

**Files:**
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/node/WinUIOwner.winui.kt`
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIComposeView.winui.kt`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/node/WinUIOwnerTest.kt`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIComposeViewDisposalTest.kt`
- Create: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIEnvironmentIntegrationTest.kt`

**Interfaces:**
- Produces: `WinUIOwner.updateLayoutDirection(LayoutDirection)`.
- Consumes: `WinUIEnvironmentObserver`, `WinUIMotionDurationScale`, and `WinUIViewLifecycleController`.
- Provides: dynamic `LocalSystemTheme`, owner density/layout direction, lifecycle, and recomposer motion scale.

- [ ] **Step 1: Write failing source-integration and owner-direction tests**

```kotlin
@Test
fun composeRootProvidesEnvironmentAndMotionScale() {
    val source = winUIComposeViewSource()
    assertTrue(source.contains("LocalSystemTheme provides systemEnvironment.systemTheme"))
    assertTrue(source.contains("owner.updateLayoutDirection(environment.layoutDirection)"))
    assertTrue(source.contains("Density(owner.density.density, environment.fontScale)"))
    assertTrue(source.contains("motionDurationScale.updateAnimationsEnabled"))
    assertTrue(source.contains("FrameRecomposer(dispatcher + motionDurationScale"))
}

@Test
fun composeRootDrivesLifecycleFromBindingFocusAndDispose() {
    val source = winUIComposeViewSource()
    assertTrue(source.contains("WinUIViewLifecycleController("))
    assertTrue(source.contains("WinUIRootLifecycleBinding("))
    assertTrue(source.contains("lifecycleController.setActive(isWindowFocused)"))
    assertTrue(source.contains("lifecycleBinding.close()"))
}

private fun winUIComposeViewSource(): String =
    findWinUIUiModuleRoot()
        .resolve("src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIComposeView.winui.kt")
        .readText()

@Test
fun ownerLayoutDirectionIsMutableAndSchedulesLayout() {
    val source = winUIOwnerSource()
    assertTrue(
        source.contains(
            "override var layoutDirection: LayoutDirection by mutableStateOf(LayoutDirection.Ltr)"
        )
    )
    assertTrue(source.contains("fun updateLayoutDirection(layoutDirection: LayoutDirection)"))
    assertTrue(source.contains("root.layoutDirection = layoutDirection"))
    assertTrue(source.contains("onMeasureAndLayoutRequested()"))
}
```

Update the disposal test to require `environmentObserver.close()` and
`lifecycleBinding.close()` before `owner.dispose()`, replacing the old direct
`setLifecycleState(DESTROYED)` assertion.

- [ ] **Step 2: Run the focused integration tests and verify RED**

Expected: all new source assertions fail because the root is still constant
LTR/theme/resumed and the recomposer lacks a motion scale.

- [ ] **Step 3: Make `WinUIOwner.layoutDirection` mutable**

```kotlin
override var layoutDirection: LayoutDirection by mutableStateOf(LayoutDirection.Ltr)
    private set

fun updateLayoutDirection(layoutDirection: LayoutDirection) {
    if (isShuttingDown || this.layoutDirection == layoutDirection) return
    this.layoutDirection = layoutDirection
    root.layoutDirection = layoutDirection
    onMeasureAndLayoutRequested()
}
```

Preserve the existing unrelated snapshot-lock edit at the bottom of
`WinUIOwner.winui.kt`; stage only this task's hunks.

- [ ] **Step 4: Integrate environment and lifecycle into `WinUIComposeView`**

Add state and adapters after `owner` initialization:

```kotlin
private var systemEnvironment by mutableStateOf(WinUIEnvironment())
private val motionDurationScale = WinUIMotionDurationScale()
private val lifecycleController = WinUIViewLifecycleController(
    architectureComponentsOwner::setLifecycleState,
)
private val lifecycleBinding = WinUIRootLifecycleBinding(
    root = rootContentControl,
    controller = lifecycleController,
)
private val environmentObserver = WinUIEnvironmentObserver(
    source = WinUIXamlEnvironmentSource(rootContentControl),
    dispatch = { block -> dispatchQueue.dispatch(block) },
    onChanged = ::applySystemEnvironment,
)

private fun applySystemEnvironment(environment: WinUIEnvironment) {
    if (isDisposed) return
    systemEnvironment = environment
    owner.updateLayoutDirection(environment.layoutDirection)
    owner.updateDensity(Density(owner.density.density, environment.fontScale))
    motionDurationScale.updateAnimationsEnabled(environment.animationsEnabled)
    scheduleRootContentSync()
    requestRender()
}
```

Provide `LocalSystemTheme`, call `lifecycleController.setActive` from
`setWindowFocused`, preserve `fontScale` in `updateDensityFromXamlRoot`, create
the root recomposer with `dispatcher + motionDurationScale`, and close the two
adapters in the aggregated disposal sequence before disposing the owner.

- [ ] **Step 5: Run focused integration tests and compile**

Run:

```powershell
.\gradlew.bat $winuiJvmProperty `
  :compose:ui:ui:winuiJvmTest `
  :compose:ui:ui:compileKotlinWinuiJvm `
  --tests "androidx.compose.ui.platform.WinUIEnvironmentTest" `
  --tests "androidx.compose.ui.platform.WinUIViewLifecycleTest" `
  --tests "androidx.compose.ui.platform.WinUIEnvironmentIntegrationTest" `
  --tests "androidx.compose.ui.platform.WinUIComposeViewDisposalTest" `
  --no-configuration-cache --no-configure-on-demand --console=plain
```

Expected: focused tests pass and WinUI JVM production sources compile.

- [ ] **Step 6: Commit root integration without unrelated hunks**

Use `git.exe diff` plus a cached-diff review. Stage only the new files and the
specific owner/compose-view hunks; do not stage the pre-existing snapshot-lock,
Gradle, or Mingw changes.

```powershell
git.exe commit -m "feat: apply WinUI environment and lifecycle to Compose"
```

### Task 5: Repository-Local Window/Lifecycle Smoke

**Files:**
- Modify: `compose/ui/ui/winui-samples/src/main/kotlin/androidx/compose/ui/winui/samples/WinUIViewSample.kt`

**Interfaces:**
- Consumes: `LocalSystemTheme`, `LocalDensity`, `LocalLayoutDirection`, and `LocalLifecycleOwner`.
- Validates: initial effective values plus live requested-theme/flow-direction and activation transitions.

- [ ] **Step 1: Replace constant assumptions with captured environment state**

Update `ValidateWinUICompositionLocals` so it checks finite positive density
and font scale, accepts both layout directions, accepts Light/Dark/Unknown while
the root is attaching, and requires lifecycle at least `CREATED` instead of
requiring it to be permanently `RESUMED` during first composition.

Add a window-mode probe that records the observed values:

```kotlin
private object WinUIEnvironmentSmokeState {
    var theme: SystemTheme = SystemTheme.Unknown
    var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    var fontScale: Float = 1f
    var lifecycleState: Lifecycle.State = Lifecycle.State.INITIALIZED
}

@Composable
private fun CaptureWinUIEnvironment() {
    WinUIEnvironmentSmokeState.theme = LocalSystemTheme.current
    WinUIEnvironmentSmokeState.layoutDirection = LocalLayoutDirection.current
    WinUIEnvironmentSmokeState.fontScale = LocalDensity.current.fontScale
    WinUIEnvironmentSmokeState.lifecycleState =
        LocalLifecycleOwner.current.lifecycle.currentState
}
```

- [ ] **Step 2: Add a real window lifecycle/environment smoke sequence**

In window sample mode, after the window root is loaded:

1. Assert the captured lifecycle reaches `RESUMED` while activated.
2. Set the root `requestedTheme` to the opposite Light/Dark value and wait for
   `LocalSystemTheme` to match.
3. Set root `flowDirection = FlowDirection.RightToLeft` and wait for
   `LocalLayoutDirection == LayoutDirection.Rtl`.
4. Restore the original theme and flow direction.
5. Preserve the existing focus/window-close smoke behavior.

The wait must use the sample's existing condition-based smoke helper and must
not use a fixed sleep.

- [ ] **Step 3: Compile and run the focused sample**

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot'
$winuiJvmProperty='-PcomposeWinUi.enableJvmTarget=true'
.\gradlew.bat $winuiJvmProperty `
  :compose:ui:ui:winui-samples:runWinUIWindowSample `
  --no-configuration-cache --no-configure-on-demand --console=plain
```

Expected log markers include the existing window/lifecycle smoke marker plus a
new `compose-winui-sample: system environment and lifecycle` marker.

- [ ] **Step 4: Commit the smoke coverage**

Stage only the sample hunks added by this task, preserving the existing user
changes elsewhere in the same file.

```powershell
git.exe commit -m "test: cover WinUI environment and lifecycle smoke"
```

### Task 6: Increment Validation and Documentation

**Files:**
- Modify: `compose/ui/compose-winui-plan.md`
- Modify only if a kotlin-winrt workaround was actually required: `kotlin-winrt-issues.md`

**Interfaces:**
- Produces: verified Increment 1 with no new Skiko ABI and no Mingw work.

- [ ] **Step 1: Run focused tests**

Run all four new/updated focused test classes from Task 4.

Expected: PASS.

- [ ] **Step 2: Run required WinUI JVM compile and tests**

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot'
$winuiJvmProperty='-PcomposeWinUi.enableJvmTarget=true'
$gradleJvmArgs='-Dorg.gradle.jvmargs=-Xmx4g -XX:+HeapDumpOnOutOfMemoryError -XX:+UseParallelGC -Dkotlin.daemon.jvm.options=-XX:MaxMetaspaceSize=768m,-Xmx2g -Dfile.encoding=UTF-8'
.\gradlew.bat $winuiJvmProperty $gradleJvmArgs `
  :compose:ui:ui:compileKotlinWinuiJvm `
  :compose:ui:ui:winuiJvmTest `
  --no-daemon --max-workers=4 --no-parallel `
  --no-configuration-cache --no-configure-on-demand --console=plain
```

Expected: compile passes. Full tests must pass after the pre-existing shared
`postDelayed` baseline conflict is resolved or isolated; do not misreport the
known 42 failures as caused by this increment.

- [ ] **Step 3: Run focused and aggregate repository samples**

```powershell
.\gradlew.bat $winuiJvmProperty `
  :compose:ui:ui:winui-samples:runWinUIWindowSample `
  :compose:ui:ui:winui-samples:runWinUISkikoSample `
  --no-configuration-cache --no-configure-on-demand --console=plain
```

Expected: both tasks pass; no internal exception, timeout, or render failure is
reported.

- [ ] **Step 4: Record hardware/manual validation honestly**

Record Windows text-scale, reduced-motion, explicit RTL, and theme-toggle checks
as manual PASS or SKIPPED. Do not infer them from compile output.

- [ ] **Step 5: Update the compose-winui checklist**

Mark only the completed system environment and lifecycle items in
`compose/ui/compose-winui-plan.md`. Add a `KWINRT-###` entry only if production
code contains a real kotlin-winrt workaround; ordinary `runCatching` around
optional event registration is not an upstream workaround.

- [ ] **Step 6: Review and create the final increment commit**

```powershell
git.exe diff --check
git.exe diff --cached --check
git.exe status --short --untracked-files=all
git.exe commit -m "docs: record WinUI environment lifecycle support"
```

Do not include `AGENTS.md`, `.agent_tmp/`, generated output, or unrelated dirty
files.
