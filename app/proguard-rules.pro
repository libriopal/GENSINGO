# Kept for when minification is enabled (see app/build.gradle.kts release block).
-keep class com.ginsengo.steward.data.db.** { *; }
-keep class com.ginsengo.steward.data.reference.** { *; }

# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ginsengo.steward.** {
    *** Companion;
}
-keepclasseswithmembers class com.ginsengo.steward.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ONNX Runtime JNI
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# MapLibre
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
