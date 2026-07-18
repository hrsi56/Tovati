package com.yv.bbttracker.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yv.bbttracker.domain.engine.BacktestSummary
import com.yv.bbttracker.domain.engine.CycleBacktestResult
import com.yv.bbttracker.domain.engine.ForecastReliability
import com.yv.bbttracker.domain.engine.HistoricalCycleBuilder
import com.yv.bbttracker.domain.engine.ThermalShiftDetector
import com.yv.bbttracker.domain.engine.WalkForwardBacktester
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import com.yv.bbttracker.domain.repository.CycleRepository
import com.yv.bbttracker.domain.repository.MeasurementRepository
import com.yv.bbttracker.domain.repository.ObservationRepository
import com.yv.bbttracker.domain.repository.SettingsRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CycleHistoryItem(
    val cycle: Cycle,
    val lengthDays: Int?,
    val includedMeasurements: Int,
    val completenessPercent: Int,
    val retrospectiveOvulationStartDate: LocalDate? = null,
    val retrospectiveOvulationEndDate: LocalDate? = null,
    val estimateReliability: ForecastReliability = ForecastReliability.INSUFFICIENT,
    val lutealPhaseDays: Int? = null,
)

enum class HistoryMessage {
    CYCLE_START_UPDATED,
    CYCLE_DELETED,
    CYCLE_UPDATE_FAILED,
    CYCLE_DELETE_FAILED,
}

data class HistoryUiState(
    val cycles: List<CycleHistoryItem> = emptyList(),
    val measurements: List<TemperatureMeasurement> = emptyList(),
    val observations: List<DailyObservation> = emptyList(),
    val isLoading: Boolean = true,
    val isCycleMutationInProgress: Boolean = false,
    val message: HistoryMessage? = null,
    val backtest: BacktestSummary? = null,
) {
    val backtestByCycleId: Map<Long, CycleBacktestResult>
        get() = backtest?.cycleResults?.associateBy { it.cycle.id }.orEmpty()
}

private data class HistoryData(
    val cycles: List<CycleHistoryItem>,
    val measurements: List<TemperatureMeasurement>,
    val observations: List<DailyObservation>,
    val backtest: BacktestSummary,
)

private data class HistoryActionState(
    val inProgress: Boolean = false,
    val message: HistoryMessage? = null,
)

class HistoryViewModel(
    private val cycleRepository: CycleRepository,
    measurementRepository: MeasurementRepository,
    observationRepository: ObservationRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val actionState = MutableStateFlow(HistoryActionState())

    private val historyData = combine(
        cycleRepository.observeCycles(),
        measurementRepository.observeAllMeasurements(),
        observationRepository.observeAllObservations(),
        settingsRepository.settings,
    ) { cycles, measurements, observations, settings ->
        HistoryData(
            cycles = cycles
                .sortedByDescending { it.startEpochDay }
                .map { cycle -> cycle.toHistoryItem(measurements, observations, settings.defaultMeasurementSite) },
            measurements = measurements.sortedWith(
                compareByDescending<TemperatureMeasurement> { it.measurementEpochDay }
                    .thenByDescending { it.measuredAtEpochMillis },
            ),
            observations = observations.sortedByDescending { it.epochDay },
            backtest = WalkForwardBacktester.backtest(
                cycles = cycles,
                measurements = measurements,
                observations = observations,
                fallbackSite = settings.defaultMeasurementSite,
            ),
        )
    }.flowOn(Dispatchers.Default)

    val state = combine(historyData, actionState) { data, action ->
        HistoryUiState(
            cycles = data.cycles,
            measurements = data.measurements,
            observations = data.observations,
            isLoading = false,
            isCycleMutationInProgress = action.inProgress,
            message = action.message,
            backtest = data.backtest,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun updateCycleStart(cycleId: Long, date: LocalDate) {
        if (actionState.value.inProgress) return
        viewModelScope.launch {
            actionState.value = HistoryActionState(inProgress = true)
            val succeeded = if (date.isAfter(LocalDate.now())) {
                false
            } else {
                runCatching { cycleRepository.updateCycleStart(cycleId, date) }
                    .getOrNull()
                    ?.isSuccess == true
            }
            actionState.value = HistoryActionState(
                message = if (succeeded) {
                    HistoryMessage.CYCLE_START_UPDATED
                } else {
                    HistoryMessage.CYCLE_UPDATE_FAILED
                },
            )
        }
    }

    fun deleteCycle(cycleId: Long) {
        if (actionState.value.inProgress) return
        viewModelScope.launch {
            actionState.value = HistoryActionState(inProgress = true)
            val succeeded = runCatching { cycleRepository.deleteCycle(cycleId) }
                .getOrNull()
                ?.isSuccess == true
            actionState.value = HistoryActionState(
                message = if (succeeded) {
                    HistoryMessage.CYCLE_DELETED
                } else {
                    HistoryMessage.CYCLE_DELETE_FAILED
                },
            )
        }
    }

    fun consumeMessage(message: HistoryMessage) {
        actionState.update { current ->
            if (current.message == message) current.copy(message = null) else current
        }
    }

    private fun Cycle.toHistoryItem(
        measurements: List<TemperatureMeasurement>,
        observations: List<DailyObservation>,
        fallbackSite: MeasurementSite,
    ): CycleHistoryItem {
        val analysisEnd = endDate ?: LocalDate.now()
        val length = endDate?.let { ChronoUnit.DAYS.between(startDate, it).toInt() + 1 }
        val cycleMeasurements = measurements.filter { contains(it.date) }

        val cycleSite = analysisSite ?: ThermalShiftDetector.preferredSite(cycleMeasurements, fallbackSite)
        val validMeasurements = ThermalShiftDetector.chooseValidMeasurements(cycleMeasurements, cycleSite)
        val historicalAnalysis = HistoricalCycleBuilder.build(
            cycle = this,
            measurements = cycleMeasurements,
            observations = observations.filter { contains(it.date) },
            fallbackSite = fallbackSite,
            asOfDate = analysisEnd,
        )
        val retrospectiveRange = historicalAnalysis.estimatedOvulationRange
        val elapsedDays = (ChronoUnit.DAYS.between(startDate, analysisEnd).toInt() + 1).coerceAtLeast(1)

        return CycleHistoryItem(
            cycle = this,
            lengthDays = length,
            includedMeasurements = validMeasurements.size,
            completenessPercent = (validMeasurements.size * 100 / elapsedDays).coerceIn(0, 100),
            retrospectiveOvulationStartDate = retrospectiveRange?.start,
            retrospectiveOvulationEndDate = retrospectiveRange?.endInclusive,
            estimateReliability = historicalAnalysis.reliability,
            lutealPhaseDays = historicalAnalysis.lutealPhaseDays,
        )
    }

    class Factory(
        private val cycleRepository: CycleRepository,
        private val measurementRepository: MeasurementRepository,
        private val observationRepository: ObservationRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HistoryViewModel(
                cycleRepository,
                measurementRepository,
                observationRepository,
                settingsRepository,
            ) as T
    }
}
