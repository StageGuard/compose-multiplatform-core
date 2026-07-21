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
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed as isPointerCtrlPressed
import windows.system.VirtualKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinUIKeyEventProcessorTest {
    @Test
    fun mapsWindowsPunctuationVirtualKeys() {
        val mappings = mapOf(
            0xBA to Key.Semicolon,
            0xBB to Key.Equals,
            0xBC to Key.Comma,
            0xBD to Key.Minus,
            0xBE to Key.Period,
            0xBF to Key.Slash,
            0xC0 to Key.Grave,
            0xDB to Key.LeftBracket,
            0xDC to Key.Backslash,
            0xDD to Key.RightBracket,
            0xDE to Key.Apostrophe,
        )

        mappings.forEach { (virtualKey, expectedKey) ->
            val events = mutableListOf<KeyEvent>()
            WinUIKeyEventProcessor().process(
                eventType = KeyEventType.KeyDown,
                key = VirtualKey(virtualKey),
                isHandled = false,
                nativeEvent = null,
            ) {
                events += it
                false
            }
            assertEquals(expectedKey, events.single().key, "VK 0x${virtualKey.toString(16)}")
        }
    }

    @Test
    fun mapsWindowsFunctionBrowserMediaAndVolumeVirtualKeys() {
        val mappings = mapOf(
            0xA6 to Key.Back,
            0xA7 to Key.Forward,
            0xA8 to Key.Refresh,
            0xAA to Key.Search,
            0xAD to Key.VolumeMute,
            0xAE to Key.VolumeDown,
            0xAF to Key.VolumeUp,
            0xB0 to Key.MediaNext,
            0xB1 to Key.MediaPrevious,
            0xB2 to Key.MediaStop,
            0xB3 to Key.MediaPlayPause,
            0xB4 to Key.Envelope,
            0xB5 to Key.Music,
            VirtualKey.NavigationCancel.abiValue to Key.Back,
        )

        mappings.forEach { (virtualKey, expectedKey) ->
            val events = mutableListOf<KeyEvent>()
            val key = VirtualKey(virtualKey)
            WinUIKeyEventProcessor().process(
                eventType = KeyEventType.KeyDown,
                key = key,
                isHandled = false,
                nativeEvent = null,
            ) {
                events += it
                false
            }
            assertEquals(expectedKey, events.single().key, "VK $virtualKey")
        }
    }

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

    @Test
    fun resetClearsPressedModifierState() {
        val processor = WinUIKeyEventProcessor()
        processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.Control,
            isHandled = false,
            nativeEvent = null,
        ) { false }

        processor.reset()
        val events = mutableListOf<KeyEvent>()
        processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.A,
            isHandled = false,
            nativeEvent = null,
        ) {
            events += it
            false
        }

        assertEquals(false, events.single().isCtrlPressed)
    }

    @Test
    fun releasingOneControlKeyKeepsOtherControlKeyPressed() {
        val processor = WinUIKeyEventProcessor()

        processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.LeftControl,
            isHandled = false,
            nativeEvent = null,
        ) { false }
        processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.RightControl,
            isHandled = false,
            nativeEvent = null,
        ) { false }
        processor.process(
            eventType = KeyEventType.KeyUp,
            key = VirtualKey.LeftControl,
            isHandled = false,
            nativeEvent = null,
        ) { false }

        val events = mutableListOf<KeyEvent>()
        processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.A,
            isHandled = false,
            nativeEvent = null,
        ) {
            events += it
            false
        }

        assertTrue(events.single().isCtrlPressed)
    }

    @Test
    fun keyUpPublishesReleasedModifierStateAfterDispatch() {
        val processor = WinUIKeyEventProcessor()
        val modifierUpdates = mutableListOf<Boolean>()

        processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.Control,
            isHandled = false,
            nativeEvent = null,
            onKeyboardModifiersChanged = { modifiers ->
                modifierUpdates += modifiers.isPointerCtrlPressed
            },
        ) { false }
        processor.process(
            eventType = KeyEventType.KeyUp,
            key = VirtualKey.Control,
            isHandled = false,
            nativeEvent = null,
            onKeyboardModifiersChanged = { modifiers ->
                modifierUpdates += modifiers.isPointerCtrlPressed
            },
        ) { false }

        assertEquals(listOf(true, true, false), modifierUpdates)
    }

    @Test
    fun pointerModifiersReplaceStaleTrackedModifierState() {
        val modifierState = WinUIKeyboardModifierState()
        val processor = WinUIKeyEventProcessor(modifierState)

        processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.Control,
            isHandled = false,
            nativeEvent = null,
        ) { false }
        modifierState.reconcilePressed(PointerKeyboardModifiers())

        val events = mutableListOf<KeyEvent>()
        processor.process(
            eventType = KeyEventType.KeyDown,
            key = VirtualKey.A,
            isHandled = false,
            nativeEvent = null,
        ) {
            events += it
            false
        }

        assertEquals(false, events.single().isCtrlPressed)
    }

}

private class FakeKeyEventSource(
    val parent: FakeKeyEventSource?,
)
