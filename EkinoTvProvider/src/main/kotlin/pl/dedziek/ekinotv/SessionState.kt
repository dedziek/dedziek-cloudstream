package pl.dedziek.ekinotv

import android.content.Context
import java.util.ArrayDeque

internal class SessionState(context: Context) {
    private val preferences = context.getSharedPreferences("dedziek_ekino_tv_v1", Context.MODE_PRIVATE)
    private val events = ArrayDeque<String>()
    @Volatile var paused: Boolean = preferences.getBoolean("paused", false); private set
    @Volatile var playerRequired: Boolean = preferences.getBoolean("playerRequired", false); private set
    @Volatile var lastMaterial: String? = preferences.getString("material", null)?.takeIf(Site::material); private set
    @Volatile var reason: String = preferences.getString("reason", "").orEmpty(); private set

    @Synchronized fun note(message: String) {
        if (events.size >= 40) events.removeFirst()
        events.addLast(message.take(240))
    }

    fun remember(url: String) {
        val public = Site.publicPage(url)?.takeIf(Site::material) ?: return
        lastMaterial = public
        preferences.edit().putString("material", public).apply()
    }

    fun pause(message: String, player: Boolean = false) {
        paused = true; reason = message; playerRequired = playerRequired || player
        preferences.edit().putBoolean("paused", true).putBoolean("playerRequired", playerRequired).putString("reason", message).apply()
        note("Wstrzymano: $message")
    }

    /** Only a real DOM result from the current browser can release a verification pause. */
    fun accept(snapshot: BrowserSnapshot): Boolean {
        if (!snapshot.verified(playerRequired)) return false
        paused = false; playerRequired = false; reason = ""
        preferences.edit().putBoolean("paused", false).putBoolean("playerRequired", false).remove("reason").apply()
        note("Użytkownik potwierdził stronę bez bramki weryfikacji")
        return true
    }

    @Synchronized fun report(): String = buildString {
        append("Ekino TV v1 · raport\nSilnik: Android System WebView\n")
        append("Automatyczne operacje: ${if (paused) "wstrzymane" else "dozwolone"}\n")
        if (reason.isNotBlank()) append("Powód: $reason\n")
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
