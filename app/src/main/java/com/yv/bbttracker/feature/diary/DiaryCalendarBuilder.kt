package com.yv.bbttracker.feature.diary

import com.yv.bbttracker.domain.engine.CycleAnalysisResult
import com.yv.bbttracker.domain.engine.AnalysisSignal
import com.yv.bbttracker.domain.engine.ForecastReliability
import com.yv.bbttracker.domain.engine.PeriodForecast
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DiaryDayMarker {
    CYCLE_START,
    BLEEDING,
    SPOTTING,
    CONCEPTION_WINDOW,
    PROSPECTIVE_OVULATION,
    RETROSPECTIVE_OVULATION,
    POSSIBLE_MENSTRUATION_END,
    POSSIBLE_CYCLE_END,
    POSSIBLE_NEXT_CYCLE_START,
    LH_POSITIVE,
    FERTILE_FLUID,
    THERMAL_SHIFT_FIRST_HIGH,
    SEXUAL_CONTACT,
}

data class DiaryDay(
    val cycleDay: Int,
    val date: LocalDate,
    val measurements: List<TemperatureMeasurement>,
    val observation: DailyObservation?,
    val markers: Set<DiaryDayMarker>,
    val isToday: Boolean,
    val isFuture: Boolean,
) {
    val hasRecordedData: Boolean get() = measurements.isNotEmpty() || observation != null
    val selectedMeasurement: TemperatureMeasurement?
        get() = measurements.firstOrNull { it.selectedForAnalysis } ?: measurements.firstOrNull()
}

data class DiaryCycleOption(
    val id: Long,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val isCurrent: Boolean,
)

data class DiaryCalendar(
    val cycle: Cycle,
    val options: List<DiaryCycleOption>,
    val days: List<DiaryDay>,
    val analysis: CycleAnalysisResult,
    val knownNextCycleStart: LocalDate?,
    val expectedMenstruationEndDate: LocalDate?,
    val expectedCurrentCycleEndRange: ClosedRange<LocalDate>?,
    val expectedNextCycleStart: PeriodForecast?,
    val currentCycleDay: Int,
) {
    val isCurrent: Boolean get() = cycle.endDate == null
    val actualCycleLength: Int?
        get() = cycle.endDate?.let { ChronoUnit.DAYS.between(cycle.startDate, it).toInt() + 1 }
    val reliability: ForecastReliability get() = analysis.reliability
}

object DiaryCalendarBuilder {
    fun build(
        cycle: Cycle,
        allCycles: List<Cycle>,
        measurements: List<TemperatureMeasurement>,
        observations: List<DailyObservation>,
        analysis: CycleAnalysisResult,
        today: LocalDate,
        typicalMenstruationLengthDays: Int? = null,
    ): DiaryCalendar {
        val options = allCycles
            .sortedByDescending { it.startEpochDay }
            .map {
                DiaryCycleOption(
                    id = it.id,
                    startDate = it.startDate,
                    endDate = it.endDate,
                    isCurrent = it.endDate == null,
                )
            }
        val nextCycleStart = allCycles
            .asSequence()
            .map(Cycle::startDate)
            .filter { it.isAfter(cycle.startDate) }
            .minOrNull()
        val forecast = analysis.nextPeriodForecast.takeIf { cycle.endDate == null }
        val cycleEndRange = forecast?.expectedStartRange?.let {
            it.start.minusDays(1)..it.endInclusive.minusDays(1)
        }
        val menstruationEndDate = typicalMenstruationLengthDays
            ?.takeIf { cycle.endDate == null }
            ?.let { days -> cycle.startDate.plusDays((days - 1).toLong()) }
        // Past cycles only show what was recorded and the single retrospective estimate. In the
        // current cycle, keep a new prospective forecast visible alongside a past estimate only
        // when the engine explicitly detected conflicting/new evidence.
        val showProspectiveForecast = cycle.endDate == null && (
            analysis.retrospectiveOvulationRange == null ||
                AnalysisSignal.CONFLICTING_SIGNALS in analysis.signals
            )
        val displayEnd = cycle.endDate
            ?: maxOf(
                today,
                menstruationEndDate ?: today,
                forecast?.expectedStartRange?.endInclusive ?: today,
                analysis.prospectiveOvulationRange
                    ?.endInclusive
                    ?.takeIf { showProspectiveForecast } ?: today,
                analysis.conceptionOpportunityWindow
                    ?.endInclusive
                    ?.takeIf { showProspectiveForecast } ?: today,
                measurements.maxOfOrNull(TemperatureMeasurement::date) ?: cycle.startDate,
                observations.maxOfOrNull(DailyObservation::date) ?: cycle.startDate,
            )
        val measurementsByDate = measurements
            .filter { !it.date.isBefore(cycle.startDate) && !it.date.isAfter(displayEnd) }
            .sortedBy { it.measuredAtEpochMillis }
            .groupBy(TemperatureMeasurement::date)
        val observationsByDate = observations
            .filter { !it.date.isBefore(cycle.startDate) && !it.date.isAfter(displayEnd) }
            .associateBy(DailyObservation::date)
        val days = generateSequence(cycle.startDate) { date ->
            date.plusDays(1).takeUnless { it.isAfter(displayEnd) }
        }.mapIndexed { index, date ->
            val observation = observationsByDate[date]
            val markers = buildSet {
                // The cycle's persisted start is the single authoritative day 1 entered by the
                // user. An observation flag elsewhere must not create a second start marker.
                if (date == cycle.startDate) {
                    add(DiaryDayMarker.CYCLE_START)
                }
                when (observation?.bleeding) {
                    BleedingLevel.SPOTTING -> add(DiaryDayMarker.SPOTTING)
                    BleedingLevel.LIGHT, BleedingLevel.MEDIUM, BleedingLevel.HEAVY ->
                        add(DiaryDayMarker.BLEEDING)
                    else -> Unit
                }
                if (
                    showProspectiveForecast &&
                    analysis.conceptionOpportunityWindow?.contains(date) == true
                ) {
                    add(DiaryDayMarker.CONCEPTION_WINDOW)
                }
                if (
                    showProspectiveForecast &&
                    analysis.prospectiveOvulationRange?.contains(date) == true
                ) {
                    add(DiaryDayMarker.PROSPECTIVE_OVULATION)
                }
                if (analysis.retrospectiveOvulationRange?.contains(date) == true) {
                    add(DiaryDayMarker.RETROSPECTIVE_OVULATION)
                }
                if (date == menstruationEndDate) {
                    add(DiaryDayMarker.POSSIBLE_MENSTRUATION_END)
                }
                if (cycleEndRange?.contains(date) == true) {
                    add(DiaryDayMarker.POSSIBLE_CYCLE_END)
                }
                if (forecast?.expectedStartRange?.contains(date) == true) {
                    add(DiaryDayMarker.POSSIBLE_NEXT_CYCLE_START)
                }
                if (observation?.lhResult == LhResult.POSITIVE) {
                    add(DiaryDayMarker.LH_POSITIVE)
                }
                if (
                    observation?.mucusObscured == false &&
                    (
                        observation.mucus == CervicalMucus.EGG_WHITE ||
                            observation.mucusSensation == MucusSensation.SLIPPERY
                        )
                ) {
                    add(DiaryDayMarker.FERTILE_FLUID)
                }
                if (analysis.thermalShift?.firstHighDate == date) {
                    add(DiaryDayMarker.THERMAL_SHIFT_FIRST_HIGH)
                }
                if (
                    observation?.sexualContact == SexualContact.SOME ||
                    observation?.sexualContact == SexualContact.YES
                ) {
                    add(DiaryDayMarker.SEXUAL_CONTACT)
                }
            }
            DiaryDay(
                cycleDay = index + 1,
                date = date,
                measurements = measurementsByDate[date].orEmpty(),
                observation = observation,
                markers = markers,
                isToday = date == today,
                isFuture = date.isAfter(today),
            )
        }.toList()

        return DiaryCalendar(
            cycle = cycle,
            options = options,
            days = days,
            analysis = analysis,
            knownNextCycleStart = nextCycleStart,
            expectedMenstruationEndDate = menstruationEndDate,
            expectedCurrentCycleEndRange = cycleEndRange,
            expectedNextCycleStart = forecast,
            currentCycleDay = (
                ChronoUnit.DAYS.between(cycle.startDate, minOf(today, displayEnd)).toInt() + 1
                ).coerceAtLeast(1),
        )
    }
}
