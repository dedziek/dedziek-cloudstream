version = 6
dependencies {
    add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    add("testImplementation", "org.robolectric:robolectric:4.16.1")
}
cloudstream {
    description = "Anime, filmy i odcinki z polskim lektorem. Bez konta; możliwa weryfikacja hostingu."
    iconUrl = "https://raw.githubusercontent.com/dedziek/dedziek-cloudstream/main/assets/icons/animewatch.png"
    authors = listOf("Dedziek CloudStream contributors")
    language = "pl"
    status = 3
    tvTypes = listOf("Anime", "AnimeMovie", "OVA")
    requiresResources = false
    isCrossPlatform = false
}
