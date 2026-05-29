-keepattributes *Annotation*
-keep class com.bluemes.app.models.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
