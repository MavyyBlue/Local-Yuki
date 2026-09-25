plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingFile = providers.environmentVariable("LOCAL_YUKI_SIGNING_FILE").orNull
val signingPassword = providers.environmentVariable("LOCAL_YUKI_SIGNING_PASSWORD").orNull
val signingReady = !signingFile.isNullOrBlank() && !signingPassword.isNullOrBlank()
val ciVersionCode = providers.environmentVariable("LOCAL_YUKI_VERSION_CODE").orNull

android {
    namespace = "com.mavyy.localyuki"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mavyy.localyuki"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode?.toIntOrNull()?.also { require(it >= 2) } ?: 2
        versionName = "0.1.1"
    }

    signingConfigs {
        create("stableDebug") {
            if (signingReady) {
                storeFile = file(requireNotNull(signingFile))
                storePassword = signingPassword
                keyAlias = "local-yuki-continuity"
                keyPassword = signingPassword
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // Missing credentials must never produce a differently signed installable APK.
            signingConfig = if (signingReady) signingConfigs.getByName("stableDebug") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":foundation-contracts"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
