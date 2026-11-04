version = 10
dependencies {
    add("compileOnly", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    add("testImplementation", "org.robolectric:robolectric:4.16.1")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
}
cloudstream {
    description = "AnimeGate: katalog, odcinki i integracja odtwarzania Playmogo po logowaniu Discord. Wersja testowa, podgląd do 8 wyników."
    authors = listOf("Dedziek CloudStream contributors")
    language = "pl"
    status = 3
    tvTypes = listOf("Anime")
}
