package com.example.kulapro.di

import com.example.kulapro.data.repository.AuthRepository
import com.example.kulapro.data.repository.AuthRepositoryFirebase
import com.example.kulapro.data.repository.ClaimsRepository
import com.example.kulapro.data.repository.OwnerRepository
import com.example.kulapro.data.repository.OwnerRepositoryFirestore
import com.example.kulapro.data.repository.OwnershipRepository
import com.example.kulapro.data.repository.OwnershipRepositoryFirestore
import com.example.kulapro.data.repository.ProfileRepository
import com.example.kulapro.data.repository.ReservationRepository
import com.example.kulapro.data.repository.ReservationRepositoryFirestore
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.RestaurantRepositoryFirestore
import com.example.kulapro.data.repository.ReviewRepository
import com.example.kulapro.data.repository.ReviewRepositoryFirestore
import com.example.kulapro.data.scanner.ScannerRepository
import com.example.kulapro.data.scanner.ScannerRepositoryHttp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the repository interfaces to their Firebase implementations.
 *
 * Only the interfaces are exposed, so nothing above this layer names Firebase. Swapping in
 * a different backend, or a fake for a test, is a change to this file alone.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun authRepository(impl: AuthRepositoryFirebase): AuthRepository = impl

    @Provides
    @Singleton
    fun claimsRepository(impl: AuthRepositoryFirebase): ClaimsRepository = impl

    @Provides
    @Singleton
    fun profileRepository(impl: AuthRepositoryFirebase): ProfileRepository = impl

    @Provides
    @Singleton
    fun restaurantRepository(
        firestore: FirebaseFirestore,
    ): RestaurantRepository = RestaurantRepositoryFirestore(firestore)

    @Provides
    @Singleton
    fun reservationRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
    ): ReservationRepository = ReservationRepositoryFirestore(firestore, auth)

    @Provides
    @Singleton
    fun reviewRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
    ): ReviewRepository = ReviewRepositoryFirestore(firestore, auth)

    @Provides
    @Singleton
    fun ownerRepository(
        firestore: FirebaseFirestore,
    ): OwnerRepository = OwnerRepositoryFirestore(firestore)

    @Provides
    @Singleton
    fun ownershipRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
    ): OwnershipRepository = OwnershipRepositoryFirestore(firestore, auth)

    /**
     * The scanner proxy, if this build was given one.
     *
     * The URL comes from local.properties through BuildConfig rather than from the source
     * tree, because it is per deployment. An empty value is a supported state: the
     * repository reports itself unavailable and the app never offers to scan.
     */
    @Provides
    @Singleton
    fun scannerRepository(
        auth: FirebaseAuth,
    ): ScannerRepository = ScannerRepositoryHttp(
        baseUrl = com.example.kulapro.BuildConfig.SCANNER_URL,
        auth = auth,
    )
}
