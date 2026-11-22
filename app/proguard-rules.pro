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

# Pasta da rede (smbj): o barramento de eventos (mbassador) encontra os handlers por anotação.
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-keepattributes *Annotation*
-dontwarn org.slf4j.**
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.security.auth.kerberos.**
-dontwarn java.beans.**
