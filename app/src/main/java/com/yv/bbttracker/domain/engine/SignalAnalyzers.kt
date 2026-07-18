package com.yv.bbttracker.domain.engine

import com.yv.bbttracker.domain.model.CervicalMucus
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.MucusSensation
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal data class LhEpisode(
    val firstPositiveDate: LocalDate,
    val lastPositiveDate: LocalDate,
    val positiveDates: List<LocalDate>,
) {
    val estimatedOvulationRange: ClosedRange<LocalDate>
        get() = firstPositiveDate.plusDays(1)..firstPositiveDate.plusDays(2)
}

internal data class LhSignalSummary(
    val episodes: List<LhEpisode>,
    val activeEpisode: LhEpisode?,
    val positiveToday: Boolean,
    val recentBorderlineDate: LocalDate?,
) {
    val latestEpisode: LhEpisode? get() = episodes.lastOrNull()
}

internal object LhSignalAnalyzer {
    private const val MAX_POSITIVE_EPISODE_GAP_DAYS = 3L
    private const val ACTIVE_SURGE_DAYS = 2L

    fun analyze(observations: List<DailyObservation>, asOfDate: LocalDate): LhSignalSummary {
        val latestByDay = observations
            .asSequence()
            .filter { !it.date.isAfter(asOfDate) }
            .groupBy { it.epochDay }
            .mapValues { (_, sameDay) -> sameDay.maxBy { it.updatedAtEpochMillis } }
            .values
            .sortedBy { it.epochDay }

        val positives = latestByDay.filter { it.lhResult == LhResult.POSITIVE }
        val episodes = mutableListOf<MutableList<DailyObservation>>()
        positives.forEach { positive ->
            val current = episodes.lastOrNull()
            val previous = current?.lastOrNull()
            val separatedByNegative = previous != null && latestByDay.any { observation ->
                observation.date.isAfter(previous.date) &&
                    observation.date.isBefore(positive.date) &&
                    observation.lhResult == LhResult.NEGATIVE
            }
            val gap = previous?.let { ChronoUnit.DAYS.between(it.date, positive.date) }
            if (current == null || gap == null || gap > MAX_POSITIVE_EPISODE_GAP_DAYS || separatedByNegative) {
                episodes += mutableListOf(positive)
            } else {
                current += positive
            }
        }

        val immutableEpisodes = episodes.map { episode ->
            val dates = episode.map { it.date }
            LhEpisode(
                firstPositiveDate = dates.first(),
                lastPositiveDate = dates.last(),
                positiveDates = dates,
            )
        }
        val active = immutableEpisodes.lastOrNull()?.takeIf { episode ->
            ChronoUnit.DAYS.between(episode.firstPositiveDate, asOfDate) in 0L..ACTIVE_SURGE_DAYS
        }
        val recentBorderline = latestByDay
            .lastOrNull { observation ->
                observation.lhResult == LhResult.BORDERLINE &&
                    ChronoUnit.DAYS.between(observation.date, asOfDate) in 0L..1L
            }
            ?.date

        return LhSignalSummary(
            episodes = immutableEpisodes,
            activeEpisode = active,
            positiveToday = latestByDay.any { observation ->
                observation.date == asOfDate && observation.lhResult == LhResult.POSITIVE
            },
            recentBorderlineDate = recentBorderline,
        )
    }
}

internal data class MucusSignalSummary(
    val fertileToday: Boolean,
    val rising: Boolean,
    val latestFertileDate: LocalDate?,
    val peakDate: LocalDate?,
    val todayQuality: Int?,
)

internal object MucusSignalAnalyzer {
    private const val FERTILE_QUALITY = 3

    fun analyze(observations: List<DailyObservation>, asOfDate: LocalDate): MucusSignalSummary {
        val usable = observations
            .asSequence()
            .filter { !it.date.isAfter(asOfDate) }
            .groupBy { it.epochDay }
            .mapValues { (_, sameDay) -> sameDay.maxBy { it.updatedAtEpochMillis } }
            .values
            .sortedBy { it.epochDay }
            .filterNot { it.mucusObscured }
            .mapNotNull { observation -> quality(observation)?.let { quality -> observation to quality } }

        val today = usable.lastOrNull { (observation, _) -> observation.date == asOfDate }
        val recent = usable.takeLast(2)
        val rising = recent.size == 2 &&
            recent[1].first.date == asOfDate &&
            ChronoUnit.DAYS.between(recent[0].first.date, recent[1].first.date) == 1L &&
            recent[1].second > recent[0].second &&
            recent[1].second >= 2

        var lastFertileInRun: LocalDate? = null
        var completedPeak: LocalDate? = null
        var previousUsableDate: LocalDate? = null
        usable.forEach { (observation, quality) ->
            if (previousUsableDate?.let { ChronoUnit.DAYS.between(it, observation.date) > 1L } == true) {
                // A missing day makes continuity unknowable; do not infer a rise or a completed
                // peak across the gap.
                lastFertileInRun = null
            }
            if (quality >= FERTILE_QUALITY) {
                // A later fertile wave supersedes an older completed peak. It becomes eligible as
                // the new peak only after a contiguous lower-quality observation follows.
                if (completedPeak != null && observation.date.isAfter(completedPeak)) {
                    completedPeak = null
                }
                // The peak day is the last peak-type day before a subsequent decline.
                lastFertileInRun = observation.date
            } else if (lastFertileInRun != null) {
                completedPeak = lastFertileInRun
                lastFertileInRun = null
            }
            previousUsableDate = observation.date
        }

        return MucusSignalSummary(
            fertileToday = today?.second?.let { it >= FERTILE_QUALITY } == true,
            rising = rising,
            latestFertileDate = usable.lastOrNull { it.second >= FERTILE_QUALITY }?.first?.date,
            // A new open fertile wave invalidates an older completed peak as the cycle's current
            // retrospective marker until a new decline is observed.
            peakDate = completedPeak.takeIf { lastFertileInRun == null },
            todayQuality = today?.second,
        )
    }

    fun quality(observation: DailyObservation): Int? {
        if (observation.mucus == CervicalMucus.NOT_CHECKED &&
            observation.mucusSensation == MucusSensation.NOT_CHECKED
        ) return null

        val appearance = when (observation.mucus) {
            CervicalMucus.NOT_CHECKED -> 0
            CervicalMucus.DRY -> 0
            CervicalMucus.STICKY -> 1
            CervicalMucus.CREAMY -> 2
            CervicalMucus.WATERY -> 3
            CervicalMucus.EGG_WHITE -> 4
        }
        val sensation = when (observation.mucusSensation) {
            MucusSensation.NOT_CHECKED -> 0
            MucusSensation.DRY -> 0
            MucusSensation.DAMP -> 1
            MucusSensation.WET -> 3
            MucusSensation.SLIPPERY -> 4
        }
        return maxOf(appearance, sensation)
    }
}

internal fun ClosedRange<LocalDate>.intersects(other: ClosedRange<LocalDate>): Boolean =
    !endInclusive.isBefore(other.start) && !other.endInclusive.isBefore(start)

internal fun ClosedRange<LocalDate>.intersection(other: ClosedRange<LocalDate>): ClosedRange<LocalDate>? =
    if (intersects(other)) maxOf(start, other.start)..minOf(endInclusive, other.endInclusive) else null
