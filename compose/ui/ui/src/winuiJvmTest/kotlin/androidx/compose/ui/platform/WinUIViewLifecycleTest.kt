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

import androidx.lifecycle.Lifecycle
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinUIViewLifecycleTest {
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

    @Test
    fun productionSourceBindsAndUnbindsEveryLifecycleSignalBeforeDisposingController() {
        val source = findWinUIUiModuleRoot()
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIViewLifecycle.winui.kt")
            .readText()

        val registrations = listOf(
            GuardedRegistration("loadedToken", "EventRegistrationToken", "root.loaded.add"),
            GuardedRegistration("unloadedToken", "EventRegistrationToken", "root.unloaded.add"),
            GuardedRegistration("visibilityToken", "Long", "root.registerPropertyChangedCallback"),
        )
        registrations.forEach { registration ->
            assertNullableGuardedRegistration(source, registration)
        }
        assertTrue(
            Regex("""root\.registerPropertyChangedCallback\s*\(\s*UIElement\.visibilityProperty""")
                .containsMatchIn(source),
            "Missing lifecycle visibility registration",
        )
        assertTrue(
            Regex("""root\.unregisterPropertyChangedCallback\s*\(\s*UIElement\.visibilityProperty""")
                .containsMatchIn(source),
            "Missing lifecycle visibility removal",
        )

        val disposeBody = source.substringAfter("override fun close() {")
        val loadedRemoval = disposeBody.indexOf("root.loaded.remove")
        val unloadedRemoval = disposeBody.indexOf("root.unloaded.remove")
        val visibilityRemoval = disposeBody.indexOf("root.unregisterPropertyChangedCallback")
        val controllerDispose = disposeBody.indexOf("controller.dispose()")
        val guardedRemovalBlocks = listOf(
            loadedRemoval,
            unloadedRemoval,
            visibilityRemoval,
        ).map { removal ->
            assertTrue(removal in 0 until controllerDispose)
            runCatchingBlocks(disposeBody).singleOrNull { removal in it }
                ?: error("Lifecycle removal at $removal must have its own runCatching guard")
        }
        assertEquals(
            guardedRemovalBlocks.size,
            guardedRemovalBlocks.distinct().size,
            "Each lifecycle removal must have an independent failure boundary",
        )
    }
}

private data class GuardedRegistration(
    val tokenName: String,
    val tokenType: String,
    val registrationCall: String,
)

private fun assertNullableGuardedRegistration(
    source: String,
    registration: GuardedRegistration,
) {
    val declaration = Regex(
        """\bval\s+${registration.tokenName}\s*:\s*${registration.tokenType}\?\s*=""",
    ).find(source) ?: error("${registration.tokenName} must be nullable")
    val call = source.indexOf(registration.registrationCall, declaration.range.last)
    assertTrue(call >= 0, "Missing guarded registration: ${registration.registrationCall}")
    val guard = runCatchingBlocks(source).singleOrNull { call in it }
        ?: error("${registration.registrationCall} must have a narrow runCatching guard")
    val fallback = source.substring(guard.last + 1)
        .trimStart()
        .startsWith(".getOrNull()")
    assertTrue(fallback, "${registration.tokenName} registration must fall back with getOrNull()")
}

private fun runCatchingBlocks(source: String): List<IntRange> = buildList {
    var searchFrom = 0
    while (true) {
        val call = source.indexOf("runCatching", searchFrom)
        if (call < 0) break
        val openBrace = source.indexOf('{', call)
        if (openBrace < 0) break

        var depth = 1
        var cursor = openBrace + 1
        while (cursor < source.length && depth > 0) {
            when (source[cursor]) {
                '{' -> depth++
                '}' -> depth--
            }
            cursor++
        }
        if (depth == 0) add(openBrace until cursor)
        searchFrom = cursor
    }
}
