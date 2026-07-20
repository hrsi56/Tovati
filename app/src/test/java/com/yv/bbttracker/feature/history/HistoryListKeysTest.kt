package com.yv.bbttracker.feature.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryListKeysTest {
    @Test
    fun equalDatabaseIdsRemainUniqueAcrossHistorySections() {
        val sharedDatabaseId = 7L

        val keys = setOf(
            HistoryListKeys.cycle(sharedDatabaseId),
            HistoryListKeys.observation(sharedDatabaseId),
            HistoryListKeys.measurement(sharedDatabaseId),
        )

        assertEquals(3, keys.size)
    }

    @Test
    fun structuralAndDataRowsUseDistinctKeys() {
        val structuralKeys = setOf(
            HistoryListKeys.HEADER,
            HistoryListKeys.EMPTY,
            HistoryListKeys.BACKTEST,
            HistoryListKeys.SIGNS_TOGGLE,
            HistoryListKeys.SIGNS_EMPTY,
            HistoryListKeys.MEASUREMENTS_TOGGLE,
            HistoryListKeys.MEASUREMENTS_EMPTY,
            HistoryListKeys.BOTTOM_SPACER,
        )
        val dataKeys = setOf(
            HistoryListKeys.cycle(1),
            HistoryListKeys.observation(1),
            HistoryListKeys.measurement(1),
        )

        assertEquals(8, structuralKeys.size)
        assertTrue(structuralKeys.intersect(dataKeys).isEmpty())
    }
}
