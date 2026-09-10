package com.discomplemented.ginseng.data.local.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.discomplemented.ginseng.data.local.database.GinsengPatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GinsengPatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(patch: GinsengPatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(patches: List<GinsengPatchEntity>)

    @Query("SELECT * FROM ginseng_patches ORDER BY timestamp DESC")
    fun getAll(): Flow<List<GinsengPatchEntity>>

    @Query("SELECT * FROM ginseng_patches WHERE id = :id")
    suspend fun getById(id: String): GinsengPatchEntity?

    @RawQuery
    suspend fun getPatchesInBoundingBox(query: SupportSQLiteQuery): List<GinsengPatchEntity>
}
