# ESTLoginKit - Consumer ProGuard Rules

# Public API
-keep class com.estaid.loginkit.EstLoginManager { *; }
-keep class com.estaid.loginkit.EstLoginConfiguration { *; }
-keep class com.estaid.loginkit.EstLoginConfiguration$Builder { *; }
-keep class com.estaid.loginkit.model.** { *; }
-keep class com.estaid.loginkit.provider.AuthProvider { *; }

# JavaScript Interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.estaid.loginkit.internal.**$$serializer { *; }
-keepclassmembers class com.estaid.loginkit.internal.** {
    *** Companion;
}
-keepclasseswithmembers class com.estaid.loginkit.internal.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit (verificationStatus 조회)
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kakao SDK
-keep class com.kakao.sdk.** { *; }
-keep class com.kakao.auth.** { *; }

# Naver SDK
-keep class com.navercorp.nid.** { *; }
