package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.DisturbanceFlag
import com.yv.bbttracker.domain.model.MeasurementSite
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalShiftDetectorTest {
    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun `fewer than six prior measurements does not produce a shift`() {
        val measurements = (0L..4L).map { measurement(start.plusDays(it), 3600 + it.toInt()) } +
            listOf(
                measurement(start.plusDays(5), 3620),
                measurement(start.plusDays(6), 3621),
                measurement(start.plusDays(7), 3630),
            )

        assertNull(ThermalShiftDetector.detect(measurements, MeasurementSite.ORAL))
    }

    @Test
    fun `three consecutive highs confirm when third is exactly plus point twenty`() {
        val result = ThermalShiftDetector.detect(regularConfirmedPattern(start), MeasurementSite.ORAL)

        requireNotNull(result)
        assertEquals(ThermalShiftState.CONFIRMED, result.state)
        assertEquals(ThermalShiftRule.THREE_HIGH_THIRD_PLUS_0_20, result.rule)
        assertEquals(3603, result.baselineCentiC)
        assertEquals(start.plusDays(6), result.firstHighDate)
        assertEquals(start.plusDays(8), result.confirmationDate)
        assertEquals(start.plusDays(4)..start.plusDays(6), result.estimatedOvulationRange)
    }

    @Test
    fun `three highs remain a candidate when third is below plus point twenty`() {
        val baseline = (0L..5L).map { measurement(start.plusDays(it), 3605) }
        val measurements = baseline + (6L..8L).map { measurement(start.plusDays(it), 3615) }

        val result = ThermalShiftDetector.detect(measurements, MeasurementSite.ORAL)

        requireNotNull(result)
        assertEquals(ThermalShiftState.CANDIDATE, result.state)
        assertEquals(ThermalShiftRule.FOURTH_HIGH_REQUIRED, result.rule)
        assertNull(result.confirmationDate)
        assertNull(result.estimatedOvulationRange)
    }

    @Test
    fun `fourth consecutive high at exact point ten confirms fallback`() {
        val baseline = (0L..5L).map { measurement(start.plusDays(it), 3605) }
        val measurements = baseline + (6L..9L).map { measurement(start.plusDays(it), 3615) }

        val result = ThermalShiftDetector.detect(measurements, MeasurementSite.ORAL)

        requireNotNull(result)
        assertTrue(result.isConfirmed)
        assertEquals(ThermalShiftRule.FOURTH_HIGH_FALLBACK, result.rule)
        assertEquals(start.plusDays(9), result.confirmationDate)
        assertEquals(4, result.highDates.size)
    }

    @Test
    fun `missing calendar day breaks the high sequence`() {
        val baseline = (0L..5L).map { measurement(start.plusDays(it), 3600) }
        val measurements = baseline + listOf(
            measurement(start.plusDays(6), 3610),
            measurement(start.plusDays(8), 3610),
            measurement(start.plusDays(9), 3620),
        )

        assertNull(ThermalShiftDetector.detect(measurements, MeasurementSite.ORAL))
    }

    @Test
    fun `six baseline measurements may span exactly eight calendar days`() {
        val firstHigh = start.plusDays(8)
        val priorOffsets = listOf(0L, 1L, 3L, 5L, 6L, 7L)
        val measurements = priorOffsets.map { measurement(start.plusDays(it), 3600) } + listOf(
            measurement(firstHigh, 3610),
            measurement(firstHigh.plusDays(1), 3610),
            measurement(firstHigh.plusDays(2), 3620),
        )

        val result = ThermalShiftDetector.detect(measurements, MeasurementSite.ORAL)

        requireNotNull(result)
        assertTrue(result.isConfirmed)
        assertEquals(firstHigh, result.firstHighDate)
    }

    @Test
    fun `baseline measurement nine days before candidate does not qualify`() {
        val firstHigh = start.plusDays(9)
        val priorOffsets = listOf(0L, 2L, 4L, 6L, 7L, 8L)
        val measurements = priorOffsets.map { measurement(start.plusDays(it), 3600) } + listOf(
            measurement(firstHigh, 3610),
            measurement(firstHigh.plusDays(1), 3610),
            measurement(firstHigh.plusDays(2), 3620),
        )

        assertNull(ThermalShiftDetector.detect(measurements, MeasurementSite.ORAL))
    }

    @Test
    fun `excluded disturbed outlier neither changes baseline nor selection`() {
        val selected = regularConfirmedPattern(start)
        val excludedOutlier = measurement(
            date = start.plusDays(5),
            temperatureCentiC = 4300,
            id = 99,
            selectedForAnalysis = false,
            disturbanceMask = DisturbanceFlag.ILLNESS_OR_FEVER,
            updatedAtEpochMillis = Long.MAX_VALUE,
        )

        val result = ThermalShiftDetector.detect(selected + excludedOutlier, MeasurementSite.ORAL)

        requireNotNull(result)
        assertTrue(result.isConfirmed)
        assertEquals(3603, result.baselineCentiC)
    }

    @Test
    fun `explicitly selected fever measurement cannot confirm a thermal shift`() {
        val pattern = regularConfirmedPattern(start).toMutableList()
        pattern[2] = pattern[2].copy(disturbanceMask = DisturbanceFlag.ILLNESS_OR_FEVER)

        val result = ThermalShiftDetector.detect(pattern, MeasurementSite.ORAL)

        assertNull(result)
    }

    @Test
    fun `different measurement site is excluded and breaks sequence`() {
        val pattern = regularConfirmedPattern(start).toMutableList()
        pattern[7] = pattern[7].copy(site = MeasurementSite.VAGINAL)

        assertNull(ThermalShiftDetector.detect(pattern, MeasurementSite.ORAL))
    }

    @Test
    fun `only selected measurement is used when a day has multiple records`() {
        val pattern = regularConfirmedPattern(start)
        val unselectedHighOutlier = measurement(
            date = start.plusDays(5),
            temperatureCentiC = 4300,
            id = 1234,
            selectedForAnalysis = false,
            updatedAtEpochMillis = Long.MAX_VALUE,
        )
        val chosen = ThermalShiftDetector.chooseValidMeasurements(
            pattern + unselectedHighOutlier,
            MeasurementSite.ORAL,
        )

        assertEquals(pattern.size, chosen.size)
        assertFalse(chosen.any { it.id == unselectedHighOutlier.id })
        assertTrue(ThermalShiftDetector.detect(pattern + unselectedHighOutlier, MeasurementSite.ORAL)!!.isConfirmed)
    }

    @Test
    fun `duplicate selected measurements are resolved by update time then measurement time then id`() {
        val date = start.plusDays(2)
        val oldestUpdate = measurement(
            date = date,
            temperatureCentiC = 3600,
            id = 10,
            hour = 9,
            updatedAtEpochMillis = 100,
        )
        val latestUpdate = measurement(
            date = date,
            temperatureCentiC = 3610,
            id = 11,
            hour = 6,
            updatedAtEpochMillis = 200,
        )
        val latestMeasurement = measurement(
            date = date,
            temperatureCentiC = 3620,
            id = 12,
            hour = 8,
            updatedAtEpochMillis = 200,
        )
        val highestIdTieBreaker = measurement(
            date = date,
            temperatureCentiC = 3630,
            id = 13,
            hour = 8,
            updatedAtEpochMillis = 200,
        )

        val chosen = ThermalShiftDetector.chooseValidMeasurements(
            listOf(highestIdTieBreaker, latestMeasurement, latestUpdate, oldestUpdate),
            MeasurementSite.ORAL,
        )

        assertEquals(listOf(highestIdTieBreaker), chosen)
    }

    @Test
    fun `a high that is one centi degree below threshold does not start a shift`() {
        val baseline = (0L..5L).map { measurement(start.plusDays(it), 3605) }
        val measurements = baseline + listOf(
            measurement(start.plusDays(6), 3614),
            measurement(start.plusDays(7), 3625),
            measurement(start.plusDays(8), 3625),
        )

        assertNull(ThermalShiftDetector.detect(measurements, MeasurementSite.ORAL))
    }

    @Test
    fun `fourth value below threshold rejects the original candidate`() {
        val baseline = (0L..5L).map { measurement(start.plusDays(it), 3605) }
        val measurements = baseline + listOf(
            measurement(start.plusDays(6), 3615),
            measurement(start.plusDays(7), 3615),
            measurement(start.plusDays(8), 3615),
            measurement(start.plusDays(9), 3614),
        )

        val result = ThermalShiftDetector.detect(measurements, MeasurementSite.ORAL)

        assertNull(result)
    }

    @Test
    fun `failed early candidate does not block a later confirmed shift`() {
        val measurements = (0L..5L).map { measurement(start.plusDays(it), 3600) } + listOf(
            measurement(start.plusDays(6), 3610),
            measurement(start.plusDays(7), 3610),
            measurement(start.plusDays(8), 3610),
            measurement(start.plusDays(9), 3600),
            measurement(start.plusDays(10), 3620),
            measurement(start.plusDays(11), 3620),
            measurement(start.plusDays(12), 3630),
        )

        val result = ThermalShiftDetector.detect(measurements, MeasurementSite.ORAL)

        requireNotNull(result)
        assertTrue(result.isConfirmed)
        assertEquals(start.plusDays(10), result.firstHighDate)
    }

    @Test
    fun `transient confirmed highs are refuted by two subsequent lows`() {
        val measurements = (0L..5L).map { measurement(start.plusDays(it), 3600) } + listOf(
            measurement(start.plusDays(6), 3610),
            measurement(start.plusDays(7), 3610),
            measurement(start.plusDays(8), 3620),
            measurement(start.plusDays(9), 3600),
            measurement(start.plusDays(10), 3600),
        )

        assertNull(ThermalShiftDetector.detect(measurements, MeasurementSite.ORAL))
    }

    @Test
    fun `one isolated dip does not refute an otherwise persistent shift`() {
        val measurements = regularConfirmedPattern(start) + listOf(
            measurement(start.plusDays(9), 3605),
            measurement(start.plusDays(10), 3620),
            measurement(start.plusDays(11), 3621),
        )

        val result = ThermalShiftDetector.detect(measurements, MeasurementSite.ORAL)

        requireNotNull(result)
        assertTrue(result.isConfirmed)
        assertEquals(2, result.subsequentSupportingHighCount)
        assertEquals(1, result.subsequentLowCount)
    }

    @Test
    fun `less than three hours sleep is excluded from confirmation`() {
        val pattern = regularConfirmedPattern(start).toMutableList()
        pattern[7] = pattern[7].copy(sleepMinutes = 120)

        assertNull(ThermalShiftDetector.detect(pattern, MeasurementSite.ORAL))
        assertFalse(
            ThermalShiftDetector.chooseValidMeasurements(pattern, MeasurementSite.ORAL)
                .any { it.date == start.plusDays(7) },
        )
    }

    @Test
    fun `explicitly not measured after waking is excluded from confirmation`() {
        val pattern = regularConfirmedPattern(start).toMutableList()
        pattern[7] = pattern[7].copy(measuredImmediatelyAfterWaking = false)

        assertNull(ThermalShiftDetector.detect(pattern, MeasurementSite.ORAL))
    }

    @Test
    fun `candidate expires with elapsed fourth day even when no new measurement exists`() {
        val baseline = (0L..5L).map { measurement(start.plusDays(it), 3605) }
        val measurements = baseline + (6L..8L).map { measurement(start.plusDays(it), 3615) }

        val onThirdHigh = ThermalShiftDetector.detect(
            measurements,
            MeasurementSite.ORAL,
            asOfDate = start.plusDays(8),
        )
        val afterMissingFourth = ThermalShiftDetector.detect(
            measurements,
            MeasurementSite.ORAL,
            asOfDate = start.plusDays(9),
        )

        assertEquals(ThermalShiftState.CANDIDATE, onThirdHigh?.state)
        assertNull(afterMissingFourth)
    }

    @Test
    fun `as of date prevents future temperatures from leaking into detection`() {
        val completePattern = regularConfirmedPattern(start)

        val beforeHighs = ThermalShiftDetector.detect(
            completePattern,
            MeasurementSite.ORAL,
            asOfDate = start.plusDays(5),
        )
        val onConfirmation = ThermalShiftDetector.detect(
            completePattern,
            MeasurementSite.ORAL,
            asOfDate = start.plusDays(8),
        )

        assertNull(beforeHighs)
        assertTrue(onConfirmation?.isConfirmed == true)
    }

    @Test
    fun `extreme centi C values do not crash or overflow threshold logic`() {
        val measurements = listOf(3200, 4300, 3210, 4290, 3220, 4280, 4300, 4300, 4300)
            .mapIndexed { index, value -> measurement(start.plusDays(index.toLong()), value) }

        val result = runCatching {
            ThermalShiftDetector.detect(measurements, MeasurementSite.ORAL)
        }

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())

        val maxValues = (0L..8L).map { measurement(start.plusDays(it), Int.MAX_VALUE) }
        assertNull(ThermalShiftDetector.detect(maxValues, MeasurementSite.ORAL))
    }
}
