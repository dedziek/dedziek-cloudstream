version = 9

dependencies {
    add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")
    add("testImplementation", "org.robolectric:robolectric:4.16.1")
    add("testImplementation", "com.squareup.okhttp3:mockwebserver:5.0.0-alpha.12")
}

cloudstream {
    description = "Filmy i seriale z Ekino TV. Odliczanie odtwarzacza i ręczna weryfikacja w ustawieniach."
    iconUrl = "https://raw.githubusercontent.com/dedziek/dedziek-cloudstream/main/assets/icons/ekino-tv-site.png"
    authors = listOf("Dedziek CloudStream contributors")
    language = "pl"
    status = 3
    tvTypes = listOf("Movie", "TvSeries")
    isCrossPlatform = false
}
