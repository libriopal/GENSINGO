package com.discomplemented.ginseng.di

import android.content.Context
import com.discomplemented.ginseng.ai.feature.FeatureExtractor
import com.discomplemented.ginseng.ai.feature.FeatureExtractorImpl
import com.discomplemented.ginseng.ai.inference.HabitatInferenceManager
import com.discomplemented.ginseng.data.repository.GinsengPatchRepositoryImpl
import com.discomplemented.ginseng.data.repository.LandownerPermissionRepositoryImpl
import com.discomplemented.ginseng.data.repository.TrackRepositoryImpl
import com.discomplemented.ginseng.domain.repository.GinsengPatchRepository
import com.discomplemented.ginseng.domain.repository.LandownerPermissionRepository
import com.discomplemented.ginseng.domain.repository.TrackRepository
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTrackRepository(
        trackRepositoryImpl: TrackRepositoryImpl
    ): TrackRepository

    @Binds
    @Singleton
    abstract fun bindGinsengPatchRepository(
        ginsengPatchRepositoryImpl: GinsengPatchRepositoryImpl
    ): GinsengPatchRepository

    @Binds
    @Singleton
    abstract fun bindLandownerPermissionRepository(
        landownerPermissionRepositoryImpl: LandownerPermissionRepositoryImpl
    ): LandownerPermissionRepository

    @Binds
    @Singleton
    abstract fun bindDemRepository(
        demRepositoryImpl: com.discomplemented.ginseng.data.repository.DemRepositoryImpl
    ): com.discomplemented.ginseng.domain.repository.DemRepository

    @Binds
    @Singleton
    abstract fun bindFeatureExtractor(
        featureExtractorImpl: FeatureExtractorImpl
    ): FeatureExtractor

    companion object {
        @Provides
        @Singleton
        fun provideGson(): Gson {
            return Gson()
        }

        @Provides
        @Singleton
        fun provideHabitatInferenceManager(
            @ApplicationContext context: Context
        ): HabitatInferenceManager {
            return HabitatInferenceManager(context)
        }
    }
}
