import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun getGitCommitCount(): Int {
    val stdout = ByteArrayOutputStream()
    return try {
        project.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            standardOutput = stdout
        }
        stdout.toString().trim().toInt()
    } catch (_: Exception) {
        1
    }
}

fun getGitVersionName(): String {
    val stdout = ByteArrayOutputStream()
    return try {
        project.exec {
            commandLine("git", "describe", "--tags", "--always", "--dirty")
            standardOutput = stdout
        }
        stdout.toString().trim()
    } catch (_: Exception) {
        "1.0-dev"
    }
}

android {
    namespace = "com.messark.hawker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.messark.hawker"
        minSdk = 26
        targetSdk = 35
        versionCode = getGitCommitCount()
        versionName = getGitVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore)
    implementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
