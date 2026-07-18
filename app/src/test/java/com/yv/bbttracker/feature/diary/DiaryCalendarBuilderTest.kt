package com.yv.bbttracker.feature.diary

import com.yv.bbttracker.domain.engine.CycleAnalysisResult
import com.yv.bbttracker.domain.engine.AnalysisSignal
import com.yv.bbttracker.domain.engine.DataQuality
import com.yv.bbttracker.domain.engine.EvidenceLevel
import com.yv.bbttracker.domain.engine.FertilityStatus
import com.yv.bbttracker.domain.engine.ForecastReliability
import com.yv.bbttracker.domain.engine.PeriodForecast
import com.yv.bbttracker.domain.engine.PeriodForecastBasis
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryCalendarBuilderTest {
    @Test
    fun currentCycleBuildsThroughForecastAndSeparatesProspectiveFromRetrospective() {
        val start = LocalDate.of(2026, 7, 1)
        val forecastRange = LocalDate.of(2026, 7, 28)..LocalDate.of(2026, 7, 29)
        val calendar = DiaryCalendarBuilder.build(
            cycle = cycle(1, start),
            allCycles = listOf(cycle(1, start)),
            measurements = emptyList(),
            observations = emptyList(),
            analysis = analysis(
                prospective = LocalDate.of(2026, 7, 17)..LocalDate.of(2026, 7, 18),
                conception = LocalDate.of(2026, 7, 12)..LocalDate.of(2026, 7, 18),
                forecast = PeriodForecast(
                    expectedStartRange = forecastRange,
                    basis = PeriodForecastBasis.DEFAULT_ESTIMATE,
                    lutealDaysUsed = null,
                    isOverdue = false,
                ),
            ),
            today = LocalDate.of(2026, 7, 10),
        )

        assertEquals(29, calendar.days.size)
        assertEquals(LocalDate.of(2026, 7, 27), calendar.expectedCurrentCycleEndRange?.start)
        assertEquals(LocalDate.of(2026, 7, 28), calendar.expectedCurrentCycleEndRange?.endInclusive)
        assertTrue(DiaryDayMarker.CONCEPTION_WINDOW in calendar.days.single { it.cycleDay == 12 }.markers)
        assertTrue(DiaryDayMarker.PROSPECTIVE_OVULATION in calendar.days.single { it.cycleDay == 18 }.markers)
        assertFalse(DiaryDayMarker.RETROSPECTIVE_OVULATION in calendar.days.single { it.cycleDay == 18 }.markers)
        assertTrue(DiaryDayMarker.POSSIBLE_NEXT_CYCLE_START in calendar.days.single { it.cycleDay == 29 }.markers)
    }

    @Test
    fun recordedDayCarriesEveryRecordAndSignalMarker() {
        val start = LocalDate.of(2026, 7, 1)
        val date = LocalDate.of(2026, 7, 14)
        val measurement = TemperatureMeasurement(
            id = 41,
            measurementEpochDay = date.toEpochDay(),
            measuredAtEpochMillis = date.atTime(6, 45).toInstant(ZoneOffset.UTC).toEpochMilli(),
            timezoneId = "UTC",
            temperatureCentiC = 3662,
            site = MeasurementSite.ORAL,
            selectedForAnalysis = true,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )
        val observation = DailyObservation(
            id = 22,
            epochDay = date.toEpochDay(),
            mucus = CervicalMucus.EGG_WHITE,
            mucusSensation = MucusSensation.SLIPPERY,
            lhResult = LhResult.POSITIVE,
            sexualContact = SexualContact.YES,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )

        val day = DiaryCalendarBuilder.build(
            cycle = cycle(1, start),
            allCycles = listOf(cycle(1, start)),
            measurements = listOf(measurement),
            observations = listOf(observation),
            analysis = analysis(),
            today = date,
        ).days.single { it.date == date }

        assertEquals(listOf(measurement), day.measurements)
        assertEquals(observation, day.observation)
        assertEquals(measurement, day.selectedMeasurement)
        assertTrue(day.hasRecordedData)
        assertTrue(DiaryDayMarker.LH_POSITIVE in day.markers)
        assertTrue(DiaryDayMarker.FERTILE_FLUID in day.markers)
        assertTrue(DiaryDayMarker.SEXUAL_CONTACT in day.markers)
    }

    @Test
    fun completedCycleStopsAtActualEndAndShowsKnownNextCycle() {
        val first = cycle(
            id = 1,
            start = LocalDate.of(2026, 5, 1),
            end = LocalDate.of(2026, 5, 28),
        )
        val second = cycle(id = 2, start = LocalDate.of(2026, 5, 29))
        val calendar = DiaryCalendarBuilder.build(
            cycle = first,
            allCycles = listOf(second, first),
            measurements = emptyList(),
            observations = emptyList(),
            analysis = analysis(
                retrospective = LocalDate.of(2026, 5, 14)..LocalDate.of(2026, 5, 14),
                forecast = PeriodForecast(
                    expectedStartRange = LocalDate.of(2026, 5, 29)..LocalDate.of(2026, 5, 30),
                    basis = PeriodForecastBasis.CYCLE_LENGTH_HISTORY,
                    lutealDaysUsed = null,
                    isOverdue = false,
                ),
            ),
            today = LocalDate.of(2026, 7, 1),
        )

        assertEquals(28, calendar.days.size)
        assertEquals(LocalDate.of(2026, 5, 29), calendar.knownNextCycleStart)
        assertEquals(null, calendar.expectedNextCycleStart)
        assertTrue(DiaryDayMarker.RETROSPECTIVE_OVULATION in calendar.days.single { it.cycleDay == 14 }.markers)
        assertTrue(calendar.days.none { DiaryDayMarker.POSSIBLE_NEXT_CYCLE_START in it.markers })
    }

    @Test
    fun persistedCycleStartIsTheOnlyDayOneMarker() {
        val start = LocalDate.of(2026, 7, 1)
        val mistakenSecondStart = DailyObservation(
            id = 33,
            epochDay = start.plusDays(3).toEpochDay(),
            isExplicitCycleStart = true,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1,
        )

        val calendar = DiaryCalendarBuilder.build(
            cycle = cycle(1, start),
            allCycles = listOf(cycle(1, start)),
            measurements = emptyList(),
            observations = listOf(mistakenSecondStart),
            analysis = analysis(),
            today = start.plusDays(3),
        )

        assertEquals(
            listOf(start),
            calendar.days.filter { DiaryDayMarker.CYCLE_START in it.markers }.map { it.date },
        )
    }

    @Test
    fun conflictKeepsPastDayAndNewForecastSeparateWithoutPaintingBetweenThem() {
        val start = LocalDate.of(2026, 7, 1)
        val retrospective = start.plusDays(13)
        val prospective = start.plusDays(29)..start.plusDays(30)
        val conception = start.plusDays(24)..prospective.endInclusive
        val calendar = DiaryCalendarBuilder.build(
            cycle = cycle(1, start),
            allCycles = listOf(cycle(1, start)),
            measurements = emptyList(),
            observations = emptyList(),
            analysis = analysis(
                prospective = prospective,
                conception = conception,
                retrospective = retrospective..retrospective,
                signals = setOf(AnalysisSignal.CONFLICTING_SIGNALS),
                forecast = PeriodForecast(
                    expectedStartRange = start.plusDays(28)..start.plusDays(29),
                    basis = PeriodForecastBasis.DEFAULT_ESTIMATE,
                    lutealDaysUsed = null,
                    isOverdue = false,
                ),
            ),
            today = start.plusDays(29),
        )

        assertEquals(
            listOf(retrospective),
            calendar.days.filter { DiaryDayMarker.RETROSPECTIVE_OVULATION in it.markers }.map { it.date },
        )
        assertEquals(
            listOf(prospective.start, prospective.endInclusive),
            calendar.days.filter { DiaryDayMarker.PROSPECTIVE_OVULATION in it.markers }.map { it.date },
        )
        assertEquals(prospective.endInclusive, calendar.days.last().date)
        assertEquals(
            7,
            calendar.days.count { DiaryDayMarker.CONCEPTION_WINDOW in it.markers },
        )
        assertTrue(
            calendar.days
                .filter { it.date in retrospective.plusDays(1)..prospective.start.minusDays(1) }
                .none { DiaryDayMarker.PROSPECTIVE_OVULATION in it.markers },
        )
    }

    private fun cycle(id: Long, start: LocalDate, end: LocalDate? = null) = Cycle(
        id = id,
        startEpochDay = start.toEpochDay(),
        endEpochDay = end?.toEpochDay(),
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private fun analysis(
        prospective: ClosedRange<LocalDate>? = null,
        conception: ClosedRange<LocalDate>? = null,
        retrospective: ClosedRange<LocalDate>? = null,
        signals: Set<AnalysisSignal> = emptySet(),
        forecast: PeriodForecast? = null,
    ) = CycleAnalysisResult(
        status = FertilityStatus.CALENDAR_ESTIMATE_ONLY,
        evidenceLevel = EvidenceLevel.CALENDAR_ONLY,
        dataQuality = DataQuality.MODERATE,
        predictedFertileWindow = conception,
        estimatedOvulationRange = retrospective ?: prospective,
        thermalShift = null,
        reasonCodes = emptyList(),
        humanExplanation = emptyList(),
        prospectiveOvulationRange = prospective,
        conceptionOpportunityWindow = conception,
        retrospectiveOvulationRange = retrospective,
        reliability = ForecastReliability.MODERATE,
        signals = signals,
        nextPeriodForecast = forecast,
    )
}
