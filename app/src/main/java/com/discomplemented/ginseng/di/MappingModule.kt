package com.discomplemented.ginseng.di

import com.discomplemented.ginseng.mapping.layers.LODTransitionManager
import com.discomplemented.ginseng.mapping.layers.LODTransitionManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MappingModule {

    @Binds
    @Singleton
    abstract fun bindLODTransitionManager(
        lodTransitionManagerImpl: LODTransitionManagerImpl
    ): LODTransitionManager
}
