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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WinUIProjectionLifetimeTest {
    @Test
    fun ownedResourceClosesOnFailure() {
        val closed = mutableListOf<String>()

        assertFailsWith<IllegalStateException> {
            WinUIOwnedResource("point") { closed += it }.use { error("conversion failed") }
        }

        assertEquals(listOf("point"), closed)
    }

    @Test
    fun releasedResourceIsNotClosedByOriginalOwner() {
        val closed = mutableListOf<String>()

        WinUIOwnedResource("point") { closed += it }
            .use { owner -> assertEquals("point", owner.releaseOwnership()) }

        assertEquals(emptyList(), closed)
    }

    @Test
    fun ownedListClosesFetchedElementsAndCollectionOnFailure() {
        val closed = mutableListOf<String>()

        val failure =
            assertFailsWith<IllegalStateException> {
                mapWinUIOwnedList(
                    values = listOf("one", "two", "three"),
                    dropLast = 0,
                    closeValues = { closed += "list" },
                    closeValue = { closed += it },
                ) { value ->
                    if (value == "two") error("bad point")
                    value
                }
            }

        assertEquals("bad point", failure.message)
        assertEquals(listOf("one", "two", "list"), closed)
    }

    @Test
    fun ownedListClosesCollectionWhenReadingSizeFails() {
        val closed = mutableListOf<String>()
        val values =
            object : AbstractList<String>() {
                override val size: Int
                    get() = error("bad size")

                override fun get(index: Int): String = error("unexpected element read")
            }

        val failure =
            assertFailsWith<IllegalStateException> {
                mapWinUIOwnedList(
                    values = values,
                    dropLast = 1,
                    closeValues = { closed += "list" },
                    closeValue = { closed += it },
                ) {
                    it
                }
            }

        assertEquals("bad size", failure.message)
        assertEquals(listOf("list"), closed)
    }
}
