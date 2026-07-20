package com.yv.bbttracker.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yv.bbttracker.R
import com.yv.bbttracker.domain.model.AppSettings
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.MeasurementInput
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.ObservationInput
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import com.yv.bbttracker.domain.repository.CycleRepository
import com.yv.bbttracker.domain.repository.MeasurementRepository
import com.yv.bbttracker.domain.repository.ObservationRepository
import com.yv.bbttracker.domain.repository.SettingsRepository
import com.yv.bbttracker.ui.formatting.Formatters
import com.yv.bbttracker.ui.theme.BbtTheme
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScrollRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openingMeasurementsDirectlyAndScrollingToOldestEntryDoesNotCrash() {
        val firstDate = LocalDate.now().minusDays(83)
        val cycles = (0L until 3L).map { index ->
            val startDate = firstDate.plusDays(index * 28)
            Cycle(
                id = index + 1,
                startEpochDay = startDate.toEpochDay(),
                endEpochDay = if (index < 2) startDate.plusDays(27).toEpochDay() else null,
                analysisSite = MeasurementSite.ORAL,
                createdAtEpochMillis = startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                updatedAtEpochMillis = startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            )
        }
        val measurements = (0L until 74L).map { index ->
            val date = firstDate.plusDays(index)
            val timestamp = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            TemperatureMeasurement(
                id = index + 1,
                measurementEpochDay = date.toEpochDay(),
                measuredAtEpochMillis = timestamp,
                timezoneId = "Asia/Jerusalem",
                temperatureCentiC = 3630 + (index % 20).toInt(),
                site = MeasurementSite.ORAL,
                selectedForAnalysis = true,
                createdAtEpochMillis = timestamp,
                updatedAtEpochMillis = timestamp,
            )
        }
        val viewModel = HistoryViewModel(
            cycleRepository = FakeCycleRepository(cycles),
            measurementRepository = FakeMeasurementRepository(measurements),
            observationRepository = FakeObservationRepository,
            settingsRepository = FakeSettingsRepository,
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val measurementsTitle = context.getString(R.string.history_measurements_section, measurements.size)
        val oldestDateText = Formatters.date(firstDate)

        composeRule.setContent {
            BbtTheme(darkTheme = false) {
                HistoryScreen(
                    viewModel = viewModel,
                    onEditMeasurement = {},
                    onEditObservation = {},
                    onOpenChart = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.state.value.measurements.size == measurements.size
        }
        composeRule
            .onNodeWithTag(HISTORY_LIST_TEST_TAG)
            .performScrollToNode(hasText(measurementsTitle))
        composeRule.onNodeWithText(measurementsTitle).performClick()
        composeRule
            .onNodeWithTag(HISTORY_LIST_TEST_TAG)
            .performScrollToNode(hasText(oldestDateText))
        composeRule.onNodeWithText(oldestDateText).assertIsDisplayed()
    }

    private class FakeCycleRepository(
        private val cycles: List<Cycle>,
    ) : CycleRepository {
        override fun observeCurrentCycle(): Flow<Cycle?> = flowOf(cycles.lastOrNull())
        override fun observeCycles(): Flow<List<Cycle>> = flowOf(cycles)
        override suspend fun getCycles(): List<Cycle> = cycles
        override suspend fun startCycle(date: LocalDate, note: String?): Result<Long> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateCycleStart(cycleId: Long, date: LocalDate): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteCycle(cycleId: Long): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }

    private class FakeMeasurementRepository(
        private val measurements: List<TemperatureMeasurement>,
    ) : MeasurementRepository {
        override fun observeMeasurementsForCycle(cycleId: Long): Flow<List<TemperatureMeasurement>> =
            flowOf(measurements)
        override fun observeMeasurementsInRange(
            start: LocalDate,
            end: LocalDate,
        ): Flow<List<TemperatureMeasurement>> = flowOf(measurements.filter { it.date in start..end })
        override fun observeMeasurementForDate(date: LocalDate): Flow<List<TemperatureMeasurement>> =
            flowOf(measurements.filter { it.date == date })
        override fun observeAllMeasurements(): Flow<List<TemperatureMeasurement>> = flowOf(measurements)
        override suspend fun getAllMeasurements(): List<TemperatureMeasurement> = measurements
        override suspend fun getMeasurement(id: Long): TemperatureMeasurement? =
            measurements.firstOrNull { it.id == id }
        override suspend fun saveMeasurement(input: MeasurementInput): Result<Long> =
            Result.failure(UnsupportedOperationException())
        override suspend fun selectForAnalysis(measurementId: Long): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteMeasurement(measurementId: Long): Result<Unit> =
            Result.failure(UnsupportedOperationException())
    }

    private object FakeObservationRepository : ObservationRepository {
        override fun observeObservation(date: LocalDate): Flow<DailyObservation?> = flowOf(null)
        override fun observeObservations(
            start: LocalDate,
            end: LocalDate,
        ): Flow<List<DailyObservation>> = flowOf(emptyList())
        override fun observeAllObservations(): Flow<List<DailyObservation>> = flowOf(emptyList())
        override suspend fun getAllObservations(): List<DailyObservation> = emptyList()
        override suspend fun upsertObservation(
            input: ObservationInput,
            startCycle: Boolean,
        ): Result<Unit> = Result.failure(UnsupportedOperationException())
    }

    private object FakeSettingsRepository : SettingsRepository {
        private val value = AppSettings(onboardingCompleted = true)
        override val settings: Flow<AppSettings> = flowOf(value)
        override suspend fun getSettings(): AppSettings = value
        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }
}
