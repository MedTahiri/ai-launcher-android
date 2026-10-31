import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val launcherConfig = rootProject.file("launcher.json")
val expectedOnScreen = layout.buildDirectory.file("launcher-expected.txt").get().asFile

// The launcher's name on the phone. A broken launcher.json falls back here and is reported by LauncherConfigFileTest.
val launcherName: String = runCatching {
    (JsonSlurper().parse(launcherConfig) as Map<*, *>)["name"] as String
}.getOrDefault("Launcher")

android {
    namespace = "io.github.medtahiri.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.medtahiri.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        resValue("string", "app_name", launcherName.replace("'", "\\'"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    testOptions {
        // launcher.json is checked from the repo root by LauncherConfigFileTest.
        unitTests.all {
            it.systemProperty("launcher.config", launcherConfig.absolutePath)
            it.systemProperty("launcher.expected", expectedOnScreen.absolutePath)
            it.outputs.file(expectedOnScreen)
            it.inputs.file(launcherConfig)
            it.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
            }
        }
    }
}

// Packages launcher.json from the repo root, the one place people and agents edit it.
abstract class PackageLauncherConfig : DefaultTask() {
    @get:InputFile abstract val config: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun copy() {
        config.get().asFile.copyTo(outputDir.get().file("launcher.json").asFile, overwrite = true)
    }
}

val packageLauncherConfig = tasks.register<PackageLauncherConfig>("packageLauncherConfig") {
    config.set(launcherConfig)
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(packageLauncherConfig, PackageLauncherConfig::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
