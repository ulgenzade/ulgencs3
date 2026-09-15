// ============================================================
// Root build.gradle.kts — recloudstream/TestPlugins formatına uygun
// ============================================================
buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.3.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
