plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.baijin.game"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.baijin.game"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":game-core"))
}

tasks.named("preBuild") {
    dependsOn(":game-core:validateContent")
}
