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
import microsoft.ui.xaml.DependencyPropertyChangedCallback
import microsoft.ui.xaml.ElementTheme
import microsoft.ui.xaml.FlowDirection
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.RoutedEventHandler
import windows.foundation.EventRegistrationToken
import windows.foundation.TypedEventHandler
import windows.ui.Color
import windows.ui.viewmanagement.UIColorType
import windows.ui.viewmanagement.UISettings
import windows.ui.viewmanagement.UISettingsAnimationsEnabledChangedEventArgs
import kotlin.math.pow

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

internal class WinUIXamlEnvironmentSource(
    private val root: FrameworkElement,
    private val uiSettings: UISettings? = runCatching { UISettings() }.getOrNull(),
) : WinUIEnvironmentSource {
    private var listener: (() -> Unit)? = null
    private var isClosed = false

    private val themeHandler: TypedEventHandler<FrameworkElement, Any?> =
        { _, _ -> notifyChanged() }
    private val loadedHandler: RoutedEventHandler = { _, _ -> notifyChanged() }
    private val flowDirectionHandler =
        DependencyPropertyChangedCallback { _, _ -> notifyChanged() }
    private val textScaleHandler: TypedEventHandler<UISettings, Any?> =
        { _, _ -> notifyChanged() }
    private val animationsHandler:
        TypedEventHandler<UISettings, UISettingsAnimationsEnabledChangedEventArgs> =
        { _, _ -> notifyChanged() }
    private val colorValuesHandler: TypedEventHandler<UISettings, Any?> =
        { _, _ -> notifyChanged() }

    private val themeToken: EventRegistrationToken? =
        runCatching { root.actualThemeChanged.add(themeHandler) }.getOrNull()
    private val loadedToken: EventRegistrationToken? =
        runCatching { root.loaded.add(loadedHandler) }.getOrNull()
    private val flowDirectionToken: Long? = runCatching {
        root.registerPropertyChangedCallback(FrameworkElement.flowDirectionProperty, flowDirectionHandler)
    }.getOrNull()
    private val textScaleToken: EventRegistrationToken? = uiSettings?.let { uiSettings ->
        runCatching { uiSettings.textScaleFactorChanged.add(textScaleHandler) }.getOrNull()
    }
    private val animationsToken: EventRegistrationToken? = uiSettings?.let { uiSettings ->
        runCatching { uiSettings.animationsEnabledChanged.add(animationsHandler) }.getOrNull()
    }
    private val colorValuesToken: EventRegistrationToken? = uiSettings?.let { uiSettings ->
        runCatching { uiSettings.colorValuesChanged.add(colorValuesHandler) }.getOrNull()
    }

    override fun snapshot(): WinUIEnvironment {
        val requestedTheme = runCatching { root.requestedTheme }.getOrDefault(ElementTheme.Default)
        val actualTheme = runCatching { root.actualTheme }.getOrDefault(ElementTheme.Default)
        val foreground = uiSettings?.let { uiSettings ->
            runCatching { uiSettings.getColorValue(UIColorType.Foreground) }.getOrNull()
        }
        val background = uiSettings?.let { uiSettings ->
            runCatching { uiSettings.getColorValue(UIColorType.Background) }.getOrNull()
        }

        return WinUIEnvironment(
            systemTheme = resolveWinUISystemTheme(
                requestedTheme = requestedTheme,
                actualTheme = actualTheme,
                foreground = foreground,
                background = background,
            ),
            layoutDirection = runCatching { root.flowDirection.toComposeLayoutDirection() }
                .getOrDefault(LayoutDirection.Ltr),
            fontScale = normalizeWinUITextScaleFactor(
                uiSettings?.let { uiSettings ->
                    runCatching { uiSettings.textScaleFactor }.getOrNull()
                }
            ),
            animationsEnabled = uiSettings?.let { uiSettings ->
                runCatching { uiSettings.animationsEnabled }.getOrDefault(true)
            } ?: true,
        )
    }

    override fun setChangeListener(listener: (() -> Unit)?) {
        this.listener = listener
    }

    private fun notifyChanged() {
        if (!isClosed) {
            listener?.invoke()
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        listener = null

        themeToken?.let { token ->
            runCatching { root.actualThemeChanged.remove(token) }
        }
        loadedToken?.let { token ->
            runCatching { root.loaded.remove(token) }
        }
        flowDirectionToken?.let { token ->
            runCatching {
                root.unregisterPropertyChangedCallback(FrameworkElement.flowDirectionProperty, token)
            }
        }
        uiSettings?.let { uiSettings ->
            textScaleToken?.let { token ->
                runCatching { uiSettings.textScaleFactorChanged.remove(token) }
            }
            animationsToken?.let { token ->
                runCatching { uiSettings.animationsEnabledChanged.remove(token) }
            }
            colorValuesToken?.let { token ->
                runCatching { uiSettings.colorValuesChanged.remove(token) }
            }
            runCatching { uiSettings.nativeObject.close() }
        }
    }
}

internal fun ElementTheme.toComposeSystemTheme(): SystemTheme = when (this) {
    ElementTheme.Dark -> SystemTheme.Dark
    ElementTheme.Light -> SystemTheme.Light
    else -> SystemTheme.Unknown
}

internal fun resolveWinUISystemTheme(
    requestedTheme: ElementTheme,
    actualTheme: ElementTheme,
    foreground: Color?,
    background: Color?,
): SystemTheme {
    val actualSystemTheme = actualTheme.toComposeSystemTheme()
    if (requestedTheme != ElementTheme.Default) {
        return actualSystemTheme.takeUnless { it == SystemTheme.Unknown }
            ?: requestedTheme.toComposeSystemTheme()
    }

    val systemTheme = if (foreground != null && background != null) {
        systemThemeFromColors(foreground, background)
    } else {
        SystemTheme.Unknown
    }
    return systemTheme.takeUnless { it == SystemTheme.Unknown } ?: actualSystemTheme
}

internal fun systemThemeFromColors(
    foreground: Color,
    background: Color,
): SystemTheme {
    val foregroundLuminance = foreground.relativeLuminance()
    val backgroundLuminance = background.relativeLuminance()
    return when {
        backgroundLuminance < foregroundLuminance -> SystemTheme.Dark
        backgroundLuminance > foregroundLuminance -> SystemTheme.Light
        else -> SystemTheme.Unknown
    }
}

private fun Color.relativeLuminance(): Double {
    fun channel(value: UByte): Double {
        val normalized = value.toInt() / 255.0
        return if (normalized <= 0.04045) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }

    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

internal fun FlowDirection.toComposeLayoutDirection(): LayoutDirection = when (this) {
    FlowDirection.RightToLeft -> LayoutDirection.Rtl
    else -> LayoutDirection.Ltr
}

internal fun normalizeWinUITextScaleFactor(value: Double?): Float =
    value?.toFloat()?.takeIf { it.isFinite() && it > 0f } ?: 1f

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
        dispatch {
            if (!isClosed) {
                onChanged(source.snapshot())
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
