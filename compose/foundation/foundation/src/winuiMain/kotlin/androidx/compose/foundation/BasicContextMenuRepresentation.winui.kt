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

package androidx.compose.foundation

import androidx.compose.foundation.text.contextmenu.data.TextContextMenuComponent
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItemWithComposableLeadingIcon
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWinUIRoot
import androidx.compose.ui.unit.IntOffset
import io.github.composefluent.winrt.runtime.EventHandlerCallback
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.controls.FontIcon
import microsoft.ui.xaml.controls.MenuFlyout
import microsoft.ui.xaml.controls.MenuFlyoutItem
import microsoft.ui.xaml.controls.MenuFlyoutSeparator
import microsoft.ui.xaml.input.KeyboardAccelerator
import windows.foundation.EventRegistrationToken
import windows.foundation.Point
import windows.system.VirtualKey
import windows.system.VirtualKeyModifiers

@OptIn(InternalComposeUiApi::class)
@Composable
internal fun DefaultOpenContextMenu(
    session: TextContextMenuSession,
    components: List<TextContextMenuComponent>,
    positionInRoot: IntOffset,
    colors: ContextMenuColors = DefaultContextMenuColors,
) {
    val root = LocalWinUIRoot.current as? FrameworkElement ?: return
    val density = LocalDensity.current
    val menu = remember(session, components) {
        createMenuFlyout(session, components)
    }
    DisposableEffect(menu) {
        onDispose {
            menu.dispose()
        }
    }
    LaunchedEffect(root, menu, positionInRoot, density) {
        if (components.isEmpty()) {
            return@LaunchedEffect
        }
        menu.flyout.showAt(
            root,
            Point(positionInRoot.x / density.density, positionInRoot.y / density.density),
        )
    }
}

private fun createMenuFlyout(
    session: TextContextMenuSession,
    components: List<TextContextMenuComponent>,
): WinUIContextMenu {
    val flyout = MenuFlyout()
    val clickRegistrations = mutableListOf<WinUIContextMenuClickRegistration>()
    components.forEach { component ->
        when (component) {
            is TextContextMenuSeparator -> flyout.items.add(MenuFlyoutSeparator())
            is TextContextMenuItemWithComposableLeadingIcon -> {
                val item = MenuFlyoutItem()
                item.text = component.label
                item.isEnabled = component.enabled
                configureTextContextMenuItemVisuals(item, component.key)
                val handler = RoutedEventHandler { _, _ ->
                    component.onClick(session)
                }
                val token = item.click.add(handler)
                clickRegistrations += WinUIContextMenuClickRegistration(item, token, handler)
                flyout.items.add(item)
            }
        }
    }
    val closedHandler: EventHandlerCallback<Any?> = { _, _ ->
        session.close()
    }
    val closedRegistration = WinUIContextMenuClosedRegistration(
        flyout.closed.add(closedHandler),
        closedHandler,
    )
    return WinUIContextMenu(flyout, clickRegistrations, closedRegistration)
}

private fun configureTextContextMenuItemVisuals(item: MenuFlyoutItem, key: Any) {
    val visuals = when (key) {
        TextContextMenuKeys.CutKey -> TextContextMenuItemVisuals("\uE8C6", "Ctrl+X", VirtualKey.X)
        TextContextMenuKeys.CopyKey -> TextContextMenuItemVisuals("\uE8C8", "Ctrl+C", VirtualKey.C)
        TextContextMenuKeys.PasteKey -> TextContextMenuItemVisuals("\uE77F", "Ctrl+V", VirtualKey.V)
        TextContextMenuKeys.SelectAllKey -> TextContextMenuItemVisuals(null, "Ctrl+A", VirtualKey.A)
        else -> null
    } ?: return
    visuals.glyph?.let { glyph ->
        item.icon = FontIcon().apply {
            this.glyph = glyph
        }
    }
    item.keyboardAcceleratorTextOverride = visuals.shortcutText
    item.keyboardAccelerators.add(
        KeyboardAccelerator().apply {
            this.key = visuals.key
            this.modifiers = VirtualKeyModifiers.Control
        }
    )
}

private data class TextContextMenuItemVisuals(
    val glyph: String?,
    val shortcutText: String,
    val key: VirtualKey,
)

private class WinUIContextMenu(
    val flyout: MenuFlyout,
    private val clickRegistrations: List<WinUIContextMenuClickRegistration>,
    private val closedRegistration: WinUIContextMenuClosedRegistration,
) {
    fun dispose() {
        clickRegistrations.forEach { registration ->
            runCatching {
                registration.item.click.remove(registration.token)
            }
        }
        runCatching {
            flyout.closed.remove(closedRegistration.token)
        }
        runCatching {
            flyout.hide()
        }
    }
}

private data class WinUIContextMenuClickRegistration(
    val item: MenuFlyoutItem,
    val token: EventRegistrationToken,
    val handler: RoutedEventHandler,
)

private data class WinUIContextMenuClosedRegistration(
    val token: EventRegistrationToken,
    val handler: EventHandlerCallback<Any?>,
)

private const val DisabledAlpha = 0.38f

internal val DefaultContextMenuColors =
    ContextMenuColors(
        backgroundColor = androidx.compose.ui.graphics.Color.White,
        textColor = androidx.compose.ui.graphics.Color.Black,
        iconColor = androidx.compose.ui.graphics.Color.Black,
        disabledTextColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = DisabledAlpha),
        disabledIconColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = DisabledAlpha),
        hoverColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.04f),
    )

internal class ContextMenuColors(
    val backgroundColor: androidx.compose.ui.graphics.Color,
    val textColor: androidx.compose.ui.graphics.Color,
    val iconColor: androidx.compose.ui.graphics.Color,
    val disabledTextColor: androidx.compose.ui.graphics.Color,
    val disabledIconColor: androidx.compose.ui.graphics.Color,
    val hoverColor: androidx.compose.ui.graphics.Color,
)
