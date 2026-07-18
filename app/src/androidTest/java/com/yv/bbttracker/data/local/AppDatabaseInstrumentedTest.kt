package com.yv.bbttracker.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yv.bbttracker.data.local.entity.CycleEntity
import com.yv.bbttracker.data.local.entity.DailyObservationEntity
import com.yv.bbttracker.data.local.entity.PredictionSnapshotEntity
import com.yv.bbttracker.data.local.entity.TemperatureMeasurementEntity
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MeasurementSource
import com.yv.bbttracker.domain.model.OvulationPain
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun allDaos_crudRoundTripsAndObservableQueriesReflectChanges() = runBlocking {
        val cycleDao = database.cycleDao()
        val measurementDao = database.measurementDao()
        val observationDao = database.observationDao()
        val predictionDao = database.predictionDao()

        val cycleId = cycleDao.insert(cycle(startDay = 20_000L))
        assertEquals(listOf(cycleId), cycleDao.observeAll().first().map { it.id })
        assertEquals(cycleId, cycleDao.observeCurrent().first()?.id)

        val storedCycle = requireNotNull(cycleDao.getById(cycleId))
        cycleDao.update(storedCycle.copy(endEpochDay = 20_028L, note = "completed"))
        assertEquals("completed", cycleDao.observeAll().first().single().note)
        assertNull(cycleDao.observeCurrent().first())

        val measurementId = measurementDao.insert(
            measurement(day = 20_003L, measuredAt = 1_700_000_000_000L),
        )
        assertEquals(
            3_665,
            measurementDao.observeForDate(20_003L).first().single().temperatureCentiC,
        )
        val storedMeasurement = requireNotNull(measurementDao.getById(measurementId))
        measurementDao.update(storedMeasurement.copy(temperatureCentiC = 3_672, note = "corrected"))
        assertEquals(
            3_672,
            measurementDao.observeInRange(20_000L, 20_010L).first().single().temperatureCentiC,
        )

        val observationId = observationDao.insert(observation(day = 20_003L))
        assertEquals(observationId, observationDao.observeForDate(20_003L).first()?.id)
        val storedObservation = requireNotNull(observationDao.getByDay(20_003L))
        observationDao.update(storedObservation.copy(lhResult = LhResult.POSITIVE, note = "peak"))
        assertEquals(LhResult.POSITIVE, observationDao.observeAll().first().single().lhResult)

        val predictionId = predictionDao.insert(prediction(day = 20_003L, cycleId = cycleId))
        assertEquals(predictionId, predictionDao.getAll().single().id)

        predictionDao.deleteAll()
        observationDao.deleteAll()
        measurementDao.deleteById(measurementId)
        cycleDao.delete(requireNotNull(cycleDao.getById(cycleId)))

        assertTrue(predictionDao.getAll().isEmpty())
        assertNull(observationDao.observeForDate(20_003L).first())
        assertTrue(measurementDao.observeForDate(20_003L).first().isEmpty())
        assertTrue(cycleDao.observeAll().first().isEmpty())
    }

    @Test
    fun uniqueDayConstraintsAndSelectionTransaction_preserveOneDailyRecordAndOneSelection() = runBlocking {
        val cycleDao = database.cycleDao()
        val observationDao = database.observationDao()
        val measurementDao = database.measurementDao()
        val day = 20_100L

        cycleDao.insert(cycle(startDay = day))
        val duplicateCycle = runCatching { cycleDao.insert(cycle(startDay = day, now = 2L)) }
        assertTrue(duplicateCycle.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(1, cycleDao.getAll().size)

        observationDao.insert(observation(day = day))
        val duplicateObservation = runCatching {
            observationDao.insert(observation(day = day, now = 2L))
        }
        assertTrue(duplicateObservation.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(1, observationDao.getAll().size)

        val firstId = measurementDao.insert(
            measurement(day = day, measuredAt = 10L, selected = true),
        )
        val secondId = measurementDao.insert(
            measurement(day = day, measuredAt = 20L, selected = false),
        )
        measurementDao.insert(
            measurement(day = day + 1, measuredAt = 30L, selected = true),
        )

        measurementDao.selectForAnalysis(id = secondId, epochDay = day, updatedAt = 99L)

        val sameDay = measurementDao.observeForDate(day).first()
        assertEquals(2, sameDay.size)
        assertEquals(listOf(secondId), sameDay.filter { it.selectedForAnalysis }.map { it.id })
        assertFalse(requireNotNull(measurementDao.getById(firstId)).selectedForAnalysis)
        assertEquals(99L, requireNotNull(measurementDao.getById(firstId)).updatedAtEpochMillis)
        assertEquals(99L, requireNotNull(measurementDao.getById(secondId)).updatedAtEpochMillis)

        val nextDay = measurementDao.observeForDate(day + 1).first().single()
        assertTrue(nextDay.selectedForAnalysis)
        assertNotNull(measurementDao.getById(nextDay.id))
    }

    @Test
    fun measurementFlow_emitsAfterInsertUpdateAndDeleteInvalidations() = runBlocking {
        val measurementDao = database.measurementDao()
        val day = 20_200L
        val updates = Channel<List<TemperatureMeasurementEntity>>(capacity = Channel.UNLIMITED)
        val collection = launch {
            measurementDao.observeForDate(day).collect { updates.send(it) }
        }

        try {
            assertTrue(nextUpdate(updates).isEmpty())

            val id = measurementDao.insert(measurement(day = day, measuredAt = 10L))
            assertEquals(listOf(3_665), nextUpdate(updates).map { it.temperatureCentiC })

            val inserted = requireNotNull(measurementDao.getById(id))
            measurementDao.update(inserted.copy(temperatureCentiC = 3_680))
            assertEquals(listOf(3_680), nextUpdate(updates).map { it.temperatureCentiC })

            measurementDao.deleteById(id)
            assertTrue(nextUpdate(updates).isEmpty())
        } finally {
            collection.cancelAndJoin()
            updates.close()
        }
    }

    private suspend fun nextUpdate(
        updates: Channel<List<TemperatureMeasurementEntity>>,
    ): List<TemperatureMeasurementEntity> = withTimeout(5_000L) { updates.receive() }

    private fun cycle(startDay: Long, now: Long = 1L) = CycleEntity(
        startEpochDay = startDay,
        endEpochDay = null,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
        note = null,
    )

    private fun measurement(
        day: Long,
        measuredAt: Long,
        selected: Boolean = true,
    ) = TemperatureMeasurementEntity(
        measurementEpochDay = day,
        measuredAtEpochMillis = measuredAt,
        timezoneId = "Asia/Jerusalem",
        temperatureCentiC = 3_665,
        site = MeasurementSite.ORAL,
        sleepMinutes = 420,
        measuredImmediatelyAfterWaking = true,
        disturbanceMask = 0L,
        disturbanceNote = null,
        note = null,
        selectedForAnalysis = selected,
        source = MeasurementSource.MANUAL,
        createdAtEpochMillis = measuredAt,
        updatedAtEpochMillis = measuredAt,
    )

    private fun observation(day: Long, now: Long = 1L) = DailyObservationEntity(
        epochDay = day,
        bleeding = BleedingLevel.LIGHT,
        mucus = CervicalMucus.CREAMY,
        lhResult = LhResult.NEGATIVE,
        ovulationPain = OvulationPain.NONE,
        isExplicitCycleStart = false,
        note = null,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
    )

    private fun prediction(day: Long, cycleId: Long) = PredictionSnapshotEntity(
        epochDay = day,
        cycleId = cycleId,
        engineVersion = "test-engine",
        status = "THERMAL_SHIFT_CONFIRMED",
        evidenceLevel = "BBT_ONLY",
        estimatedOvulationStartEpochDay = day - 2,
        estimatedOvulationEndEpochDay = day - 1,
        thermalShiftFirstHighEpochDay = day,
        baselineCentiC = 3_660,
        dataQuality = "GOOD",
        explanationJson = "{}",
        generatedAtEpochMillis = 100L,
    )
}
