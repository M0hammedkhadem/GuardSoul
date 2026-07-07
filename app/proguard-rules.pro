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
# AccessibilityService subclasses
# ---------------------------------------
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService { *; }

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
# Serialization models
# ---------------------------------------
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.Transient <fields>;
}
