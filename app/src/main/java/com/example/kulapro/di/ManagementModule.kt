package com.example.kulapro.di

import com.example.kulapro.data.repository.AdminRepository
import com.example.kulapro.data.repository.AdminRepositoryFirestore
import com.example.kulapro.data.repository.OwnerRepository
import com.example.kulapro.data.repository.OwnerRepositoryFirestore
import com.example.kulapro.data.repository.OwnershipRepository
import com.example.kulapro.data.repository.OwnershipRepositoryFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The repositories behind the two management views.
 *
 * Split out of [RepositoryModule] along the same line the app itself is split along:
 * running a restaurant and running the platform are different jobs from booking a table,
 * and nothing a diner does reaches any of these.
 */
@Module
@InstallIn(SingletonComponent::class)
object ManagementModule {

    @Provides
    @Singleton
    fun ownerRepository(
        firestore: FirebaseFirestore,
    ): OwnerRepository = OwnerRepositoryFirestore(firestore)

    @Provides
    @Singleton
    fun adminRepository(
        firestore: FirebaseFirestore,
    ): AdminRepository = AdminRepositoryFirestore(firestore)

    @Provides
    @Singleton
    fun ownershipRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
    ): OwnershipRepository = OwnershipRepositoryFirestore(firestore, auth)
}
