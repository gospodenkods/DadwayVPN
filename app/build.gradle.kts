plugins {
    id("com.android.application")
}

val releaseStoreFile = System.getenv("DADWAY_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("DADWAY_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("DADWAY_KEY_ALIAS")
val releaseKeyPassword = System.getenv("DADWAY_KEY_PASSWORD")
val buildAbiApks = providers.gradleProperty("dadwayAbiApks").orNull.toBoolean()
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "ru.dadway.xrayv2"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.dadway.dadwayvpn"
        minSdk = 23
        targetSdk = 36
        versionCode = 860
        versionName = "8.6.0"
        if (!buildAbiApks) {
            ndk {
                abiFilters += setOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    androidResources {
        localeFilters += setOf("ru", "en")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("dadwayRelease") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("dadwayRelease")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    splits {
        abi {
            isEnable = buildAbiApks
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
}

dependencies {
    implementation(files("libs/libXray.aar"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
