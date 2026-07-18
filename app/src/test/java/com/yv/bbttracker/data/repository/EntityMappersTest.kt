package com.yv.bbttracker.data.repository

import com.yv.bbttracker.data.local.entity.CycleEntity
import com.yv.bbttracker.data.local.entity.DailyObservationEntity
import com.yv.bbttracker.data.local.entity.TemperatureMeasurementEntity
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MeasurementSource
import com.yv.bbttracker.domain.model.MoodFlag
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.OvulationPain
import com.yv.bbttracker.domain.model.PhysicalSymptomFlag
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMappersTest {
    @Test
    fun `cycle mapper preserves every field`() {
        val entity = CycleEntity(
            id = 41,
            startEpochDay = 20_000,
            endEpochDay = 20_028,
            analysisSite = MeasurementSite.VAGINAL,
            createdAtEpochMillis = 101,
            updatedAtEpochMillis = 202,
            note = "הערת מחזור",
        )

        assertEquals(
            Cycle(
                id = 41,
                startEpochDay = 20_000,
                endEpochDay = 20_028,
                analysisSite = MeasurementSite.VAGINAL,
                createdAtEpochMillis = 101,
                updatedAtEpochMillis = 202,
                note = "הערת מחזור",
            ),
            entity.toDomain(),
        )
    }

    @Test
    fun `measurement mapper preserves nullable metadata flags and source`() {
        val entity = TemperatureMeasurementEntity(
            id = 42,
            measurementEpochDay = 20_001,
            measuredAtEpochMillis = 1_700_000_000_000,
            timezoneId = "Asia/Jerusalem",
            temperatureCentiC = 3_657,
            site = MeasurementSite.VAGINAL,
            sleepMinutes = null,
            measuredImmediatelyAfterWaking = false,
            disturbanceMask = 513,
            disturbanceNote = "נסיעה",
            note = "הערת מדידה",
            selectedForAnalysis = false,
            source = MeasurementSource.MANUAL,
            createdAtEpochMillis = 303,
            updatedAtEpochMillis = 404,
        )

        assertEquals(
            TemperatureMeasurement(
                id = 42,
                measurementEpochDay = 20_001,
                measuredAtEpochMillis = 1_700_000_000_000,
                timezoneId = "Asia/Jerusalem",
                temperatureCentiC = 3_657,
                site = MeasurementSite.VAGINAL,
                sleepMinutes = null,
                measuredImmediatelyAfterWaking = false,
                disturbanceMask = 513,
                disturbanceNote = "נסיעה",
                note = "הערת מדידה",
                selectedForAnalysis = false,
                source = MeasurementSource.MANUAL,
                createdAtEpochMillis = 303,
                updatedAtEpochMillis = 404,
            ),
            entity.toDomain(),
        )
    }

    @Test
    fun `observation mapper preserves every enum and cycle start marker`() {
        val entity = DailyObservationEntity(
            id = 43,
            epochDay = 20_002,
            bleeding = BleedingLevel.HEAVY,
            mucus = CervicalMucus.EGG_WHITE,
            mucusSensation = MucusSensation.SLIPPERY,
            mucusObscured = true,
            lhResult = LhResult.BORDERLINE,
            lhTestMinutesOfDay = 13 * 60 + 25,
            lhTestBrand = "Example LH",
            lhTestSensitivityMilliIu = 25,
            ovulationPain = OvulationPain.RIGHT,
            moodMask = MoodFlag.HAPPY or MoodFlag.ENERGETIC,
            moodNote = "יום נעים",
            libidoLevel = LibidoLevel.HIGH,
            sexualContact = SexualContact.YES,
            sexualContactInitiatedByUser = true,
            physicalSymptomMask = PhysicalSymptomFlag.BLOATING or PhysicalSymptomFlag.HEADACHE,
            painReliefPillCount = 2,
            painReliefMedicationNote = "איבופרופן",
            isExplicitCycleStart = true,
            note = "הערת תצפית",
            createdAtEpochMillis = 505,
            updatedAtEpochMillis = 606,
        )

        assertEquals(
            DailyObservation(
                id = 43,
                epochDay = 20_002,
                bleeding = BleedingLevel.HEAVY,
                mucus = CervicalMucus.EGG_WHITE,
                mucusSensation = MucusSensation.SLIPPERY,
                mucusObscured = true,
                lhResult = LhResult.BORDERLINE,
                lhTestMinutesOfDay = 13 * 60 + 25,
                lhTestBrand = "Example LH",
                lhTestSensitivityMilliIu = 25,
                ovulationPain = OvulationPain.RIGHT,
                moodMask = MoodFlag.HAPPY or MoodFlag.ENERGETIC,
                moodNote = "יום נעים",
                libidoLevel = LibidoLevel.HIGH,
                sexualContact = SexualContact.YES,
                sexualContactInitiatedByUser = true,
                physicalSymptomMask = PhysicalSymptomFlag.BLOATING or PhysicalSymptomFlag.HEADACHE,
                painReliefPillCount = 2,
                painReliefMedicationNote = "איבופרופן",
                isExplicitCycleStart = true,
                note = "הערת תצפית",
                createdAtEpochMillis = 505,
                updatedAtEpochMillis = 606,
            ),
            entity.toDomain(),
        )
    }
}
