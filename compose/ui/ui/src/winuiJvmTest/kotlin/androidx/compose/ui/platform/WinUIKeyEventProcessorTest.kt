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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import windows.system.VirtualKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinUIKeyEventProcessorTest {
    @Test
    fun matchesKeyEventsFromComposeRenderHostDescendants() {
        val renderHost = FakeKeyEventSource(parent = null)
        val swapChainPanel = FakeKeyEventSource(parent = renderHost)

        val matches = isComposeKeyEventSubtreeSource(
            source = swapChainPanel,
            subtreeSources = listOf(renderHost),
            parentOf = { source -> source.parent },
            sameIdentity = { first, second -> first === second },
        )

        assertEquals(true, matches)
    }

    @Test
    fun doesNotMatchKeyEventsOutsideComposeRenderHostSubtree() {
        val renderHost = FakeKeyEventSource(parent = null)
        val nativeChild = FakeKeyEventSource(parent = null)

        val matches = isComposeKeyEventSubtreeSource(
            source = nativeChild,
            subtreeSources = listOf(renderHost),
            parentOf = { source -> source.parent },
            sameIdentity = { first, second -> first === second },
        )

        assertEquals(false, matches)
    }

    @Test
    fun returnsFalseWhenKeyEventSourceParentLookupFails() {
        val renderHost = FakeKeyEventSource(parent = null)
        val source = FakeKeyEventSource(parent = renderHost)

        val matches = isComposeKeyEventSubtreeSource(
            source = source,
            subtreeSources = listOf(renderHost),
            parentOf = { error("parent lookup failed") },
            sameIdentity = { first, second -> first === second },
        )

        assertEquals(false, matches)
    }

    @Test
    fun returnsFalseWhenKeyEventSourceIdentityCheckFails() {
        val renderHost = FakeKeyEventSource(parent = null)
        val source = FakeKeyEventSource(parent = renderHost)

        val matches = isComposeKeyEventSubtreeSource(
            source = source,
            subtreeSources = listOf(renderHost),
            parentOf = { it.parent },
            sameIdentity = { _, _ -> error("identity check failed") },
        )

        assertEquals(false, matches)
    }

    @Test
    fun skipsAlreadyHandledNativeEvents() {
        val processor = WinUIKeyEventProcessor()
        var dispatchCount = 0

        val handled = processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.A,
            isHandled = true,
            nativeEvent = null,
        ) {
            dispatchCount += 1
            true
        }

        assertNull(handled)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun dispatchesUnhandledNativeEvents() {
        val processor = WinUIKeyEventProcessor()
        val events = mutableListOf<KeyEvent>()

        val handled = processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.A,
            isHandled = false,
            nativeEvent = null,
        ) {
            events += it
            true
        }

        assertEquals(true, handled)
        assertEquals(Key.A, events.single().key)
        assertEquals(KeyEventType.KeyDown, events.single().type)
    }

    @Test
    fun keyDownDoesNotSynthesizeTextCodePointsFromVirtualKeys() {
        val processor = WinUIKeyEventProcessor()
        val events = mutableListOf<KeyEvent>()

        listOf(
            VirtualKey.A,
            VirtualKey.Number1,
            VirtualKey.Space,
        ).forEach { key ->
            processor.process(
                eventType = KeyEventType.KeyDown,
                key = key,
                isHandled = false,
                nativeEvent = null,
            ) {
                events += it
                false
            }
        }

        assertEquals(listOf(0, 0, 0), events.map { it.utf16CodePoint })
    }

    @Test
    fun skipsNativeChildEventsWithoutUpdatingModifierState() {
        val processor = WinUIKeyEventProcessor()
        val events = mutableListOf<KeyEvent>()

        val skipped = processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.Control,
            isHandled = false,
            nativeEvent = null,
            shouldDispatchEvent = { false },
        ) {
            events += it
            true
        }
        val handled = processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.A,
            isHandled = false,
            nativeEvent = null,
            shouldDispatchEvent = { true },
        ) {
            events += it
            true
        }

        assertNull(skipped)
        assertEquals(true, handled)
        assertEquals(1, events.size)
        assertEquals(Key.A, events.single().key)
        assertEquals(false, events.single().isCtrlPressed)
    }

    @Test
    fun tracksModifierStateAcrossKeyEvents() {
        val processor = WinUIKeyEventProcessor()
        val events = mutableListOf<KeyEvent>()

        processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.Control,
            isHandled = false,
            nativeEvent = null,
        ) {
            events += it
            false
        }
        processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.A,
            isHandled = false,
            nativeEvent = null,
        ) {
            events += it
            false
        }
        processor.process(
            eventType = KeyEventType.KeyUp,
            key = VirtualKey.Control,
            isHandled = false,
            nativeEvent = null,
        ) {
            events += it
            false
        }
        processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.B,
            isHandled = false,
            nativeEvent = null,
        ) {
            events += it
            false
        }

        assertTrue(events[1].isCtrlPressed)
        assertEquals(Key.A, events[1].key)
        assertTrue(events[2].isCtrlPressed)
        assertEquals(Key.CtrlLeft, events[2].key)
        assertEquals(Key.B, events[3].key)
        assertEquals(false, events[3].isCtrlPressed)
    }

}

private class FakeKeyEventSource(
    val parent: FakeKeyEventSource?,
)
