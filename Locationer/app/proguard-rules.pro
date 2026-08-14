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

# Optional classes referenced by the bundled AMap SDK but absent from this artifact.
-dontwarn com.amap.ams.gnss.GnssSoftLocator
-dontwarn net.jafama.FastMath

# ============================================
# 高德地图 SDK 完整保护规则
# R8 优化高德 SDK 的 final R class 会导致资源丢失和运行崩溃
# ============================================

# 保留高德 SDK 所有类和方法（防止反射和内部调用失败）
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.tencent.** { *; }

# 保留高德 SDK 的 R 类（final int 字段不能被 R8 安全优化）
-keepclassmembers class com.amap.api.map3d.R { public static final int *; }
-keepclassmembers class com.amap.api.map3d.R$attr { public static final int *; }
-keepclassmembers class com.amap.api.map3d.R$color { public static final int *; }
-keepclassmembers class com.amap.api.map3d.R$dimen { public static final int *; }
-keepclassmembers class com.amap.api.map3d.R$drawable { public static final int *; }
-keepclassmembers class com.amap.api.map3d.R$id { public static final int *; }
-keepclassmembers class com.amap.api.map3d.R$layout { public static final int *; }
-keepclassmembers class com.amap.api.map3d.R$string { public static final int *; }
-keepclassmembers class com.amap.api.map3d.R$style { public static final int *; }
-keepclassmembers class com.amap.api.map3d.R$styleable { public static final int *; }

# 保留高德 SDK 的 Native Library
-keep class com.amap.api.location.** { *; }

# ============================================
# ISensorListenerDelegate 接口及实现（关键！）
# 高德 native 层通过 JNI 回调此接口，混淆后方法名改变导致崩溃
# ============================================
-keep,allowobfuscation,allowshrinking interface com.amap.api.location.ISensorListenerDelegate { *; }
-keepclassmembers,allowobfuscation,allowshrinking class * implements com.amap.api.location.ISensorListenerDelegate { *; }
