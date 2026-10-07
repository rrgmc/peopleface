plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.rrgmc.peopleface"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rrgmc.peopleface"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "1.26"
    }

    // APK file name: peopleface-<version>-<build type>.apk
    base.archivesName = "peopleface-${defaultConfig.versionName}"

    // The release key stays outside the repository. Its location and passwords come from Gradle
    // properties: the user's gradle.properties locally, -P flags filled from secrets in CI.
    // Without them the release APK is built unsigned.
    val signingStoreFile = providers.gradleProperty("peopleface.signing.storeFile").orNull

    signingConfigs {
        if (signingStoreFile != null) {
            create("release") {
                storeFile = file(signingStoreFile)
                storePassword = providers.gradleProperty("peopleface.signing.storePassword").get()
                keyAlias = providers.gradleProperty("peopleface.signing.keyAlias").get()
                keyPassword = providers.gradleProperty("peopleface.signing.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.exifinterface)
    implementation(libs.mlkit.face.detection)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
