package com.routineflow.app.di

import com.routineflow.app.data.ChainRepository
import com.routineflow.app.data.LocalChainRepository
import com.routineflow.app.notifications.OvertimeNotifier
import com.routineflow.app.notifications.RoutineNotifier
import com.routineflow.app.notifications.ActionSpeech
import com.routineflow.app.notifications.AndroidActionSpeech
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

    @Binds
    @Singleton
    abstract fun bindRoutineNotifier(notifier: OvertimeNotifier): RoutineNotifier

    @Binds
    @Singleton
    abstract fun bindActionSpeech(speech: AndroidActionSpeech): ActionSpeech
}
