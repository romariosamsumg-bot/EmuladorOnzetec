# Regras R8 do Retrô Play.
# kotlinx.serialization (rotas de navegação type-safe)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class com.retroplay.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# LibretroDroid: o código nativo acessa classes, campos e métodos por nome via JNI
# (Variable, Controller, GLRetroShader, RetroException, GLRetroView.refreshAspectRatio...).
# O AAR não traz regras próprias, então mantemos o pacote inteiro (é pequeno).
-keep class com.swordfish.libretrodroid.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
