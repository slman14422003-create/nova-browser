-keepattributes *Annotation*
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
