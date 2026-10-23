# 默认规则之外，本项目需要保留的符号

# kotlinx.serialization：@Serializable 数据类依赖生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.papertong.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.papertong.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.papertong.app.**$$serializer { *; }

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions

# ML Kit 通过反射加载识别器
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# PDFBox-Android 使用反射访问部分内部类
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
