package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Limited retrospective support for a visible two-level temperature pattern when the strict
 * three-over-six confirmation rule cannot run because an otherwise useful chart has gaps.
 *
 * This detector never creates or interpolates a temperature. It requires five observed baseline
 * measurements and three observed higher measurements, and its result must never be described as
 * a confirmed thermal shift. The strict [ThermalShiftDetector] remains the confirmation layer.
 */
internal object ThermalTrendDetector {
    private const val MIN_BASELINE_OBSERVATIONS = 5
    private const val MAX_BASELINE_OBSERVATIONS = 6
    private const val BASELINE_LOOKBACK_DAYS = 10L
    private const val MIN_HIGH_OBSERVATIONS = 3
    private const val HIGH_WINDOW_DAYS = 5L
    private const val MIN_LEVEL_CHANGE_CENTI_C = 15
    private const val MIN_HIGH_MARGIN_CENTI_C = 10

    fun detect(
        measurements: List<TemperatureMeasurement>,
        defaultMeasurementSite: MeasurementSite,
        asOfDate: LocalDate,
    ): ThermalTrendResult? {
        val valid = ThermalShiftDetector.chooseValidMeasurements(
            measurements = measurements.filter { !it.date.isAfter(asOfDate) },
            defaultMeasurementSite = defaultMeasurementSite,
        )
        if (valid.size < MIN_BASELINE_OBSERVATIONS + MIN_HIGH_OBSERVATIONS) return null

        return valid.mapIndexedNotNull { index, possibleFirstHigh ->
            val baselineStart = possibleFirstHigh.date.minusDays(BASELINE_LOOKBACK_DAYS)
            val baseline = valid
                .subList(0, index)
                .filter { measurement ->
                    !measurement.date.isBefore(baselineStart) &&
                        measurement.date.isBefore(possibleFirstHigh.date)
                }
                .takeLast(MAX_BASELINE_OBSERVATIONS)
            if (baseline.size < MIN_BASELINE_OBSERVATIONS) return@mapIndexedNotNull null

            val highWindowEnd = minOf(asOfDate, possibleFirstHigh.date.plusDays(HIGH_WINDOW_DAYS - 1))
            val observedAfter = valid.filter { measurement ->
                measurement.date in possibleFirstHigh.date..highWindowEnd
            }
            if (observedAfter.size < MIN_HIGH_OBSERVATIONS) return@mapIndexedNotNull null
            if (ChronoUnit.DAYS.between(observedAfter.first().date, observedAfter.last().date) < 2L) {
                return@mapIndexedNotNull null
            }

            val baselineCenter = medianCentiC(baseline)
            val highCenter = medianCentiC(observedAfter)
            val levelChange = highCenter - baselineCenter
            if (levelChange < MIN_LEVEL_CHANGE_CENTI_C) return@mapIndexedNotNull null

            val supportingHighs = observedAfter.filter { measurement ->
                measurement.temperatureCentiC >= baselineCenter + MIN_HIGH_MARGIN_CENTI_C
            }
            if (supportingHighs.size < MIN_HIGH_OBSERVATIONS) return@mapIndexedNotNull null

            val observedSpanDays = ChronoUnit.DAYS.between(
                possibleFirstHigh.date,
                observedAfter.last().date,
            ).toInt() + 1
            val missingWithinHighWindow = (observedSpanDays - observedAfter.size).coerceAtLeast(0)
            ScoredThermalTrend(
                result = ThermalTrendResult(
                    firstHighDate = possibleFirstHigh.date,
                    baselineCentiC = baselineCenter,
                    observedHighDates = supportingHighs.map(TemperatureMeasurement::date),
                    estimatedOvulationRange = possibleFirstHigh.date.minusDays(2)..possibleFirstHigh.date,
                    missingDaysWithinHighWindow = missingWithinHighWindow,
                ),
                score = levelChange * 2 + supportingHighs.size * 5 - missingWithinHighWindow * 3,
            )
        }.maxWithOrNull(
            compareBy<ScoredThermalTrend> { it.score }
                .thenBy { -it.result.firstHighDate.toEpochDay() },
        )?.result
    }

    private fun medianCentiC(measurements: List<TemperatureMeasurement>): Int {
        val sorted = measurements.map(TemperatureMeasurement::temperatureCentiC).sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else (sorted[middle - 1].toLong() + sorted[middle].toLong()).div(2L).toInt()
    }

    private data class ScoredThermalTrend(
        val result: ThermalTrendResult,
        val score: Int,
    )
}

internal data class ThermalTrendResult(
    val firstHighDate: LocalDate,
    val baselineCentiC: Int,
    val observedHighDates: List<LocalDate>,
    val estimatedOvulationRange: ClosedRange<LocalDate>,
    val missingDaysWithinHighWindow: Int,
)
