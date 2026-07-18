package com.yv.bbttracker.domain.engine

import java.time.LocalDate

/**
 * Converts broad internal evidence into the compact estimate shown to the user. The engine keeps
 * the complete daily weight distribution for diagnostics, but the product intentionally presents
 * one best consecutive pair prospectively and one best-supported day retrospectively.
 */
internal object FocusedEstimateSelector {
    fun bestTwoDayRange(
        weights: Map<LocalDate, Double>,
        allowedRange: ClosedRange<LocalDate>? = null,
    ): ClosedRange<LocalDate>? {
        val candidates = weights
            .asSequence()
            .filter { (date, weight) ->
                weight.isFinite() && weight > 0.0 && (allowedRange == null || date in allowedRange)
            }
            .sortedBy { it.key }
            .toList()
        if (candidates.isEmpty()) return null

        val byDate = candidates.associate { it.key to it.value }
        val validStarts = candidates.filter { entry ->
            val next = entry.key.plusDays(1)
            next in byDate && (allowedRange == null || next in allowedRange)
        }
        val first = validStarts.maxWithOrNull(
            compareBy<Map.Entry<LocalDate, Double>> { entry ->
                entry.value + byDate.getValue(entry.key.plusDays(1))
            }.thenByDescending { it.key.toEpochDay() },
        )?.key ?: return null
        return first..first.plusDays(1)
    }

    /**
     * Positive urinary LH most often points to ovulation over the following two days. If a surge
     * remains positive longer, keep the estimate actionable without painting the whole episode.
     */
    fun lhProspectiveRange(episode: LhEpisode, currentDate: LocalDate): ClosedRange<LocalDate> {
        val firstExpected = episode.firstPositiveDate.plusDays(1)
        val firstShown = maxOf(firstExpected, currentDate)
        return firstShown..firstShown.plusDays(1)
    }

    fun retrospectiveDay(
        thermalShift: ThermalShiftResult?,
        lhEpisode: LhEpisode?,
        mucusPeakDate: LocalDate?,
    ): LocalDate? {
        val thermal = thermalShift?.takeIf(ThermalShiftResult::isConfirmed)
        if (thermal == null && lhEpisode == null && mucusPeakDate == null) return null

        val candidates = linkedSetOf<LocalDate>().apply {
            thermal?.estimatedOvulationRange?.dates()?.let(::addAll)
            lhEpisode?.estimatedOvulationRange?.dates()?.let(::addAll)
            mucusPeakDate?.let { peak -> addAll((peak.minusDays(1)..peak.plusDays(1)).dates()) }
        }
        return candidates.maxWithOrNull(
            compareBy<LocalDate> { date ->
                var score = 0
                thermal?.let { shift ->
                    val range = shift.estimatedOvulationRange
                        ?: shift.firstHighDate.minusDays(2)..shift.firstHighDate
                    if (date in range) score += 6
                    if (date == shift.firstHighDate.minusDays(1)) score += 6
                }
                lhEpisode?.let { episode ->
                    if (date in episode.estimatedOvulationRange) score += 3
                    if (date == episode.firstPositiveDate.plusDays(1)) score += 3
                }
                mucusPeakDate?.let { peak ->
                    if (date in peak.minusDays(1)..peak.plusDays(1)) score += 3
                    if (date == peak) score += 4
                }
                score
            }.thenByDescending(LocalDate::toEpochDay),
        )
    }

    private fun ClosedRange<LocalDate>.dates(): List<LocalDate> = buildList {
        var date = start
        while (!date.isAfter(endInclusive)) {
            add(date)
            date = date.plusDays(1)
        }
    }
}
