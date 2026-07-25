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
# -------------------------------
# PDFBox optional JPEG2000 (JPX)
# These classes are optional.
# Safe to ignore for Android.
# -------------------------------
-dontwarn com.gemalto.jp2.**
# MLKit
-keep class com.google.mlkit.** { *; }

# PDFBox (reflection safety)
-keep class com.tom_roush.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
