package com.yv.bbttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the current schema to the checked-in Room schema export. Version 1 to 4 is covered by
 * [AppDatabaseMigrationTest].
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseSchemaBaselineTest {
    private lateinit var roomDatabase: AppDatabase
    private lateinit var sqliteDatabase: SupportSQLiteDatabase

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        roomDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sqliteDatabase = roomDatabase.openHelper.writableDatabase
    }

    @After
    fun closeDatabase() {
        roomDatabase.close()
    }

    @Test
    fun versionFour_hasExpectedIdentityTablesColumnsAndUniqueDailyIndexes() {
        assertEquals(4, sqliteDatabase.version)
        assertEquals(EXPORTED_V4_IDENTITY_HASH, readIdentityHash())

        val applicationTables = mutableSetOf<String>()
        sqliteDatabase.query(
            "SELECT name FROM sqlite_master " +
                "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('room_master_table', 'android_metadata')",
        ).use { cursor ->
            while (cursor.moveToNext()) applicationTables += cursor.getString(0)
        }
        assertEquals(
            setOf("cycles", "temperature_measurements", "daily_observations", "prediction_snapshots"),
            applicationTables,
        )

        assertTrue(uniqueIndexes("cycles").contains("index_cycles_startEpochDay"))
        assertTrue(uniqueIndexes("daily_observations").contains("index_daily_observations_epochDay"))
        assertTrue(columns("cycles").contains("analysisSite"))
        assertTrue(
            columns("daily_observations").containsAll(
                setOf(
                    "mucusSensation",
                    "mucusObscured",
                    "lhTestMinutesOfDay",
                    "lhTestBrand",
                    "lhTestSensitivityMilliIu",
                    "moodMask",
                    "moodNote",
                    "libidoLevel",
                    "sexualContact",
                    "sexualContactInitiatedByUser",
                    "physicalSymptomMask",
                    "painReliefPillCount",
                    "painReliefMedicationNote",
                ),
            ),
        )
    }

    private fun readIdentityHash(): String = sqliteDatabase.query(
        "SELECT identity_hash FROM room_master_table WHERE id = 42",
    ).use { cursor ->
        check(cursor.moveToFirst()) { "Room identity row was not created" }
        cursor.getString(0)
    }

    private fun uniqueIndexes(table: String): Set<String> {
        val indexes = mutableSetOf<String>()
        sqliteDatabase.query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val uniqueColumn = cursor.getColumnIndexOrThrow("unique")
            while (cursor.moveToNext()) {
                if (cursor.getInt(uniqueColumn) == 1) indexes += cursor.getString(nameColumn)
            }
        }
        return indexes
    }

    private fun columns(table: String): Set<String> {
        val result = mutableSetOf<String>()
        sqliteDatabase.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) result += cursor.getString(nameColumn)
        }
        return result
    }

    private companion object {
        // app/schemas/com.yv.bbttracker.data.local.AppDatabase/4.json
        const val EXPORTED_V4_IDENTITY_HASH = "e8b2a253f5fcbfc0478c134044430e00"
    }
}
