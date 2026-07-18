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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yv.bbttracker.R
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MoodFlag
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.OvulationPain
import com.yv.bbttracker.domain.model.PhysicalSymptomFlag
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.ui.components.ExpandableFormSection
import com.yv.bbttracker.ui.components.FormSection
import com.yv.bbttracker.ui.formatting.Formatters
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationScreen(viewModel: ObservationViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showLhTimePicker by remember { mutableStateOf(false) }
    var lhDetailsExpanded by remember(state.isLoading, state.date) {
        mutableStateOf(
            !state.isLoading && (
                state.lhTestMinutesOfDay != null ||
                    state.lhTestBrand.isNotBlank() ||
                    state.lhTestSensitivityText.isNotBlank()
                ),
        )
    }
    var moreDetailsExpanded by remember(state.isLoading, state.date) {
        mutableStateOf(
            !state.isLoading && (
                state.mucusObscured ||
                    state.ovulationPain != OvulationPain.NOT_RECORDED ||
                    state.note.isNotBlank()
                ),
        )
    }
    LaunchedEffect(viewModel) { viewModel.effects.collectLatest { onBack() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.observation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { viewModel.onEvent(ObservationEvent.Save) },
                enabled = !state.isSaving && !state.isLoading,
                modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.save_observation))
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
            Text(Formatters.longDate(state.date), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            FormSection(
                title = stringResource(R.string.bleeding),
                supporting = stringResource(R.string.bleeding_supporting),
            ) {
                EnumChips(
                    options = listOf(
                        BleedingLevel.NOT_RECORDED to R.string.not_recorded,
                        BleedingLevel.NONE to R.string.bleeding_none,
                        BleedingLevel.SPOTTING to R.string.bleeding_spotting,
                        BleedingLevel.LIGHT to R.string.bleeding_light,
                        BleedingLevel.MEDIUM to R.string.bleeding_medium,
                        BleedingLevel.HEAVY to R.string.bleeding_heavy,
                    ),
                    selected = state.bleeding,
                    onSelected = { viewModel.onEvent(ObservationEvent.BleedingChanged(it)) },
                )
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = state.isExplicitCycleStart,
                        enabled = !state.isExistingCycleStart,
                        onCheckedChange = { viewModel.onEvent(ObservationEvent.CycleStartChanged(it)) },
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp, top = 8.dp)) {
                        Text(stringResource(R.string.explicit_cycle_start), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.cycle_start_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.cycleStartRequiresBleeding) {
                    Text(
                        stringResource(R.string.cycle_start_requires_bleeding),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.isExistingCycleStart) {
                    Text(
                        stringResource(R.string.cycle_start_edit_history),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FormSection(
                title = stringResource(R.string.mucus),
                supporting = stringResource(R.string.mucus_tracking_guidance),
            ) {
                Text(stringResource(R.string.mucus_appearance), style = MaterialTheme.typography.labelLarge)
                EnumChips(
                    options = listOf(
                        CervicalMucus.NOT_CHECKED to R.string.mucus_not_checked,
                        CervicalMucus.DRY to R.string.mucus_dry,
                        CervicalMucus.STICKY to R.string.mucus_sticky,
                        CervicalMucus.CREAMY to R.string.mucus_creamy,
                        CervicalMucus.WATERY to R.string.mucus_watery,
                        CervicalMucus.EGG_WHITE to R.string.mucus_egg_white,
                    ),
                    selected = state.mucus,
                    onSelected = { viewModel.onEvent(ObservationEvent.MucusChanged(it)) },
                )
                Text(stringResource(R.string.mucus_sensation), style = MaterialTheme.typography.labelLarge)
                EnumChips(
                    options = listOf(
                        MucusSensation.NOT_CHECKED to R.string.mucus_sensation_not_checked,
                        MucusSensation.DRY to R.string.mucus_sensation_dry,
                        MucusSensation.DAMP to R.string.mucus_sensation_damp,
                        MucusSensation.WET to R.string.mucus_sensation_wet,
                        MucusSensation.SLIPPERY to R.string.mucus_sensation_slippery,
                    ),
                    selected = state.mucusSensation,
                    onSelected = { viewModel.onEvent(ObservationEvent.MucusSensationChanged(it)) },
                )
            }

            FormSection(
                title = stringResource(R.string.lh_result),
                supporting = stringResource(R.string.lh_test_guidance),
            ) {
                EnumChips(
                    options = listOf(
                        LhResult.NOT_TESTED to R.string.lh_not_tested,
                        LhResult.NEGATIVE to R.string.lh_negative,
                        LhResult.BORDERLINE to R.string.lh_borderline,
                        LhResult.POSITIVE to R.string.lh_positive,
                        LhResult.INVALID to R.string.lh_invalid,
                    ),
                    selected = state.lhResult,
                    onSelected = { viewModel.onEvent(ObservationEvent.LhChanged(it)) },
                )
            }
            if (state.lhResult != LhResult.NOT_TESTED) {
                ExpandableFormSection(
                    title = stringResource(R.string.lh_metadata_optional),
                    summary = stringResource(R.string.lh_metadata_summary),
                    expanded = lhDetailsExpanded,
                    onExpandedChange = { lhDetailsExpanded = it },
                    expandContentDescription = stringResource(R.string.expand),
                    collapseContentDescription = stringResource(R.string.collapse),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { showLhTimePicker = true }, modifier = Modifier.weight(1f)) {
                            val timeText = state.lhTestMinutesOfDay?.let {
                                "%02d:%02d".format(it / 60, it % 60)
                            }
                            Text(timeText ?: stringResource(R.string.lh_add_test_time))
                        }
                        if (state.lhTestMinutesOfDay != null) {
                            TextButton(onClick = { viewModel.onEvent(ObservationEvent.LhTestTimeChanged(null)) }) {
                                Text(stringResource(R.string.clear))
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.lhTestBrand,
                        onValueChange = { viewModel.onEvent(ObservationEvent.LhTestBrandChanged(it)) },
                        label = { Text(stringResource(R.string.lh_test_brand)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.lhTestSensitivityText,
                        onValueChange = { viewModel.onEvent(ObservationEvent.LhTestSensitivityChanged(it)) },
                        label = { Text(stringResource(R.string.lh_test_sensitivity)) },
                        suffix = { Text(stringResource(R.string.lh_sensitivity_unit)) },
                        supportingText = { Text(stringResource(R.string.lh_test_sensitivity_helper)) },
                        isError = state.lhSensitivityInvalid,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.lhSensitivityInvalid) {
                        Text(
                            stringResource(R.string.lh_test_sensitivity_invalid),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            FormSection(
                title = stringResource(R.string.daily_wellbeing_title),
                supporting = stringResource(R.string.daily_wellbeing_supporting),
            ) {
                Text(stringResource(R.string.mood_title), style = MaterialTheme.typography.labelLarge)
                MultiSelectChips(
                    options = moodOptions(),
                    selectedMask = state.moodMask,
                    onToggle = { viewModel.onEvent(ObservationEvent.MoodToggled(it)) },
                )
                OutlinedTextField(
                    value = state.moodNote,
                    onValueChange = { viewModel.onEvent(ObservationEvent.MoodNoteChanged(it)) },
                    label = { Text(stringResource(R.string.mood_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                Text(stringResource(R.string.libido_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.libido_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EnumChips(
                    options = buildList {
                        add(LibidoLevel.NOT_RECORDED to R.string.not_recorded)
                        add(LibidoLevel.MASTURBATED to R.string.libido_masturbated)
                        add(LibidoLevel.HIGH to R.string.libido_high)
                        add(LibidoLevel.MEDIUM to R.string.libido_medium)
                        add(LibidoLevel.LOW to R.string.libido_low)
                        add(LibidoLevel.VERY_LOW to R.string.libido_very_low)
                        if (state.libidoLevel == LibidoLevel.VERY_HIGH) {
                            add(LibidoLevel.VERY_HIGH to R.string.libido_very_high)
                        }
                    },
                    selected = state.libidoLevel,
                    onSelected = { viewModel.onEvent(ObservationEvent.LibidoChanged(it)) },
                )

                Text(stringResource(R.string.sexual_contact_title), style = MaterialTheme.typography.labelLarge)
                EnumChips(
                    options = listOf(
                        SexualContact.NOT_RECORDED to R.string.not_recorded,
                        SexualContact.NONE to R.string.sexual_contact_none,
                        SexualContact.SOME to R.string.sexual_contact_some,
                        SexualContact.YES to R.string.sexual_contact_yes,
                    ),
                    selected = state.sexualContact,
                    onSelected = { viewModel.onEvent(ObservationEvent.SexualContactChanged(it)) },
                )
                if (state.sexualContact == SexualContact.YES) {
                    Text(
                        stringResource(R.string.sexual_contact_initiated_title),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        stringResource(R.string.sexual_contact_initiated_supporting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EnumChips(
                        options = listOf(
                            null to R.string.not_recorded,
                            true to R.string.initiated_by_me_yes,
                            false to R.string.initiated_by_me_no,
                        ),
                        selected = state.sexualContactInitiatedByUser,
                        onSelected = {
                            viewModel.onEvent(ObservationEvent.SexualContactInitiatedChanged(it))
                        },
                    )
                }
            }

            FormSection(
                title = stringResource(R.string.physical_symptoms_title),
                supporting = stringResource(R.string.physical_symptoms_supporting),
            ) {
                MultiSelectChips(
                    options = physicalSymptomOptions(),
                    selectedMask = state.physicalSymptomMask,
                    onToggle = { viewModel.onEvent(ObservationEvent.PhysicalSymptomToggled(it)) },
                )
                HorizontalDivider()
                Text(stringResource(R.string.pain_relief_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.pain_relief_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = state.painReliefPillCount == null,
                        onClick = {
                            viewModel.onEvent(ObservationEvent.PainReliefPillCountChanged(null))
                        },
                        label = { Text(stringResource(R.string.not_recorded)) },
                    )
                    FilterChip(
                        selected = state.painReliefPillCount == 0,
                        onClick = {
                            viewModel.onEvent(ObservationEvent.PainReliefPillCountChanged(0))
                        },
                        label = { Text(stringResource(R.string.pain_relief_none)) },
                    )
                }
                state.painReliefPillCount?.let { count ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            enabled = count > 0,
                            onClick = {
                                viewModel.onEvent(
                                    ObservationEvent.PainReliefPillCountChanged(count - 1),
                                )
                            },
                        ) { Text("−") }
                        Text(
                            stringResource(R.string.pain_relief_pill_count_value, count),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            enabled = count < com.yv.bbttracker.domain.model.MAX_DAILY_PAIN_RELIEF_PILLS,
                            onClick = {
                                viewModel.onEvent(
                                    ObservationEvent.PainReliefPillCountChanged(count + 1),
                                )
                            },
                        ) { Text("+") }
                    }
                    if (count > 0) {
                        OutlinedTextField(
                            value = state.painReliefMedicationNote,
                            onValueChange = {
                                viewModel.onEvent(
                                    ObservationEvent.PainReliefMedicationNoteChanged(it),
                                )
                            },
                            label = { Text(stringResource(R.string.pain_relief_medication_note)) },
                            supportingText = {
                                Text(stringResource(R.string.pain_relief_medication_note_supporting))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }

            ExpandableFormSection(
                title = stringResource(R.string.observation_more_details),
                summary = stringResource(R.string.observation_more_details_summary),
                expanded = moreDetailsExpanded,
                onExpandedChange = { moreDetailsExpanded = it },
                expandContentDescription = stringResource(R.string.expand),
                collapseContentDescription = stringResource(R.string.collapse),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = state.mucusObscured,
                        onCheckedChange = { viewModel.onEvent(ObservationEvent.MucusObscuredChanged(it)) },
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp, top = 8.dp)) {
                        Text(stringResource(R.string.mucus_obscured), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.mucus_obscured_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(stringResource(R.string.ovulation_pain), style = MaterialTheme.typography.labelLarge)
                EnumChips(
                    options = listOf(
                        OvulationPain.NOT_RECORDED to R.string.not_recorded,
                        OvulationPain.NONE to R.string.pain_none,
                        OvulationPain.LEFT to R.string.pain_left,
                        OvulationPain.RIGHT to R.string.pain_right,
                        OvulationPain.UNCLEAR to R.string.pain_unclear,
                    ),
                    selected = state.ovulationPain,
                    onSelected = { viewModel.onEvent(ObservationEvent.PainChanged(it)) },
                )
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.onEvent(ObservationEvent.NoteChanged(it)) },
                    label = { Text(stringResource(R.string.notes_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            if (state.saveFailed) {
                Text(stringResource(R.string.error_database), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(84.dp))
        }
    }

    if (showLhTimePicker) {
        val initialMinutes = state.lhTestMinutesOfDay
            ?: LocalTime.now().let { it.hour * 60 + it.minute }
        val timeState = rememberTimePickerState(
            initialHour = initialMinutes / 60,
            initialMinute = initialMinutes % 60,
            is24Hour = true,
        )
        TimePickerDialog(
            onDismissRequest = { showLhTimePicker = false },
            title = { Text(stringResource(R.string.lh_test_time)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(
                            ObservationEvent.LhTestTimeChanged(timeState.hour * 60 + timeState.minute),
                        )
                        showLhTimePicker = false
                    },
                ) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = { showLhTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) { TimeInput(timeState) }
    }

    if (state.showCycleConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(ObservationEvent.DismissCycleStart) },
            title = { Text(stringResource(R.string.cycle_start_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.cycle_start_confirm_body))
                    if (state.nearbyCycleWarning) {
                        Text(stringResource(R.string.cycle_start_close_warning), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(ObservationEvent.ConfirmCycleStart) }) {
                    Text(stringResource(R.string.start_cycle))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(ObservationEvent.DismissCycleStart) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun <T> EnumChips(options: List<Pair<T, Int>>, selected: T, onSelected: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun MultiSelectChips(
    options: List<Pair<Long, Int>>,
    selectedMask: Long,
    onToggle: (Long) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (flag, label) ->
            FilterChip(
                selected = selectedMask and flag != 0L,
                onClick = { onToggle(flag) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

private fun moodOptions(): List<Pair<Long, Int>> = listOf(
    MoodFlag.CALM to R.string.mood_calm,
    MoodFlag.HAPPY to R.string.mood_happy,
    MoodFlag.ENERGETIC to R.string.mood_energetic,
    MoodFlag.SENSITIVE to R.string.mood_sensitive,
    MoodFlag.IRRITABLE to R.string.mood_irritable,
    MoodFlag.ANXIOUS to R.string.mood_anxious,
    MoodFlag.SAD to R.string.mood_sad,
    MoodFlag.LOW to R.string.mood_low,
)

private fun physicalSymptomOptions(): List<Pair<Long, Int>> = listOf(
    PhysicalSymptomFlag.ABDOMINAL_PAIN to R.string.symptom_abdominal_pain,
    PhysicalSymptomFlag.HEADACHE to R.string.symptom_headache,
    PhysicalSymptomFlag.ACNE to R.string.symptom_acne,
    PhysicalSymptomFlag.BLOATING to R.string.symptom_bloating,
    PhysicalSymptomFlag.BACK_PAIN to R.string.symptom_back_pain,
    PhysicalSymptomFlag.BREAST_TENDERNESS to R.string.symptom_breast_tenderness,
    PhysicalSymptomFlag.FATIGUE to R.string.symptom_fatigue,
    PhysicalSymptomFlag.NAUSEA to R.string.symptom_nausea,
    PhysicalSymptomFlag.PELVIC_PRESSURE to R.string.symptom_pelvic_pressure,
    PhysicalSymptomFlag.SLEEP_CHANGES to R.string.symptom_sleep_changes,
    PhysicalSymptomFlag.OTHER to R.string.symptom_other,
)
