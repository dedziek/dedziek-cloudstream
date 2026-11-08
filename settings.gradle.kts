// Kotlin 2.3 metadata requires D8/R8 8.13.19 or newer, even with AGP 8.7.
pluginManagement {
    buildscript {
        repositories { google(); mavenCentral() }
        dependencies { classpath("com.android.tools:r8:8.13.19") }
    }
}

rootProject.name = "FilmanCloudStream"
include("FilmanProvider")
include("AnimeWatchProvider")
include("AnimeGateProvider")
include("EkinoTvProvider")
include("OgladajAnimeProvider")
