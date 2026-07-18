package com.yv.bbttracker.feature.chart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.yv.bbttracker.R
import com.yv.bbttracker.domain.engine.AnalysisSignal
import com.yv.bbttracker.domain.engine.ForecastReliability
import com.yv.bbttracker.domain.model.TemperatureMeasurement
import com.yv.bbttracker.ui.components.ExpandableFormSection
import com.yv.bbttracker.ui.formatting.Formatters
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class SeriesKind { INCLUDED, EXCLUDED }

/**
 * Keeps the BBT band visible and, when a forecast window extends past the measured days,
 * stretches the x-range so the shaded window can be drawn in the still-empty future days.
 */
private fun bbtRangeProvider(extendMaxXTo: Double?) = object : CartesianLayerRangeProvider {
    override fun getMaxX(minX: Double, maxX: Double, extraStore: com.patrykandpatrick.vico.compose.common.data.ExtraStore): Double =
        extendMaxXTo?.let { max(maxX, it) } ?: maxX

    override fun getMinY(minY: Double, maxY: Double, extraStore: com.patrykandpatrick.vico.compose.common.data.ExtraStore): Double =
        min(35.5, minY - 0.10)

    override fun getMaxY(minY: Double, maxY: Double, extraStore: com.patrykandpatrick.vico.compose.common.data.ExtraStore): Double =
        max(37.5, maxY + 0.10)
}

@Composable
fun ChartScreen(viewModel: ChartViewModel, onEditMeasurement: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var legendExpanded by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.chart_title), style = MaterialTheme.typography.headlineMedium)
            state.cycle?.let {
                Text(
                    stringResource(R.string.cycle_started_on, Formatters.date(it.startDate)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.analysis?.let { analysis ->
            item { ChartForecastSummary(analysis, state.isCurrentCycle, state.sexualContactDays) }
        }
        if (!state.isLoading && state.measurements.isEmpty()) {
            item { EmptyChart() }
        } else if (state.cycle != null && state.measurements.isNotEmpty()) {
            item { BbtVicoChart(state) }
            item {
                ExpandableFormSection(
                    title = stringResource(R.string.chart_legend_title),
                    summary = stringResource(R.string.chart_legend_summary),
                    expanded = legendExpanded,
                    onExpandedChange = { legendExpanded = it },
                    expandContentDescription = stringResource(R.string.expand),
                    collapseContentDescription = stringResource(R.string.collapse),
                ) {
                    ChartLegend(state)
                }
            }
            item {
                Text(
                    stringResource(R.string.chart_measurements_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(state.measurements.sortedByDescending { it.measurementEpochDay }, key = { it.id }) { measurement ->
                MeasurementDetailRow(measurement, state, onEditMeasurement)
            }
        }
        item { Spacer(Modifier.height(84.dp)) }
    }
}

@Composable
private fun BbtVicoChart(state: ChartUiState) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val included = state.included
    val excluded = state.excluded
    val seriesKinds = buildList {
        if (included.isNotEmpty()) add(SeriesKind.INCLUDED)
        if (excluded.isNotEmpty()) add(SeriesKind.EXCLUDED)
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val lhColor = MaterialTheme.colorScheme.tertiary
    val mucusColor = MaterialTheme.colorScheme.secondary
    val forecastColor = MaterialTheme.colorScheme.primary
    val periodColor = MaterialTheme.colorScheme.error
    val showProspectiveRanges = state.analysis?.let { analysis ->
        analysis.retrospectiveOvulationRange == null ||
            AnalysisSignal.CONFLICTING_SIGNALS in analysis.signals
    } == true

    val periodWindowDays = state.analysis?.nextPeriodForecast?.expectedStartRange
        ?.takeIf { state.isCurrentCycle }
        ?.let { window ->
            val start = state.cycleDay(window.start)?.toDouble()
            val end = state.cycleDay(window.endInclusive)?.toDouble()
            if (start != null && end != null) start to end else null
        }
    val forecastMaxCycleDay = listOfNotNull(
        periodWindowDays?.second,
        state.analysis?.prospectiveOvulationRange
            ?.endInclusive
            ?.takeIf { state.isCurrentCycle && showProspectiveRanges }
            ?.let(state::cycleDay)
            ?.toDouble(),
        state.analysis?.conceptionOpportunityWindow
            ?.endInclusive
            ?.takeIf { state.isCurrentCycle && showProspectiveRanges }
            ?.let(state::cycleDay)
            ?.toDouble(),
    ).maxOrNull()

    val includedLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(primaryColor)),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(rememberShapeComponent(Fill(primaryColor), CircleShape)),
        ),
    )
    val excludedLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.Point(
                rememberShapeComponent(
                    fill = Fill(surfaceColor),
                    shape = CircleShape,
                    strokeFill = Fill(primaryColor),
                    strokeThickness = 2.dp,
                ),
            ),
        ),
    )
    val lines = seriesKinds.map { kind ->
        key(kind) {
            when (kind) {
                SeriesKind.INCLUDED -> includedLine
                SeriesKind.EXCLUDED -> excludedLine
            }
        }
    }
    val xFormatter = remember {
        CartesianValueFormatter { _, x, _ ->
            x.roundToInt().coerceAtLeast(1).toString()
        }
    }
    val yFormatter = remember { CartesianValueFormatter.decimal(decimalCount = 2, suffix = "°C") }
    val labelBackground = rememberShapeComponent(
        fill = Fill(MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        strokeFill = Fill(MaterialTheme.colorScheme.outline),
        strokeThickness = 1.dp,
    )
    val markerLabel = rememberTextComponent(
        style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, textAlign = TextAlign.Center),
        padding = Insets(8.dp, 5.dp),
        background = labelBackground,
    )
    val marker = rememberDefaultCartesianMarker(
        label = markerLabel,
        valueFormatter = DefaultCartesianMarker.ValueFormatter.default(decimalCount = 2, suffix = "°C", colorCode = false),
        labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
    )
    val decorations = buildList<Decoration> {
        state.analysis?.conceptionOpportunityWindow
            ?.takeIf { state.isCurrentCycle && showProspectiveRanges }
            ?.let { window ->
            val start = state.cycleDay(window.start)?.toDouble()
            val end = state.cycleDay(window.endInclusive)?.toDouble()
            if (start != null && end != null) add(VerticalWindowDecoration(start, end, lhColor.copy(alpha = 0.10f)))
        }
        state.analysis?.prospectiveOvulationRange
            ?.takeIf { state.isCurrentCycle && showProspectiveRanges }
            ?.let { window ->
            val start = state.cycleDay(window.start)?.toDouble()
            val end = state.cycleDay(window.endInclusive)?.toDouble()
            if (start != null && end != null) add(VerticalWindowDecoration(start, end, forecastColor.copy(alpha = 0.13f)))
        }
        state.analysis?.retrospectiveOvulationRange?.let { window ->
            val start = state.cycleDay(window.start)?.toDouble()
            val end = state.cycleDay(window.endInclusive)?.toDouble()
            if (start != null && end != null) add(VerticalWindowDecoration(start, end, mucusColor.copy(alpha = 0.18f)))
        }
        state.analysis?.thermalShift?.baselineCentiC?.let { baseline ->
            val baselineLine = rememberLineComponent(fill = Fill(mucusColor), thickness = 1.dp)
            add(remember(baseline, baselineLine) {
                HorizontalLine(
                    y = { baseline / 100.0 },
                    line = baselineLine,
                    label = { Formatters.temperature(baseline) },
                )
            })
        }
        state.positiveLhDays.forEach { date ->
            state.cycleDay(date)?.let { day ->
                add(VerticalWindowDecoration(day.toDouble(), day.toDouble(), lhColor.copy(alpha = 0.16f)))
            }
        }
        state.fertileMucusDays.forEach { date ->
            state.cycleDay(date)?.let { day ->
                add(VerticalWindowDecoration(day.toDouble(), day.toDouble(), mucusColor.copy(alpha = 0.14f)))
            }
        }
        periodWindowDays?.let { (start, end) ->
            add(VerticalWindowDecoration(start, end, periodColor.copy(alpha = 0.10f)))
        }
    }

    val rangeProvider = remember(forecastMaxCycleDay) { bbtRangeProvider(forecastMaxCycleDay) }
    val initialFocusCycleDay = remember(state.cycle?.id) {
        state.measurements
            .maxByOrNull(TemperatureMeasurement::measurementEpochDay)
            ?.date
            ?.let(state::cycleDay)
            ?: 1
    }
    val scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.Start)
    val focusFraction = remember(initialFocusCycleDay, forecastMaxCycleDay) {
        val displayMaxCycleDay = max(
            initialFocusCycleDay.toDouble(),
            forecastMaxCycleDay ?: initialFocusCycleDay.toDouble(),
        )
        if (displayMaxCycleDay <= 1.0) {
            0f
        } else {
            ((initialFocusCycleDay - 1) / (displayMaxCycleDay - 1)).toFloat().coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(state.cycle?.id, state.measurements, state.observations) {
        modelProducer.runTransaction {
            lineModel {
                seriesKinds.forEach { kind ->
                    when (kind) {
                        SeriesKind.INCLUDED -> series(
                            x = included.map { state.cycleDay(it.date) ?: 1 },
                            y = included.map { it.temperatureCentiC / 100.0 },
                        )
                        SeriesKind.EXCLUDED -> series(
                            x = excluded.map { state.cycleDay(it.date) ?: 1 },
                            y = excluded.map { it.temperatureCentiC / 100.0 },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(state.cycle?.id, initialFocusCycleDay, focusFraction) {
        // The producer receives its first model after the chart is composed. Waiting until Vico
        // has measured a nonzero scroll range avoids consuming the one-shot initial scroll while
        // the model is still empty (which would leave RTL charts at cycle days 1–8).
        snapshotFlow { scrollState.maxValue }.first { it != 0f }
        // Vico's x-based absolute helper assumes an LTR x direction. The chart follows the app's
        // RTL direction, so target the equivalent proportional absolute offset instead.
        scrollState.scroll(Scroll.Absolute.pixels(scrollState.maxValue * focusFraction))
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(lines),
                        pointSpacing = 32.dp,
                        rangeProvider = rangeProvider,
                    ),
                    startAxis = VerticalAxis.rememberStart(valueFormatter = yFormatter),
                    bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = xFormatter, labelRotationDegrees = 0f),
                    decorations = decorations,
                    marker = marker,
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxWidth().height(310.dp).padding(8.dp),
                scrollState = scrollState,
                zoomState = rememberVicoZoomState(),
            )
            Text(
                stringResource(R.string.chart_cycle_day_axis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
        }
    }
}

private class VerticalWindowDecoration(
    private val startX: Double,
    private val endX: Double,
    private val color: Color,
) : Decoration {
    override fun drawUnderLayers(context: CartesianDrawingContext) = with(context) {
        val drawingStart = (if (isLtr) layerBounds.left else layerBounds.right) +
            layoutDirectionMultiplier * layerDimensions.startPadding - scroll
        fun toCanvasX(value: Double): Float = drawingStart +
            layoutDirectionMultiplier * layerDimensions.xSpacing * ((value - ranges.minX) / ranges.xStep).toFloat()
        val start = toCanvasX(startX - 0.5)
        val end = toCanvasX(endX + 0.5)
        canvas.drawRect(
            Rect(min(start, end), layerBounds.top, max(start, end), layerBounds.bottom),
            Paint().apply { this.color = this@VerticalWindowDecoration.color },
        )
    }
}

@Composable
private fun ChartLegend(state: ChartUiState) {
    val showProspectiveRanges = state.analysis?.let { analysis ->
        analysis.retrospectiveOvulationRange == null ||
            AnalysisSignal.CONFLICTING_SIGNALS in analysis.signals
    } == true
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LegendItem(R.string.chart_legend_measured, MaterialTheme.colorScheme.primary, hollow = false, square = false)
        if (state.excluded.isNotEmpty()) LegendItem(R.string.chart_legend_excluded, MaterialTheme.colorScheme.primary, hollow = true, square = false)
        if (state.analysis?.thermalShift?.baselineCentiC != null) LegendItem(R.string.chart_legend_baseline, MaterialTheme.colorScheme.secondary, hollow = false, square = true)
        if (state.isCurrentCycle && state.analysis?.conceptionOpportunityWindow != null &&
            showProspectiveRanges
        ) {
            LegendItem(R.string.chart_legend_conception_window, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), hollow = false, square = true)
        }
        if (state.isCurrentCycle && state.analysis?.prospectiveOvulationRange != null &&
            showProspectiveRanges
        ) {
            LegendItem(R.string.chart_legend_ovulation_forecast, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), hollow = false, square = true)
        }
        if (state.analysis?.retrospectiveOvulationRange != null) LegendItem(R.string.chart_legend_retrospective, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), hollow = false, square = true)
        if (state.positiveLhDays.isNotEmpty()) LegendItem(R.string.chart_legend_lh, MaterialTheme.colorScheme.tertiary, hollow = false, square = false)
        if (state.fertileMucusDays.isNotEmpty()) LegendItem(R.string.chart_legend_mucus, MaterialTheme.colorScheme.secondary, hollow = false, square = true)
        if (state.isCurrentCycle && state.analysis?.nextPeriodForecast != null) LegendItem(R.string.chart_legend_period, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), hollow = false, square = true)
    }
}

@Composable
private fun ChartForecastSummary(
    analysis: com.yv.bbttracker.domain.engine.CycleAnalysisResult,
    isCurrentCycle: Boolean = true,
    sexualContactDays: List<LocalDate> = emptyList(),
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                stringResource(
                    if (isCurrentCycle) R.string.chart_forecast_summary else R.string.chart_retrospective_summary,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            val hasConflict = AnalysisSignal.CONFLICTING_SIGNALS in analysis.signals
            val showProspectiveRanges = analysis.retrospectiveOvulationRange == null || hasConflict
            analysis.conceptionOpportunityWindow?.takeIf { isCurrentCycle && showProspectiveRanges }?.let { range ->
                Text(
                    stringResource(
                        R.string.chart_conception_range,
                        Formatters.dateRange(range),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            analysis.prospectiveOvulationRange?.takeIf { isCurrentCycle && showProspectiveRanges }?.let { range ->
                Text(
                    stringResource(
                        R.string.chart_prospective_range,
                        Formatters.dateRange(range),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            analysis.retrospectiveOvulationRange?.let { range ->
                Text(
                    stringResource(
                        R.string.chart_retrospective_range,
                        Formatters.dateRange(range),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (
                isCurrentCycle && hasConflict &&
                analysis.retrospectiveOvulationRange != null &&
                analysis.prospectiveOvulationRange != null
            ) {
                Text(
                    stringResource(R.string.forecast_conflict_pair_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            analysis.nextPeriodForecast?.takeIf { isCurrentCycle }?.let { forecast ->
                Text(
                    stringResource(
                        R.string.chart_period_range,
                        Formatters.dateRange(forecast.expectedStartRange),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            val contactWindow = analysis.retrospectiveOvulationRange
                ?.let { range -> range.start.minusDays(5)..range.endInclusive }
                ?: analysis.conceptionOpportunityWindow
            contactWindow?.takeIf { isCurrentCycle }?.let { window ->
                val contactDaysInWindow = sexualContactDays.distinct().count { it in window }
                if (contactDaysInWindow > 0) {
                    Text(
                        stringResource(R.string.chart_contact_in_window, contactDaysInWindow),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                stringResource(chartReliabilityResource(analysis.reliability)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stringResource(R.string.short_safety_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun chartReliabilityResource(reliability: ForecastReliability): Int = when (reliability) {
    ForecastReliability.INSUFFICIENT -> R.string.reliability_insufficient
    ForecastReliability.LIMITED -> R.string.reliability_limited
    ForecastReliability.MODERATE -> R.string.reliability_moderate
    ForecastReliability.STRONG -> R.string.reliability_strong
}

@Composable
private fun LegendItem(label: Int, color: Color, hollow: Boolean, square: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            modifier = Modifier.size(12.dp),
            shape = if (square) RoundedCornerShape(2.dp) else CircleShape,
            color = if (hollow) MaterialTheme.colorScheme.surface else color,
            border = if (hollow) BorderStroke(2.dp, color) else null,
        ) {}
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MeasurementDetailRow(
    measurement: TemperatureMeasurement,
    state: ChartUiState,
    onEditMeasurement: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEditMeasurement(measurement.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.cycle_day_short, state.cycleDay(measurement.date) ?: 0),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(Formatters.date(measurement.date), style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.temperature_value, Formatters.temperature(measurement.temperatureCentiC)),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(if (measurement.selectedForAnalysis) R.string.analysis_included else R.string.analysis_excluded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyChart() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Icon(Icons.Outlined.AutoGraph, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.chart_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.chart_empty_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
}
