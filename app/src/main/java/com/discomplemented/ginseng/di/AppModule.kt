package com.discomplemented.ginseng.di

import android.content.Context
import com.discomplemented.ginseng.ai.inference.HabitatInferenceManager
import com.discomplemented.ginseng.compliance.GeofenceCompliance
import com.discomplemented.ginseng.compliance.SeasonalCompliance
import com.discomplemented.ginseng.data.remote.SyncService
import com.discomplemented.ginseng.data.remote.SyncServiceImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for compliance, AI, and sync services.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGeofenceCompliance(): GeofenceCompliance {
        return GeofenceCompliance()
    }

    @Provides
    @Singleton
    fun provideSeasonalCompliance(): SeasonalCompliance {
        return SeasonalCompliance()
    }

    @Provides
    @Singleton
    fun provideHabitatInferenceManager(
        @ApplicationContext context: Context
    ): HabitatInferenceManager {
        return HabitatInferenceManager(context).apply { initialize() }
    }

    @Provides
    @Singleton
    fun provideSyncService(
        @ApplicationContext context: Context
    ): SyncService {
        return SyncServiceImpl(context)
    }
}
