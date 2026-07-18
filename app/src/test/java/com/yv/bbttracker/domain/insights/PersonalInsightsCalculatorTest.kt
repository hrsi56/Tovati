package com.yv.bbttracker.domain.insights

import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MoodFlag
import com.yv.bbttracker.domain.model.PhysicalSymptomFlag
import com.yv.bbttracker.domain.model.SexualContact
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalInsightsCalculatorTest {
    private val today = LocalDate.of(2026, 7, 18)
    private val currentCycle = cycle(3, today.minusDays(11), null)

    @Test
    fun `initiation percentage uses only explicitly attributed sexual contact`() {
        val observations = listOf(
            observation(today.minusDays(1), contact = SexualContact.YES, initiated = false),
            observation(today.minusDays(2), contact = SexualContact.YES, initiated = false),
            observation(today.minusDays(3), contact = SexualContact.YES, initiated = true),
            observation(today.minusDays(4), contact = SexualContact.YES, initiated = null),
            observation(today.minusDays(5), contact = SexualContact.NONE, initiated = null),
        )

        val result = calculate(observations)

        assertTrue(PersonalInsight.LowInitiationRate(33) in result)
    }

    @Test
    fun `initiation insight waits for enough attributed entries`() {
        val observations = listOf(
            observation(today.minusDays(1), contact = SexualContact.YES, initiated = false),
            observation(today.minusDays(2), contact = SexualContact.YES, initiated = false),
        )

        assertFalse(calculate(observations).any { it is PersonalInsight.LowInitiationRate })
    }

    @Test
    fun `masturbation is not interpreted as an ordinal desire score`() {
        val observations = listOf(
            observation(today, libido = LibidoLevel.MASTURBATED, contact = SexualContact.NONE),
            observation(today.minusDays(1), libido = LibidoLevel.MASTURBATED, contact = SexualContact.NONE),
            observation(today.minusDays(2), libido = LibidoLevel.MASTURBATED, contact = SexualContact.NONE),
        )

        assertFalse(calculate(observations).any { it is PersonalInsight.HighDesireLowContact })
    }

    @Test
    fun `recent high desire and low contact are detected from recorded values`() {
        val observations = listOf(
            observation(today, libido = LibidoLevel.HIGH, contact = SexualContact.NONE),
            observation(today.minusDays(1), libido = LibidoLevel.VERY_HIGH, contact = SexualContact.NONE),
            observation(today.minusDays(2), libido = LibidoLevel.HIGH, contact = SexualContact.SOME),
        )

        assertTrue(PersonalInsight.HighDesireLowContact in calculate(observations))
    }

    @Test
    fun `low mood requires at least three scored days`() {
        val observations = listOf(
            observation(today, mood = MoodFlag.LOW),
            observation(today.minusDays(1), mood = MoodFlag.SAD),
            observation(today.minusDays(2), mood = MoodFlag.ANXIOUS),
        )

        assertTrue(PersonalInsight.LowMood in calculate(observations))
        assertFalse(
            calculate(observations.take(2)).any { it is PersonalInsight.LowMood },
        )
    }

    @Test
    fun `repeated recent symptom is compared with equivalent cycle week`() {
        val previousOne = cycle(1, currentCycle.startDate.minusDays(56), 28)
        val previousTwo = cycle(2, currentCycle.startDate.minusDays(28), 28)
        val observations = listOf(
            observation(today, symptom = PhysicalSymptomFlag.HEADACHE),
            observation(today.minusDays(2), symptom = PhysicalSymptomFlag.HEADACHE),
            cycleDayObservation(previousOne, 10, PhysicalSymptomFlag.HEADACHE),
            cycleDayObservation(previousTwo, 9, PhysicalSymptomFlag.HEADACHE),
        )

        val result = PersonalInsightsCalculator.calculate(
            today = today,
            currentCycle = currentCycle,
            cycles = listOf(previousOne, previousTwo, currentCycle),
            observations = observations,
        )

        assertTrue(PersonalInsight.RecurringSymptom(PhysicalSymptomFlag.HEADACHE, 100) in result)
    }

    @Test
    fun `pain relief comparison ignores days that were not reported`() {
        val previousOne = cycle(1, currentCycle.startDate.minusDays(56), 28)
        val previousTwo = cycle(2, currentCycle.startDate.minusDays(28), 28)
        val observations = listOf(
            painObservation(currentCycle, 10, 2),
            painObservation(currentCycle, 11, 2),
            painObservation(currentCycle, 12, 2),
            painObservation(previousOne, 10, 1),
            painObservation(previousOne, 11, 1),
            painObservation(previousOne, 12, 1),
            painObservation(previousTwo, 10, 1),
            painObservation(previousTwo, 11, 1),
            painObservation(previousTwo, 12, 1),
        )

        val result = PersonalInsightsCalculator.calculate(
            today = today,
            currentCycle = currentCycle,
            cycles = listOf(previousOne, previousTwo, currentCycle),
            observations = observations,
        )

        assertTrue(PersonalInsight.HigherPainReliefUse in result)
    }

    @Test
    fun `one standard deviation fewer pain relief pills is recognized`() {
        val previousOne = cycle(1, currentCycle.startDate.minusDays(56), 28)
        val previousTwo = cycle(2, currentCycle.startDate.minusDays(28), 28)
        val observations = buildList {
            for (day in 10..12) add(painObservation(currentCycle, day, 0))
            for (day in 10..12) add(painObservation(previousOne, day, 1))
            for (day in 10..12) add(painObservation(previousTwo, day, 3))
        }

        val result = PersonalInsightsCalculator.calculate(
            today = today,
            currentCycle = currentCycle,
            cycles = listOf(previousOne, previousTwo, currentCycle),
            observations = observations,
        )

        assertTrue(PersonalInsight.LowerPainReliefUse in result)
    }

    @Test
    fun `a five day observation streak gets a positive insight`() {
        val observations = (0L..4L).map { observation(today.minusDays(it)) }

        assertEquals(
            PersonalInsight.TrackingStreak(5),
            calculate(observations).single(),
        )
    }

    private fun calculate(observations: List<DailyObservation>) =
        PersonalInsightsCalculator.calculate(
            today = today,
            currentCycle = currentCycle,
            cycles = listOf(currentCycle),
            observations = observations,
        )

    private fun cycle(id: Long, start: LocalDate, length: Int?) = Cycle(
        id = id,
        startEpochDay = start.toEpochDay(),
        endEpochDay = length?.let { start.plusDays(it.toLong() - 1).toEpochDay() },
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private fun cycleDayObservation(cycle: Cycle, day: Int, symptom: Long) =
        observation(cycle.startDate.plusDays(day.toLong() - 1), symptom = symptom)

    private fun painObservation(cycle: Cycle, day: Int, count: Int) =
        observation(
            date = cycle.startDate.plusDays(day.toLong() - 1),
            pillCount = count,
        )

    private fun observation(
        date: LocalDate,
        libido: LibidoLevel = LibidoLevel.NOT_RECORDED,
        contact: SexualContact = SexualContact.NOT_RECORDED,
        initiated: Boolean? = null,
        mood: Long = 0,
        symptom: Long = 0,
        pillCount: Int? = null,
    ) = DailyObservation(
        epochDay = date.toEpochDay(),
        libidoLevel = libido,
        sexualContact = contact,
        sexualContactInitiatedByUser = initiated,
        moodMask = mood,
        physicalSymptomMask = symptom,
        painReliefPillCount = pillCount,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}
