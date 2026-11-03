import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    }
}
allprojects {
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}
fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")
    extensions.configure<CloudstreamExtension>("cloudstream") {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "dedziek/dedziek-cloudstream")
    }
    extensions.configure<BaseExtension>("android") {
        namespace = "pl.filmancloudstream"
        compileSdkVersion(35)
        defaultConfig { minSdk = 23; targetSdk = 35 }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    dependencies {
        add("cloudstream", "com.lagradost:cloudstream3:pre-release")
        add("implementation", kotlin("stdlib"))
        add("implementation", "com.github.Blatzar:NiceHttp:0.4.11")
        add("implementation", "org.jsoup:jsoup:1.18.3")
        add("compileOnly", "org.jspecify:jspecify:1.0.0")
        add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
        add("testImplementation", "junit:junit:4.13.2")
    }
}
