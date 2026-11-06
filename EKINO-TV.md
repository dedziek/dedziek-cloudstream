# Ekino TV

Dodatek **EkinoTvProvider v3**, źródło **Ekino TV**. Napisany od nowa dla `https://ekino-tv.pl`; przeglądarka obsługuje oficjalne przekierowanie do `https://ekino.ws`. To osobna paczka i klasa dodatku. Kod starego EkinoProvider v1–v13 został usunięty z aktualnej gałęzi, a historia zmian pozostaje dostępna w Git.

## Instalacja

1. Usuń **EkinoProvider / Ekino** w rozszerzeniach CloudStream. Zainstalowanej kopii na telefonie nie usuwa wycofanie paczki z GitHuba.
2. Odśwież repozytorium **Dedziek CloudStream** i zainstaluj **EkinoTvProvider**. Możesz też pobrać [nową paczkę](https://raw.githubusercontent.com/dedziek/dedziek-cloudstream/builds/EkinoTvProvider.cs3).
3. Wybierz źródło **Ekino TV**. Wymagany jest Android 6+ z działającym Android System WebView oraz aktualny CloudStream Beta. Zgodność z konkretnym Android TV nie została sprawdzona.

## V3 — spóźnione błędy poprzedniego filmu

Podczas automatycznego przełączania filmów WebView może przez chwilę zwracać poprzedni adres. V3 przypisuje błędy do adresu żądanej strony i wybranego odtwarzacza. Spóźnione HTTP 403 albo błąd certyfikatu poprzedniego filmu nie zatrzymują wtedy następnego. Odmowa dostępu i błąd HTTPS dotyczące aktualnie otwieranego materiału nadal przerywają ładowanie; błędny certyfikat jest zawsze odrzucany.

Jeżeli masz v1 lub v2, zaktualizuj ten sam EkinoTvProvider do v3 i uruchom ponownie CloudStream. V3 zawiera opisane poniżej poprawki timeoutu z v2.

## V2 — przełączanie filmów i limit czasu

V1 po zwykłym przekroczeniu czasu zapisywała trwałe wstrzymanie i kierowała do weryfikacji. Zgłoszenie z 23:47:46 pokazuje właśnie taki zapisany stan: strona główna i wszystkie wyszukiwania zostały odrzucone natychmiast. Sam ten log nie potwierdza CAPTCHA.

V2 odczytuje gotowy dokument strony (`interactive` lub `complete`) bez czekania na zakończenie ładowania reklam i pozostałych zasobów. Sprawdza adres oraz tożsamość dokumentu, aby poprzedni film lub stare zdarzenie przeglądarki nie zastąpiły nowego wyniku. Po zamknięciu filmu ponownie przygotowuje stronę potrzebną do wyszukiwania.

Własny timeout strony wynosi 20 sekund, odtwarzacza 30 sekund. Przekroczenie własnego limitu przerywa jedną operację i wprowadza 10 sekund przerwy, bez ponawiania żądań w tle. Przerwa wygasa sama; nie wymaga CAPTCHA. Anulowanie lub krótszy limit CloudStream są przekazywane do aplikacji bez zapisywania blokady. Raport podaje etap: katalog, materiał, wyniki wyszukiwania, skrypt odtwarzacza lub ramka.

Aktualizacja automatycznie usuwa zwykły timeout błędnie zapisany przez v1. Zapisana rzeczywista CAPTCHA pozostaje do ręcznej weryfikacji. Po aktualizacji do v2 uruchom ponownie CloudStream.

## Zasada działania

Katalog, formularz wyszukiwania, szczegóły filmu, odcinki i ramka odtwarzacza działają w jednej instancji WebView. Przeglądarka zachowuje własny User-Agent, cookies i DOM storage. Dodatek nie eksportuje cookies do klienta HTTP, nie importuje sesji ze starego dodatku i nie podszywa się pod Chrome. Cookies WebView są przechowywane przez Androida w profilu WebView aplikacji; inne dodatki CloudStream mogą korzystać z tego samego profilu.

Wybranie zwykłego odtwarzacza uruchamia funkcję `ShowPlayer` dostarczoną przez stronę, razem z jej odliczaniem. Dodatek odczytuje publiczny link, następnie korzysta z ekstraktora hostingu, zwykłych źródeł HTML albo publicznego API Byse. Klient hostingów ma oddzielne, krótkotrwałe cookies i nie otrzymuje sesji Ekino. Tokenów, linków wideo i wyników odtwarzania nie zapisuje na dysku. Metadane maksymalnie pięciu materiałów są przechowywane w pamięci przez minutę.

Całe operacje są wykonywane kolejno. Jedna próba sprawdza najwyżej cztery zwykłe odtwarzacze, a po znalezieniu źródła pomija pozostałe z takim samym opisem języka. Wykryta CAPTCHA, logowanie HTTP 401, odmowa HTTP 403/429 i pętla weryfikacji zatrzymują kolejne automatyczne operacje do ręcznego sprawdzenia. Pozostałe błędy sieci i serwera przerywają bieżącą operację i wprowadzają 10 sekund przerwy. Już znalezione źródło pozostaje dostępne. Zakończenie odtwarzania lub zamknięcie okna usuwa aktywny dokument, aby zatrzymać jego skrypty i odświeżanie; sesja przeglądarki pozostaje zachowana.

## Weryfikacja lub logowanie

Otwórz zębatkę dodatku → **Otwórz ostatni materiał**. Wybierz zwykły odtwarzacz na stronie i wykonaj weryfikację. Naciśnij **Sprawdź i zamknij**, dopiero gdy odtwarzacz udostępni link. Dodatek sprawdza zawartość ramki, zamiast uznawać sam zielony znacznik CAPTCHA lub obecność cookies za potwierdzenie dostępu. Przycisk **Otwórz stronę / logowanie** udostępnia zwykły formularz witryny; konto nie jest wymagane przez dodatek.

Przy powtarzającej się nawigacji do tego samego dokumentu przeglądarka zatrzymuje ładowanie. Ograniczenie wynosi trzy żądania danego dokumentu w ciągu 15 sekund i najwyżej sześć do ręcznego ponowienia; wewnętrzne ramki Cloudflare nie są do tego wliczane. Okno przerywa oczekiwanie także po trzech minutach. **Wczytaj ponownie** to świadoma, ręczna próba użytkownika. Zamknięcie okna bez zakończenia weryfikacji pozostawia pauzę. Pauza i publiczny adres ostatniego materiału przetrwają restart aplikacji.

Jeśli także Chrome na Wi‑Fi potwierdza CAPTCHA i natychmiast ją odświeża, przerwij próby w tej sieci. W zgłoszonym przypadku ten sam telefon działał na danych komórkowych. To wskazuje na udział sieci, adresu IP lub trasy; nie jest dowodem konkretnego typu ani czasu blokady. Dodatek nie może zagwarantować akceptacji weryfikacji przez witrynę. Zewnętrzny Chrome i WebView CloudStream nadal mają odrębne sesje.

Link pomocy Cloudflare można otworzyć przez świadome kliknięcie w widocznej weryfikacji. Osobne okno dopuszcza tylko `challenges.cloudflare.com`. Samo otwarcie tej pomocy nie jest traktowane jako zakończenie weryfikacji.

## Raport i sprawdzenie

**Kopiuj raport** pokazuje etapy nawigacji, faktycznie otrzymane błędy HTTP/WebView, wysłanie żądania do trasy weryfikacji, numeryczne kody klienta Turnstile oraz wynik odczytu hostingu. Raport nie zapisuje cookies, tokenów CAPTCHA, treści formularzy ani pełnych adresów wideo. Nie podaje wymyślonego HTTP 200 dla nawigacji WebView, której statusu Android nie ujawnił.

Testy lokalne obejmują parser katalogu i seriali, zwykłe odtwarzacze, CAPTCHA w ramce przy HTTP 200, warunki wznowienia sesji, utrwalanie pauzy, limit ponownej nawigacji, zachowanie znalezionego źródła, protokół Byse i uwierzytelnienie jego odpowiedzi. Nie zastępują odtwarzania filmu na fizycznym telefonie.

Walidację v2 i v3 opisano w [analizie zgłoszenia](EKINO-TV-AUDIT.md). Testy sterują rzeczywistym kodem dodatku i callbackami WebView; nie są próbą odtwarzania filmu na fizycznym telefonie.

[Analiza całej historii v1–v13 i zgłoszeń](EKINO-TV-AUDIT.md).

Dokumentacja użyta przy projektowaniu sesji: [Cloudflare — WebView i spójność środowiska](https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/), [Android WebView](https://developer.android.com/reference/android/webkit/WebView), [Android WebChromeClient](https://developer.android.com/reference/android/webkit/WebChromeClient). Dodatek pozostawia weryfikację użytkownikowi i nie implementuje rozwiązywania Turnstile.
