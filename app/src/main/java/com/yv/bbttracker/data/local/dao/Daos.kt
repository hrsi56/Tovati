package com.yv.bbttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yv.bbttracker.data.local.entity.CycleEntity
import com.yv.bbttracker.data.local.entity.DailyObservationEntity
import com.yv.bbttracker.data.local.entity.PredictionSnapshotEntity
import com.yv.bbttracker.data.local.entity.TemperatureMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleDao {
    @Query("SELECT * FROM cycles WHERE endEpochDay IS NULL ORDER BY startEpochDay DESC LIMIT 1")
    fun observeCurrent(): Flow<CycleEntity?>

    @Query("SELECT * FROM cycles ORDER BY startEpochDay DESC")
    fun observeAll(): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles ORDER BY startEpochDay ASC")
    suspend fun getAll(): List<CycleEntity>

    @Query("SELECT * FROM cycles WHERE id = :id")
    suspend fun getById(id: Long): CycleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CycleEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<CycleEntity>): List<Long>

    @Update
    suspend fun update(entity: CycleEntity)

    @Delete
    suspend fun delete(entity: CycleEntity)

    @Query("DELETE FROM cycles")
    suspend fun deleteAll()
}

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM temperature_measurements WHERE measurementEpochDay = :epochDay ORDER BY measuredAtEpochMillis ASC")
    fun observeForDate(epochDay: Long): Flow<List<TemperatureMeasurementEntity>>

    @Query("SELECT * FROM temperature_measurements WHERE measurementEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY measurementEpochDay ASC, measuredAtEpochMillis ASC")
    fun observeInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TemperatureMeasurementEntity>>

    @Query("SELECT * FROM temperature_measurements ORDER BY measurementEpochDay ASC, measuredAtEpochMillis ASC")
    fun observeAll(): Flow<List<TemperatureMeasurementEntity>>

    @Query("SELECT * FROM temperature_measurements ORDER BY measurementEpochDay ASC, measuredAtEpochMillis ASC")
    suspend fun getAll(): List<TemperatureMeasurementEntity>

    @Query("SELECT * FROM temperature_measurements WHERE id = :id")
    suspend fun getById(id: Long): TemperatureMeasurementEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TemperatureMeasurementEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<TemperatureMeasurementEntity>): List<Long>

    @Update
    suspend fun update(entity: TemperatureMeasurementEntity)

    @Query("DELETE FROM temperature_measurements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE temperature_measurements SET selectedForAnalysis = 0, updatedAtEpochMillis = :updatedAt WHERE measurementEpochDay = :epochDay")
    suspend fun clearSelectionForDay(epochDay: Long, updatedAt: Long)

    @Query("UPDATE temperature_measurements SET selectedForAnalysis = 1, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun markSelected(id: Long, updatedAt: Long)

    @Transaction
    suspend fun selectForAnalysis(id: Long, epochDay: Long, updatedAt: Long) {
        clearSelectionForDay(epochDay, updatedAt)
        markSelected(id, updatedAt)
    }

    @Query("DELETE FROM temperature_measurements")
    suspend fun deleteAll()
}

@Dao
interface ObservationDao {
    @Query("SELECT * FROM daily_observations WHERE epochDay = :epochDay LIMIT 1")
    fun observeForDate(epochDay: Long): Flow<DailyObservationEntity?>

    @Query("SELECT * FROM daily_observations WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY epochDay ASC")
    fun observeInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<DailyObservationEntity>>

    @Query("SELECT * FROM daily_observations ORDER BY epochDay ASC")
    fun observeAll(): Flow<List<DailyObservationEntity>>

    @Query("SELECT * FROM daily_observations ORDER BY epochDay ASC")
    suspend fun getAll(): List<DailyObservationEntity>

    @Query("SELECT * FROM daily_observations WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getByDay(epochDay: Long): DailyObservationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DailyObservationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<DailyObservationEntity>): List<Long>

    @Update
    suspend fun update(entity: DailyObservationEntity)

    @Query("DELETE FROM daily_observations")
    suspend fun deleteAll()
}

@Dao
interface PredictionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PredictionSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<PredictionSnapshotEntity>): List<Long>

    @Query("SELECT * FROM prediction_snapshots ORDER BY generatedAtEpochMillis ASC")
    suspend fun getAll(): List<PredictionSnapshotEntity>

    @Query("DELETE FROM prediction_snapshots")
    suspend fun deleteAll()
}

