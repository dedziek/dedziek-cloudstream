package pl.dedziek.ekinotv

import kotlinx.coroutines.CancellationException

/** Serialized by the provider, with a bounded number of free mirrors and no automatic retries. */
internal class Playback(
    private val browser: SiteBrowser,
    private val resolve: suspend (String, String) -> HostResult,
    private val note: (String) -> Unit
) {
    suspend fun links(material: WebPage, emit: suspend (Media, Player) -> Unit): Boolean {
        val players = Catalogue.players(material).take(4)
        if (players.isEmpty()) throw SiteProblem("Ekino TV: nie znaleziono zwykłych odtwarzaczy. Sprawdź materiał w ustawieniach dodatku.")
        val languages = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        var found = false
        try {
            for (player in players) {
                if (player.languageKey in languages) continue
                val wrapper = browser.player(material.url, player)
                val links = Catalogue.embeds(wrapper).take(4)
                note("${player.host}: odnośniki=${links.size}")
                var playerFound = false
                for (link in links) {
                    val external = if (Site.bridge(link)) browser.bridge(link) else listOf(link)
                    for (embed in external.take(3)) {
                        val result = try { resolve(embed, wrapper.url) }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { HostResult(message = "błąd połączenia z hostingiem") }
                        note("Hosting ${runCatching { java.net.URI(embed).host }.getOrDefault("?")}: ${result.message}")
                        for (media in result.media) {
                            if (!seen.add("${media.url}|${player.languageKey}")) continue
                            emit(media, player); found = true; playerFound = true
                        }
                        if (result.blocked) {
                            if (found) return true
                            throw SiteProblem("Ekino TV: ${result.message}. Sprawdź odtwarzacz na stronie; próby zatrzymano.")
                        }
                        if (playerFound) break
                    }
                    if (playerFound) break
                }
                if (playerFound) languages.add(player.languageKey)
            }
        } catch (error: CancellationException) { throw error }
        catch (error: SiteProblem) {
            if (!found) throw error
            note("Zachowano znalezione wideo; kolejne źródło wymaga interwencji")
        }
        if (!found) throw SiteProblem("Ekino TV: odtwarzacze nie udostępniły wideo. Raport jest w ustawieniach dodatku.")
        return true
    }
}
