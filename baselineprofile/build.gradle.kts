// Gera o Baseline Profile da inicialização e da tela Início numa TV/emulador conectado:
//   ./gradlew :app:generateReleaseBaselineProfile
// O resultado vai para app/src/release/generated/baselineProfiles/ e entra no APK release.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.retroplay.baselineprofile"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    defaultConfig {
        // Baseline Profiles exigem Android 9+ no aparelho que gera; o app continua minSdk 26.
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro)
}
