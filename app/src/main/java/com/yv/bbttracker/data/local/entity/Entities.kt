package com.yv.bbttracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MeasurementSource
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.OvulationPain
import com.yv.bbttracker.domain.model.SexualContact

@Entity(
    tableName = "cycles",
    indices = [Index(value = ["startEpochDay"], unique = true)],
)
data class CycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val analysisSite: MeasurementSite? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val note: String?,
)

@Entity(
    tableName = "temperature_measurements",
    indices = [Index("measurementEpochDay")],
)
data class TemperatureMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measurementEpochDay: Long,
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
    val source: MeasurementSource = MeasurementSource.MANUAL,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "daily_observations",
    indices = [Index(value = ["epochDay"], unique = true)],
)
data class DailyObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val bleeding: BleedingLevel,
    val mucus: CervicalMucus,
    @ColumnInfo(defaultValue = "'NOT_CHECKED'")
    val mucusSensation: MucusSensation = MucusSensation.NOT_CHECKED,
    @ColumnInfo(defaultValue = "0")
    val mucusObscured: Boolean = false,
    val lhResult: LhResult,
    val lhTestMinutesOfDay: Int? = null,
    val lhTestBrand: String? = null,
    val lhTestSensitivityMilliIu: Int? = null,
    val ovulationPain: OvulationPain,
    @ColumnInfo(defaultValue = "0")
    val moodMask: Long = 0,
    val moodNote: String? = null,
    @ColumnInfo(defaultValue = "'NOT_RECORDED'")
    val libidoLevel: LibidoLevel = LibidoLevel.NOT_RECORDED,
    @ColumnInfo(defaultValue = "'NOT_RECORDED'")
    val sexualContact: SexualContact = SexualContact.NOT_RECORDED,
    val sexualContactInitiatedByUser: Boolean? = null,
    @ColumnInfo(defaultValue = "0")
    val physicalSymptomMask: Long = 0,
    val painReliefPillCount: Int? = null,
    val painReliefMedicationNote: String? = null,
    val isExplicitCycleStart: Boolean,
    val note: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "prediction_snapshots",
    indices = [Index("epochDay")],
)
data class PredictionSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val cycleId: Long?,
    val engineVersion: String,
    val status: String,
    val evidenceLevel: String,
    val estimatedOvulationStartEpochDay: Long?,
    val estimatedOvulationEndEpochDay: Long?,
    val thermalShiftFirstHighEpochDay: Long?,
    val baselineCentiC: Int?,
    val dataQuality: String,
    val explanationJson: String,
    val generatedAtEpochMillis: Long,
)
