# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# signingConfigs.debug.storeFile and signingConfigs.release.storeFile settings
# in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Keep Room entities
-keep class com.discomplemented.ginseng.data.local.database.entity.** { *; }
-keep class com.discomplemented.ginseng.domain.model.** { *; }

# Keep Hilt classes
-keep class * extends dagger.internal.DaggerGenerated
-keep @dagger.Module class *
-keep @dagger.hilt.android.AndroidEntryPoint class *

# Keep MapLibre
-keep class org.maplibre.** { *; }

# Keep ONNX Runtime
-keep class com.microsoft.onnxruntime.** { *; }

# Keep Gson
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
