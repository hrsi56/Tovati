package com.yv.bbttracker.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
enum class MeasurementSite { ORAL, VAGINAL, RECTAL }

@Serializable
enum class MeasurementSource { MANUAL }

@Serializable
enum class BleedingLevel { NOT_RECORDED, NONE, SPOTTING, LIGHT, MEDIUM, HEAVY }

@Serializable
enum class CervicalMucus { NOT_CHECKED, DRY, STICKY, CREAMY, WATERY, EGG_WHITE }

@Serializable
enum class MucusSensation { NOT_CHECKED, DRY, DAMP, WET, SLIPPERY }

@Serializable
enum class LhResult { NOT_TESTED, NEGATIVE, BORDERLINE, POSITIVE, INVALID }

@Serializable
enum class OvulationPain { NOT_RECORDED, NONE, LEFT, RIGHT, UNCLEAR }

@Serializable
enum class LibidoLevel { NOT_RECORDED, VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH, MASTURBATED }

@Serializable
enum class SexualContact { NOT_RECORDED, NONE, SOME, YES }

@Serializable
enum class TrackingGoal { CYCLE_AWARENESS, TRYING_TO_CONCEIVE }

object MoodFlag {
    const val CALM = 1L shl 0
    const val HAPPY = 1L shl 1
    const val ENERGETIC = 1L shl 2
    const val SENSITIVE = 1L shl 3
    const val IRRITABLE = 1L shl 4
    const val ANXIOUS = 1L shl 5
    const val SAD = 1L shl 6
    const val LOW = 1L shl 7

    val all: List<Long> = listOf(CALM, HAPPY, ENERGETIC, SENSITIVE, IRRITABLE, ANXIOUS, SAD, LOW)
}

object PhysicalSymptomFlag {
    const val ABDOMINAL_PAIN = 1L shl 0
    const val HEADACHE = 1L shl 1
    const val ACNE = 1L shl 2
    const val BLOATING = 1L shl 3
    const val BACK_PAIN = 1L shl 4
    const val BREAST_TENDERNESS = 1L shl 5
    const val FATIGUE = 1L shl 6
    const val NAUSEA = 1L shl 7
    const val PELVIC_PRESSURE = 1L shl 8
    const val SLEEP_CHANGES = 1L shl 9
    const val OTHER = 1L shl 10

    val all: List<Long> = listOf(
        ABDOMINAL_PAIN,
        HEADACHE,
        ACNE,
        BLOATING,
        BACK_PAIN,
        BREAST_TENDERNESS,
        FATIGUE,
        NAUSEA,
        PELVIC_PRESSURE,
        SLEEP_CHANGES,
        OTHER,
    )
}

object DisturbanceFlag {
    const val ILLNESS_OR_FEVER = 1L shl 0
    const val SHORT_SLEEP = 1L shl 1
    const val INTERRUPTED_SLEEP = 1L shl 2
    const val LATE_MEASUREMENT = 1L shl 3
    const val ALCOHOL = 1L shl 4
    const val MEDICATION = 1L shl 5
    const val TRAVEL_OR_TIMEZONE = 1L shl 6
    const val DIFFERENT_MEASUREMENT_SITE = 1L shl 7
    const val NOT_IMMEDIATELY_AFTER_WAKING = 1L shl 8
    const val OTHER = 1L shl 9

    const val MAJOR_EXCLUSION_RECOMMENDATION = ILLNESS_OR_FEVER or DIFFERENT_MEASUREMENT_SITE

    val all: List<Long> = listOf(
        ILLNESS_OR_FEVER,
        SHORT_SLEEP,
        INTERRUPTED_SLEEP,
        LATE_MEASUREMENT,
        ALCOHOL,
        MEDICATION,
        TRAVEL_OR_TIMEZONE,
        DIFFERENT_MEASUREMENT_SITE,
        NOT_IMMEDIATELY_AFTER_WAKING,
        OTHER,
    )
}

@Serializable
data class Cycle(
    val id: Long = 0,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val analysisSite: MeasurementSite? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val note: String? = null,
) {
    val startDate: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
    val endDate: LocalDate? get() = endEpochDay?.let(LocalDate::ofEpochDay)
    fun contains(date: LocalDate): Boolean =
        date.toEpochDay() >= startEpochDay && (endEpochDay == null || date.toEpochDay() <= endEpochDay)
}

@Serializable
data class TemperatureMeasurement(
    val id: Long = 0,
    val measurementEpochDay: Long,
    val measuredAtEpochMillis: Long,
    val timezoneId: String,
    val temperatureCentiC: Int,
    val site: MeasurementSite,
    val sleepMinutes: Int? = null,
    val measuredImmediatelyAfterWaking: Boolean? = null,
    val disturbanceMask: Long = 0,
    val disturbanceNote: String? = null,
    val note: String? = null,
    val selectedForAnalysis: Boolean = true,
    val source: MeasurementSource = MeasurementSource.MANUAL,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(measurementEpochDay)
    val measuredAt: Instant get() = Instant.ofEpochMilli(measuredAtEpochMillis)
    val zoneId: ZoneId get() = runCatching { ZoneId.of(timezoneId) }.getOrDefault(ZoneId.systemDefault())
}

@Serializable
data class DailyObservation(
    val id: Long = 0,
    val epochDay: Long,
    val bleeding: BleedingLevel = BleedingLevel.NOT_RECORDED,
    val mucus: CervicalMucus = CervicalMucus.NOT_CHECKED,
    val mucusSensation: MucusSensation = MucusSensation.NOT_CHECKED,
    val mucusObscured: Boolean = false,
    val lhResult: LhResult = LhResult.NOT_TESTED,
    val lhTestMinutesOfDay: Int? = null,
    val lhTestBrand: String? = null,
    val lhTestSensitivityMilliIu: Int? = null,
    val ovulationPain: OvulationPain = OvulationPain.NOT_RECORDED,
    val moodMask: Long = 0,
    val moodNote: String? = null,
    val libidoLevel: LibidoLevel = LibidoLevel.NOT_RECORDED,
    val sexualContact: SexualContact = SexualContact.NOT_RECORDED,
    val sexualContactInitiatedByUser: Boolean? = null,
    val physicalSymptomMask: Long = 0,
    val painReliefPillCount: Int? = null,
    val painReliefMedicationNote: String? = null,
    val isExplicitCycleStart: Boolean = false,
    val note: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

@Serializable
data class PredictionSnapshot(
    val id: Long = 0,
    val epochDay: Long,
    val cycleId: Long? = null,
    val engineVersion: String,
    val status: String,
    val evidenceLevel: String,
    val estimatedOvulationStartEpochDay: Long? = null,
    val estimatedOvulationEndEpochDay: Long? = null,
    val thermalShiftFirstHighEpochDay: Long? = null,
    val baselineCentiC: Int? = null,
    val dataQuality: String,
    val explanationJson: String,
    val generatedAtEpochMillis: Long,
)

@Serializable
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val trackingGoal: TrackingGoal = TrackingGoal.CYCLE_AWARENESS,
    val defaultMeasurementSite: MeasurementSite = MeasurementSite.ORAL,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 7,
    val reminderMinute: Int = 0,
    val biometricLockEnabled: Boolean = false,
    val screenshotsBlocked: Boolean = false,
    val acceptedDisclaimerVersion: Int = 0,
    val lastSuccessfulBackupEpochMillis: Long? = null,
    val chartVisibleDays: Int = 40,
)

data class MeasurementInput(
    val id: Long = 0,
    val date: LocalDate,
    val measuredAtEpochMillis: Long,
    val timezoneId: String,
    val temperatureCentiC: Int,
    val site: MeasurementSite,
    val sleepMinutes: Int?,
    val measuredImmediatelyAfterWaking: Boolean?,
    val disturbanceMask: Long,
    val disturbanceNote: String?,
    val note: String?,
    val selectedForAnalysis: Boolean,
)

data class ObservationInput(
    val date: LocalDate,
    val bleeding: BleedingLevel,
    val mucus: CervicalMucus,
    val mucusSensation: MucusSensation = MucusSensation.NOT_CHECKED,
    val mucusObscured: Boolean = false,
    val lhResult: LhResult,
    val lhTestMinutesOfDay: Int? = null,
    val lhTestBrand: String? = null,
    val lhTestSensitivityMilliIu: Int? = null,
    val ovulationPain: OvulationPain,
    val moodMask: Long = 0,
    val moodNote: String? = null,
    val libidoLevel: LibidoLevel = LibidoLevel.NOT_RECORDED,
    val sexualContact: SexualContact = SexualContact.NOT_RECORDED,
    val sexualContactInitiatedByUser: Boolean? = null,
    val physicalSymptomMask: Long = 0,
    val painReliefPillCount: Int? = null,
    val painReliefMedicationNote: String? = null,
    val isExplicitCycleStart: Boolean,
    val note: String?,
)

sealed interface AppError {
    data object DatabaseUnavailable : AppError
    data object InvalidTemperature : AppError
    data object OverlappingCycle : AppError
    data object BackupWrongPasswordOrCorrupt : AppError
    data object BackupUnsupportedVersion : AppError
    data object FileAccessDenied : AppError
    data object NotificationPermissionDenied : AppError
    data class Unknown(val cause: Throwable) : AppError
}

const val DISCLAIMER_VERSION = 1

const val LH_TEST_BRAND_MAX_LENGTH = 80
const val LH_TEST_SENSITIVITY_MIN_MILLI_IU = 5
const val LH_TEST_SENSITIVITY_MAX_MILLI_IU = 100
const val MOOD_NOTE_MAX_LENGTH = 500
const val MAX_DAILY_PAIN_RELIEF_PILLS = 20
const val PAIN_RELIEF_MEDICATION_NOTE_MAX_LENGTH = 200
