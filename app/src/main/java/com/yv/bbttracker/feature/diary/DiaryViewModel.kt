package com.yv.bbttracker.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yv.bbttracker.domain.engine.CycleAnalysisEngine
import com.yv.bbttracker.domain.engine.CycleAnalysisInput
import com.yv.bbttracker.domain.engine.HistoricalCycleBuilder
import com.yv.bbttracker.domain.engine.ThermalShiftDetector
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.repository.CycleRepository
import com.yv.bbttracker.domain.repository.MeasurementRepository
import com.yv.bbttracker.domain.repository.ObservationRepository
import com.yv.bbttracker.domain.repository.SettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DiaryUiState(
    val isLoading: Boolean = true,
    val calendar: DiaryCalendar? = null,
)

class DiaryViewModel(
    cycleRepository: CycleRepository,
    measurementRepository: MeasurementRepository,
    observationRepository: ObservationRepository,
    settingsRepository: SettingsRepository,
    private val engine: CycleAnalysisEngine = CycleAnalysisEngine(),
    private val todayProvider: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val selectedCycleId = MutableStateFlow<Long?>(null)

    val state = combine(
        cycleRepository.observeCycles(),
        measurementRepository.observeAllMeasurements(),
        observationRepository.observeAllObservations(),
        settingsRepository.settings,
        selectedCycleId,
    ) { cycles, measurements, observations, settings, requestedCycleId ->
        val cycle = selectCycle(cycles, requestedCycleId)
            ?: return@combine DiaryUiState(isLoading = false)
        val today = todayProvider()
        val cycleMeasurements = measurements.filter { cycle.contains(it.date) }
        val cycleObservations = observations.filter { cycle.contains(it.date) }
        val cycleSite = cycle.analysisSite
            ?: ThermalShiftDetector.preferredSite(cycleMeasurements, settings.defaultMeasurementSite)
        val historical = HistoricalCycleBuilder.buildAll(
            cycles = cycles.filter { it.id != cycle.id },
            measurements = measurements,
            observations = observations,
            fallbackSite = settings.defaultMeasurementSite,
            beforeDate = cycle.startDate,
        )
        val analysisDate = minOf(today, cycle.endDate ?: today)
        val analysis = engine.analyze(
            CycleAnalysisInput(
                currentDate = analysisDate,
                currentCycle = cycle,
                previousCycles = historical,
                temperatures = cycleMeasurements,
                observations = cycleObservations,
                defaultMeasurementSite = cycleSite,
                typicalCycleLengthDays = settings.typicalCycleLengthDays,
            ),
        )
        DiaryUiState(
            isLoading = false,
            calendar = DiaryCalendarBuilder.build(
                cycle = cycle,
                allCycles = cycles,
                measurements = cycleMeasurements,
                observations = cycleObservations,
                analysis = analysis,
                today = today,
                typicalMenstruationLengthDays = settings.typicalMenstruationLengthDays,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryUiState())

    fun selectCycle(cycleId: Long) {
        selectedCycleId.value = cycleId
    }

    private fun selectCycle(cycles: List<Cycle>, requestedCycleId: Long?): Cycle? =
        cycles.firstOrNull { it.id == requestedCycleId }
            ?: cycles.firstOrNull { it.endDate == null }
            ?: cycles.maxByOrNull { it.startEpochDay }

    class Factory(
        private val cycleRepository: CycleRepository,
        private val measurementRepository: MeasurementRepository,
        private val observationRepository: ObservationRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DiaryViewModel(
                cycleRepository,
                measurementRepository,
                observationRepository,
                settingsRepository,
            ) as T
    }
}
