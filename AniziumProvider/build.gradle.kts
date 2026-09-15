apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "com.lagradost.cloudstream3.gradle")

cloudstream {
    setRepoUrl("https://raw.githubusercontent.com/ulgenzade/ulgencs3/builds")
    authors = listOf("ulgenzade")
}

android {
    namespace = "com.ulgencs3.anizium"
    defaultConfig {
        minSdk = 21
        compileSdk = 34
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
}