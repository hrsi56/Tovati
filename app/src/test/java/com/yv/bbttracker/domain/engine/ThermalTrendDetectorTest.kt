package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.MeasurementSite
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalTrendDetectorTest {
    private val start = LocalDate.of(2026, 8, 1)

    @Test
    fun `three observed highs can support a limited trend across one missing day`() {
        val firstHigh = start.plusDays(6)
        val result = ThermalTrendDetector.detect(
            measurements = baseline() + listOf(
                measurement(firstHigh, 3620),
                measurement(firstHigh.plusDays(2), 3621),
                measurement(firstHigh.plusDays(3), 3622),
            ),
            defaultMeasurementSite = MeasurementSite.ORAL,
            asOfDate = firstHigh.plusDays(3),
        )

        assertEquals(firstHigh, result?.firstHighDate)
        assertEquals(1, result?.missingDaysWithinHighWindow)
        assertEquals(3, result?.observedHighDates?.size)
        assertTrue(firstHigh.plusDays(1) !in requireNotNull(result).observedHighDates)
    }

    @Test
    fun `two measured highs plus a missing day are not promoted to a trend`() {
        val firstHigh = start.plusDays(6)
        val result = ThermalTrendDetector.detect(
            measurements = baseline() + listOf(
                measurement(firstHigh, 3620),
                measurement(firstHigh.plusDays(2), 3622),
            ),
            defaultMeasurementSite = MeasurementSite.ORAL,
            asOfDate = firstHigh.plusDays(3),
        )

        assertNull(result)
    }

    @Test
    fun `one isolated spike in otherwise low temperatures is not a thermal trend`() {
        val afterBaseline = listOf(
            measurement(start.plusDays(6), 3630),
            measurement(start.plusDays(7), 3601),
            measurement(start.plusDays(8), 3602),
            measurement(start.plusDays(9), 3600),
        )

        assertNull(
            ThermalTrendDetector.detect(
                measurements = baseline() + afterBaseline,
                defaultMeasurementSite = MeasurementSite.ORAL,
                asOfDate = start.plusDays(9),
            ),
        )
    }

    private fun baseline() = (0L..5L).map { offset ->
        measurement(start.plusDays(offset), 3600 + (offset % 2L).toInt())
    }
}
