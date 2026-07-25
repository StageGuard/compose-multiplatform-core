# Compose WinUI Pointer Projection Lifetime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every WinRT projection created by the compose-winui pointer path so JVM and MinGW MPP sample memory plateaus after pointer-input warm-up.

**Architecture:** Consume kotlin-winrt from the repository submodule/composite build, then make `acquireInterfaceReference()` children owned by their parent `ComObjectReference`. At the Compose boundary, wrap transient event args, current points, properties, historical collections, and elements in explicit owners; transfer only the current drag-source point into a one-value slot that closes on replacement or clearing.

**Tech Stack:** Kotlin Multiplatform, kotlin-winrt COM runtime, Jetpack Compose UI, Gradle composite builds, JUnit/kotlin.test, Windows App SDK/WinUI, PowerShell process metrics and `jcmd`.

## Global Constraints

- Keep compose-winui a standalone WinUI platform target; do not introduce Desktop, AWT, Swing, or Skiko Desktop dependencies.
- Use `external/kotlin-winrt` as a Git submodule and composite build; do not publish a temporary patched runtime into the user Maven cache.
- Keep shared pointer behavior in `winuiMain`; do not fork JVM and MinGW implementations.
- Do not add a global kotlin-winrt delegate callback lease, JVM Cleaner, Kotlin/Native finalizer, hard-coded ABI slot, or process-shell fallback.
- Record the runtime defect as `KWINRT-064` and reference it from non-obvious Compose cleanup code.
- Write a failing test and observe the intended failure before each production change.
- Use JDK 25 and `--no-configuration-cache` for WinUI validation.
- Use `git.exe` for status, staging, and commits. Never commit `AGENTS.md`, `.agent_tmp/`, `.codex/`, `.dotnet/`, or unrelated existing files.
- Store generated logs, CSV files, screenshots, dumps, and temporary scripts under `.agent_tmp/`.
- Commit each independently verified implementation task promptly.

---

## File Map

- `.gitmodules`: pins the kotlin-winrt repository as `external/kotlin-winrt`.
- `settings.gradle`: prefers the local kotlin-winrt plugin build and lets composite dependency substitution replace Maven runtime modules.
- `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUISourceSetIsolationTest.kt`: guards submodule/composite wiring.
- `external/kotlin-winrt/winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/ComObjectReference.kt`: attaches derived interface references to their parent.
- `external/kotlin-winrt/winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/OwnedComObjectReferenceRegistry.kt`: owns and closes derived references with race-safe failure handling.
- `external/kotlin-winrt/winrt-runtime/src/commonTest/kotlin/io/github/composefluent/winrt/runtime/ComObjectReferenceOwnershipTest.kt`: verifies real managed-COM reference counts and idempotence.
- `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIOwnedResourceSlot.winui.kt`: holds at most one drag-owned native resource and reports ownership plus cleanup failure.
- `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIProjectionLifetime.winui.kt`: explicit transient owner and list-consumption helpers.
- `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIDragAndDropAdapter.winui.kt`: owns and clears the latest drag-source point.
- `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIPointerInputAdapter.winui.kt`: closes event args, properties, current points, historical collections, and elements.
- `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIDragAndDropAdapterTest.kt`: verifies retained-point replacement and clear behavior.
- `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIProjectionLifetimeTest.kt`: verifies transient success, transfer, and failure cleanup.
- `kotlin-winrt-issues.md`: records `KWINRT-064`, local fix commit, and validation evidence.
- `compose/ui/compose-winui-plan.md`: records the completed pointer-memory hardening step after validation.

---

### Task 1: Use The kotlin-winrt Submodule Composite

**Files:**
- Create: `.gitmodules`
- Create: `external/kotlin-winrt` (Git submodule)
- Modify: `settings.gradle:3-39`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUISourceSetIsolationTest.kt`

**Interfaces:**
- Consumes: plugin ID `io.github.compose-fluent.winrt` and Maven coordinates under `io.github.compose-fluent`.
- Produces: local composite projects for the plugin, runtime, authoring, generator, metadata, and compiler plugin.

- [ ] **Step 1: Add the failing structural test**

Add this test to `WinUISourceSetIsolationTest`:

```kotlin
@Test
fun winuiUsesKotlinWinrtCompositeSubmodule() {
    val repositoryRoot = findRepositoryRoot()
    val gitmodules = repositoryRoot.resolve(".gitmodules")
    val settings = repositoryRoot.resolve("settings.gradle").readText()

    assertTrue(gitmodules.exists(), "kotlin-winrt must be a repository submodule.")
    assertTrue(
        gitmodules.readText().contains("path = external/kotlin-winrt"),
        "The kotlin-winrt submodule must live at external/kotlin-winrt.",
    )
    assertTrue(
        settings.contains("external/kotlin-winrt/winrt-gradle-plugin"),
        "Plugin resolution must prefer the local kotlin-winrt composite.",
    )
}
```

- [ ] **Step 2: Run the test and observe the intended failure**

Run:

```powershell
$env:JAVA_HOME = (Resolve-Path '.agent_tmp\jdk25\jdk-25.0.3+9').Path
.\gradlew.bat :compose:ui:ui:winuiJvmTest `
  --tests 'androidx.compose.ui.platform.WinUISourceSetIsolationTest.winuiUsesKotlinWinrtCompositeSubmodule' `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand
```

Expected: FAIL with `kotlin-winrt must be a repository submodule.`

- [ ] **Step 3: Add the submodule**

Run:

```powershell
git.exe submodule add https://github.com/compose-fluent/kotlin-winrt.git external/kotlin-winrt
git.exe -C external/kotlin-winrt checkout 8aa74e09
git.exe -C external/kotlin-winrt rev-parse HEAD
```

Expected base revision: `8aa74e09`, which includes the generated Windows COM
interop source additions required by the repository's closed `KWINRT-063` path.

- [ ] **Step 4: Prefer the local plugin composite**

Update the start of `settings.gradle` so the Maven fallback is used only when
the submodule is absent:

```groovy
pluginManagement {
    def localKotlinWinRtPluginBuild = file("external/kotlin-winrt/winrt-gradle-plugin")
    if (localKotlinWinRtPluginBuild.isDirectory()) {
        includeBuild(localKotlinWinRtPluginBuild)
    }
    repositories {
        if (true /* In JetBrains Fork */) {
            mavenCentral()
            google()
            maven {
                url = "https://plugins.gradle.org/m2/"
            }
            maven {
                name = "mavenCentralSnapshots"
                url = "https://central.sonatype.com/repository/maven-snapshots/"
                mavenContent {
                    snapshotsOnly()
                }
            }
        }
    }
    resolutionStrategy {
        eachPlugin {
            if (
                requested.id.id == "io.github.compose-fluent.winrt" &&
                    !localKotlinWinRtPluginBuild.isDirectory()
            ) {
                useModule(
                    "io.github.compose-fluent:winrt-gradle-plugin:" +
                        "${requested.version ?: "0.1.0-SNAPSHOT"}"
                )
            }
        }
    }
    plugins {
        id("io.github.compose-fluent.winrt") version "0.1.0-SNAPSHOT"
    }
    includeBuild("androidx-settings-plugins")
}
```

Do not add a second top-level include of the same plugin build. The
`winrt-gradle-plugin/settings.gradle.kts` build already includes the local
runtime, authoring, generator, metadata, and compiler projects, allowing
composite substitution for their Maven coordinates.

- [ ] **Step 5: Verify composite resolution and the structural test**

```powershell
$env:JAVA_HOME = (Resolve-Path '.agent_tmp\jdk25\jdk-25.0.3+9').Path
.\gradlew.bat :compose:ui:ui:winuiJvmTest `
  --tests 'androidx.compose.ui.platform.WinUISourceSetIsolationTest.winuiUsesKotlinWinrtCompositeSubmodule' `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand
.\gradlew.bat :compose:ui:ui:dependencies `
  --configuration winuiJvmCompileClasspath `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand `
  | Select-String 'project :winrt-runtime|io.github.compose-fluent:winrt-runtime'
```

Expected: the structural test passes and dependency output selects the local
`:winrt-runtime` project rather than an unresolved Maven snapshot.

- [ ] **Step 6: Commit the composite setup**

```powershell
git.exe add -- .gitmodules external/kotlin-winrt settings.gradle `
  compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUISourceSetIsolationTest.kt
git.exe commit -m "build: use kotlin-winrt composite submodule"
```

---

### Task 2: Make Acquired Interfaces Parent-Owned

**Files:**
- Create: `external/kotlin-winrt/winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/OwnedComObjectReferenceRegistry.kt`
- Create: `external/kotlin-winrt/winrt-runtime/src/commonTest/kotlin/io/github/composefluent/winrt/runtime/ComObjectReferenceOwnershipTest.kt`
- Modify: `external/kotlin-winrt/winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/ComObjectReference.kt`
- Modify: `kotlin-winrt-issues.md`

**Interfaces:**
- Consumes: `PlatformLock`, `ComObjectReference.close()`, and `WinRTObjectDisposedException`.
- Produces: `ComObjectReference.own(reference)` and parent-owned behavior from `acquireInterfaceReference(parent, iid)`.

- [ ] **Step 1: Write the first failing real-COM lifetime test**

Create `ComObjectReferenceOwnershipTest.kt` with a managed COM host exposing two
interfaces:

```kotlin
package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComObjectReferenceOwnershipTest {
    @Test
    fun acquired_interface_closes_with_parent() {
        val primaryIid = Guid("11111111-1111-1111-1111-111111111111")
        val secondaryIid = Guid("22222222-2222-2222-2222-222222222222")
        var cleanupCount = 0
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(primaryIid, methods = emptyList()),
                WinRTInspectableInterfaceDefinition(secondaryIid, methods = emptyList()),
            ),
            defaultInterfaceId = primaryIid,
            cleanupAction = { cleanupCount++ },
        )
        val parent = host.createPrimaryReference()
    val child = acquireInterfaceReference(parent, secondaryIid)
    host.close()
    assertFalse(child.isDisposed)

        try {
            parent.close()
            assertTrue(child.isDisposed)
            assertEquals(1, cleanupCount)
        } finally {
            child.close()
            parent.close()
            host.close()
        }
    }
}
```

- [ ] **Step 2: Run the runtime test and confirm the leak is detected**

```powershell
$env:JAVA_HOME = (Resolve-Path '.agent_tmp\jdk25\jdk-25.0.3+9').Path
external\kotlin-winrt\gradlew.bat -p external\kotlin-winrt `
  :winrt-runtime:jvmTest `
  --tests 'io.github.composefluent.winrt.runtime.ComObjectReferenceOwnershipTest' `
  --no-configuration-cache
```

Expected: FAIL because `child.isDisposed` is false and the host cleanup count
remains zero after closing the parent.

- [ ] **Step 3: Implement the owned-child registry**

Create `OwnedComObjectReferenceRegistry.kt` with this shape:

```kotlin
package io.github.composefluent.winrt.runtime

internal class OwnedComObjectReferenceRegistry {
    private val lock = PlatformLock()
    private val references = mutableListOf<ComObjectReference>()
    private var closed = false

    fun <T : ComObjectReference> register(reference: T): T {
        val parentClosed = lock.withLock {
            if (closed) {
                true
            } else {
                references += reference
                false
            }
        }
        if (parentClosed) {
            val failure = WinRTObjectDisposedException("Object reference is disposed.")
            runCatching { reference.close() }
                .onFailure(failure::addSuppressed)
            throw failure
        }
        return reference
    }

    fun close() {
        val owned = lock.withLock {
            if (closed) {
                emptyList()
            } else {
                closed = true
                references.toList().also { references.clear() }
            }
        }
        var failure: Throwable? = null
        owned.forEach { reference ->
            runCatching { reference.close() }
                .onFailure { error ->
                    failure?.addSuppressed(error) ?: run { failure = error }
                }
        }
        failure?.let { throw it }
    }
}
```

Modify `ComObjectReference`:

```kotlin
private val ownedReferences = OwnedComObjectReferenceRegistry()

internal fun <T : ComObjectReference> own(reference: T): T =
    ownedReferences.register(reference)

override fun close() {
    var failure: Throwable? = null
    runCatching { ownedReferences.close() }
        .onFailure { failure = it }
    runCatching { comPtr.close() }
        .onFailure { error ->
            failure?.addSuppressed(error) ?: run { failure = error }
        }
    failure?.let { throw it }
}
```

Change `acquireInterfaceReference` so the detached QI reference is registered
before it escapes:

```kotlin
fun acquireInterfaceReference(instance: ComObjectReference, iid: Guid): IUnknownReference =
    instance.queryInterface(iid).getOrThrow().use { reference ->
        instance.own(IUnknownReference(reference.getRefPointer(), iid))
    }
```

- [ ] **Step 4: Add idempotence, multi-child, disposed-registration, and failure-order tests**

Extend `ComObjectReferenceOwnershipTest` with these focused tests and helper:

```kotlin
@Test
fun acquired_interfaces_close_with_parent_when_one_child_was_already_closed() {
    val primaryIid = Guid("11111111-1111-1111-1111-111111111111")
    val firstSecondaryIid = Guid("22222222-2222-2222-2222-222222222222")
    val secondSecondaryIid = Guid("33333333-3333-3333-3333-333333333333")
    var cleanupCount = 0
    val host = WinRTInspectableComObject(
        interfaceDefinitions = listOf(
            WinRTInspectableInterfaceDefinition(primaryIid, methods = emptyList()),
            WinRTInspectableInterfaceDefinition(firstSecondaryIid, methods = emptyList()),
            WinRTInspectableInterfaceDefinition(secondSecondaryIid, methods = emptyList()),
        ),
        defaultInterfaceId = primaryIid,
        cleanupAction = { cleanupCount++ },
    )
    val parent = host.createPrimaryReference()
    val first = acquireInterfaceReference(parent, firstSecondaryIid)
    val second = acquireInterfaceReference(parent, secondSecondaryIid)
    host.close()

    first.close()
    parent.close()

    assertTrue(first.isDisposed)
    assertTrue(second.isDisposed)
    assertEquals(1, cleanupCount)
}

@Test
fun registering_after_registry_close_closes_incoming_reference() {
    val closed = mutableListOf<String>()
    val registry = OwnedComObjectReferenceRegistry()
    val incoming = RecordingReference("incoming", closed)
    registry.close()

    assertFailsWith<WinRTObjectDisposedException> {
        registry.register(incoming)
    }

    assertEquals(listOf("incoming"), closed)
}

@Test
fun registry_closes_all_children_and_preserves_failure_order() {
    val closed = mutableListOf<String>()
    val firstFailure = IllegalStateException("first")
    val secondFailure = IllegalArgumentException("second")
    val registry = OwnedComObjectReferenceRegistry()
    registry.register(RecordingReference("first", closed, firstFailure))
    registry.register(RecordingReference("second", closed, secondFailure))
    registry.register(RecordingReference("third", closed))

    val thrown = assertFailsWith<IllegalStateException> { registry.close() }

    assertSame(firstFailure, thrown)
    assertEquals(listOf("first", "second", "third"), closed)
    assertEquals(listOf(secondFailure), thrown.suppressedExceptions)
}

private class RecordingReference(
    private val name: String,
    private val closed: MutableList<String>,
    private val failure: Throwable? = null,
) : ComObjectReference(
    pointer = PlatformAbi.nullComPtr,
    interfaceId = IID.IUnknown,
    preventReleaseOnDispose = true,
) {
    override fun close() {
        closed += name
        failure?.let { throw it }
        super.close()
    }
}
```

Add `assertFailsWith` and `assertSame` imports. The first two lifetime tests use
real `WinRTInspectableComObject` references for reference-count assertions; the
`RecordingReference` subclass uses a borrowed null pointer only to inject close
failures into the registry tests.

- [ ] **Step 5: Run JVM and MinGW runtime tests**

```powershell
external\kotlin-winrt\gradlew.bat -p external\kotlin-winrt `
  :winrt-runtime:jvmTest :winrt-runtime:mingwX64Test `
  --no-configuration-cache
```

Expected: PASS with the new ownership tests running on both targets.

- [ ] **Step 6: Commit the runtime fix inside the submodule**

```powershell
git.exe -C external/kotlin-winrt add -- winrt-runtime/src/commonMain `
  winrt-runtime/src/commonTest
git.exe -C external/kotlin-winrt commit -m "fix: tie acquired interfaces to parent references"
```

- [ ] **Step 7: Record KWINRT-064 and commit the new gitlink**

Add a `KWINRT-064` entry describing the missing parent/default-interface
ownership, the pointer reproduction, the local kotlin-winrt commit, and the
narrow Compose cleanup still required. Then commit only the issue record and
submodule pointer:

```powershell
git.exe add -- external/kotlin-winrt kotlin-winrt-issues.md
git.exe commit -m "fix: update kotlin-winrt interface ownership"
```

---

### Task 3: Own The Retained Drag Pointer

**Files:**
- Create: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIOwnedResourceSlot.winui.kt`
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIDragAndDropAdapter.winui.kt`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIDragAndDropAdapterTest.kt`

**Interfaces:**
- Produces: `WinUIOwnedResourceUpdate(ownsIncoming, failure)` and `WinUIOwnedResourceSlot<T>.replace/clear/value`.
- Consumed later by: `WinUIPointerInputAdapter` to decide whether it must close the current point.

- [ ] **Step 1: Write failing slot lifecycle tests**

Add tests covering replacement, clear, identity, and failure ordering:

```kotlin
@Test
fun ownedResourceSlotClosesReplacedAndClearedValues() {
    val closed = mutableListOf<String>()
    val slot = WinUIOwnedResourceSlot<String> { closed += it }

    assertTrue(slot.replace("first").ownsIncoming)
    assertTrue(slot.replace("second").ownsIncoming)
    assertEquals(listOf("first"), closed)
    assertEquals("second", slot.value)

    slot.clear().failure?.let { throw it }
    assertEquals(listOf("first", "second"), closed)
    assertNull(slot.value)
}

@Test
fun ownedResourceSlotKeepsNewValueWhenOldCloseFails() {
    val failure = IllegalStateException("close failed")
    val slot = WinUIOwnedResourceSlot<String> { if (it == "first") throw failure }
    slot.replace("first")

    val result = slot.replace("second")

    assertTrue(result.ownsIncoming)
    assertSame(failure, result.failure)
    assertEquals("second", slot.value)
}

@Test
fun ownedResourceSlotDoesNotCloseIdenticalValueOnReplacement() {
    val value = Any()
    val closed = mutableListOf<Any>()
    val slot = WinUIOwnedResourceSlot<Any> { closed += it }
    slot.replace(value)

    val result = slot.replace(value)

    assertTrue(result.ownsIncoming)
    assertEquals(emptyList(), closed)
    slot.clear().failure?.let { throw it }
    assertEquals(listOf(value), closed)
}
```

- [ ] **Step 2: Run the focused test and observe the missing-type failure**

```powershell
.\gradlew.bat :compose:ui:ui:winuiJvmTest `
  --tests 'androidx.compose.ui.platform.WinUIDragAndDropAdapterTest' `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand
```

Expected: test compilation fails because `WinUIOwnedResourceSlot` does not
exist.

- [ ] **Step 3: Implement the one-value owned slot**

```kotlin
internal data class WinUIOwnedResourceUpdate(
    val ownsIncoming: Boolean,
    val failure: Throwable? = null,
)

internal class WinUIOwnedResourceSlot<T : Any>(
    private val closeResource: (T) -> Unit,
) {
    var value: T? = null
        private set

    fun replace(next: T?): WinUIOwnedResourceUpdate {
        if (value === next) return WinUIOwnedResourceUpdate(next != null)
        val previous = value
        value = next
        val failure = previous?.let { runCatching { closeResource(it) }.exceptionOrNull() }
        return WinUIOwnedResourceUpdate(next != null, failure)
    }

    fun clear(): WinUIOwnedResourceUpdate = replace(null)
}
```

- [ ] **Step 4: Route `latestPointerPoint` through the slot**

In `WinUIDragAndDropAdapter`:

```kotlin
private val sourcePointerPoint =
    WinUIOwnedResourceSlot<PointerPoint> { point -> point.nativeObject.close() }

val pointerPoint = sourcePointerPoint.value ?: return false

internal fun updateSourcePointerPoint(
    pointerPoint: PointerPoint?,
): WinUIOwnedResourceUpdate =
    if (isDisposed) {
        WinUIOwnedResourceUpdate(ownsIncoming = false)
    } else {
        sourcePointerPoint.replace(pointerPoint)
    }
```

Replace every `latestPointerPoint = null` cleanup with:

```kotlin
sourcePointerPoint.clear().failure?.let { throw it }
```

- [ ] **Step 5: Run drag tests and the full focused WinUI JVM tests**

```powershell
.\gradlew.bat :compose:ui:ui:winuiJvmTest `
  --tests 'androidx.compose.ui.platform.WinUIDragAndDropAdapterTest' `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand
.\gradlew.bat :compose:ui:ui:winuiJvmTest `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand
```

Expected: PASS.

- [ ] **Step 6: Commit the drag ownership change**

```powershell
git.exe add -- compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIOwnedResourceSlot.winui.kt `
  compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIDragAndDropAdapter.winui.kt `
  compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIDragAndDropAdapterTest.kt
git.exe commit -m "fix: release retained WinUI drag pointer"
```

---

### Task 4: Close Transient Pointer Projections

**Files:**
- Create: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIProjectionLifetime.winui.kt`
- Create: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIProjectionLifetimeTest.kt`
- Modify: `compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIPointerInputAdapter.winui.kt`
- Modify: `compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIPointerEventProcessorTest.kt`

**Interfaces:**
- Consumes: `WinUIOwnedResourceUpdate` from Task 3 and parent-owned default interfaces from Task 2.
- Produces: `WinUIOwnedResource<T>`, `mapWinUIOwnedList`, and a pointer handler that retains only Kotlin/Compose values.

- [ ] **Step 1: Write failing transient-owner tests**

Create `WinUIProjectionLifetimeTest.kt`:

```kotlin
package androidx.compose.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WinUIProjectionLifetimeTest {
    @Test
    fun ownedResourceClosesOnFailure() {
        val closed = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            WinUIOwnedResource("point") { closed += it }.use {
                error("conversion failed")
            }
        }
        assertEquals(listOf("point"), closed)
    }

    @Test
    fun releasedResourceIsNotClosedByOriginalOwner() {
        val closed = mutableListOf<String>()
        WinUIOwnedResource("point") { closed += it }.use { owner ->
            assertEquals("point", owner.releaseOwnership())
        }
        assertEquals(emptyList(), closed)
    }

    @Test
    fun ownedListClosesFetchedElementsAndCollectionOnFailure() {
        val closed = mutableListOf<String>()
        val failure = assertFailsWith<IllegalStateException> {
            mapWinUIOwnedList(
                values = listOf("one", "two", "three"),
                endExclusive = 3,
                closeValues = { closed += "list" },
                closeValue = { closed += it },
            ) { value ->
                if (value == "two") error("bad point")
                value
            }
        }
        assertEquals("bad point", failure.message)
        assertEquals(listOf("one", "two", "list"), closed)
    }
}
```

- [ ] **Step 2: Run the tests and verify missing ownership helpers fail compilation**

```powershell
.\gradlew.bat :compose:ui:ui:winuiJvmTest `
  --tests 'androidx.compose.ui.platform.WinUIProjectionLifetimeTest' `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand
```

- [ ] **Step 3: Implement transient ownership helpers**

Create `WinUIProjectionLifetime.winui.kt`:

```kotlin
package androidx.compose.ui.platform

internal class WinUIOwnedResource<T : Any>(
    resource: T,
    private val closeResource: (T) -> Unit,
) : AutoCloseable {
    private var resource: T? = resource

    val value: T
        get() = checkNotNull(resource) { "Resource ownership was already released." }

    fun releaseOwnership(): T = value.also { resource = null }

    override fun close() {
        val current = resource
        resource = null
        if (current != null) closeResource(current)
    }
}

internal inline fun <T : Any, R : Any> mapWinUIOwnedList(
    values: List<T>,
    endExclusive: Int,
    crossinline closeValues: (List<T>) -> Unit,
    crossinline closeValue: (T) -> Unit,
    transform: (T) -> R?,
): List<R> =
    WinUIOwnedResource(values, closeValues).use {
        buildList {
            repeat(endExclusive.coerceIn(0, values.size)) { index ->
                WinUIOwnedResource(values[index], closeValue).use { value ->
                    transform(value.value)?.let(::add)
                }
            }
        }
    }
```

- [ ] **Step 4: Close every pointer callback projection**

Change `onSourcePointerPointChanged` to return `WinUIOwnedResourceUpdate`, with
`WinUIOwnedResourceUpdate(ownsIncoming = false)` as its default. Refactor the
normal pointer handler to this ownership boundary:

```kotlin
val handler: PointerEventHandler = { sender, args ->
    // KWINRT-064: callback projections are synchronous transients unless ownership is transferred.
    WinUIOwnedResource(args) { it.nativeObject.close() }.use { argsOwner ->
        if (!isDisposed) {
            cancellationSent = false
            val point = args.getCurrentPoint(root)
            WinUIOwnedResource(point) { it.nativeObject.close() }.use { pointOwner ->
                val pointerEvent = createPointerEvent(eventType, args, pointOwner.value)
                updatePointerCapture(pointerEvent, args)
                val activePointers = pointerStateTracker.update(
                    eventType = eventType,
                    changedPointer = pointerEvent.toPointerSample(),
                )
                debugPointerInput {
                    "native event=$eventType sender=${sender?.debugClassName()} " +
                        "handledBefore=${args.handled} ${pointerEvent.debugString()}"
                }
                val handled = pointerEventProcessor.process(
                    event = pointerEvent,
                    onBeforeDispatch = { event ->
                        val update = updateDragSourcePointer(event)
                        if (update.ownsIncoming) pointOwner.releaseOwnership()
                        update.failure?.let { throw it }
                    },
                    sendPointerEvent = { dispatchType, position, uptimeMillis, pointerId,
                            down, type, buttons, keyboardModifiers, button, scrollDelta,
                            isInBounds, nativeEvent ->
                        owner.sendPointerEvent(
                            eventType = dispatchType,
                            position = position,
                            uptimeMillis = uptimeMillis,
                            pointerId = pointerId,
                            down = down,
                            type = type,
                            buttons = buttons,
                            keyboardModifiers = keyboardModifiers,
                            button = button,
                            scrollDelta = scrollDelta,
                            isInBounds = isInBounds,
                            nativeEvent = nativeEvent,
                            pointerSamples = activePointers,
                        )
                    },
                )
                args.handled = handled
            }
        }
    }
}
```

Wrap the `args` object in the cancel and capture-lost handlers with the same
`WinUIOwnedResource(args) { it.nativeObject.close() }.use { ... }` boundary so
every `PointerEventHandler` invocation releases its callback projection.

Change `createPointerEvent` to accept the already-owned current point and close
its properties after conversion:

```kotlin
private fun createPointerEvent(
    eventType: PointerEventType,
    args: PointerRoutedEventArgs,
    point: PointerPoint,
): WinUIPointerEvent {
    val position = point.position
    val properties = checkNotNull(point.properties) {
        "WinUI pointer properties are not available."
    }
    return WinUIOwnedResource(properties) { it.nativeObject.close() }.use { owner ->
        val ownedProperties = owner.value
        val buttons = ownedProperties.toComposeButtons()
        val nativeKeyboardModifiers = args.toComposeKeyboardModifiersOrNull()
        if (nativeKeyboardModifiers != null) {
            keyboardModifierState.reconcilePressed(nativeKeyboardModifiers)
        }
        WinUIPointerEvent(
            eventType = eventType,
            position = winUIPositionToComposeOffset(
                position.x,
                position.y,
                this@WinUIPointerInputAdapter.owner.density,
            ),
            uptimeMillis = point.timestamp.toLong() / MicrosecondsPerMillisecond,
            pointerId = point.pointerId.toLong(),
            down = point.isComposePointerDown(eventType, buttons),
            type = point.toComposePointerType(ownedProperties),
            buttons = buttons,
            keyboardModifiers = nativeKeyboardModifiers
                ?: keyboardModifierState.toPointerKeyboardModifiers(),
            button = ownedProperties.pointerUpdateKind.toComposeButton(),
            scrollDelta = if (eventType == PointerEventType.Scroll) {
                ownedProperties.toComposeScrollDelta()
            } else {
                Offset.Zero
            },
            isInBounds = eventType != PointerEventType.Exit,
            nativeEvent = args,
            sourcePointerPoint = point,
            pressure = point.toComposePressure(eventType, ownedProperties),
            activeHover = point.toComposeActiveHover(eventType, ownedProperties),
            historical = args.toComposeHistoricalChanges(
                root,
                this@WinUIPointerInputAdapter.owner.density,
            ),
        )
    }
}
```

Replace historical conversion with indexed consumption that closes the actual
`WinRTListProjection.FromAbiHelper` and each fetched point:

```kotlin
private fun PointerRoutedEventArgs.toComposeHistoricalChanges(
    root: UIElement,
    density: Density,
): List<HistoricalChange> = runCatching {
    val points = getIntermediatePoints(root)
    mapWinUIOwnedList(
        values = points,
        endExclusive = (points.size - 1).coerceAtLeast(0),
        closeValues = { values -> (values as AutoCloseable).close() },
        closeValue = { point -> point.nativeObject.close() },
    ) { historicalPoint ->
        val position = historicalPoint.position
        if (!position.x.isFinite() || !position.y.isFinite()) null else
            HistoricalChange(
                uptimeMillis = historicalPoint.timestamp.toLong() / MicrosecondsPerMillisecond,
                position = winUIPositionToComposeOffset(position.x, position.y, density),
            )
    }
}.getOrDefault(emptyList())
```

Add a short `KWINRT-064` comment at the ownership boundary, not at every close.

- [ ] **Step 5: Preserve explicit drag transfer semantics in tests**

Add these tests to `WinUIPointerEventProcessorTest`; they use the real owner and
slot helpers and prove ownership is decided before Compose dispatch:

```kotlin
@Test
fun acceptedDragTransferKeepsPointUntilSlotIsCleared() {
    val events = mutableListOf<String>()
    val closed = mutableListOf<String>()
    val slot = WinUIOwnedResourceSlot<String> { closed += it }

    WinUIOwnedResource("point") { closed += it }.use { pointOwner ->
        WinUIPointerEventProcessor().process(
            event = samplePointerEvent(),
            onBeforeDispatch = {
                val update = slot.replace(pointOwner.value)
                if (update.ownsIncoming) pointOwner.releaseOwnership()
                update.failure?.let { throw it }
                events += "source"
            },
        ) { _, _, _, _, _, _, _, _, _, _, _, _ ->
            events += "compose"
            true
        }
    }

    assertEquals(listOf("source", "compose"), events)
    assertEquals(emptyList(), closed)
    slot.clear().failure?.let { throw it }
    assertEquals(listOf("point"), closed)
}

@Test
fun rejectedDragTransferLeavesPointWithOriginalOwner() {
    val closed = mutableListOf<String>()

    WinUIOwnedResource("point") { closed += it }.use { pointOwner ->
        WinUIPointerEventProcessor().process(
            event = samplePointerEvent(),
            onBeforeDispatch = {
                val update = WinUIOwnedResourceUpdate(ownsIncoming = false)
                if (update.ownsIncoming) pointOwner.releaseOwnership()
            },
        ) { _, _, _, _, _, _, _, _, _, _, _, _ -> true }
    }

    assertEquals(listOf("point"), closed)
}
```

- [ ] **Step 6: Run focused and full WinUI JVM tests**

```powershell
.\gradlew.bat :compose:ui:ui:winuiJvmTest `
  --tests 'androidx.compose.ui.platform.WinUIProjectionLifetimeTest' `
  --tests 'androidx.compose.ui.platform.WinUIPointerEventProcessorTest' `
  --tests 'androidx.compose.ui.platform.WinUIDragAndDropAdapterTest' `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand

.\gradlew.bat :compose:ui:ui:winuiJvmTest `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand
```

Expected: PASS with no pointer, drag, capture, or disposal regressions.

- [ ] **Step 7: Commit transient pointer cleanup**

```powershell
git.exe add -- compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIProjectionLifetime.winui.kt `
  compose/ui/ui/src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIPointerInputAdapter.winui.kt `
  compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIProjectionLifetimeTest.kt `
  compose/ui/ui/src/winuiJvmTest/kotlin/androidx/compose/ui/platform/WinUIPointerEventProcessorTest.kt
git.exe commit -m "fix: close transient WinUI pointer projections"
```

---

### Task 5: Validate Samples And Memory Plateau

**Files:**
- Modify: `kotlin-winrt-issues.md`
- Modify: `compose/ui/compose-winui-plan.md`
- Generate untracked: `.agent_tmp/pointer-memory-after-jvm.csv`
- Generate untracked: `.agent_tmp/pointer-memory-after-mingw.csv`

**Interfaces:**
- Consumes: completed runtime and Compose ownership changes.
- Produces: build, sample, and process-memory evidence closing `KWINRT-064` for this repository baseline.

- [ ] **Step 1: Compile and test compose-ui for WinUI JVM**

```powershell
$env:JAVA_HOME = (Resolve-Path '.agent_tmp\jdk25\jdk-25.0.3+9').Path
.\gradlew.bat :compose:ui:ui:compileKotlinWinuiJvm `
  :compose:ui:ui:winuiJvmTest `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run the repository-local compose-ui WinUI sample**

```powershell
.\gradlew.bat :compose:ui:ui:winui-samples:runWinUISkikoSample `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand
```

Wait for its configured smoke completion and confirm the process exits cleanly.

- [ ] **Step 3: Build and run the JVM MPP sample**

First run the complete auto-exit validation task:

```powershell
.\gradlew.bat :compose:mpp:demo-winui:runWinUIMppSample `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand
```

Then build and launch the staged JVM host directly for interactive measurement:

```powershell
.\gradlew.bat :compose:mpp:demo-winui:buildWinRTApplicationHost `
  '-PcomposeWinUi.enableJvmTarget=true' `
  --no-configuration-cache --no-configure-on-demand
$jvmLayout = Resolve-Path `
  'out\compose-multiplatform-core\compose\mpp\demo-winui\build\kotlin-winrt\application-layout\jvm'
$env:KOTLIN_WINRT_JVM_OPTIONS =
  '-Xmx512m;-XX:+UseParallelGC;-Dfile.encoding=UTF-8;' +
  '-Dcompose.winui.mpp.sample.autoExit=false;' +
  '-Dcompose.winui.mpp.sample.autoTraverse=false'
$jvmSample = Start-Process `
  -FilePath "$jvmLayout\demo-winui.exe" `
  -WorkingDirectory $jvmLayout `
  -PassThru
$jvmSample.Id | Set-Content '.agent_tmp\pointer-memory-after-jvm.pid'
```

Confirm visible hover, press/release, wheel, and drag behavior before measuring.

- [ ] **Step 4: Measure three post-warm-up JVM pointer rounds**

Create `.agent_tmp/Measure-WinUIPointerMemory.ps1` with this exact content:

```powershell
param(
    [Parameter(Mandatory)] [int] $TargetProcessId,
    [Parameter(Mandatory)] [string] $OutputPath,
    [int] $InputsPerRound = 4000,
    [int] $MeasuredRounds = 3,
    [switch] $Jvm,
    [string] $JdkHome
)

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class PointerMemoryInput {
    [DllImport("user32.dll")]
    public static extern void mouse_event(uint flags, int dx, int dy, uint data, UIntPtr extraInfo);
}
'@

$rows = [System.Collections.Generic.List[object]]::new()
$totalInputs = 0
$jcmd = if ($Jvm) { Join-Path $JdkHome 'bin\jcmd.exe' } else { $null }

function Invoke-FullGc {
    if ($Jvm) {
        & $jcmd $TargetProcessId GC.run |
            Out-File -Append '.agent_tmp\pointer-memory-after-jvm-jcmd.log'
        Start-Sleep -Seconds 2
    }
}

function Add-MemorySample([string] $phase) {
    $process = Get-Process -Id $TargetProcessId -ErrorAction Stop
    $script:rows.Add([pscustomobject]@{
        Timestamp = (Get-Date).ToString('o')
        Phase = $phase
        InputsSent = $script:totalInputs
        WorkingSetMB = [Math]::Round($process.WorkingSet64 / 1MB, 1)
        PrivateMB = [Math]::Round($process.PrivateMemorySize64 / 1MB, 1)
        Handles = $process.HandleCount
    })
}

function Send-PointerInputs([int] $count) {
    for ($index = 0; $index -lt $count; $index++) {
        $delta = if (($index % 2) -eq 0) { 8 } else { -8 }
        [PointerMemoryInput]::mouse_event(0x0001, $delta, 0, 0, [UIntPtr]::Zero)
        $script:totalInputs++
        Start-Sleep -Milliseconds 8
    }
}

Invoke-FullGc
Add-MemorySample 'baseline-after-gc'
Send-PointerInputs $InputsPerRound
Invoke-FullGc
Add-MemorySample 'warmup-after-gc'
for ($round = 1; $round -le $MeasuredRounds; $round++) {
    Send-PointerInputs $InputsPerRound
    Invoke-FullGc
    Start-Sleep -Seconds 3
    Add-MemorySample "round-$round-settled"
}
$rows | Export-Csv -NoTypeInformation -Path $OutputPath
```

Run it against the JVM sample without enabling
`compose.winui.pointerInput.debug`:

```powershell
.\.agent_tmp\Measure-WinUIPointerMemory.ps1 `
  -TargetProcessId $jvmSample.Id `
  -OutputPath '.agent_tmp\pointer-memory-after-jvm.csv' `
  -Jvm `
  -JdkHome (Resolve-Path '.agent_tmp\jdk25\jdk-25.0.3+9')
```

Expected: after one warm-up plus three rounds of at least 4,000 inputs, private
bytes from the first measured post-GC round to the final round grow by no more
than 5 MB and do not show a repeated event-proportional slope.

- [ ] **Step 5: Compile and run the MinGW MPP sample**

```powershell
.\gradlew.bat :compose:ui:ui:compileKotlinWinuiMingw `
  :compose:mpp:demo-winui:stageWinRTApplicationPackage `
  '-PcomposeWinUi.enableMingwTarget=true' `
  --no-configuration-cache --no-configure-on-demand
$mingwLayout = Resolve-Path `
  'out\compose-multiplatform-core\compose\mpp\demo-winui\build\kotlin-winrt\application-layout\mingwX64\release'
$mingwSample = Start-Process `
  -FilePath "$mingwLayout\demo-winui.exe" `
  -WorkingDirectory $mingwLayout `
  -PassThru
.\.agent_tmp\Measure-WinUIPointerMemory.ps1 `
  -TargetProcessId $mingwSample.Id `
  -OutputPath '.agent_tmp\pointer-memory-after-mingw.csv'
```

The shared script runs one warm-up and three equal 4,000-input rounds separated
by equal settling intervals. Close both sample processes after their CSV files
are written:

```powershell
Stop-Process -Id $jvmSample.Id, $mingwSample.Id -ErrorAction SilentlyContinue
```

Expected: post-warm-up private bytes plateau within 5 MB aggregate growth.

- [ ] **Step 6: Update issue and implementation-plan evidence**

Update `KWINRT-064` with:

```text
- kotlin-winrt submodule commit containing parent-owned interface references;
- compose-winui commits containing drag and transient pointer cleanup;
- JVM and MinGW event counts plus first/final settled private bytes;
- exact Gradle sample tasks that passed with --no-configuration-cache.
```

Mark the corresponding pointer-memory hardening item complete in
`compose/ui/compose-winui-plan.md`. Keep raw logs only under `.agent_tmp/`.

- [ ] **Step 7: Commit verified documentation**

```powershell
git.exe add -- kotlin-winrt-issues.md compose/ui/compose-winui-plan.md
git.exe commit -m "docs: record WinUI pointer lifetime validation"
```

- [ ] **Step 8: Final verification and scope check**

```powershell
git.exe status --short --branch
git.exe log -6 --oneline --decorate
git.exe diff HEAD~4..HEAD --check
```

Confirm no unrelated untracked files were staged, `AGENTS.md` remains
untracked, and all required processes have exited.
