version = 6
dependencies {
    add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    add("testImplementation", "org.robolectric:robolectric:4.16.1")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
}
cloudstream {
    description = "AnimeGate: logowanie Discord, sesja i test struktury API. Prototyp nie udostępnia jeszcze katalogu ani odtwarzania w CloudStream."
    authors = listOf("Dedziek CloudStream contributors")
    language = "pl"
    status = 3
    tvTypes = listOf("Anime")
}
