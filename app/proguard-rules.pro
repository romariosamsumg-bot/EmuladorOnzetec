# Regras R8 do Retrô Play.
# kotlinx.serialization (rotas de navegação type-safe)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class com.retroplay.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
