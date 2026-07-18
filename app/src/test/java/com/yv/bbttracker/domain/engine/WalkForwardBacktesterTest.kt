package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkForwardBacktesterTest {
    // Baseline on cycle days 9..14 and highs on days 15..17, the third high +0.20 above
    // baseline, so the shift confirms on cycle day 17 and pins the best ovulation day to day 14.
    private fun confirmedShiftTemperatures(start: LocalDate): List<TemperatureMeasurement> {
        val baseline = listOf(3600, 3601, 3602, 3600, 3603, 3602)
            .mapIndexed { index, temperature -> measurement(start.plusDays(8L + index), temperature) }
        return baseline + listOf(
            measurement(start.plusDays(14), 3613),
            measurement(start.plusDays(15), 3613),
            measurement(start.plusDays(16), 3623),
        )
    }

    @Test
    fun `confirmed cycle anchors at thermal confirmation and hits the actual period`() {
        val start = LocalDate.of(2026, 1, 1)
        val summary = WalkForwardBacktester.backtest(
            cycles = listOf(cycle(start, 28), cycle(start.plusDays(28))),
            measurements = confirmedShiftTemperatures(start),
            observations = emptyList(),
            fallbackSite = MeasurementSite.ORAL,
        )

        assertEquals(1, summary.evaluatedCycleCount)
        val result = summary.cycleResults.single()

        assertEquals(start.plusDays(28), result.actualNextPeriodDate)
        assertEquals(start.plusDays(13)..start.plusDays(13), result.referenceOvulationRange)
        // The forecast becomes ovulation-anchored on the confirmation day, cycle day 17.
        assertEquals(start.plusDays(16), result.anchorDate)
        assertEquals(PeriodForecastBasis.OVULATION_AND_DEFAULT_LUTEAL, result.anchorBasis)
        assertEquals(start.plusDays(27)..start.plusDays(28), result.periodRangeAtAnchor)
        assertEquals(0, result.periodErrorDaysAtAnchor)
        assertEquals(true, result.periodHitAtAnchor)
        assertEquals(12, result.leadDaysAtAnchor)
        assertEquals(true, result.finalPeriodHit)
    }

    @Test
    fun `later cycles anchor with the personal luteal basis learned from earlier ones`() {
        val starts = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 29),
            LocalDate.of(2026, 2, 26),
        )
        val summary = WalkForwardBacktester.backtest(
            cycles = starts.map { cycle(it, 28) } + cycle(starts.last().plusDays(28)),
            measurements = starts.flatMap { confirmedShiftTemperatures(it) },
            observations = emptyList(),
            fallbackSite = MeasurementSite.ORAL,
        )

        assertEquals(3, summary.evaluatedCycleCount)
        assertEquals(3, summary.anchoredCycleCount)
        assertEquals(3, summary.anchoredPeriodHitCount)
        assertEquals(0.0, summary.medianAbsErrorDaysAtAnchor!!, 1e-9)
        assertEquals(12.0, summary.medianLeadDaysAtAnchor!!, 1e-9)

        val third = summary.cycleResults.last()
        // Ovulation day 14 of 28-day cycles gives a personal luteal median of 14 days.
        assertEquals(PeriodForecastBasis.OVULATION_AND_PERSONAL_LUTEAL, third.anchorBasis)
        assertEquals(
            starts[2].plusDays(28)..starts[2].plusDays(29),
            third.periodRangeAtAnchor,
        )
        assertEquals(true, third.periodHitAtAnchor)
    }

    @Test
    fun `cycle without ovulation evidence has no anchor but still a final calendar forecast`() {
        val start = LocalDate.of(2026, 1, 1)
        val flatTemperatures = (0L..3L).map { measurement(start.plusDays(it), 3600) }
        val summary = WalkForwardBacktester.backtest(
            cycles = listOf(cycle(start, 28), cycle(start.plusDays(28))),
            measurements = flatTemperatures,
            observations = emptyList(),
            fallbackSite = MeasurementSite.ORAL,
        )

        val result = summary.cycleResults.single()
        assertNull(result.anchorDate)
        assertNull(result.periodHitAtAnchor)
        assertNull(summary.medianAbsErrorDaysAtAnchor)
        // A focused default estimate is intentionally decisive and can miss by one day.
        assertEquals(start.plusDays(29)..start.plusDays(30), result.finalPeriodRange)
        assertEquals(false, result.finalPeriodHit)
    }

    @Test
    fun `unusually long luteal phase is reported as a signed miss`() {
        val start = LocalDate.of(2026, 1, 1)
        val summary = WalkForwardBacktester.backtest(
            cycles = listOf(cycle(start, 35), cycle(start.plusDays(35))),
            measurements = confirmedShiftTemperatures(start),
            observations = emptyList(),
            fallbackSite = MeasurementSite.ORAL,
        )

        val result = summary.cycleResults.single()
        assertEquals(start.plusDays(35), result.actualNextPeriodDate)
        assertEquals(start.plusDays(27)..start.plusDays(28), result.periodRangeAtAnchor)
        // The actual period came seven days after the focused anchored range ended.
        assertEquals(7, result.periodErrorDaysAtAnchor)
        assertEquals(false, result.periodHitAtAnchor)
        assertTrue(summary.anchoredPeriodHitCount == 0)
    }

    @Test
    fun `cycle with a gap before the next stored cycle is excluded from accuracy metrics`() {
        val start = LocalDate.of(2026, 1, 1)
        val completed = cycle(start, 28)
        val nextSurvivingCycle = cycle(start.plusDays(56))

        val summary = WalkForwardBacktester.backtest(
            cycles = listOf(completed, nextSurvivingCycle),
            measurements = confirmedShiftTemperatures(start),
            observations = emptyList(),
            fallbackSite = MeasurementSite.ORAL,
        )

        assertEquals(0, summary.evaluatedCycleCount)
        assertTrue(summary.cycleResults.isEmpty())
    }
}
