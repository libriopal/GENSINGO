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
interface TrackDao {

    @Insert
    suspend fun insertAll(points: List<TrackPoint>)

    @Query("SELECT * FROM track_points WHERE time >= :since ORDER BY time")
    fun observeSince(since: Long): Flow<List<TrackPoint>>

    @Query("SELECT * FROM track_points WHERE sessionId = :session ORDER BY time DESC LIMIT 1")
    suspend fun lastOf(session: String): TrackPoint?

    @Query("SELECT * FROM track_points WHERE time < :before ORDER BY time")
    suspend fun before(before: Long): List<TrackPoint>

    @Query("SELECT COUNT(*) FROM track_points")
    suspend fun count(): Int
}

@Dao
interface FindDao {

    @Query("SELECT * FROM finds ORDER BY time DESC")
    fun observeAll(): Flow<List<Find>>

    @Query("SELECT * FROM finds ORDER BY time DESC")
    suspend fun all(): List<Find>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(find: Find)

    @Update
    suspend fun updateAll(finds: List<Find>)

    @Delete
    suspend fun delete(find: Find)
}

@Dao
interface SuggestionDao {

    @Query("SELECT * FROM suggestions WHERE runId = :runId ORDER BY rank")
    fun observeRun(runId: String): Flow<List<Suggestion>>

    @Query("SELECT * FROM suggestions ORDER BY createdAt DESC")
    suspend fun all(): List<Suggestion>

    @Query("SELECT * FROM suggestions WHERE status IN ('NEW', 'VISITED')")
    suspend fun open(): List<Suggestion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Suggestion>)

    @Query("UPDATE suggestions SET status = :status, statusTime = :time WHERE id = :id")
    suspend fun setStatus(id: String, status: String, time: Long)
}

@Dao
interface ResearchRunDao {

    @Query("SELECT * FROM research_runs ORDER BY time DESC LIMIT 1")
    fun observeLatest(): Flow<ResearchRun?>

    @Query("SELECT * FROM research_runs ORDER BY time DESC LIMIT 1")
    suspend fun latest(): ResearchRun?

    @Query("SELECT * FROM research_runs WHERE provider IS NOT NULL AND status = 'OK' ORDER BY time DESC LIMIT 1")
    suspend fun latestModelRun(): ResearchRun?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: ResearchRun)
}
