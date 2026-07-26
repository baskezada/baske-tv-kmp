# ==== Reglas R8 para el build de release (minify + shrink) ====

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# ---- kotlinx.serialization ----
# Mantener los serializers generados y los campos de los modelos @Serializable.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class cl.baske.tv.data.model.**$$serializer { *; }
-keepclassmembers class cl.baske.tv.data.model.** { *; }
-dontnote kotlinx.serialization.**

# ---- libVLC (JNI: los nombres NO pueden cambiar ni eliminarse) ----
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-dontwarn org.videolan.**

# ---- Koin (resuelve por tipo; mantener nuestras clases inyectadas) ----
-keep class cl.baske.tv.**ViewModel { *; }
-keep class cl.baske.tv.data.** { *; }
-keep class cl.baske.tv.di.** { *; }

# ---- Ktor / OkHttp (mayormente traen sus reglas, silenciar warnings) ----
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ---- Coil3 trae sus propias reglas; nada extra ----
