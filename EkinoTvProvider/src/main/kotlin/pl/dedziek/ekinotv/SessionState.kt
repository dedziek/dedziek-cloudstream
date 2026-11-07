package pl.dedziek.ekinotv

import android.content.Context
import android.os.SystemClock
import java.util.ArrayDeque

internal class SessionState(context: Context, private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {
    private val preferences = context.getSharedPreferences("dedziek_ekino_tv_v1", Context.MODE_PRIVATE)
    private val events = ArrayDeque<String>()
    private val requests = linkedMapOf("strony" to 0, "ramki" to 0, "weryfikacja" to 0, "zatrzymane" to 0)
    @Volatile var paused: Boolean = preferences.getBoolean("paused", false); private set
    @Volatile var playerRequired: Boolean = preferences.getBoolean("playerRequired", false); private set
    @Volatile var lastMaterial: String? = preferences.getString("material", null)?.takeIf(Site::material); private set
    @Volatile var reason: String = preferences.getString("reason", "").orEmpty(); private set
    @Volatile private var retryAt: Long = 0
    @Volatile private var transientReason = ""

    init {
        // V1 persisted an ordinary timeout as if it required CAPTCHA. Preserve real gates.
        if (paused && !playerRequired && reason == "Przekroczono czas oczekiwania na stronę") {
            paused = false; reason = ""
            preferences.edit().putBoolean("paused", false).remove("reason").apply()
            note("Usunięto błędne trwałe wstrzymanie po limicie czasu v1")
        }
    }

    @Synchronized fun note(message: String) {
        if (events.size >= 40) events.removeFirst()
        events.addLast(message.take(240))
    }

    /** Aggregate counts only; never retain URLs, cookies, form data or challenge tokens. */
    @Synchronized fun request(kind: String) { requests[kind]?.let { requests[kind] = it + 1 } }

    fun remember(url: String) {
        val public = Site.publicPage(url)?.takeIf(Site::material) ?: return
        lastMaterial = public
        preferences.edit().putString("material", public).apply()
    }

    fun pause(message: String, player: Boolean = false) {
        retryAt = 0
        paused = true; reason = message; playerRequired = playerRequired || player
        preferences.edit().putBoolean("paused", true).putBoolean("playerRequired", playerRequired).putString("reason", message).apply()
        note("Wstrzymano: $message")
    }

    /** A bounded cooldown, without retries, persistent pause or a claim about CAPTCHA. */
    fun temporaryFailure(message: String) {
        if (!paused) { transientReason = message; retryAt = clock() + 10_000 }
        note("Przerwano bieżącą operację: $message")
    }

    fun rejection(): String? {
        if (paused) return "Ekino TV: $reason. Otwórz ustawienia dodatku → „${SettingsUi.VERIFY}”. Wybierz odtwarzacz na stronie, a po weryfikacji naciśnij „Sprawdź i zamknij”."
        val remaining = retryAt - clock()
        return if (remaining > 0) "Ekino TV: $transientReason. Spróbuj ponownie za ${(remaining + 999) / 1000} s. Dodatek nie ponawia żądań w tle." else null
    }

    /** Only a real DOM result from the current browser can release a verification pause. */
    fun accept(snapshot: BrowserSnapshot): Boolean {
        if (!snapshot.verified(playerRequired)) return false
        paused = false; playerRequired = false; reason = ""
        retryAt = 0; transientReason = ""
        preferences.edit().putBoolean("paused", false).putBoolean("playerRequired", false).remove("reason").apply()
        note("Użytkownik potwierdził stronę bez bramki weryfikacji")
        return true
    }

    @Synchronized fun report(): String = buildString {
        append("Ekino TV v9 · raport\nSilnik: Android System WebView\n")
        append("Dokumenty/POST WebView od uruchomienia; zatrzymane = wszystkie żądania odrzucone lokalnie: ")
        append(requests.entries.joinToString(", ") { "${it.key}=${it.value}" }); append('\n')
        val cooldown = (retryAt - clock()).coerceAtLeast(0)
        append("Automatyczne operacje: ${when {
            paused -> "wstrzymane"
            cooldown > 0 -> "przerwa ${(cooldown + 999) / 1000} s"
            else -> "dozwolone"
        }}\n")
        if (reason.isNotBlank()) append("Powód: $reason\n")
        if (transientReason.isNotBlank()) append("Ostatni błąd przejściowy: $transientReason\n")
        append("Ostatni materiał: ${if (lastMaterial != null) "zapamiętany lokalnie" else "brak"}\n")
        append(events.joinToString("\n"))
    }
}

/** Count only our own protected documents, never Cloudflare's internal frame navigations. */
internal class NavigationBudget(private val limit: Int = 3, private val windowMs: Long = 15_000) {
    private val entries = ArrayDeque<Pair<String, Long>>()
    private val totals = mutableMapOf<String, Int>()
    @Synchronized fun reset() { entries.clear(); totals.clear() }
    @Synchronized fun record(url: String, now: Long): Boolean {
        val public = Site.publicPage(url) ?: return true
        while (entries.isNotEmpty() && now - entries.first.second > windowMs) entries.removeFirst()
        entries.addLast(public to now)
        totals[public] = (totals[public] ?: 0) + 1
        return entries.count { it.first == public } <= limit && totals.getValue(public) <= 6
    }
}
