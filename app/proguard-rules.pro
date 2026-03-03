-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.coinlab.app.data.remote.dto.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose
-dontwarn androidx.compose.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Glance
-keep class androidx.glance.** { *; }

# Certificate Pinning - keep OkHttp certificate pinner
-keepclassmembers class okhttp3.internal.tls.** { *; }

# WebSocket models
-keep class com.coinlab.app.data.remote.websocket.TickerUpdate { *; }
-keep class com.coinlab.app.data.remote.api.GlobalDataDto { *; }
-keep class com.coinlab.app.data.remote.api.GlobalDataInner { *; }

# Keep domain models for serialization
-keep class com.coinlab.app.domain.model.** { *; }
-keep class com.coinlab.app.data.local.entity.** { *; }

# v7.7 — Coil image loading (critical for coin logos)
-dontwarn coil.**
-keep class coil.** { *; }

# v7.7 — Paging 3 (infinite scroll in market list)
-keep class androidx.paging.** { *; }
-dontwarn androidx.paging.**

# v7.7 — DataStore Preferences
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# v7.7 — Lottie animations
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# v7.7 — Biometric authentication
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# v7.7 — DynamicCoinRegistry / HardcodedCoinFallback data classes
-keep class com.coinlab.app.data.remote.DynamicCoinRegistry$CoinMeta { *; }
-keep class com.coinlab.app.data.remote.HardcodedCoinFallback$FallbackCoinEntry { *; }
-keep class com.coinlab.app.data.remote.BinanceCoinMapper { *; }
