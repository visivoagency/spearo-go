package com.spearotracker.spearogo.di

import android.content.Context
import androidx.room.Room
import com.spearotracker.spearogo.services.AppDatabase
import com.spearotracker.spearogo.services.LocationDao
import com.spearotracker.spearogo.services.MIGRATION_1_2
import com.spearotracker.spearogo.sync.AppIdentityHeaders
import com.spearotracker.spearogo.sync.AuthClient
import com.spearotracker.spearogo.sync.FirestoreClient
import com.spearotracker.spearogo.sync.RemoteAuthSignIn
import com.spearotracker.spearogo.sync.SharedPrefsSyncMarkerStore
import com.spearotracker.spearogo.sync.SharedPrefsTokenStore
import com.spearotracker.spearogo.sync.SpotSyncService
import com.spearotracker.spearogo.sync.TokenStore
import com.spearotracker.spearogo.utils.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "spearogo.db"
        ).addMigrations(MIGRATION_1_2).build()
    }

    @Provides
    @Singleton
    fun provideLocationDao(database: AppDatabase): LocationDao {
        return database.locationDao()
    }

    // ── Account sign-in and spot sync (REST, no Firebase SDK) ──────────────────

    /** Shared by AuthClient, FirestoreClient and RemoteAuthSignIn. The weather/tide services keep their own. */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore = SharedPrefsTokenStore(context)

    @Provides
    @Singleton
    fun provideAuthClient(
        @ApplicationContext context: Context, http: OkHttpClient, store: TokenStore,
    ): AuthClient = AuthClient(
        http = http,
        baseIdentity = Constants.Firebase.IDENTITY_BASE,
        baseSecureToken = Constants.Firebase.SECURE_TOKEN_BASE,
        store = store,
        clock = System::currentTimeMillis,
        appHeaders = AppIdentityHeaders.of(context),
    )

    @Provides
    @Singleton
    fun provideFirestoreClient(@ApplicationContext context: Context, http: OkHttpClient): FirestoreClient =
        FirestoreClient(http, Constants.Firebase.FIRESTORE_BASE, AppIdentityHeaders.of(context))

    /** Built on the application context, so Task 7 can call signIn() from any screen. */
    @Provides
    @Singleton
    fun provideRemoteAuthSignIn(
        @ApplicationContext context: Context, http: OkHttpClient, auth: AuthClient,
    ): RemoteAuthSignIn = RemoteAuthSignIn(context, http, auth)

    @Provides
    @Singleton
    fun provideSpotSyncService(
        @ApplicationContext context: Context, auth: AuthClient, firestore: FirestoreClient, dao: LocationDao,
    ): SpotSyncService = SpotSyncService(auth, firestore, dao, SharedPrefsSyncMarkerStore(context))
}
