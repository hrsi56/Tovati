package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import java.time.LocalDate
import kotlin.math.abs

/**
 * Single source of truth for converting raw completed cycles into evidence for the personal prior.
 * ViewModels should use this builder instead of independently re-running only the thermal rule.
 */
object HistoricalCycleBuilder {
    fun build(
        cycle: Cycle,
        measurements: List<TemperatureMeasurement>,
        observations: List<DailyObservation>,
        fallbackSite: MeasurementSite,
        asOfDate: LocalDate = cycle.endDate ?: LocalDate.now(),
    ): CycleWithAnalysis {
        val effectiveAsOf = minOf(asOfDate, cycle.endDate ?: asOfDate)
        val cycleMeasurements = measurements.filter { cycle.contains(it.date) && !it.date.isAfter(effectiveAsOf) }
        val cycleObservations = observations.filter { cycle.contains(it.date) && !it.date.isAfter(effectiveAsOf) }
        val analysisSite = cycle.analysisSite
            ?: ThermalShiftDetector.preferredSite(cycleMeasurements, fallbackSite)
        val thermal = ThermalShiftDetector.detect(cycleMeasurements, analysisSite, effectiveAsOf)
        val lh = LhSignalAnalyzer.analyze(cycleObservations, effectiveAsOf)
        val mucus = MucusSignalAnalyzer.analyze(cycleObservations, effectiveAsOf)

        val thermalRange = thermal?.takeIf { it.isConfirmed }?.estimatedOvulationRange
        val chosenLhEpisode = chooseLhEpisode(lh.episodes, thermalRange)
        val lhRange = chosenLhEpisode?.estimatedOvulationRange
        val mucusRange = mucus.peakDate?.let { it.minusDays(1)..it.plusDays(1) }
        val signs = linkedSetOf<AnalysisSignal>().apply {
            if (chosenLhEpisode != null) add(AnalysisSignal.LH_SURGE)
            if (mucus.peakDate != null) add(AnalysisSignal.MUCUS_PEAK)
            when (thermal?.state) {
                ThermalShiftState.CANDIDATE -> add(AnalysisSignal.THERMAL_CANDIDATE)
                ThermalShiftState.CONFIRMED -> add(AnalysisSignal.THERMAL_SHIFT)
                null -> Unit
            }
            if (thermal?.excludedUnreliableMeasurementCount?.let { it > 0 } == true) {
                add(AnalysisSignal.UNRELIABLE_TEMPERATURES_EXCLUDED)
            }
        }

        val signEstimate = when {
            lhRange != null && mucusRange != null && lhRange.intersects(mucusRange) ->
                lhRange.intersection(mucusRange)
            lhRange != null && mucusRange != null -> {
                signs += AnalysisSignal.CONFLICTING_SIGNALS
                null
            }
            lhRange != null -> lhRange
            mucusRange != null -> mucusRange
            else -> null
        }
        if (thermalRange != null && signEstimate != null && !thermalRange.intersects(signEstimate)) {
            signs += AnalysisSignal.CONFLICTING_SIGNALS
        }

        val estimate = FocusedEstimateSelector.retrospectiveDay(
            thermalShift = thermal,
            lhEpisode = chosenLhEpisode,
            mucusPeakDate = mucus.peakDate,
        )?.let { day -> day..day }
        val biologicalSignalCount = listOfNotNull(
            chosenLhEpisode,
            mucus.peakDate,
            thermal?.takeIf { it.isConfirmed },
        ).size
        val reliability = when {
            AnalysisSignal.CONFLICTING_SIGNALS in signs -> ForecastReliability.LIMITED
            thermal?.isConfirmed == true && biologicalSignalCount >= 2 -> ForecastReliability.STRONG
            thermal?.isConfirmed == true -> ForecastReliability.MODERATE
            chosenLhEpisode != null && mucus.peakDate != null -> ForecastReliability.MODERATE
            estimate != null -> ForecastReliability.LIMITED
            else -> ForecastReliability.INSUFFICIENT
        }

        return CycleWithAnalysis(
            cycle = cycle,
            confirmedThermalShiftFirstHighDate = thermal?.takeIf { it.isConfirmed }?.firstHighDate,
            estimatedOvulationRange = estimate,
            firstPositiveLhDate = chosenLhEpisode?.firstPositiveDate,
            mucusPeakDate = mucus.peakDate,
            reliability = reliability,
            signals = signs,
        )
    }

    fun buildAll(
        cycles: List<Cycle>,
        measurements: List<TemperatureMeasurement>,
        observations: List<DailyObservation>,
        fallbackSite: MeasurementSite,
        beforeDate: LocalDate? = null,
    ): List<CycleWithAnalysis> = cycles
        .asSequence()
        .filter { it.endDate != null }
        .filter { beforeDate == null || it.startDate.isBefore(beforeDate) }
        .sortedBy { it.startEpochDay }
        .map { cycle -> build(cycle, measurements, observations, fallbackSite) }
        .toList()

    private fun chooseLhEpisode(
        episodes: List<LhEpisode>,
        thermalRange: ClosedRange<LocalDate>?,
    ): LhEpisode? {
        if (thermalRange == null) return episodes.lastOrNull()
        val thermalMidpoint = (thermalRange.start.toEpochDay() + thermalRange.endInclusive.toEpochDay()) / 2L
        return episodes.minByOrNull { episode ->
            val range = episode.estimatedOvulationRange
            val episodeMidpoint = (range.start.toEpochDay() + range.endInclusive.toEpochDay()) / 2L
            abs(episodeMidpoint - thermalMidpoint)
        }
    }
}
