package pl.ogladajanimecloudstream

import org.junit.Assert.*
import org.junit.Test

class OgladajAnimeParserTest {
    private val base = OgladajAnimeParser.BASE

    private val sampleCardsHtml = """
        <div class="col-12 col-lg-6 anime-item m-0 mb-3 mb-md-0 p-0 p-md-3">
            <div class="card bg-white ">
                <div class="row card-body justify-content-center pb-0 pt-1 py-md-3">
                    <figure class="col-4 m-0 p-0">
                        <a href="/anime/naruto-shippuuden" aria-label="Naruto Shippuuden">
                            <img class="img-fluid lozad rounded tooltip tooltip-anime" 
                                data-srcset="https://cdn.ogladajanime.pl/images/anime_new/664/1.webp?20250121233823"
                                src="https://cdn.ogladajanime.pl/images/anime_new/664/0.webp?20250121233823"
                                alt="Naruto Shippuuden" />
                        </a>
                        <span class="col-6 m-0 mt-2 badge badge-warning">TV</span>
                    </figure>
                    <div class="col-8 overflow-hidden pl-3 pl-md-2 pr-2 text-center">
                        <h5 class="card-title text-trim m-0 p-0">
                            <a href="/anime/naruto-shippuuden">Naruto Shippuuden</a>
                        </h5>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-6 col-sm-4 col-md-3 col-lg-2 m-0 p-0 px-2 pb-3">
            <div class="card bg-white m-0 p-0">
                <div class="row card-body justify-content-center m-0 p-0">
                    <div class="col-12 m-0 p-0">
                        <a href="/anime/naruto-movie" aria-label="Naruto Movie">
                            <img class="img-fluid lozad rounded" src="https://cdn.ogladajanime.pl/images/anime_new/999/0.webp" alt="Naruto Movie" />
                        </a>
                    </div>
                    <div class="col-12 text-sm">
                        <span class="badge badge-warning">Movie</span>
                    </div>
                </div>
            </div>
            <div class="text-center ml-1 mr-1 text-trim-2 text-bold">Naruto Movie</div>
        </div>
    """.trimIndent()

    private val sampleDetailHtml = """
        <!doctype html>
        <html>
        <head>
            <title>Naruto Shippuuden - Oglądaj Anime</title>
            <meta name="description" content="Opis przygód Naruto po powrocie do wioski." />
        </head>
        <body>
            <div class="row">
                <div class="col-3 p-0 pl-2">
                    <img class="img-fluid lozad rounded float-right"
                         data-srcset="https://cdn.ogladajanime.pl/images/anime_new/664/2.webp"
                         src="https://cdn.ogladajanime.pl/images/anime_new/664/0.webp"
                         alt="Naruto Shippuuden" />
                </div>
                <div class="col-9 text-left">
                    <h4 id="anime_name_id" anime_id="664" class="m-0">Naruto: Shippuuden</h4>
                    <div class="outlines mt-1">
                        <span class="btn disabled btn-outline-warning">TV</span>
                    </div>
                    <p id="animeDesc" class="text-gray content">Opis przygód Naruto po powrocie do wioski.</p>
                    <div class="row text-gray mt-3">
                        <p class="m-0"><b class="text-muted">Status:</b> Zakończone</p>
                        <p class="m-0"><b class="text-muted">Start emisji:</b> 2007-02-15</p>
                        <span class="mr-1"><b class="text-muted">Gatunki:</b></span>
                        <a class="badge badge-outline-secondary text-gray mr-1" href="/search/custom/1">Akcja</a>
                        <a class="badge badge-outline-secondary text-gray mr-1" href="/search/custom/2">Przygoda</a>
                    </div>
                </div>
            </div>
            <div id="ep_list">
                <a class="list-group-item" title="Zapowiedź" onclick="changeEpisode(this, '0', 223794);" value="0" ep_id="223794">
                    <span class="badge">0</span>
                    <p class="text-trim">Zapowiedź</p>
                </a>
                <a class="list-group-item" title="Powrót do wioski" onclick="changeEpisode(this, '1', 62788);" value="1" ep_id="62788">
                    <span class="badge">1</span>
                    <p class="text-trim">Powrót do wioski</p>
                </a>
                <a class="list-group-item" title="Akatsuki wkracza do akcji" onclick="changeEpisode(this, '2', 62787);" value="2" ep_id="62787">
                    <span class="badge">2</span>
                    <p class="text-trim">Akatsuki wkracza do akcji</p>
                </a>
            </div>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun cardsFromHtmlExtractsTitlesAndPosters() {
        val cards = OgladajAnimeParser.cardsFromHtml(sampleCardsHtml, base)
        assertEquals(2, cards.size)
        assertEquals("Naruto Shippuuden", cards[0].title)
        assertEquals("$base/anime/naruto-shippuuden", cards[0].url)
        assertEquals("https://cdn.ogladajanime.pl/images/anime_new/664/1.webp?20250121233823", cards[0].poster)
        assertEquals("TV", cards[0].type)

        assertEquals("Naruto Movie", cards[1].title)
        assertEquals("$base/anime/naruto-movie", cards[1].url)
        assertEquals("Movie", cards[1].type)
    }

    @Test
    fun detailsExtractsMetadataAndEpisodes() {
        val doc = OgladajAnimeParser.document(sampleDetailHtml, base)
        val detail = OgladajAnimeParser.details(doc, "$base/anime/naruto-shippuuden")

        assertEquals("Naruto: Shippuuden", detail.title)
        assertEquals("https://cdn.ogladajanime.pl/images/anime_new/664/2.webp", detail.poster)
        assertEquals("Opis przygód Naruto po powrocie do wioski.", detail.plot)
        assertEquals(2007, detail.year)
        assertEquals("Zakończone", detail.status)
        assertEquals("TV", detail.type)
        assertEquals(listOf("Akcja", "Przygoda"), detail.genres)

        assertEquals(2, detail.episodes.size)
        assertEquals("Powrót do wioski", detail.episodes[0].title)
        assertEquals(1.0, detail.episodes[0].episode)
        assertEquals("62788", detail.episodes[0].epId)
        assertEquals("$base/anime/naruto-shippuuden?watchepisode=62788&subaction=player", detail.episodes[0].url)

        assertEquals("Akatsuki wkracza do akcji", detail.episodes[1].title)
        assertEquals(2.0, detail.episodes[1].episode)
        assertEquals("62787", detail.episodes[1].epId)
    }

    @Test
    fun siteUrlValidation() {
        assertTrue(OgladajAnimeParser.isSiteUrl("https://ogladajanime.pl/anime/naruto"))
        assertTrue(OgladajAnimeParser.isSiteUrl("https://www.ogladajanime.pl/anime/naruto"))
        assertTrue(OgladajAnimeParser.isSiteUrl("/anime/naruto"))
        assertFalse(OgladajAnimeParser.isSiteUrl("https://malicious.com/anime/naruto"))
    }
}
