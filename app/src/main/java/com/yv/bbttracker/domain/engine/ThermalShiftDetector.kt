package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.DisturbanceFlag
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import java.time.LocalDate

/** Pure Kotlin, conservative thermal-pattern detector. */
object ThermalShiftDetector {
    const val HIGH_THRESHOLD_CENTI_C = 10
    const val THIRD_HIGH_CONFIRMATION_CENTI_C = 20
    const val REQUIRED_BASELINE_MEASUREMENTS = 6
    const val BASELINE_LOOKBACK_DAYS = 8L
    const val MIN_RELIABLE_SLEEP_MINUTES = 180
    const val PERSISTENCE_LOOKAHEAD_DAYS = 4L

    /**
     * Keeps a cycle's comparison site stable after its first selected measurement. Callers should
     * prefer a site explicitly persisted on the cycle when one exists.
     */
    fun preferredSite(
        measurements: List<TemperatureMeasurement>,
        fallback: MeasurementSite,
    ): MeasurementSite = measurements
        .asSequence()
        .filter { it.selectedForAnalysis }
        .minWithOrNull(
            compareBy<TemperatureMeasurement> { it.measurementEpochDay }
                .thenBy { it.measuredAtEpochMillis }
                .thenBy { it.createdAtEpochMillis }
                .thenBy { it.id },
        )
        ?.site
        ?: fallback

    /**
     * Returns at most one reliable selected measurement per date at [defaultMeasurementSite].
     * Fever, less than three hours of sleep, and an explicitly non-immediate measurement are too
     * unreliable to confirm a thermal transition even when accidentally left selected.
     */
    fun chooseValidMeasurements(
        measurements: List<TemperatureMeasurement>,
        defaultMeasurementSite: MeasurementSite,
    ): List<TemperatureMeasurement> = measurements
        .asSequence()
        .filter { it.selectedForAnalysis }
        .filter { it.site == defaultMeasurementSite }
        .filter(::isReliableForThermalConfirmation)
        .groupBy { it.measurementEpochDay }
        .values
        .map { sameDay ->
            sameDay.maxWith(
                compareBy<TemperatureMeasurement> { it.updatedAtEpochMillis }
                    .thenBy { it.measuredAtEpochMillis }
                    .thenBy { it.id },
            )
        }
        .sortedBy { it.measurementEpochDay }

    fun unreliableSelectedMeasurements(
        measurements: List<TemperatureMeasurement>,
        defaultMeasurementSite: MeasurementSite,
    ): List<TemperatureMeasurement> = measurements.filter { measurement ->
        measurement.selectedForAnalysis &&
            measurement.site == defaultMeasurementSite &&
            !isReliableForThermalConfirmation(measurement)
    }

    fun isReliableForThermalConfirmation(measurement: TemperatureMeasurement): Boolean {
        if (measurement.disturbanceMask.hasFlag(DisturbanceFlag.ILLNESS_OR_FEVER)) return false
        if (measurement.disturbanceMask.hasFlag(DisturbanceFlag.DIFFERENT_MEASUREMENT_SITE)) return false
        if (measurement.sleepMinutes != null && measurement.sleepMinutes < MIN_RELIABLE_SLEEP_MINUTES) return false
        if (measurement.measuredImmediatelyAfterWaking == false) return false
        if (measurement.disturbanceMask.hasFlag(DisturbanceFlag.NOT_IMMEDIATELY_AFTER_WAKING)) return false
        return true
    }

    /**
     * Scans the complete input instead of returning the first three-high candidate. A failed fourth
     * day is rejected, and confirmed patterns contradicted by at least two subsequent lows in the
     * next four days are discarded before later candidates are considered.
     */
    fun detect(
        measurements: List<TemperatureMeasurement>,
        defaultMeasurementSite: MeasurementSite,
        asOfDate: LocalDate? = null,
    ): ThermalShiftResult? {
        val effectiveAsOf = asOfDate ?: measurements.maxOfOrNull { it.date } ?: return null
        val scopedMeasurements = measurements.filter { !it.date.isAfter(effectiveAsOf) }
        val valid = chooseValidMeasurements(scopedMeasurements, defaultMeasurementSite)
        if (valid.size < REQUIRED_BASELINE_MEASUREMENTS + 3) return null

        val byDate = valid.associateBy { it.date }
        val excludedCount = unreliableSelectedMeasurements(scopedMeasurements, defaultMeasurementSite).size
        val confirmedCandidates = mutableListOf<ScoredThermalResult>()
        val pendingCandidates = mutableListOf<ThermalShiftResult>()

        for ((index, potentialFirstHigh) in valid.withIndex()) {
            val firstHighDate = potentialFirstHigh.date
            val lookbackStart = firstHighDate.minusDays(BASELINE_LOOKBACK_DAYS)
            val previousSix = valid
                .subList(0, index)
                .asSequence()
                .filter { !it.date.isBefore(lookbackStart) && it.date.isBefore(firstHighDate) }
                .toList()
                .takeLast(REQUIRED_BASELINE_MEASUREMENTS)

            if (previousSix.size < REQUIRED_BASELINE_MEASUREMENTS) continue

            val baseline = previousSix.maxOf { it.temperatureCentiC }
            val highThreshold = baseline.toLong() + HIGH_THRESHOLD_CENTI_C
            val firstThree = (0L..2L).map { offset -> byDate[firstHighDate.plusDays(offset)] }

            // A missing or excluded calendar day breaks the consecutive sequence.
            if (firstThree.any { it == null }) continue
            val threeMeasurements = firstThree.filterNotNull()
            if (threeMeasurements.any { it.temperatureCentiC.toLong() < highThreshold }) continue

            val firstThreeDates = threeMeasurements.map { it.date }
            val third = threeMeasurements[2]
            val provisional = if (
                third.temperatureCentiC.toLong() >= baseline.toLong() + THIRD_HIGH_CONFIRMATION_CENTI_C
            ) {
                confirmed(
                    firstHighDate = firstHighDate,
                    baselineCentiC = baseline,
                    highDates = firstThreeDates,
                    confirmationDate = third.date,
                    rule = ThermalShiftRule.THREE_HIGH_THIRD_PLUS_0_20,
                    excludedCount = excludedCount,
                )
            } else {
                val fourthDate = firstHighDate.plusDays(3)
                val fourth = byDate[fourthDate]
                when {
                    fourth != null && fourth.temperatureCentiC.toLong() >= highThreshold -> confirmed(
                        firstHighDate = firstHighDate,
                        baselineCentiC = baseline,
                        highDates = firstThreeDates + fourth.date,
                        confirmationDate = fourth.date,
                        rule = ThermalShiftRule.FOURTH_HIGH_FALLBACK,
                        excludedCount = excludedCount,
                    )

                    // A candidate is pending only while the fourth day has not yet elapsed. A low
                    // or missing fourth day in an already progressed chart rejects this candidate.
                    effectiveAsOf.isBefore(fourthDate) -> ThermalShiftResult(
                        state = ThermalShiftState.CANDIDATE,
                        firstHighDate = firstHighDate,
                        baselineCentiC = baseline,
                        highDates = firstThreeDates,
                        confirmationDate = null,
                        rule = ThermalShiftRule.FOURTH_HIGH_REQUIRED,
                        estimatedOvulationRange = null,
                        excludedUnreliableMeasurementCount = excludedCount,
                    )

                    else -> null
                }
            } ?: continue

            if (!provisional.isConfirmed) {
                pendingCandidates += provisional
                continue
            }

            val persistenceEnd = provisional.confirmationDate!!.plusDays(PERSISTENCE_LOOKAHEAD_DAYS)
            val subsequent = valid.filter { measurement ->
                measurement.date.isAfter(provisional.confirmationDate) &&
                    !measurement.date.isAfter(persistenceEnd)
            }
            val laterHighCount = subsequent.count { it.temperatureCentiC.toLong() >= highThreshold }
            val laterLowCount = subsequent.size - laterHighCount

            // Two lows shortly after a provisional rise are enough to refute a sustained change.
            if (laterLowCount >= 2) continue

            val supported = provisional.copy(
                subsequentSupportingHighCount = laterHighCount,
                subsequentLowCount = laterLowCount,
            )
            val minimumInitialMargin = threeMeasurements.minOf {
                (it.temperatureCentiC.toLong() - highThreshold).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            confirmedCandidates += ScoredThermalResult(
                result = supported,
                persistenceScore = laterHighCount * 4 - laterLowCount * 3 + minimumInitialMargin,
            )
        }

        return confirmedCandidates
            .sortedWith(
                compareByDescending<ScoredThermalResult> { it.persistenceScore }
                    .thenBy { it.result.firstHighDate },
            )
            .firstOrNull()
            ?.result
            ?: pendingCandidates.maxByOrNull { it.firstHighDate }
    }

    private fun confirmed(
        firstHighDate: LocalDate,
        baselineCentiC: Int,
        highDates: List<LocalDate>,
        confirmationDate: LocalDate,
        rule: ThermalShiftRule,
        excludedCount: Int,
    ) = ThermalShiftResult(
        state = ThermalShiftState.CONFIRMED,
        firstHighDate = firstHighDate,
        baselineCentiC = baselineCentiC,
        highDates = highDates,
        confirmationDate = confirmationDate,
        rule = rule,
        estimatedOvulationRange = firstHighDate.minusDays(2)..firstHighDate,
        excludedUnreliableMeasurementCount = excludedCount,
    )

    private data class ScoredThermalResult(
        val result: ThermalShiftResult,
        val persistenceScore: Int,
    )
}

private fun Long.hasFlag(flag: Long): Boolean = this and flag != 0L
