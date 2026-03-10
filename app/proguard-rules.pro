# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ── Keep line numbers for crash reports ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Room DAO & Entity ──
-keep class com.theblankstate.preamble.data.Task { *; }
-keep class com.theblankstate.preamble.data.DateStats { *; }

# ── Firebase Realtime Database serialization ──
-keep class com.theblankstate.preamble.sync.FirebaseTaskSyncManager$RemoteTask { *; }
-keepclassmembers class com.theblankstate.preamble.sync.FirebaseTaskSyncManager$RemoteTask {
    public <init>();
    public <init>(...);
}

# ── Gson reflection ──
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── OkHttp ──
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── Google API Client ──
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.** { *; }
-dontwarn com.google.api.client.googleapis.extensions.android.**
-dontwarn com.google.api.client.http.**

# ── Google Play Services Auth ──
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# ── Kotlin coroutines ──
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── Compose: keep @Composable functions referenced by reflection ──
-keep class kotlin.Metadata { *; }