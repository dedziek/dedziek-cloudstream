version = 5

dependencies {
    add("testImplementation", "org.robolectric:robolectric:4.16.1")
}
cloudstream {
    description = "Filmy i seriale z Filman. Wymaga darmowego konta i zapisania sesji w ustawieniach."
    iconUrl = "https://raw.githubusercontent.com/dedziek/dedziek-cloudstream/main/assets/icons/filman.png"
    authors = listOf("FilmanCloudStream contributors")
    language = "pl"
    status = 3
    tvTypes = listOf("Movie", "TvSeries")
    requiresResources = false
    isCrossPlatform = false
}
