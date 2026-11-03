# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# --- release shrinking keeps (no obfuscation; these survive minification) ---
-dontobfuscate
-dontwarn

# JS engine bridge: kotlin-reflect reads @JSDocs annotations at runtime
-keepclassmembers class com.tsutsen.platformplayer.api.media.platforms.js.JSClient {
    @com.tsutsen.platformplayer.api.media.platforms.js.JSDocs <methods>;
}
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# WebView JS interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# kotlinx-serialization plugin generates its own keeps for @Serializable;
# keep serializers reachable via reflection just in case
-keep class kotlinx.serialization.** { *; }

# Javet V8: called via JNI/reflection from native — R8 must not strip or rename
-keep class com.caoccao.javet.** { *; }
-dontwarn com.caoccao.javet.**
