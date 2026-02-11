import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        // JitPack wajib ada di sini buat narik plugin recloudstream
        maven("https://jitpack.io") 
    }

    dependencies {
        // GANTI KE VERSI STABIL (Standard CloudStream)
        classpath("com.android.tools.build:gradle:8.2.2") 
        
        // KUNCI: Pakai Commit Hash spesifik, JANGAN master-SNAPSHOT
        // Hash 'e116639' adalah versi stabil plugin Cloudstream
        classpath("com.github.recloudstream:gradle:e116639")
        
        // Pakai Kotlin 1.9.x agar kompatibel dengan CloudStream core
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/phisher98/cloudstream-extensions-phisher")
        authors = listOf("Phisher98")
    }

    android {
        namespace = "com.phisher98"

        defaultConfig {
            minSdk = 21
            compileSdkVersion(34) // 35 boleh, tapi 34 lebih aman
            targetSdk = 34
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }

    dependencies {
        val implementation by configurations
        val cloudstream by configurations
        
        // Dependency Wajib
        cloudstream("com.lagradost:cloudstream3:pre-release") 

        // Standard libs
        implementation(kotlin("stdlib"))
        implementation("com.github.Blatzar:NiceHttp:0.4.11") // Versi stabil
        implementation("org.jsoup:jsoup:1.17.2") // Versi stabil
        
        // Serialization & JSON
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
        implementation("com.google.code.gson:gson:2.10.1")
        
        // Coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
        
        // Tools tambahan
        implementation("org.mozilla:rhino:1.7.14") 
        implementation("me.xdrop:fuzzywuzzy:1.4.0")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
