package com.yv.bbttracker.feature.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.LH_TEST_BRAND_MAX_LENGTH
import com.yv.bbttracker.domain.model.LH_TEST_SENSITIVITY_MAX_MILLI_IU
import com.yv.bbttracker.domain.model.LH_TEST_SENSITIVITY_MIN_MILLI_IU
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MAX_DAILY_PAIN_RELIEF_PILLS
import com.yv.bbttracker.domain.model.MOOD_NOTE_MAX_LENGTH
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.ObservationInput
import com.yv.bbttracker.domain.model.OvulationPain
import com.yv.bbttracker.domain.model.PAIN_RELIEF_MEDICATION_NOTE_MAX_LENGTH
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.repository.CycleRepository
import com.yv.bbttracker.domain.repository.ObservationRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

data class ObservationUiState(
    val date: LocalDate,
    val bleeding: BleedingLevel = BleedingLevel.NOT_RECORDED,
    val mucus: CervicalMucus = CervicalMucus.NOT_CHECKED,
    val mucusSensation: MucusSensation = MucusSensation.NOT_CHECKED,
    val mucusObscured: Boolean = false,
    val lhResult: LhResult = LhResult.NOT_TESTED,
    val lhTestMinutesOfDay: Int? = null,
    val lhTestBrand: String = "",
    val lhTestSensitivityText: String = "",
    val lhSensitivityInvalid: Boolean = false,
    val ovulationPain: OvulationPain = OvulationPain.NOT_RECORDED,
    val moodMask: Long = 0,
    val moodNote: String = "",
    val libidoLevel: LibidoLevel = LibidoLevel.NOT_RECORDED,
    val sexualContact: SexualContact = SexualContact.NOT_RECORDED,
    val sexualContactInitiatedByUser: Boolean? = null,
    val physicalSymptomMask: Long = 0,
    val painReliefPillCount: Int? = null,
    val painReliefMedicationNote: String = "",
    val isExplicitCycleStart: Boolean = false,
    val note: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val showCycleConfirmation: Boolean = false,
    val nearbyCycleWarning: Boolean = false,
    val cycleStartRequiresBleeding: Boolean = false,
    val isExistingCycleStart: Boolean = false,
    val saveFailed: Boolean = false,
)

sealed interface ObservationEvent {
    data class BleedingChanged(val value: BleedingLevel) : ObservationEvent
    data class MucusChanged(val value: CervicalMucus) : ObservationEvent
    data class MucusSensationChanged(val value: MucusSensation) : ObservationEvent
    data class MucusObscuredChanged(val value: Boolean) : ObservationEvent
    data class LhChanged(val value: LhResult) : ObservationEvent
    data class LhTestTimeChanged(val minutesOfDay: Int?) : ObservationEvent
    data class LhTestBrandChanged(val value: String) : ObservationEvent
    data class LhTestSensitivityChanged(val value: String) : ObservationEvent
    data class PainChanged(val value: OvulationPain) : ObservationEvent
    data class MoodToggled(val flag: Long) : ObservationEvent
    data class MoodNoteChanged(val value: String) : ObservationEvent
    data class LibidoChanged(val value: LibidoLevel) : ObservationEvent
    data class SexualContactChanged(val value: SexualContact) : ObservationEvent
    data class SexualContactInitiatedChanged(val value: Boolean?) : ObservationEvent
    data class PhysicalSymptomToggled(val flag: Long) : ObservationEvent
    data class PainReliefPillCountChanged(val value: Int?) : ObservationEvent
    data class PainReliefMedicationNoteChanged(val value: String) : ObservationEvent
    data class CycleStartChanged(val value: Boolean) : ObservationEvent
    data class NoteChanged(val value: String) : ObservationEvent
    data object Save : ObservationEvent
    data object ConfirmCycleStart : ObservationEvent
    data object DismissCycleStart : ObservationEvent
}

sealed interface ObservationEffect { data object Saved : ObservationEffect }

class ObservationViewModel(
    date: LocalDate,
    suggestCycleStart: Boolean,
    private val observationRepository: ObservationRepository,
    private val cycleRepository: CycleRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ObservationUiState(
            date = date,
            isExplicitCycleStart = suggestCycleStart,
        ),
    )
    val state: StateFlow<ObservationUiState> = _state.asStateFlow()
    private val effectChannel = Channel<ObservationEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val existing = observationRepository.observeObservation(date).first()
            val cycleExists = cycleRepository.getCycles().any { it.startEpochDay == date.toEpochDay() }
            _state.update {
                if (existing == null) it.copy(
                    isExplicitCycleStart = it.isExplicitCycleStart || cycleExists,
                    isExistingCycleStart = cycleExists,
                    isLoading = false,
                )
                else it.copy(
                    bleeding = existing.bleeding,
                    mucus = existing.mucus,
                    mucusSensation = existing.mucusSensation,
                    mucusObscured = existing.mucusObscured,
                    lhResult = existing.lhResult,
                    lhTestMinutesOfDay = existing.lhTestMinutesOfDay,
                    lhTestBrand = existing.lhTestBrand.orEmpty(),
                    lhTestSensitivityText = existing.lhTestSensitivityMilliIu?.toString().orEmpty(),
                    ovulationPain = existing.ovulationPain,
                    moodMask = existing.moodMask,
                    moodNote = existing.moodNote.orEmpty(),
                    libidoLevel = existing.libidoLevel,
                    sexualContact = existing.sexualContact,
                    sexualContactInitiatedByUser = existing.sexualContactInitiatedByUser,
                    physicalSymptomMask = existing.physicalSymptomMask,
                    painReliefPillCount = existing.painReliefPillCount,
                    painReliefMedicationNote = existing.painReliefMedicationNote.orEmpty(),
                    isExplicitCycleStart = existing.isExplicitCycleStart || cycleExists,
                    isExistingCycleStart = cycleExists,
                    note = existing.note.orEmpty(),
                    isLoading = false,
                )
            }
        }
    }

    fun onEvent(event: ObservationEvent) {
        when (event) {
            is ObservationEvent.BleedingChanged -> _state.update {
                it.copy(bleeding = event.value, cycleStartRequiresBleeding = false)
            }
            is ObservationEvent.MucusChanged -> _state.update { it.copy(mucus = event.value) }
            is ObservationEvent.MucusSensationChanged -> _state.update { it.copy(mucusSensation = event.value) }
            is ObservationEvent.MucusObscuredChanged -> _state.update { it.copy(mucusObscured = event.value) }
            is ObservationEvent.LhChanged -> _state.update {
                if (event.value == LhResult.NOT_TESTED) {
                    it.copy(
                        lhResult = event.value,
                        lhTestMinutesOfDay = null,
                        lhTestBrand = "",
                        lhTestSensitivityText = "",
                        lhSensitivityInvalid = false,
                    )
                } else {
                    it.copy(lhResult = event.value)
                }
            }
            is ObservationEvent.LhTestTimeChanged -> _state.update {
                it.copy(lhTestMinutesOfDay = event.minutesOfDay?.coerceIn(0, 1_439))
            }
            is ObservationEvent.LhTestBrandChanged -> _state.update {
                it.copy(lhTestBrand = event.value.take(LH_TEST_BRAND_MAX_LENGTH))
            }
            is ObservationEvent.LhTestSensitivityChanged -> _state.update {
                it.copy(
                    lhTestSensitivityText = event.value.filter(Char::isDigit).take(3),
                    lhSensitivityInvalid = false,
                )
            }
            is ObservationEvent.PainChanged -> _state.update { it.copy(ovulationPain = event.value) }
            is ObservationEvent.MoodToggled -> _state.update {
                val selected = it.moodMask and event.flag != 0L
                it.copy(moodMask = if (selected) it.moodMask and event.flag.inv() else it.moodMask or event.flag)
            }
            is ObservationEvent.MoodNoteChanged -> _state.update {
                it.copy(moodNote = event.value.take(MOOD_NOTE_MAX_LENGTH))
            }
            is ObservationEvent.LibidoChanged -> _state.update { it.copy(libidoLevel = event.value) }
            is ObservationEvent.SexualContactChanged -> _state.update {
                it.copy(
                    sexualContact = event.value,
                    sexualContactInitiatedByUser = it.sexualContactInitiatedByUser
                        .takeIf { event.value == SexualContact.YES },
                )
            }
            is ObservationEvent.SexualContactInitiatedChanged -> _state.update {
                if (it.sexualContact == SexualContact.YES) {
                    it.copy(sexualContactInitiatedByUser = event.value)
                } else {
                    it.copy(sexualContactInitiatedByUser = null)
                }
            }
            is ObservationEvent.PhysicalSymptomToggled -> _state.update {
                val selected = it.physicalSymptomMask and event.flag != 0L
                it.copy(
                    physicalSymptomMask = if (selected) {
                        it.physicalSymptomMask and event.flag.inv()
                    } else {
                        it.physicalSymptomMask or event.flag
                    },
                )
            }
            is ObservationEvent.PainReliefPillCountChanged -> _state.update {
                val count = event.value?.coerceIn(0, MAX_DAILY_PAIN_RELIEF_PILLS)
                it.copy(
                    painReliefPillCount = count,
                    painReliefMedicationNote = it.painReliefMedicationNote
                        .takeIf { count != null && count > 0 }
                        .orEmpty(),
                )
            }
            is ObservationEvent.PainReliefMedicationNoteChanged -> _state.update {
                it.copy(
                    painReliefMedicationNote = event.value
                        .take(PAIN_RELIEF_MEDICATION_NOTE_MAX_LENGTH),
                )
            }
            is ObservationEvent.CycleStartChanged -> _state.update {
                if (it.isExistingCycleStart && !event.value) it
                else it.copy(isExplicitCycleStart = event.value, cycleStartRequiresBleeding = false)
            }
            is ObservationEvent.NoteChanged -> _state.update { it.copy(note = event.value.take(1000)) }
            ObservationEvent.Save -> requestSave()
            ObservationEvent.ConfirmCycleStart -> save(confirmCycleStart = true)
            ObservationEvent.DismissCycleStart -> _state.update { it.copy(showCycleConfirmation = false) }
        }
    }

    private fun requestSave() {
        val sensitivity = _state.value.lhTestSensitivityText.toIntOrNull()
        if (_state.value.lhResult != LhResult.NOT_TESTED &&
            _state.value.lhTestSensitivityText.isNotBlank() &&
            (sensitivity == null ||
                sensitivity !in LH_TEST_SENSITIVITY_MIN_MILLI_IU..LH_TEST_SENSITIVITY_MAX_MILLI_IU)
        ) {
            _state.update { it.copy(lhSensitivityInvalid = true) }
            return
        }
        if (_state.value.isExplicitCycleStart) {
            if (_state.value.bleeding !in setOf(BleedingLevel.LIGHT, BleedingLevel.MEDIUM, BleedingLevel.HEAVY)) {
                _state.update { it.copy(cycleStartRequiresBleeding = true) }
                return
            }
            viewModelScope.launch {
                val state = _state.value
                val warning = cycleRepository.getCycles().any {
                    it.startEpochDay != state.date.toEpochDay() && abs(it.startEpochDay - state.date.toEpochDay()) < 10
                }
                _state.update { it.copy(showCycleConfirmation = true, nearbyCycleWarning = warning) }
            }
        } else save(confirmCycleStart = false)
    }

    private fun save(confirmCycleStart: Boolean) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            val current = _state.value
            _state.update { it.copy(isSaving = true, showCycleConfirmation = false, saveFailed = false) }
            val cycles = cycleRepository.getCycles()
            val shouldStartCycle = current.isExplicitCycleStart &&
                confirmCycleStart &&
                cycles.none { it.startEpochDay == current.date.toEpochDay() }
            val observationResult = observationRepository.upsertObservation(
                input = ObservationInput(
                    date = current.date,
                    bleeding = current.bleeding,
                    mucus = current.mucus,
                    mucusSensation = current.mucusSensation,
                    mucusObscured = current.mucusObscured,
                    lhResult = current.lhResult,
                    lhTestMinutesOfDay = current.lhTestMinutesOfDay,
                    lhTestBrand = current.lhTestBrand,
                    lhTestSensitivityMilliIu = current.lhTestSensitivityText.toIntOrNull(),
                    ovulationPain = current.ovulationPain,
                    moodMask = current.moodMask,
                    moodNote = current.moodNote,
                    libidoLevel = current.libidoLevel,
                    sexualContact = current.sexualContact,
                    sexualContactInitiatedByUser = current.sexualContactInitiatedByUser,
                    physicalSymptomMask = current.physicalSymptomMask,
                    painReliefPillCount = current.painReliefPillCount,
                    painReliefMedicationNote = current.painReliefMedicationNote,
                    isExplicitCycleStart = current.isExplicitCycleStart,
                    note = current.note,
                ),
                startCycle = shouldStartCycle,
            )
            if (observationResult.isSuccess) effectChannel.send(ObservationEffect.Saved)
            else _state.update { it.copy(isSaving = false, saveFailed = true) }
        }
    }

    class Factory(
        private val date: LocalDate,
        private val suggestCycleStart: Boolean,
        private val observationRepository: ObservationRepository,
        private val cycleRepository: CycleRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ObservationViewModel(date, suggestCycleStart, observationRepository, cycleRepository) as T
    }
}
