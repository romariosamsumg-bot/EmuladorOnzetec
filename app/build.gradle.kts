import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

/**
 * Assinatura do release. A chave NUNCA vai para o repositório. Fontes, em ordem:
 * 1. variáveis de ambiente (usadas no GitHub Actions): RETROPLAY_KEYSTORE_FILE,
 *    RETROPLAY_KEYSTORE_PASSWORD, RETROPLAY_KEY_ALIAS, RETROPLAY_KEY_PASSWORD;
 * 2. arquivo keystore.properties na raiz (ignorado pelo git), com storeFile, storePassword,
 *    keyAlias e keyPassword.
 * Sem nenhuma das duas, o release sai sem assinatura (app-release-unsigned.apk).
 */
data class ReleaseSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

fun releaseSigning(): ReleaseSigning? {
    fun env(name: String) = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
    env("RETROPLAY_KEYSTORE_FILE")?.let { path ->
        return ReleaseSigning(file(path), env("RETROPLAY_KEYSTORE_PASSWORD") ?: return null, env("RETROPLAY_KEY_ALIAS") ?: return null, env("RETROPLAY_KEY_PASSWORD") ?: return null)
    }
    val propsFile = rootProject.file("keystore.properties")
    if (!propsFile.isFile) return null
    val p = Properties().apply { propsFile.inputStream().use(::load) }
    return ReleaseSigning(
        rootProject.file(p.getProperty("storeFile") ?: return null),
        p.getProperty("storePassword") ?: return null,
        p.getProperty("keyAlias") ?: return null,
        p.getProperty("keyPassword") ?: return null,
    )
}

val signing = releaseSigning()

// Versão: o CI informa (tag v1.2.3 → "1.2.3"; número da execução → versionCode).
val appVersionName = providers.environmentVariable("RETROPLAY_VERSION_NAME").orNull?.takeIf { it.isNotBlank() } ?: "1.0.0"
val appVersionCode = providers.environmentVariable("RETROPLAY_VERSION_CODE").orNull?.toIntOrNull() ?: 1

android {
    namespace = "com.retroplay"
    // Mesmo NDK do LibretroDroid (third_party): é ele que tira os símbolos de depuração das .so.
    ndkVersion = "27.2.12479018"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        applicationId = "com.retroplay.tv"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Só pt-BR (padrão), inglês e espanhol: evita carregar traduções inúteis das bibliotecas.
    androidResources {
        localeFilters += listOf("pt-rBR", "en", "es")
    }

    // O idioma é trocado dentro do app (Interface → Idioma): o bundle precisa de todos os idiomas.
    bundle {
        language { enableSplit = false }
    }

    // Dois apps a partir do mesmo código:
    // - tv: TV, TV Box e Stick (controle remoto/gamepad, menu lateral, interface em 960x540);
    // - mobile: celular (toque, menu inferior, telas em retrato; o jogo abre deitado).
    flavorDimensions += "device"
    productFlavors {
        create("tv") {
            dimension = "device"
            applicationId = "com.retroplay.tv"
            buildConfigField("boolean", "TV_APP", "true")
        }
        create("mobile") {
            dimension = "device"
            applicationId = "com.retroplay.mobile"
            buildConfigField("boolean", "TV_APP", "false")
        }
    }

    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // x86_64 incluso no debug para rodar no emulador Android TV.
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
        }
        release {
            if (signing != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "DebugProbesKt.bin")
    }
}

dependencies {
    baselineProfile(project(":baselineprofile"))
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":data"))

    implementation(project(":libretrodroid"))
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Interface do celular (barra inferior, campos de texto, folhas inferiores).
    "mobileImplementation"(platform(libs.compose.bom))
    "mobileImplementation"(libs.compose.material3)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    kspTest(libs.hilt.compiler)
}

tasks.withType<Test>().configureEach {
    // Repassa -Pretroplay.screenshots=true para gerar PNGs das telas nos testes de UI.
    systemProperty("retroplay.screenshots", providers.gradleProperty("retroplay.screenshots").getOrElse("false"))
}

// Modelos do domínio (Game, GameSystem, Profile...) são imutáveis: declarados estáveis para o
// Compose pular a recomposição de cards e fileiras quando nada mudou neles.
composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
}
