package com.yv.bbttracker.feature.entry

import com.yv.bbttracker.domain.model.BleedingLevel
import com.yv.bbttracker.domain.model.Cycle
import com.yv.bbttracker.domain.model.DailyObservation
import com.yv.bbttracker.domain.model.LhResult
import com.yv.bbttracker.domain.model.LibidoLevel
import com.yv.bbttracker.domain.model.MoodFlag
import com.yv.bbttracker.domain.model.ObservationInput
import com.yv.bbttracker.domain.model.OvulationPain
import com.yv.bbttracker.domain.model.PhysicalSymptomFlag
import com.yv.bbttracker.domain.model.SexualContact
import com.yv.bbttracker.domain.repository.CycleRepository
import com.yv.bbttracker.domain.repository.ObservationRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObservationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `new observation distinguishes not recorded from explicit none`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(BleedingLevel.NOT_RECORDED, viewModel.state.value.bleeding)
        assertEquals(OvulationPain.NOT_RECORDED, viewModel.state.value.ovulationPain)
    }

    @Test
    fun `selecting not tested clears all optional LH metadata before save`() = runTest(dispatcher) {
        val observations = FakeObservationRepository()
        val viewModel = viewModel(observations)
        advanceUntilIdle()

        viewModel.onEvent(ObservationEvent.LhChanged(LhResult.POSITIVE))
        viewModel.onEvent(ObservationEvent.LhTestTimeChanged(13 * 60 + 20))
        viewModel.onEvent(ObservationEvent.LhTestBrandChanged("Example"))
        viewModel.onEvent(ObservationEvent.LhTestSensitivityChanged("25"))
        viewModel.onEvent(ObservationEvent.LhChanged(LhResult.NOT_TESTED))
        viewModel.onEvent(ObservationEvent.Save)
        advanceUntilIdle()

        val saved = requireNotNull(observations.saved)
        assertEquals(LhResult.NOT_TESTED, saved.lhResult)
        assertNull(saved.lhTestMinutesOfDay)
        assertTrue(saved.lhTestBrand.isNullOrBlank())
        assertNull(saved.lhTestSensitivityMilliIu)
    }

    @Test
    fun `out of range sensitivity blocks save until corrected`() = runTest(dispatcher) {
        val observations = FakeObservationRepository()
        val viewModel = viewModel(observations)
        advanceUntilIdle()

        viewModel.onEvent(ObservationEvent.LhChanged(LhResult.BORDERLINE))
        viewModel.onEvent(ObservationEvent.LhTestSensitivityChanged("101"))
        viewModel.onEvent(ObservationEvent.Save)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.lhSensitivityInvalid)
        assertNull(observations.saved)

        viewModel.onEvent(ObservationEvent.LhTestSensitivityChanged("20"))
        viewModel.onEvent(ObservationEvent.Save)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.lhSensitivityInvalid)
        assertEquals(20, observations.saved?.lhTestSensitivityMilliIu)
    }

    @Test
    fun `wellbeing intimacy and physical symptoms are saved together`() = runTest(dispatcher) {
        val observations = FakeObservationRepository()
        val viewModel = viewModel(observations)
        advanceUntilIdle()

        viewModel.onEvent(ObservationEvent.MoodToggled(MoodFlag.HAPPY))
        viewModel.onEvent(ObservationEvent.MoodToggled(MoodFlag.ENERGETIC))
        viewModel.onEvent(ObservationEvent.MoodNoteChanged("יום רגוע ונעים"))
        viewModel.onEvent(ObservationEvent.LibidoChanged(LibidoLevel.MASTURBATED))
        viewModel.onEvent(ObservationEvent.SexualContactChanged(SexualContact.YES))
        viewModel.onEvent(ObservationEvent.SexualContactInitiatedChanged(true))
        viewModel.onEvent(ObservationEvent.PhysicalSymptomToggled(PhysicalSymptomFlag.BLOATING))
        viewModel.onEvent(ObservationEvent.PhysicalSymptomToggled(PhysicalSymptomFlag.HEADACHE))
        viewModel.onEvent(ObservationEvent.PainReliefPillCountChanged(2))
        viewModel.onEvent(ObservationEvent.PainReliefMedicationNoteChanged("איבופרופן"))
        viewModel.onEvent(ObservationEvent.Save)
        advanceUntilIdle()

        val saved = requireNotNull(observations.saved)
        assertEquals(MoodFlag.HAPPY or MoodFlag.ENERGETIC, saved.moodMask)
        assertEquals("יום רגוע ונעים", saved.moodNote)
        assertEquals(LibidoLevel.MASTURBATED, saved.libidoLevel)
        assertEquals(SexualContact.YES, saved.sexualContact)
        assertEquals(true, saved.sexualContactInitiatedByUser)
        assertEquals(
            PhysicalSymptomFlag.BLOATING or PhysicalSymptomFlag.HEADACHE,
            saved.physicalSymptomMask,
        )
        assertEquals(2, saved.painReliefPillCount)
        assertEquals("איבופרופן", saved.painReliefMedicationNote)
    }

    @Test
    fun `dependent intimacy and medication details clear when their parent answer changes`() =
        runTest(dispatcher) {
            val observations = FakeObservationRepository()
            val viewModel = viewModel(observations)
            advanceUntilIdle()

            viewModel.onEvent(ObservationEvent.SexualContactChanged(SexualContact.YES))
            viewModel.onEvent(ObservationEvent.SexualContactInitiatedChanged(true))
            viewModel.onEvent(ObservationEvent.SexualContactChanged(SexualContact.NONE))
            viewModel.onEvent(ObservationEvent.PainReliefPillCountChanged(3))
            viewModel.onEvent(ObservationEvent.PainReliefMedicationNoteChanged("אקמול"))
            viewModel.onEvent(ObservationEvent.PainReliefPillCountChanged(0))
            viewModel.onEvent(ObservationEvent.Save)
            advanceUntilIdle()

            val saved = requireNotNull(observations.saved)
            assertNull(saved.sexualContactInitiatedByUser)
            assertEquals(0, saved.painReliefPillCount)
            assertTrue(saved.painReliefMedicationNote.isNullOrBlank())
        }

    private fun viewModel(
        observations: FakeObservationRepository = FakeObservationRepository(),
    ) = ObservationViewModel(
        date = LocalDate.ofEpochDay(20_000),
        suggestCycleStart = false,
        observationRepository = observations,
        cycleRepository = EmptyCycleRepository,
    )

    private class FakeObservationRepository : ObservationRepository {
        private val value = MutableStateFlow<DailyObservation?>(null)
        var saved: ObservationInput? = null

        override fun observeObservation(date: LocalDate): Flow<DailyObservation?> = value
        override fun observeObservations(start: LocalDate, end: LocalDate): Flow<List<DailyObservation>> =
            MutableStateFlow(emptyList())

        override fun observeAllObservations(): Flow<List<DailyObservation>> = MutableStateFlow(emptyList())
        override suspend fun getAllObservations(): List<DailyObservation> = emptyList()
        override suspend fun upsertObservation(input: ObservationInput, startCycle: Boolean): Result<Unit> {
            saved = input
            return Result.success(Unit)
        }
    }

    private object EmptyCycleRepository : CycleRepository {
        override fun observeCurrentCycle(): Flow<Cycle?> = MutableStateFlow(null)
        override fun observeCycles(): Flow<List<Cycle>> = MutableStateFlow(emptyList())
        override suspend fun getCycles(): List<Cycle> = emptyList()
        override suspend fun startCycle(date: LocalDate, note: String?): Result<Long> = Result.success(1)
        override suspend fun updateCycleStart(cycleId: Long, date: LocalDate): Result<Unit> = Result.success(Unit)
        override suspend fun deleteCycle(cycleId: Long): Result<Unit> = Result.success(Unit)
    }
}
