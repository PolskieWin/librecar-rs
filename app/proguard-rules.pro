# Keep JNI entry points called from native Rust liblibrecar_core.so
-keep class com.librecar.rust.nativeengine.RustEngine {
    native <methods>;
}

# Keep kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.librecar.rust.** {
    *** Companion;
}
-keepclasseswithmembers class com.librecar.rust.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Strip debug logs in release for speed & tiny APK
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
