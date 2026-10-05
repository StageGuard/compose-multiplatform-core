/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.platform

import androidx.compose.ui.SystemTheme
import androidx.compose.ui.unit.LayoutDirection
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import microsoft.ui.xaml.ElementTheme
import microsoft.ui.xaml.FlowDirection
import windows.ui.Color

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
            dispatch = { block ->
                pending.addLast(block)
                true
            },
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
    fun observerSnapshotsUpdatesOnlyAfterDispatcherMarshalling() {
        val source = FakeEnvironmentSource(WinUIEnvironment())
        val pending = ArrayDeque<() -> Unit>()
        val observer = WinUIEnvironmentObserver(
            source = source,
            dispatch = { block ->
                pending.addLast(block)
                true
            },
            onChanged = {},
        )

        assertEquals(1, source.snapshotCount)

        source.emitChange()

        assertEquals(1, source.snapshotCount)
        pending.removeFirst().invoke()
        assertEquals(2, source.snapshotCount)

        observer.close()
    }

    @Test
    fun observerIgnoresPendingCallbacksAfterCloseAndClosesSourceOnce() {
        val source = FakeEnvironmentSource(WinUIEnvironment())
        val pending = ArrayDeque<() -> Unit>()
        var updateCount = 0
        val observer = WinUIEnvironmentObserver(
            source = source,
            dispatch = { block ->
                pending.addLast(block)
                true
            },
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

    @Test
    fun xamlValuesMapToComposeEnvironmentValues() {
        assertEquals(SystemTheme.Dark, ElementTheme.Dark.toComposeSystemTheme())
        assertEquals(SystemTheme.Light, ElementTheme.Light.toComposeSystemTheme())
        assertEquals(SystemTheme.Unknown, ElementTheme.Default.toComposeSystemTheme())
        assertEquals(
            LayoutDirection.Ltr,
            FlowDirection.LeftToRight.toComposeLayoutDirection(),
        )
        assertEquals(
            LayoutDirection.Rtl,
            FlowDirection.RightToLeft.toComposeLayoutDirection(),
        )
    }

    @Test
    fun layoutDirectionIsRtlForAnRtlTreeOrAnRtlLocale() {
        assertEquals(LayoutDirection.Ltr, winUILayoutDirection(FlowDirection.LeftToRight, false))
        assertEquals(LayoutDirection.Rtl, winUILayoutDirection(FlowDirection.RightToLeft, false))
        // XAML does not flow right to left for an RTL locale by itself; the desktop target does.
        assertEquals(LayoutDirection.Rtl, winUILayoutDirection(FlowDirection.LeftToRight, true))
        assertEquals(LayoutDirection.Ltr, winUILayoutDirection(null, false))
    }

    @Test
    fun systemColorsMapToComposeSystemTheme() {
        val black = Color(a = 255u, r = 0u, g = 0u, b = 0u)
        val white = Color(a = 255u, r = 255u, g = 255u, b = 255u)

        assertEquals(SystemTheme.Dark, systemThemeFromColors(white, black))
        assertEquals(SystemTheme.Light, systemThemeFromColors(black, white))
        assertEquals(SystemTheme.Unknown, systemThemeFromColors(black, black))
    }

    @Test
    fun actualXamlThemeOverridesSystemColors() {
        val black = Color(a = 255u, r = 0u, g = 0u, b = 0u)
        val white = Color(a = 255u, r = 255u, g = 255u, b = 255u)

        assertEquals(
            SystemTheme.Dark,
            resolveWinUISystemTheme(
                requestedTheme = ElementTheme.Dark,
                actualTheme = ElementTheme.Dark,
                foreground = black,
                background = white,
            ),
        )
        assertEquals(
            SystemTheme.Light,
            resolveWinUISystemTheme(
                requestedTheme = ElementTheme.Default,
                actualTheme = ElementTheme.Light,
                foreground = white,
                background = black,
            ),
        )
    }

    @Test
    fun systemColorsAreFallbackWhenXamlThemeIsUnknown() {
        val black = Color(a = 255u, r = 0u, g = 0u, b = 0u)
        val white = Color(a = 255u, r = 255u, g = 255u, b = 255u)

        assertEquals(
            SystemTheme.Dark,
            resolveWinUISystemTheme(
                requestedTheme = ElementTheme.Default,
                actualTheme = ElementTheme.Default,
                foreground = white,
                background = black,
            ),
        )
    }

    @Test
    fun textScaleUsesOnlyFinitePositiveValues() {
        assertEquals(1f, normalizeWinUITextScaleFactor(null))
        assertEquals(1f, normalizeWinUITextScaleFactor(Double.NaN))
        assertEquals(1f, normalizeWinUITextScaleFactor(Double.POSITIVE_INFINITY))
        assertEquals(1f, normalizeWinUITextScaleFactor(0.0))
        assertEquals(1f, normalizeWinUITextScaleFactor(-1.0))
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
            "uiSettings.colorValuesChanged.add",
            "root.actualThemeChanged.remove",
            "root.loaded.remove",
            "root.unregisterPropertyChangedCallback(FrameworkElement.flowDirectionProperty",
            "uiSettings.textScaleFactorChanged.remove",
            "uiSettings.animationsEnabledChanged.remove",
            "uiSettings.colorValuesChanged.remove",
        ).forEach { required ->
            assertTrue(source.contains(required), "Missing environment wiring: $required")
        }

        val textScaleRemove = source.indexOf("uiSettings.textScaleFactorChanged.remove")
        val animationsRemove = source.indexOf("uiSettings.animationsEnabledChanged.remove")
        val colorsRemove = source.indexOf("uiSettings.colorValuesChanged.remove")
        val settingsClose = source.indexOf("uiSettings.nativeObject.close()")
        assertTrue(settingsClose > textScaleRemove, "UISettings must close after text-scale removal")
        assertTrue(settingsClose > animationsRemove, "UISettings must close after animations removal")
        assertTrue(settingsClose > colorsRemove, "UISettings must close after color-values removal")
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

private class FakeEnvironmentSource(
    initial: WinUIEnvironment,
) : WinUIEnvironmentSource {
    var current = initial
    var listener: (() -> Unit)? = null
    var closeCount = 0
    var snapshotCount = 0

    override fun snapshot(): WinUIEnvironment {
        snapshotCount += 1
        return current
    }

    override fun setChangeListener(listener: (() -> Unit)?) {
        this.listener = listener
    }

    fun emitChange() {
        listener?.invoke()
    }

    override fun close() {
        closeCount += 1
    }
}
