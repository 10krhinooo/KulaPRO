package com.example.kulapro.di

import com.example.kulapro.util.Clock
import com.example.kulapro.util.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Real time, as something that can be handed in.
 *
 * Its own module rather than another entry on the repository bindings: a clock is not a
 * repository, and the file binding them was already at the size where one more function
 * made it harder to read than to split.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun clock(): Clock = SystemClock
}
