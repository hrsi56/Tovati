package com.yv.bbttracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yv.bbttracker.data.local.converter.RoomConverters
import com.yv.bbttracker.data.local.dao.CycleDao
import com.yv.bbttracker.data.local.dao.MeasurementDao
import com.yv.bbttracker.data.local.dao.ObservationDao
import com.yv.bbttracker.data.local.dao.PredictionDao
import com.yv.bbttracker.data.local.entity.CycleEntity
import com.yv.bbttracker.data.local.entity.DailyObservationEntity
import com.yv.bbttracker.data.local.entity.PredictionSnapshotEntity
import com.yv.bbttracker.data.local.entity.TemperatureMeasurementEntity

@Database(
    entities = [
        CycleEntity::class,
        TemperatureMeasurementEntity::class,
        DailyObservationEntity::class,
        PredictionSnapshotEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cycleDao(): CycleDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun observationDao(): ObservationDao
    abstract fun predictionDao(): PredictionDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "bbt-tracker.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cycles ADD COLUMN analysisSite TEXT")
                db.execSQL(
                    """
                    UPDATE cycles
                    SET analysisSite = (
                        SELECT measurement.site
                        FROM temperature_measurements AS measurement
                        WHERE measurement.selectedForAnalysis = 1
                          AND measurement.measurementEpochDay >= cycles.startEpochDay
                          AND (
                              cycles.endEpochDay IS NULL OR
                              measurement.measurementEpochDay <= cycles.endEpochDay
                          )
                        ORDER BY measurement.measurementEpochDay ASC,
                                 measurement.measuredAtEpochMillis ASC,
                                 measurement.createdAtEpochMillis ASC,
                                 measurement.id ASC
                        LIMIT 1
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "ALTER TABLE daily_observations " +
                        "ADD COLUMN mucusSensation TEXT NOT NULL DEFAULT 'NOT_CHECKED'",
                )
                db.execSQL(
                    "ALTER TABLE daily_observations " +
                        "ADD COLUMN mucusObscured INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE daily_observations ADD COLUMN lhTestMinutesOfDay INTEGER")
                db.execSQL("ALTER TABLE daily_observations ADD COLUMN lhTestBrand TEXT")
                db.execSQL("ALTER TABLE daily_observations ADD COLUMN lhTestSensitivityMilliIu INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE daily_observations ADD COLUMN moodMask INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE daily_observations ADD COLUMN moodNote TEXT")
                db.execSQL(
                    "ALTER TABLE daily_observations " +
                        "ADD COLUMN libidoLevel TEXT NOT NULL DEFAULT 'NOT_RECORDED'",
                )
                db.execSQL(
                    "ALTER TABLE daily_observations " +
                        "ADD COLUMN sexualContact TEXT NOT NULL DEFAULT 'NOT_RECORDED'",
                )
                db.execSQL(
                    "ALTER TABLE daily_observations " +
                        "ADD COLUMN physicalSymptomMask INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE daily_observations " +
                        "ADD COLUMN sexualContactInitiatedByUser INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE daily_observations ADD COLUMN painReliefPillCount INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE daily_observations ADD COLUMN painReliefMedicationNote TEXT",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Older versions allowed several measurements on the same date. Keep the one
                // saved most recently, then enforce the new one-measurement-per-date invariant.
                db.execSQL(
                    """
                    DELETE FROM temperature_measurements
                    WHERE id != (
                        SELECT candidate.id
                        FROM temperature_measurements AS candidate
                        WHERE candidate.measurementEpochDay =
                            temperature_measurements.measurementEpochDay
                        ORDER BY candidate.updatedAtEpochMillis DESC, candidate.id DESC
                        LIMIT 1
                    )
                    """.trimIndent(),
                )
                db.execSQL("DROP INDEX IF EXISTS index_temperature_measurements_measurementEpochDay")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                        index_temperature_measurements_measurementEpochDay
                    ON temperature_measurements (measurementEpochDay)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    UPDATE cycles
                    SET analysisSite = CASE
                        WHEN EXISTS (
                            SELECT 1
                            FROM temperature_measurements AS matching
                            WHERE matching.selectedForAnalysis = 1
                              AND matching.site = cycles.analysisSite
                              AND matching.measurementEpochDay >= cycles.startEpochDay
                              AND (
                                  cycles.endEpochDay IS NULL OR
                                  matching.measurementEpochDay <= cycles.endEpochDay
                              )
                        ) THEN cycles.analysisSite
                        ELSE (
                            SELECT selected.site
                            FROM temperature_measurements AS selected
                            WHERE selected.selectedForAnalysis = 1
                              AND selected.measurementEpochDay >= cycles.startEpochDay
                              AND (
                                  cycles.endEpochDay IS NULL OR
                                  selected.measurementEpochDay <= cycles.endEpochDay
                              )
                            ORDER BY selected.measurementEpochDay ASC,
                                     selected.measuredAtEpochMillis ASC,
                                     selected.createdAtEpochMillis ASC,
                                     selected.id ASC
                            LIMIT 1
                        )
                    END
                    """.trimIndent(),
                )
            }
        }
    }
}
