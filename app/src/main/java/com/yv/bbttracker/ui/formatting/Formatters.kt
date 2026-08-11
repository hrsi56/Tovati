package com.yv.bbttracker.ui.formatting

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yv.bbttracker.R
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
    @Composable
    fun relativeDayLabel(date: LocalDate, today: LocalDate): String =
        relativeDayText(date, today).localized()

    /** A compact relative framing of a date range; falls back to absolute dates when the range straddles today. */
    @Composable
    fun relativeDateRange(range: ClosedRange<LocalDate>, today: LocalDate): String =
        relativeDateRangeText(range, today).localized()

    internal fun relativeDayText(date: LocalDate, today: LocalDate): RelativeDateText {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> RelativeDateText.Today
            days == 1L -> RelativeDateText.Tomorrow
            days == -1L -> RelativeDateText.Yesterday
            days > 1L -> RelativeDateText.InDays(days)
            else -> RelativeDateText.DaysAgo(-days)
        }
    }

    internal fun relativeDateRangeText(
        range: ClosedRange<LocalDate>,
        today: LocalDate,
    ): RelativeDateText {
        val start = range.start
        val end = range.endInclusive
        if (start == end) return relativeDayText(start, today)
        if (start.isAfter(end)) return RelativeDateText.AbsoluteRange(start, end)

        val daysToStart = ChronoUnit.DAYS.between(today, start)
        val daysToEnd = ChronoUnit.DAYS.between(today, end)
        return when {
            daysToStart == 0L && daysToEnd == 1L -> RelativeDateText.TodayThroughTomorrow
            daysToStart == -1L && daysToEnd == 0L -> RelativeDateText.YesterdayThroughToday
            daysToStart == 0L && daysToEnd > 1L ->
                RelativeDateText.TodayThroughFuture(daysToEnd)
            daysToStart == 1L && daysToEnd > 1L ->
                RelativeDateText.TomorrowThroughFuture(daysToEnd)
            daysToStart > 1L && daysToEnd > 1L ->
                RelativeDateText.FutureRange(daysToStart, daysToEnd)
            daysToStart < -1L && daysToEnd == 0L ->
                RelativeDateText.PastThroughToday(-daysToStart)
            daysToStart < -1L && daysToEnd == -1L ->
                RelativeDateText.PastThroughYesterday(-daysToStart)
            daysToStart < 0L && daysToEnd < 0L ->
                RelativeDateText.PastRange(-daysToEnd, -daysToStart)
            else -> RelativeDateText.AbsoluteRange(start, end)
        }
    }

    @Composable
    private fun RelativeDateText.localized(): String = when (this) {
        RelativeDateText.Today -> stringResource(R.string.relative_date_today)
        RelativeDateText.Tomorrow -> stringResource(R.string.relative_date_tomorrow)
        RelativeDateText.Yesterday -> stringResource(R.string.relative_date_yesterday)
        is RelativeDateText.InDays -> stringResource(R.string.relative_date_in_days, days)
        is RelativeDateText.DaysAgo -> stringResource(R.string.relative_date_days_ago, days)
        RelativeDateText.TodayThroughTomorrow ->
            stringResource(R.string.relative_date_today_through_tomorrow)
        RelativeDateText.YesterdayThroughToday ->
            stringResource(R.string.relative_date_yesterday_through_today)
        is RelativeDateText.TodayThroughFuture ->
            stringResource(R.string.relative_date_today_through_future, endDays)
        is RelativeDateText.TomorrowThroughFuture ->
            stringResource(R.string.relative_date_tomorrow_through_future, endDays)
        is RelativeDateText.FutureRange ->
            stringResource(R.string.relative_date_future_range, startDays, endDays)
        is RelativeDateText.PastThroughToday ->
            stringResource(R.string.relative_date_past_through_today, startDaysAgo)
        is RelativeDateText.PastThroughYesterday ->
            stringResource(R.string.relative_date_past_through_yesterday, startDaysAgo)
        is RelativeDateText.PastRange ->
            stringResource(R.string.relative_date_past_range, recentDaysAgo, earlierDaysAgo)
        is RelativeDateText.AbsoluteRange -> dateRange(start, end)
    }

    fun longDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

    fun time(epochMillis: Long, zoneId: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(DateTimeFormatter.ofPattern("HH:mm"))

    fun temperature(centiCelsius: Int): String = TemperatureValidator.format(centiCelsius)

    fun decimal(value: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", value)
}

internal sealed interface RelativeDateText {
    data object Today : RelativeDateText
    data object Tomorrow : RelativeDateText
    data object Yesterday : RelativeDateText
    data class InDays(val days: Long) : RelativeDateText
    data class DaysAgo(val days: Long) : RelativeDateText
    data object TodayThroughTomorrow : RelativeDateText
    data object YesterdayThroughToday : RelativeDateText
    data class TodayThroughFuture(val endDays: Long) : RelativeDateText
    data class TomorrowThroughFuture(val endDays: Long) : RelativeDateText
    data class FutureRange(val startDays: Long, val endDays: Long) : RelativeDateText
    data class PastThroughToday(val startDaysAgo: Long) : RelativeDateText
    data class PastThroughYesterday(val startDaysAgo: Long) : RelativeDateText
    data class PastRange(val recentDaysAgo: Long, val earlierDaysAgo: Long) : RelativeDateText
    data class AbsoluteRange(val start: LocalDate, val end: LocalDate) : RelativeDateText
}
