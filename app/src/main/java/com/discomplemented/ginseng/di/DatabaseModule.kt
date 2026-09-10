package com.discomplemented.ginseng.di

import android.content.Context
import androidx.room.Room
import com.discomplemented.ginseng.data.local.database.GinsengDatabase
import com.discomplemented.ginseng.data.local.database.dao.GinsengPatchDao
import com.discomplemented.ginseng.data.local.database.dao.LandownerPermissionDao
import com.discomplemented.ginseng.data.local.database.dao.TrackNodeDao
import com.discomplemented.ginseng.data.repository.GinsengPatchRepositoryImpl
import com.discomplemented.ginseng.data.repository.TrackRepositoryImpl
import com.discomplemented.ginseng.domain.repository.GinsengPatchRepository
import com.discomplemented.ginseng.domain.repository.TrackRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for database and repository layer.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGinsengDatabase(
        @ApplicationContext context: Context
    ): GinsengDatabase {
        return Room.databaseBuilder(
            context,
            GinsengDatabase::class.java,
            "ginseng_scout.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideTrackNodeDao(db: GinsengDatabase): TrackNodeDao {
        return db.trackNodeDao()
    }

    @Provides
    @Singleton
    fun provideGinsengPatchDao(db: GinsengDatabase): GinsengPatchDao {
        return db.ginsengPatchDao()
    }

    @Provides
    @Singleton
    fun provideLandownerPermissionDao(db: GinsengDatabase): LandownerPermissionDao {
        return db.landownerPermissionDao()
    }

    @Provides
    @Singleton
    fun provideTrackRepository(dao: TrackNodeDao): TrackRepository {
        return TrackRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideGinsengPatchRepository(dao: GinsengPatchDao): GinsengPatchRepository {
        return GinsengPatchRepositoryImpl(dao)
    }
}
