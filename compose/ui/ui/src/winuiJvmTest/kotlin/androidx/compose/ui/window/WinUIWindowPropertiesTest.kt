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

import androidx.compose.ui.ComposeFeatureFlags
import androidx.compose.ui.LayerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WinUIWindowPropertiesTest {
    @Test
    fun popupPropertiesUseValueEquality() {
        val first = PopupProperties(
            focusable = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false,
            usePlatformDefaultWidth = true,
        )
        val second = PopupProperties(
            focusable = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false,
            usePlatformDefaultWidth = true,
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun popupPropertiesCompareAllPublicFields() {
        val baseline = PopupProperties()

        assertNotEquals(baseline, PopupProperties(focusable = true))
        assertNotEquals(baseline, PopupProperties(dismissOnBackPress = false))
        assertNotEquals(baseline, PopupProperties(dismissOnClickOutside = false))
        assertNotEquals(baseline, PopupProperties(clippingEnabled = false))
        assertNotEquals(baseline, PopupProperties(usePlatformDefaultWidth = true))
    }

    @Test
    fun popupPropertiesFollowLayerFeatureFlag() {
        ComposeFeatureFlags.layerType.withOverride(LayerType.OnWindow) {
            assertEquals(LayerType.OnWindow, PopupProperties().layerType)
        }
        ComposeFeatureFlags.layerType.withOverride(LayerType.OnSameCanvas) {
            assertEquals(LayerType.OnSameCanvas, PopupProperties().layerType)
        }
    }

    @Test
    fun dialogPropertiesUseValueEquality() {
        val first = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        )
        val second = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun dialogPropertiesCompareAllPublicFields() {
        val baseline = DialogProperties()

        assertNotEquals(baseline, DialogProperties(dismissOnBackPress = false))
        assertNotEquals(baseline, DialogProperties(dismissOnClickOutside = false))
        assertNotEquals(baseline, DialogProperties(usePlatformDefaultWidth = false))
    }

    @Test
    fun dialogDefaultWidthPropertyIsExplicit() {
        assertTrue(DialogProperties().usePlatformDefaultWidth)
        assertFalse(DialogProperties(usePlatformDefaultWidth = false).usePlatformDefaultWidth)
    }
}
