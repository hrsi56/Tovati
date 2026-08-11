package com.yv.bbttracker.ui.formatting

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FormattersTest {
    @Test
    fun `single day range is rendered as one date`() {
        val date = LocalDate.of(2026, 7, 1)

        val formatted = Formatters.dateRange(date..date)

        assertEquals("\u206601/07/2026\u2069", formatted)
        assertFalse('–' in formatted)
    }

    @Test
    fun `two day range keeps both dates in chronological order`() {
        val start = LocalDate.of(2026, 7, 17)

        assertEquals(
            "\u206617/07/2026–18/07/2026\u2069",
            Formatters.dateRange(start..start.plusDays(1)),
        )
    }

    @Test
    fun `single relative dates use semantic localized tokens`() {
        val today = LocalDate.of(2026, 8, 10)

        assertEquals(RelativeDateText.Yesterday, Formatters.relativeDayText(today.minusDays(1), today))
        assertEquals(RelativeDateText.Today, Formatters.relativeDayText(today, today))
        assertEquals(RelativeDateText.Tomorrow, Formatters.relativeDayText(today.plusDays(1), today))
        assertEquals(RelativeDateText.InDays(4), Formatters.relativeDayText(today.plusDays(4), today))
        assertEquals(RelativeDateText.DaysAgo(3), Formatters.relativeDayText(today.minusDays(3), today))
    }

    @Test
    fun `today through tomorrow never becomes a zero based numeric range`() {
        val today = LocalDate.of(2026, 8, 10)

        assertEquals(
            RelativeDateText.TodayThroughTomorrow,
            Formatters.relativeDateRangeText(today..today.plusDays(1), today),
        )
    }

    @Test
    fun `yesterday through today uses a natural semantic range`() {
        val today = LocalDate.of(2026, 8, 10)

        assertEquals(
            RelativeDateText.YesterdayThroughToday,
            Formatters.relativeDateRangeText(today.minusDays(1)..today, today),
        )
    }

    @Test
    fun `future range beginning tomorrow names tomorrow`() {
        val today = LocalDate.of(2026, 8, 10)

        assertEquals(
            RelativeDateText.TomorrowThroughFuture(3),
            Formatters.relativeDateRangeText(today.plusDays(1)..today.plusDays(3), today),
        )
    }

    @Test
    fun `past range ending yesterday names yesterday`() {
        val today = LocalDate.of(2026, 8, 10)

        assertEquals(
            RelativeDateText.PastThroughYesterday(3),
            Formatters.relativeDateRangeText(today.minusDays(3)..today.minusDays(1), today),
        )
    }

    @Test
    fun `range crossing from past to future falls back to absolute dates`() {
        val today = LocalDate.of(2026, 8, 10)
        val start = today.minusDays(1)
        val end = today.plusDays(1)

        assertEquals(
            RelativeDateText.AbsoluteRange(start, end),
            Formatters.relativeDateRangeText(start..end, today),
        )
    }
}
