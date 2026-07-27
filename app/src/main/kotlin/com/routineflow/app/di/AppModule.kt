package com.routineflow.app.di

import com.routineflow.app.data.ChainRepository
import com.routineflow.app.data.LocalChainRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindChainRepository(repository: LocalChainRepository): ChainRepository
}
