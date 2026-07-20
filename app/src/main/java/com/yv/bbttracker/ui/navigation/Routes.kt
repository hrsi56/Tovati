package com.yv.bbttracker.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object TodayRoute : NavKey
@Serializable data class ChartRoute(val cycleId: Long = 0) : NavKey
@Serializable data object DiaryRoute : NavKey
@Serializable data object HistoryRoute : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data class ReconfigureOnboardingRoute(val requestId: Long) : NavKey
@Serializable data class MeasurementRoute(val measurementId: Long = 0, val dateEpochDay: Long) : NavKey
@Serializable data class ObservationRoute(val dateEpochDay: Long, val suggestCycleStart: Boolean = false) : NavKey
