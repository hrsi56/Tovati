package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.DisturbanceFlag
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Transparent deterministic fusion of a broad personal prior with current LH, mucus and thermal
 * evidence. Relative weights rank possible dates but are intentionally not presented as calibrated
 * probabilities. The engine never returns a contraceptive "safe day" conclusion.
 */
class CycleAnalysisEngine {
    fun analyze(input: CycleAnalysisInput): CycleAnalysisResult {
        val cycleTemperatures = input.temperatures.filter { measurement ->
            input.currentCycle.contains(measurement.date) && !measurement.date.isAfter(input.currentDate)
        }
        val cycleObservations = input.observations.filter { observation ->
            input.currentCycle.contains(observation.date) && !observation.date.isAfter(input.currentDate)
        }
        val validMeasurements = ThermalShiftDetector.chooseValidMeasurements(
            measurements = cycleTemperatures,
            defaultMeasurementSite = input.defaultMeasurementSite,
        )
        val thermalShift = ThermalShiftDetector.detect(
            measurements = cycleTemperatures,
            defaultMeasurementSite = input.defaultMeasurementSite,
            asOfDate = input.currentDate,
        )
        val qualityAssessment = assessDataQuality(
            input = input,
            cycleTemperatures = cycleTemperatures,
            validMeasurements = validMeasurements,
        )
        val lh = LhSignalAnalyzer.analyze(cycleObservations, input.currentDate)
        val mucus = MucusSignalAnalyzer.analyze(cycleObservations, input.currentDate)
        val forecast = PersonalPriorCalculator.calculate(
            currentCycleStart = input.currentCycle.startDate,
            currentDate = input.currentDate,
            previousCycles = input.previousCycles,
            typicalCycleLengthDays = input.typicalCycleLengthDays,
        )
        // A current LH episode is kept as one focused two-day estimate. A prolonged positive can
        // move the actionable pair forward, but never widens the display across the whole episode.
        val prospectiveLhEpisode = lh.activeEpisode ?: lh.latestEpisode?.takeIf {
            lh.positiveToday && it.lastPositiveDate == input.currentDate
        }
        val prospectiveLhRange = prospectiveLhEpisode?.let { episode ->
            FocusedEstimateSelector.lhProspectiveRange(episode, input.currentDate)
        }
        val lhRange = lh.latestEpisode?.estimatedOvulationRange
        val mucusPeakRange = mucus.peakDate?.let { it.minusDays(1)..it.plusDays(1) }
        val thermalRange = thermalShift?.takeIf { it.isConfirmed }?.estimatedOvulationRange
        val retrospectiveLhSupported = lhRange != null && listOfNotNull(thermalRange, mucusPeakRange)
            .any { rangesAlign(it, lhRange) }
        val prospectiveMucusDate = mucus.latestFertileDate?.takeIf { date ->
            (mucus.peakDate == null || date.isAfter(mucus.peakDate)) &&
                ChronoUnit.DAYS.between(date, input.currentDate) in 0L..1L
        }

        val signals = linkedSetOf<AnalysisSignal>().apply {
            addAll(forecast.signals)
            if (prospectiveLhEpisode != null || retrospectiveLhSupported) add(AnalysisSignal.LH_SURGE)
            if (lh.recentBorderlineDate != null) add(AnalysisSignal.LH_BORDERLINE)
            if (prospectiveMucusDate != null) add(AnalysisSignal.FERTILE_MUCUS)
            if (mucus.rising) add(AnalysisSignal.RISING_MUCUS)
            if (mucus.peakDate != null) add(AnalysisSignal.MUCUS_PEAK)
            when (thermalShift?.state) {
                ThermalShiftState.CANDIDATE -> add(AnalysisSignal.THERMAL_CANDIDATE)
                ThermalShiftState.CONFIRMED -> add(AnalysisSignal.THERMAL_SHIFT)
                null -> Unit
            }
            if (qualityAssessment.excludedUnreliableCount > 0) {
                add(AnalysisSignal.UNRELIABLE_TEMPERATURES_EXCLUDED)
            }
        }

        // Prospective weights use only information that can help before thermal confirmation.
        val prospectiveRaw = forecast.weights.toMutableMap()
        prospectiveLhEpisode?.let { applyLhEpisode(prospectiveRaw, it) }
        if (prospectiveLhEpisode == null) {
            lh.recentBorderlineDate?.let { applyBorderlineLh(prospectiveRaw, it) }
        }
        prospectiveMucusDate?.let { applyFertileMucus(prospectiveRaw, it, mucus.rising) }
        val prospectiveNormalized = normalize(prospectiveRaw)
        val prospectiveRange = when {
            // A live surge is more time-specific than the personal prior.
            prospectiveLhRange != null -> prospectiveLhRange
            // With fertile fluid today, choose the strongest consecutive pair in the short
            // biologically relevant horizon instead of spanning it with a distant calendar prior.
            mucus.fertileToday -> FocusedEstimateSelector.bestTwoDayRange(
                weights = prospectiveNormalized,
                allowedRange = input.currentDate..input.currentDate.plusDays(3),
            )
            else -> FocusedEstimateSelector.bestTwoDayRange(prospectiveNormalized)
        }

        // Final date weights also incorporate evidence that has become retrospective.
        val fusedRaw = prospectiveRaw.toMutableMap()
        if (prospectiveLhEpisode == null) lh.latestEpisode?.let { applyLhEpisode(fusedRaw, it) }
        mucus.peakDate?.let { applyMucusPeak(fusedRaw, it) }
        thermalShift?.let { applyThermalEvidence(fusedRaw, it) }

        val conflictRanges = mutableListOf<ClosedRange<LocalDate>>().apply {
            addAll(listOfNotNull(lhRange, mucusPeakRange).filter { signRange ->
            thermalRange != null && !rangesAlign(thermalRange, signRange)
            })
            if (thermalRange == null && lhRange != null && mucusPeakRange != null &&
                !rangesAlign(lhRange, mucusPeakRange)
            ) {
                add(lhRange)
                add(mucusPeakRange)
            }
        }
        val activeSignAfterThermal = thermalRange != null && (
            (prospectiveLhEpisode != null && input.currentDate.isAfter(thermalRange.endInclusive.plusDays(2))) ||
                (mucus.fertileToday && input.currentDate.isAfter(thermalRange.endInclusive.plusDays(2)))
            )
        val signsConflict = conflictRanges.isNotEmpty() || activeSignAfterThermal
        if (signsConflict) signals += AnalysisSignal.CONFLICTING_SIGNALS

        var fusedNormalized = normalize(fusedRaw)
        if (signsConflict) {
            // Keep the contradiction visible in status and reliability, while retaining the full
            // distribution for diagnostics. The displayed estimate remains a focused best guess.
            fusedNormalized = normalize(
                fusedNormalized.mapValues { (date, weight) ->
                    weight * 0.65 + (forecast.weights[date] ?: 0.0) * 0.35
                },
            )
        }

        val retrospectiveLhEpisode = lh.latestEpisode?.takeIf {
            prospectiveLhEpisode == null || thermalRange != null || mucusPeakRange != null
        }
        val retrospectiveRange = FocusedEstimateSelector.retrospectiveDay(
            thermalShift = thermalShift,
            lhEpisode = retrospectiveLhEpisode,
            mucusPeakDate = mucus.peakDate,
        )?.let { day -> day..day }

        val conceptionWindow = prospectiveRange?.let { range ->
            maxOf(input.currentCycle.startDate, range.start.minusDays(5))..range.endInclusive
        }
        val overdueForecast = prospectiveRange != null &&
            input.currentDate.isAfter(prospectiveRange.endInclusive) &&
            prospectiveLhEpisode == null &&
            !mucus.fertileToday &&
            thermalShift == null

        val status = determineStatus(
            currentDate = input.currentDate,
            quality = qualityAssessment.quality,
            thermalShift = thermalShift,
            activeLh = prospectiveLhEpisode != null,
            fertileMucusToday = mucus.fertileToday,
            conceptionWindow = conceptionWindow,
            historyAvailable = forecast.historyCycleCount > 0 ||
                AnalysisSignal.SELF_REPORTED_CYCLE_LENGTH in forecast.signals,
            overdueForecast = overdueForecast,
            signsConflict = signsConflict,
        )
        val evidence = determineEvidence(signals)
        val reliability = determineReliability(
            quality = qualityAssessment.quality,
            signals = signals,
            historyCycleCount = forecast.historyCycleCount,
        )
        val fertilityLevelToday = determineFertilityLevel(
            currentDate = input.currentDate,
            conceptionWindow = conceptionWindow,
            activeLh = prospectiveLhEpisode != null,
            borderlineLh = lh.recentBorderlineDate != null,
            mucus = mucus,
            thermalRange = thermalRange,
            signsConflict = signsConflict,
        )
        val nextAction = determineNextAction(
            fertilityLevel = fertilityLevelToday,
            quality = qualityAssessment.quality,
            activeLh = prospectiveLhEpisode != null,
            borderlineLh = lh.recentBorderlineDate != null,
            thermalShift = thermalShift,
            signsConflict = signsConflict,
        )

        val reasons = linkedSetOf<ReasonCode>().apply {
            addAll(qualityAssessment.reasonCodes)
            if (
                forecast.historyCycleCount > 0 ||
                AnalysisSignal.SELF_REPORTED_CYCLE_LENGTH in forecast.signals
            ) {
                add(ReasonCode.CALENDAR_PRIOR_AVAILABLE)
            }
            if (AnalysisSignal.SELF_REPORTED_CYCLE_LENGTH in forecast.signals) {
                add(ReasonCode.SELF_REPORTED_CYCLE_LENGTH_AVAILABLE)
            }
            if (AnalysisSignal.PERSONAL_HISTORY in forecast.signals) {
                add(ReasonCode.PERSONAL_OVULATION_HISTORY_AVAILABLE)
            }
            if (forecast.isUnstable) add(ReasonCode.CALENDAR_PRIOR_UNSTABLE)
            if (mucus.latestFertileDate != null) add(ReasonCode.FERTILE_MUCUS_RECORDED)
            if (mucus.rising) add(ReasonCode.MUCUS_PATTERN_RISING)
            if (mucus.peakDate != null) add(ReasonCode.MUCUS_PEAK_IDENTIFIED)
            if (lh.recentBorderlineDate != null) add(ReasonCode.LH_BORDERLINE_RECORDED)
            if (lh.latestEpisode != null) add(ReasonCode.LH_POSITIVE_RECORDED)
            when (thermalShift?.state) {
                ThermalShiftState.CANDIDATE -> {
                    add(ReasonCode.THREE_HIGH_TEMPERATURES)
                    add(ReasonCode.FOURTH_HIGH_TEMPERATURE_REQUIRED)
                }
                ThermalShiftState.CONFIRMED -> {
                    add(ReasonCode.THREE_HIGH_TEMPERATURES)
                    if (thermalShift.rule == ThermalShiftRule.FOURTH_HIGH_FALLBACK) {
                        add(ReasonCode.FOURTH_HIGH_TEMPERATURE_REQUIRED)
                    }
                    add(ReasonCode.THERMAL_SHIFT_CONFIRMED)
                }
                null -> Unit
            }
            if (signsConflict) add(ReasonCode.SIGNS_DO_NOT_ALIGN)
            if (overdueForecast) {
                add(ReasonCode.CURRENT_CYCLE_LONGER_THAN_EXPECTED)
                add(ReasonCode.CALENDAR_PRIOR_UNSTABLE)
            }
        }

        // The period forecast anchors on ovulation evidence when it exists: the luteal phase is
        // far more stable than the follicular phase, so "ovulation + personal luteal length"
        // stays accurate even in a cycle whose first half was unusual. The broad prior-based
        // prospective range is intentionally not used as an anchor.
        val nextPeriodForecast = PeriodForecastCalculator.forecast(
            currentCycleStart = input.currentCycle.startDate,
            currentDate = input.currentDate,
            previousCycles = input.previousCycles,
            ovulationEstimate = retrospectiveRange ?: prospectiveLhRange,
            typicalCycleLengthDays = input.typicalCycleLengthDays,
        )

        val estimatedCompatibilityRange = retrospectiveRange
            ?: prospectiveLhRange
            ?: prospectiveRange
        return CycleAnalysisResult(
            status = status,
            evidenceLevel = evidence,
            dataQuality = qualityAssessment.quality,
            predictedFertileWindow = conceptionWindow,
            estimatedOvulationRange = estimatedCompatibilityRange,
            thermalShift = thermalShift,
            reasonCodes = reasons.toList(),
            humanExplanation = explanationsFor(status, reliability, signsConflict, signals),
            engineVersion = ENGINE_VERSION,
            prospectiveOvulationRange = prospectiveRange,
            conceptionOpportunityWindow = conceptionWindow,
            retrospectiveOvulationRange = retrospectiveRange,
            reliability = reliability,
            signals = signals,
            fertilityLevelToday = fertilityLevelToday,
            nextAction = nextAction,
            dailyOvulationWeights = fusedNormalized.map { (date, weight) ->
                DailyOvulationWeight(date, weight)
            },
            nextPeriodForecast = nextPeriodForecast,
        )
    }

    private fun assessDataQuality(
        input: CycleAnalysisInput,
        cycleTemperatures: List<TemperatureMeasurement>,
        validMeasurements: List<TemperatureMeasurement>,
    ): QualityAssessment {
        val reasons = linkedSetOf<ReasonCode>()
        if (validMeasurements.size < 6) reasons += ReasonCode.TOO_FEW_TEMPERATURES

        val siteChanged = cycleTemperatures.any { measurement ->
            measurement.site != input.defaultMeasurementSite ||
                measurement.disturbanceMask.hasFlag(DisturbanceFlag.DIFFERENT_MEASUREMENT_SITE)
        }
        if (siteChanged) reasons += ReasonCode.MEASUREMENT_SITE_CHANGED

        val disturbedCount = cycleTemperatures
            .filter { it.selectedForAnalysis }
            .groupBy { it.measurementEpochDay }
            .count { (_, measurements) -> measurements.any { it.disturbanceMask != 0L } }
        if (disturbedCount > 0) reasons += ReasonCode.DISTURBED_MEASUREMENTS

        val excludedUnreliableCount = ThermalShiftDetector.unreliableSelectedMeasurements(
            cycleTemperatures,
            input.defaultMeasurementSite,
        ).size
        if (excludedUnreliableCount > 0) reasons += ReasonCode.UNRELIABLE_TEMPERATURES_EXCLUDED

        val analysisEnd = minOf(input.currentDate, input.currentCycle.endDate ?: input.currentDate)
        val expectedDays = if (analysisEnd.isBefore(input.currentCycle.startDate)) {
            0
        } else {
            ChronoUnit.DAYS.between(input.currentCycle.startDate, analysisEnd).toInt() + 1
        }
        val missingDays = max(0, expectedDays - validMeasurements.size)
        val missingRatio = if (expectedDays == 0) 1.0 else missingDays.toDouble() / expectedDays
        if (expectedDays >= 8 && missingRatio > 0.40) reasons += ReasonCode.TOO_MANY_MISSING_DAYS

        val observedDayCount = max(1, cycleTemperatures.filter { it.selectedForAnalysis }
            .map { it.measurementEpochDay }.distinct().size)
        val disturbedRatio = disturbedCount.toDouble() / observedDayCount
        val measurementTimeSpreadMinutes = circularTimeSpreadMinutes(
            validMeasurements.map { measurement ->
                measurement.measuredAt.atZone(measurement.zoneId).toLocalTime().let { it.hour * 60 + it.minute }
            },
        )

        val quality = when {
            validMeasurements.size < 6 -> DataQuality.INSUFFICIENT
            validMeasurements.size < 8 -> DataQuality.LOW
            siteChanged || excludedUnreliableCount >= 2 || disturbedRatio >= 0.35 ||
                missingRatio > 0.50 || measurementTimeSpreadMinutes > 180 -> DataQuality.LOW
            validMeasurements.size >= 10 && missingRatio <= 0.25 && disturbedRatio <= 0.15 &&
                measurementTimeSpreadMinutes <= 90 -> DataQuality.GOOD
            else -> DataQuality.MODERATE
        }
        return QualityAssessment(quality, reasons.toList(), excludedUnreliableCount)
    }

    private fun determineStatus(
        currentDate: LocalDate,
        quality: DataQuality,
        thermalShift: ThermalShiftResult?,
        activeLh: Boolean,
        fertileMucusToday: Boolean,
        conceptionWindow: ClosedRange<LocalDate>?,
        historyAvailable: Boolean,
        overdueForecast: Boolean,
        signsConflict: Boolean,
    ): FertilityStatus = when {
        signsConflict -> FertilityStatus.UNCERTAIN
        thermalShift?.isConfirmed == true && thermalShift.confirmationDate == currentDate ->
            FertilityStatus.THERMAL_SHIFT_CONFIRMED
        thermalShift?.isConfirmed == true -> FertilityStatus.POST_OVULATORY_ESTIMATE
        activeLh -> FertilityStatus.LH_SURGE_DETECTED
        fertileMucusToday -> FertilityStatus.FERTILITY_SIGNS_PRESENT
        thermalShift?.state == ThermalShiftState.CANDIDATE -> FertilityStatus.THERMAL_SHIFT_CANDIDATE
        quality == DataQuality.INSUFFICIENT && !historyAvailable -> FertilityStatus.INSUFFICIENT_DATA
        overdueForecast -> FertilityStatus.UNCERTAIN
        conceptionWindow != null && currentDate in conceptionWindow -> FertilityStatus.PREDICTED_FERTILE_WINDOW
        historyAvailable -> FertilityStatus.CALENDAR_ESTIMATE_ONLY
        else -> FertilityStatus.UNCERTAIN
    }

    private fun determineEvidence(signals: Set<AnalysisSignal>): EvidenceLevel {
        val biologicalGroups = listOf(
            AnalysisSignal.LH_SURGE in signals || AnalysisSignal.LH_BORDERLINE in signals,
            AnalysisSignal.FERTILE_MUCUS in signals || AnalysisSignal.MUCUS_PEAK in signals,
            AnalysisSignal.THERMAL_SHIFT in signals || AnalysisSignal.THERMAL_CANDIDATE in signals,
        ).count { it }
        return when {
            biologicalGroups >= 2 && (AnalysisSignal.THERMAL_SHIFT in signals ||
                AnalysisSignal.THERMAL_CANDIDATE in signals) -> EvidenceLevel.COMBINED_PATTERN
            AnalysisSignal.THERMAL_SHIFT in signals || AnalysisSignal.THERMAL_CANDIDATE in signals ->
                EvidenceLevel.THERMAL_PATTERN
            biologicalGroups >= 2 -> EvidenceLevel.MULTIPLE_SIGNS
            biologicalGroups == 1 -> EvidenceLevel.ONE_BIOLOGICAL_SIGN
            AnalysisSignal.SELF_REPORTED_CYCLE_LENGTH in signals ||
                AnalysisSignal.PERSONAL_HISTORY in signals ||
                AnalysisSignal.CYCLE_LENGTH_HISTORY in signals ->
                EvidenceLevel.CALENDAR_ONLY
            else -> EvidenceLevel.NONE
        }
    }

    private fun determineReliability(
        quality: DataQuality,
        signals: Set<AnalysisSignal>,
        historyCycleCount: Int,
    ): ForecastReliability {
        if (AnalysisSignal.CONFLICTING_SIGNALS in signals) return ForecastReliability.LIMITED
        val biologicalGroups = listOf(
            AnalysisSignal.LH_SURGE in signals,
            AnalysisSignal.FERTILE_MUCUS in signals || AnalysisSignal.MUCUS_PEAK in signals,
            AnalysisSignal.THERMAL_SHIFT in signals,
        ).count { it }
        return when {
            biologicalGroups >= 2 && quality in setOf(DataQuality.MODERATE, DataQuality.GOOD) ->
                ForecastReliability.STRONG
            biologicalGroups >= 1 && (historyCycleCount >= 2 || quality != DataQuality.INSUFFICIENT) ->
                ForecastReliability.MODERATE
            biologicalGroups >= 1 || historyCycleCount >= 1 -> ForecastReliability.LIMITED
            else -> ForecastReliability.INSUFFICIENT
        }
    }

    private fun determineFertilityLevel(
        currentDate: LocalDate,
        conceptionWindow: ClosedRange<LocalDate>?,
        activeLh: Boolean,
        borderlineLh: Boolean,
        mucus: MucusSignalSummary,
        thermalRange: ClosedRange<LocalDate>?,
        signsConflict: Boolean,
    ): FertilityLevelToday = when {
        signsConflict -> FertilityLevelToday.UNKNOWN
        activeLh && mucus.fertileToday -> FertilityLevelToday.PEAK_SIGNAL
        activeLh -> FertilityLevelToday.PEAK_SIGNAL
        mucus.fertileToday || (borderlineLh && mucus.rising) -> FertilityLevelToday.HIGH
        borderlineLh || mucus.rising || (conceptionWindow != null && currentDate in conceptionWindow) ->
            FertilityLevelToday.ELEVATED
        thermalRange != null && currentDate.isAfter(thermalRange.endInclusive.plusDays(2)) ->
            FertilityLevelToday.BACKGROUND
        conceptionWindow != null -> FertilityLevelToday.BACKGROUND
        else -> FertilityLevelToday.UNKNOWN
    }

    private fun determineNextAction(
        fertilityLevel: FertilityLevelToday,
        quality: DataQuality,
        activeLh: Boolean,
        borderlineLh: Boolean,
        thermalShift: ThermalShiftResult?,
        signsConflict: Boolean,
    ): NextAction = when {
        signsConflict -> NextAction.REVIEW_CONFLICTING_SIGNALS
        fertilityLevel in setOf(FertilityLevelToday.HIGH, FertilityLevelToday.PEAK_SIGNAL) ->
            NextAction.PRIORITIZE_CONCEPTION_TIMING
        borderlineLh && !activeLh -> NextAction.REPEAT_LH_TEST
        thermalShift?.state == ThermalShiftState.CANDIDATE -> NextAction.AWAIT_THERMAL_CONFIRMATION
        fertilityLevel == FertilityLevelToday.ELEVATED && !activeLh ->
            NextAction.START_OR_CONTINUE_LH_TESTING
        quality == DataQuality.LOW -> NextAction.IMPROVE_TEMPERATURE_QUALITY
        else -> NextAction.CONTINUE_DAILY_TRACKING
    }

    private fun explanationsFor(
        status: FertilityStatus,
        reliability: ForecastReliability,
        signsConflict: Boolean,
        signals: Set<AnalysisSignal>,
    ): List<String> = buildList {
        add(
            when (status) {
                FertilityStatus.INSUFFICIENT_DATA ->
                    if (AnalysisSignal.SELF_REPORTED_CYCLE_LENGTH in signals) {
                        "עדיין אין מספיק היסטוריה; הערכת הפתיחה משתמשת באורך המחזור שמסרת ואינה אישור ביוץ."
                    } else {
                        "עדיין אין מספיק נתונים אישיים; מוצג צמד הימים בעל המשקל הגבוה ביותר כהערכה התחלתית."
                    }
                FertilityStatus.CALENDAR_ESTIMATE_ONLY ->
                    if (AnalysisSignal.SELF_REPORTED_CYCLE_LENGTH in signals) {
                        "הערכת לוח־השנה משלבת את אורך המחזור שמסרת עם ההיסטוריה שנצברה ואינה אישור ביוץ."
                    } else {
                        "ההערכה מבוססת בעיקר על היסטוריה אישית ואינה אישור ביוץ."
                    }
                FertilityStatus.PREDICTED_FERTILE_WINDOW -> "היום נמצא בחלון ניסיון להרות שנגזר מטווח ביוץ אפשרי."
                FertilityStatus.FERTILITY_SIGNS_PRESENT -> "דפוס הנוזל הנוכחי תומך בפוריות מוגברת, אך אינו מאשר ביוץ."
                FertilityStatus.LH_SURGE_DETECTED -> "זוהתה אפיזודת LH מהחיובי הראשון; חיוביים רצופים אינם מאפסים אותה."
                FertilityStatus.THERMAL_SHIFT_CANDIDATE -> "נראה מועמד לשינוי תרמי שעדיין דורש התמדה."
                FertilityStatus.THERMAL_SHIFT_CONFIRMED -> "זוהה שינוי תרמי מתמשך כהערכה רטרוספקטיבית."
                FertilityStatus.POST_OVULATORY_ESTIMATE -> "הדפוס התרמי תומך בהערכה רטרוספקטיבית לאחר ביוץ."
                FertilityStatus.UNCERTAIN -> "הנתונים אינם מתיישבים לדפוס יחיד; התאריך המוצג הוא הניחוש הממוקד של האפליקציה."
            },
        )
        if (signsConflict) add("קיימים סימנים סותרים; האפליקציה בחרה אומדן ממוקד אך הורידה את התמיכה בו.")
        if (reliability == ForecastReliability.LIMITED) add("מהימנות ההערכה מוגבלת; אין לפרש את המשקלים כאחוזים.")
    }.distinct().take(3)

    private data class QualityAssessment(
        val quality: DataQuality,
        val reasonCodes: List<ReasonCode>,
        val excludedUnreliableCount: Int,
    )
}

private data class PersonalForecast(
    val weights: Map<LocalDate, Double>,
    val historyCycleCount: Int,
    val signals: Set<AnalysisSignal>,
    val isUnstable: Boolean,
)

private object PersonalPriorCalculator {
    private const val MIN_OVULATION_CYCLE_DAY = 5
    private const val DEFAULT_MAX_OVULATION_CYCLE_DAY = 45
    private const val ABSOLUTE_MAX_OVULATION_CYCLE_DAY = 120

    fun calculate(
        currentCycleStart: LocalDate,
        currentDate: LocalDate,
        previousCycles: List<CycleWithAnalysis>,
        typicalCycleLengthDays: Int?,
    ): PersonalForecast {
        val completed = previousCycles
            .filter { it.cycle.endDate != null && it.cycle.startDate.isBefore(currentCycleStart) }
            .sortedByDescending { it.cycle.startEpochDay }
        val currentCycleDay = ChronoUnit.DAYS.between(currentCycleStart, currentDate).toInt() + 1
        val historicalMaximum = completed.mapNotNull { it.estimatedOvulationCycleDay }.maxOrNull() ?: 0
        val maxDay = maxOf(
            DEFAULT_MAX_OVULATION_CYCLE_DAY,
            currentCycleDay + 21,
            historicalMaximum + 12,
        ).coerceAtMost(ABSOLUTE_MAX_OVULATION_CYCLE_DAY)

        val cycleLengths = completed.mapNotNull { it.cycleLengthDays?.toDouble() }
        val medianLength = cycleLengths.takeIf { it.isNotEmpty() }?.let(::median)
        val cycleLengthEstimate = PeriodForecastCalculator.cycleLengthEstimate(
            completedLengths = cycleLengths,
            typicalCycleLengthDays = typicalCycleLengthDays,
        )
        val lengthMad = medianLength?.let { center -> median(cycleLengths.map { abs(it - center) }) } ?: 0.0
        // Prefer the personal median luteal length over the textbook 14-day assumption when the
        // history carries enough retrospective ovulation estimates to derive one.
        val lutealDays = PeriodForecastCalculator.lutealStats(completed)?.medianDays ?: 14.0
        val fallbackCenter = ((cycleLengthEstimate?.days ?: 29.0) - lutealDays)
            .coerceIn(MIN_OVULATION_CYCLE_DAY.toDouble(), maxDay.toDouble())
        val fallbackSigma = max(6.0, lengthMad + 4.0)

        val raw = linkedMapOf<LocalDate, Double>()
        (MIN_OVULATION_CYCLE_DAY..maxDay).forEach { day ->
            val broadFallback = gaussian(day.toDouble(), fallbackCenter, fallbackSigma)
            raw[currentCycleStart.plusDays((day - 1).toLong())] = 0.005 + broadFallback
        }

        val reportedWeight = when (cycleLengths.size) {
            0 -> 1.4
            1 -> 0.75
            2 -> 0.35
            else -> 0.0
        }
        if (typicalCycleLengthDays != null && reportedWeight > 0.0) {
            val reportedCenter = (typicalCycleLengthDays.toDouble() - lutealDays)
                .coerceIn(MIN_OVULATION_CYCLE_DAY.toDouble(), maxDay.toDouble())
            raw.keys.forEach { date ->
                val day = ChronoUnit.DAYS.between(currentCycleStart, date).toInt() + 1
                raw[date] = raw.getValue(date) + reportedWeight *
                    gaussian(day.toDouble(), reportedCenter, 4.0)
            }
        }

        completed.forEachIndexed { index, historical ->
            val center = historical.estimatedOvulationCycleDay?.toDouble()
                ?: historical.cycleLengthDays?.minus(14)?.toDouble()
                ?: return@forEachIndexed
            if (center !in MIN_OVULATION_CYCLE_DAY.toDouble()..maxDay.toDouble()) return@forEachIndexed
            val recencyWeight = 0.82.pow(index.toDouble())
            val evidenceWeight = when (historical.reliability) {
                ForecastReliability.STRONG -> 1.0
                ForecastReliability.MODERATE -> 0.75
                ForecastReliability.LIMITED -> 0.40
                ForecastReliability.INSUFFICIENT -> 0.20
            }
            val sigma = when (historical.reliability) {
                ForecastReliability.STRONG -> 2.5
                ForecastReliability.MODERATE -> 3.5
                ForecastReliability.LIMITED -> 5.5
                ForecastReliability.INSUFFICIENT -> 7.0
            }
            raw.keys.forEach { date ->
                val day = ChronoUnit.DAYS.between(currentCycleStart, date).toInt() + 1
                raw[date] = raw.getValue(date) + recencyWeight * evidenceWeight *
                    gaussian(day.toDouble(), center, sigma)
            }
        }

        val personalDays = completed.mapNotNull { it.estimatedOvulationCycleDay?.toDouble() }
        val ovulationMad = personalDays.takeIf { it.size >= 2 }?.let { values ->
            val center = median(values)
            median(values.map { abs(it - center) })
        } ?: 0.0
        val signals = linkedSetOf<AnalysisSignal>().apply {
            if (typicalCycleLengthDays != null && cycleLengths.size < 3) {
                add(AnalysisSignal.SELF_REPORTED_CYCLE_LENGTH)
            }
            if (completed.isNotEmpty()) add(AnalysisSignal.CYCLE_LENGTH_HISTORY)
            if (personalDays.isNotEmpty()) add(AnalysisSignal.PERSONAL_HISTORY)
        }
        return PersonalForecast(
            weights = normalize(raw),
            historyCycleCount = completed.size,
            signals = signals,
            isUnstable = lengthMad > 2.0 || ovulationMad > 3.0,
        )
    }

    private fun gaussian(x: Double, center: Double, sigma: Double): Double =
        exp(-0.5 * ((x - center) / sigma).pow(2.0))
}

private fun applyLhEpisode(weights: MutableMap<LocalDate, Double>, episode: LhEpisode) {
    weights.keys.forEach { candidate ->
        val offset = ChronoUnit.DAYS.between(episode.firstPositiveDate, candidate).toInt()
        val multiplier = when (offset) {
            -1 -> 1.2
            0 -> 5.0
            1 -> 8.0
            2 -> 4.5
            3 -> 1.4
            else -> if (offset < -1) 0.75 else 0.65
        }
        weights[candidate] = weights.getValue(candidate) * multiplier
    }
}

private fun applyBorderlineLh(weights: MutableMap<LocalDate, Double>, date: LocalDate) {
    weights.keys.forEach { candidate ->
        val offset = ChronoUnit.DAYS.between(date, candidate).toInt()
        val multiplier = when (offset) {
            0 -> 1.5
            1 -> 2.1
            2 -> 1.8
            3 -> 1.3
            else -> 0.95
        }
        weights[candidate] = weights.getValue(candidate) * multiplier
    }
}

private fun applyFertileMucus(
    weights: MutableMap<LocalDate, Double>,
    date: LocalDate,
    rising: Boolean,
) {
    weights.keys.forEach { candidate ->
        val offset = ChronoUnit.DAYS.between(date, candidate).toInt()
        val base = when (offset) {
            -1 -> 1.4
            0 -> 3.0
            1 -> 3.8
            2 -> 3.2
            3 -> 2.0
            4 -> 1.25
            else -> 0.90
        }
        val multiplier = if (rising && offset in 1..3) base * 1.20 else base
        weights[candidate] = weights.getValue(candidate) * multiplier
    }
}

private fun applyMucusPeak(weights: MutableMap<LocalDate, Double>, peakDate: LocalDate) {
    weights.keys.forEach { candidate ->
        val distance = abs(ChronoUnit.DAYS.between(peakDate, candidate).toInt())
        val multiplier = when (distance) {
            0 -> 5.0
            1 -> 3.5
            2 -> 1.4
            else -> 0.75
        }
        weights[candidate] = weights.getValue(candidate) * multiplier
    }
}

private fun applyThermalEvidence(
    weights: MutableMap<LocalDate, Double>,
    thermalShift: ThermalShiftResult,
) {
    val range = thermalShift.estimatedOvulationRange
        ?: thermalShift.firstHighDate.minusDays(2)..thermalShift.firstHighDate
    weights.keys.forEach { candidate ->
        val distance = distanceFromRange(candidate, range)
        val multiplier = when (thermalShift.state) {
            ThermalShiftState.CONFIRMED -> when (distance) {
                0L -> 10.0
                1L -> 2.5
                else -> 0.30
            }
            ThermalShiftState.CANDIDATE -> when (distance) {
                0L -> 3.0
                1L -> 1.4
                else -> 0.80
            }
        }
        weights[candidate] = weights.getValue(candidate) * multiplier
    }
}

private fun rangesAlign(
    first: ClosedRange<LocalDate>,
    second: ClosedRange<LocalDate>,
): Boolean = first.intersects(second) ||
    distanceFromRange(first.start, second) <= 1L ||
    distanceFromRange(second.start, first) <= 1L

private fun distanceFromRange(date: LocalDate, range: ClosedRange<LocalDate>): Long = when {
    date.isBefore(range.start) -> ChronoUnit.DAYS.between(date, range.start)
    date.isAfter(range.endInclusive) -> ChronoUnit.DAYS.between(range.endInclusive, date)
    else -> 0L
}

private fun normalize(weights: Map<LocalDate, Double>): LinkedHashMap<LocalDate, Double> {
    val clean = weights.toSortedMap().mapValues { (_, value) ->
        value.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    }
    val total = clean.values.sum()
    if (clean.isEmpty()) return linkedMapOf()
    if (!total.isFinite() || total <= 0.0) {
        val equal = 1.0 / clean.size.toDouble()
        return clean.keys.associateWithTo(linkedMapOf()) { equal }
    }
    return clean.mapValuesTo(linkedMapOf()) { (_, value) -> value / total }
}

private fun circularTimeSpreadMinutes(minutes: List<Int>): Int {
    if (minutes.size < 2) return 0
    val sorted = minutes.map { ((it % 1_440) + 1_440) % 1_440 }.sorted()
    val gaps = sorted.zipWithNext { first, second -> second - first } +
        (sorted.first() + 1_440 - sorted.last())
    return 1_440 - (gaps.maxOrNull() ?: 1_440)
}

private fun Long.hasFlag(flag: Long): Boolean = this and flag != 0L
