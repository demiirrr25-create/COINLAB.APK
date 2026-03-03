package com.coinlab.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * v8.9 — Firebase DI Module
 *
 * Provides Firebase singleton instances for Realtime Database, Firestore, Auth, and Storage.
 * Community features use Realtime Database. Other features may still use Firestore.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        val database = FirebaseDatabase.getInstance("https://com-coinlab-app-default-rtdb.europe-west1.firebasedatabase.app/")
        try {
            database.setPersistenceEnabled(true)
        } catch (_: Exception) {
            // Already set — safe to ignore
        }
        return database
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(50L * 1024 * 1024) // 50 MB cache
                    .build()
            )
            .build()
        firestore.firestoreSettings = settings
        return firestore
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()
}
