package com.example.kulapro.di

import android.content.Context
import com.example.kulapro.data.repository.AuthRepositoryFirebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The Firebase handles everything else is built on.
 *
 * Separate from the repository bindings: these are the SDK's own singletons, and a test or
 * a different backend replaces the bindings without touching this.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * One instance, not three.
     *
     * It is the AuthRepository, the ProfileRepository and the ClaimsRepository at once, and
     * separate copies would each hold their own snapshot listeners on the same documents.
     */
    @Provides
    @Singleton
    fun authRepositoryFirebase(
        @ApplicationContext context: Context,
    ): AuthRepositoryFirebase = AuthRepositoryFirebase(context)
}
