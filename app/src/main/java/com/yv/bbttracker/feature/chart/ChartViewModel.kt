package com.yv.bbttracker.feature.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yv.bbttracker.domain.engine.CycleAnalysisEngine
import com.yv.bbttracker.domain.engine.CycleAnalysisInput
import com.yv.bbttracker.domain.engine.CycleAnalysisResult
import com.yv.bbttracker.domain.engine.HistoricalCycleBuilder
import com.yv.bbttracker.domain.engine.ThermalShiftDetector
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import com.yv.bbttracker.domain.repository.CycleRepository
import com.yv.bbttracker.domain.repository.MeasurementRepository
import com.yv.bbttracker.domain.repository.ObservationRepository
import com.yv.bbttracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class ChartUiState(
    val isLoading: Boolean = true,
    val cycle: Cycle? = null,
    val measurements: List<TemperatureMeasurement> = emptyList(),
    val observations: List<DailyObservation> = emptyList(),
    val analysis: CycleAnalysisResult? = null,
    val analysisSite: MeasurementSite = MeasurementSite.ORAL,
) {
    val isCurrentCycle: Boolean get() = cycle?.endDate == null
    val included: List<TemperatureMeasurement>
        get() = ThermalShiftDetector.chooseValidMeasurements(measurements, analysisSite)
    val excluded: List<TemperatureMeasurement>
        get() {
            val includedIds = included.mapTo(mutableSetOf()) { it.id }
            return measurements.filter { it.id !in includedIds }.sortedBy { it.measurementEpochDay }
        }
    val positiveLhDays: List<LocalDate>
        get() = observations.filter { it.lhResult == LhResult.POSITIVE }.map { it.date }
    val fertileMucusDays: List<LocalDate>
        get() = observations.filter {
            !it.mucusObscured &&
                (it.mucus == CervicalMucus.EGG_WHITE || it.mucusSensation == MucusSensation.SLIPPERY)
        }.map { it.date }
    val sexualContactDays: List<LocalDate>
        get() = observations.filter {
            it.sexualContact == SexualContact.SOME || it.sexualContact == SexualContact.YES
        }.map { it.date }

    fun cycleDay(date: LocalDate): Int? = cycle?.let { (date.toEpochDay() - it.startEpochDay + 1).toInt() }?.takeIf { it > 0 }
}

class ChartViewModel(
    cycleId: Long,
    cycleRepository: CycleRepository,
    measurementRepository: MeasurementRepository,
    observationRepository: ObservationRepository,
    settingsRepository: SettingsRepository,
    private val engine: CycleAnalysisEngine = CycleAnalysisEngine(),
) : ViewModel() {
    val state = combine(
        cycleRepository.observeCycles(),
        measurementRepository.observeAllMeasurements(),
        observationRepository.observeAllObservations(),
        settingsRepository.settings,
    ) { cycles, measurements, observations, settings ->
        val cycle = cycles.firstOrNull { it.id == cycleId }.takeIf { cycleId != 0L }
            ?: cycles.firstOrNull { it.endEpochDay == null }
            ?: cycles.firstOrNull()
        if (cycle == null) return@combine ChartUiState(isLoading = false)
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
        val analysisDate = minOf(LocalDate.now(), cycle.endDate ?: LocalDate.now())
        ChartUiState(
            isLoading = false,
            cycle = cycle,
            measurements = cycleMeasurements,
            observations = cycleObservations,
            analysisSite = cycleSite,
            analysis = engine.analyze(
                CycleAnalysisInput(
                    currentDate = analysisDate,
                    currentCycle = cycle,
                    previousCycles = historical,
                    temperatures = cycleMeasurements,
                    observations = cycleObservations,
                    defaultMeasurementSite = cycleSite,
                ),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartUiState())

    class Factory(
        private val cycleId: Long,
        private val cycleRepository: CycleRepository,
        private val measurementRepository: MeasurementRepository,
        private val observationRepository: ObservationRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChartViewModel(cycleId, cycleRepository, measurementRepository, observationRepository, settingsRepository) as T
    }
}
