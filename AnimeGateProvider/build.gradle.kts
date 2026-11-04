version = 1
dependencies {
    add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    add("testImplementation", "org.robolectric:robolectric:4.16.1")
}
cloudstream {
    description = "AnimeGate: prototyp logowania Discord i sprawdzania sesji. Ta wersja nie udostępnia jeszcze katalogu ani odtwarzania."
    authors = listOf("Dedziek CloudStream contributors")
    language = "pl"
    status = 3
    tvTypes = listOf("Anime")
}
