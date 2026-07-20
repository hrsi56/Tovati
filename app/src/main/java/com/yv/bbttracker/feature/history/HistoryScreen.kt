package com.yv.bbttracker.feature.history

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yv.bbttracker.R
import com.yv.bbttracker.domain.engine.ForecastReliability
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MoodFlag
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.OvulationPain
import com.yv.bbttracker.domain.model.PhysicalSymptomFlag
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.ui.components.ExpandableFormSection
import com.yv.bbttracker.ui.formatting.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal const val HISTORY_LIST_TEST_TAG = "history-list"

private data class PendingCycleStartEdit(
    val item: CycleHistoryItem,
    val newStartDate: LocalDate,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onEditMeasurement: (Long) -> Unit,
    onEditObservation: (LocalDate) -> Unit,
    onOpenChart: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showObservationDatePicker by remember { mutableStateOf(false) }
    var cycleToPickStartFor by remember { mutableStateOf<CycleHistoryItem?>(null) }
    var pendingCycleStartEdit by remember { mutableStateOf<PendingCycleStartEdit?>(null) }
    var cycleToDelete by remember { mutableStateOf<CycleHistoryItem?>(null) }
    var observationsExpanded by remember { mutableStateOf(false) }
    var measurementsExpanded by remember { mutableStateOf(false) }
    var backtestExpanded by remember { mutableStateOf(false) }

    val message = state.message
    val messageText = when (message) {
        HistoryMessage.CYCLE_START_UPDATED -> stringResource(R.string.history_cycle_start_updated)
        HistoryMessage.CYCLE_DELETED -> stringResource(R.string.history_cycle_deleted)
        HistoryMessage.CYCLE_UPDATE_FAILED -> stringResource(R.string.history_cycle_update_failed)
        HistoryMessage.CYCLE_DELETE_FAILED -> stringResource(R.string.history_cycle_delete_failed)
        null -> null
    }
    LaunchedEffect(message, messageText) {
        if (message != null && messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage(message)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .testTag(HISTORY_LIST_TEST_TAG),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(
                key = HistoryListKeys.HEADER,
                contentType = "history_header",
            ) {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showObservationDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.history_add_or_edit_signs))
                }
            }

            if (!state.isLoading && state.cycles.isEmpty()) {
                item(
                    key = HistoryListKeys.EMPTY,
                    contentType = "history_empty",
                ) { EmptyHistory() }
            } else {
                state.backtest?.takeIf { it.anchoredCycleCount > 0 }?.let { backtest ->
                    item(
                        key = HistoryListKeys.BACKTEST,
                        contentType = "history_backtest",
                    ) {
                        BacktestSummaryCard(
                            backtest = backtest,
                            expanded = backtestExpanded,
                            onExpandedChange = { backtestExpanded = it },
                        )
                    }
                }
                items(
                    items = state.cycles,
                    key = { HistoryListKeys.cycle(it.cycle.id) },
                    contentType = { "history_cycle" },
                ) { item ->
                    CycleCard(
                        item = item,
                        backtestResult = state.backtestByCycleId[item.cycle.id],
                        actionsEnabled = !state.isCycleMutationInProgress,
                        onOpenChart = { onOpenChart(item.cycle.id) },
                        onEditStart = { cycleToPickStartFor = item },
                        onDelete = { cycleToDelete = item },
                    )
                }
            }

            item(
                key = HistoryListKeys.SIGNS_TOGGLE,
                contentType = "history_section_toggle",
            ) {
                Spacer(Modifier.height(6.dp))
                HistorySectionToggle(
                    icon = Icons.Outlined.CalendarMonth,
                    title = stringResource(R.string.history_signs_section, state.observations.size),
                    summary = stringResource(R.string.history_signs_section_summary),
                    expanded = observationsExpanded,
                    onClick = { observationsExpanded = !observationsExpanded },
                )
            }
            if (observationsExpanded && !state.isLoading && state.observations.isEmpty()) {
                item(
                    key = HistoryListKeys.SIGNS_EMPTY,
                    contentType = "history_empty_message",
                ) {
                    Text(stringResource(R.string.history_daily_signs_empty), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (observationsExpanded) {
                items(
                    items = state.observations,
                    key = { HistoryListKeys.observation(it.epochDay) },
                    contentType = { "history_observation" },
                ) { observation ->
                    ObservationCard(observation = observation, onClick = { onEditObservation(observation.date) })
                }
            }

            item(
                key = HistoryListKeys.MEASUREMENTS_TOGGLE,
                contentType = "history_section_toggle",
            ) {
                HistorySectionToggle(
                    icon = Icons.Outlined.Thermostat,
                    title = stringResource(R.string.history_measurements_section, state.measurements.size),
                    summary = stringResource(R.string.history_measurements_section_summary),
                    expanded = measurementsExpanded,
                    onClick = { measurementsExpanded = !measurementsExpanded },
                )
            }
            if (measurementsExpanded && !state.isLoading && state.measurements.isEmpty()) {
                item(
                    key = HistoryListKeys.MEASUREMENTS_EMPTY,
                    contentType = "history_empty_message",
                ) {
                    Text(stringResource(R.string.daily_records_empty), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (measurementsExpanded) {
                items(
                    items = state.measurements,
                    key = { HistoryListKeys.measurement(it.id) },
                    contentType = { "history_measurement" },
                ) { measurement ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onEditMeasurement(measurement.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(Icons.Outlined.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(Formatters.date(measurement.date), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(
                                        if (measurement.selectedForAnalysis) {
                                            R.string.analysis_included
                                        } else {
                                            R.string.analysis_excluded
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                stringResource(R.string.temperature_value, Formatters.temperature(measurement.temperatureCentiC)),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }
            }
            item(
                key = HistoryListKeys.BOTTOM_SPACER,
                contentType = "history_bottom_spacer",
            ) { Spacer(Modifier.height(84.dp)) }
        }
    }

    if (showObservationDatePicker) {
        PastOrTodayDatePickerDialog(
            initialDate = LocalDate.now(),
            onDismiss = { showObservationDatePicker = false },
            onDateSelected = { date ->
                showObservationDatePicker = false
                onEditObservation(date)
            },
        )
    }

    cycleToPickStartFor?.let { item ->
        PastOrTodayDatePickerDialog(
            initialDate = item.cycle.startDate,
            onDismiss = { cycleToPickStartFor = null },
            onDateSelected = { date ->
                cycleToPickStartFor = null
                if (date != item.cycle.startDate) {
                    pendingCycleStartEdit = PendingCycleStartEdit(item, date)
                }
            },
        )
    }

    pendingCycleStartEdit?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingCycleStartEdit = null },
            title = { Text(stringResource(R.string.history_change_cycle_start_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.history_change_cycle_start_confirm_body,
                        Formatters.date(pending.item.cycle.startDate),
                        Formatters.date(pending.newStartDate),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isCycleMutationInProgress,
                    onClick = {
                        viewModel.updateCycleStart(pending.item.cycle.id, pending.newStartDate)
                        pendingCycleStartEdit = null
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCycleStartEdit = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    cycleToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { cycleToDelete = null },
            title = { Text(stringResource(R.string.history_delete_cycle_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.history_delete_cycle_confirm_body,
                        Formatters.date(item.cycle.startDate),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isCycleMutationInProgress,
                    onClick = {
                        viewModel.deleteCycle(item.cycle.id)
                        cycleToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { cycleToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun HistorySectionToggle(
    icon: ImageVector,
    title: String,
    summary: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyHistory() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.history_empty_body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun BacktestSummaryCard(
    backtest: com.yv.bbttracker.domain.engine.BacktestSummary,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    ExpandableFormSection(
        title = stringResource(R.string.backtest_title),
        summary = stringResource(
            R.string.backtest_summary_hits,
            backtest.anchoredPeriodHitCount,
            backtest.anchoredCycleCount,
        ),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        expandContentDescription = stringResource(R.string.expand),
        collapseContentDescription = stringResource(R.string.collapse),
    ) {
        backtest.medianAbsErrorDaysAtAnchor?.let { medianError ->
            Text(
                stringResource(R.string.backtest_summary_median_error, Formatters.decimal(medianError)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        backtest.medianLeadDaysAtAnchor?.let { medianLead ->
            Text(
                stringResource(R.string.backtest_summary_lead, Formatters.decimal(medianLead)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(R.string.backtest_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CycleCard(
    item: CycleHistoryItem,
    backtestResult: com.yv.bbttracker.domain.engine.CycleBacktestResult?,
    actionsEnabled: Boolean,
    onOpenChart: () -> Unit,
    onEditStart: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenChart),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        stringResource(R.string.cycle_started_on, Formatters.date(item.cycle.startDate)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        item.lengthDays?.let { stringResource(R.string.cycle_length, it) }
                            ?: stringResource(R.string.cycle_open),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.valid_measurement_count, item.includedMeasurements),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.data_completeness, "${item.completenessPercent}%"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val estimatedStart = item.retrospectiveOvulationStartDate
                    val estimatedEnd = item.retrospectiveOvulationEndDate
                    if (estimatedStart != null && estimatedEnd != null) {
                        Text(
                            stringResource(
                                R.string.history_confirmed_thermal_estimate,
                                Formatters.dateRange(estimatedStart, estimatedEnd),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        item.lutealPhaseDays?.let { lutealDays ->
                            Text(
                                stringResource(R.string.history_luteal_length, lutealDays),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            stringResource(
                                R.string.history_estimate_support,
                                stringResource(historyReliabilityResource(item.estimateReliability)),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.short_safety_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    backtestResult?.let { result ->
                        val errorDays = result.periodErrorDaysAtAnchor
                        val leadDays = result.leadDaysAtAnchor
                        when {
                            result.periodHitAtAnchor == true && leadDays != null -> Text(
                                stringResource(R.string.backtest_cycle_hit, leadDays),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            errorDays != null -> Text(
                                stringResource(R.string.backtest_cycle_miss, kotlin.math.abs(errorDays)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> Unit
                        }
                    }
                }
                Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.open_cycle_chart))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(enabled = actionsEnabled, onClick = onEditStart) {
                    Text(stringResource(R.string.edit))
                }
                TextButton(enabled = actionsEnabled, onClick = onDelete) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ObservationCard(observation: DailyObservation, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(Formatters.date(observation.date), style = MaterialTheme.typography.titleMedium)
                ObservationSummary(observation)
            }
            Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.edit))
        }
    }
}

@Composable
private fun ObservationSummary(observation: DailyObservation) {
    val moodLabels = mutableListOf<String>()
    for (label in moodLabelResources(observation.moodMask)) {
        moodLabels += stringResource(label)
    }
    val symptomLabels = mutableListOf<String>()
    for (label in symptomLabelResources(observation.physicalSymptomMask)) {
        symptomLabels += stringResource(label)
    }

    val lines = mutableListOf<String>()
    if (observation.bleeding != BleedingLevel.NOT_RECORDED) {
        lines += stringResource(
            R.string.history_sign_value,
            stringResource(R.string.bleeding),
            stringResource(observation.bleeding.labelRes()),
        )
    }
    if (observation.mucus != CervicalMucus.NOT_CHECKED) {
        lines += stringResource(
            R.string.history_sign_value,
            stringResource(R.string.mucus),
            stringResource(observation.mucus.labelRes()),
        )
    }
    if (observation.mucusSensation != MucusSensation.NOT_CHECKED) {
        lines += stringResource(
            R.string.history_sign_value,
            stringResource(R.string.mucus_sensation),
            stringResource(observation.mucusSensation.labelRes()),
        )
    }
    if (observation.lhResult != LhResult.NOT_TESTED) {
        lines += stringResource(
            R.string.history_sign_value,
            stringResource(R.string.lh_result),
            stringResource(observation.lhResult.labelRes()),
        )
    }
    if (moodLabels.isNotEmpty()) {
        lines += stringResource(
            R.string.history_sign_value,
            stringResource(R.string.mood_title),
            moodLabels.joinToString(" · "),
        )
    }
    if (observation.libidoLevel != LibidoLevel.NOT_RECORDED) {
        lines += stringResource(
            R.string.history_sign_value,
            stringResource(R.string.libido_title),
            stringResource(observation.libidoLevel.labelRes()),
        )
    }
    if (observation.sexualContact != SexualContact.NOT_RECORDED) {
        lines += stringResource(
            R.string.history_sign_value,
            stringResource(R.string.sexual_contact_title),
            stringResource(observation.sexualContact.labelRes()),
        )
    }
    observation.sexualContactInitiatedByUser?.let { initiated ->
        lines += stringResource(
            R.string.history_sign_value,
            stringResource(R.string.sexual_contact_initiated_title),
            stringResource(
                if (initiated) R.string.initiated_by_me_yes else R.string.initiated_by_me_no,
            ),
        )
    }
    if (symptomLabels.isNotEmpty()) {
        lines += stringResource(
            R.string.history_sign_value,
            stringResource(R.string.physical_symptoms_title),
            symptomLabels.joinToString(" · "),
        )
    }
    observation.painReliefPillCount?.let { count ->
        lines += stringResource(
            R.string.history_sign_value,
            stringResource(R.string.pain_relief_title),
            stringResource(R.string.pain_relief_pill_count_value, count),
        )
    }
    if (observation.mucusObscured) {
        lines += stringResource(
            R.string.history_sign_value,
            stringResource(R.string.mucus_obscured),
            stringResource(R.string.yes),
        )
    }
    if (observation.ovulationPain != OvulationPain.NOT_RECORDED) {
        lines += stringResource(
            R.string.history_sign_value,
            stringResource(R.string.ovulation_pain),
            stringResource(observation.ovulationPain.labelRes()),
        )
    }

    if (lines.isEmpty() && !observation.isExplicitCycleStart &&
        observation.note.isNullOrBlank() && observation.moodNote.isNullOrBlank()
    ) {
        Text(
            stringResource(R.string.not_recorded),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        for (line in lines.take(3)) {
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (lines.size > 3) {
            Text(
                stringResource(R.string.history_more_values, lines.size - 3),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (observation.isExplicitCycleStart) {
            Text(
                stringResource(R.string.cycle_started),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        observation.moodNote?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        observation.painReliefMedicationNote?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        observation.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastOrTodayDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    key(initialDate) {
        val todayUtcMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val initialUtcMillis = initialDate
            .coerceAtMost(LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialUtcMillis,
            selectableDates = remember(todayUtcMillis) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let { selectedMillis ->
                            onDateSelected(
                                Instant.ofEpochMilli(selectedMillis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate(),
                            )
                        }
                    },
                ) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@StringRes
private fun BleedingLevel.labelRes(): Int = when (this) {
    BleedingLevel.NOT_RECORDED -> R.string.not_recorded
    BleedingLevel.NONE -> R.string.bleeding_none
    BleedingLevel.SPOTTING -> R.string.bleeding_spotting
    BleedingLevel.LIGHT -> R.string.bleeding_light
    BleedingLevel.MEDIUM -> R.string.bleeding_medium
    BleedingLevel.HEAVY -> R.string.bleeding_heavy
}

@StringRes
private fun CervicalMucus.labelRes(): Int = when (this) {
    CervicalMucus.NOT_CHECKED -> R.string.mucus_not_checked
    CervicalMucus.DRY -> R.string.mucus_dry
    CervicalMucus.STICKY -> R.string.mucus_sticky
    CervicalMucus.CREAMY -> R.string.mucus_creamy
    CervicalMucus.WATERY -> R.string.mucus_watery
    CervicalMucus.EGG_WHITE -> R.string.mucus_egg_white
}

@StringRes
private fun MucusSensation.labelRes(): Int = when (this) {
    MucusSensation.NOT_CHECKED -> R.string.mucus_sensation_not_checked
    MucusSensation.DRY -> R.string.mucus_sensation_dry
    MucusSensation.DAMP -> R.string.mucus_sensation_damp
    MucusSensation.WET -> R.string.mucus_sensation_wet
    MucusSensation.SLIPPERY -> R.string.mucus_sensation_slippery
}

@StringRes
private fun LhResult.labelRes(): Int = when (this) {
    LhResult.NOT_TESTED -> R.string.lh_not_tested
    LhResult.NEGATIVE -> R.string.lh_negative
    LhResult.BORDERLINE -> R.string.lh_borderline
    LhResult.POSITIVE -> R.string.lh_positive
    LhResult.INVALID -> R.string.lh_invalid
}

@StringRes
private fun OvulationPain.labelRes(): Int = when (this) {
    OvulationPain.NOT_RECORDED -> R.string.not_recorded
    OvulationPain.NONE -> R.string.pain_none
    OvulationPain.LEFT -> R.string.pain_left
    OvulationPain.RIGHT -> R.string.pain_right
    OvulationPain.UNCLEAR -> R.string.pain_unclear
}

@StringRes
private fun LibidoLevel.labelRes(): Int = when (this) {
    LibidoLevel.NOT_RECORDED -> R.string.not_recorded
    LibidoLevel.VERY_LOW -> R.string.libido_very_low
    LibidoLevel.LOW -> R.string.libido_low
    LibidoLevel.MEDIUM -> R.string.libido_medium
    LibidoLevel.HIGH -> R.string.libido_high
    LibidoLevel.VERY_HIGH -> R.string.libido_very_high
    LibidoLevel.MASTURBATED -> R.string.libido_masturbated
}

@StringRes
private fun SexualContact.labelRes(): Int = when (this) {
    SexualContact.NOT_RECORDED -> R.string.not_recorded
    SexualContact.NONE -> R.string.sexual_contact_none
    SexualContact.SOME -> R.string.sexual_contact_some
    SexualContact.YES -> R.string.sexual_contact_yes
}

private fun moodLabelResources(mask: Long): List<Int> = buildList {
    if (mask and MoodFlag.CALM != 0L) add(R.string.mood_calm)
    if (mask and MoodFlag.HAPPY != 0L) add(R.string.mood_happy)
    if (mask and MoodFlag.ENERGETIC != 0L) add(R.string.mood_energetic)
    if (mask and MoodFlag.SENSITIVE != 0L) add(R.string.mood_sensitive)
    if (mask and MoodFlag.IRRITABLE != 0L) add(R.string.mood_irritable)
    if (mask and MoodFlag.ANXIOUS != 0L) add(R.string.mood_anxious)
    if (mask and MoodFlag.SAD != 0L) add(R.string.mood_sad)
    if (mask and MoodFlag.LOW != 0L) add(R.string.mood_low)
}

private fun symptomLabelResources(mask: Long): List<Int> = buildList {
    if (mask and PhysicalSymptomFlag.ABDOMINAL_PAIN != 0L) add(R.string.symptom_abdominal_pain)
    if (mask and PhysicalSymptomFlag.HEADACHE != 0L) add(R.string.symptom_headache)
    if (mask and PhysicalSymptomFlag.ACNE != 0L) add(R.string.symptom_acne)
    if (mask and PhysicalSymptomFlag.BLOATING != 0L) add(R.string.symptom_bloating)
    if (mask and PhysicalSymptomFlag.BACK_PAIN != 0L) add(R.string.symptom_back_pain)
    if (mask and PhysicalSymptomFlag.BREAST_TENDERNESS != 0L) add(R.string.symptom_breast_tenderness)
    if (mask and PhysicalSymptomFlag.FATIGUE != 0L) add(R.string.symptom_fatigue)
    if (mask and PhysicalSymptomFlag.NAUSEA != 0L) add(R.string.symptom_nausea)
    if (mask and PhysicalSymptomFlag.PELVIC_PRESSURE != 0L) add(R.string.symptom_pelvic_pressure)
    if (mask and PhysicalSymptomFlag.SLEEP_CHANGES != 0L) add(R.string.symptom_sleep_changes)
    if (mask and PhysicalSymptomFlag.OTHER != 0L) add(R.string.symptom_other)
}

private fun historyReliabilityResource(reliability: ForecastReliability): Int = when (reliability) {
    ForecastReliability.INSUFFICIENT -> R.string.reliability_value_insufficient
    ForecastReliability.LIMITED -> R.string.reliability_value_limited
    ForecastReliability.MODERATE -> R.string.reliability_value_moderate
    ForecastReliability.STRONG -> R.string.reliability_value_strong
}
