package com.yv.bbttracker.feature.today

import com.yv.bbttracker.domain.engine.AnalysisSignal
import com.yv.bbttracker.domain.engine.NextAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayAnalysisPresentationTest {
    @Test
    fun `forecast basis shows contributing signals in useful order`() {
        val signals = setOf(
            AnalysisSignal.SELF_REPORTED_CYCLE_LENGTH,
            AnalysisSignal.FERTILE_MUCUS,
            AnalysisSignal.LH_SURGE,
            AnalysisSignal.UNRELIABLE_TEMPERATURES_EXCLUDED,
        )

        assertEquals(
            listOf(
                AnalysisSignal.LH_SURGE,
                AnalysisSignal.FERTILE_MUCUS,
                AnalysisSignal.SELF_REPORTED_CYCLE_LENGTH,
            ),
            visibleForecastBasisSignals(signals),
        )
    }

    @Test
    fun `excluded measurements and conflicts are cautions rather than forecast bases`() {
        val signals = setOf(
            AnalysisSignal.CONFLICTING_SIGNALS,
            AnalysisSignal.UNRELIABLE_TEMPERATURES_EXCLUDED,
        )

        assertEquals(emptyList<AnalysisSignal>(), visibleForecastBasisSignals(signals))
        assertEquals(
            listOf(
                AnalysisSignal.CONFLICTING_SIGNALS,
                AnalysisSignal.UNRELIABLE_TEMPERATURES_EXCLUDED,
            ),
            visibleForecastCautionSignals(signals),
        )
    }

    @Test
    fun `generic tracking is hidden while genuinely actionable guidance remains`() {
        assertNull(visibleNextAction(null))
        assertNull(visibleNextAction(NextAction.CONTINUE_DAILY_TRACKING))
        assertEquals(
            NextAction.REPEAT_LH_TEST,
            visibleNextAction(NextAction.REPEAT_LH_TEST),
        )
        assertEquals(
            NextAction.AWAIT_THERMAL_CONFIRMATION,
            visibleNextAction(NextAction.AWAIT_THERMAL_CONFIRMATION),
        )
    }
}
