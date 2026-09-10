package com.discomplemented.ginseng.di

import android.content.Context
import com.discomplemented.ginseng.core.security.FileStorageManager
import com.discomplemented.ginseng.core.security.SecurityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecurityManager(@ApplicationContext context: Context): SecurityManager {
        return SecurityManager(context)
    }

    @Provides
    @Singleton
    fun provideFileStorageManager(@ApplicationContext context: Context): FileStorageManager {
        return FileStorageManager(context)
    }
}
