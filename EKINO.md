# EkinoProvider v4

Dodatek CloudStream dla `https://ekino-tv.pl/`. Dnia 4 listopada 2026 adres przekierowywał do `https://ekino.ws/`, wskazanej przez stronę jako jej oficjalna domena. Moduł korzysta z tego dokładnego pochodzenia; nie wybiera automatycznie nowych domen z reklam lub zewnętrznych list.

V4 otwiera widoczne okno weryfikacji bezpośrednio podczas odtwarzania, jeśli odtwarzacz Ekino wymaga ręcznego potwierdzenia. Okno pokazuje stronę wybranego filmu lub odcinka. Użytkownik otwiera odtwarzacz i sam wykonuje weryfikację; dodatek nie klika Play ani CAPTCHA i nie wysyła własnych odpowiedzi weryfikacyjnych. Przycisk **Gotowe — kontynuuj** zapisuje sesję z rzeczywistym identyfikatorem WebView i wznawia ten sam odczyt w CloudStream. Powstają świeże identyfikatory źródeł, właściwe dla zapisanej sesji.

Weryfikacja może być otwarta tylko raz na próbę odtwarzania. Zamknięcie, anulowanie albo limit trzech minut przerywa tę próbę; utrzymująca się blokada nie uruchamia pętli. HTTP 403 i inne odmowy dostępu nie otwierają automatycznie innej przeglądarki. Jeżeli zwykły link wideo jest już dostępny, sam obecny na stronie znacznik CAPTCHA nie wymusza okna. Ramki Cloudflare i reCAPTCHA nie są traktowane jako źródła wideo.

V3 zachowuje anonimową sesję PHP pomiędzy pobraniem strony materiału a jego odtwarzaczami. Cookies takiej operacji pozostają wyłącznie w pamięci i są ograniczone do dokładnego pochodzenia Ekino oraz właściwych ścieżek. Dodano rozpoznanie zwykłego odnośnika z ramki `/watch/f/`, osobny odczyt publicznej strony pośredniej `play.ekino.link` i przekazanie właściwego referera do ekstraktora hostingu. Strona pośrednia nie otrzymuje cookies Ekino. Odpowiedź 401/403/429 lub interaktywna weryfikacja zatrzymuje operację bez automatycznych ponowień i rozwiązywania CAPTCHA. W razie braku wideo komunikat podaje liczbę źródeł, brakujących odnośników, błędów strony pośredniej i prób ekstraktorów, bez adresów i danych sesji.

V2 naprawia błąd „brak zwykłych odtwarzaczy” dla stron takich jak **The List (33512)**. Ich funkcja `ShowPlayer` osadza ramkę od razu, bez licznika `time`; V1 błędnie odrzucała ten poprawny wariant. Parser rozpoznaje teraz zarówno natychmiastowe osadzenie, jak i ograniczone odliczanie podane przez stronę. Nieznany sposób odmierzania czasu lub zmieniona trasa nadal oznaczają brak obsługi, bez zgadywania opóźnienia.

## Instalacja i korzystanie

1. Odśwież dodatki w repozytorium **Dedziek CloudStream** i zainstaluj **EkinoProvider**.
2. Wybierz źródło **Ekino**. Dostępne są najnowsze filmy z kolejnymi stronami, bieżący katalog seriali, wyszukiwanie filmów i seriali oraz listy sezonów i odcinków.
3. Katalog nie wymaga konta. Jeżeli materiał wymaga zalogowania, otwórz zębatkę EkinoProvider, wybierz **Otwórz logowanie**, zaloguj się w formularzu strony i wybierz **Zapisz sesję**. Weryfikację strony wykonujesz samodzielnie. Zapis cookies nie jest dowodem poprawnego logowania; sprawdź wybrany materiał.

Wyszukiwanie korzysta z aktualnego formularza `POST /search/qf` i pola `q`. Wyniki pochodzą z tego formularza i mają jego ograniczenia; dodatek nie wysyła zapytań do zewnętrznej wyszukiwarki pokazywanej jako alternatywa przez stronę. Filmy mają stronicowanie w formacie `strona[N]+`; lista seriali na stronie głównej nie ma fikcyjnego stronicowania. Odcinki są odczytywane z `#list-series`, sortowane po sezonie i numerze oraz deduplikowane.

## Odtwarzanie i ograniczenia

Dodatek odczytuje wyłącznie zwykłe źródła `ShowPlayer` reklamowane przez stronę i ich publiczne ramki/odnośniki. Zachowuje wersję językową z zakładki odtwarzacza. Przed załadowaniem źródeł uwzględnia odliczanie, jeśli strona je podaje; wariant bez licznika nie dodaje sztucznego oczekiwania. Gdy układ funkcji odtwarzacza zmieni się, zgłasza brak obsługi zamiast zgadywać nowy adres.

Zewnętrzne hostingi obsługują ekstraktory CloudStream; ich dostępność i zgodność zależą również od hostingu. Opcjonalnie obsługiwane są bezpośrednie źródła HTML `<video>`/`<source>`. Moduł nie promuje odtwarzacza Premium ani ograniczonych podglądów do pełnych źródeł.

**Status: wersja testowa.** Potwierdzono odpowiedzi strony głównej, katalogu filmów, wyszukiwania, szczegółów filmu, serialu i odcinka. Parser sprawdzono na pobranych stronach i syntetycznych przykładach struktury. Żądanie do serwera darmowego odtwarzacza ze środowiska programistycznego otrzymało HTTP 403 Cloudflare. Nie ponawiano go z inną tożsamością, domeną ani przeglądarką. Pełne odtwarzanie oraz rzeczywiste logowanie wymagają próby na urządzeniu użytkownika; sama kompilacja ich nie potwierdza. W v3 strukturę dodatkowej strony pośredniej zweryfikowano na podstawie publicznego kodu integracji Ekino, a izolację cookies i przekazywanie referera sprawdzono testami. Odmowa dostępu ze środowiska programistycznego nie była obchodzona.

HTTP 401 wymaga logowania, 403 oznacza odmowę dostępu, a 429 ograniczenie liczby żądań. Te odpowiedzi przerywają pobieranie źródeł bez automatycznych ponowień. Ręczna weryfikacja rozpoznana w odtwarzaczu ma osobny przepływ opisany dla v4; ponowny odczyt jest wykonywany tylko po świadomym użyciu przycisku „Gotowe — kontynuuj”. Jeśli ten sam materiał nie działa na stronie na danym urządzeniu, sam dodatek nie przywróci dostępu. Anulowanie odtwarzania przerywa oczekiwanie.

## Sesja i aktualizacje

Hasło wpisywane jest wyłącznie w oryginalnym formularzu witryny. Sesja jest zapisywana osobno od pozostałych dodatków, w zaszyfrowanym AES-GCM pliku Android Keystore poza kopią zapasową. Sesja i rzeczywisty identyfikator WebView są wysyłane wyłącznie do dokładnej domeny HTTPS `ekino.ws`; cookies nie trafiają do hostingów wideo ani do starej domeny `ekino-tv.pl`. Przekierowania HTTP są sprawdzane przed kolejnym żądaniem. Nie eksportuje się haseł, cookies ani pełnych adresów odtwarzaczy do raportów. **Usuń zapisaną sesję** usuwa zapis Ekino i jego cookies, bez globalnego czyszczenia magazynu CloudStream.

Stała nazwa wewnętrzna `EkinoProvider` i rosnący numer wersji pozwalają korzystać ze standardowych automatycznych aktualizacji rozszerzeń CloudStream. GitHub Actions buduje, testuje i weryfikuje paczkę przed publikacją razem z pozostałymi własnymi dodatkami; synchronizacja ObejrzyjTo pozostaje osobnym katalogiem.

## Walidacja

Testy parsera obejmują aktualny układ wyszukiwania i katalogu, plakaty, metadane, nawiasy w adresach odcinków, sezony, duplikaty, paginację, wersje językowe, brak zgadywania nowych tras, pomijanie Premium, niebezpieczne adresy i rozpoznanie odmowy dostępu. Osobny test układu logowania sprawdza widoczny obszar strony i przyciski. Przykłady testowe nie zawierają danych sesji ani surowych stron logowania.
