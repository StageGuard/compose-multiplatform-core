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
    fun lifecycleStateMatchesEveryBooleanCombination() {
        val values = listOf(false, true)
        var caseCount = 0

        for (isLoaded in values) {
            for (isVisible in values) {
                for (isActive in values) {
                    for (isDisposed in values) {
                        val expected = when {
                            isDisposed -> Lifecycle.State.DESTROYED
                            !isLoaded || !isVisible -> Lifecycle.State.CREATED
                            !isActive -> Lifecycle.State.STARTED
                            else -> Lifecycle.State.RESUMED
                        }
                        assertEquals(
                            expected,
                            calculateWinUIViewLifecycleState(
                                isLoaded,
                                isVisible,
                                isActive,
                                isDisposed,
                            ),
                            "loaded=$isLoaded visible=$isVisible active=$isActive " +
                                "disposed=$isDisposed",
                        )
                        caseCount++
                    }
                }
            }
        }

        assertEquals(16, caseCount)
    }

    @Test
    fun controllerPublishesOnlyDistinctAtomicTransitionsAndDestroyIsTerminal() {
        val states = mutableListOf<Lifecycle.State>()
        val controller = WinUIViewLifecycleController(states::add)

        controller.setRootState(isLoaded = true, isVisible = false)
        controller.setRootState(isLoaded = true, isVisible = true)
        controller.setActive(true)
        controller.setActive(true)
        controller.setVisible(false)
        controller.dispose()
        controller.setRootState(isLoaded = true, isVisible = true)

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
    fun loadedCollapsedInitializationIsAtomic() {
        val states = mutableListOf<Lifecycle.State>()
        val controller = WinUIViewLifecycleController(states::add)
        val source = FakeWinUIViewLifecycleSource(
            initialIsLoaded = true,
            initialIsVisible = false,
        )
        val binding = WinUIRootLifecycleBinding(source, controller)

        assertEquals(listOf(Lifecycle.State.CREATED), states)

        binding.close()
    }

    @Test
    fun bindingPublishesLoadedAndVisibilityTransitionsWithoutDuplicates() {
        val states = mutableListOf<Lifecycle.State>()
        val controller = WinUIViewLifecycleController(states::add)
        val source = FakeWinUIViewLifecycleSource(
            initialIsLoaded = false,
            initialIsVisible = true,
        )
        val binding = WinUIRootLifecycleBinding(source, controller)

        controller.setActive(true)
        source.emitLoaded(true)
        source.emitLoaded(true)
        source.emitVisible(false)
        source.emitVisible(false)
        source.emitVisible(true)
        source.emitLoaded(false)

        assertEquals(
            listOf(
                Lifecycle.State.CREATED,
                Lifecycle.State.RESUMED,
                Lifecycle.State.CREATED,
                Lifecycle.State.RESUMED,
                Lifecycle.State.CREATED,
            ),
            states,
        )

        binding.close()
    }

    @Test
    fun closeAttemptsAllCleanupDisposesOnceAndMakesCallbacksInert() {
        val states = mutableListOf<Lifecycle.State>()
        val controller = WinUIViewLifecycleController(states::add)
        val source = FakeWinUIViewLifecycleSource(
            initialIsLoaded = true,
            initialIsVisible = true,
            cleanupFailureIndex = 1,
        )
        val binding = WinUIRootLifecycleBinding(source, controller)

        binding.close()
        binding.close()
        val statesAfterClose = states.toList()
        source.emitLoaded(false)
        source.emitVisible(false)

        assertEquals(listOf(0, 1, 2), source.cleanupAttempts)
        assertEquals(
            listOf(
                Lifecycle.State.CREATED,
                Lifecycle.State.STARTED,
                Lifecycle.State.DESTROYED,
            ),
            states,
        )
        assertEquals(statesAfterClose, states)
    }

    @Test
    fun productionSourceUsesRequiredNativeXamlLifecycleSignals() {
        val source = findWinUIUiModuleRoot()
            .resolve("src/winuiMain/kotlin/androidx/compose/ui/platform/WinUIViewLifecycle.winui.kt")
            .readText()
            .filterNot { it.isWhitespace() }

        listOf(
            "root.loaded.add(",
            "root.unloaded.add(",
            "root.registerPropertyChangedCallback(UIElement.visibilityProperty",
            "root.loaded.remove(",
            "root.unloaded.remove(",
            "root.unregisterPropertyChangedCallback(UIElement.visibilityProperty",
        ).forEach { nativeCall ->
            assertTrue(source.contains(nativeCall), "Missing native lifecycle call: $nativeCall")
        }
    }
}

private class FakeWinUIViewLifecycleSource(
    private val initialIsLoaded: Boolean,
    private val initialIsVisible: Boolean,
    private val cleanupFailureIndex: Int? = null,
) : WinUIViewLifecycleSource {
    private lateinit var onLoadedChanged: (Boolean) -> Unit
    private lateinit var onVisibilityChanged: (Boolean) -> Unit
    val cleanupAttempts = mutableListOf<Int>()

    override fun register(
        onLoadedChanged: (Boolean) -> Unit,
        onVisibilityChanged: (Boolean) -> Unit,
    ): List<() -> Unit> {
        this.onLoadedChanged = onLoadedChanged
        this.onVisibilityChanged = onVisibilityChanged
        return List(3) { index ->
            {
                cleanupAttempts += index
                if (index == cleanupFailureIndex) error("cleanup $index failed")
            }
        }
    }

    override fun readRootState(): WinUIViewLifecycleRootState = WinUIViewLifecycleRootState(
        isLoaded = initialIsLoaded,
        isVisible = initialIsVisible,
    )

    fun emitLoaded(value: Boolean) {
        onLoadedChanged(value)
    }

    fun emitVisible(value: Boolean) {
        onVisibilityChanged(value)
    }
}
