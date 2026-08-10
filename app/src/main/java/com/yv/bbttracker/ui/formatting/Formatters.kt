package com.yv.bbttracker.ui.formatting

import com.yv.bbttracker.domain.validation.TemperatureValidator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object Formatters {
    private val numericDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun date(date: LocalDate): String = numericDate.format(date)

    /** Keeps a numeric date range in chronological order inside RTL text. */
    fun dateRange(start: LocalDate, endInclusive: LocalDate): String =
        if (start == endInclusive) "\u2066${date(start)}\u2069"
        else "\u2066${date(start)}–${date(endInclusive)}\u2069"

    fun dateRange(range: ClosedRange<LocalDate>): String = dateRange(range.start, range.endInclusive)

    /** A short, human-relative label for a single date ("today"/"tomorrow"/"in N days"). */
    fun relativeDayLabel(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> "היום"
            days == 1L -> "מחר"
            days == -1L -> "אתמול"
            days > 1L -> "\u2066בעוד $days ימים\u2069"
            else -> "\u2066לפני ${-days} ימים\u2069"
        }
    }

    /** A compact relative framing of a date range; falls back to absolute dates when the range straddles today. */
    fun relativeDateRange(range: ClosedRange<LocalDate>, today: LocalDate): String {
        val start = range.start
        val end = range.endInclusive
        if (start == end) return relativeDayLabel(start, today)
        val daysToStart = ChronoUnit.DAYS.between(today, start)
        val daysToEnd = ChronoUnit.DAYS.between(today, end)
        return when {
            daysToStart >= 0 && daysToEnd >= 0 -> "\u2066בעוד $daysToStart–$daysToEnd ימים\u2069"
            daysToStart <= 0 && daysToEnd <= 0 -> "\u2066לפני ${-daysToEnd}–${-daysToStart} ימים\u2069"
            else -> dateRange(range)
        }
    }

    fun longDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

    fun time(epochMillis: Long, zoneId: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(DateTimeFormatter.ofPattern("HH:mm"))

    fun temperature(centiCelsius: Int): String = TemperatureValidator.format(centiCelsius)

    fun decimal(value: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", value)
}
