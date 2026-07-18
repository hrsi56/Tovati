package com.yv.bbttracker.domain.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusedEstimateSelectorTest {
    private val start = LocalDate.of(2026, 7, 1)

    @Test
    fun `prospective selector picks the strongest consecutive two day pair`() {
        val weights = linkedMapOf(
            start to 0.10,
            start.plusDays(1) to 0.31,
            start.plusDays(2) to 0.30,
            start.plusDays(3) to 0.29,
        )

        assertEquals(
            start.plusDays(1)..start.plusDays(2),
            FocusedEstimateSelector.bestTwoDayRange(weights),
        )
    }

    @Test
    fun `prospective selector honors the actionable horizon`() {
        val weights = linkedMapOf(
            start to 0.60,
            start.plusDays(1) to 0.20,
            start.plusDays(5) to 0.10,
            start.plusDays(6) to 0.09,
        )

        assertEquals(
            start.plusDays(5)..start.plusDays(6),
            FocusedEstimateSelector.bestTwoDayRange(
                weights = weights,
                allowedRange = start.plusDays(5)..start.plusDays(7),
            ),
        )
    }

    @Test
    fun `prospective selector never lets the second day escape the allowed horizon`() {
        val weights = linkedMapOf(
            start to 0.10,
            start.plusDays(1) to 0.20,
            start.plusDays(2) to 0.30,
            start.plusDays(3) to 0.90,
            start.plusDays(4) to 0.80,
        )

        assertEquals(
            start.plusDays(2)..start.plusDays(3),
            FocusedEstimateSelector.bestTwoDayRange(
                weights = weights,
                allowedRange = start..start.plusDays(3),
            ),
        )
    }

    @Test
    fun `prolonged LH episode moves the displayed pair without widening it`() {
        val episode = LhEpisode(
            firstPositiveDate = start,
            lastPositiveDate = start.plusDays(3),
            positiveDates = listOf(start, start.plusDays(3)),
        )

        assertEquals(
            start.plusDays(3)..start.plusDays(4),
            FocusedEstimateSelector.lhProspectiveRange(episode, start.plusDays(3)),
        )
    }

    @Test
    fun `thermal evidence pins one retrospective day even when late LH conflicts`() {
        val firstHigh = start.plusDays(10)
        val thermal = ThermalShiftResult(
            state = ThermalShiftState.CONFIRMED,
            firstHighDate = firstHigh,
            baselineCentiC = 3600,
            highDates = listOf(firstHigh, firstHigh.plusDays(1), firstHigh.plusDays(2)),
            confirmationDate = firstHigh.plusDays(2),
            rule = ThermalShiftRule.THREE_HIGH_THIRD_PLUS_0_20,
            estimatedOvulationRange = firstHigh.minusDays(2)..firstHigh,
        )
        val lateLh = LhEpisode(
            firstPositiveDate = firstHigh.plusDays(5),
            lastPositiveDate = firstHigh.plusDays(5),
            positiveDates = listOf(firstHigh.plusDays(5)),
        )

        assertEquals(
            firstHigh.minusDays(1),
            FocusedEstimateSelector.retrospectiveDay(thermal, lateLh, mucusPeakDate = null),
        )
    }
}
