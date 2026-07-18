package com.yv.bbttracker.domain.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodForecastCalculatorTest {
    private val currentCycleStart = LocalDate.of(2026, 6, 1)

    @Test
    fun `luteal stats need at least two plausible samples`() {
        val single = listOf(analyzedCycle(LocalDate.of(2026, 1, 1), length = 28, ovulationDay = 14))

        assertNull(PeriodForecastCalculator.lutealStats(emptyList()))
        assertNull(PeriodForecastCalculator.lutealStats(single))
    }

    @Test
    fun `luteal stats use median and mad of plausible samples`() {
        val cycles = listOf(
            analyzedCycle(LocalDate.of(2026, 1, 1), length = 28, ovulationDay = 14), // luteal 14
            analyzedCycle(LocalDate.of(2026, 2, 1), length = 30, ovulationDay = 18), // luteal 12
            analyzedCycle(LocalDate.of(2026, 3, 1), length = 29, ovulationDay = 16), // luteal 13
        )

        val stats = requireNotNull(PeriodForecastCalculator.lutealStats(cycles))

        assertEquals(13.0, stats.medianDays, 1e-9)
        assertEquals(1.0, stats.madDays, 1e-9)
        assertEquals(3, stats.sampleSize)
    }

    @Test
    fun `implausible luteal lengths are excluded from the stats`() {
        val cycles = listOf(
            analyzedCycle(LocalDate.of(2026, 1, 1), length = 28, ovulationDay = 14), // luteal 14
            analyzedCycle(LocalDate.of(2026, 2, 1), length = 28, ovulationDay = 26), // luteal 2
            analyzedCycle(LocalDate.of(2026, 3, 1), length = 40, ovulationDay = 10), // luteal 30
        )

        assertNull(PeriodForecastCalculator.lutealStats(cycles))
    }

    @Test
    fun `ovulation anchor with personal luteal takes precedence over cycle history`() {
        val cycles = listOf(
            analyzedCycle(LocalDate.of(2026, 1, 1), length = 28, ovulationDay = 14),
            analyzedCycle(LocalDate.of(2026, 2, 1), length = 28, ovulationDay = 14),
        )
        val ovulation = currentCycleStart.plusDays(19)..currentCycleStart.plusDays(21)

        val forecast = PeriodForecastCalculator.forecast(
            currentCycleStart = currentCycleStart,
            currentDate = currentCycleStart.plusDays(22),
            previousCycles = cycles,
            ovulationEstimate = ovulation,
        )

        assertEquals(PeriodForecastBasis.OVULATION_AND_PERSONAL_LUTEAL, forecast.basis)
        assertEquals(14, forecast.lutealDaysUsed)
        // Even though the calendar history says day 28, a late ovulation moves the forecast.
        assertEquals(
            currentCycleStart.plusDays(35)..currentCycleStart.plusDays(36),
            forecast.expectedStartRange,
        )
        assertFalse(forecast.isOverdue)
    }

    @Test
    fun `ovulation anchor without luteal history uses the default luteal length`() {
        val ovulation = currentCycleStart.plusDays(13)..currentCycleStart.plusDays(15)

        val forecast = PeriodForecastCalculator.forecast(
            currentCycleStart = currentCycleStart,
            currentDate = currentCycleStart.plusDays(16),
            previousCycles = emptyList(),
            ovulationEstimate = ovulation,
        )

        assertEquals(PeriodForecastBasis.OVULATION_AND_DEFAULT_LUTEAL, forecast.basis)
        assertEquals(PeriodForecastCalculator.DEFAULT_LUTEAL_DAYS, forecast.lutealDaysUsed)
        assertEquals(
            currentCycleStart.plusDays(28)..currentCycleStart.plusDays(29),
            forecast.expectedStartRange,
        )
    }

    @Test
    fun `cycle length history selects the median pair despite variable cycles`() {
        val cycles = listOf(
            completedCycle(LocalDate.of(2026, 1, 1), length = 27),
            completedCycle(LocalDate.of(2026, 2, 1), length = 29),
            completedCycle(LocalDate.of(2026, 3, 5), length = 33),
        )

        val forecast = PeriodForecastCalculator.forecast(
            currentCycleStart = currentCycleStart,
            currentDate = currentCycleStart.plusDays(4),
            previousCycles = cycles,
            ovulationEstimate = null,
        )

        assertEquals(PeriodForecastBasis.CYCLE_LENGTH_HISTORY, forecast.basis)
        assertEquals(
            currentCycleStart.plusDays(29)..currentCycleStart.plusDays(30),
            forecast.expectedStartRange,
        )
        assertNull(forecast.lutealDaysUsed)
    }

    @Test
    fun `default estimate is used without any completed history`() {
        val forecast = PeriodForecastCalculator.forecast(
            currentCycleStart = currentCycleStart,
            currentDate = currentCycleStart,
            previousCycles = emptyList(),
            ovulationEstimate = null,
        )

        assertEquals(PeriodForecastBasis.DEFAULT_ESTIMATE, forecast.basis)
        assertEquals(
            currentCycleStart.plusDays(29)..currentCycleStart.plusDays(30),
            forecast.expectedStartRange,
        )
    }

    @Test
    fun `regular history centers the forecast on the true next period date`() {
        // 28-day cycles with ovulation on cycle day 14: the luteal phase covers days 15..28 and
        // the next period truly starts on what would be cycle day 29.
        val cycles = listOf(
            analyzedCycle(LocalDate.of(2026, 1, 1), length = 28, ovulationDay = 14),
            analyzedCycle(LocalDate.of(2026, 2, 1), length = 28, ovulationDay = 14),
        )
        val ovulationDate = currentCycleStart.plusDays(13)
        val trueNextPeriod = currentCycleStart.plusDays(28)

        val forecast = PeriodForecastCalculator.forecast(
            currentCycleStart = currentCycleStart,
            currentDate = ovulationDate.plusDays(3),
            previousCycles = cycles,
            ovulationEstimate = ovulationDate..ovulationDate,
        )

        assertEquals(
            trueNextPeriod..trueNextPeriod.plusDays(1),
            forecast.expectedStartRange,
        )
    }

    @Test
    fun `forecast is overdue when the whole range has passed`() {
        val ovulation = currentCycleStart.plusDays(13)..currentCycleStart.plusDays(13)

        val forecast = PeriodForecastCalculator.forecast(
            currentCycleStart = currentCycleStart,
            currentDate = currentCycleStart.plusDays(40),
            previousCycles = emptyList(),
            ovulationEstimate = ovulation,
        )

        assertTrue(forecast.isOverdue)
    }

    private fun analyzedCycle(start: LocalDate, length: Int, ovulationDay: Int): CycleWithAnalysis {
        val ovulationDate = start.plusDays((ovulationDay - 1).toLong())
        return CycleWithAnalysis(
            cycle = cycle(start, length),
            estimatedOvulationRange = ovulationDate..ovulationDate,
            reliability = ForecastReliability.STRONG,
        )
    }

    private fun completedCycle(start: LocalDate, length: Int): CycleWithAnalysis =
        CycleWithAnalysis(cycle = cycle(start, length))
}
