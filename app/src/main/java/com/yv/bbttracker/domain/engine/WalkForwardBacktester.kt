package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Result of replaying one completed cycle day by day with only the data known on each day. */
data class CycleBacktestResult(
    val cycle: Cycle,
    /** Start of the immediately following cycle, as explicitly stored in the diary. */
    val actualNextPeriodDate: LocalDate,
    val referenceOvulationRange: ClosedRange<LocalDate>?,
    val referenceReliability: ForecastReliability,
    /** First replay day whose period forecast became anchored on ovulation evidence, if any. */
    val anchorDate: LocalDate?,
    val anchorBasis: PeriodForecastBasis?,
    val periodRangeAtAnchor: ClosedRange<LocalDate>?,
    /** Signed days from the anchored range to the actual next period; zero means inside it. */
    val periodErrorDaysAtAnchor: Int?,
    /** How many days before the actual period the anchored forecast was already available. */
    val leadDaysAtAnchor: Int?,
    val finalPeriodRange: ClosedRange<LocalDate>?,
    val finalPeriodErrorDays: Int?,
) {
    val periodHitAtAnchor: Boolean? get() = periodErrorDaysAtAnchor?.let { it == 0 }
    val finalPeriodHit: Boolean? get() = finalPeriodErrorDays?.let { it == 0 }
}

data class BacktestSummary(val cycleResults: List<CycleBacktestResult>) {
    val evaluatedCycleCount: Int get() = cycleResults.size
    val anchoredCycleCount: Int get() = cycleResults.count { it.anchorDate != null }
    val anchoredPeriodHitCount: Int get() = cycleResults.count { it.periodHitAtAnchor == true }

    val medianAbsErrorDaysAtAnchor: Double?
        get() = cycleResults
            .mapNotNull { result -> result.periodErrorDaysAtAnchor?.let { abs(it).toDouble() } }
            .takeIf { it.isNotEmpty() }
            ?.let(::median)

    val medianLeadDaysAtAnchor: Double?
        get() = cycleResults
            .mapNotNull { it.leadDaysAtAnchor?.toDouble() }
            .takeIf { it.isNotEmpty() }
            ?.let(::median)
}

/**
 * Walk-forward replay as described in the forecast target document: every completed cycle is
 * re-analyzed one day at a time using only the data recorded up to that day, and the resulting
 * period forecasts are compared with the actual next period start. The engine is deterministic
 * and already scopes its inputs to the analysis date, so the replay reproduces exactly what the
 * app would have shown on each day — no stored snapshots are required.
 */
object WalkForwardBacktester {
    private val OVULATION_ANCHORED_BASES = setOf(
        PeriodForecastBasis.OVULATION_AND_PERSONAL_LUTEAL,
        PeriodForecastBasis.OVULATION_AND_DEFAULT_LUTEAL,
    )

    fun backtest(
        cycles: List<Cycle>,
        measurements: List<TemperatureMeasurement>,
        observations: List<DailyObservation>,
        fallbackSite: MeasurementSite,
        engine: CycleAnalysisEngine = CycleAnalysisEngine(),
    ): BacktestSummary {
        val ordered = cycles.sortedBy { it.startEpochDay }
        val evaluable = ordered.mapIndexedNotNull { index, cycle ->
            val endDate = cycle.endDate ?: return@mapIndexedNotNull null
            val nextCycle = ordered.getOrNull(index + 1) ?: return@mapIndexedNotNull null
            // An actual period outcome exists only when the next stored cycle starts immediately
            // after this cycle. A gap can mean that an intermediate cycle was deleted or missing;
            // inferring a period date from endDate alone would create a phantom ground truth.
            nextCycle.takeIf { it.startDate == endDate.plusDays(1) }?.let { cycle to it.startDate }
        }
        return BacktestSummary(
            evaluable.map { (cycle, actualNextPeriodDate) ->
                backtestCycle(
                    cycle = cycle,
                    actualNextPeriodDate = actualNextPeriodDate,
                    cycles = cycles,
                    measurements = measurements,
                    observations = observations,
                    fallbackSite = fallbackSite,
                    engine = engine,
                )
            },
        )
    }

    private fun backtestCycle(
        cycle: Cycle,
        actualNextPeriodDate: LocalDate,
        cycles: List<Cycle>,
        measurements: List<TemperatureMeasurement>,
        observations: List<DailyObservation>,
        fallbackSite: MeasurementSite,
        engine: CycleAnalysisEngine,
    ): CycleBacktestResult {
        val endDate = requireNotNull(cycle.endDate)
        val cycleMeasurements = measurements.filter { cycle.contains(it.date) }
        val cycleObservations = observations.filter { cycle.contains(it.date) }
        val site = cycle.analysisSite
            ?: ThermalShiftDetector.preferredSite(cycleMeasurements, fallbackSite)
        val previous = HistoricalCycleBuilder.buildAll(
            cycles = cycles.filter { it.id != cycle.id },
            measurements = measurements,
            observations = observations,
            fallbackSite = fallbackSite,
            beforeDate = cycle.startDate,
        )
        val reference = HistoricalCycleBuilder.build(
            cycle = cycle,
            measurements = cycleMeasurements,
            observations = cycleObservations,
            fallbackSite = fallbackSite,
        )

        fun forecastAsOf(day: LocalDate): PeriodForecast? = engine.analyze(
            CycleAnalysisInput(
                currentDate = day,
                currentCycle = cycle,
                previousCycles = previous,
                temperatures = cycleMeasurements,
                observations = cycleObservations,
                defaultMeasurementSite = site,
            ),
        ).nextPeriodForecast

        var anchorDate: LocalDate? = null
        var anchorForecast: PeriodForecast? = null
        var day = cycle.startDate
        while (!day.isAfter(endDate)) {
            val forecast = forecastAsOf(day)
            if (forecast != null && forecast.basis in OVULATION_ANCHORED_BASES) {
                anchorDate = day
                anchorForecast = forecast
                break
            }
            day = day.plusDays(1)
        }
        val finalForecast = forecastAsOf(endDate)

        return CycleBacktestResult(
            cycle = cycle,
            actualNextPeriodDate = actualNextPeriodDate,
            referenceOvulationRange = reference.estimatedOvulationRange,
            referenceReliability = reference.reliability,
            anchorDate = anchorDate,
            anchorBasis = anchorForecast?.basis,
            periodRangeAtAnchor = anchorForecast?.expectedStartRange,
            periodErrorDaysAtAnchor = anchorForecast
                ?.let { signedDistance(actualNextPeriodDate, it.expectedStartRange) },
            leadDaysAtAnchor = anchorDate
                ?.let { ChronoUnit.DAYS.between(it, actualNextPeriodDate).toInt() },
            finalPeriodRange = finalForecast?.expectedStartRange,
            finalPeriodErrorDays = finalForecast
                ?.let { signedDistance(actualNextPeriodDate, it.expectedStartRange) },
        )
    }

    private fun signedDistance(date: LocalDate, range: ClosedRange<LocalDate>): Int = when {
        date.isBefore(range.start) -> -ChronoUnit.DAYS.between(date, range.start).toInt()
        date.isAfter(range.endInclusive) -> ChronoUnit.DAYS.between(range.endInclusive, date).toInt()
        else -> 0
    }
}
