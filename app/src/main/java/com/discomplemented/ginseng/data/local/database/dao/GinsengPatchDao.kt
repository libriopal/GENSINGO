package com.discomplemented.ginseng.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.discomplemented.ginseng.data.local.database.entity.GinsengPatchEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ginseng patches.
 */
@Dao
interface GinsengPatchDao {
    
    @Insert
    suspend fun insert(patch: GinsengPatchEntity): Long
    
    @Insert
    suspend fun insertBatch(patches: List<GinsengPatchEntity>): List<Long>
    
    @Update
    suspend fun update(patch: GinsengPatchEntity)
    
    @Query("SELECT * FROM ginseng_patches ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<GinsengPatchEntity>>
    
    @Query("SELECT * FROM ginseng_patches ORDER BY timestamp DESC")
    suspend fun getAll(): List<GinsengPatchEntity>
    
    @Query("SELECT * FROM ginseng_patches WHERE id = :id")
    suspend fun getById(id: String): GinsengPatchEntity?
    
    @Query("""
        SELECT * FROM ginseng_patches 
        WHERE latitude BETWEEN :minLat AND :maxLat
        AND longitude BETWEEN :minLon AND :maxLon
        ORDER BY timestamp DESC
    """)
    suspend fun queryBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<GinsengPatchEntity>
    
    @Query("SELECT * FROM ginseng_patches WHERE synced = 0 ORDER BY timestamp ASC LIMIT :batchSize")
    suspend fun getUnsynced(batchSize: Int = 50): List<GinsengPatchEntity>
    
    @Query("UPDATE ginseng_patches SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
    
    @Query("DELETE FROM ginseng_patches WHERE id = :id")
    suspend fun delete(id: String): Int
}
