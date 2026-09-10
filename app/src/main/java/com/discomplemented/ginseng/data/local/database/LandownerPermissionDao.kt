package com.discomplemented.ginseng.data.local.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.discomplemented.ginseng.data.local.database.LandownerPermissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LandownerPermissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(permission: LandownerPermissionEntity)

    @Query("SELECT * FROM landowner_permissions")
    fun getAll(): Flow<List<LandownerPermissionEntity>>

    @Query("SELECT * FROM landowner_permissions WHERE id = :id")
    suspend fun getById(id: String): LandownerPermissionEntity?

    @RawQuery
    suspend fun getPermissionsInBoundingBox(query: SupportSQLiteQuery): List<LandownerPermissionEntity>
}
