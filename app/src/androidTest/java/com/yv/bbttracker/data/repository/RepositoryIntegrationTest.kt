package com.yv.bbttracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yv.bbttracker.data.local.AppDatabase
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.MeasurementInput
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.ObservationInput
import com.yv.bbttracker.domain.model.OvulationPain
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var cycleRepository: CycleRepositoryImpl
    private lateinit var measurementRepository: MeasurementRepositoryImpl
    private lateinit var observationRepository: ObservationRepositoryImpl

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cycleRepository = CycleRepositoryImpl(database)
        measurementRepository = MeasurementRepositoryImpl(database)
        observationRepository = ObservationRepositoryImpl(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun firstSelectedMeasurementLocksCycleSiteAndLaterSelectionsDoNotRewriteIt() = runBlocking {
        val start = LocalDate.ofEpochDay(20_000)
        val cycleId = cycleRepository.startCycle(start).getOrThrow()

        measurementRepository.saveMeasurement(
            measurementInput(start.plusDays(2), MeasurementSite.VAGINAL),
        ).getOrThrow()
        assertEquals(MeasurementSite.VAGINAL, database.cycleDao().getById(cycleId)?.analysisSite)

        measurementRepository.saveMeasurement(
            measurementInput(start.plusDays(1), MeasurementSite.ORAL),
        ).getOrThrow()
        assertEquals(MeasurementSite.VAGINAL, database.cycleDao().getById(cycleId)?.analysisSite)
    }

    @Test
    fun startingCycleAfterMeasurementBackfillsEarliestSelectedSite() = runBlocking {
        val start = LocalDate.ofEpochDay(21_000)
        measurementRepository.saveMeasurement(
            measurementInput(start.plusDays(2), MeasurementSite.RECTAL),
        ).getOrThrow()
        measurementRepository.saveMeasurement(
            measurementInput(start, MeasurementSite.ORAL),
        ).getOrThrow()

        val cycleId = cycleRepository.startCycle(start).getOrThrow()

        assertEquals(MeasurementSite.ORAL, database.cycleDao().getById(cycleId)?.analysisSite)
    }

    @Test
    fun movingCycleStartEarlierShortensPreviousCycleAndRehomesItsAnalysisSite() = runBlocking {
        val previousStart = LocalDate.ofEpochDay(21_100)
        val originalStart = previousStart.plusDays(10)
        val newStart = originalStart.minusDays(2)
        val previousId = cycleRepository.startCycle(previousStart).getOrThrow()

        // The first selection locks the previous cycle to VAGINAL even though an earlier ORAL
        // measurement is selected later. Moving the boundary earlier removes the VAGINAL lock
        // measurement from the previous cycle, so its remaining data should resolve to ORAL.
        measurementRepository.saveMeasurement(
            measurementInput(originalStart.minusDays(1), MeasurementSite.VAGINAL),
        ).getOrThrow()
        measurementRepository.saveMeasurement(
            measurementInput(previousStart.plusDays(2), MeasurementSite.ORAL),
        ).getOrThrow()
        val currentId = cycleRepository.startCycle(originalStart).getOrThrow()
        measurementRepository.saveMeasurement(
            measurementInput(originalStart, MeasurementSite.RECTAL),
        ).getOrThrow()
        recordActualBleeding(originalStart, explicitCycleStart = true)
        recordActualBleeding(newStart)

        cycleRepository.updateCycleStart(currentId, newStart).getOrThrow()

        val previous = database.cycleDao().getById(previousId)
        val current = database.cycleDao().getById(currentId)
        assertEquals(newStart.toEpochDay() - 1, previous?.endEpochDay)
        assertEquals(MeasurementSite.ORAL, previous?.analysisSite)
        assertEquals(newStart.toEpochDay(), current?.startEpochDay)
        assertEquals(MeasurementSite.RECTAL, current?.analysisSite)
        assertEquals(false, database.observationDao().getByDay(originalStart.toEpochDay())?.isExplicitCycleStart)
        assertEquals(true, database.observationDao().getByDay(newStart.toEpochDay())?.isExplicitCycleStart)
    }

    @Test
    fun movingCycleStartLaterExtendsPreviousCycleAndRehomesCurrentAnalysisSite() = runBlocking {
        val previousStart = LocalDate.ofEpochDay(21_200)
        val originalStart = previousStart.plusDays(10)
        val newStart = originalStart.plusDays(2)
        val previousId = cycleRepository.startCycle(previousStart).getOrThrow()
        val currentId = cycleRepository.startCycle(originalStart).getOrThrow()

        measurementRepository.saveMeasurement(
            measurementInput(originalStart, MeasurementSite.ORAL),
        ).getOrThrow()
        measurementRepository.saveMeasurement(
            measurementInput(newStart.plusDays(1), MeasurementSite.VAGINAL),
        ).getOrThrow()
        recordActualBleeding(originalStart, explicitCycleStart = true)
        recordActualBleeding(newStart)

        cycleRepository.updateCycleStart(currentId, newStart).getOrThrow()

        val previous = database.cycleDao().getById(previousId)
        val current = database.cycleDao().getById(currentId)
        assertEquals(newStart.toEpochDay() - 1, previous?.endEpochDay)
        assertEquals(MeasurementSite.ORAL, previous?.analysisSite)
        assertEquals(newStart.toEpochDay(), current?.startEpochDay)
        assertEquals(MeasurementSite.VAGINAL, current?.analysisSite)
        assertEquals(false, database.observationDao().getByDay(originalStart.toEpochDay())?.isExplicitCycleStart)
        assertEquals(true, database.observationDao().getByDay(newStart.toEpochDay())?.isExplicitCycleStart)
    }

    @Test
    fun notTestedLhClearsMetadataAndInvalidSensitivityIsRejected() = runBlocking {
        val date = LocalDate.ofEpochDay(22_000)
        val cleared = observationRepository.upsertObservation(
            observationInput(
                date = date,
                result = LhResult.NOT_TESTED,
                minutes = 800,
                brand = "Should be cleared",
                sensitivity = 25,
            ),
        )
        assertTrue(cleared.isSuccess)
        val stored = database.observationDao().getByDay(date.toEpochDay())
        assertNull(stored?.lhTestMinutesOfDay)
        assertNull(stored?.lhTestBrand)
        assertNull(stored?.lhTestSensitivityMilliIu)

        val rejected = observationRepository.upsertObservation(
            observationInput(
                date = date.plusDays(1),
                result = LhResult.BORDERLINE,
                sensitivity = 101,
            ),
        )
        assertTrue(rejected.isFailure)
        assertNull(database.observationDao().getByDay(date.plusDays(1).toEpochDay()))
    }

    private suspend fun recordActualBleeding(date: LocalDate, explicitCycleStart: Boolean = false) {
        observationRepository.upsertObservation(
            observationInput(
                date = date,
                result = LhResult.NOT_TESTED,
                bleeding = BleedingLevel.LIGHT,
                explicitCycleStart = explicitCycleStart,
            ),
            startCycle = false,
        ).getOrThrow()
    }

    private fun measurementInput(date: LocalDate, site: MeasurementSite) = MeasurementInput(
        date = date,
        measuredAtEpochMillis = date.toEpochDay() * 86_400_000L + 21_600_000L,
        timezoneId = "Asia/Jerusalem",
        temperatureCentiC = 3_665,
        site = site,
        sleepMinutes = 420,
        measuredImmediatelyAfterWaking = true,
        disturbanceMask = 0,
        disturbanceNote = null,
        note = null,
        selectedForAnalysis = true,
    )

    private fun observationInput(
        date: LocalDate,
        result: LhResult,
        minutes: Int? = null,
        brand: String? = null,
        sensitivity: Int? = null,
        bleeding: BleedingLevel = BleedingLevel.NOT_RECORDED,
        explicitCycleStart: Boolean = false,
    ) = ObservationInput(
        date = date,
        bleeding = bleeding,
        mucus = CervicalMucus.NOT_CHECKED,
        mucusSensation = MucusSensation.NOT_CHECKED,
        mucusObscured = false,
        lhResult = result,
        lhTestMinutesOfDay = minutes,
        lhTestBrand = brand,
        lhTestSensitivityMilliIu = sensitivity,
        ovulationPain = OvulationPain.NOT_RECORDED,
        isExplicitCycleStart = explicitCycleStart,
        note = null,
    )
}
