package com.yv.bbttracker.feature.today

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yv.bbttracker.R
import com.yv.bbttracker.domain.engine.CycleAnalysisResult
import com.yv.bbttracker.domain.engine.DataQuality
import com.yv.bbttracker.domain.engine.AnalysisSignal
import com.yv.bbttracker.domain.engine.FertilityStatus
import com.yv.bbttracker.domain.engine.FertilityLevelToday
import com.yv.bbttracker.domain.engine.ForecastReliability
import com.yv.bbttracker.domain.engine.NextAction
import com.yv.bbttracker.domain.engine.PeriodForecast
import com.yv.bbttracker.domain.engine.PeriodForecastBasis
import com.yv.bbttracker.domain.insights.PersonalInsight
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.PhysicalSymptomFlag
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.model.TrackingGoal
import com.yv.bbttracker.ui.components.ExpandableFormSection
import com.yv.bbttracker.ui.formatting.Formatters

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onAddMeasurement: () -> Unit,
    onEditMeasurement: (Long) -> Unit,
    onObservation: () -> Unit,
    onOpenChart: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var explanationExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.today_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    Formatters.longDate(state.date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = CircleShape,
                color = if (state.cycleDay != null) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Text(
                    text = state.cycleDay?.let { stringResource(R.string.cycle_day_compact, it) }
                        ?: stringResource(R.string.no_active_cycle_compact),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.cycleDay != null) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                )
            }
        }

        DailyLogCard(
            state = state,
            onMeasurement = {
                state.primaryMeasurement?.id?.let(onEditMeasurement) ?: onAddMeasurement()
            },
            onObservation = onObservation,
        )
        if (state.insights.isNotEmpty()) {
            PersonalInsightsCard(state.insights)
        }
        StatusCard(state.analysis, state.trackingGoal)
        ForecastCard(state.analysis)

        ExpandableFormSection(
            title = stringResource(R.string.why_this_status),
            expanded = explanationExpanded,
            onExpandedChange = { explanationExpanded = it },
            expandContentDescription = stringResource(R.string.expand),
            collapseContentDescription = stringResource(R.string.collapse),
            summary = stringResource(R.string.status_explanation_summary),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(explanationFor(state.analysis), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                stringResource(R.string.data_changed_since_yesterday),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(onClick = onOpenChart, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Icon(Icons.Outlined.AutoGraph, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 5.dp))
            Text(stringResource(R.string.open_chart))
        }
        Spacer(Modifier.height(84.dp))
    }
}

@Composable
private fun DailyLogCard(
    state: TodayUiState,
    onMeasurement: () -> Unit,
    onObservation: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Text(
                stringResource(R.string.today_log_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 7.dp),
            )
            val measurement = state.primaryMeasurement
            DailyActionRow(
                icon = Icons.Outlined.Thermostat,
                title = stringResource(R.string.today_measurement),
                summary = measurement?.let {
                    stringResource(
                        R.string.measurement_quick_summary,
                        Formatters.temperature(it.temperatureCentiC),
                        Formatters.time(it.measuredAtEpochMillis, it.zoneId),
                    )
                } ?: stringResource(R.string.measurement_not_recorded),
                completed = measurement != null,
                onClick = onMeasurement,
            )
            HorizontalDivider(Modifier.padding(horizontal = 18.dp))
            DailyActionRow(
                icon = Icons.Outlined.Bloodtype,
                title = stringResource(if (state.cycle == null) R.string.start_cycle else R.string.daily_signs),
                summary = if (state.observation != null) {
                    observationSummary(state.observation)
                } else {
                    stringResource(
                        if (state.cycle == null) R.string.start_cycle_supporting else R.string.signs_not_recorded,
                    )
                },
                completed = state.observation != null,
                onClick = onObservation,
            )
        }
    }
}

@Composable
private fun observationSummary(observation: DailyObservation): String {
    val recorded = mutableListOf<String>()
    if (observation.bleeding != BleedingLevel.NOT_RECORDED) {
        recorded += stringResource(R.string.bleeding)
    }
    if (observation.mucus != CervicalMucus.NOT_CHECKED ||
        observation.mucusSensation != MucusSensation.NOT_CHECKED
    ) {
        recorded += stringResource(R.string.mucus)
    }
    if (observation.lhResult != LhResult.NOT_TESTED) {
        recorded += stringResource(R.string.lh_result)
    }
    if (observation.moodMask != 0L || !observation.moodNote.isNullOrBlank()) {
        recorded += stringResource(R.string.mood_title)
    }
    if (observation.libidoLevel != LibidoLevel.NOT_RECORDED) {
        recorded += stringResource(R.string.libido_title)
    }
    if (observation.sexualContact != SexualContact.NOT_RECORDED) {
        recorded += stringResource(R.string.sexual_contact_title)
    }
    if (observation.physicalSymptomMask != 0L) {
        recorded += stringResource(R.string.physical_symptoms_title)
    }
    if (observation.painReliefPillCount != null) {
        recorded += stringResource(R.string.pain_relief_title)
    }
    return if (recorded.isEmpty()) {
        stringResource(R.string.signs_recorded)
    } else {
        stringResource(R.string.signs_recorded_summary, recorded.take(3).joinToString(" · "))
    }
}

@Composable
private fun PersonalInsightsCard(insights: List<PersonalInsight>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    stringResource(R.string.personal_insights_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            insights.forEach { insight ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text("•", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        insightText(insight),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                stringResource(R.string.personal_insights_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.74f),
            )
        }
    }
}

@Composable
private fun insightText(insight: PersonalInsight): String = when (insight) {
    is PersonalInsight.LowInitiationRate -> stringResource(
        R.string.insight_low_initiation,
        insight.percent,
    )
    is PersonalInsight.RecurringSymptom -> stringResource(
        R.string.insight_recurring_symptom,
        stringResource(symptomResource(insight.symptomFlag)),
        insight.percent,
    )
    PersonalInsight.ContactWithoutDesire -> stringResource(R.string.insight_contact_without_desire)
    PersonalInsight.HighDesireLowContact -> stringResource(R.string.insight_high_desire_low_contact)
    PersonalInsight.LowMood -> stringResource(R.string.insight_low_mood)
    PersonalInsight.HigherPainReliefUse -> stringResource(R.string.insight_higher_pain_relief)
    PersonalInsight.LowerPainReliefUse -> stringResource(R.string.insight_lower_pain_relief)
    is PersonalInsight.TrackingStreak -> stringResource(
        R.string.insight_tracking_streak,
        insight.days,
    )
}

@StringRes
private fun symptomResource(flag: Long): Int = when (flag) {
    PhysicalSymptomFlag.ABDOMINAL_PAIN -> R.string.symptom_abdominal_pain
    PhysicalSymptomFlag.HEADACHE -> R.string.symptom_headache
    PhysicalSymptomFlag.ACNE -> R.string.symptom_acne
    PhysicalSymptomFlag.BLOATING -> R.string.symptom_bloating
    PhysicalSymptomFlag.BACK_PAIN -> R.string.symptom_back_pain
    PhysicalSymptomFlag.BREAST_TENDERNESS -> R.string.symptom_breast_tenderness
    PhysicalSymptomFlag.FATIGUE -> R.string.symptom_fatigue
    PhysicalSymptomFlag.NAUSEA -> R.string.symptom_nausea
    PhysicalSymptomFlag.PELVIC_PRESSURE -> R.string.symptom_pelvic_pressure
    PhysicalSymptomFlag.SLEEP_CHANGES -> R.string.symptom_sleep_changes
    else -> R.string.symptom_other
}

@Composable
private fun DailyActionRow(
    icon: ImageVector,
    title: String,
    summary: String,
    completed: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = if (completed) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ) {
            Icon(
                imageVector = if (completed) Icons.Outlined.CheckCircle else icon,
                contentDescription = null,
                tint = if (completed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Outlined.ChevronLeft,
            contentDescription = stringResource(if (completed) R.string.edit else R.string.add),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusCard(analysis: CycleAnalysisResult?, trackingGoal: TrackingGoal) {
    val level = analysis?.fertilityLevelToday
    val containerColor = when (level) {
        FertilityLevelToday.HIGH,
        FertilityLevelToday.PEAK_SIGNAL,
        -> MaterialTheme.colorScheme.tertiaryContainer
        FertilityLevelToday.ELEVATED -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.EventRepeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.fertility_today_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(fertilityLevelResource(analysis?.fertilityLevelToday)),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(statusResource(analysis?.status)), style = MaterialTheme.typography.bodyLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                StatusPill(stringResource(reliabilityResource(analysis?.reliability)))
                StatusPill(stringResource(qualityResource(analysis?.dataQuality)))
            }
            if (analysis?.fertilityLevelToday == FertilityLevelToday.BACKGROUND) {
                Text(
                    stringResource(R.string.background_not_safe_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!analysis?.signals.isNullOrEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    analysis.signals
                        .sortedBy { it.ordinal }
                        .take(5)
                        .forEach { signal ->
                            StatusPill(stringResource(signalResource(signal)))
                        }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                stringResource(nextActionResource(analysis?.nextAction, trackingGoal)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ForecastCard(analysis: CycleAnalysisResult?) {
    val conceptionWindow = analysis?.conceptionOpportunityWindow
    val prospectiveRange = analysis?.prospectiveOvulationRange
    val retrospectiveRange = analysis?.retrospectiveOvulationRange
    val periodForecast = analysis?.nextPeriodForecast
    val hasConflict = analysis?.signals?.contains(AnalysisSignal.CONFLICTING_SIGNALS) == true
    val showProspectiveRanges = retrospectiveRange == null || hasConflict
    if (conceptionWindow == null && prospectiveRange == null && retrospectiveRange == null &&
        periodForecast == null
    ) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.forecast_ranges_title), style = MaterialTheme.typography.titleMedium)
            conceptionWindow?.takeIf { showProspectiveRanges }?.let {
                DateRangeRow(R.string.conception_window_label, it)
            }
            prospectiveRange?.takeIf { showProspectiveRanges }?.let {
                DateRangeRow(R.string.prospective_ovulation_label, it)
            }
            retrospectiveRange?.let {
                DateRangeRow(R.string.retrospective_ovulation_label, it)
            }
            if (hasConflict && retrospectiveRange != null && prospectiveRange != null) {
                Text(
                    stringResource(R.string.forecast_conflict_pair_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            periodForecast?.let { PeriodForecastRow(it) }
            Text(
                stringResource(R.string.forecast_ranges_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun PeriodForecastRow(forecast: PeriodForecast) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.next_period_label), style = MaterialTheme.typography.labelLarge)
        Text(
            stringResource(R.string.date_range_value, Formatters.dateRange(forecast.expectedStartRange)),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            periodBasisText(forecast),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (forecast.isOverdue) {
            Text(
                stringResource(R.string.period_overdue_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun periodBasisText(forecast: PeriodForecast): String = when (forecast.basis) {
    PeriodForecastBasis.OVULATION_AND_PERSONAL_LUTEAL ->
        stringResource(R.string.period_basis_ovulation_personal, forecast.lutealDaysUsed ?: 0)
    PeriodForecastBasis.OVULATION_AND_DEFAULT_LUTEAL ->
        stringResource(R.string.period_basis_ovulation_default, forecast.lutealDaysUsed ?: 0)
    PeriodForecastBasis.CYCLE_LENGTH_HISTORY -> stringResource(R.string.period_basis_cycle_history)
    PeriodForecastBasis.DEFAULT_ESTIMATE -> stringResource(R.string.period_basis_default)
}

@Composable
private fun DateRangeRow(labelRes: Int, range: ClosedRange<java.time.LocalDate>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
        Text(
            stringResource(
                R.string.date_range_value,
                Formatters.dateRange(range),
            ),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun explanationFor(analysis: CycleAnalysisResult?): String {
    if (analysis == null) return stringResource(R.string.explanation_insufficient)
    return when (analysis.status) {
        FertilityStatus.INSUFFICIENT_DATA -> stringResource(R.string.explanation_insufficient)
        FertilityStatus.CALENDAR_ESTIMATE_ONLY,
        FertilityStatus.PREDICTED_FERTILE_WINDOW,
        -> stringResource(R.string.explanation_calendar)
        FertilityStatus.FERTILITY_SIGNS_PRESENT -> stringResource(R.string.explanation_fertile_mucus)
        FertilityStatus.LH_SURGE_DETECTED -> stringResource(R.string.explanation_lh_positive)
        FertilityStatus.THERMAL_SHIFT_CANDIDATE -> stringResource(R.string.explanation_shift_candidate)
        FertilityStatus.THERMAL_SHIFT_CONFIRMED,
        FertilityStatus.POST_OVULATORY_ESTIMATE,
        -> analysis.retrospectiveOvulationRange?.let {
            stringResource(
                R.string.explanation_shift_confirmed,
                Formatters.dateRange(it),
            )
        } ?: stringResource(R.string.explanation_shift_candidate)
        FertilityStatus.UNCERTAIN -> stringResource(R.string.explanation_uncertain)
    }
}

private fun fertilityLevelResource(level: FertilityLevelToday?): Int = when (level) {
    null, FertilityLevelToday.UNKNOWN -> R.string.fertility_level_unknown
    FertilityLevelToday.BACKGROUND -> R.string.fertility_level_background
    FertilityLevelToday.ELEVATED -> R.string.fertility_level_elevated
    FertilityLevelToday.HIGH -> R.string.fertility_level_high
    FertilityLevelToday.PEAK_SIGNAL -> R.string.fertility_level_peak
}

private fun reliabilityResource(reliability: ForecastReliability?): Int = when (reliability) {
    null, ForecastReliability.INSUFFICIENT -> R.string.reliability_insufficient
    ForecastReliability.LIMITED -> R.string.reliability_limited
    ForecastReliability.MODERATE -> R.string.reliability_moderate
    ForecastReliability.STRONG -> R.string.reliability_strong
}

private fun signalResource(signal: AnalysisSignal): Int = when (signal) {
    AnalysisSignal.PERSONAL_HISTORY -> R.string.signal_personal_history
    AnalysisSignal.CYCLE_LENGTH_HISTORY -> R.string.signal_cycle_history
    AnalysisSignal.LH_BORDERLINE -> R.string.signal_lh_borderline
    AnalysisSignal.LH_SURGE -> R.string.signal_lh_surge
    AnalysisSignal.FERTILE_MUCUS -> R.string.signal_fertile_mucus
    AnalysisSignal.RISING_MUCUS -> R.string.signal_rising_mucus
    AnalysisSignal.MUCUS_PEAK -> R.string.signal_mucus_peak
    AnalysisSignal.THERMAL_CANDIDATE -> R.string.signal_thermal_candidate
    AnalysisSignal.THERMAL_SHIFT -> R.string.signal_thermal_shift
    AnalysisSignal.CONFLICTING_SIGNALS -> R.string.signal_conflict
    AnalysisSignal.UNRELIABLE_TEMPERATURES_EXCLUDED -> R.string.signal_temperatures_excluded
}

private fun nextActionResource(action: NextAction?, goal: TrackingGoal): Int = when (action) {
    null, NextAction.CONTINUE_DAILY_TRACKING -> R.string.action_continue_tracking
    NextAction.START_OR_CONTINUE_LH_TESTING -> R.string.action_lh_testing
    NextAction.REPEAT_LH_TEST -> R.string.action_repeat_lh
    NextAction.PRIORITIZE_CONCEPTION_TIMING -> if (goal == TrackingGoal.TRYING_TO_CONCEIVE) {
        R.string.action_conception_timing
    } else {
        R.string.action_peak_awareness
    }
    NextAction.AWAIT_THERMAL_CONFIRMATION -> R.string.action_await_thermal
    NextAction.IMPROVE_TEMPERATURE_QUALITY -> R.string.action_improve_temperature
    NextAction.REVIEW_CONFLICTING_SIGNALS -> R.string.action_review_conflict
}

private fun statusResource(status: FertilityStatus?): Int = when (status) {
    null, FertilityStatus.INSUFFICIENT_DATA -> R.string.status_insufficient_data
    FertilityStatus.CALENDAR_ESTIMATE_ONLY -> R.string.status_calendar_estimate
    FertilityStatus.PREDICTED_FERTILE_WINDOW -> R.string.status_predicted_window
    FertilityStatus.FERTILITY_SIGNS_PRESENT -> R.string.status_fertility_signs
    FertilityStatus.LH_SURGE_DETECTED -> R.string.status_lh_surge
    FertilityStatus.THERMAL_SHIFT_CANDIDATE -> R.string.status_shift_candidate
    FertilityStatus.THERMAL_SHIFT_CONFIRMED -> R.string.status_shift_confirmed
    FertilityStatus.POST_OVULATORY_ESTIMATE -> R.string.status_post_ovulatory
    FertilityStatus.UNCERTAIN -> R.string.status_uncertain
}

private fun qualityResource(quality: DataQuality?): Int = when (quality) {
    null, DataQuality.INSUFFICIENT -> R.string.quality_insufficient
    DataQuality.LOW -> R.string.quality_low
    DataQuality.MODERATE -> R.string.quality_moderate
    DataQuality.GOOD -> R.string.quality_good
}
