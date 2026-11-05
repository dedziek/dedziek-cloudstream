package pl.ekinocloudstream

internal object EkinoMessages {
    const val CLOUDFLARE = "Ekino: strona wymaga weryfikacji Cloudflare. Jeśli weryfikacja się zapętla, spróbuj zmienić sieć (Wi-Fi / dane komórkowe) i uruchom materiał ponownie."
    const val BRIDGE_VERIFICATION = "Ekino: strona pośrednia wymaga ręcznej weryfikacji CAPTCHA. Sprawdź materiał w przeglądarce. Jeśli weryfikacja się zapętla, spróbuj innej sieci (Wi-Fi / dane komórkowe)."
    const val ACCESS_DENIED = "Ekino: hosting odmówił dostępu (HTTP 403). Jeśli Cloudflare nie przechodzi, spróbuj innej sieci (Wi-Fi / dane komórkowe)."

    fun playerVerification(host: String = ""): String {
        // Use only the short ShowPlayer host identifier, never the encrypted player ID or URL.
        val label = host.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,32}")) }?.let { " ($it)" }.orEmpty()
        return "Ekino: odtwarzacz$label wymaga ręcznej weryfikacji CAPTCHA. Sprawdź materiał w przeglądarce. Jeśli weryfikacja się zapętla, spróbuj innej sieci (Wi-Fi / dane komórkowe)."
    }
}
