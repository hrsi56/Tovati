package com.yv.bbttracker.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yv.bbttracker.domain.model.AppSettings
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TrackingGoal
import com.yv.bbttracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val trackingGoal = stringPreferencesKey("tracking_goal")
        val measurementSite = stringPreferencesKey("default_measurement_site")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val biometric = booleanPreferencesKey("biometric_lock_enabled")
        val screenshots = booleanPreferencesKey("screenshots_blocked")
        val disclaimer = intPreferencesKey("accepted_disclaimer_version")
        val lastBackup = longPreferencesKey("last_successful_backup")
        val chartDays = intPreferencesKey("chart_visible_days")
    }

    override val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map(::toSettings)

    override suspend fun getSettings(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { preferences ->
            writeSettings(preferences, transform(toSettings(preferences)))
        }
    }

    suspend fun reset() {
        context.settingsDataStore.edit { it.clear() }
    }

    private fun toSettings(preferences: Preferences): AppSettings = AppSettings(
        onboardingCompleted = preferences[Keys.onboarding] ?: false,
        trackingGoal = preferences[Keys.trackingGoal]
            ?.let { runCatching { TrackingGoal.valueOf(it) }.getOrNull() }
            ?: TrackingGoal.CYCLE_AWARENESS,
        defaultMeasurementSite = preferences[Keys.measurementSite]
            ?.let { runCatching { MeasurementSite.valueOf(it) }.getOrNull() }
            ?: MeasurementSite.ORAL,
        reminderEnabled = preferences[Keys.reminderEnabled] ?: false,
        reminderHour = (preferences[Keys.reminderHour] ?: 7).coerceIn(0, 23),
        reminderMinute = (preferences[Keys.reminderMinute] ?: 0).coerceIn(0, 59),
        biometricLockEnabled = preferences[Keys.biometric] ?: false,
        screenshotsBlocked = preferences[Keys.screenshots] ?: false,
        acceptedDisclaimerVersion = preferences[Keys.disclaimer] ?: 0,
        lastSuccessfulBackupEpochMillis = preferences[Keys.lastBackup],
        chartVisibleDays = (preferences[Keys.chartDays] ?: 40).coerceIn(10, 120),
    )

    private fun writeSettings(preferences: androidx.datastore.preferences.core.MutablePreferences, value: AppSettings) {
        preferences[Keys.onboarding] = value.onboardingCompleted
        preferences[Keys.trackingGoal] = value.trackingGoal.name
        preferences[Keys.measurementSite] = value.defaultMeasurementSite.name
        preferences[Keys.reminderEnabled] = value.reminderEnabled
        preferences[Keys.reminderHour] = value.reminderHour.coerceIn(0, 23)
        preferences[Keys.reminderMinute] = value.reminderMinute.coerceIn(0, 59)
        preferences[Keys.biometric] = value.biometricLockEnabled
        preferences[Keys.screenshots] = value.screenshotsBlocked
        preferences[Keys.disclaimer] = value.acceptedDisclaimerVersion
        value.lastSuccessfulBackupEpochMillis?.let { preferences[Keys.lastBackup] = it }
            ?: preferences.remove(Keys.lastBackup)
        preferences[Keys.chartDays] = value.chartVisibleDays.coerceIn(10, 120)
    }
}

