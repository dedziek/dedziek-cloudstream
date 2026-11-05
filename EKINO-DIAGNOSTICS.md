# Próba Ekino, 5 listopada 2026

## V13 — ograniczenie zbędnych ramek

Próba z jedną sesją PHP i odliczaniem strony: trzy pobrania strony Gang-over, po dwa zwykłe odtwarzacze. Pierwszych pięć ramek zwróciło HTTP 200 bez CAPTCHA; szósta (upzone) zwróciła Turnstile. PHP pozostało to samo. Nie odczytywano hostingów ani mediów. Zatrzymano próbę przy pierwszej bramce, bez wysyłania tokenów. To odtworzenie pojawienia się weryfikacji, nie interaktywnej pętli ani stałego limitu liczby odczytów.

V12 odwiedzała kolejne hostingi nawet po uzyskaniu wideo. V13 po sukcesie pomija pozostałe ramki z tą samą, niepustą etykietą języka. Zachowuje inne języki i alternatywy po nieudanym odczycie. Dla układu player/upzone o tej samej wersji językowej, przy działającym player, liczba odczytanych ramek spada z dwóch do jednej. Nie jest to usunięcie bieżącej pętli w Chrome.

Odczytany skrypt Ekino POSTuje token do `/watch/verify.php`, następnie wykonuje reload w obu gałęziach odpowiedzi. Zielony stan widżetu nie potwierdza akceptacji tokenu przez serwer Ekino. Nie ustalono powodu odrzucenia ani braku utrwalenia stanu na telefonie użytkownika. Nie zapisano w repozytorium cookies, tokenów ani zaszyfrowanych identyfikatorów ramek.


Walidacja v13: kompilacja i `:EkinoProvider:testDebugUnitTest` zakończone poprawnie — **97 testów, 0 błędów i pominięć**. Nowe testy obejmują pomijanie kolejnych ramek dopiero po sukcesie, alternatywy po niepowodzeniu, zachowanie różnych języków i pustych etykiet, niezależność kolejnych uruchomień oraz zatrzymanie przy odmowie. Paczka v13 ma 108 099 bajtów i SHA-256 `e6a1da4ef2af8fd608ba1878dea0f400cee6ac5a707e5aaae57502461660267a`.

## V12 — timeout przed wyborem odtwarzacza

Nowy log urządzenia: start testu 17:07:30, timeout strony głównej 17:07:55, następnie próby wyszukiwania `over`, `iron`, `guy` i koniec 17:09:29 bez wyników. Nie dotarł do obsługi Byse ani pozostałych hostingów. Nie ma w nim kodu HTTP ani informacji, czy oczekiwanie zatrzymało się przy DNS, TCP, TLS czy odpowiedzi serwera. Nie potwierdza blokady IP ani błędu ekstraktora Byse.

Sprawdzono publiczny katalog i zapytanie `over` z tego środowiska: HTTP 200, odpowiednio około 0,11 s i 0,26 s w próbie urllib. Oddzielna próba rzeczywistej klasy `EkinoSiteHttp` z nowym obserwatorem połączenia i parserem dodatku dała katalog HTTP 200, 10 pozycji, około 2,04 s razem z parsowaniem. Wyszukiwanie zwróciło zwykłe HTTP 302 na tym samym pochodzeniu, a po obsłużeniu przekierowania jak w providerze: HTTP 200, 21 pozycji, około 0,39 s. DNS/TCP/TLS zakończyły się poprawnie; jedno User-Agent w żądaniu. To inna sieć i środowisko niż urządzenie użytkownika, więc nie odtwarza jego timeoutu.

V12 dodaje pasywny `EventListener` dla natywnych żądań Ekino i strony pośredniej. Zapisuje kategorie i czasy etapów bez URL, adresów IP, cookies i surowych komunikatów wyjątków. Raport **ostatniego połączenia** można skopiować w ustawieniach niezależnie od raportu odtwarzania. Pozostaje w pamięci procesu; kopiowanie nie uruchamia żadnego sprawdzenia sieci. Pierwszy błąd I/O zapisuje wstrzymanie przed zwolnieniem kolejki, więc dalsze katalogi/wyszukiwania nie wykonują kolejnych 25-sekundowych prób. Anulowanie coroutine nie powoduje zapisania wstrzymania. Nie dodano ponowień, wymuszania IPv4 ani alternatywnego DNS.

Porównano też wskazaną przez użytkownika obsługę Byse w AnimeWatch. `AnimeWatchByse` dziedziczy `ByseSX`; provider ma dodatkowy mechanizm przeglądarkowy. Odczytany upstream `ByseSX` używa GET `details`, `embed_frame_url`, GET `playback` i pierwszych dwóch fragmentów klucza. Zaobserwowany w Ekino `mfw09.org` używał POST inicjalizacji oraz payloadu wersji 5 z 30 fragmentami klucza. Wspólna nazwa hostingu nie oznacza identycznego wariantu API. Nie zmieniano AnimeWatch w ramach diagnostyki timeoutu katalogu Ekino.

Do ustalenia przyczyny na urządzeniu potrzebny jest teraz **Raport ostatniego połączenia** po jednej próbie oraz informacja, czy równocześnie ta sama strona otwiera się w Chrome w tej samej sieci. Poniżej pozostawiono wcześniejsze wyniki v11 i v10.

Po pytaniu o identyfikator z widżetu dodano także odczyt nagłówka `cf-ray`, ograniczony do formatu Ray ID. Sam identyfikator służy do korelacji z logami administratora; nie jest diagnozą. Nie zapisano rzeczywistego identyfikatora użytkownika w testach ani kodzie. Gdy żądanie nie otrzyma nagłówków HTTP, raport wskazuje brak Ray ID.

Walidacja v12: `sh gradlew :EkinoProvider:testDebugUnitTest :EkinoProvider:make --no-daemon --console=plain` — **92 testy, 0 błędów i pominięć**. Nowe testy używają prawdziwego lokalnego serwera HTTP: jedna opóźniona odpowiedź kończy się timeoutem, kolejna oczekująca operacja nie dochodzi do sieci. Obejmują także błąd DNS, etap TLS, zachowanie po anulowaniu coroutine, trwałość wstrzymania i kopiowanie raportu przed jakąkolwiek próbą odtwarzania na Android API 28/35. Paczka: wersja 12, 112 170 bajtów, SHA-256 `12248d2a5c5382c1c11afdf4821a36fd0f5e7af17a7699cc2f2407e8b7fa4b24`. To naprawa kolejki po błędzie i diagnostyka; przyczyna timeoutu na urządzeniu nadal nie jest potwierdzona.

## V11 — Powrót: potwierdzone źródło Byse

Raport urządzenia z v10 dla filmu **Powrót (26101)** potwierdził HTTP 200 bez CAPTCHA dla strony filmu, wszystkich czterech ramek i strony pośredniej. Sesja PHP docierała do ramek i nie była zmieniana. Nie był to błąd CAPTCHA w tej konkretnej próbie: problem dotyczył kolejnych hostingów.

Odczyty publicznych stron z tego środowiska wykazały:

| Hosting | Wynik | Zmiana w v11 |
|---|---|---|
| mfw09.org | HTTP 200, aplikacja `Byse Frontend` zamiast statycznego źródła | Obsługa publicznej inicjalizacji API Byse |
| dood.la → playmogo.com | HTTP 200, tytuł „Video not found” | Plik niedostępny w tej próbie; brak ponownego odczytu po pustym ekstraktorze |
| onlystream.tv | Błąd DNS | Osobny komunikat o DNS |
| upzone.cc | HTTP 200, informacja o wyczerpanym transferze darmowym i Premium | Osobny komunikat o limicie, bez dalszego odczytu źródeł |

Publiczny przepływ Byse `captcha` → `captcha/verify` → `playback` zwrócił trzy razy HTTP 200. Zadanie obliczeniowe miało trudność 16; serwer przyjął wynik. Odpowiedź AES-256-GCM, wersja 5 z 30 fragmentami klucza, zawierała jedno źródło HLS oznaczone 1080p. Pobranie samej playlisty, bez segmentów filmu, dało HTTP 200 i poprawne `#EXTM3U` (607 bajtów).

Następnie wykonano oddzielną próbę **rzeczywistego skompilowanego kodu** `EkinoByseResolver` i `EkinoHostHttp`, z domyślnymi ograniczeniami obliczeń. Kod zwrócił `Byse API OK`, jedno źródło, czas około 1,99 s. Odczyt playlisty z nagłówkami tego źródła ponownie zwrócił HTTP 200 i poprawne HLS. To weryfikuje pobieranie źródła i playlisty; nie potwierdza renderowania obrazu/dźwięku na Androidzie.

Żądania API pozostają na pochodzeniu odtwarzacza, a cookies hostingów są oddzielone od Ekino. Nie użyto API klucza konta ani Premium. Przy odczycie jednego statycznego pliku JavaScript uzyskano HTTP 403; nie ponawiano odczytu tego zasobu. Obsługę normalnej inicjalizacji publicznego odtwarzacza porównano z implementacją [MoonGetter](https://github.com/darkryh/MoonGetter/tree/master/server-src/moongetter-filemoon), MIT, z zachowaniem licencji w źródle i pliku DEX. Interaktywna CAPTCHA/Cloudflare, odmowa API, przekierowanie lub przekroczenie budżetu nie są automatycznie ponawiane.

Walidacja v11: `sh gradlew :EkinoProvider:testDebugUnitTest :EkinoProvider:make --no-daemon --console=plain` — **84 testy, 0 błędów i pominięć**. Obejmują pełną sekwencję API, wektor obliczeń publicznego odtwarzacza, dwa formaty uwierzytelnionego szyfrogramu, zmianę danych szyfrogramu, limity, anulowanie, blokadę dalszych odczytów po odmowie oraz brak automatycznego rozwiązywania interaktywnej weryfikacji. Paczka v11: 107 618 bajtów, SHA-256 `c58d251ab6d721a40171852d85ecf02abd32650ba5faaf96ddd56d462ffaafde`; manifest i obecność informacji MIT w DEX zweryfikowane.

Poniżej zachowano wcześniejszą diagnostykę v10 dla innego filmu i poprzednią sumę paczki; nie są wynikami kompilacji v11.

Zgłoszenie: test CloudStream znajduje film **Zabójcza gra / Murder & Matrimony (33270)**, a ładowanie źródeł kończy się komunikatem o CAPTCHA odtwarzacza `mix`. Użytkownik zgłasza też zapętloną weryfikację w Chrome.

## Wynik odczytu publicznych stron

Próba z tego środowiska korzystała z jednego magazynu cookies PHP, zwykłych żądań GET i referera strony filmu. Przed ramkami uwzględniono reklamowane 10 sekund odliczania. Nie wykonywano skryptów, CAPTCHA, logowania ani ponowień po odmowie. Klient próby: Python urllib, User-Agent `Mozilla/5.0`; nie jest to pomiar zachowania Android WebView ani połączenia użytkownika.

| Etap | HTTP | Sesja PHP | CAPTCHA / Cloudflare | Domena odnośnika |
|---|---|---|---|---|
| Strona filmu | 200 | utworzona | brak znaczników | — |
| player | 200 | bez zmiany | brak znaczników | play.ekino.link |
| dood | 200 | bez zmiany | brak znaczników | dood.la |
| hqq | 200 | bez zmiany | brak znaczników | player.ekino-tv.link |
| mix | 200 | bez zmiany | brak znaczników | mdzsmutpcvykb.net |
| upzone | 200 | bez zmiany | brak znaczników | upzone.cc |

Dodatkowa wcześniejsza próba samej ramki `mix` również dała HTTP 200 i zwykły link. Nie pobierano stron zewnętrznych hostingów ani mediów. Ten wynik potwierdza dostępność ramek w badanym środowisku; nie potwierdza odtwarzania ani nie wyklucza ograniczenia po stronie serwisu dotyczącego urządzenia lub sieci użytkownika.

## Znaleziony błąd i zmiana

NiceHttp 0.4.11 tworzy nagłówki przez `defaultHeaders + headers`, a następnie `toHeaders()`. CloudStream ustawia domyślny nagłówek `user-agent`; Ekino v9 podawało `User-Agent`. Mapa Kotlin rozróżnia te klucze, natomiast HTTP traktuje je jako tę samą nazwę. W szczególności po zapisaniu sesji WebView żądanie mogło zawierać dwa różne identyfikatory przeglądarki. Test regresji odtwarza ten wynik bez połączenia z serwisem.

V10 używa osobnej instancji Requests bez domyślnych nagłówków i cookies. Zachowuje transport/DNS bazowego klienta CloudStream, wyłącza globalny CookieJar oraz `retryOnConnectionFailure`. Test z MockWebServer sprawdza nagłówki faktycznie otrzymane przez serwer: jeden User-Agent, cookie bieżącej operacji, brak dostępu do globalnego magazynu cookies oraz brak śledzenia przekierowań. To naprawa błędu klienta; związek z konkretną blokadą użytkownika pozostaje do potwierdzenia.

Źródła implementacji: [NiceHttp Requests 0.4.11](https://github.com/Blatzar/NiceHttp/blob/0.4.11/library/src/main/java/com/lagradost/nicehttp/Requests.kt), [składanie nagłówków](https://github.com/Blatzar/NiceHttp/blob/0.4.11/library/src/main/java/com/lagradost/nicehttp/Utils.kt), [domyślny klient CloudStream](https://github.com/recloudstream/cloudstream/blob/master/library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainActivity.kt).

## Test na urządzeniu po instalacji v10

Jeśli strona nadal nie działa w Chrome, pozostaw żądania wstrzymane. Gdy ten sam materiał działa już ręcznie, wznów dodatek świadomie i wykonaj **jedną** próbę odtwarzania. Jeśli wystąpi CAPTCHA, nie wznawiaj kolejnych testów: skopiuj **Raport ostatniego odtwarzania** z ustawień Ekino albo komunikat z wbudowanego testu CloudStream. Raport jest pasywny, nie wysyła żądań i nie zawiera cookies, HTML ani identyfikatorów źródeł.

Raport pozwala ustalić, czy PHP docierało do ramki, czy serwer zmienił sesję, ile ramek poprzedziło CAPTCHA, jaki znacznik ją wywołał i czy po odpowiedzi wstrzymano ruch. Zapisana sesja/clearance oznacza obecność cookie, nie dowód poprawnego logowania lub przejścia weryfikacji.

## Walidacja lokalna

`sh gradlew :EkinoProvider:testDebugUnitTest :EkinoProvider:make --no-daemon --console=plain` zakończone poprawnie: **72 testy, 0 błędów, 0 pominiętych**, w tym układ i kopiowanie raportu na Android API 28 i 35, utrzymanie wstrzymania żądań, parser, izolacja sesji oraz rzeczywiste nagłówki lokalnego serwera HTTP.

Paczka zawiera `manifest.json` i `classes.dex`; manifest wskazuje `EkinoProvider`, wersję `10` i `pl.ekinocloudstream.EkinoPlugin`. Rozmiar: 90 832 bajty; SHA-256: `bd01d3c50ff290525969fd84a4795f00016e6e9039cc157a3d3bda079af6e62c`. Testy nie potwierdzają rozwiązania CAPTCHA ani odtwarzania na fizycznym urządzeniu.
