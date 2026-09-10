package com.discomplemented.ginseng.di

import android.content.Context
import com.discomplemented.ginseng.compliance.geofence.GeofenceMonitor
import com.discomplemented.ginseng.compliance.geofence.GeofenceMonitorImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ComplianceModule {

    @Binds
    @Singleton
    abstract fun bindGeofenceMonitor(
        geofenceMonitorImpl: GeofenceMonitorImpl
    ): GeofenceMonitor
}

// Note: GeofenceMonitorImpl requires FusedLocationProviderClient and Context,
// which are already provided by LocationProviderModule and Hilt.
