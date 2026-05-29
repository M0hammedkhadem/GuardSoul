# ============================================================
# ProGuard / R8 rules for Secure App (com.agon.app)
# ============================================================

# ---------------------------------------
# Room (entities, DAOs, database)
# ---------------------------------------
-keep class com.agon.app.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ---------------------------------------
# Koin (keep injected classes & modules)
# ---------------------------------------
-keep class * extends org.koin.core.module.Module { *; }
-keep class * extends org.koin.core.component.KoinComponent { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.KoinReflectionApi <methods>;
}
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}
-keep class * {
    @org.koin.core.annotation.Single <init>(...);
}

# ---------------------------------------
# Firebase (Auth, Realtime Database, FCM)
# ---------------------------------------
-keep class com.agon.app.data.firebase.** { *; }
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepclassmembers class * {
    @com.google.firebase.database.IgnoreExtraProperties <fields>;
}

# ---------------------------------------
# TensorFlow Lite (JNI needs exact names)
# ---------------------------------------
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }

# ---------------------------------------
# AccessibilityService subclasses
# ---------------------------------------
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService { *; }

# ---------------------------------------
# VpnService subclass
# ---------------------------------------
-keep class * extends android.net.VpnService { *; }
-keepclassmembers class * extends android.net.VpnService { *; }

# ---------------------------------------
# BroadcastReceivers
# ---------------------------------------
-keep class * extends android.content.BroadcastReceiver { *; }
-keepclassmembers class * extends android.content.BroadcastReceiver {
    public void onReceive(android.content.Context, android.content.Intent);
}

# ---------------------------------------
# WorkManager Workers
# ---------------------------------------
-keep class * extends androidx.work.Worker { *; }
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---------------------------------------
# EncryptedSharedPreferences (security-crypto)
# ---------------------------------------
-keep class androidx.security.crypto.** { *; }
-keepclassmembers class androidx.security.crypto.** { *; }

# ---------------------------------------
# Serializable / Parcelable
# ---------------------------------------
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ---------------------------------------
# Kotlin Coroutines metadata
# ---------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembers class kotlinx.coroutines.** {
    kotlinx.coroutines.internal.SystemPropsAccessor $systemProps;
}

# ---------------------------------------
# Kotlin metadata (preserve annotations)
# ---------------------------------------
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# ---------------------------------------
# Jetpack Compose
# ---------------------------------------
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# ---------------------------------------
# Gson / serialization models
# ---------------------------------------
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.Transient <fields>;
}
