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

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositeKeyHashCode
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * Calls [onBack] on a back event (Esc on Windows) while [enabled], before the handlers registered
 * earlier, as the Skiko popups and dialogs do (`OnBackClickEventHandler`).
 */
@OptIn(InternalComposeApi::class)
@Composable
internal fun WinUIBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val compositeKey = currentCompositeKeyHashCode
    val handler = remember(compositeKey) {
        WinUIOnBackClickEventHandler(compositeKey) { currentOnBack() }
    }
    SideEffect {
        handler.backClickIsEnabled = enabled
    }
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
        ?: return
    DisposableEffect(dispatcher, handler) {
        dispatcher.addHandler(handler)
        onDispose { handler.remove() }
    }
}

private class WinUIOnBackClickEventHandler(
    compositeKey: CompositeKeyHashCode,
    private val onBack: () -> Unit,
) : NavigationEventHandler<NavigationEventInfo>(
    initialInfo = BackClickHandlerInfo(compositeKey),
    isBackEnabled = true,
) {
    private var isProgressEvent = false
    var backClickIsEnabled: Boolean = true

    override fun onBackProgressed(event: NavigationEvent) {
        isProgressEvent = true
    }

    override fun onBackCompleted() {
        if (!isProgressEvent && backClickIsEnabled) {
            onBack()
        }
        isProgressEvent = false
    }

    override fun onBackCancelled() {
        isProgressEvent = false
    }
}

private data class BackClickHandlerInfo(
    val compositeKey: CompositeKeyHashCode,
) : NavigationEventInfo()
