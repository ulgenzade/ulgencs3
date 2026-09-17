plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.ulgencs3.fullhdfilmizlesene"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xno-param-assertions", "-Xno-call-assertions")
    }
}

dependencies {
    implementation("com.lagradost:cloudstream3:pre-release")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.1")
}
