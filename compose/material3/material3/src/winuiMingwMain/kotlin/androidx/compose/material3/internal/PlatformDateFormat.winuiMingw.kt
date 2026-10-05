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

@file:OptIn(ExperimentalTime::class)

package androidx.compose.material3.internal

import androidx.compose.material3.CalendarLocale
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import windows.globalization.Calendar
import windows.globalization.datetimeformatting.DateTimeFormatter
import windows.system.userprofile.GlobalizationPreferences

@OptIn(ExperimentalMaterial3Api::class)
internal actual class PlatformDateFormat actual constructor(private val locale: CalendarLocale) {
    private val languages = listOf(locale.toLanguageTag())

    actual val firstDayOfWeek: Int
        get() = GlobalizationPreferences.weekStartsOn.abiValue.let { day ->
            if (day == 0) 7 else day
        }

    actual fun formatWithPattern(
        utcTimeMillis: Long,
        pattern: String,
        cache: MutableMap<String, Any>,
    ): String = calendarAt(utcTimeMillis).formatPattern(pattern)

    actual fun formatWithSkeleton(
        utcTimeMillis: Long,
        skeleton: String,
        cache: MutableMap<String, Any>,
    ): String {
        val template = when (skeleton) {
            DatePickerDefaults.YearMonthSkeleton -> "month.full year"
            DatePickerDefaults.YearAbbrMonthDaySkeleton -> "month.abbreviated day year"
            DatePickerDefaults.YearMonthWeekdayDaySkeleton ->
                "dayofweek.full month.full day year"
            else -> skeleton.toWinRTTemplate()
        }
        val cacheKey = "winrt:$template:${locale.toLanguageTag()}"
        val formatter = cache.getOrPut(cacheKey) {
            DateTimeFormatter(template, languages)
        } as DateTimeFormatter
        // The formatter marks the direction of every field with U+200E or U+200F, which the
        // formatters of the other targets do not and which take room in a line of text.
        return formatter.format(Instant.fromEpochMilliseconds(utcTimeMillis), UtcTimeZone)
            .filterNot { it == '‎' || it == '‏' }
    }

    actual fun parse(
        date: String,
        pattern: String,
        locale: CalendarLocale,
        cache: MutableMap<String, Any>,
    ): CalendarDate? {
        val fields = Regex("[dMy]+").findAll(pattern).toList()
        if (fields.isEmpty()) return null
        val digits = date.mapNotNull(Char::digitToIntOrNull).joinToString(separator = "")
        if (digits.length != fields.sumOf { it.value.length }) return null

        var offset = 0
        var year: Int? = null
        var month: Int? = null
        var day: Int? = null
        fields.forEach { field ->
            val width = field.value.length
            val value = digits.substring(offset, offset + width).toIntOrNull() ?: return null
            offset += width
            when (field.value.first()) {
                'y' -> year = if (width == 2) 2000 + value else value
                'M' -> month = value
                'd' -> day = value
            }
        }

        return runCatching {
            val localDate = LocalDate(
                year = requireNotNull(year),
                month = requireNotNull(month),
                day = requireNotNull(day),
            )
            CalendarDate(
                year = localDate.year,
                month = localDate.month.number,
                dayOfMonth = localDate.day,
                utcTimeMillis = localDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
            )
        }.getOrNull()
    }

    actual fun getDateInputFormat(): DateInputFormat {
        val pattern = DateTimeFormatter("shortdate", languages).patterns.firstOrNull().orEmpty()
        val fields = Regex("\\{(year|month|day)[^}]*}").findAll(pattern).toList()
        if (fields.size < 3) return DateInputFormat("yyyy/MM/dd", '/')

        val delimiter = fields.zipWithNext()
            .asSequence()
            .map { (left, right) -> pattern.substring(left.range.last + 1, right.range.first) }
            .flatMap(String::asSequence)
            .firstOrNull { it == '/' || it == '-' || it == '.' }
            ?: '/'
        val normalized = fields.joinToString(separator = delimiter.toString()) { field ->
            when (field.groupValues[1]) {
                "year" -> "yyyy"
                "month" -> "MM"
                else -> "dd"
            }
        }
        return DateInputFormat(normalized, delimiter)
    }

    // The second name is the narrow one, as on the other targets ("M" for Monday). The shortest
    // abbreviation of WinRT has two letters in many languages ("Mo"), and its first letter is
    // the narrow name there.
    actual val weekdayNames: List<Pair<String, String>>
        get() = calendarAt(KnownMondayUtcMillis).let { calendar ->
            List(DaysInWeek) {
                val names = calendar.dayOfWeekAsString() to
                    calendar.dayOfWeekAsString(1).firstCodePoint()
                calendar.addDays(1)
                names
            }
        }

    actual fun is24HourFormat(): Boolean =
        GlobalizationPreferences.clocks.any { it.contains("24", ignoreCase = true) }

    private fun calendarAt(utcTimeMillis: Long): Calendar = Calendar(languages).apply {
        changeTimeZone(UtcTimeZone)
        setDateTime(Instant.fromEpochMilliseconds(utcTimeMillis))
    }
}

private fun Calendar.formatPattern(pattern: String): String = buildString {
    var index = 0
    var quoted = false
    while (index < pattern.length) {
        val char = pattern[index]
        if (char == '\'') {
            if (index + 1 < pattern.length && pattern[index + 1] == '\'') {
                append('\'')
                index += 2
            } else {
                quoted = !quoted
                index++
            }
            continue
        }
        if (quoted || !char.isLetter()) {
            append(char)
            index++
            continue
        }

        val end = pattern.indexOfFirstFrom(index + 1) { it != char }
        val length = (if (end < 0) pattern.length else end) - index
        append(
            when (char) {
                'y' -> if (length == 2) yearAsTruncatedString(2) else yearAsPaddedString(length)
                'M' -> when {
                    length >= 4 -> monthAsString()
                    length == 3 -> monthAsString(3)
                    length == 2 -> monthAsPaddedNumericString(2)
                    else -> monthAsNumericString()
                }
                'L' -> when {
                    length >= 4 -> monthAsSoloString()
                    length == 3 -> monthAsSoloString(3)
                    length == 2 -> monthAsPaddedNumericString(2)
                    else -> monthAsNumericString()
                }
                'd' -> if (length >= 2) dayAsPaddedString(length) else dayAsString()
                'E', 'e', 'c' ->
                    if (length >= 4) dayOfWeekAsString() else dayOfWeekAsString(length)
                else -> pattern.substring(index, index + length)
            },
        )
        index += length
    }
}

private fun String.toWinRTTemplate(): String = buildList {
    if ('E' in this@toWinRTTemplate) add("dayofweek.full")
    when {
        "MMMM" in this@toWinRTTemplate -> add("month.full")
        "MMM" in this@toWinRTTemplate -> add("month.abbreviated")
        'M' in this@toWinRTTemplate -> add("month.integer")
    }
    if ('d' in this@toWinRTTemplate) add("day")
    if ('y' in this@toWinRTTemplate) add("year")
}.joinToString(separator = " ").ifEmpty { "shortdate" }

private inline fun String.indexOfFirstFrom(startIndex: Int, predicate: (Char) -> Boolean): Int {
    for (index in startIndex until length) {
        if (predicate(this[index])) return index
    }
    return -1
}

private fun String.firstCodePoint(): String =
    if (length >= 2 && this[0].isHighSurrogate() && this[1].isLowSurrogate()) take(2) else take(1)

private const val UtcTimeZone = "UTC"
private const val KnownMondayUtcMillis = 1704067200000L // 2024-01-01
