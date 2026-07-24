package com.yv.bbttracker.feature.entry

import com.yv.bbttracker.domain.model.AppSettings
import com.yv.bbttracker.domain.model.MeasurementInput
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import com.yv.bbttracker.domain.repository.MeasurementRepository
import com.yv.bbttracker.domain.repository.SettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementEntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `existing past date can be replaced and a fresh today entry remains saveable`() =
        runTest(dispatcher) {
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val repository = FakeMeasurementRepository(
                mutableListOf(measurement(id = 1, date = yesterday, temperature = 3_650)),
            )

            val yesterdayEntry = viewModel(yesterday, repository)
            advanceUntilIdle()
            yesterdayEntry.onEvent(MeasurementEntryEvent.TemperatureChanged("36.80"))
            yesterdayEntry.onEvent(MeasurementEntryEvent.Save)
            advanceUntilIdle()
            assertEquals(EntryWarning.PAST_DATE, yesterdayEntry.state.value.warning)

            yesterdayEntry.onEvent(MeasurementEntryEvent.ConfirmWarning)
            advanceUntilIdle()
            assertEquals(1, repository.values.count { it.date == yesterday })
            assertEquals(3_680, repository.values.single { it.date == yesterday }.temperatureCentiC)

            val todayEntry = viewModel(today, repository)
            advanceUntilIdle()
            assertFalse(todayEntry.state.value.isLoading)
            assertFalse(todayEntry.state.value.isSaving)
            assertNull(todayEntry.state.value.warning)

            todayEntry.onEvent(MeasurementEntryEvent.TemperatureChanged("36.70"))
            todayEntry.onEvent(MeasurementEntryEvent.Save)
            advanceUntilIdle()
            assertEquals(today, repository.savedInputs.last().date)
            assertEquals(2, repository.values.size)
        }

    private fun viewModel(
        date: LocalDate,
        repository: MeasurementRepository,
    ) = MeasurementEntryViewModel(
        measurementId = 0,
        initialDate = date,
        measurementRepository = repository,
        settingsRepository = FakeSettingsRepository,
    )

    private class FakeMeasurementRepository(
        val values: MutableList<TemperatureMeasurement> = mutableListOf(),
    ) : MeasurementRepository {
        val savedInputs = mutableListOf<MeasurementInput>()

        override fun observeMeasurementsForCycle(cycleId: Long): Flow<List<TemperatureMeasurement>> =
            MutableStateFlow(values.toList())

        override fun observeMeasurementsInRange(
            start: LocalDate,
            end: LocalDate,
        ): Flow<List<TemperatureMeasurement>> = MutableStateFlow(
            values.filter { it.date in start..end },
        )

        override fun observeMeasurementForDate(date: LocalDate): Flow<List<TemperatureMeasurement>> =
            MutableStateFlow(values.filter { it.date == date })

        override fun observeAllMeasurements(): Flow<List<TemperatureMeasurement>> =
            MutableStateFlow(values.toList())

        override suspend fun getAllMeasurements(): List<TemperatureMeasurement> = values

        override suspend fun getMeasurement(id: Long): TemperatureMeasurement? =
            values.firstOrNull { it.id == id }

        override suspend fun saveMeasurement(input: MeasurementInput): Result<Long> {
            savedInputs += input
            val existing = values.firstOrNull { it.date == input.date }
            val id = input.id.takeIf { it != 0L } ?: existing?.id ?: (values.maxOfOrNull { it.id } ?: 0L) + 1
            values.removeAll { it.date == input.date || (input.id != 0L && it.id == input.id) }
            values += measurement(id, input.date, input.temperatureCentiC)
            return Result.success(id)
        }

        override suspend fun selectForAnalysis(measurementId: Long): Result<Unit> =
            Result.success(Unit)

        override suspend fun deleteMeasurement(measurementId: Long): Result<Unit> {
            values.removeAll { it.id == measurementId }
            return Result.success(Unit)
        }
    }

    private object FakeSettingsRepository : SettingsRepository {
        private val value = MutableStateFlow(AppSettings(defaultMeasurementSite = MeasurementSite.ORAL))
        override val settings: Flow<AppSettings> = value
        override suspend fun getSettings(): AppSettings = value.value
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            value.value = transform(value.value)
        }
    }

    private companion object {
        fun measurement(
            id: Long,
            date: LocalDate,
            temperature: Int,
        ) = TemperatureMeasurement(
            id = id,
            measurementEpochDay = date.toEpochDay(),
            measuredAtEpochMillis = date.toEpochDay() * 86_400_000L,
            timezoneId = "Asia/Jerusalem",
            temperatureCentiC = temperature,
            site = MeasurementSite.ORAL,
            createdAtEpochMillis = id,
            updatedAtEpochMillis = id,
        )
    }
}
