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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.unit.LayoutDirection

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
            if (!isClosed) {
                onChanged(next)
            }
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
