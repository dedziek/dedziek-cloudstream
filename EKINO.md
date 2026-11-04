# EkinoProvider v1

Dodatek CloudStream dla `https://ekino-tv.pl/`. Dnia 4 listopada 2026 adres przekierowywał do `https://ekino.ws/`, wskazanej przez stronę jako jej oficjalna domena. Moduł korzysta z tego dokładnego pochodzenia; nie wybiera automatycznie nowych domen z reklam lub zewnętrznych list.

## Instalacja i korzystanie

1. Odśwież dodatki w repozytorium **Dedziek CloudStream** i zainstaluj **EkinoProvider**.
2. Wybierz źródło **Ekino**. Dostępne są najnowsze filmy z kolejnymi stronami, bieżący katalog seriali, wyszukiwanie filmów i seriali oraz listy sezonów i odcinków.
3. Katalog nie wymaga konta. Jeżeli materiał wymaga zalogowania, otwórz zębatkę EkinoProvider, wybierz **Otwórz logowanie**, zaloguj się w formularzu strony i wybierz **Zapisz sesję**. Weryfikację strony wykonujesz samodzielnie. Zapis cookies nie jest dowodem poprawnego logowania; sprawdź wybrany materiał.

Wyszukiwanie korzysta z aktualnego formularza `POST /search/qf` i pola `q`. Wyniki pochodzą z tego formularza i mają jego ograniczenia; dodatek nie wysyła zapytań do zewnętrznej wyszukiwarki pokazywanej jako alternatywa przez stronę. Filmy mają stronicowanie w formacie `strona[N]+`; lista seriali na stronie głównej nie ma fikcyjnego stronicowania. Odcinki są odczytywane z `#list-series`, sortowane po sezonie i numerze oraz deduplikowane.

## Odtwarzanie i ograniczenia

Dodatek odczytuje wyłącznie zwykłe źródła `ShowPlayer` reklamowane przez stronę i ich publiczne ramki/odnośniki. Zachowuje wersję językową z zakładki odtwarzacza. Przed załadowaniem źródeł uwzględnia odliczanie podane przez stronę, więc wybór materiału może zająć kilkanaście sekund. Gdy układ funkcji odtwarzacza zmieni się, zgłasza brak obsługi zamiast zgadywać nowy adres.

Zewnętrzne hostingi obsługują ekstraktory CloudStream; ich dostępność i zgodność zależą również od hostingu. Opcjonalnie obsługiwane są bezpośrednie źródła HTML `<video>`/`<source>`. Moduł nie promuje odtwarzacza Premium ani ograniczonych podglądów do pełnych źródeł.

**Status: wersja testowa.** Potwierdzono odpowiedzi strony głównej, katalogu filmów, wyszukiwania, szczegółów filmu, serialu i odcinka. Parser sprawdzono na pobranych stronach i syntetycznych przykładach struktury. Żądanie do serwera darmowego odtwarzacza ze środowiska programistycznego otrzymało HTTP 403 Cloudflare. Nie ponawiano go z inną tożsamością, domeną ani przeglądarką. Pełne odtwarzanie oraz rzeczywiste logowanie wymagają próby na urządzeniu użytkownika; sama kompilacja ich nie potwierdza.

HTTP 401 wymaga logowania, 403 oznacza odmowę dostępu, a 429 ograniczenie liczby żądań. Te odpowiedzi przerywają pobieranie źródeł bez automatycznych ponowień. Jeśli ten sam materiał nie działa na stronie na danym urządzeniu, sam dodatek nie przywróci dostępu. Anulowanie odtwarzania przerywa oczekiwanie.

## Sesja i aktualizacje

Hasło wpisywane jest wyłącznie w oryginalnym formularzu witryny. Sesja jest zapisywana osobno od pozostałych dodatków, w zaszyfrowanym AES-GCM pliku Android Keystore poza kopią zapasową. Sesja i rzeczywisty identyfikator WebView są wysyłane wyłącznie do dokładnej domeny HTTPS `ekino.ws`; cookies nie trafiają do hostingów wideo ani do starej domeny `ekino-tv.pl`. Przekierowania HTTP są sprawdzane przed kolejnym żądaniem. Nie eksportuje się haseł, cookies ani pełnych adresów odtwarzaczy do raportów. **Usuń zapisaną sesję** usuwa zapis Ekino i jego cookies, bez globalnego czyszczenia magazynu CloudStream.

Stała nazwa wewnętrzna `EkinoProvider` i rosnący numer wersji pozwalają korzystać ze standardowych automatycznych aktualizacji rozszerzeń CloudStream. GitHub Actions buduje, testuje i weryfikuje paczkę przed publikacją razem z pozostałymi własnymi dodatkami; synchronizacja ObejrzyjTo pozostaje osobnym katalogiem.

## Walidacja

Testy parsera obejmują aktualny układ wyszukiwania i katalogu, plakaty, metadane, nawiasy w adresach odcinków, sezony, duplikaty, paginację, wersje językowe, brak zgadywania nowych tras, pomijanie Premium, niebezpieczne adresy i rozpoznanie odmowy dostępu. Osobny test układu logowania sprawdza widoczny obszar strony i przyciski. Przykłady testowe nie zawierają danych sesji ani surowych stron logowania.
