package com.yv.bbttracker.feature.entry

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yv.bbttracker.R
import com.yv.bbttracker.domain.model.DisturbanceFlag
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.ui.components.ExpandableFormSection
import com.yv.bbttracker.ui.components.FormSection
import com.yv.bbttracker.ui.formatting.Formatters
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementEntryScreen(
    viewModel: MeasurementEntryViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var detailsExpanded by remember(state.id, state.isLoading) {
        mutableStateOf(
            !state.isLoading && (
                state.isEditing ||
                    state.sleepHoursText.isNotBlank() ||
                    state.sleepMinutesText.isNotBlank() ||
                    state.disturbanceMask != 0L ||
                    state.disturbanceNote.isNotBlank() ||
                    state.note.isNotBlank() ||
                    !state.selectedForAnalysis
                ),
        )
    }
    val temperatureFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { onBack() }
    }
    LaunchedEffect(state.isLoading, state.isEditing) {
        if (!state.isLoading && !state.isEditing) {
            temperatureFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.measurement_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = { viewModel.onEvent(MeasurementEntryEvent.Save) },
                    enabled = !state.isSaving && !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.save_measurement))
                    }
                }
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f).height(54.dp)) {
                    Text(Formatters.date(state.date))
                }
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f).height(54.dp)) {
                    Text("%02d:%02d".format(state.time.hour, state.time.minute))
                }
            }

            FormSection(
                title = stringResource(R.string.temperature_entry_title),
                supporting = stringResource(R.string.bbt_measurement_guidance),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = state.temperatureText,
                        onValueChange = { viewModel.onEvent(MeasurementEntryEvent.TemperatureChanged(it)) },
                        label = { Text(stringResource(R.string.temperature)) },
                        placeholder = { Text(stringResource(R.string.temperature_hint)) },
                        suffix = { Text("°C") },
                        supportingText = { Text(stringResource(R.string.temperature_helper)) },
                        isError = state.error == EntryError.INVALID_TEMPERATURE,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.onEvent(MeasurementEntryEvent.Save) },
                        ),
                        textStyle = MaterialTheme.typography.titleLarge,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(temperatureFocusRequester),
                    )
                }
                if (state.error != null) {
                    Text(
                        text = stringResource(
                            when (state.error) {
                                EntryError.INVALID_TEMPERATURE -> R.string.temperature_invalid
                                EntryError.FUTURE_TIME -> R.string.future_time_invalid
                                EntryError.SAVE_FAILED -> R.string.error_database
                                null -> R.string.error_unknown
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            FormSection(
                title = stringResource(R.string.measurement_conditions_title),
                supporting = stringResource(R.string.measurement_conditions_supporting),
            ) {
                Text(stringResource(R.string.measurement_site), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SiteChip(MeasurementSite.ORAL, R.string.site_oral, state, viewModel::onEvent)
                    SiteChip(MeasurementSite.VAGINAL, R.string.site_vaginal, state, viewModel::onEvent)
                    SiteChip(MeasurementSite.RECTAL, R.string.site_rectal, state, viewModel::onEvent)
                }
                SwitchRow(
                    text = stringResource(R.string.measured_after_waking),
                    supporting = stringResource(R.string.measured_after_waking_supporting),
                    checked = state.measuredImmediatelyAfterWaking,
                    onCheckedChange = { viewModel.onEvent(MeasurementEntryEvent.ImmediatelyAfterWakingChanged(it)) },
                )
            }

            ExpandableFormSection(
                title = stringResource(R.string.measurement_more_details),
                summary = stringResource(R.string.measurement_more_details_summary),
                expanded = detailsExpanded,
                onExpandedChange = { detailsExpanded = it },
                expandContentDescription = stringResource(R.string.expand),
                collapseContentDescription = stringResource(R.string.collapse),
            ) {
                Text(stringResource(R.string.sleep_duration), style = MaterialTheme.typography.labelLarge)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.sleepHoursText,
                            onValueChange = { viewModel.onEvent(MeasurementEntryEvent.SleepHoursChanged(it)) },
                            label = { Text(stringResource(R.string.sleep_hours)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.sleepMinutesText,
                            onValueChange = { viewModel.onEvent(MeasurementEntryEvent.SleepMinutesChanged(it)) },
                            label = { Text(stringResource(R.string.sleep_minutes)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text(stringResource(R.string.disturbances), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    disturbanceOptions().forEach { (flag, label) ->
                        FilterChip(
                            selected = state.disturbanceMask and flag != 0L,
                            onClick = { viewModel.onEvent(MeasurementEntryEvent.DisturbanceToggled(flag)) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                if (state.disturbanceMask and DisturbanceFlag.MAJOR_EXCLUSION_RECOMMENDATION != 0L) {
                    Text(
                        stringResource(R.string.exclusion_recommended),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (state.disturbanceMask != 0L || state.disturbanceNote.isNotBlank()) {
                    OutlinedTextField(
                        value = state.disturbanceNote,
                        onValueChange = { viewModel.onEvent(MeasurementEntryEvent.DisturbanceNoteChanged(it)) },
                        label = { Text(stringResource(R.string.disturbance_note)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
                SwitchRow(
                    text = stringResource(R.string.use_for_analysis),
                    supporting = stringResource(R.string.use_for_analysis_supporting),
                    checked = state.selectedForAnalysis,
                    onCheckedChange = { viewModel.onEvent(MeasurementEntryEvent.SelectedForAnalysisChanged(it)) },
                )
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.onEvent(MeasurementEntryEvent.NoteChanged(it)) },
                    label = { Text(stringResource(R.string.notes_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            if (state.isEditing) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                TextButton(
                    onClick = { viewModel.onEvent(MeasurementEntryEvent.RequestDelete) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.measurement_delete_action), color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        viewModel.onEvent(MeasurementEntryEvent.DateChanged(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(dateState) }
    }
    if (showTimePicker) {
        val timeState = rememberTimePickerState(state.time.hour, state.time.minute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.time)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(MeasurementEntryEvent.TimeChanged(java.time.LocalTime.of(timeState.hour, timeState.minute)))
                    showTimePicker = false
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { TimeInput(timeState) }
    }
    state.warning?.let { warning ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(MeasurementEntryEvent.DismissWarning) },
            title = { Text(stringResource(R.string.measurement_title)) },
            text = {
                Text(
                    stringResource(
                        when (warning) {
                            EntryWarning.PAST_DATE -> R.string.past_date_warning
                            EntryWarning.SOFT_TEMPERATURE -> R.string.temperature_soft_warning
                            EntryWarning.DUPLICATE -> R.string.duplicate_measurement
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(MeasurementEntryEvent.ConfirmWarning) }) {
                    Text(stringResource(R.string.save_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(MeasurementEntryEvent.DismissWarning) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(MeasurementEntryEvent.DismissDelete) },
            title = { Text(stringResource(R.string.delete_measurement_title)) },
            text = { Text(stringResource(R.string.delete_measurement_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(MeasurementEntryEvent.ConfirmDelete) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(MeasurementEntryEvent.DismissDelete) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SiteChip(
    site: MeasurementSite,
    @StringRes label: Int,
    state: MeasurementEntryUiState,
    onEvent: (MeasurementEntryEvent) -> Unit,
) {
    FilterChip(
        selected = state.site == site,
        onClick = { onEvent(MeasurementEntryEvent.SiteChanged(site)) },
        label = { Text(stringResource(label)) },
    )
}

@Composable
private fun SwitchRow(
    text: String,
    supporting: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun disturbanceOptions(): List<Pair<Long, Int>> = listOf(
    DisturbanceFlag.ILLNESS_OR_FEVER to R.string.disturbance_fever,
    DisturbanceFlag.SHORT_SLEEP to R.string.disturbance_short_sleep,
    DisturbanceFlag.INTERRUPTED_SLEEP to R.string.disturbance_interrupted_sleep,
    DisturbanceFlag.LATE_MEASUREMENT to R.string.disturbance_late,
    DisturbanceFlag.ALCOHOL to R.string.disturbance_alcohol,
    DisturbanceFlag.MEDICATION to R.string.disturbance_medication,
    DisturbanceFlag.TRAVEL_OR_TIMEZONE to R.string.disturbance_travel,
    DisturbanceFlag.DIFFERENT_MEASUREMENT_SITE to R.string.disturbance_site_changed,
    DisturbanceFlag.NOT_IMMEDIATELY_AFTER_WAKING to R.string.disturbance_not_immediate,
    DisturbanceFlag.OTHER to R.string.disturbance_other,
)
