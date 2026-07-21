/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.foundation.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.HandlePopup
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.OffsetProvider
import androidx.compose.foundation.text.selection.SelectionHandleAnchor
import androidx.compose.foundation.text.selection.SelectionHandleInfo
import androidx.compose.foundation.text.selection.SelectionHandleInfoKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

private const val Sqrt2 = 1.41421356f
private val WinUICursorHandleHeight = 25.dp
private val WinUICursorHandleWidth = WinUICursorHandleHeight * 2f / (1 + Sqrt2)

@Composable
internal actual fun CursorHandle(
    offsetProvider: OffsetProvider,
    modifier: Modifier,
    minTouchTargetSize: DpSize,
) {
    val finalModifier = modifier.semantics {
        this[SelectionHandleInfoKey] = winUICursorHandleInfo(offsetProvider.provide())
    }
    HandlePopup(
        positionProvider = offsetProvider,
        handleReferencePoint = Alignment.TopCenter,
    ) {
        if (minTouchTargetSize.isSpecified) {
            Box(
                modifier = finalModifier.requiredSizeIn(
                    minWidth = minTouchTargetSize.width,
                    minHeight = minTouchTargetSize.height,
                ),
                contentAlignment = Alignment.TopCenter,
            ) {
                DefaultWinUICursorHandle()
            }
        } else {
            DefaultWinUICursorHandle(finalModifier)
        }
    }
}

internal fun winUICursorHandleInfo(position: Offset): SelectionHandleInfo =
    SelectionHandleInfo(
        handle = Handle.Cursor,
        position = position,
        anchor = SelectionHandleAnchor.Middle,
        visible = position.isSpecified,
    )

@Composable
private fun DefaultWinUICursorHandle(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .size(WinUICursorHandleWidth, WinUICursorHandleHeight)
            .drawWinUICursorHandle(LocalTextSelectionColors.current.handleColor)
    )
}

private fun Modifier.drawWinUICursorHandle(handleColor: Color): Modifier = drawWithCache {
    val radius = size.width / 2f
    onDrawWithContent {
        drawContent()
        withTransform({
            translate(left = radius)
            rotate(degrees = 45f, pivot = Offset.Zero)
        }) {
            drawRect(
                color = handleColor,
                topLeft = Offset.Zero,
                size = Size(radius, radius),
            )
            drawCircle(
                color = handleColor,
                radius = radius,
                center = Offset(radius, radius),
            )
        }
    }
}
