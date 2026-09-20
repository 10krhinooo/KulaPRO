package com.example.kulapro.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.kulapro.data.settings.SETTINGS_STORE_NAME
import com.example.kulapro.data.settings.SettingsRepository
import com.example.kulapro.data.settings.SettingsRepositoryDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by
    preferencesDataStore(SETTINGS_STORE_NAME)

/**
 * Where the preferences file is decided.
 *
 * The store is named here rather than inside the repository, so the repository can be handed
 * a different file in a test and the app still gets exactly one store for the process, which
 * is what DataStore requires.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun settingsStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsStore

    @Provides
    @Singleton
    fun settingsRepository(
        store: DataStore<Preferences>,
    ): SettingsRepository = SettingsRepositoryDataStore(store)
}
