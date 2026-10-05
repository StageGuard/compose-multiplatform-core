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

import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Measures the content of a layer against the window and places it where [calculatePosition]
 * says, as `ComposeSceneLayerMeasurePolicy` of the Skiko targets does. The window has no system
 * bars on Windows, so there are no platform insets to apply.
 */
internal fun WinUIComposeLayerMeasurePolicy(
    usePlatformDefaultWidth: Boolean,
    calculatePosition: MeasureScope.(contentSize: IntSize) -> IntOffset,
) = MeasurePolicy { measurables, constraints ->
    val contentConstraints = if (usePlatformDefaultWidth) {
        constraints.constrain(platformDefaultConstraints(constraints))
    } else {
        constraints
    }
    val placeables = measurables.map { it.measure(contentConstraints) }
    val contentSize = IntSize(
        width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth,
        height = placeables.maxOfOrNull { it.height } ?: constraints.minHeight,
    )

    // When unconstrained, use content size as layout dimensions to allow the content's
    // preferred size to be measured
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else contentSize.width
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else contentSize.height
    layout(width, height) {
        val position = calculatePosition(contentSize)
        placeables.forEach { it.place(position.x, position.y) }
    }
}

internal fun clipPositionToWindow(
    position: IntOffset,
    contentSize: IntSize,
    windowSize: IntSize,
) = IntOffset(
    x = if (contentSize.width < windowSize.width) {
        position.x.coerceIn(0, windowSize.width - contentSize.width)
    } else 0,
    y = if (contentSize.height < windowSize.height) {
        position.y.coerceIn(0, windowSize.height - contentSize.height)
    } else 0,
)

private fun Density.platformDefaultConstraints(constraints: Constraints): Constraints =
    constraints.copy(
        maxWidth = min(preferredDialogWidth(constraints), constraints.maxWidth)
    )

// Ported from Android. See https://cs.android.com/search?q=abc_config_prefDialogWidth
private fun Density.preferredDialogWidth(constraints: Constraints): Int {
    val smallestWidth = min(constraints.maxWidth, constraints.maxHeight).toDp()
    return when {
        smallestWidth >= 600.dp -> 580.dp
        smallestWidth >= 480.dp -> 440.dp
        else -> 320.dp
    }.roundToPx()
}
