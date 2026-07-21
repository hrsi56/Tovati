package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.DisturbanceFlag
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MoodFlag
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.PhysicalSymptomFlag
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleAnalysisEngineTest {
    private val engine = CycleAnalysisEngine()
    private val currentCycleStart = LocalDate.of(2026, 6, 1)

    @Test
    fun `no history shows a focused pair but reports insufficient evidence`() {
        val result = analyze(currentDate = currentCycleStart.plusDays(4))

        assertNotNull(result.prospectiveOvulationRange)
        assertEquals(2, rangeDays(result.prospectiveOvulationRange!!))
        assertNotNull(result.conceptionOpportunityWindow)
        assertEquals(FertilityStatus.INSUFFICIENT_DATA, result.status)
        assertEquals(ForecastReliability.INSUFFICIENT, result.reliability)
        assertFalse(AnalysisSignal.CYCLE_LENGTH_HISTORY in result.signals)
    }

    @Test
    fun `reported cycle length focuses the initial estimate without overstating reliability`() {
        val result = analyze(
            currentDate = currentCycleStart.plusDays(4),
            typicalCycleLengthDays = 34,
        )
        val peakDate = result.dailyOvulationWeights.maxBy { it.relativeWeight }.date
        val peakCycleDay = (peakDate.toEpochDay() - currentCycleStart.toEpochDay() + 1).toInt()

        assertTrue(peakCycleDay in 19..21)
        assertTrue(AnalysisSignal.SELF_REPORTED_CYCLE_LENGTH in result.signals)
        assertTrue(ReasonCode.SELF_REPORTED_CYCLE_LENGTH_AVAILABLE in result.reasonCodes)
        assertEquals(EvidenceLevel.CALENDAR_ONLY, result.evidenceLevel)
        assertEquals(ForecastReliability.INSUFFICIENT, result.reliability)
        assertEquals(
            PeriodForecastBasis.SELF_REPORTED_CYCLE_LENGTH,
            result.nextPeriodForecast?.basis,
        )
    }

    @Test
    fun `conception opportunity starts five days before possible ovulation and ends with it`() {
        val result = analyze(
            currentDate = currentCycleStart,
            previousCycles = stablePreviousCycles(count = 3),
        )
        val ovulation = requireNotNull(result.prospectiveOvulationRange)
        val opportunity = requireNotNull(result.conceptionOpportunityWindow)

        assertEquals(maxOf(currentCycleStart, ovulation.start.minusDays(5)), opportunity.start)
        assertEquals(ovulation.endInclusive, opportunity.endInclusive)
        assertEquals(2, rangeDays(ovulation))
        assertEquals(opportunity, result.predictedFertileWindow)
        assertTrue(AnalysisSignal.CYCLE_LENGTH_HISTORY in result.signals)
        assertTrue(ReasonCode.CALENDAR_PRIOR_AVAILABLE in result.reasonCodes)
    }

    @Test
    fun `recency and evidence quality shape the personal prior`() {
        val oldStart = LocalDate.of(2026, 1, 1)
        val recentStart = LocalDate.of(2026, 3, 1)
        val previous = listOf(
            CycleWithAnalysis(
                cycle = cycle(oldStart, 28),
                estimatedOvulationRange = oldStart.plusDays(9)..oldStart.plusDays(9),
                reliability = ForecastReliability.LIMITED,
            ),
            CycleWithAnalysis(
                cycle = cycle(recentStart, 35),
                estimatedOvulationRange = recentStart.plusDays(19)..recentStart.plusDays(19),
                reliability = ForecastReliability.STRONG,
            ),
        )

        val result = analyze(currentCycleStart, previousCycles = previous)
        val peakDate = result.dailyOvulationWeights.maxBy { it.relativeWeight }.date
        val peakCycleDay = (peakDate.toEpochDay() - currentCycleStart.toEpochDay() + 1).toInt()

        assertTrue(peakCycleDay >= 17)
        assertTrue(AnalysisSignal.PERSONAL_HISTORY in result.signals)
        assertTrue(ReasonCode.PERSONAL_OVULATION_HISTORY_AVAILABLE in result.reasonCodes)
    }

    @Test
    fun `cycle variability is reported without unboundedly extending the forecast`() {
        val previous = listOf(24, 28, 36).mapIndexed { index, length ->
            val start = LocalDate.of(2026, 1, 1).plusMonths(index.toLong())
            CycleWithAnalysis(cycle(start, length))
        }
        val currentDate = currentCycleStart.plusDays(45)

        val result = analyze(currentDate, previousCycles = previous)

        assertTrue(ReasonCode.CALENDAR_PRIOR_UNSTABLE in result.reasonCodes)
        assertTrue(ReasonCode.CURRENT_CYCLE_LONGER_THAN_EXPECTED in result.reasonCodes)
        assertEquals(FertilityStatus.UNCERTAIN, result.status)
        assertTrue(result.prospectiveOvulationRange!!.endInclusive.isBefore(currentDate))
    }

    @Test
    fun `consecutive positive LH days use first positive in episode`() {
        val firstPositive = currentCycleStart.plusDays(8)
        val currentDate = firstPositive.plusDays(1)
        val result = analyze(
            currentDate = currentDate,
            observations = listOf(
                observation(firstPositive, lhResult = LhResult.POSITIVE),
                observation(currentDate, lhResult = LhResult.POSITIVE),
            ),
        )

        assertEquals(FertilityStatus.LH_SURGE_DETECTED, result.status)
        assertEquals(firstPositive.plusDays(1)..firstPositive.plusDays(2), result.estimatedOvulationRange)
        assertEquals(firstPositive.plusDays(1)..firstPositive.plusDays(2), result.prospectiveOvulationRange)
        assertEquals(
            firstPositive.minusDays(4)..firstPositive.plusDays(2),
            result.conceptionOpportunityWindow,
        )
        assertTrue(currentDate in result.conceptionOpportunityWindow!!)
        assertEquals(FertilityLevelToday.PEAK_SIGNAL, result.fertilityLevelToday)
        assertTrue(AnalysisSignal.LH_SURGE in result.signals)
    }

    @Test
    fun `LH episode remains active through day two after first positive`() {
        val firstPositive = currentCycleStart.plusDays(8)
        val currentDate = firstPositive.plusDays(2)
        val result = analyze(
            currentDate = currentDate,
            observations = listOf(observation(firstPositive, lhResult = LhResult.POSITIVE)),
        )

        assertEquals(currentDate..currentDate.plusDays(1), result.prospectiveOvulationRange)
        assertEquals(FertilityStatus.LH_SURGE_DETECTED, result.status)
        assertEquals(FertilityLevelToday.PEAK_SIGNAL, result.fertilityLevelToday)
    }

    @Test
    fun `LH episode expires prospectively on day three without another positive`() {
        val firstPositive = currentCycleStart.plusDays(8)
        val currentDate = firstPositive.plusDays(3)
        val baseline = analyze(currentDate)
        val result = analyze(
            currentDate = currentDate,
            observations = listOf(observation(firstPositive, lhResult = LhResult.POSITIVE)),
        )

        assertEquals(baseline.prospectiveOvulationRange, result.prospectiveOvulationRange)
        assertEquals(firstPositive.plusDays(1)..firstPositive.plusDays(1), result.retrospectiveOvulationRange)
        assertFalse(AnalysisSignal.LH_SURGE in result.signals)
        assertFalse(result.fertilityLevelToday == FertilityLevelToday.PEAK_SIGNAL)
    }

    @Test
    fun `prolonged positive LH keeps a focused actionable pair`() {
        val firstPositive = currentCycleStart.plusDays(8)
        val currentDate = firstPositive.plusDays(3)
        val result = analyze(
            currentDate = currentDate,
            observations = listOf(
                observation(firstPositive, lhResult = LhResult.POSITIVE),
                observation(currentDate, lhResult = LhResult.POSITIVE),
            ),
        )

        assertEquals(currentDate..currentDate.plusDays(1), result.prospectiveOvulationRange)
        assertTrue(currentDate in result.conceptionOpportunityWindow!!)
        assertEquals(FertilityStatus.LH_SURGE_DETECTED, result.status)
        assertEquals(FertilityLevelToday.PEAK_SIGNAL, result.fertilityLevelToday)
        assertTrue(AnalysisSignal.LH_SURGE in result.signals)
    }

    @Test
    fun `continued daily positive LH remains visible as a two day pair on day four`() {
        val firstPositive = currentCycleStart.plusDays(8)
        val currentDate = firstPositive.plusDays(4)
        val positives = (0L..4L).map { offset ->
            observation(firstPositive.plusDays(offset), lhResult = LhResult.POSITIVE)
        }

        val result = analyze(currentDate = currentDate, observations = positives)

        assertEquals(currentDate..currentDate.plusDays(1), result.prospectiveOvulationRange)
        assertTrue(currentDate in result.conceptionOpportunityWindow!!)
        assertEquals(FertilityLevelToday.PEAK_SIGNAL, result.fertilityLevelToday)
    }

    @Test
    fun `negative between positives starts a new LH episode`() {
        val first = currentCycleStart.plusDays(8)
        val second = currentCycleStart.plusDays(10)
        val result = analyze(
            currentDate = second,
            observations = listOf(
                observation(first, lhResult = LhResult.POSITIVE),
                observation(first.plusDays(1), lhResult = LhResult.NEGATIVE),
                observation(second, lhResult = LhResult.POSITIVE),
            ),
        )

        assertEquals(second.plusDays(1)..second.plusDays(2), result.estimatedOvulationRange)
    }

    @Test
    fun `old unsupported LH episode is retrospective and does not alter prospective forecast`() {
        val oldPositive = currentCycleStart.plusDays(8)
        val currentDate = oldPositive.plusDays(5)
        val baseline = analyze(currentDate)
        val withOldLh = analyze(
            currentDate,
            observations = listOf(observation(oldPositive, lhResult = LhResult.POSITIVE)),
        )

        assertEquals(baseline.prospectiveOvulationRange, withOldLh.prospectiveOvulationRange)
        assertEquals(oldPositive.plusDays(1)..oldPositive.plusDays(1), withOldLh.retrospectiveOvulationRange)
        assertFalse(AnalysisSignal.LH_SURGE in withOldLh.signals)
        assertEquals(ForecastReliability.INSUFFICIENT, withOldLh.reliability)
        assertFalse(withOldLh.fertilityLevelToday == FertilityLevelToday.PEAK_SIGNAL)
    }

    @Test
    fun `borderline LH is weaker evidence and recommends a repeat test`() {
        val currentDate = currentCycleStart.plusDays(8)
        val result = analyze(
            currentDate = currentDate,
            observations = listOf(observation(currentDate, lhResult = LhResult.BORDERLINE)),
        )

        assertTrue(AnalysisSignal.LH_BORDERLINE in result.signals)
        assertEquals(FertilityLevelToday.ELEVATED, result.fertilityLevelToday)
        assertEquals(NextAction.REPEAT_LH_TEST, result.nextAction)
        assertTrue(ReasonCode.LH_BORDERLINE_RECORDED in result.reasonCodes)
    }

    @Test
    fun `slippery sensation counts as fertile even with dry appearance`() {
        val currentDate = currentCycleStart.plusDays(8)
        val slippery = observation(currentDate, mucus = CervicalMucus.DRY)
            .copy(mucusSensation = MucusSensation.SLIPPERY)

        val result = analyze(currentDate, observations = listOf(slippery))

        assertEquals(FertilityStatus.FERTILITY_SIGNS_PRESENT, result.status)
        assertEquals(FertilityLevelToday.HIGH, result.fertilityLevelToday)
        assertTrue(AnalysisSignal.FERTILE_MUCUS in result.signals)
    }

    @Test
    fun `fertile mucus today keeps conception opportunity open today despite a late personal prior`() {
        val currentDate = currentCycleStart.plusDays(8)
        val lateHistory = (0..2).map { index ->
            val start = LocalDate.of(2026, 1, 1).plusMonths(index.toLong())
            CycleWithAnalysis(
                cycle = cycle(start, 40),
                estimatedOvulationRange = start.plusDays(29)..start.plusDays(31),
                reliability = ForecastReliability.STRONG,
            )
        }
        val result = analyze(
            currentDate = currentDate,
            previousCycles = lateHistory,
            observations = listOf(
                observation(currentDate, mucus = CervicalMucus.EGG_WHITE)
                    .copy(mucusSensation = MucusSensation.SLIPPERY),
            ),
        )

        val opportunity = requireNotNull(result.conceptionOpportunityWindow)
        val ovulation = requireNotNull(result.prospectiveOvulationRange)
        assertFalse(opportunity.start.isAfter(currentDate))
        assertTrue(currentDate in opportunity)
        assertEquals(2, rangeDays(ovulation))
        assertTrue(ovulation.start in currentDate..currentDate.plusDays(3))
        assertTrue(ovulation.endInclusive in currentDate..currentDate.plusDays(3))
    }

    @Test
    fun `rising mucus trend is exposed as a prospective signal`() {
        val currentDate = currentCycleStart.plusDays(8)
        val result = analyze(
            currentDate,
            observations = listOf(
                observation(currentDate.minusDays(1), mucus = CervicalMucus.STICKY)
                    .copy(mucusSensation = MucusSensation.DAMP),
                observation(currentDate, mucus = CervicalMucus.CREAMY)
                    .copy(mucusSensation = MucusSensation.WET),
            ),
        )

        assertTrue(AnalysisSignal.RISING_MUCUS in result.signals)
        assertTrue(ReasonCode.MUCUS_PATTERN_RISING in result.reasonCodes)
        assertEquals(NextAction.PRIORITIZE_CONCEPTION_TIMING, result.nextAction)
    }

    @Test
    fun `mucus observations separated by missing days do not create a rising trend`() {
        val currentDate = currentCycleStart.plusDays(10)
        val result = analyze(
            currentDate,
            observations = listOf(
                observation(currentDate.minusDays(4), mucus = CervicalMucus.STICKY),
                observation(currentDate, mucus = CervicalMucus.CREAMY)
                    .copy(mucusSensation = MucusSensation.WET),
            ),
        )

        assertTrue(AnalysisSignal.FERTILE_MUCUS in result.signals)
        assertFalse(AnalysisSignal.RISING_MUCUS in result.signals)
        assertFalse(ReasonCode.MUCUS_PATTERN_RISING in result.reasonCodes)
    }

    @Test
    fun `mucus peak is retrospective only after a recorded decline`() {
        val peak = currentCycleStart.plusDays(8)
        val result = analyze(
            currentDate = peak.plusDays(1),
            observations = listOf(
                observation(peak, mucus = CervicalMucus.EGG_WHITE)
                    .copy(mucusSensation = MucusSensation.SLIPPERY),
                observation(peak.plusDays(1), mucus = CervicalMucus.CREAMY)
                    .copy(mucusSensation = MucusSensation.DRY),
            ),
        )

        assertEquals(peak..peak, result.retrospectiveOvulationRange)
        assertTrue(AnalysisSignal.MUCUS_PEAK in result.signals)
        assertTrue(ReasonCode.MUCUS_PEAK_IDENTIFIED in result.reasonCodes)
    }

    @Test
    fun `missing days prevent a retrospective mucus peak inference`() {
        val fertileDate = currentCycleStart.plusDays(8)
        val currentDate = fertileDate.plusDays(3)
        val result = analyze(
            currentDate = currentDate,
            observations = listOf(
                observation(fertileDate, mucus = CervicalMucus.EGG_WHITE),
                observation(currentDate, mucus = CervicalMucus.DRY),
            ),
        )

        assertNull(result.retrospectiveOvulationRange)
        assertFalse(AnalysisSignal.MUCUS_PEAK in result.signals)
    }

    @Test
    fun `new fertile mucus wave remains prospective after an earlier completed peak`() {
        val firstPeak = currentCycleStart.plusDays(7)
        val currentDate = currentCycleStart.plusDays(12)
        val result = analyze(
            currentDate,
            observations = listOf(
                observation(firstPeak, mucus = CervicalMucus.EGG_WHITE),
                observation(firstPeak.plusDays(1), mucus = CervicalMucus.DRY),
                observation(currentDate, mucus = CervicalMucus.WATERY)
                    .copy(mucusSensation = MucusSensation.WET),
            ),
        )

        assertEquals(FertilityStatus.FERTILITY_SIGNS_PRESENT, result.status)
        assertTrue(AnalysisSignal.FERTILE_MUCUS in result.signals)
        assertEquals(FertilityLevelToday.HIGH, result.fertilityLevelToday)
        assertNull(result.retrospectiveOvulationRange)
        assertFalse(AnalysisSignal.MUCUS_PEAK in result.signals)
    }

    @Test
    fun `LH and fertile mucus combine without claiming confirmation`() {
        val currentDate = currentCycleStart.plusDays(8)
        val result = analyze(
            currentDate,
            observations = listOf(
                observation(currentDate, mucus = CervicalMucus.WATERY, lhResult = LhResult.POSITIVE)
                    .copy(mucusSensation = MucusSensation.WET),
            ),
        )

        assertEquals(EvidenceLevel.MULTIPLE_SIGNS, result.evidenceLevel)
        assertEquals(FertilityStatus.LH_SURGE_DETECTED, result.status)
        assertNull(result.retrospectiveOvulationRange)
    }

    @Test
    fun `confirmed thermal shift after aligned LH is combined retrospective evidence`() {
        val temperatures = regularConfirmedPattern(currentCycleStart)
        val firstHigh = currentCycleStart.plusDays(6)
        val confirmationDate = firstHigh.plusDays(2)
        val result = analyze(
            currentDate = confirmationDate,
            temperatures = temperatures,
            observations = listOf(observation(firstHigh.minusDays(1), lhResult = LhResult.POSITIVE)),
        )

        assertEquals(FertilityStatus.THERMAL_SHIFT_CONFIRMED, result.status)
        assertEquals(EvidenceLevel.COMBINED_PATTERN, result.evidenceLevel)
        assertEquals(firstHigh.minusDays(1)..firstHigh.minusDays(1), result.retrospectiveOvulationRange)
        assertTrue(AnalysisSignal.THERMAL_SHIFT in result.signals)
    }

    @Test
    fun `aligned thermal LH and mucus narrow retrospective range by intersection`() {
        val temperatures = regularConfirmedPattern(currentCycleStart)
        val firstHigh = currentCycleStart.plusDays(6)
        val lhDate = firstHigh.minusDays(1)
        val result = analyze(
            currentDate = firstHigh.plusDays(2),
            temperatures = temperatures,
            observations = listOf(
                observation(lhDate, mucus = CervicalMucus.EGG_WHITE, lhResult = LhResult.POSITIVE)
                    .copy(mucusSensation = MucusSensation.SLIPPERY),
                observation(lhDate.plusDays(1), mucus = CervicalMucus.CREAMY)
                    .copy(mucusSensation = MucusSensation.DRY),
            ),
        )

        // All three signs converge most strongly on the day before the first high temperature.
        assertEquals(firstHigh.minusDays(1)..firstHigh.minusDays(1), result.retrospectiveOvulationRange)
        assertEquals(EvidenceLevel.COMBINED_PATTERN, result.evidenceLevel)
        assertFalse(AnalysisSignal.CONFLICTING_SIGNALS in result.signals)
    }

    @Test
    fun `late LH conflict stays visible without widening the retrospective day`() {
        val temperatures = regularConfirmedPattern(currentCycleStart)
        val firstHigh = currentCycleStart.plusDays(6)
        val currentDate = firstHigh.plusDays(5)
        val thermalOnly = analyze(currentDate, temperatures = temperatures)
        val conflict = analyze(
            currentDate,
            temperatures = temperatures,
            observations = listOf(observation(currentDate, lhResult = LhResult.POSITIVE)),
        )

        assertEquals(FertilityStatus.UNCERTAIN, conflict.status)
        assertEquals(ForecastReliability.LIMITED, conflict.reliability)
        assertTrue(AnalysisSignal.CONFLICTING_SIGNALS in conflict.signals)
        assertTrue(ReasonCode.SIGNS_DO_NOT_ALIGN in conflict.reasonCodes)
        assertEquals(1, rangeDays(conflict.retrospectiveOvulationRange!!))
        assertEquals(thermalOnly.retrospectiveOvulationRange, conflict.retrospectiveOvulationRange)
        assertEquals(2, rangeDays(conflict.prospectiveOvulationRange!!))
    }

    @Test
    fun `three high candidate asks for another temperature`() {
        val baseline = (0L..5L).map { measurement(currentCycleStart.plusDays(it), 3605) }
        val temperatures = baseline + (6L..8L).map { measurement(currentCycleStart.plusDays(it), 3615) }
        val result = analyze(currentCycleStart.plusDays(8), temperatures = temperatures)

        assertEquals(FertilityStatus.THERMAL_SHIFT_CANDIDATE, result.status)
        assertEquals(NextAction.AWAIT_THERMAL_CONFIRMATION, result.nextAction)
        assertTrue(ReasonCode.FOURTH_HIGH_TEMPERATURE_REQUIRED in result.reasonCodes)
    }

    @Test
    fun `fever measurement is excluded and traced`() {
        val temperatures = regularConfirmedPattern(currentCycleStart).toMutableList()
        temperatures[7] = temperatures[7].copy(disturbanceMask = DisturbanceFlag.ILLNESS_OR_FEVER)
        val result = analyze(currentCycleStart.plusDays(8), temperatures = temperatures)

        assertNull(result.thermalShift)
        assertTrue(AnalysisSignal.UNRELIABLE_TEMPERATURES_EXCLUDED in result.signals)
        assertTrue(ReasonCode.UNRELIABLE_TEMPERATURES_EXCLUDED in result.reasonCodes)
    }

    @Test
    fun `six reliable measurements are low quality and ten consistent are good`() {
        val six = (0L..5L).map { measurement(currentCycleStart.plusDays(it), 3600) }
        val ten = (0L..9L).map { measurement(currentCycleStart.plusDays(it), 3600) }

        assertEquals(DataQuality.LOW, analyze(currentCycleStart.plusDays(5), temperatures = six).dataQuality)
        assertEquals(DataQuality.GOOD, analyze(currentCycleStart.plusDays(9), temperatures = ten).dataQuality)
    }

    @Test
    fun `a short clean temperature series does not pretend there is a quality problem`() {
        val seven = (0L..6L).map { measurement(currentCycleStart.plusDays(it), 3600) }

        val result = analyze(currentCycleStart.plusDays(6), temperatures = seven)

        assertEquals(DataQuality.LOW, result.dataQuality)
        assertEquals(NextAction.CONTINUE_DAILY_TRACKING, result.nextAction)
        assertFalse(ReasonCode.DISTURBED_MEASUREMENTS in result.reasonCodes)
        assertFalse(ReasonCode.MEASUREMENT_SITE_CHANGED in result.reasonCodes)
    }

    @Test
    fun `starting clean temperature tracking mid-cycle does not produce an impossible next step`() {
        val recentSeven = (40L..46L).map { measurement(currentCycleStart.plusDays(it), 3600) }

        val result = analyze(currentCycleStart.plusDays(46), temperatures = recentSeven)

        assertEquals(DataQuality.LOW, result.dataQuality)
        assertTrue(ReasonCode.TOO_MANY_MISSING_DAYS in result.reasonCodes)
        assertEquals(NextAction.CONTINUE_DAILY_TRACKING, result.nextAction)
    }

    @Test
    fun `an actual temperature quality problem produces corrective guidance`() {
        val disturbed = (0L..6L).map { offset ->
            measurement(
                currentCycleStart.plusDays(offset),
                3600,
                disturbanceMask = if (offset < 3L) DisturbanceFlag.LATE_MEASUREMENT else 0L,
            )
        }

        val result = analyze(currentCycleStart.plusDays(6), temperatures = disturbed)

        assertEquals(DataQuality.LOW, result.dataQuality)
        assertEquals(NextAction.IMPROVE_TEMPERATURE_QUALITY, result.nextAction)
        assertTrue(ReasonCode.DISTURBED_MEASUREMENTS in result.reasonCodes)
    }

    @Test
    fun `one isolated disturbed measurement does not become a persistent next step`() {
        val mostlyClean = (0L..6L).map { offset ->
            measurement(
                currentCycleStart.plusDays(offset),
                3600,
                disturbanceMask = if (offset == 1L) DisturbanceFlag.LATE_MEASUREMENT else 0L,
            )
        }

        val result = analyze(currentCycleStart.plusDays(6), temperatures = mostlyClean)

        assertTrue(ReasonCode.DISTURBED_MEASUREMENTS in result.reasonCodes)
        assertEquals(NextAction.CONTINUE_DAILY_TRACKING, result.nextAction)
    }

    @Test
    fun `elevated fertility prioritizes LH testing over low BBT quality`() {
        val currentDate = currentCycleStart.plusDays(8)
        val sixTemperatures = (0L..5L).map { measurement(currentCycleStart.plusDays(it), 3600) }
        val result = analyze(
            currentDate,
            previousCycles = stablePreviousCycles(3),
            temperatures = sixTemperatures,
        )

        assertEquals(DataQuality.LOW, result.dataQuality)
        assertEquals(FertilityLevelToday.ELEVATED, result.fertilityLevelToday)
        assertEquals(NextAction.START_OR_CONTINUE_LH_TESTING, result.nextAction)
    }

    @Test
    fun `measurement times around midnight use circular spread`() {
        val temperatures = (0L..9L).map { offset ->
            measurement(
                currentCycleStart.plusDays(offset),
                3600,
                hour = if (offset % 2L == 0L) 23 else 0,
            )
        }

        val result = analyze(currentCycleStart.plusDays(9), temperatures = temperatures)

        assertEquals(DataQuality.GOOD, result.dataQuality)
    }

    @Test
    fun `future observations and temperatures cannot affect an as of result`() {
        val asOf = currentCycleStart.plusDays(8)
        val pastTemperatures = (0L..8L).map { measurement(currentCycleStart.plusDays(it), 3600) }
        val futureDate = asOf.plusDays(2)
        val withoutFuture = analyze(asOf, temperatures = pastTemperatures)
        val withFuture = analyze(
            asOf,
            temperatures = pastTemperatures + measurement(futureDate, 3700),
            observations = listOf(
                observation(futureDate, mucus = CervicalMucus.EGG_WHITE, lhResult = LhResult.POSITIVE),
            ),
        )

        assertEquals(withoutFuture, withFuture)
    }

    @Test
    fun `subjective wellbeing and sexual contact do not independently move ovulation forecast`() {
        val currentDate = currentCycleStart.plusDays(8)
        val neutralObservation = observation(currentDate)
        val contextualObservation = neutralObservation.copy(
            moodMask = MoodFlag.HAPPY or MoodFlag.ENERGETIC,
            moodNote = "מרגישה מצוין",
            libidoLevel = LibidoLevel.VERY_HIGH,
            sexualContact = SexualContact.YES,
            physicalSymptomMask = PhysicalSymptomFlag.ABDOMINAL_PAIN or
                PhysicalSymptomFlag.BLOATING,
        )

        val neutral = analyze(currentDate, observations = listOf(neutralObservation))
        val contextual = analyze(currentDate, observations = listOf(contextualObservation))

        assertEquals(neutral, contextual)
    }

    @Test
    fun `analysis is deterministic regardless of input order`() {
        val currentDate = currentCycleStart.plusDays(8)
        val temperatures = regularConfirmedPattern(currentCycleStart)
        val observations = listOf(
            observation(currentDate.minusDays(1), mucus = CervicalMucus.WATERY),
            observation(currentDate, lhResult = LhResult.POSITIVE),
        )

        val forward = analyze(currentDate, temperatures = temperatures, observations = observations)
        val reverse = analyze(
            currentDate,
            temperatures = temperatures.reversed(),
            observations = observations.reversed(),
        )

        assertEquals(forward, reverse)
    }

    @Test
    fun `daily relative weights are finite positive normalized and date sorted`() {
        val result = analyze(
            currentCycleStart.plusDays(10),
            previousCycles = stablePreviousCycles(4),
            observations = listOf(
                observation(currentCycleStart.plusDays(9), lhResult = LhResult.BORDERLINE),
            ),
        )
        val weights = result.dailyOvulationWeights

        assertTrue(weights.isNotEmpty())
        assertEquals(weights.map { it.date }.sorted(), weights.map { it.date })
        assertTrue(weights.all { it.relativeWeight.isFinite() && it.relativeWeight > 0.0 })
        assertEquals(1.0, weights.sumOf { it.relativeWeight }, 1e-9)
    }

    @Test
    fun `next period forecast without any data uses a focused default pair`() {
        val result = analyze(currentDate = currentCycleStart.plusDays(4))
        val forecast = requireNotNull(result.nextPeriodForecast)

        assertEquals(PeriodForecastBasis.DEFAULT_ESTIMATE, forecast.basis)
        assertEquals(
            currentCycleStart.plusDays(29)..currentCycleStart.plusDays(30),
            forecast.expectedStartRange,
        )
        assertNull(forecast.lutealDaysUsed)
        assertFalse(forecast.isOverdue)
    }

    @Test
    fun `next period forecast uses cycle length history without ovulation evidence`() {
        val result = analyze(
            currentDate = currentCycleStart.plusDays(4),
            previousCycles = stablePreviousCycles(3),
        )
        val forecast = requireNotNull(result.nextPeriodForecast)

        assertEquals(PeriodForecastBasis.CYCLE_LENGTH_HISTORY, forecast.basis)
        assertEquals(
            currentCycleStart.plusDays(28)..currentCycleStart.plusDays(29),
            forecast.expectedStartRange,
        )
        assertNull(forecast.lutealDaysUsed)
    }

    @Test
    fun `confirmed thermal shift anchors next period on ovulation plus default luteal`() {
        val temperatures = regularConfirmedPattern(currentCycleStart)
        val firstHigh = currentCycleStart.plusDays(6)
        val result = analyze(currentDate = firstHigh.plusDays(2), temperatures = temperatures)
        val forecast = requireNotNull(result.nextPeriodForecast)

        assertEquals(PeriodForecastBasis.OVULATION_AND_DEFAULT_LUTEAL, forecast.basis)
        assertEquals(13, forecast.lutealDaysUsed)
        // The best retrospective day is firstHigh-1; default luteal anchoring returns two days.
        assertEquals(
            firstHigh.plusDays(13)..firstHigh.plusDays(14),
            forecast.expectedStartRange,
        )
    }

    @Test
    fun `personal luteal length from history sharpens the period forecast`() {
        val previous = (0..1).map { index ->
            val start = LocalDate.of(2026, 1, 1).plusMonths(index.toLong())
            CycleWithAnalysis(
                cycle = cycle(start, 28),
                estimatedOvulationRange = start.plusDays(13)..start.plusDays(13),
                reliability = ForecastReliability.STRONG,
            )
        }
        val temperatures = regularConfirmedPattern(currentCycleStart)
        val firstHigh = currentCycleStart.plusDays(6)
        val result = analyze(
            currentDate = firstHigh.plusDays(2),
            previousCycles = previous,
            temperatures = temperatures,
        )
        val forecast = requireNotNull(result.nextPeriodForecast)

        assertEquals(PeriodForecastBasis.OVULATION_AND_PERSONAL_LUTEAL, forecast.basis)
        // Ovulation on cycle day 14 of a 28-day cycle leaves a 14-day personal luteal phase.
        assertEquals(14, forecast.lutealDaysUsed)
        assertEquals(
            firstHigh.plusDays(14)..firstHigh.plusDays(15),
            forecast.expectedStartRange,
        )
    }

    @Test
    fun `active LH surge anchors the period forecast before thermal confirmation`() {
        val firstPositive = currentCycleStart.plusDays(8)
        val result = analyze(
            currentDate = firstPositive.plusDays(1),
            observations = listOf(
                observation(firstPositive, lhResult = LhResult.POSITIVE),
                observation(firstPositive.plusDays(1), lhResult = LhResult.POSITIVE),
            ),
            typicalCycleLengthDays = 40,
        )
        val forecast = requireNotNull(result.nextPeriodForecast)

        assertEquals(PeriodForecastBasis.OVULATION_AND_DEFAULT_LUTEAL, forecast.basis)
        assertEquals(
            firstPositive.plusDays(15)..firstPositive.plusDays(16),
            forecast.expectedStartRange,
        )
    }

    @Test
    fun `period forecast reports overdue when the expected range has passed`() {
        val result = analyze(
            currentDate = currentCycleStart.plusDays(45),
            previousCycles = stablePreviousCycles(3),
        )
        val forecast = requireNotNull(result.nextPeriodForecast)

        assertEquals(PeriodForecastBasis.CYCLE_LENGTH_HISTORY, forecast.basis)
        assertTrue(forecast.isOverdue)
    }

    @Test
    fun `engine version and explanation are always included`() {
        val result = analyze(currentCycleStart)

        assertEquals("bbt-fusion-2.3.1", ENGINE_VERSION)
        assertEquals(ENGINE_VERSION, result.engineVersion)
        assertTrue(result.humanExplanation.isNotEmpty())
        assertTrue(result.humanExplanation.size <= 3)
    }

    private fun analyze(
        currentDate: LocalDate,
        previousCycles: List<CycleWithAnalysis> = emptyList(),
        temperatures: List<TemperatureMeasurement> = emptyList(),
        observations: List<com.yv.bbttracker.domain.model.DailyObservation> = emptyList(),
        typicalCycleLengthDays: Int? = null,
    ): CycleAnalysisResult = engine.analyze(
        CycleAnalysisInput(
            currentDate = currentDate,
            currentCycle = cycle(currentCycleStart),
            previousCycles = previousCycles,
            temperatures = temperatures,
            observations = observations,
            defaultMeasurementSite = MeasurementSite.ORAL,
            typicalCycleLengthDays = typicalCycleLengthDays,
        ),
    )

    private fun stablePreviousCycles(count: Int): List<CycleWithAnalysis> = (0 until count).map { index ->
        val start = LocalDate.of(2026, 1, 1).plusMonths(index.toLong())
        CycleWithAnalysis(cycle(start, 28))
    }

    private fun rangeDays(range: ClosedRange<LocalDate>): Long =
        range.endInclusive.toEpochDay() - range.start.toEpochDay() + 1
}
