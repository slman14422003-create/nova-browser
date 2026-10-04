-keepattributes *Annotation*

# ---- تصغير أقصى (R8) ----
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkParameterIsNotNull(...);
}
-dontwarn java.lang.invoke.StringConcatFactory

# NewPipeExtractor + Rhino + jsoup
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.protobuf.**
-dontwarn sun.misc.Unsafe
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.lang.model.**

# FFmpegKit
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }
-dontwarn com.arthenica.**
