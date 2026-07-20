package com.yv.bbttracker.data.backup

import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MeasurementSource
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.OvulationPain
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BackupPayloadCompatibilityTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = true
    }

    @Test
    fun `version one payload decodes with lossless legacy values and absent new metadata`() {
        val payload = json.decodeFromString(
            BackupPayload.serializer(),
            LEGACY_VERSION_ONE_PAYLOAD,
        )

        assertEquals(1, payload.schemaVersion)
        assertEquals(1, payload.cycles.size)
        assertEquals(null, payload.cycles.single().analysisSite)
        val observation = payload.dailyObservations.single()
        assertEquals(BleedingLevel.LIGHT, observation.bleeding)
        assertEquals(CervicalMucus.CREAMY, observation.mucus)
        assertEquals(MucusSensation.NOT_CHECKED, observation.mucusSensation)
        assertFalse(observation.mucusObscured)
        assertEquals(LhResult.POSITIVE, observation.lhResult)
        assertNull(observation.lhTestMinutesOfDay)
        assertNull(observation.lhTestBrand)
        assertNull(observation.lhTestSensitivityMilliIu)
        assertEquals(OvulationPain.NONE, observation.ovulationPain)
        assertEquals(0L, observation.moodMask)
        assertNull(observation.moodNote)
        assertEquals(LibidoLevel.NOT_RECORDED, observation.libidoLevel)
        assertEquals(SexualContact.NOT_RECORDED, observation.sexualContact)
        assertNull(observation.sexualContactInitiatedByUser)
        assertEquals(0L, observation.physicalSymptomMask)
        assertNull(observation.painReliefPillCount)
        assertNull(observation.painReliefMedicationNote)
        assertNull(payload.settings.typicalCycleLengthDays)
        assertNull(payload.settings.typicalMenstruationLengthDays)
    }

    @Test
    fun `version one restore normalization locks cycle to earliest selected in-range site`() {
        val decoded = json.decodeFromString(
            BackupPayload.serializer(),
            LEGACY_VERSION_ONE_PAYLOAD,
        )
        val payload = decoded.copy(
            temperatureMeasurements = listOf(
                measurement(id = 1, day = 20_005, site = MeasurementSite.VAGINAL),
                measurement(id = 2, day = 20_002, site = MeasurementSite.ORAL),
                measurement(id = 3, day = 19_999, site = MeasurementSite.RECTAL),
            ),
        )

        val normalized = payload.withBackfilledAnalysisSites()

        assertNull(payload.cycles.single().analysisSite)
        assertEquals(MeasurementSite.ORAL, normalized.cycles.single().analysisSite)
    }

    private fun measurement(
        id: Long,
        day: Long,
        site: MeasurementSite,
    ) = TemperatureMeasurement(
        id = id,
        measurementEpochDay = day,
        measuredAtEpochMillis = day * 86_400_000L + 21_600_000L,
        timezoneId = "Asia/Jerusalem",
        temperatureCentiC = 3_665,
        site = site,
        selectedForAnalysis = true,
        source = MeasurementSource.MANUAL,
        createdAtEpochMillis = id,
        updatedAtEpochMillis = id,
    )

    private companion object {
        val LEGACY_VERSION_ONE_PAYLOAD =
            """
            {
              "schemaVersion": 1,
              "appVersion": "1.0.0",
              "cycles": [{
                "id": 3,
                "startEpochDay": 20000,
                "endEpochDay": 20028,
                "createdAtEpochMillis": 101,
                "updatedAtEpochMillis": 202,
                "note": "legacy"
              }],
              "temperatureMeasurements": [],
              "dailyObservations": [{
                "id": 4,
                "epochDay": 20003,
                "bleeding": "LIGHT",
                "mucus": "CREAMY",
                "lhResult": "POSITIVE",
                "ovulationPain": "NONE",
                "isExplicitCycleStart": false,
                "note": "kept",
                "createdAtEpochMillis": 303,
                "updatedAtEpochMillis": 404
              }],
              "settings": {
                "onboardingCompleted": true,
                "trackingGoal": "TRYING_TO_CONCEIVE",
                "defaultMeasurementSite": "ORAL",
                "reminderEnabled": false,
                "reminderHour": 7,
                "reminderMinute": 0,
                "biometricLockEnabled": false,
                "screenshotsBlocked": true,
                "acceptedDisclaimerVersion": 1,
                "lastSuccessfulBackupEpochMillis": null,
                "chartVisibleDays": 40
              }
            }
            """.trimIndent()
    }
}
