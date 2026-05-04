# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp

# Room（KSP 生成的代码已经处理好，这里仅保留实体）
-keep class com.kian.khup.core.data.db.entities.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.kian.khup.**$$serializer { *; }
-keepclassmembers class com.kian.khup.** {
    *** Companion;
}
-keepclasseswithmembers class com.kian.khup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
