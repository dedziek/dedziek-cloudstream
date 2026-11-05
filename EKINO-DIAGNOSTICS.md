# Próba Ekino, 5 listopada 2026

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
