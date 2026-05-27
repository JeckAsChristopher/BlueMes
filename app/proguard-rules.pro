# BlueMes ProGuard Rules

# Keep Room entities
-keep class com.bluemes.app.data.local.entities.** { *; }

# Keep Gson serialization models
-keep class com.bluemes.app.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Navigation component
-keepnames class androidx.navigation.fragment.NavHostFragment

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
