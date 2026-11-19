version = 4
dependencies {
    add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
}
cloudstream {
    description = "Polski serwis z anime online - odcinki z napisami i lektorem z ogladajanime.pl."
    iconUrl = "https://raw.githubusercontent.com/dedziek/dedziek-cloudstream/main/assets/icons/ogladajanime-site.png"
    authors = listOf("Dedziek CloudStream contributors")
    language = "pl"
    status = 3
    tvTypes = listOf("Anime", "AnimeMovie", "OVA")
}
