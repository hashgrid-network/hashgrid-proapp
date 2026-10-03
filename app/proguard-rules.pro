# ============================================================================
# HASHGRID PRO - PRODUCTION PROGUARD / R8 OPTIMIZATION & PERSISTENCE RULES
# ============================================================================

# 1. Preserve Data Models & Field Names (CRITICAL FOR FIRESTORE SYNC)
# Prevents R8 from renaming fields like minerBalanceUsdt, userRigs, etc.
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** {
    <fields>;
    <methods>;
}

# 2. Preserve Payment Models & DTOs (NOWPayments Gateway)
-keep class com.example.data.payment.** { *; }
-keepclassmembers class com.example.data.payment.** {
    <fields>;
    <methods>;
}

# 3. Google Firebase & Firestore Serialization Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }
-keepclassmembers class com.google.firebase.firestore.** {
    <fields>;
    <methods>;
}

# 4. AndroidX EncryptedSharedPreferences & MasterKey (Tink Crypto)
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# 5. Kotlin Coroutines & StateFlow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# 6. Preserve Line Numbers for Crash Reports & Debugging
-keepattributes SourceFile,LineNumberTable
