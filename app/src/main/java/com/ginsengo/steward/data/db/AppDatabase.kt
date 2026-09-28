package com.ginsengo.steward.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        GinsengPatch::class,
        HabitatReadingRecord::class,
        GinsengObservationEntity::class,
        MonteCarloRecordEntity::class,
        RadiusBufferEntity::class,
        VerifiedHarvestPolygonEntity::class,
        ProspectingTourEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun patchDao(): PatchDao
    abstract fun habitatReadingDao(): HabitatReadingDao
    abstract fun observationDao(): GinsengObservationDao
    abstract fun monteCarloDao(): MonteCarloRecordDao
    abstract fun radiusBufferDao(): RadiusBufferDao
    abstract fun harvestPolygonDao(): HarvestPolygonDao
    abstract fun tourDao(): ProspectingTourDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gensingo.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
