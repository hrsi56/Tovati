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
}
