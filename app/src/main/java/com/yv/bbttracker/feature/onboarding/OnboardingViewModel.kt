package com.yv.bbttracker.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yv.bbttracker.domain.model.AppSettings
import com.yv.bbttracker.domain.model.DISCLAIMER_VERSION
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TrackingGoal
import com.yv.bbttracker.domain.repository.SettingsRepository
import com.yv.bbttracker.notification.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val page: Int = 0,
    val trackingGoal: TrackingGoal = TrackingGoal.CYCLE_AWARENESS,
    val measurementSite: MeasurementSite = MeasurementSite.ORAL,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 7,
    val reminderMinute: Int = 0,
    val disclaimerAccepted: Boolean = false,
    val isSaving: Boolean = false,
) {
    val canGoBack: Boolean get() = page > 0
    val isLastPage: Boolean get() = page == LAST_PAGE
    val canContinue: Boolean get() = !isLastPage || disclaimerAccepted

    companion object {
        const val LAST_PAGE = 6
        const val PAGE_COUNT = 7
    }
}

sealed interface OnboardingEvent {
    data object Next : OnboardingEvent
    data object Back : OnboardingEvent
    data class GoalChanged(val goal: TrackingGoal) : OnboardingEvent
    data class SiteChanged(val site: MeasurementSite) : OnboardingEvent
    data class ReminderChanged(val enabled: Boolean) : OnboardingEvent
    data class ReminderTimeChanged(val hour: Int, val minute: Int) : OnboardingEvent
    data class DisclaimerChanged(val accepted: Boolean) : OnboardingEvent
}

class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    initialSettings: AppSettings = AppSettings(),
) : ViewModel() {
    private val _state = MutableStateFlow(
        OnboardingUiState(
            page = if (initialSettings.onboardingCompleted) OnboardingUiState.LAST_PAGE else 0,
            trackingGoal = initialSettings.trackingGoal,
            measurementSite = initialSettings.defaultMeasurementSite,
            reminderEnabled = initialSettings.reminderEnabled,
            reminderHour = initialSettings.reminderHour,
            reminderMinute = initialSettings.reminderMinute,
        ),
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.Back -> _state.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }
            OnboardingEvent.Next -> {
                val current = _state.value
                if (current.isLastPage) finish() else _state.update { it.copy(page = it.page + 1) }
            }
            is OnboardingEvent.GoalChanged -> _state.update { it.copy(trackingGoal = event.goal) }
            is OnboardingEvent.SiteChanged -> _state.update { it.copy(measurementSite = event.site) }
            is OnboardingEvent.ReminderChanged -> _state.update { it.copy(reminderEnabled = event.enabled) }
            is OnboardingEvent.ReminderTimeChanged -> _state.update {
                it.copy(reminderHour = event.hour.coerceIn(0, 23), reminderMinute = event.minute.coerceIn(0, 59))
            }
            is OnboardingEvent.DisclaimerChanged -> _state.update { it.copy(disclaimerAccepted = event.accepted) }
        }
    }

    private fun finish() {
        val current = _state.value
        if (!current.disclaimerAccepted || current.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            settingsRepository.update {
                AppSettings(
                    onboardingCompleted = true,
                    trackingGoal = current.trackingGoal,
                    defaultMeasurementSite = current.measurementSite,
                    reminderEnabled = current.reminderEnabled,
                    reminderHour = current.reminderHour,
                    reminderMinute = current.reminderMinute,
                    biometricLockEnabled = it.biometricLockEnabled,
                    screenshotsBlocked = it.screenshotsBlocked,
                    acceptedDisclaimerVersion = DISCLAIMER_VERSION,
                    lastSuccessfulBackupEpochMillis = it.lastSuccessfulBackupEpochMillis,
                    chartVisibleDays = it.chartVisibleDays,
                )
            }
            if (current.reminderEnabled) reminderScheduler.schedule(current.reminderHour, current.reminderMinute)
            else reminderScheduler.cancel()
            _state.update { it.copy(isSaving = false) }
        }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val reminderScheduler: ReminderScheduler,
        private val initialSettings: AppSettings = AppSettings(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            OnboardingViewModel(settingsRepository, reminderScheduler, initialSettings) as T
    }
}
