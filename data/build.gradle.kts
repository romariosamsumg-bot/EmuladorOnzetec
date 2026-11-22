plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.retroplay.data"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    // Esquemas exportados do Room disponíveis para o teste de migração.
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
}

dependencies {
    api(project(":domain"))
    implementation(project(":core:common"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // Pasta da rede (SMB/compartilhamento do Windows).
    implementation(libs.smbj)
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.nanohttpd)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    testImplementation(libs.work.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.work.testing)
    testImplementation(libs.room.testing)
}

tasks.withType<Test>().configureEach {
    systemProperty("retroplay.network", providers.gradleProperty("retroplay.network").getOrElse("false"))
    systemProperty("retroplay.e2e", providers.gradleProperty("retroplay.e2e").getOrElse("false"))
    systemProperty("retroplay.smb", providers.gradleProperty("retroplay.smb").getOrElse("false"))
}
