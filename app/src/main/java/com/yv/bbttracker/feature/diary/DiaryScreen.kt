package com.yv.bbttracker.feature.diary

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yv.bbttracker.R
import com.yv.bbttracker.domain.engine.AnalysisSignal
import com.yv.bbttracker.domain.engine.ForecastReliability
import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.DisturbanceFlag
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MeasurementSite
import com.yv.bbttracker.domain.model.MoodFlag
import com.yv.bbttracker.domain.model.MucusSensation
import com.yv.bbttracker.domain.model.OvulationPain
import com.yv.bbttracker.domain.model.PhysicalSymptomFlag
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import com.yv.bbttracker.ui.components.LabelValueRow
import com.yv.bbttracker.ui.formatting.Formatters
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ActualPeriodColor = Color(0xFFF3C2D2)
private val ConceptionColor = Color(0xFFFFE5A3)
private val ProspectiveOvulationColor = Color(0xFFD9CEF4)
private val RetrospectiveOvulationColor = Color(0xFFB8E3D9)
private val PossibleMenstruationEndColor = Color(0xFF8F5A70)
private val PossibleNextCycleColor = Color(0xFFAD3E67)
private val PossibleCycleEndColor = Color(0xFFD17A3D)
private val ActualCycleStartBorderColor = Color(0xFF3C2630)
private val CalendarCellShape = RoundedCornerShape(13.dp)
private val DayDateFormatter = DateTimeFormatter.ofPattern("dd/MM")
private enum class DayCellSignal { LH, FLUID, CONTACT, WELLBEING }

@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel,
    onEditMeasurement: (Long) -> Unit,
    onAddMeasurement: (LocalDate) -> Unit,
    onEditObservation: (LocalDate) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        state.calendar == null -> DiaryEmptyState()
        else -> DiaryContent(
            calendar = requireNotNull(state.calendar),
            onSelectCycle = viewModel::selectCycle,
            onEditMeasurement = onEditMeasurement,
            onAddMeasurement = onAddMeasurement,
            onEditObservation = onEditObservation,
        )
    }
}

@Composable
private fun DiaryEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.diary_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.diary_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiaryContent(
    calendar: DiaryCalendar,
    onSelectCycle: (Long) -> Unit,
    onEditMeasurement: (Long) -> Unit,
    onAddMeasurement: (LocalDate) -> Unit,
    onEditObservation: (LocalDate) -> Unit,
) {
    var selectedDayEpoch by rememberSaveable(calendar.cycle.id) { mutableStateOf<Long?>(null) }
    val selectedDay = remember(calendar.days, selectedDayEpoch) {
        selectedDayEpoch?.let { epoch -> calendar.days.firstOrNull { it.date.toEpochDay() == epoch } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 18.dp, 16.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(stringResource(R.string.diary_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.diary_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item {
            CycleSelector(
                options = calendar.options,
                selectedCycleId = calendar.cycle.id,
                onSelectCycle = onSelectCycle,
            )
        }
        item { CycleSummaryCard(calendar) }
        item {
            CalendarLegend()
            Text(
                stringResource(R.string.diary_tap_day),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        items(calendar.days.chunked(7).size) { weekIndex ->
            CycleWeek(
                weekNumber = weekIndex + 1,
                days = calendar.days.chunked(7)[weekIndex],
                onDayClick = { selectedDayEpoch = it.date.toEpochDay() },
            )
        }
        item {
            Text(
                stringResource(R.string.diary_forecast_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }

    selectedDay?.let { day ->
        DayDetailsSheet(
            day = day,
            onDismiss = { selectedDayEpoch = null },
            onEditMeasurement = {
                selectedDayEpoch = null
                onEditMeasurement(it)
            },
            onAddMeasurement = {
                selectedDayEpoch = null
                onAddMeasurement(day.date)
            },
            onEditObservation = {
                selectedDayEpoch = null
                onEditObservation(day.date)
            },
        )
    }
}

@Composable
private fun CycleSelector(
    options: List<DiaryCycleOption>,
    selectedCycleId: Long,
    onSelectCycle: (Long) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = DiaryCycleOption::id) { option ->
            FilterChip(
                selected = option.id == selectedCycleId,
                onClick = { onSelectCycle(option.id) },
                label = {
                    Text(
                        if (option.isCurrent) stringResource(R.string.diary_current_cycle)
                        else stringResource(R.string.diary_previous_cycle, Formatters.date(option.startDate)),
                    )
                },
            )
        }
    }
}

@Composable
private fun CycleSummaryCard(calendar: DiaryCalendar) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
        ),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .padding(20.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.diary_cycle_summary), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.diary_cycle_summary_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = CircleShape,
            ) {
                Text(
                    if (calendar.isCurrent) stringResource(R.string.cycle_day_compact, calendar.currentCycleDay)
                    else stringResource(R.string.cycle_length, calendar.actualCycleLength ?: calendar.days.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        SummaryMetric(
            label = stringResource(R.string.diary_actual_start),
            value = Formatters.longDate(calendar.cycle.startDate),
            status = stringResource(R.string.diary_recorded),
        )
        if (calendar.isCurrent) {
            calendar.expectedMenstruationEndDate?.let { date ->
                SummaryMetric(
                    label = stringResource(R.string.diary_expected_menstruation_end),
                    value = Formatters.longDate(date),
                    supporting = stringResource(R.string.diary_based_on_reported_menstruation_length),
                    status = stringResource(R.string.diary_forecast),
                )
            }
            calendar.expectedCurrentCycleEndRange?.let { range ->
                SummaryMetric(
                    label = stringResource(R.string.diary_expected_end),
                    value = Formatters.dateRange(range),
                    supporting = cycleDayRange(calendar.cycle.startDate, range),
                    status = stringResource(R.string.diary_forecast),
                )
            }
            calendar.expectedNextCycleStart?.let { forecast ->
                SummaryMetric(
                    label = stringResource(R.string.diary_next_cycle_expected),
                    value = Formatters.dateRange(forecast.expectedStartRange),
                    status = stringResource(R.string.diary_forecast),
                )
            }
        } else {
            calendar.cycle.endDate?.let {
                SummaryMetric(
                    label = stringResource(R.string.diary_actual_end),
                    value = Formatters.longDate(it),
                    status = stringResource(R.string.diary_recorded),
                )
            }
            calendar.knownNextCycleStart?.let {
                SummaryMetric(
                    label = stringResource(R.string.diary_next_cycle_actual),
                    value = Formatters.longDate(it),
                    status = stringResource(R.string.diary_recorded),
                )
            }
        }
        val retrospective = calendar.analysis.retrospectiveOvulationRange
        val prospective = calendar.analysis.prospectiveOvulationRange
        val hasConflict = AnalysisSignal.CONFLICTING_SIGNALS in calendar.analysis.signals
        retrospective?.let {
            SummaryMetric(
                label = stringResource(R.string.diary_ovulation_retrospective),
                value = Formatters.dateRange(it),
                supporting = cycleDayRange(calendar.cycle.startDate, it),
                status = reliabilityText(calendar.reliability),
            )
        }
        if (calendar.isCurrent && prospective != null && (retrospective == null || hasConflict)) {
            SummaryMetric(
                label = stringResource(R.string.diary_ovulation_expected),
                value = Formatters.dateRange(prospective),
                supporting = cycleDayRange(calendar.cycle.startDate, prospective),
                status = reliabilityText(calendar.reliability),
            )
        }
        if (retrospective == null && prospective == null) {
            SummaryMetric(
                label = stringResource(R.string.diary_ovulation_expected),
                value = stringResource(R.string.diary_no_ovulation_estimate),
                status = reliabilityText(calendar.reliability),
            )
        }
        if (calendar.isCurrent && hasConflict && retrospective != null && prospective != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        stringResource(R.string.diary_conflict_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        stringResource(R.string.diary_conflict_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    status: String,
    supporting: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun CalendarLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.diary_color_legend_title), style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LegendItem(ActualPeriodColor, stringResource(R.string.diary_legend_actual_period))
            LegendItem(
                color = Color.Transparent,
                label = stringResource(R.string.diary_legend_cycle_start),
                borderColor = ActualCycleStartBorderColor,
            )
            LegendItem(ConceptionColor, stringResource(R.string.diary_legend_conception))
            LegendItem(ProspectiveOvulationColor, stringResource(R.string.diary_legend_ovulation_expected))
            LegendItem(RetrospectiveOvulationColor, stringResource(R.string.diary_legend_ovulation_past))
            LegendItem(
                color = Color.Transparent,
                label = stringResource(R.string.diary_legend_menstruation_end),
                borderColor = PossibleMenstruationEndColor,
            )
            LegendItem(
                color = Color.Transparent,
                label = stringResource(R.string.diary_legend_cycle_end),
                borderColor = PossibleCycleEndColor,
            )
            LegendItem(
                color = Color.Transparent,
                label = stringResource(R.string.diary_legend_next_cycle),
                borderColor = PossibleNextCycleColor,
            )
            LegendItem(
                color = Color.Transparent,
                label = stringResource(R.string.diary_legend_today),
                borderColor = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(R.string.diary_legend_relationship),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.diary_symbol_legend_title), style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SymbolLegendItem(DayCellSignal.LH, stringResource(R.string.diary_legend_lh_symbol))
            SymbolLegendItem(DayCellSignal.FLUID, stringResource(R.string.diary_legend_fluid_symbol))
            SymbolLegendItem(DayCellSignal.CONTACT, stringResource(R.string.diary_legend_contact_symbol))
            SymbolLegendItem(DayCellSignal.WELLBEING, stringResource(R.string.diary_legend_wellbeing_symbol))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("36.5°", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.diary_legend_temperature_symbol), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SymbolLegendItem(signal: DayCellSignal, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DaySignalSymbol(signal)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, borderColor: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(13.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
                .then(
                    if (borderColor != null) Modifier.border(1.5.dp, borderColor, RoundedCornerShape(4.dp))
                    else Modifier,
                ),
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CycleWeek(
    weekNumber: Int,
    days: List<DiaryDay>,
    onDayClick: (DiaryDay) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.diary_week, weekNumber),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            days.forEach { day ->
                CycleDayCell(day = day, onClick = { onDayClick(day) }, modifier = Modifier.weight(1f))
            }
            repeat(7 - days.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun CycleDayCell(day: DiaryDay, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = when {
        DiaryDayMarker.BLEEDING in day.markers -> ActualPeriodColor
        DiaryDayMarker.RETROSPECTIVE_OVULATION in day.markers -> RetrospectiveOvulationColor
        DiaryDayMarker.PROSPECTIVE_OVULATION in day.markers -> ProspectiveOvulationColor
        DiaryDayMarker.CONCEPTION_WINDOW in day.markers -> ConceptionColor
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (day.isFuture) 0.38f else 0.7f)
    }
    val borderColor = when {
        day.isToday -> MaterialTheme.colorScheme.primary
        DiaryDayMarker.CYCLE_START in day.markers -> ActualCycleStartBorderColor
        DiaryDayMarker.POSSIBLE_NEXT_CYCLE_START in day.markers -> PossibleNextCycleColor
        DiaryDayMarker.POSSIBLE_MENSTRUATION_END in day.markers -> PossibleMenstruationEndColor
        DiaryDayMarker.POSSIBLE_CYCLE_END in day.markers -> PossibleCycleEndColor
        else -> Color.Transparent
    }
    val borderWidth = when {
        day.isToday -> 2.5.dp
        borderColor != Color.Transparent -> 1.5.dp
        else -> 0.dp
    }
    Surface(
        modifier = modifier
            .aspectRatio(0.78f)
            .clip(CalendarCellShape)
            .clickable(onClick = onClick),
        color = background,
        shape = CalendarCellShape,
        border = BorderStroke(borderWidth, borderColor),
        tonalElevation = if (day.isToday) 2.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                day.cycleDay.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (day.isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                lineHeight = 18.sp,
            )
            Text(
                DayDateFormatter.format(day.date),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 10.sp,
            )
            Spacer(Modifier.weight(1f))
            day.selectedMeasurement?.let {
                Text(
                    "${Formatters.temperature(it.temperatureCentiC)}°",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            DaySignalDots(day)
        }
    }
}

@Composable
private fun DaySignalDots(day: DiaryDay) {
    val signals = buildList {
        if (DiaryDayMarker.LH_POSITIVE in day.markers) add(DayCellSignal.LH)
        if (DiaryDayMarker.FERTILE_FLUID in day.markers) add(DayCellSignal.FLUID)
        if (DiaryDayMarker.SEXUAL_CONTACT in day.markers) add(DayCellSignal.CONTACT)
        if (
            day.observation?.physicalSymptomMask?.let { it != 0L } == true ||
            day.observation?.moodMask?.let { it != 0L } == true
        ) add(DayCellSignal.WELLBEING)
    }.take(3)
    Row(
        modifier = Modifier.height(13.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        signals.forEach { signal ->
            DaySignalSymbol(signal)
        }
    }
}

@Composable
private fun DaySignalSymbol(signal: DayCellSignal) {
    when (signal) {
        DayCellSignal.LH -> Surface(
            color = Color(0xFF5D448D),
            shape = RoundedCornerShape(3.dp),
            modifier = Modifier.size(width = 15.dp, height = 10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "LH",
                    color = Color.White,
                    fontSize = 6.sp,
                    lineHeight = 7.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        DayCellSignal.FLUID -> Icon(
            Icons.Outlined.WaterDrop,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = Color(0xFF237A70),
        )
        DayCellSignal.CONTACT -> Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = PossibleNextCycleColor,
        )
        DayCellSignal.WELLBEING -> Box(
            Modifier.size(4.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DayDetailsSheet(
    day: DiaryDay,
    onDismiss: () -> Unit,
    onEditMeasurement: (Long) -> Unit,
    onAddMeasurement: () -> Unit,
    onEditObservation: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 0.dp, 20.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.diary_cycle_day_and_date, day.cycleDay, Formatters.longDate(day.date)),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.diary_day_details),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (day.markers.isNotEmpty()) {
                item { ForecastAndMarkerSection(day) }
            }
            item {
                Text(stringResource(R.string.diary_recorded_details), style = MaterialTheme.typography.titleLarge)
            }
            if (!day.hasRecordedData) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            stringResource(R.string.diary_no_recorded_data),
                            modifier = Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (day.measurements.isNotEmpty()) {
                item {
                    DetailSectionHeader(
                        Icons.Outlined.Thermostat,
                        stringResource(R.string.diary_temperature_count, day.measurements.size),
                    )
                }
                items(day.measurements, key = TemperatureMeasurement::id) { measurement ->
                    MeasurementDetailCard(
                        measurement = measurement,
                        number = day.measurements.indexOf(measurement) + 1,
                        onEdit = { onEditMeasurement(measurement.id) },
                    )
                }
            }
            day.observation?.let { observation ->
                item {
                    DetailSectionHeader(Icons.Outlined.WaterDrop, stringResource(R.string.daily_signs))
                    ObservationDetails(observation)
                }
            }
            if (!day.isFuture) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (day.measurements.isEmpty()) {
                            Button(onClick = onAddMeasurement, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.diary_add_temperature))
                            }
                        }
                        OutlinedButton(onClick = onEditObservation, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.diary_edit_signs))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastAndMarkerSection(day: DiaryDay) {
    val descriptions = markerDescriptions(day)
    if (descriptions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.diary_forecast_details), style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            descriptions.forEach { (text, color) ->
                Surface(color = color, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun markerDescriptions(day: DiaryDay): List<Pair<String, Color>> = buildList {
    if (day.isToday) add(stringResource(R.string.diary_today) to MaterialTheme.colorScheme.primaryContainer)
    if (DiaryDayMarker.CYCLE_START in day.markers) {
        add(stringResource(R.string.diary_cycle_start_yes) to ActualPeriodColor)
    }
    if (DiaryDayMarker.BLEEDING in day.markers) {
        add(stringResource(R.string.diary_bleeding_day) to ActualPeriodColor)
    }
    if (DiaryDayMarker.SPOTTING in day.markers) {
        add(stringResource(R.string.diary_spotting_day) to ActualPeriodColor.copy(alpha = 0.65f))
    }
    if (DiaryDayMarker.CONCEPTION_WINDOW in day.markers) {
        add(stringResource(R.string.diary_conception_day) to ConceptionColor)
    }
    if (DiaryDayMarker.PROSPECTIVE_OVULATION in day.markers) {
        add(stringResource(R.string.diary_prospective_ovulation_day) to ProspectiveOvulationColor)
    }
    if (DiaryDayMarker.RETROSPECTIVE_OVULATION in day.markers) {
        add(stringResource(R.string.diary_retrospective_ovulation_day) to RetrospectiveOvulationColor)
    }
    if (DiaryDayMarker.POSSIBLE_MENSTRUATION_END in day.markers) {
        add(
            stringResource(R.string.diary_possible_menstruation_end) to
                PossibleMenstruationEndColor.copy(alpha = 0.30f),
        )
    }
    if (DiaryDayMarker.POSSIBLE_CYCLE_END in day.markers) {
        add(stringResource(R.string.diary_possible_cycle_end) to Color(0xFFFFE0C7))
    }
    if (DiaryDayMarker.POSSIBLE_NEXT_CYCLE_START in day.markers) {
        add(stringResource(R.string.diary_possible_next_start) to Color(0xFFF4CEDC))
    }
    if (DiaryDayMarker.LH_POSITIVE in day.markers) {
        add(stringResource(R.string.diary_lh_positive_day) to ProspectiveOvulationColor)
    }
    if (DiaryDayMarker.FERTILE_FLUID in day.markers) {
        add(stringResource(R.string.diary_fertile_fluid_day) to RetrospectiveOvulationColor)
    }
    if (DiaryDayMarker.THERMAL_SHIFT_FIRST_HIGH in day.markers) {
        add(stringResource(R.string.diary_thermal_shift_day) to Color(0xFFD5E5F7))
    }
    if (DiaryDayMarker.SEXUAL_CONTACT in day.markers) {
        add(stringResource(R.string.diary_contact_day) to Color(0xFFF6D8E3))
    }
}

@Composable
private fun DetailSectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun MeasurementDetailCard(
    measurement: TemperatureMeasurement,
    number: Int,
    onEdit: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(stringResource(R.string.diary_measurement_number, number), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.temperature_value, Formatters.temperature(measurement.temperatureCentiC)),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                AssistChip(onClick = onEdit, label = { Text(stringResource(R.string.edit)) })
            }
            LabelValueRow(
                stringResource(R.string.time),
                Formatters.time(measurement.measuredAtEpochMillis, measurement.zoneId),
            )
            LabelValueRow(stringResource(R.string.measurement_site), stringResource(measurement.site.labelRes()))
            LabelValueRow(
                stringResource(R.string.use_for_analysis),
                stringResource(
                    if (measurement.selectedForAnalysis) R.string.analysis_included
                    else R.string.analysis_excluded,
                ),
            )
            measurement.sleepMinutes?.let {
                LabelValueRow(
                    stringResource(R.string.sleep_duration),
                    stringResource(R.string.diary_sleep_value, it / 60, it % 60),
                )
            }
            Text(
                when (measurement.measuredImmediatelyAfterWaking) {
                    true -> stringResource(R.string.diary_immediate_waking)
                    false -> stringResource(R.string.diary_not_immediate_waking)
                    null -> stringResource(R.string.diary_waking_not_recorded)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val disturbances = disturbanceLabels(measurement.disturbanceMask)
            Text(
                if (disturbances.isEmpty()) stringResource(R.string.diary_disturbances_none)
                else disturbances.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            measurement.disturbanceNote?.takeIf(String::isNotBlank)?.let {
                DetailTextBlock(stringResource(R.string.diary_disturbance_details), it)
            }
            measurement.note?.takeIf(String::isNotBlank)?.let {
                DetailTextBlock(stringResource(R.string.diary_measurement_note), it)
            }
            LabelValueRow(stringResource(R.string.diary_source), stringResource(R.string.diary_manual_source))
        }
    }
}

@Composable
private fun ObservationDetails(observation: DailyObservation) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            LabelValueRow(stringResource(R.string.bleeding), stringResource(observation.bleeding.labelRes()))
            HorizontalDivider()
            LabelValueRow(stringResource(R.string.mucus_appearance), stringResource(observation.mucus.labelRes()))
            LabelValueRow(
                stringResource(R.string.mucus_sensation),
                stringResource(observation.mucusSensation.labelRes()),
            )
            Text(
                stringResource(
                    if (observation.mucusObscured) R.string.diary_fluid_obscured_yes
                    else R.string.diary_fluid_obscured_no,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            LabelValueRow(stringResource(R.string.lh_result), stringResource(observation.lhResult.labelRes()))
            observation.lhTestMinutesOfDay?.let {
                LabelValueRow(stringResource(R.string.lh_test_time), minutesOfDay(it))
            }
            observation.lhTestBrand?.takeIf(String::isNotBlank)?.let {
                LabelValueRow(stringResource(R.string.lh_test_brand), it)
            }
            observation.lhTestSensitivityMilliIu?.let {
                LabelValueRow(
                    stringResource(R.string.lh_test_sensitivity),
                    stringResource(R.string.diary_lh_sensitivity_value, it),
                )
            }
            LabelValueRow(
                stringResource(R.string.ovulation_pain),
                stringResource(observation.ovulationPain.labelRes()),
            )
            HorizontalDivider()
            DetailTextBlock(
                stringResource(R.string.mood_title),
                moodLabels(observation.moodMask).ifEmpty {
                    listOf(stringResource(R.string.diary_mood_none))
                }.joinToString(" · "),
            )
            observation.moodNote?.takeIf(String::isNotBlank)?.let {
                DetailTextBlock(stringResource(R.string.diary_mood_note), it)
            }
            LabelValueRow(
                stringResource(R.string.libido_title),
                stringResource(observation.libidoLevel.labelRes()),
            )
            LabelValueRow(
                stringResource(R.string.sexual_contact_title),
                stringResource(observation.sexualContact.labelRes()),
            )
            observation.sexualContactInitiatedByUser?.let { initiated ->
                LabelValueRow(
                    stringResource(R.string.sexual_contact_initiated_title),
                    stringResource(
                        if (initiated) R.string.initiated_by_me_yes
                        else R.string.initiated_by_me_no,
                    ),
                )
            }
            DetailTextBlock(
                stringResource(R.string.physical_symptoms_title),
                symptomLabels(observation.physicalSymptomMask).ifEmpty {
                    listOf(stringResource(R.string.diary_symptoms_none))
                }.joinToString(" · "),
            )
            observation.painReliefPillCount?.let { count ->
                LabelValueRow(
                    stringResource(R.string.pain_relief_title),
                    stringResource(R.string.pain_relief_pill_count_value, count),
                )
            }
            observation.painReliefMedicationNote?.takeIf(String::isNotBlank)?.let {
                DetailTextBlock(stringResource(R.string.pain_relief_medication_note), it)
            }
            HorizontalDivider()
            Text(
                stringResource(
                    if (observation.isExplicitCycleStart) R.string.diary_cycle_start_yes
                    else R.string.diary_cycle_start_no,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            observation.note?.takeIf(String::isNotBlank)?.let {
                DetailTextBlock(stringResource(R.string.diary_daily_note), it)
            }
        }
    }
}

@Composable
private fun DetailTextBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun disturbanceLabels(mask: Long): List<String> = buildList {
    disturbanceResources.forEach { (flag, resource) ->
        if (mask and flag != 0L) add(stringResource(resource))
    }
}

@Composable
private fun moodLabels(mask: Long): List<String> = buildList {
    moodResources.forEach { (flag, resource) ->
        if (mask and flag != 0L) add(stringResource(resource))
    }
}

@Composable
private fun symptomLabels(mask: Long): List<String> = buildList {
    symptomResources.forEach { (flag, resource) ->
        if (mask and flag != 0L) add(stringResource(resource))
    }
}

@Composable
private fun reliabilityText(reliability: ForecastReliability): String = stringResource(
    when (reliability) {
        ForecastReliability.INSUFFICIENT -> R.string.reliability_value_insufficient
        ForecastReliability.LIMITED -> R.string.reliability_value_limited
        ForecastReliability.MODERATE -> R.string.reliability_value_moderate
        ForecastReliability.STRONG -> R.string.reliability_value_strong
    },
)

@Composable
private fun cycleDayRange(start: LocalDate, range: ClosedRange<LocalDate>): String {
    val first = (range.start.toEpochDay() - start.toEpochDay() + 1).toInt()
    val last = (range.endInclusive.toEpochDay() - start.toEpochDay() + 1).toInt()
    return if (first == last) stringResource(R.string.diary_cycle_day_single, first)
    else stringResource(R.string.diary_cycle_days_range, first, last)
}

private fun minutesOfDay(value: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", value / 60, value % 60)

@StringRes
private fun MeasurementSite.labelRes(): Int = when (this) {
    MeasurementSite.ORAL -> R.string.site_oral
    MeasurementSite.VAGINAL -> R.string.site_vaginal
    MeasurementSite.RECTAL -> R.string.site_rectal
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

private val disturbanceResources = listOf(
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

private val moodResources = listOf(
    MoodFlag.CALM to R.string.mood_calm,
    MoodFlag.HAPPY to R.string.mood_happy,
    MoodFlag.ENERGETIC to R.string.mood_energetic,
    MoodFlag.SENSITIVE to R.string.mood_sensitive,
    MoodFlag.IRRITABLE to R.string.mood_irritable,
    MoodFlag.ANXIOUS to R.string.mood_anxious,
    MoodFlag.SAD to R.string.mood_sad,
    MoodFlag.LOW to R.string.mood_low,
)

private val symptomResources = listOf(
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
