# PaperTong 的 R8 / ProGuard 规则
#
# 重要背景：release 构建直到 M8 才第一次在 CI 里跑。此前的规则是照着
# 常见写法抄的，没有经过验证。下面每一条都注明"为什么需要"，
# 不确定能删的一律不删——release 包被裁坏的代价是一次线上崩溃，
# 而多留几条规则的代价只是几十 KB。

# ---------------------------------------------------------------- 基础属性
# 反射、序列化、泛型都依赖这些元数据；R8 默认会删掉其中一部分
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions,
                EnclosingMethod, MethodParameters, SourceFile, LineNumberTable

# 不要因为引用了可选的 JDK/Android 类就报错
-dontwarn org.jetbrains.annotations.**

# ------------------------------------------------- kotlinx.serialization
# 这是本项目最容易出问题的地方：@Serializable 类生成的 Companion.serializer()
# 是反射入口，R8 看不到调用点就会把它裁掉，表现为**运行时**报
# "Serializer for class X is not found"。编译期完全正常，CI 也发现不了。
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$serializer serializer(...);
}

# 序列化器对象本身（XXX$$serializer）不能被裁
-keep,includedescriptorclasses class com.papertong.app.**$$serializer { *; }
# 上面只保证不被裁，类名仍会被混淆。索引式序列化在运行时按字符串名查找
# 描述符，改名会让检查类名的验证脚本失去意义，也增加排查成本，
# 因此对数据模型整体禁止改名（体积代价可忽略：项目里只有几条数据类）。
-keepnames class com.papertong.app.core.history.** { *; }
-keepnames class com.papertong.app.core.model.** { *; }
-keepclassmembers class com.papertong.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.papertong.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 枚举被 @Serializable 使用时，其 values()/valueOf() 由库反射调用
-keepclassmembers enum com.papertong.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ------------------------------------------------------------- Kotlin 元数据
# Kotlin 协程的 suspend 函数、默认参数依赖这些
-keepclassmembers class kotlin.Metadata { public *; }
# 保留 data class 的 copy/componentN（序列化与界面都用得到）
-keepclassmembers class com.papertong.app.** {
    *** component*(...);
    *** copy(...);
}

# ----------------------------------------------------------- OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
# Retrofit 通过注解 + 泛型反射解析接口方法
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# --------------------------------------------------------------- ML Kit
# 识别器通过反射与 ServiceLoader 加载，类名不能改
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.common.internal.** { *; }

# ------------------------------------------------------------- PdfBox-Android
# 用反射访问 PDF 内部结构与字体
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
# BouncyCastle 通过 JCA provider 按类名查找，类名被改就找不到
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ------------------------------------------------------------------ 其他
# 去掉日志：release 包里不留 Log.d/Log.v。
# 注意 Log.w / Log.e 保留——崩溃排查需要它们，且已确认不含用户内容。
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
