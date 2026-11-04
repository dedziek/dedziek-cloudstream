version = 4
dependencies {
    add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
}
cloudstream {
    description = "AnimeWatch.pl: katalog anime, wyszukiwanie i odcinki z polskim lektorem IVO. Wersja testowa."
    authors = listOf("Dedziek CloudStream contributors")
    language = "pl"
    status = 3
    tvTypes = listOf("Anime", "AnimeMovie", "OVA")
    requiresResources = false
    isCrossPlatform = false
}
