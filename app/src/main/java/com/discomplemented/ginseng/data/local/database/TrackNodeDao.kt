package com.discomplemented.ginseng.data.local.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.discomplemented.ginseng.data.local.database.TrackNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackNodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: TrackNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<TrackNodeEntity>)

    @Query("SELECT * FROM track_nodes ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TrackNodeEntity>>

    @RawQuery
    suspend fun getNodesInBoundingBox(query: SupportSQLiteQuery): List<TrackNodeEntity>
}
