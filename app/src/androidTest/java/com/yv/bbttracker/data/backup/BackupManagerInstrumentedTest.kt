package com.yv.bbttracker.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yv.bbttracker.data.local.AppDatabase
import com.yv.bbttracker.data.repository.CycleRepositoryImpl
import com.yv.bbttracker.data.repository.MeasurementRepositoryImpl
import com.yv.bbttracker.data.repository.ObservationRepositoryImpl
import com.yv.bbttracker.domain.model.AppSettings
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MeasurementInput
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MoodFlag
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.ObservationInput
import com.yv.bbttracker.domain.model.OvulationPain
import com.yv.bbttracker.domain.model.PhysicalSymptomFlag
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.model.TrackingGoal
import com.yv.bbttracker.domain.repository.SettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupManagerInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var cycleRepository: CycleRepositoryImpl
    private lateinit var measurementRepository: MeasurementRepositoryImpl
    private lateinit var observationRepository: ObservationRepositoryImpl
    private lateinit var settingsRepository: InMemorySettingsRepository
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cycleRepository = CycleRepositoryImpl(database)
        measurementRepository = MeasurementRepositoryImpl(database)
        observationRepository = ObservationRepositoryImpl(database)
        settingsRepository = InMemorySettingsRepository(
            AppSettings(
                onboardingCompleted = true,
                trackingGoal = TrackingGoal.TRYING_TO_CONCEIVE,
                defaultMeasurementSite = MeasurementSite.VAGINAL,
                reminderEnabled = true,
                reminderHour = 6,
                reminderMinute = 35,
                biometricLockEnabled = true,
                screenshotsBlocked = true,
                acceptedDisclaimerVersion = 1,
                chartVisibleDays = 55,
            ),
        )
        backupManager = BackupManager(
            database = database,
            cycleRepository = cycleRepository,
            measurementRepository = measurementRepository,
            observationRepository = observationRepository,
            settingsRepository = settingsRepository,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun encryptedBackupRoundTripRestoresEveryCurrentFieldAndCsvColumn() = runBlocking {
        val date = LocalDate.of(2026, 7, 16)
        val moodMask = MoodFlag.HAPPY or MoodFlag.ENERGETIC
        val symptomMask = PhysicalSymptomFlag.HEADACHE or
            PhysicalSymptomFlag.BLOATING or
            PhysicalSymptomFlag.BACK_PAIN

        cycleRepository.startCycle(date.minusDays(12), "מחזור בדיקה").getOrThrow()
        measurementRepository.saveMeasurement(
            MeasurementInput(
                date = date,
                measuredAtEpochMillis = date.toEpochDay() * 86_400_000L + 6 * 3_600_000L + 40 * 60_000L,
                timezoneId = "Asia/Jerusalem",
                temperatureCentiC = 3_668,
                site = MeasurementSite.VAGINAL,
                sleepMinutes = 415,
                measuredImmediatelyAfterWaking = true,
                disturbanceMask = 0,
                disturbanceNote = null,
                note = "מדידה רגילה",
                selectedForAnalysis = true,
            ),
        ).getOrThrow()
        observationRepository.upsertObservation(
            ObservationInput(
                date = date,
                bleeding = BleedingLevel.NONE,
                mucus = CervicalMucus.EGG_WHITE,
                mucusSensation = MucusSensation.SLIPPERY,
                mucusObscured = false,
                lhResult = LhResult.POSITIVE,
                lhTestMinutesOfDay = 13 * 60 + 20,
                lhTestBrand = "בדיקת LH",
                lhTestSensitivityMilliIu = 25,
                ovulationPain = OvulationPain.RIGHT,
                moodMask = moodMask,
                moodNote = "שמחה ורגועה",
                libidoLevel = LibidoLevel.HIGH,
                sexualContact = SexualContact.YES,
                sexualContactInitiatedByUser = true,
                physicalSymptomMask = symptomMask,
                painReliefPillCount = 2,
                painReliefMedicationNote = "איבופרופן",
                isExplicitCycleStart = false,
                note = "הערה יומית",
            ),
        ).getOrThrow()

        val password = "סיסמת גיבוי חזקה 123!"
        val encrypted = backupManager.createEncryptedBackup(password.toCharArray())
        assertFalse(encrypted.contains("שמחה ורגועה"))
        assertEquals(RestoreSummary(1, 1, 1), backupManager.inspectEncryptedBackup(encrypted, password.toCharArray()))

        val csv = backupManager.exportCsv().toString(Charsets.UTF_8)
        assertTrue(
            csv.contains(
                "libido_level,sexual_contact,sexual_contact_initiated_by_user," +
                    "physical_symptoms,pain_relief_pill_count,pain_relief_medication_note",
            ),
        )
        assertTrue(csv.contains("HAPPY|ENERGETIC"))
        assertTrue(csv.contains("HIGH,YES,true"))
        assertTrue(csv.contains("HEADACHE|BLOATING|BACK_PAIN"))
        assertTrue(csv.contains(",2,איבופרופן,"))

        backupManager.deleteAllData()
        settingsRepository.update {
            AppSettings(
                onboardingCompleted = true,
                lastSuccessfulBackupEpochMillis = 123_456L,
            )
        }
        assertTrue(database.cycleDao().getAll().isEmpty())
        assertTrue(database.measurementDao().getAll().isEmpty())
        assertTrue(database.observationDao().getAll().isEmpty())

        val summary = backupManager.restoreEncryptedBackup(encrypted, password.toCharArray())

        assertEquals(RestoreSummary(1, 1, 1), summary)
        val cycle = database.cycleDao().getAll().single()
        val measurement = database.measurementDao().getAll().single()
        val observation = database.observationDao().getAll().single()
        assertEquals("מחזור בדיקה", cycle.note)
        assertEquals(MeasurementSite.VAGINAL, cycle.analysisSite)
        assertEquals(3_668, measurement.temperatureCentiC)
        assertEquals(415, measurement.sleepMinutes)
        assertEquals(CervicalMucus.EGG_WHITE, observation.mucus)
        assertEquals(MucusSensation.SLIPPERY, observation.mucusSensation)
        assertEquals(LhResult.POSITIVE, observation.lhResult)
        assertEquals("בדיקת LH", observation.lhTestBrand)
        assertEquals(moodMask, observation.moodMask)
        assertEquals("שמחה ורגועה", observation.moodNote)
        assertEquals(LibidoLevel.HIGH, observation.libidoLevel)
        assertEquals(SexualContact.YES, observation.sexualContact)
        assertTrue(observation.sexualContactInitiatedByUser == true)
        assertEquals(symptomMask, observation.physicalSymptomMask)
        assertEquals(2, observation.painReliefPillCount)
        assertEquals("איבופרופן", observation.painReliefMedicationNote)
        assertEquals("הערה יומית", observation.note)

        val restoredSettings = settingsRepository.getSettings()
        assertEquals(TrackingGoal.TRYING_TO_CONCEIVE, restoredSettings.trackingGoal)
        assertEquals(MeasurementSite.VAGINAL, restoredSettings.defaultMeasurementSite)
        assertTrue(restoredSettings.reminderEnabled)
        assertEquals(6, restoredSettings.reminderHour)
        assertEquals(35, restoredSettings.reminderMinute)
        assertFalse(restoredSettings.biometricLockEnabled)
        assertTrue(restoredSettings.screenshotsBlocked)
        assertEquals(55, restoredSettings.chartVisibleDays)
        assertEquals(123_456L, restoredSettings.lastSuccessfulBackupEpochMillis)
    }

    private class InMemorySettingsRepository(initial: AppSettings) : SettingsRepository {
        private val values = MutableStateFlow(initial)

        override val settings: Flow<AppSettings> = values

        override suspend fun getSettings(): AppSettings = values.value

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            values.value = transform(values.value)
        }
    }
}
