package com.discomplemented.ginseng.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.discomplemented.ginseng.data.local.database.entity.LandownerPermissionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for landowner permissions.
 */
@Dao
interface LandownerPermissionDao {
    
    @Insert
    suspend fun insert(permission: LandownerPermissionEntity): Long
    
    @Query("SELECT * FROM landowner_permissions ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<LandownerPermissionEntity>>
    
    @Query("SELECT * FROM landowner_permissions ORDER BY timestamp DESC")
    suspend fun getAll(): List<LandownerPermissionEntity>
    
    @Query("SELECT * FROM landowner_permissions WHERE id = :id")
    suspend fun getById(id: String): LandownerPermissionEntity?
    
    @Query("DELETE FROM landowner_permissions WHERE id = :id")
    suspend fun delete(id: String): Int
}
