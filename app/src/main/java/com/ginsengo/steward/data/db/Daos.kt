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
