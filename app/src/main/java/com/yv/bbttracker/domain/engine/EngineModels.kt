package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Version persisted with every analysis snapshot. Threshold or rule changes must bump this value.
 */
const val ENGINE_VERSION = "bbt-fusion-2.3.1"

enum class FertilityStatus {
    INSUFFICIENT_DATA,
    CALENDAR_ESTIMATE_ONLY,
    PREDICTED_FERTILE_WINDOW,
    FERTILITY_SIGNS_PRESENT,
    LH_SURGE_DETECTED,
    THERMAL_SHIFT_CANDIDATE,
    THERMAL_SHIFT_CONFIRMED,
    POST_OVULATORY_ESTIMATE,
    UNCERTAIN,
}

enum class EvidenceLevel {
    NONE,
    CALENDAR_ONLY,
    ONE_BIOLOGICAL_SIGN,
    MULTIPLE_SIGNS,
    THERMAL_PATTERN,
    COMBINED_PATTERN,
}

enum class DataQuality {
    INSUFFICIENT,
    LOW,
    MODERATE,
    GOOD,
}

/** Reliability of this app's relative forecast, not a clinically calibrated probability. */
enum class ForecastReliability {
    INSUFFICIENT,
    LIMITED,
    MODERATE,
    STRONG,
}

enum class AnalysisSignal {
    SELF_REPORTED_CYCLE_LENGTH,
    PERSONAL_HISTORY,
    CYCLE_LENGTH_HISTORY,
    LH_BORDERLINE,
    LH_SURGE,
    FERTILE_MUCUS,
    RISING_MUCUS,
    MUCUS_PEAK,
    THERMAL_CANDIDATE,
    THERMAL_SHIFT,
    CONFLICTING_SIGNALS,
    UNRELIABLE_TEMPERATURES_EXCLUDED,
}

/** A conception-oriented description. BACKGROUND must never be interpreted as infertile or safe. */
enum class FertilityLevelToday {
    UNKNOWN,
    BACKGROUND,
    ELEVATED,
    HIGH,
    PEAK_SIGNAL,
}

enum class NextAction {
    CONTINUE_DAILY_TRACKING,
    START_OR_CONTINUE_LH_TESTING,
    REPEAT_LH_TEST,
    PRIORITIZE_CONCEPTION_TIMING,
    AWAIT_THERMAL_CONFIRMATION,
    IMPROVE_TEMPERATURE_QUALITY,
    REVIEW_CONFLICTING_SIGNALS,
}

enum class ReasonCode {
    TOO_FEW_TEMPERATURES,
    TOO_MANY_MISSING_DAYS,
    DISTURBED_MEASUREMENTS,
    MEASUREMENT_SITE_CHANGED,
    UNRELIABLE_TEMPERATURES_EXCLUDED,
    CALENDAR_PRIOR_AVAILABLE,
    SELF_REPORTED_CYCLE_LENGTH_AVAILABLE,
    CALENDAR_PRIOR_UNSTABLE,
    PERSONAL_OVULATION_HISTORY_AVAILABLE,
    FERTILE_MUCUS_RECORDED,
    MUCUS_PATTERN_RISING,
    MUCUS_PEAK_IDENTIFIED,
    LH_BORDERLINE_RECORDED,
    LH_POSITIVE_RECORDED,
    THREE_HIGH_TEMPERATURES,
    FOURTH_HIGH_TEMPERATURE_REQUIRED,
    THERMAL_SHIFT_CONFIRMED,
    THERMAL_PATTERN_REFUTED,
    SIGNS_DO_NOT_ALIGN,
    CURRENT_CYCLE_LONGER_THAN_EXPECTED,
}

enum class ThermalShiftState {
    CANDIDATE,
    CONFIRMED,
}

enum class ThermalShiftRule {
    /** Three consecutive highs, with the third at least 0.20 C above baseline. */
    THREE_HIGH_THIRD_PLUS_0_20,

    /** The third high did not reach +0.20 C, so a fourth day at +0.10 C confirmed the pattern. */
    FOURTH_HIGH_FALLBACK,

    /** Three highs exist, but the third did not reach +0.20 C and no qualifying fourth exists yet. */
    FOURTH_HIGH_REQUIRED,
}

data class ThermalShiftResult(
    val state: ThermalShiftState,
    val firstHighDate: LocalDate,
    val baselineCentiC: Int,
    val highDates: List<LocalDate>,
    val confirmationDate: LocalDate?,
    val rule: ThermalShiftRule,
    val estimatedOvulationRange: ClosedRange<LocalDate>?,
    /** Number of later, usable temperatures that continue above the high threshold. */
    val subsequentSupportingHighCount: Int = 0,
    /** Number of later, usable temperatures that fall below the high threshold. */
    val subsequentLowCount: Int = 0,
    /** Number of selected measurements excluded because they were not reliable enough. */
    val excludedUnreliableMeasurementCount: Int = 0,
) {
    val isConfirmed: Boolean get() = state == ThermalShiftState.CONFIRMED
    val firstHighEpochDay: Long get() = firstHighDate.toEpochDay()
}

/**
 * A relative weight is useful for deterministic ranking and interval construction. It is not a
 * validated probability and must not be displayed to the user as a percentage.
 */
data class DailyOvulationWeight(
    val date: LocalDate,
    val relativeWeight: Double,
)

/**
 * Completed historical-cycle evidence used by the personal prior. Existing callers that only
 * supplied a confirmed first-high date remain source compatible.
 */
data class CycleWithAnalysis(
    val cycle: Cycle,
    val confirmedThermalShiftFirstHighDate: LocalDate? = null,
    val estimatedOvulationRange: ClosedRange<LocalDate>? = null,
    val firstPositiveLhDate: LocalDate? = null,
    val mucusPeakDate: LocalDate? = null,
    val reliability: ForecastReliability = ForecastReliability.LIMITED,
    val signals: Set<AnalysisSignal> = emptySet(),
) {
    val cycleLengthDays: Int?
        get() = cycle.endDate
            ?.let { end -> ChronoUnit.DAYS.between(cycle.startDate, end).toInt() + 1 }
            ?.takeIf { it > 0 }

    val confirmedThermalShiftCycleDay: Int?
        get() = confirmedThermalShiftFirstHighDate
            ?.takeIf { shiftDate -> cycle.contains(shiftDate) }
            ?.let { shiftDate -> ChronoUnit.DAYS.between(cycle.startDate, shiftDate).toInt() + 1 }
            ?.takeIf { it > 0 }

    val estimatedOvulationCycleDay: Int?
        get() = estimatedOvulationRange
            ?.let { range ->
                val midpointEpochDay = (range.start.toEpochDay() + range.endInclusive.toEpochDay()) / 2L
                LocalDate.ofEpochDay(midpointEpochDay)
            }
            ?.takeIf(cycle::contains)
            ?.let { date -> ChronoUnit.DAYS.between(cycle.startDate, date).toInt() + 1 }
            ?: confirmedThermalShiftCycleDay?.minus(1)

    /**
     * Days after the estimated ovulation day through the cycle's last day. Purely descriptive;
     * plausibility filtering is up to the consumer.
     */
    val lutealPhaseDays: Int?
        get() {
            val length = cycleLengthDays ?: return null
            val ovulationDay = estimatedOvulationCycleDay ?: return null
            return (length - ovulationDay).takeIf { it > 0 }
        }
}

data class CycleAnalysisInput(
    val currentDate: LocalDate,
    val currentCycle: Cycle,
    val previousCycles: List<CycleWithAnalysis>,
    val temperatures: List<TemperatureMeasurement>,
    val observations: List<DailyObservation>,
    val defaultMeasurementSite: MeasurementSite,
    /** Optional onboarding estimate used only while completed personal history is sparse. */
    val typicalCycleLengthDays: Int? = null,
)

data class CycleAnalysisResult(
    val status: FertilityStatus,
    val evidenceLevel: EvidenceLevel,
    val dataQuality: DataQuality,
    /** Compatibility field: now represents the conception opportunity window. */
    val predictedFertileWindow: ClosedRange<LocalDate>?,
    /** Compatibility field: retrospective estimate first, otherwise the prospective range. */
    val estimatedOvulationRange: ClosedRange<LocalDate>?,
    val thermalShift: ThermalShiftResult?,
    val reasonCodes: List<ReasonCode>,
    val humanExplanation: List<String>,
    val engineVersion: String = ENGINE_VERSION,
    /** History and current pre-ovulatory signs only; never a claim that ovulation occurred. */
    val prospectiveOvulationRange: ClosedRange<LocalDate>? = null,
    /** Union of the six-day opportunity windows for the possible ovulation dates. */
    val conceptionOpportunityWindow: ClosedRange<LocalDate>? = null,
    /** Retrospective home-sign estimate; support may be limited and is never confirmation. */
    val retrospectiveOvulationRange: ClosedRange<LocalDate>? = null,
    val reliability: ForecastReliability = ForecastReliability.INSUFFICIENT,
    val signals: Set<AnalysisSignal> = emptySet(),
    val fertilityLevelToday: FertilityLevelToday = FertilityLevelToday.UNKNOWN,
    val nextAction: NextAction = NextAction.CONTINUE_DAILY_TRACKING,
    val dailyOvulationWeights: List<DailyOvulationWeight> = emptyList(),
    /** Expected next-period start range; a heuristic estimate, never a guarantee. */
    val nextPeriodForecast: PeriodForecast? = null,
)
