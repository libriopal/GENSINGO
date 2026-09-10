package com.discomplemented.ginseng.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.discomplemented.ginseng.data.local.database.dao.GinsengPatchDao
import com.discomplemented.ginseng.data.local.database.dao.LandownerPermissionDao
import com.discomplemented.ginseng.data.local.database.dao.TrackNodeDao
import com.discomplemented.ginseng.data.local.database.entity.GinsengPatchEntity
import com.discomplemented.ginseng.data.local.database.entity.LandownerPermissionEntity
import com.discomplemented.ginseng.data.local.database.entity.TrackNodeEntity

/**
 * Main Room database for GENSINGO.
 * Stores track nodes, ginseng patches, and landowner permissions.
 */
@Database(
    entities = [
        TrackNodeEntity::class,
        GinsengPatchEntity::class,
        LandownerPermissionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class GinsengDatabase : RoomDatabase() {
    abstract fun trackNodeDao(): TrackNodeDao
    abstract fun ginsengPatchDao(): GinsengPatchDao
    abstract fun landownerPermissionDao(): LandownerPermissionDao

    companion object {
        private const val DATABASE_NAME = "ginseng_scout.db"

        @Volatile
        private var instance: GinsengDatabase? = null

        fun getInstance(context: Context): GinsengDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GinsengDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}
