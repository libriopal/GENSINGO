package com.discomplemented.ginseng.di

import android.content.Context
import androidx.room.Room
import com.discomplemented.ginseng.data.local.database.AppDatabase
import com.discomplemented.ginseng.data.local.database.GinsengPatchDao
import com.discomplemented.ginseng.data.local.database.LandownerPermissionDao
import com.discomplemented.ginseng.data.local.database.TrackNodeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.build(context)
    }

    @Provides
    @Singleton
    fun provideTrackNodeDao(database: AppDatabase): TrackNodeDao {
        return database.trackNodeDao()
    }

    @Provides
    @Singleton
    fun provideGinsengPatchDao(database: AppDatabase): GinsengPatchDao {
        return database.ginsengPatchDao()
    }

    @Provides
    @Singleton
    fun provideLandownerPermissionDao(database: AppDatabase): LandownerPermissionDao {
        return database.landownerPermissionDao()
    }
}
