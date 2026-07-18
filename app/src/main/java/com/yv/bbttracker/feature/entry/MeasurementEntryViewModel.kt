package com.yv.bbttracker.feature.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yv.bbttracker.domain.model.DisturbanceFlag
import com.yv.bbttracker.domain.model.MeasurementInput
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.repository.MeasurementRepository
import com.yv.bbttracker.domain.repository.SettingsRepository
import com.yv.bbttracker.domain.validation.TemperatureValidation
import com.yv.bbttracker.domain.validation.TemperatureValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class EntryWarning { PAST_DATE, SOFT_TEMPERATURE, DUPLICATE }

data class MeasurementEntryUiState(
    val id: Long = 0,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val timezoneId: String = ZoneId.systemDefault().id,
    val temperatureText: String = "",
    val site: MeasurementSite = MeasurementSite.ORAL,
    val sleepHoursText: String = "",
    val sleepMinutesText: String = "",
    val measuredImmediatelyAfterWaking: Boolean = true,
    val disturbanceMask: Long = 0,
    val disturbanceNote: String = "",
    val selectedForAnalysis: Boolean = true,
    val note: String = "",
    val error: EntryError? = null,
    val warning: EntryWarning? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
) {
    val isEditing: Boolean get() = id != 0L
}

enum class EntryError { INVALID_TEMPERATURE, FUTURE_TIME, SAVE_FAILED }

sealed interface MeasurementEntryEvent {
    data class DateChanged(val date: LocalDate) : MeasurementEntryEvent
    data class TimeChanged(val time: LocalTime) : MeasurementEntryEvent
    data class TemperatureChanged(val value: String) : MeasurementEntryEvent
    data class SiteChanged(val site: MeasurementSite) : MeasurementEntryEvent
    data class SleepHoursChanged(val value: String) : MeasurementEntryEvent
    data class SleepMinutesChanged(val value: String) : MeasurementEntryEvent
    data class ImmediatelyAfterWakingChanged(val value: Boolean) : MeasurementEntryEvent
    data class DisturbanceToggled(val flag: Long) : MeasurementEntryEvent
    data class DisturbanceNoteChanged(val value: String) : MeasurementEntryEvent
    data class SelectedForAnalysisChanged(val value: Boolean) : MeasurementEntryEvent
    data class NoteChanged(val value: String) : MeasurementEntryEvent
    data object Save : MeasurementEntryEvent
    data object ConfirmWarning : MeasurementEntryEvent
    data object DismissWarning : MeasurementEntryEvent
    data object RequestDelete : MeasurementEntryEvent
    data object DismissDelete : MeasurementEntryEvent
    data object ConfirmDelete : MeasurementEntryEvent
}

sealed interface MeasurementEntryEffect {
    data object Saved : MeasurementEntryEffect
    data object Deleted : MeasurementEntryEffect
}

class MeasurementEntryViewModel(
    private val measurementId: Long,
    initialDate: LocalDate,
    private val measurementRepository: MeasurementRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MeasurementEntryUiState(date = initialDate))
    val state: StateFlow<MeasurementEntryUiState> = _state.asStateFlow()
    private val effectsChannel = Channel<MeasurementEntryEffect>(Channel.BUFFERED)
    val effects = effectsChannel.receiveAsFlow()
    private val acceptedWarnings = mutableSetOf<EntryWarning>()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            val existing = measurementId.takeIf { it != 0L }?.let { measurementRepository.getMeasurement(it) }
            _state.update {
                if (existing == null) it.copy(site = settings.defaultMeasurementSite, isLoading = false)
                else it.copy(
                    id = existing.id,
                    date = existing.date,
                    time = existing.measuredAt.atZone(existing.zoneId).toLocalTime().withSecond(0).withNano(0),
                    timezoneId = existing.timezoneId,
                    temperatureText = TemperatureValidator.format(existing.temperatureCentiC),
                    site = existing.site,
                    sleepHoursText = existing.sleepMinutes?.div(60)?.toString().orEmpty(),
                    sleepMinutesText = existing.sleepMinutes?.rem(60)?.toString().orEmpty(),
                    measuredImmediatelyAfterWaking = existing.measuredImmediatelyAfterWaking ?: true,
                    disturbanceMask = existing.disturbanceMask,
                    disturbanceNote = existing.disturbanceNote.orEmpty(),
                    selectedForAnalysis = existing.selectedForAnalysis,
                    note = existing.note.orEmpty(),
                    isLoading = false,
                )
            }
        }
    }

    fun onEvent(event: MeasurementEntryEvent) {
        when (event) {
            is MeasurementEntryEvent.DateChanged -> change { it.copy(date = event.date, error = null) }
            is MeasurementEntryEvent.TimeChanged -> change { it.copy(time = event.time, error = null) }
            is MeasurementEntryEvent.TemperatureChanged -> change { it.copy(temperatureText = event.value.take(6), error = null) }
            is MeasurementEntryEvent.SiteChanged -> change { current ->
                current.copy(site = event.site, error = null).let { updated ->
                    if ((updated.disturbanceMask and DisturbanceFlag.DIFFERENT_MEASUREMENT_SITE) != 0L) {
                        updated.copy(selectedForAnalysis = false)
                    } else updated
                }
            }
            is MeasurementEntryEvent.SleepHoursChanged -> change {
                it.copy(sleepHoursText = event.value.filter(Char::isDigit).take(2))
            }
            is MeasurementEntryEvent.SleepMinutesChanged -> change {
                it.copy(sleepMinutesText = event.value.filter(Char::isDigit).take(2))
            }
            is MeasurementEntryEvent.ImmediatelyAfterWakingChanged -> change { current ->
                val mask = if (event.value) current.disturbanceMask and DisturbanceFlag.NOT_IMMEDIATELY_AFTER_WAKING.inv()
                else current.disturbanceMask or DisturbanceFlag.NOT_IMMEDIATELY_AFTER_WAKING
                current.copy(measuredImmediatelyAfterWaking = event.value, disturbanceMask = mask)
            }
            is MeasurementEntryEvent.DisturbanceToggled -> change { current ->
                val checked = current.disturbanceMask and event.flag != 0L
                val mask = if (checked) current.disturbanceMask and event.flag.inv() else current.disturbanceMask or event.flag
                current.copy(
                    disturbanceMask = mask,
                    selectedForAnalysis = if (!checked && event.flag and DisturbanceFlag.MAJOR_EXCLUSION_RECOMMENDATION != 0L) false
                    else current.selectedForAnalysis,
                )
            }
            is MeasurementEntryEvent.DisturbanceNoteChanged -> change { it.copy(disturbanceNote = event.value.take(500)) }
            is MeasurementEntryEvent.SelectedForAnalysisChanged -> change { it.copy(selectedForAnalysis = event.value) }
            is MeasurementEntryEvent.NoteChanged -> change { it.copy(note = event.value.take(1000)) }
            MeasurementEntryEvent.Save -> save()
            MeasurementEntryEvent.ConfirmWarning -> {
                _state.value.warning?.let(acceptedWarnings::add)
                _state.update { it.copy(warning = null) }
                save()
            }
            MeasurementEntryEvent.DismissWarning -> _state.update { it.copy(warning = null) }
            MeasurementEntryEvent.RequestDelete -> _state.update { it.copy(showDeleteConfirmation = true) }
            MeasurementEntryEvent.DismissDelete -> _state.update { it.copy(showDeleteConfirmation = false) }
            MeasurementEntryEvent.ConfirmDelete -> delete()
        }
    }

    private fun change(transform: (MeasurementEntryUiState) -> MeasurementEntryUiState) {
        acceptedWarnings.clear()
        _state.update(transform)
    }

    private fun save() {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            val current = _state.value
            val validation = TemperatureValidator.parse(current.temperatureText)
            if (validation !is TemperatureValidation.Valid) {
                _state.update { it.copy(error = EntryError.INVALID_TEMPERATURE) }
                return@launch
            }
            val zone = runCatching { ZoneId.of(current.timezoneId) }.getOrDefault(ZoneId.systemDefault())
            val measuredAt = current.date.atTime(current.time).atZone(zone).toInstant()
            if (measuredAt.isAfter(java.time.Instant.now().plus(Duration.ofHours(24)))) {
                _state.update { it.copy(error = EntryError.FUTURE_TIME) }
                return@launch
            }
            if (current.date.isBefore(LocalDate.now()) && EntryWarning.PAST_DATE !in acceptedWarnings) {
                _state.update { it.copy(warning = EntryWarning.PAST_DATE) }
                return@launch
            }
            if (validation.hasSoftWarning && EntryWarning.SOFT_TEMPERATURE !in acceptedWarnings) {
                _state.update { it.copy(warning = EntryWarning.SOFT_TEMPERATURE) }
                return@launch
            }
            val selectedAlready = measurementRepository.observeMeasurementForDate(current.date).first()
                .any { it.id != current.id && it.selectedForAnalysis }
            if (current.selectedForAnalysis && selectedAlready && EntryWarning.DUPLICATE !in acceptedWarnings) {
                _state.update { it.copy(warning = EntryWarning.DUPLICATE) }
                return@launch
            }
            _state.update { it.copy(isSaving = true, error = null) }
            val sleepMinutes = current.sleepHoursText.toIntOrNull()?.times(60)?.plus(
                current.sleepMinutesText.toIntOrNull() ?: 0,
            ) ?: current.sleepMinutesText.toIntOrNull()
            val result = measurementRepository.saveMeasurement(
                MeasurementInput(
                    id = current.id,
                    date = current.date,
                    measuredAtEpochMillis = measuredAt.toEpochMilli(),
                    timezoneId = zone.id,
                    temperatureCentiC = validation.centiCelsius,
                    site = current.site,
                    sleepMinutes = sleepMinutes?.coerceIn(0, 24 * 60),
                    measuredImmediatelyAfterWaking = current.measuredImmediatelyAfterWaking,
                    disturbanceMask = current.disturbanceMask,
                    disturbanceNote = current.disturbanceNote,
                    note = current.note,
                    selectedForAnalysis = current.selectedForAnalysis,
                ),
            )
            if (result.isSuccess) effectsChannel.send(MeasurementEntryEffect.Saved)
            else _state.update { it.copy(isSaving = false, error = EntryError.SAVE_FAILED) }
        }
    }

    private fun delete() {
        val id = _state.value.id
        if (id == 0L) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, showDeleteConfirmation = false) }
            if (measurementRepository.deleteMeasurement(id).isSuccess) effectsChannel.send(MeasurementEntryEffect.Deleted)
            else _state.update { it.copy(isSaving = false, error = EntryError.SAVE_FAILED) }
        }
    }

    class Factory(
        private val measurementId: Long,
        private val initialDate: LocalDate,
        private val measurementRepository: MeasurementRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MeasurementEntryViewModel(measurementId, initialDate, measurementRepository, settingsRepository) as T
    }
}
