package com.yv.bbttracker.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.yv.bbttracker.R
import com.yv.bbttracker.domain.model.DISCLAIMER_VERSION
import com.yv.bbttracker.feature.chart.ChartScreen
import com.yv.bbttracker.feature.chart.ChartViewModel
import com.yv.bbttracker.feature.entry.MeasurementEntryScreen
import com.yv.bbttracker.feature.entry.MeasurementEntryViewModel
import com.yv.bbttracker.feature.entry.ObservationScreen
import com.yv.bbttracker.feature.entry.ObservationViewModel
import com.yv.bbttracker.feature.diary.DiaryScreen
import com.yv.bbttracker.feature.diary.DiaryViewModel
import com.yv.bbttracker.feature.history.HistoryScreen
import com.yv.bbttracker.feature.history.HistoryViewModel
import com.yv.bbttracker.feature.onboarding.OnboardingScreen
import com.yv.bbttracker.feature.onboarding.OnboardingViewModel
import com.yv.bbttracker.feature.settings.SettingsScreen
import com.yv.bbttracker.feature.settings.SettingsViewModel
import com.yv.bbttracker.feature.today.TodayScreen
import com.yv.bbttracker.feature.today.TodayViewModel
import com.yv.bbttracker.ui.navigation.ChartRoute
import com.yv.bbttracker.ui.navigation.DiaryRoute
import com.yv.bbttracker.ui.navigation.HistoryRoute
import com.yv.bbttracker.ui.navigation.MeasurementRoute
import com.yv.bbttracker.ui.navigation.ObservationRoute
import com.yv.bbttracker.ui.navigation.ReconfigureOnboardingRoute
import com.yv.bbttracker.ui.navigation.SettingsRoute
import com.yv.bbttracker.ui.navigation.TodayRoute
import java.time.LocalDate

@Composable
fun AppGraph(
    container: AppContainer,
    locked: Boolean,
    securityReady: Boolean,
    onUnlock: () -> Unit,
) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    when {
        !securityReady -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        locked -> AppLockedScreen(onUnlock)
        settings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        settings?.onboardingCompleted != true || settings?.acceptedDisclaimerVersion != DISCLAIMER_VERSION -> {
            val onboardingViewModel: OnboardingViewModel = viewModel(
                key = "onboarding",
                factory = OnboardingViewModel.Factory(
                    settingsRepository = container.settingsRepository,
                    reminderScheduler = container.reminderScheduler,
                    initialSettings = requireNotNull(settings),
                ),
            )
            OnboardingScreen(onboardingViewModel)
        }
        else -> MainNavigation(container, requireNotNull(settings))
    }
}

@Composable
private fun MainNavigation(
    container: AppContainer,
    settings: com.yv.bbttracker.domain.model.AppSettings,
) {
    val backStack = rememberNavBackStack(TodayRoute)
    val current = backStack.lastOrNull()
    val isTopLevel = current is TodayRoute || current is ChartRoute && current.cycleId == 0L ||
        current is DiaryRoute || current is HistoryRoute || current is SettingsRoute

    Scaffold(
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    BottomDestination(TodayRoute, current is TodayRoute, R.string.nav_today, Icons.Outlined.Today) {
                        backStack.goTopLevel(TodayRoute)
                    }
                    BottomDestination(ChartRoute(), current is ChartRoute, R.string.nav_chart, Icons.Outlined.AutoGraph) {
                        backStack.goTopLevel(ChartRoute())
                    }
                    BottomDestination(DiaryRoute, current is DiaryRoute, R.string.nav_diary, Icons.Outlined.CalendarMonth) {
                        backStack.goTopLevel(DiaryRoute)
                    }
                    BottomDestination(HistoryRoute, current is HistoryRoute, R.string.nav_history, Icons.Outlined.History) {
                        backStack.goTopLevel(HistoryRoute)
                    }
                    BottomDestination(SettingsRoute, current is SettingsRoute, R.string.nav_settings, Icons.Outlined.Settings) {
                        backStack.goTopLevel(SettingsRoute)
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize().padding(padding),
            entryProvider = { key ->
                when (key) {
                    TodayRoute -> NavEntry<NavKey>(key) {
                        val vm: TodayViewModel = viewModel(
                            key = "today",
                            factory = TodayViewModel.Factory(
                                container.cycleRepository,
                                container.measurementRepository,
                                container.observationRepository,
                                container.settingsRepository,
                            ),
                        )
                        val state by vm.state.collectAsStateWithLifecycle()
                        TodayScreen(
                            viewModel = vm,
                            onAddMeasurement = { backStack.add(MeasurementRoute(dateEpochDay = LocalDate.now().toEpochDay())) },
                            onEditMeasurement = { id -> backStack.add(MeasurementRoute(id, LocalDate.now().toEpochDay())) },
                            onObservation = {
                                backStack.add(ObservationRoute(LocalDate.now().toEpochDay(), suggestCycleStart = state.cycle == null))
                            },
                            onOpenChart = { backStack.goTopLevel(ChartRoute()) },
                        )
                    }
                    is ChartRoute -> NavEntry<NavKey>(key) {
                        val vm: ChartViewModel = viewModel(
                            key = "chart-${key.cycleId}",
                            factory = ChartViewModel.Factory(
                                key.cycleId,
                                container.cycleRepository,
                                container.measurementRepository,
                                container.observationRepository,
                                container.settingsRepository,
                            ),
                        )
                        ChartScreen(vm) { id -> backStack.add(MeasurementRoute(id, LocalDate.now().toEpochDay())) }
                    }
                    DiaryRoute -> NavEntry<NavKey>(key) {
                        val vm: DiaryViewModel = viewModel(
                            key = "diary",
                            factory = DiaryViewModel.Factory(
                                container.cycleRepository,
                                container.measurementRepository,
                                container.observationRepository,
                                container.settingsRepository,
                            ),
                        )
                        DiaryScreen(
                            viewModel = vm,
                            onEditMeasurement = { id ->
                                backStack.add(MeasurementRoute(id, LocalDate.now().toEpochDay()))
                            },
                            onAddMeasurement = { date ->
                                backStack.add(MeasurementRoute(dateEpochDay = date.toEpochDay()))
                            },
                            onEditObservation = { date ->
                                backStack.add(ObservationRoute(date.toEpochDay(), suggestCycleStart = false))
                            },
                        )
                    }
                    HistoryRoute -> NavEntry<NavKey>(key) {
                        val vm: HistoryViewModel = viewModel(
                            key = "history",
                            factory = HistoryViewModel.Factory(
                                container.cycleRepository,
                                container.measurementRepository,
                                container.observationRepository,
                                container.settingsRepository,
                            ),
                        )
                        HistoryScreen(
                            viewModel = vm,
                            onEditMeasurement = { id -> backStack.add(MeasurementRoute(id, LocalDate.now().toEpochDay())) },
                            onEditObservation = { date ->
                                backStack.add(ObservationRoute(date.toEpochDay(), suggestCycleStart = false))
                            },
                            onOpenChart = { cycleId -> backStack.add(ChartRoute(cycleId)) },
                        )
                    }
                    SettingsRoute -> NavEntry<NavKey>(key) {
                        val vm: SettingsViewModel = viewModel(
                            key = "settings",
                            factory = SettingsViewModel.Factory(
                                container.settingsRepository,
                                container.reminderScheduler,
                                container.backupManager,
                                container.documentGateway,
                            ),
                        )
                        SettingsScreen(
                            viewModel = vm,
                            onReconfigure = {
                                backStack.add(
                                    ReconfigureOnboardingRoute(System.currentTimeMillis()),
                                )
                            },
                        )
                    }
                    is ReconfigureOnboardingRoute -> NavEntry<NavKey>(key) {
                        val vm: OnboardingViewModel = viewModel(
                            key = "reconfigure-onboarding-${key.requestId}",
                            factory = OnboardingViewModel.Factory(
                                settingsRepository = container.settingsRepository,
                                reminderScheduler = container.reminderScheduler,
                                initialSettings = settings,
                                reconfigure = true,
                            ),
                        )
                        OnboardingScreen(
                            viewModel = vm,
                            onFinished = { backStack.removeLastOrNull() },
                            onDismiss = { backStack.removeLastOrNull() },
                        )
                    }
                    is MeasurementRoute -> NavEntry<NavKey>(key) {
                        val vm: MeasurementEntryViewModel = viewModel(
                            key = "measurement-${key.measurementId}-${key.dateEpochDay}",
                            factory = MeasurementEntryViewModel.Factory(
                                key.measurementId,
                                LocalDate.ofEpochDay(key.dateEpochDay),
                                container.measurementRepository,
                                container.settingsRepository,
                            ),
                        )
                        MeasurementEntryScreen(vm) { backStack.removeLastOrNull() }
                    }
                    is ObservationRoute -> NavEntry<NavKey>(key) {
                        val vm: ObservationViewModel = viewModel(
                            key = "observation-${key.dateEpochDay}",
                            factory = ObservationViewModel.Factory(
                                LocalDate.ofEpochDay(key.dateEpochDay),
                                key.suggestCycleStart,
                                container.observationRepository,
                                container.cycleRepository,
                            ),
                        )
                        ObservationScreen(vm) { backStack.removeLastOrNull() }
                    }
                    else -> NavEntry<NavKey>(key) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            },
        )
    }
}

@Composable
private fun RowScope.BottomDestination(
    route: NavKey,
    selected: Boolean,
    label: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(label)) },
    )
}

private fun androidx.navigation3.runtime.NavBackStack<NavKey>.goTopLevel(route: NavKey) {
    clear()
    add(route)
}

@Composable
private fun AppLockedScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.unlock_subtitle), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onUnlock, modifier = Modifier.padding(top = 20.dp)) {
            Text(stringResource(R.string.unlock_action))
        }
    }
}
