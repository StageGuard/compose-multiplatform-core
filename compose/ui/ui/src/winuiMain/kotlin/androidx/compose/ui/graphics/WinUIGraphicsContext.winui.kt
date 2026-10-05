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

package androidx.compose.ui.graphics

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.setLightingInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize

@OptIn(InternalComposeUiApi::class)
internal object WinUIGraphicsContext : GraphicsContext {
    private var skiaGraphicsContext = SkiaGraphicsContext()

    val activeGraphicsLayersCount: Int
        get() = skiaGraphicsContext.activeGraphicsLayersCount

    override fun createGraphicsLayer(): GraphicsLayer =
        skiaGraphicsContext.createGraphicsLayer()

    override fun releaseGraphicsLayer(layer: GraphicsLayer) =
        skiaGraphicsContext.releaseGraphicsLayer(layer)

    /**
     * Places the light that elevation shadows are cast from, as the Skiko RootNodeOwner does. The
     * root is the window content, so it has no offset in the window.
     */
    fun setLightingInfo(density: Density, containerSize: IntSize) =
        skiaGraphicsContext.setLightingInfo(
            canvasOffset = Offset.Zero,
            density = density,
            containerSize = containerSize,
        )

    internal fun resetForTest() {
        skiaGraphicsContext.close()
        skiaGraphicsContext = SkiaGraphicsContext()
    }
}

