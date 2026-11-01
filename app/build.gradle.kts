import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.sdwinnie.universalbluetooth"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sdwinnie.universalbluetooth"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val ksFile = rootProject.file("keystore/universal-bt.jks")
            val storePw = localProps.getProperty("UNIVERSAL_BT_STORE_PW")
                ?: System.getenv("UNIVERSAL_BT_STORE_PW")
            if (ksFile.exists() && !storePw.isNullOrBlank()) {
                storeFile = ksFile
                storePassword = storePw
                keyAlias = localProps.getProperty("UNIVERSAL_BT_KEY_ALIAS") ?: "universalbt"
                keyPassword = localProps.getProperty("UNIVERSAL_BT_KEY_PW") ?: storePw
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSign = signingConfigs.getByName("release")
            signingConfig = if (releaseSign.storeFile?.exists() == true) {
                releaseSign
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            val releaseSign = signingConfigs.getByName("release")
            if (releaseSign.storeFile?.exists() == true) {
                signingConfig = releaseSign
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    testImplementation("junit:junit:4.13.2")
}
