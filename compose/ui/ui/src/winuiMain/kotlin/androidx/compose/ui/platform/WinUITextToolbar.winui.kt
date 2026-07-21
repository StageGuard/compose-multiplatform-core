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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import io.github.composefluent.winrt.runtime.EventHandlerCallback
import windows.foundation.EventRegistrationToken
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.controls.MenuFlyout
import microsoft.ui.xaml.controls.MenuFlyoutItem
import microsoft.ui.xaml.controls.primitives.FlyoutShowOptions

internal class WinUITextToolbar(
    private val hostProvider: () -> FrameworkElement? = { null },
    private val densityProvider: () -> Density = { Density(1f) },
) : TextToolbar {
    private var currentMenu: WinUITextToolbarMenu? = null

    override val status: TextToolbarStatus
        get() = if (currentMenu == null) {
            TextToolbarStatus.Hidden
        } else {
            TextToolbarStatus.Shown
        }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?,
    ) {
        hide()
        val requests = buildList {
            addRequest("Copy", onCopyRequested)
            addRequest("Paste", onPasteRequested)
            addRequest("Cut", onCutRequested)
            addRequest("Select all", onSelectAllRequested)
            addRequest("Autofill", onAutofillRequested)
        }
        val host = hostProvider()
        val nativeMenu = if (host != null && requests.isNotEmpty()) {
            createNativeMenu(requests)
        } else {
            null
        }
        val menu = WinUITextToolbarMenu(
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested,
            onAutofillRequested = onAutofillRequested,
            itemLabels = requests.map { it.label },
            nativeMenu = nativeMenu?.flyout,
            clickRegistrations = nativeMenu?.clickRegistrations.orEmpty(),
            closedRegistration = nativeMenu?.closedRegistration,
        )
        currentMenu = menu
        if (host != null && nativeMenu != null) {
            showNativeMenu(host, nativeMenu.flyout, menu)
        }
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        showMenu(
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested,
            onAutofillRequested = null,
        )
    }

    override fun hide() {
        val menu = currentMenu ?: return
        currentMenu = null
        clearNativeRegistrations(menu)
        menu.nativeMenu?.hide()
    }

    internal fun menuForTest(): WinUITextToolbarMenu? = currentMenu

    private fun createNativeMenu(
        requests: List<WinUITextToolbarRequest>,
    ): NativeMenu {
        val flyout = MenuFlyout()
        val clickRegistrations = mutableListOf<WinUITextToolbarClickRegistration>()
        requests.forEach { request ->
            val item = MenuFlyoutItem()
            item.text = request.label
            val handler: RoutedEventHandler = { _, _ ->
                request.callback()
                hide()
            }
            val token = try {
                item.click.add(handler)
            } catch (throwable: Throwable) {
                clickRegistrations.forEach(::clearClickRegistration)
                throw throwable
            }
            clickRegistrations += WinUITextToolbarClickRegistration(item, token, handler)
            flyout.items.add(item)
        }
        val closedHandler: EventHandlerCallback<Any?> = { _, _ ->
            val menu = currentMenu
            if (menu?.nativeMenu == flyout) {
                currentMenu = null
                clearNativeRegistrations(menu)
            }
        }
        val closedRegistration = try {
            WinUITextToolbarClosedRegistration(flyout.closed.add(closedHandler), closedHandler)
        } catch (throwable: Throwable) {
            clickRegistrations.forEach(::clearClickRegistration)
            throw throwable
        }
        return NativeMenu(flyout, clickRegistrations, closedRegistration)
    }

    private fun showNativeMenu(
        host: FrameworkElement,
        flyout: MenuFlyout,
        menu: WinUITextToolbarMenu,
    ) {
        if (currentMenu?.nativeMenu == flyout) {
            try {
                System.err.println(
                    "WinUIContextMenu.showAt source=textToolbar " +
                        "root=${host::class.simpleName} " +
                        "loaded=${runCatching { host.isLoaded }.getOrNull()} " +
                        "xamlRoot=${runCatching { host.xamlRoot != null }.getOrNull()} " +
                        "size=${runCatching { host.actualWidth }.getOrNull()}x" +
                        "${runCatching { host.actualHeight }.getOrNull()} " +
                        "density=${densityProvider().density} " +
                        "rect=${menu.rect} " +
                        "items=${menu.itemLabels.size}"
                )
                val options = FlyoutShowOptions().also { showOptions ->
                    showOptions.position = menu.rect.toWinUITextToolbarXamlPoint(
                        densityProvider().density
                    )
                }
                flyout.showAt(host, options)
            } catch (_: Throwable) {
                if (currentMenu?.nativeMenu == flyout) {
                    currentMenu = null
                    clearNativeRegistrations(menu)
                }
            }
        }
    }

    private fun clearNativeRegistrations(menu: WinUITextToolbarMenu) {
        menu.clickRegistrations.forEach(::clearClickRegistration)
        val closedRegistration = menu.closedRegistration
        if (closedRegistration != null) {
            runCatching {
                menu.nativeMenu?.closed?.remove(closedRegistration.token)
            }
        }
    }

    private fun clearClickRegistration(registration: WinUITextToolbarClickRegistration) {
        runCatching {
            registration.item.click.remove(registration.token)
        }
    }
}

internal data class WinUITextToolbarMenu(
    val rect: Rect,
    val onCopyRequested: (() -> Unit)?,
    val onPasteRequested: (() -> Unit)?,
    val onCutRequested: (() -> Unit)?,
    val onSelectAllRequested: (() -> Unit)?,
    val onAutofillRequested: (() -> Unit)?,
    val itemLabels: List<String>,
    val nativeMenu: MenuFlyout?,
    val clickRegistrations: List<WinUITextToolbarClickRegistration>,
    val closedRegistration: WinUITextToolbarClosedRegistration?,
)

internal data class WinUITextToolbarClickRegistration(
    val item: MenuFlyoutItem,
    val token: EventRegistrationToken,
    val handler: RoutedEventHandler,
)

internal data class WinUITextToolbarClosedRegistration(
    val token: EventRegistrationToken,
    val handler: EventHandlerCallback<Any?>,
)

private data class WinUITextToolbarRequest(
    val label: String,
    val callback: () -> Unit,
)

private data class NativeMenu(
    val flyout: MenuFlyout,
    val clickRegistrations: List<WinUITextToolbarClickRegistration>,
    val closedRegistration: WinUITextToolbarClosedRegistration,
)

private fun MutableList<WinUITextToolbarRequest>.addRequest(label: String, callback: (() -> Unit)?) {
    if (callback != null) {
        add(WinUITextToolbarRequest(label, callback))
    }
}
