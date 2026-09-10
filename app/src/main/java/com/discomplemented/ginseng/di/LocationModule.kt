package com.discomplemented.ginseng.di

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.discomplemented.ginseng.location.batcher.LocationBatcher
import com.discomplemented.ginseng.location.batcher.LocationBatcherImpl
import com.discomplemented.ginseng.location.tracker.LocationTracker
import com.discomplemented.ginseng.location.tracker.LocationTrackerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for location services.
 */
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }

    @Provides
    @Singleton
    fun provideLocationTracker(
        fusedLocationClient: FusedLocationProviderClient,
        @ApplicationContext context: Context
    ): LocationTracker {
        return LocationTrackerImpl(fusedLocationClient, context)
    }

    @Provides
    @Singleton
    fun provideLocationBatcher(
        trackRepository: com.discomplemented.ginseng.domain.repository.TrackRepository
    ): LocationBatcher {
        return LocationBatcherImpl(trackRepository)
    }
}
