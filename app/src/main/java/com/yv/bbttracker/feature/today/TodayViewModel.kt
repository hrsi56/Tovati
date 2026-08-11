package com.yv.bbttracker.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yv.bbttracker.domain.engine.CycleAnalysisEngine
import com.yv.bbttracker.domain.engine.CycleAnalysisInput
import com.yv.bbttracker.domain.engine.CycleAnalysisResult
import com.yv.bbttracker.domain.engine.HistoricalCycleBuilder
import com.yv.bbttracker.domain.engine.ThermalShiftDetector
import com.yv.bbttracker.domain.insights.PersonalInsight
import com.yv.bbttracker.domain.insights.PersonalInsightsCalculator
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import com.yv.bbttracker.domain.model.TrackingGoal
import com.yv.bbttracker.domain.repository.CycleRepository
import com.yv.bbttracker.domain.repository.MeasurementRepository
import com.yv.bbttracker.domain.repository.ObservationRepository
import com.yv.bbttracker.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

internal const val RECENT_TEMPERATURE_WINDOW_DAYS = 7

data class TodayUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val cycle: Cycle? = null,
    val cycleDay: Int? = null,
    val measurements: List<TemperatureMeasurement> = emptyList(),
    val recentMeasurements: List<TemperatureMeasurement> = emptyList(),
    val observation: DailyObservation? = null,
    val analysis: CycleAnalysisResult? = null,
    val insights: List<PersonalInsight> = emptyList(),
    val trackingGoal: TrackingGoal = TrackingGoal.CYCLE_AWARENESS,
) {
    val primaryMeasurement: TemperatureMeasurement?
        get() = measurements.firstOrNull { it.selectedForAnalysis } ?: measurements.maxByOrNull { it.measuredAtEpochMillis }
}

class TodayViewModel(
    cycleRepository: CycleRepository,
    measurementRepository: MeasurementRepository,
    observationRepository: ObservationRepository,
    settingsRepository: SettingsRepository,
    private val engine: CycleAnalysisEngine = CycleAnalysisEngine(),
) : ViewModel() {
    private val dateFlow = flow {
        while (currentCoroutineContext().isActive) {
            val now = ZonedDateTime.now()
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L))
        }
    }

    private val cyclesFlow = combine(
        cycleRepository.observeCurrentCycle(),
        cycleRepository.observeCycles(),
    ) { current, cycles -> current to cycles }

    val state = combine(
        cyclesFlow,
        measurementRepository.observeAllMeasurements(),
        observationRepository.observeAllObservations(),
        settingsRepository.settings,
        dateFlow,
    ) { (currentCycle, cycles), measurements, observations, settings, today ->
        val todayMeasurements = measurements.filter { it.measurementEpochDay == today.toEpochDay() }
        val todayObservation = observations.firstOrNull { it.epochDay == today.toEpochDay() }
        val currentMeasurements = currentCycle
            ?.let { cycle -> measurements.filter { cycle.contains(it.date) } }
            .orEmpty()
        val currentSite = currentCycle?.analysisSite
            ?: ThermalShiftDetector.preferredSite(currentMeasurements, settings.defaultMeasurementSite)
        val recentMeasurements = selectRecentThermalMeasurements(
            measurements = measurements,
            today = today,
            measurementSite = currentSite,
        )
        val result = currentCycle?.let { cycle ->
            val historical = HistoricalCycleBuilder.buildAll(
                cycles = cycles.filter { it.id != cycle.id },
                measurements = measurements,
                observations = observations,
                fallbackSite = settings.defaultMeasurementSite,
                beforeDate = cycle.startDate,
            )
            engine.analyze(
                CycleAnalysisInput(
                    currentDate = today,
                    currentCycle = cycle,
                    previousCycles = historical,
                    temperatures = currentMeasurements,
                    observations = observations.filter { cycle.contains(it.date) },
                    defaultMeasurementSite = currentSite,
                    typicalCycleLengthDays = settings.typicalCycleLengthDays,
                ),
            )
        }
        TodayUiState(
            isLoading = false,
            date = today,
            cycle = currentCycle,
            cycleDay = currentCycle?.let { (today.toEpochDay() - it.startEpochDay + 1).toInt() }?.takeIf { it > 0 },
            measurements = todayMeasurements,
            recentMeasurements = recentMeasurements,
            observation = todayObservation,
            analysis = result,
            insights = PersonalInsightsCalculator.calculate(
                today = today,
                currentCycle = currentCycle,
                cycles = cycles,
                observations = observations,
            ),
            trackingGoal = settings.trackingGoal,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())
    class Factory(
        private val cycleRepository: CycleRepository,
        private val measurementRepository: MeasurementRepository,
        private val observationRepository: ObservationRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TodayViewModel(cycleRepository, measurementRepository, observationRepository, settingsRepository) as T
    }
}

/**
 * Uses the exact same eligibility rules as the thermal analysis: selected, reliable measurements
 * from one stable measurement site, with at most the latest saved measurement for each date.
 */
internal fun selectRecentThermalMeasurements(
    measurements: List<TemperatureMeasurement>,
    today: LocalDate,
    measurementSite: MeasurementSite,
): List<TemperatureMeasurement> {
    val windowStart = today.minusDays((RECENT_TEMPERATURE_WINDOW_DAYS - 1).toLong())
    return ThermalShiftDetector.chooseValidMeasurements(
        measurements = measurements.filter { it.date in windowStart..today },
        defaultMeasurementSite = measurementSite,
    )
}
