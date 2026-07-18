package com.yv.bbttracker.feature.settings

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFreshnessTest {
    private val jerusalem = ZoneId.of("Asia/Jerusalem")

    @Test
    fun `backup created during the local calendar day is fresh`() {
        assertTrue(
            backupCreatedOnDate(
                createdAt = Instant.parse("2026-07-17T21:30:00Z"),
                date = LocalDate.of(2026, 7, 18),
                zoneId = jerusalem,
            ),
        )
    }

    @Test
    fun `backup from the preceding local day is stale`() {
        assertFalse(
            backupCreatedOnDate(
                createdAt = Instant.parse("2026-07-17T20:30:00Z"),
                date = LocalDate.of(2026, 7, 18),
                zoneId = jerusalem,
            ),
        )
    }
}
