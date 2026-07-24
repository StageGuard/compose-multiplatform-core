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

package androidx.compose.mpp.demo.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun DragAndDropExample() {
    var status by remember { mutableStateOf("Drop native WinUI content here") }
    var isActive by remember { mutableStateOf(false) }
    var isInside by remember { mutableStateOf(false) }
    val target = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                isActive = true
                status = "Drag started"
            }

            override fun onEntered(event: DragAndDropEvent) {
                isInside = true
                status = "Drag entered"
            }

            override fun onMoved(event: DragAndDropEvent) {
                status = "Drag moved"
            }

            override fun onExited(event: DragAndDropEvent) {
                isInside = false
                status = "Drag exited"
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                status = "Dropped ${event.nativeEvent?.let { it::class.simpleName } ?: "content"}"
                return true
            }

            override fun onEnded(event: DragAndDropEvent) {
                isActive = false
                isInside = false
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .size(280.dp)
                .background(if (isActive) Color(0xFFDFF5EA) else Color(0xFFECEFF1))
                .border(BorderStroke(if (isInside) 4.dp else 1.dp, Color(0xFF2E7D32)))
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { true },
                    target = target,
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(status)
        }
    }
}
