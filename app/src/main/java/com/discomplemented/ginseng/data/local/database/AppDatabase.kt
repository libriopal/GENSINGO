package com.discomplemented.ginseng.data.local.database

import android.content.Context
import androidx.room.Callback
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.discomplemented.ginseng.data.local.database.TrackNodeDao
import com.discomplemented.ginseng.data.local.database.GinsengPatchDao
import com.discomplemented.ginseng.data.local.database.LandownerPermissionDao
import com.discomplemented.ginseng.data.local.database.TrackNodeEntity
import com.discomplemented.ginseng.data.local.database.GinsengPatchEntity
import com.discomplemented.ginseng.data.local.database.LandownerPermissionEntity
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackNodeEntity::class,
        GinsengPatchEntity::class,
        LandownerPermissionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackNodeDao(): TrackNodeDao
    abstract fun ginsengPatchDao(): GinsengPatchDao
    abstract fun landownerPermissionDao(): LandownerPermissionDao

    companion object {
        private const val TRACK_RTREE = "track_nodes_rtree"
        private const val PATCH_RTREE = "ginseng_patches_rtree"

        /**
         * Callback to create R-Tree virtual tables for spatial indexing.
         */
        val CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // R-Tree table: id, minX, maxX, minY, maxY
                // For track_nodes: id (UUID string), lat, lon
                db.execSQL("CREATE VIRTUAL TABLE $TRACK_RTREE USING rtree(id, minLat, maxLat, minLon, maxLon)")

                // For ginseng_patches: id (UUID string), lat, lon
                db.execSQL("CREATE VIRTUAL TABLE $PATCH_RTREE USING rtree(id, minLat, maxLat, minLon, maxLon)")
            }
        }

        /**
         * Helper to provide the database instance.
         */
        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ginseng_scout_db"
            )
            .addCallback(CALLBACK)
            .build()
        }
    }
}
