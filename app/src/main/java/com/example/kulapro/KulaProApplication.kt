package com.example.kulapro

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point, and the root of the dependency graph.
 *
 * Screens used to construct their own repositories through default parameters, which meant
 * every one of them knew it was talking to Firebase and none of them could be previewed or
 * tested without it. The graph is assembled here instead.
 */
@HiltAndroidApp
class KulaProApplication : Application()
