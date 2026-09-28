package com.ginsengo.steward.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PatchDao {

    @Query("SELECT * FROM ginseng_patches ORDER BY lastVisitedDate DESC")
    fun observeAll(): Flow<List<GinsengPatch>>

    @Query("SELECT * FROM ginseng_patches WHERE id = :id")
    fun observeById(id: String): Flow<GinsengPatch?>

    @Query("SELECT * FROM ginseng_patches WHERE id = :id")
    suspend fun byId(id: String): GinsengPatch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(patch: GinsengPatch)

    @Update
    suspend fun update(patch: GinsengPatch)

    @Delete
    suspend fun delete(patch: GinsengPatch)

    @Query("SELECT COUNT(*) FROM ginseng_patches")
    suspend fun count(): Int
}

@Dao
interface HabitatReadingDao {

    @Query("SELECT * FROM habitat_readings ORDER BY createdDate DESC")
    fun observeAll(): Flow<List<HabitatReadingRecord>>

    @Query("SELECT * FROM habitat_readings WHERE patchId = :patchId ORDER BY createdDate DESC")
    fun observeForPatch(patchId: String): Flow<List<HabitatReadingRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: HabitatReadingRecord)

    @Query("DELETE FROM habitat_readings WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM habitat_readings ORDER BY createdDate DESC LIMIT 1")
    suspend fun latest(): HabitatReadingRecord?
}

@Dao
interface GinsengObservationDao {

    @Query("SELECT * FROM ginseng_observations ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<GinsengObservationEntity>>

    @Query("SELECT * FROM ginseng_observations WHERE county = :county ORDER BY timestamp DESC")
    fun observeByCounty(county: String): Flow<List<GinsengObservationEntity>>

    @Query("SELECT * FROM ginseng_observations WHERE county = :county ORDER BY timestamp DESC")
    suspend fun getByCounty(county: String): List<GinsengObservationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(observation: GinsengObservationEntity)

    @Query("SELECT COUNT(*) FROM ginseng_observations")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM ginseng_observations WHERE observationType = 'CONFIRMED_PATCH'")
    suspend fun confirmedCount(): Int

    @Delete
    suspend fun delete(observation: GinsengObservationEntity)
}

@Dao
interface MonteCarloRecordDao {

    @Query("SELECT * FROM monte_carlo_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MonteCarloRecordEntity>>

    @Query("SELECT * FROM monte_carlo_logs WHERE bufferCounty = :county ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForCounty(county: String): MonteCarloRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MonteCarloRecordEntity)

    @Query("SELECT COUNT(*) FROM monte_carlo_logs")
    suspend fun count(): Int
}

@Dao
interface RadiusBufferDao {

    @Query("SELECT * FROM radius_buffers WHERE id = 'active_buffer'")
    fun observeActive(): Flow<RadiusBufferEntity?>

    @Query("SELECT * FROM radius_buffers WHERE id = 'active_buffer'")
    suspend fun getActive(): RadiusBufferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActive(buffer: RadiusBufferEntity)
}

@Dao
interface HarvestPolygonDao {

    @Query("SELECT * FROM verified_harvest_polygons ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<VerifiedHarvestPolygonEntity>>

    @Query("SELECT * FROM verified_harvest_polygons WHERE county = :county ORDER BY timestamp DESC")
    fun observeByCounty(county: String): Flow<List<VerifiedHarvestPolygonEntity>>

    @Query("SELECT * FROM verified_harvest_polygons WHERE county = :county ORDER BY timestamp DESC")
    suspend fun getByCounty(county: String): List<VerifiedHarvestPolygonEntity>

    @Query("SELECT * FROM verified_harvest_polygons ORDER BY timestamp DESC")
    suspend fun getAll(): List<VerifiedHarvestPolygonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(polygon: VerifiedHarvestPolygonEntity)

    @Delete
    suspend fun delete(polygon: VerifiedHarvestPolygonEntity)

    @Query("SELECT COUNT(*) FROM verified_harvest_polygons")
    suspend fun count(): Int

    @Query("SELECT SUM(estimatedRootsHarvested) FROM verified_harvest_polygons WHERE county = :county")
    suspend fun totalRootsDugInCounty(county: String): Int?
}

@Dao
interface ProspectingTourDao {
    @Query("SELECT * FROM prospecting_tours ORDER BY startTimeMs DESC")
    fun observeAll(): Flow<List<ProspectingTourEntity>>

    @Query("SELECT * FROM prospecting_tours WHERE county = :county ORDER BY startTimeMs DESC")
    fun observeByCounty(county: String): Flow<List<ProspectingTourEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tour: ProspectingTourEntity)

    @Update
    suspend fun update(tour: ProspectingTourEntity)

    @Query("SELECT * FROM prospecting_tours WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProspectingTourEntity?

    @Query("SELECT COUNT(*) FROM prospecting_tours")
    suspend fun count(): Int
}
