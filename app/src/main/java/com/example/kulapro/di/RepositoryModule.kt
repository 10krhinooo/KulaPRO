package com.example.kulapro.di

import com.example.kulapro.data.repository.AuthRepository
import com.example.kulapro.data.repository.AuthRepositoryFirebase
import com.example.kulapro.data.repository.ClaimsRepository
import com.example.kulapro.data.repository.FavouritesRepository
import com.example.kulapro.data.repository.FavouritesRepositoryFirestore
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
 * Binds the diner-facing repository interfaces to their Firebase implementations.
 *
 * Only the interfaces are exposed, so nothing above this layer names Firebase. The
 * restaurant and platform sides are bound in [ManagementModule], which keeps the two halves
 * of the app apart here as well as on screen.
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
    fun favouritesRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
    ): FavouritesRepository = FavouritesRepositoryFirestore(firestore, auth)

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
