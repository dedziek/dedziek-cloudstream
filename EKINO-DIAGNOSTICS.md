# Próba Ekino, 5 listopada 2026

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
