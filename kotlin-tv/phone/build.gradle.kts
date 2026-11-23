import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = Properties().apply {
    val config = rootProject.file("keystore.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}

val appVersion = rootProject.file("../VERSION").takeIf { it.exists() }?.readText()?.trim()
    ?.takeIf { it.isNotBlank() } ?: "1.1.0"

fun signingValue(env: String, key: String): String? =
    System.getenv(env) ?: signingProperties.getProperty(key)

android {
    lint {
        // CI 与本地打包不需要 lint 拦截
        checkReleaseBuilds = false
        abortOnError = false
    }

    namespace = "com.jx.hongguoplus"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jx.hongguoplus"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("HONGGUOPLUS_VERSION_CODE")?.toInt() ?: 38
        versionName = System.getenv("HONGGUOPLUS_VERSION_NAME") ?: appVersion
        resourceConfigurations += listOf("zh")
    }

    signingConfigs {
        create("delivery") {
            storeType = "PKCS12"
            storeFile = signingValue("HONGGUOPLUS_KEYSTORE", "storeFile")?.let { file(it) }
            storePassword = signingValue("HONGGUOPLUS_STORE_PASSWORD", "storePassword")
            keyAlias = signingValue("HONGGUOPLUS_KEY_ALIAS", "keyAlias")
            keyPassword = signingValue("HONGGUOPLUS_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("delivery")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += setOf(
            "META-INF/versions/**",
            "META-INF/*.kotlin_module",
            "org/bouncycastle/pqc/**",
            "org/bouncycastle/x509/**"
        )
    }
}

dependencies {
    implementation(project(":core")) { exclude(group = "org.json") }
    implementation(project(":playback-api"))
    implementation(project(":playback-media"))
    implementation(project(":playback-native"))
    implementation(project(":playback-phoenix"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
