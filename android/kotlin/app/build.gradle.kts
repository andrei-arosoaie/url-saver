plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.know.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.know.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.13.0-dev"
    }
    // This first checkpoint is an isolated native design preview, never a production client.
    flavorDimensions += "environment"
    productFlavors {
        create("preview") {
            dimension = "environment"
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
}
