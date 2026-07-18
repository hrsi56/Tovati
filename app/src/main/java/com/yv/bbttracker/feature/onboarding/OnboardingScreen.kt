package com.yv.bbttracker.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yv.bbttracker.R
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.TrackingGoal

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onEvent(OnboardingEvent.ReminderChanged(granted)) }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.canGoBack) {
                    OutlinedButton(
                        onClick = { viewModel.onEvent(OnboardingEvent.Back) },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(stringResource(R.string.back)) }
                }
                Button(
                    onClick = { viewModel.onEvent(OnboardingEvent.Next) },
                    enabled = state.canContinue && !state.isSaving,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(stringResource(if (state.isLastPage) R.string.finish_setup else R.string.continue_label))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_progress, state.page + 1, OnboardingUiState.PAGE_COUNT),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(32.dp))
            when (state.page) {
                0 -> IntroPage(
                    Icons.Outlined.FavoriteBorder,
                    R.string.onboarding_welcome_title,
                    R.string.onboarding_welcome_body,
                )
                1 -> IntroPage(
                    Icons.Outlined.HealthAndSafety,
                    R.string.onboarding_safety_title,
                    R.string.onboarding_safety_body,
                )
                2 -> IntroPage(
                    Icons.Outlined.Lock,
                    R.string.onboarding_privacy_title,
                    R.string.onboarding_privacy_body,
                    supportingText = R.string.offline_promise,
                )
                3 -> ChoicePage(
                    icon = Icons.Outlined.TrackChanges,
                    title = R.string.onboarding_goal_title,
                    options = listOf(
                        Triple(TrackingGoal.CYCLE_AWARENESS, R.string.goal_track, state.trackingGoal == TrackingGoal.CYCLE_AWARENESS),
                        Triple(TrackingGoal.TRYING_TO_CONCEIVE, R.string.goal_conceive, state.trackingGoal == TrackingGoal.TRYING_TO_CONCEIVE),
                    ),
                    onSelected = { viewModel.onEvent(OnboardingEvent.GoalChanged(it)) },
                )
                4 -> MeasurementSitePage(state, viewModel::onEvent)
                5 -> ReminderPage(
                    state = state,
                    onToggle = { enabled ->
                        if (!enabled) viewModel.onEvent(OnboardingEvent.ReminderChanged(false))
                        else if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else viewModel.onEvent(OnboardingEvent.ReminderChanged(true))
                    },
                    onTimeChanged = { h, m -> viewModel.onEvent(OnboardingEvent.ReminderTimeChanged(h, m)) },
                )
                6 -> DisclaimerPage(state.disclaimerAccepted) {
                    viewModel.onEvent(OnboardingEvent.DisclaimerChanged(it))
                }
            }
        }
    }
}

@Composable
private fun IntroPage(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes supportingText: Int? = null,
) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(24.dp))
    Text(stringResource(title), style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(18.dp))
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
    supportingText?.let {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(it), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun <T> ChoicePage(
    icon: ImageVector,
    @StringRes title: Int,
    options: List<Triple<T, Int, Boolean>>,
    onSelected: (T) -> Unit,
) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
    Spacer(Modifier.height(20.dp))
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(24.dp))
    options.forEachIndexed { index, (value, label, selected) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelected(value) })
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
        }
        if (index < options.lastIndex) HorizontalDivider()
    }
}

@Composable
private fun MeasurementSitePage(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Icon(Icons.Outlined.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.onboarding_site_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.onboarding_site_body), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(20.dp))
    val options = listOf(
        MeasurementSite.ORAL to R.string.site_oral,
        MeasurementSite.VAGINAL to R.string.site_vaginal,
        MeasurementSite.RECTAL to R.string.site_rectal,
    )
    options.forEach { (site, label) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(state.measurementSite == site, role = Role.RadioButton) {
                    onEvent(OnboardingEvent.SiteChanged(site))
                }
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = state.measurementSite == site, onClick = null)
            Text(stringResource(label), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderPage(
    state: OnboardingUiState,
    onToggle: (Boolean) -> Unit,
    onTimeChanged: (Int, Int) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.onboarding_reminder_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.onboarding_reminder_body), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.enable_reminder), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = state.reminderEnabled, onCheckedChange = onToggle)
    }
    if (state.reminderEnabled) {
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.reminder_time) + "  %02d:%02d".format(state.reminderHour, state.reminderMinute))
        }
    }
    if (showTimePicker) {
        val pickerState = rememberTimePickerState(state.reminderHour, state.reminderMinute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.reminder_time)) },
            confirmButton = {
                Button(onClick = {
                    onTimeChanged(pickerState.hour, pickerState.minute)
                    showTimePicker = false
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                IconButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) { TimeInput(pickerState) }
    }
}

@Composable
private fun DisclaimerPage(accepted: Boolean, onAcceptedChange: (Boolean) -> Unit) {
    Icon(Icons.AutoMirrored.Outlined.FactCheck, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.onboarding_disclaimer_title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.disclaimer_text), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(28.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(accepted, role = Role.Checkbox) { onAcceptedChange(!accepted) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = accepted, onCheckedChange = null)
        Text(stringResource(R.string.accept_disclaimer), modifier = Modifier.padding(start = 12.dp, top = 10.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
