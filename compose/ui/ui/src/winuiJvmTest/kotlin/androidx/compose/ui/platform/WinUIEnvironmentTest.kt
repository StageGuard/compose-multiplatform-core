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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    fun emitChange() {
        listener?.invoke()
    }

    override fun close() {
        closeCount += 1
    }
}
