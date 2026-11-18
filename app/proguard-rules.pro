# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.connector.app.**$$serializer { *; }
-keepclassmembers class com.connector.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.connector.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
