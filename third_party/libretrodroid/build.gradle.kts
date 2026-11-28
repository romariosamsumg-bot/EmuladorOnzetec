// LibretroDroid 0.14.0 (Filippo Scognamiglio, GPL-3.0) com uma modificação do Onzetec Games:
// AudioTap (src/main/cpp/audiotap.*), uma cópia do som do jogo para gravar os highlights com áudio.
// Fontes originais: https://github.com/Swordfish90/LibretroDroid (tag 0.14.0), com oboe e
// libretro-common reduzidos ao que o núcleo usa. Veja README.md nesta pasta.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.swordfish.libretrodroid"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake { arguments += listOf("-DANDROID_STL=c++_static") }
        }
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64") }
    }

    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
