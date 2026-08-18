import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

abstract class SyncAtlasRootCertificateTask :
    DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceCertificate:
            RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory:
            DirectoryProperty

    @TaskAction
    fun copyCertificate() {
        val sourceFile =
            sourceCertificate.get().asFile

        check(sourceFile.isFile) {
            "Atlas root certificate not found at " +
                    sourceFile.absolutePath
        }

        val destinationFile =
            outputDirectory.file(
                "raw/atlas_local_root.crt"
            ).get().asFile

        destinationFile.parentFile.mkdirs()

        sourceFile.copyTo(
            target = destinationFile,
            overwrite = true
        )
    }
}

val atlasRootCertificate =
    rootProject.layout.projectDirectory.file(
        "../apps/web/pki/atlas-local-root.crt"
    )

android {
    signingConfigs {
        create("release") {
            storeFile =
                rootProject.file("release-key.jks")
            storePassword = "123456"
            keyAlias = "atlas-sign"
            keyPassword = "123456"
        }
    }

    namespace = "org.gtlv.atlas"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "org.gtlv.atlas"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner." +
                    "AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig =
                signingConfigs.getByName("release")

            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val variantTaskName =
            variant.name.replaceFirstChar {
                it.uppercase()
            }

        val syncCertificateTask =
            tasks.register<
                    SyncAtlasRootCertificateTask
                    >(
                "sync${variantTaskName}" +
                        "AtlasRootCertificate"
            ) {
                sourceCertificate.set(
                    atlasRootCertificate
                )

                outputDirectory.set(
                    layout.buildDirectory.dir(
                        "generated/" +
                                "atlasCertificateResources/" +
                                variant.name
                    )
                )
            }

        variant.sources.res
            ?.addGeneratedSourceDirectory(
                syncCertificateTask,
                SyncAtlasRootCertificateTask::
                outputDirectory
            )
    }
}

dependencies {
    implementation(project(":car_common"))
    implementation(project(":core"))
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose")
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.maplibre.sdk)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
