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
