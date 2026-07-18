package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MucusSensation
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalCycleBuilderTest {
    private val start = LocalDate.of(2026, 2, 1)

    @Test
    fun `persisted cycle analysis site wins over an accidental first measurement`() {
        val completed = cycle(start, 14).copy(analysisSite = MeasurementSite.ORAL)
        val accidentalVaginal = measurement(
            date = start,
            temperatureCentiC = 3700,
            id = 99_999,
            site = MeasurementSite.VAGINAL,
        )

        val result = HistoricalCycleBuilder.build(
            cycle = completed,
            measurements = listOf(accidentalVaginal) + regularConfirmedPattern(start),
            observations = emptyList(),
            fallbackSite = MeasurementSite.VAGINAL,
        )

        assertNotNull(result.confirmedThermalShiftFirstHighDate)
        assertTrue(AnalysisSignal.THERMAL_SHIFT in result.signals)
    }

    @Test
    fun `builder combines first LH positive mucus peak and thermal evidence`() {
        val completed = cycle(start, 14).copy(analysisSite = MeasurementSite.ORAL)
        val firstHigh = start.plusDays(6)
        val lhDate = firstHigh.minusDays(1)
        val result = HistoricalCycleBuilder.build(
            cycle = completed,
            measurements = regularConfirmedPattern(start),
            observations = listOf(
                observation(lhDate, mucus = CervicalMucus.EGG_WHITE, lhResult = LhResult.POSITIVE)
                    .copy(mucusSensation = MucusSensation.SLIPPERY),
                observation(lhDate.plusDays(1), mucus = CervicalMucus.CREAMY)
                    .copy(mucusSensation = MucusSensation.DRY),
            ),
            fallbackSite = MeasurementSite.ORAL,
        )

        assertEquals(lhDate, result.firstPositiveLhDate)
        assertEquals(lhDate, result.mucusPeakDate)
        assertEquals(firstHigh.minusDays(1)..firstHigh.minusDays(1), result.estimatedOvulationRange)
        assertEquals(ForecastReliability.STRONG, result.reliability)
    }

    @Test
    fun `builder honors as of date and never reads future signals`() {
        val completed = cycle(start, 20).copy(analysisSite = MeasurementSite.ORAL)
        val asOf = start.plusDays(5)
        val futurePositive = start.plusDays(9)
        val result = HistoricalCycleBuilder.build(
            cycle = completed,
            measurements = regularConfirmedPattern(start),
            observations = listOf(observation(futurePositive, lhResult = LhResult.POSITIVE)),
            fallbackSite = MeasurementSite.ORAL,
            asOfDate = asOf,
        )

        assertNull(result.confirmedThermalShiftFirstHighDate)
        assertNull(result.firstPositiveLhDate)
        assertNull(result.estimatedOvulationRange)
    }
}
