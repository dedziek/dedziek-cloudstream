version = 4

dependencies {
    add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    add("testImplementation", "org.robolectric:robolectric:4.16.1")
}

cloudstream {
    description = "Ekino: filmy, seriale i odcinki z ekino-tv.pl (obecnie ekino.ws). Zwykłe odtwarzacze i opcjonalne logowanie w ustawieniach. Wersja testowa."
    authors = listOf("Dedziek CloudStream contributors")
    language = "pl"
    status = 3
    tvTypes = listOf("Movie", "TvSeries")
    isCrossPlatform = false
}
