package com.yv.bbttracker.domain.engine

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Personal luteal-phase statistics derived from completed historical cycles that carry both a
 * cycle length and a retrospective ovulation estimate. Median and MAD keep a single outlier
 * cycle from dominating the profile.
 */
data class LutealPhaseStats(
    val medianDays: Double,
    val madDays: Double,
    val sampleSize: Int,
)

/** Population-shrunk estimate; a single personal cycle can inform it without dominating it. */
internal data class LutealPhaseEstimate(
    val centerDays: Double,
    val spreadDays: Double,
    val personalSampleSize: Int,
)

enum class PeriodForecastBasis {
    /** Ovulation estimate in the current cycle plus the personal median luteal length. */
    OVULATION_AND_PERSONAL_LUTEAL,

    /** Ovulation estimate in the current cycle plus a typical default luteal length. */
    OVULATION_AND_DEFAULT_LUTEAL,

    /** No usable ovulation estimate yet; the personal cycle-length history is used instead. */
    CYCLE_LENGTH_HISTORY,

    /** Early estimate based only on the cycle length supplied during onboarding. */
    SELF_REPORTED_CYCLE_LENGTH,

    /** Sparse recorded history blended with the onboarding cycle-length estimate. */
    REPORTED_AND_CYCLE_LENGTH_HISTORY,

    /** Neither an ovulation estimate nor any completed cycle exists; focused default pair. */
    DEFAULT_ESTIMATE,
}

/**
 * Two-day expected start range for the next menstruation. Like every engine output this is a
 * transparent heuristic estimate, never a guarantee. A range that has already passed is reported through
 * [isOverdue] instead of being silently clamped forward.
 */
data class PeriodForecast(
    val expectedStartRange: ClosedRange<LocalDate>,
    val basis: PeriodForecastBasis,
    /** Luteal length in days the estimate used, when the basis is ovulation-anchored. */
    val lutealDaysUsed: Int?,
    val isOverdue: Boolean,
)

/**
 * Predicts the next period start. Once the current cycle has an ovulation estimate, the luteal
 * phase is the right anchor: follicular length varies far more than luteal length, so
 * "ovulation + personal luteal" stays accurate even in a cycle whose first half was unusual.
 * Without an ovulation estimate the forecast falls back to cycle-length history.
 */
object PeriodForecastCalculator {
    /** Real-world large-scale tracking data places the median luteal phase near 13 days, not 14. */
    const val DEFAULT_LUTEAL_DAYS = 13
    /** Matches the default cycle length assumed by the personal prior. */
    const val DEFAULT_CYCLE_LENGTH_DAYS = 29
    private const val MIN_PLAUSIBLE_LUTEAL_DAYS = 7
    private const val MAX_PLAUSIBLE_LUTEAL_DAYS = 20
    private const val MIN_SAMPLES_FOR_PERSONAL_LUTEAL = 2
    private const val HISTORY_SAMPLES_TO_REPLACE_REPORTED_LENGTH = 3
    private const val POPULATION_PRIOR_WEIGHT = 1.5
    private const val DEFAULT_LUTEAL_SPREAD_DAYS = 1.75

    fun lutealStats(previousCycles: List<CycleWithAnalysis>): LutealPhaseStats? {
        val samples = previousCycles.mapNotNull { historical ->
            historical.lutealPhaseDays
                ?.takeIf { it in MIN_PLAUSIBLE_LUTEAL_DAYS..MAX_PLAUSIBLE_LUTEAL_DAYS }
                ?.toDouble()
        }
        if (samples.size < MIN_SAMPLES_FOR_PERSONAL_LUTEAL) return null
        val center = median(samples)
        return LutealPhaseStats(
            medianDays = center,
            madDays = median(samples.map { abs(it - center) }),
            sampleSize = samples.size,
        )
    }

    /**
     * Uses every plausible observed personal sample, including the first one, but shrinks sparse
     * history toward the population center. This is learning from a real completed cycle, not
     * filling a missing cycle or inventing a luteal length.
     */
    internal fun lutealEstimate(previousCycles: List<CycleWithAnalysis>): LutealPhaseEstimate {
        val samples = previousCycles.mapNotNull { historical ->
            val days = historical.lutealPhaseDays
                ?.takeIf { it in MIN_PLAUSIBLE_LUTEAL_DAYS..MAX_PLAUSIBLE_LUTEAL_DAYS }
                ?.toDouble()
                ?: return@mapNotNull null
            val reliabilityWeight = when (historical.reliability) {
                ForecastReliability.STRONG -> 1.0
                ForecastReliability.MODERATE -> 0.80
                ForecastReliability.LIMITED -> 0.45
                ForecastReliability.INSUFFICIENT -> 0.0
            }
            days to reliabilityWeight
        }.filter { (_, weight) -> weight > 0.0 }

        val personalWeight = samples.sumOf { (_, weight) -> weight }
        val center = (
            DEFAULT_LUTEAL_DAYS * POPULATION_PRIOR_WEIGHT +
                samples.sumOf { (days, weight) -> days * weight }
            ) / (POPULATION_PRIOR_WEIGHT + personalWeight)
        val personalValues = samples.map { (days, _) -> days }
        val spread = if (personalValues.size >= 2) {
            val personalCenter = median(personalValues)
            max(1.0, 1.4826 * median(personalValues.map { abs(it - personalCenter) }))
        } else {
            DEFAULT_LUTEAL_SPREAD_DAYS
        }
        return LutealPhaseEstimate(
            centerDays = center,
            spreadDays = spread,
            personalSampleSize = samples.size,
        )
    }

    fun forecast(
        currentCycleStart: LocalDate,
        currentDate: LocalDate,
        previousCycles: List<CycleWithAnalysis>,
        ovulationEstimate: ClosedRange<LocalDate>?,
        typicalCycleLengthDays: Int? = null,
        /** Optional current-cycle distribution, supplied only when a biological sign supports it. */
        ovulationWeights: Map<LocalDate, Double>? = null,
    ): PeriodForecast {
        val completedLengths = previousCycles.mapNotNull { it.cycleLengthDays?.toDouble() }
        val luteal = lutealEstimate(previousCycles)
        val cycleLengthEstimate = cycleLengthEstimate(
            completedLengths = completedLengths,
            typicalCycleLengthDays = typicalCycleLengthDays,
        )

        val ovulationDistribution = when {
            ovulationEstimate != null -> ovulationEstimate.dates().associateWith { 1.0 }
            !ovulationWeights.isNullOrEmpty() -> ovulationWeights
            else -> null
        }?.normalizedWeights()

        // Convolve the observed ovulation evidence with a luteal-length distribution. This keeps
        // uncertainty instead of collapsing both quantities to one midpoint before calculating.
        val (bestStart, basis, lutealDaysUsed) = when {
            !ovulationDistribution.isNullOrEmpty() -> {
                val periodWeights = linkedMapOf<LocalDate, Double>()
                ovulationDistribution.forEach { (ovulationDate, ovulationWeight) ->
                    (MIN_PLAUSIBLE_LUTEAL_DAYS..MAX_PLAUSIBLE_LUTEAL_DAYS).forEach { lutealDays ->
                        val lutealWeight = gaussian(
                            lutealDays.toDouble(),
                            luteal.centerDays,
                            luteal.spreadDays,
                        )
                        val periodDate = ovulationDate.plusDays(lutealDays + 1L)
                        periodWeights[periodDate] =
                            (periodWeights[periodDate] ?: 0.0) + ovulationWeight * lutealWeight
                    }
                }
                val bestRange = requireNotNull(bestTwoDayPeriodRange(periodWeights))
                Triple(
                    bestRange.start,
                    if (luteal.personalSampleSize > 0) {
                        PeriodForecastBasis.OVULATION_AND_PERSONAL_LUTEAL
                    } else {
                        PeriodForecastBasis.OVULATION_AND_DEFAULT_LUTEAL
                    },
                    luteal.centerDays.roundToLong().toInt(),
                )
            }

            cycleLengthEstimate != null -> {
                // A cycle of N days occupies days 1..N, so the next period starts N days after
                // the current start.
                val center = currentCycleStart.plusDays(cycleLengthEstimate.days.roundToLong())
                Triple(
                    center,
                    cycleLengthEstimate.basis,
                    null,
                )
            }

            else -> {
                val center = currentCycleStart.plusDays(DEFAULT_CYCLE_LENGTH_DAYS.toLong())
                Triple(
                    center,
                    PeriodForecastBasis.DEFAULT_ESTIMATE,
                    null,
                )
            }
        }
        val range = bestStart..bestStart.plusDays(1)

        return PeriodForecast(
            expectedStartRange = range,
            basis = basis,
            lutealDaysUsed = lutealDaysUsed,
            isOverdue = currentDate.isAfter(range.endInclusive),
        )
    }

    internal fun cycleLengthEstimate(
        completedLengths: List<Double>,
        typicalCycleLengthDays: Int?,
    ): CycleLengthEstimate? {
        val reported = typicalCycleLengthDays?.toDouble()
        val historicalMedian = completedLengths.takeIf { it.isNotEmpty() }?.let(::median)
        return when {
            historicalMedian == null && reported != null -> CycleLengthEstimate(
                days = reported,
                basis = PeriodForecastBasis.SELF_REPORTED_CYCLE_LENGTH,
            )
            historicalMedian != null &&
                reported != null &&
                completedLengths.size < HISTORY_SAMPLES_TO_REPLACE_REPORTED_LENGTH -> {
                CycleLengthEstimate(
                    days = (
                        historicalMedian * completedLengths.size.toDouble() + reported
                        ) / (completedLengths.size + 1).toDouble(),
                    basis = PeriodForecastBasis.REPORTED_AND_CYCLE_LENGTH_HISTORY,
                )
            }
            historicalMedian != null -> CycleLengthEstimate(
                days = historicalMedian,
                basis = PeriodForecastBasis.CYCLE_LENGTH_HISTORY,
            )
            else -> null
        }
    }
}

internal data class CycleLengthEstimate(
    val days: Double,
    val basis: PeriodForecastBasis,
)

private fun ClosedRange<LocalDate>.midpoint(): LocalDate =
    LocalDate.ofEpochDay((start.toEpochDay() + endInclusive.toEpochDay()) / 2L)

private fun ClosedRange<LocalDate>.dates(): List<LocalDate> = buildList {
    var date = start
    while (!date.isAfter(endInclusive)) {
        add(date)
        date = date.plusDays(1)
    }
}

private fun Map<LocalDate, Double>.normalizedWeights(): Map<LocalDate, Double> {
    val clean = entries.filter { (_, weight) -> weight.isFinite() && weight > 0.0 }
    val total = clean.sumOf(Map.Entry<LocalDate, Double>::value)
    if (clean.isEmpty() || total <= 0.0 || !total.isFinite()) return emptyMap()
    return clean.associate { (date, weight) -> date to weight / total }
}

private fun gaussian(x: Double, center: Double, spread: Double): Double =
    exp(-0.5 * ((x - center) / spread).pow(2.0))

/** Display the modal start date and the following day, while deriving the mode by convolution. */
private fun bestTwoDayPeriodRange(weights: Map<LocalDate, Double>): ClosedRange<LocalDate>? {
    val clean = weights.filterValues { it.isFinite() && it > 0.0 }
    val start = clean.entries
        .maxWithOrNull(
            compareBy<Map.Entry<LocalDate, Double>> { it.value }
                .thenBy { it.key.toEpochDay() },
        )
        ?.key
        ?: return null
    return start..start.plusDays(1)
}

internal fun median(values: List<Double>): Double {
    require(values.isNotEmpty())
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle]
    else (sorted[middle - 1] + sorted[middle]) / 2.0
}
