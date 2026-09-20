package com.example.kulapro

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point, and the root of the dependency graph.
 *
 * Screens used to construct their own repositories through default parameters, which meant
 * every one of them knew it was talking to Firebase and none of them could be previewed or
 * tested without it. The graph is assembled here instead.
 */
@HiltAndroidApp
class KulaProApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Workers are built by Hilt so they can be injected.
     *
     * The manifest disables WorkManager's own initialiser for the same reason: two
     * initialisers race, and the one that wins decides whether a worker can have
     * dependencies.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
