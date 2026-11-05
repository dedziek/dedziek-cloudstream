# Ekino TV

Nowy dodatek **EkinoTvProvider v1**, źródło **Ekino TV**. Napisany od nowa dla `https://ekino-tv.pl`; przeglądarka obsługuje oficjalne przekierowanie do `https://ekino.ws`. To osobna paczka i klasa dodatku. Kod starego EkinoProvider v1–v13 został usunięty z aktualnej gałęzi, a historia zmian pozostaje dostępna w Git.

## Instalacja

1. Usuń **EkinoProvider / Ekino** w rozszerzeniach CloudStream. Zainstalowanej kopii na telefonie nie usuwa wycofanie paczki z GitHuba.
2. Odśwież repozytorium **Dedziek CloudStream** i zainstaluj **EkinoTvProvider**. Możesz też pobrać [nową paczkę](https://raw.githubusercontent.com/dedziek/dedziek-cloudstream/builds/EkinoTvProvider.cs3).
3. Wybierz źródło **Ekino TV**. Wymagany jest Android 6+ z działającym Android System WebView oraz aktualny CloudStream Beta. Zgodność z konkretnym Android TV nie została sprawdzona.

## Zasada działania

Katalog, formularz wyszukiwania, szczegóły filmu, odcinki i ramka odtwarzacza działają w jednej instancji WebView. Przeglądarka zachowuje własny User-Agent, cookies i DOM storage. Dodatek nie eksportuje cookies do klienta HTTP, nie importuje sesji ze starego dodatku i nie podszywa się pod Chrome. Cookies WebView są przechowywane przez Androida w profilu WebView aplikacji; inne dodatki CloudStream mogą korzystać z tego samego profilu.

Wybranie zwykłego odtwarzacza uruchamia funkcję `ShowPlayer` dostarczoną przez stronę, razem z jej odliczaniem. Dodatek odczytuje publiczny link, następnie korzysta z ekstraktora hostingu, zwykłych źródeł HTML albo publicznego API Byse. Klient hostingów ma oddzielne, krótkotrwałe cookies i nie otrzymuje sesji Ekino. Tokenów, linków wideo i wyników odtwarzania nie zapisuje na dysku. Metadane maksymalnie pięciu materiałów są przechowywane w pamięci przez minutę.

Całe operacje są wykonywane kolejno. Jedna próba sprawdza najwyżej cztery zwykłe odtwarzacze, a po znalezieniu źródła pomija pozostałe z takim samym opisem języka. CAPTCHA, błąd sieci, błąd HTTPS lub odpowiedź HTTP 4xx/5xx strony zatrzymują automatyczne ładowanie. Już znalezione źródło pozostaje dostępne. Zakończenie odtwarzania lub zamknięcie okna usuwa aktywny dokument, aby zatrzymać jego skrypty i odświeżanie; sesja przeglądarki pozostaje zachowana.

## Weryfikacja lub logowanie

Otwórz zębatkę dodatku → **Otwórz ostatni materiał**. Wybierz zwykły odtwarzacz na stronie i wykonaj weryfikację. Naciśnij **Sprawdź i zamknij**, dopiero gdy odtwarzacz udostępni link. Dodatek sprawdza zawartość ramki, zamiast uznawać sam zielony znacznik CAPTCHA lub obecność cookies za potwierdzenie dostępu. Przycisk **Otwórz stronę / logowanie** udostępnia zwykły formularz witryny; konto nie jest wymagane przez dodatek.

Przy powtarzającej się nawigacji do tego samego dokumentu przeglądarka zatrzymuje ładowanie. Ograniczenie wynosi trzy żądania danego dokumentu w ciągu 15 sekund i najwyżej sześć do ręcznego ponowienia; wewnętrzne ramki Cloudflare nie są do tego wliczane. Okno przerywa oczekiwanie także po trzech minutach. **Wczytaj ponownie** to świadoma, ręczna próba użytkownika. Zamknięcie okna bez zakończenia weryfikacji pozostawia pauzę. Pauza i publiczny adres ostatniego materiału przetrwają restart aplikacji.

Jeśli także Chrome na Wi‑Fi potwierdza CAPTCHA i natychmiast ją odświeża, przerwij próby w tej sieci. W zgłoszonym przypadku ten sam telefon działał na danych komórkowych. To wskazuje na udział sieci, adresu IP lub trasy; nie jest dowodem konkretnego typu ani czasu blokady. Dodatek nie może zagwarantować akceptacji weryfikacji przez witrynę. Zewnętrzny Chrome i WebView CloudStream nadal mają odrębne sesje.

Link pomocy Cloudflare można otworzyć przez świadome kliknięcie w widocznej weryfikacji. Osobne okno dopuszcza tylko `challenges.cloudflare.com`. Samo otwarcie tej pomocy nie jest traktowane jako zakończenie weryfikacji.

## Raport i sprawdzenie

**Kopiuj raport** pokazuje etapy nawigacji, faktycznie otrzymane błędy HTTP/WebView, wysłanie żądania do trasy weryfikacji, numeryczne kody klienta Turnstile oraz wynik odczytu hostingu. Raport nie zapisuje cookies, tokenów CAPTCHA, treści formularzy ani pełnych adresów wideo. Nie podaje wymyślonego HTTP 200 dla nawigacji WebView, której statusu Android nie ujawnił.

Testy lokalne obejmują parser katalogu i seriali, zwykłe odtwarzacze, CAPTCHA w ramce przy HTTP 200, warunki wznowienia sesji, utrwalanie pauzy, limit ponownej nawigacji, zachowanie znalezionego źródła, protokół Byse i uwierzytelnienie jego odpowiedzi. Nie zastępują odtwarzania filmu na fizycznym telefonie.

Walidacja v1: pełna kompilacja czterech dodatków i **178 testów JUnit (29 dla Ekino TV), 22 testy Node i 8 testów Python** zakończyły się poprawnie. Sprawdzono nową klasę w manifeście, brak starej klasy w DEX, informację MIT w paczce i katalog bez starego wpisu. Lokalna paczka: 85 543 bajty, SHA-256 `ee797326153f59a5450188e9e9977a4cc88ae29045d08fa86ce0afbfa62906b6`.

[Analiza całej historii v1–v13 i zgłoszeń](EKINO-TV-AUDIT.md).

Dokumentacja użyta przy projektowaniu sesji: [Cloudflare — WebView i spójność środowiska](https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/), [Android WebView](https://developer.android.com/reference/android/webkit/WebView), [Android WebChromeClient](https://developer.android.com/reference/android/webkit/WebChromeClient). Dodatek pozostawia weryfikację użytkownikowi i nie implementuje rozwiązywania Turnstile.
