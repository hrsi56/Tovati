package com.yv.bbttracker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yv.bbttracker.domain.model.MeasurementSite
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To4_preservesLegacyDataAndBackfillsNewObservationDefaults() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO cycles
                    (id, startEpochDay, endEpochDay, createdAtEpochMillis, updatedAtEpochMillis, note)
                VALUES (1, 20000, 20028, 101, 202, 'legacy cycle')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO cycles
                    (id, startEpochDay, endEpochDay, createdAtEpochMillis, updatedAtEpochMillis, note)
                VALUES (2, 20100, NULL, 303, 404, NULL)
                """.trimIndent(),
            )
            insertLegacyMeasurement(id = 1, day = 20_000, site = "RECTAL", selected = false)
            insertLegacyMeasurement(id = 2, day = 20_002, site = "VAGINAL", selected = true)
            insertLegacyMeasurement(id = 3, day = 20_001, site = "ORAL", selected = true)
            execSQL(
                """
                INSERT INTO daily_observations
                    (id, epochDay, bleeding, mucus, lhResult, ovulationPain,
                     isExplicitCycleStart, note, createdAtEpochMillis, updatedAtEpochMillis)
                VALUES (9, 20003, 'LIGHT', 'CREAMY', 'POSITIVE', 'NONE', 0,
                        'legacy observation', 505, 606)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            4,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
        )

        migrated.query(
            "SELECT id, startEpochDay, endEpochDay, analysisSite, note FROM cycles ORDER BY id",
        ).use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(0))
            assertEquals(20_000L, cursor.getLong(1))
            assertEquals(20_028L, cursor.getLong(2))
            assertEquals(MeasurementSite.ORAL.name, cursor.getString(3))
            assertEquals("legacy cycle", cursor.getString(4))
            cursor.moveToNext()
            assertEquals(2L, cursor.getLong(0))
            assertNull(cursor.nullableString(3))
        }

        migrated.query(
            """
            SELECT bleeding, mucus, mucusSensation, mucusObscured, lhResult,
                   lhTestMinutesOfDay, lhTestBrand, lhTestSensitivityMilliIu,
                   ovulationPain, moodMask, moodNote, libidoLevel, sexualContact,
                   sexualContactInitiatedByUser, physicalSymptomMask,
                   painReliefPillCount, painReliefMedicationNote, note
            FROM daily_observations WHERE id = 9
            """.trimIndent(),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("LIGHT", cursor.getString(0))
            assertEquals("CREAMY", cursor.getString(1))
            assertEquals("NOT_CHECKED", cursor.getString(2))
            assertFalse(cursor.getInt(3) != 0)
            assertEquals("POSITIVE", cursor.getString(4))
            assertNull(cursor.nullableString(5))
            assertNull(cursor.nullableString(6))
            assertNull(cursor.nullableString(7))
            assertEquals("NONE", cursor.getString(8))
            assertEquals(0L, cursor.getLong(9))
            assertNull(cursor.nullableString(10))
            assertEquals("NOT_RECORDED", cursor.getString(11))
            assertEquals("NOT_RECORDED", cursor.getString(12))
            assertNull(cursor.nullableString(13))
            assertEquals(0L, cursor.getLong(14))
            assertNull(cursor.nullableString(15))
            assertNull(cursor.nullableString(16))
            assertEquals("legacy observation", cursor.getString(17))
        }

        migrated.close()
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertLegacyMeasurement(
        id: Long,
        day: Long,
        site: String,
        selected: Boolean,
    ) {
        execSQL(
            """
            INSERT INTO temperature_measurements
                (id, measurementEpochDay, measuredAtEpochMillis, timezoneId, temperatureCentiC,
                 site, sleepMinutes, measuredImmediatelyAfterWaking, disturbanceMask,
                 disturbanceNote, note, selectedForAnalysis, source,
                 createdAtEpochMillis, updatedAtEpochMillis)
            VALUES ($id, $day, ${1_000 + id}, 'Asia/Jerusalem', 3650,
                    '$site', 420, 1, 0, NULL, NULL, ${if (selected) 1 else 0}, 'MANUAL',
                    ${2_000 + id}, ${3_000 + id})
            """.trimIndent(),
        )
    }

    private fun android.database.Cursor.nullableString(column: Int): String? =
        if (isNull(column)) null else getString(column)

    private companion object {
        const val TEST_DATABASE = "bbt-migration-test"
    }
}
