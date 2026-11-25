# VragPlayer R8 / ProGuard optimization rules
# Keeps essential Android components, Media3 playback service, and Kotlinx Serialization
# while allowing R8 to strip ~4,000 unused Material icons and dead Compose bytecode.

-keep class com.example.VragApplication { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.service.PlaybackService { *; }

# Media3 / ExoPlayer reflection & audio renderers
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Preserve clean stack traces if needed
-keepattributes SourceFile,LineNumberTable
