version = 6

dependencies {
    add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")
    add("testImplementation", "org.robolectric:robolectric:4.16.1")
    add("testImplementation", "com.squareup.okhttp3:mockwebserver:5.0.0-alpha.12")
}

cloudstream {
    description = "Ekino TV: nowy dodatek do ekino-tv.pl. Filmy, seriale, wspólna sesja przeglądarki i ręczna weryfikacja. Wersja testowa."
    authors = listOf("Dedziek CloudStream contributors")
    language = "pl"
    status = 3
    tvTypes = listOf("Movie", "TvSeries")
    isCrossPlatform = false
}
