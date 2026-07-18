package com.yv.bbttracker.ui.formatting

import com.yv.bbttracker.domain.validation.TemperatureValidator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Formatters {
    private val numericDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun date(date: LocalDate): String = numericDate.format(date)

    /** Keeps a numeric date range in chronological order inside RTL text. */
    fun dateRange(start: LocalDate, endInclusive: LocalDate): String =
        if (start == endInclusive) "\u2066${date(start)}\u2069"
        else "\u2066${date(start)}–${date(endInclusive)}\u2069"

    fun dateRange(range: ClosedRange<LocalDate>): String = dateRange(range.start, range.endInclusive)

    fun longDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

    fun time(epochMillis: Long, zoneId: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(DateTimeFormatter.ofPattern("HH:mm"))

    fun temperature(centiCelsius: Int): String = TemperatureValidator.format(centiCelsius)

    fun decimal(value: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", value)
}
