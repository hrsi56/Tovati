package com.yv.bbttracker.domain.insights

import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MoodFlag
import com.yv.bbttracker.domain.model.PhysicalSymptomFlag
import com.yv.bbttracker.domain.model.SexualContact
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

sealed interface PersonalInsight {
    data class LowInitiationRate(val percent: Int) : PersonalInsight
    data class RecurringSymptom(val symptomFlag: Long, val percent: Int) : PersonalInsight
    data object ContactWithoutDesire : PersonalInsight
    data object HighDesireLowContact : PersonalInsight
    data object LowMood : PersonalInsight
    data object HigherPainReliefUse : PersonalInsight
    data object LowerPainReliefUse : PersonalInsight
    data class TrackingStreak(val days: Int) : PersonalInsight
}

/**
 * Finds small personal patterns in locally stored observations.
 *
 * Missing values are never treated as zero, and each comparison has a minimum sample size so an
 * isolated entry does not become a broad conclusion. These insights are descriptive only and do
 * not feed the fertility prediction engine.
 */
object PersonalInsightsCalculator {
    private const val RECENT_DAYS = 7L
    private const val INITIATION_LOOKBACK_DAYS = 90L
    private const val MIN_RECENT_SAMPLES = 3
    private const val MIN_INITIATION_SAMPLES = 3
    private const val MIN_COMPARABLE_CYCLES = 2
    private const val MAX_VISIBLE_INSIGHTS = 3

    fun calculate(
        today: LocalDate,
        currentCycle: Cycle?,
        cycles: List<Cycle>,
        observations: List<DailyObservation>,
    ): List<PersonalInsight> {
        val recentStart = today.minusDays(RECENT_DAYS - 1)
        val recent = observations.filter { it.date in recentStart..today }
        val candidates = buildList {
            contactWithoutDesire(recent)?.let(::add)
            lowMood(recent)?.let(::add)
            highDesireLowContact(recent)?.let(::add)
            painReliefComparison(today, currentCycle, cycles, observations)?.let(::add)
            recurringSymptom(today, currentCycle, cycles, observations)?.let(::add)
            lowInitiationRate(today, observations)?.let(::add)
            trackingStreak(today, observations)?.let(::add)
        }
        return candidates.take(MAX_VISIBLE_INSIGHTS)
    }

    private fun lowInitiationRate(
        today: LocalDate,
        observations: List<DailyObservation>,
    ): PersonalInsight.LowInitiationRate? {
        val lookbackStart = today.minusDays(INITIATION_LOOKBACK_DAYS - 1)
        val explicitlyAttributed = observations.filter {
            it.date in lookbackStart..today &&
                it.sexualContact == SexualContact.YES &&
                it.sexualContactInitiatedByUser != null
        }
        if (explicitlyAttributed.size < MIN_INITIATION_SAMPLES) return null
        val initiatedCount = explicitlyAttributed.count { it.sexualContactInitiatedByUser == true }
        val percent = (initiatedCount * 100.0 / explicitlyAttributed.size).roundToInt()
        return percent.takeIf { it < 37 }?.let(PersonalInsight::LowInitiationRate)
    }

    private fun contactWithoutDesire(
        recent: List<DailyObservation>,
    ): PersonalInsight.ContactWithoutDesire? =
        PersonalInsight.ContactWithoutDesire.takeIf {
            recent.any {
                it.libidoLevel == LibidoLevel.VERY_LOW &&
                    it.sexualContact in setOf(SexualContact.SOME, SexualContact.YES)
            }
        }

    private fun highDesireLowContact(
        recent: List<DailyObservation>,
    ): PersonalInsight.HighDesireLowContact? {
        val desireScores = recent.mapNotNull { it.libidoLevel.desireScore() }
        val contactScores = recent.mapNotNull { it.sexualContact.contactScore() }
        if (desireScores.size < MIN_RECENT_SAMPLES || contactScores.size < MIN_RECENT_SAMPLES) {
            return null
        }
        return PersonalInsight.HighDesireLowContact.takeIf {
            desireScores.average() > 0.5 && contactScores.average() < 0.5
        }
    }

    private fun lowMood(recent: List<DailyObservation>): PersonalInsight.LowMood? {
        val moodScores = recent.mapNotNull { it.moodScore() }
        if (moodScores.size < MIN_RECENT_SAMPLES) return null
        return PersonalInsight.LowMood.takeIf { moodScores.average() < 0.5 }
    }

    private fun recurringSymptom(
        today: LocalDate,
        currentCycle: Cycle?,
        cycles: List<Cycle>,
        observations: List<DailyObservation>,
    ): PersonalInsight.RecurringSymptom? {
        val cycle = currentCycle ?: return null
        val currentCycleDay = (today.toEpochDay() - cycle.startEpochDay + 1).toInt()
        if (currentCycleDay < 1) return null

        val recentStart = today.minusDays(RECENT_DAYS - 1)
        val recent = observations.filter { it.date in recentStart..today }
        val repeatedFlags = PhysicalSymptomFlag.all.filter { flag ->
            recent.count { it.physicalSymptomMask and flag != 0L } > 1
        }
        if (repeatedFlags.isEmpty()) return null

        val cycleWeekStart = ((currentCycleDay - 1) / 7) * 7 + 1
        val cycleWeekEnd = cycleWeekStart + 6
        val priorCycles = cycles.filter { it.id != cycle.id && it.startEpochDay < cycle.startEpochDay }
        val observationsByCycle = priorCycles.mapNotNull { previousCycle ->
            val inEquivalentWeek = observations.filter { observation ->
                val day = (observation.epochDay - previousCycle.startEpochDay + 1).toInt()
                previousCycle.contains(observation.date) && day in cycleWeekStart..cycleWeekEnd
            }
            inEquivalentWeek.takeIf { it.isNotEmpty() }
        }
        if (observationsByCycle.size < MIN_COMPARABLE_CYCLES) return null

        return repeatedFlags.mapNotNull { flag ->
            val cyclesWithSymptom = observationsByCycle.count { cycleObservations ->
                cycleObservations.any { it.physicalSymptomMask and flag != 0L }
            }
            val percent = (cyclesWithSymptom * 100.0 / observationsByCycle.size).roundToInt()
            percent.takeIf { it > 33 }?.let {
                PersonalInsight.RecurringSymptom(flag, it)
            }
        }.maxByOrNull { it.percent }
    }

    private fun painReliefComparison(
        today: LocalDate,
        currentCycle: Cycle?,
        cycles: List<Cycle>,
        observations: List<DailyObservation>,
    ): PersonalInsight? {
        val cycle = currentCycle ?: return null
        val currentCycleDay = (today.toEpochDay() - cycle.startEpochDay + 1).toInt()
        if (currentCycleDay < 1) return null
        val rangeEnd = currentCycleDay
        val rangeStart = (rangeEnd - 6).coerceAtLeast(1)

        val currentValues = observations.mapNotNull { observation ->
            val day = (observation.epochDay - cycle.startEpochDay + 1).toInt()
            observation.painReliefPillCount?.takeIf {
                cycle.contains(observation.date) && day in rangeStart..rangeEnd
            }
        }
        if (currentValues.size < MIN_RECENT_SAMPLES) return null

        val historicalMeans = cycles
            .asSequence()
            .filter { it.id != cycle.id && it.startEpochDay < cycle.startEpochDay }
            .mapNotNull { previousCycle ->
                val values = observations.mapNotNull { observation ->
                    val day = (observation.epochDay - previousCycle.startEpochDay + 1).toInt()
                    observation.painReliefPillCount?.takeIf {
                        previousCycle.contains(observation.date) && day in rangeStart..rangeEnd
                    }
                }
                values.takeIf { it.size >= MIN_RECENT_SAMPLES }?.average()
            }
            .toList()
        if (historicalMeans.size < MIN_COMPARABLE_CYCLES) return null

        val currentMean = currentValues.average()
        val historicalMean = historicalMeans.average()
        val standardDeviation = sqrt(
            historicalMeans.sumOf { (it - historicalMean) * (it - historicalMean) } /
                historicalMeans.size,
        )
        return when {
            currentMean > historicalMean -> PersonalInsight.HigherPainReliefUse
            standardDeviation > 0.0 && currentMean <= historicalMean - standardDeviation ->
                PersonalInsight.LowerPainReliefUse
            else -> null
        }
    }

    private fun trackingStreak(
        today: LocalDate,
        observations: List<DailyObservation>,
    ): PersonalInsight.TrackingStreak? {
        val recordedDays = observations.asSequence().map { it.epochDay }.toHashSet()
        var cursor = today.toEpochDay()
        var streak = 0
        while (cursor in recordedDays) {
            streak += 1
            cursor -= 1
        }
        return streak.takeIf { it >= 5 }?.let(PersonalInsight::TrackingStreak)
    }

    private fun LibidoLevel.desireScore(): Double? = when (this) {
        LibidoLevel.NOT_RECORDED, LibidoLevel.MASTURBATED -> null
        LibidoLevel.VERY_LOW -> 0.0
        LibidoLevel.LOW -> 1.0 / 3.0
        LibidoLevel.MEDIUM -> 2.0 / 3.0
        LibidoLevel.HIGH -> 1.0
        LibidoLevel.VERY_HIGH -> 1.0
    }

    private fun SexualContact.contactScore(): Double? = when (this) {
        SexualContact.NOT_RECORDED -> null
        SexualContact.NONE -> 0.0
        SexualContact.SOME -> 0.5
        SexualContact.YES -> 1.0
    }

    private fun DailyObservation.moodScore(): Double? {
        val positiveFlags = listOf(MoodFlag.CALM, MoodFlag.HAPPY, MoodFlag.ENERGETIC)
        val negativeFlags = listOf(MoodFlag.IRRITABLE, MoodFlag.ANXIOUS, MoodFlag.SAD, MoodFlag.LOW)
        val positiveCount = positiveFlags.count { moodMask and it != 0L }
        val negativeCount = negativeFlags.count { moodMask and it != 0L }
        val scoredCount = positiveCount + negativeCount
        return if (scoredCount == 0) null else positiveCount.toDouble() / scoredCount
    }
}
