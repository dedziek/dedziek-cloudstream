# EkinoProvider v8

Dodatek CloudStream dla `https://ekino-tv.pl/`. Dnia 4 listopada 2026 adres przekierowywał do `https://ekino.ws/`, wskazanej przez stronę jako jej oficjalna domena. Moduł korzysta z tego dokładnego pochodzenia; nie wybiera automatycznie nowych domen z reklam lub zewnętrznych list.

V8 dodaje wyłącznie ręcznie otwierane okno **Otwórz materiał do weryfikacji** w ustawieniach dodatku. Po CAPTCHA wykrytej w ramce Ekino zapamiętuje w pamięci ostatnią publiczną stronę filmu/odcinka. Nie zapisuje zaszyfrowanego identyfikatora ShowPlayer ani adresu `/watch/f/`; takie adresy mogą być związane z poprzednią anonimową sesją. W oknie użytkownik wybiera odtwarzacz i wykonuje weryfikację normalnie na stronie, a następnie zapisuje cookies Ekino wraz z rzeczywistym identyfikatorem WebView. Dodatek nie otwiera okna ani nie ponawia odtwarzania automatycznie. Rozwiązanie CAPTCHA, utrzymanie jej stanu po zapisaniu sesji i pełne odtwarzanie wymagają próby na urządzeniu; zapis cookies sam ich nie potwierdza.

V7 rozróżnia CAPTCHA w odtwarzaczu od strony ochronnej Cloudflare. Sam formularz CAPTCHA nie jest diagnozą problemu z internetem; wskazówka zmiany sieci dotyczy zapętlającej się weryfikacji. Błąd podaje krótki identyfikator hostingu z zakładki ShowPlayer, bez zaszyfrowanego identyfikatora źródła ani URL. Gdy wcześniejszy odtwarzacz zwrócił już wideo, późniejsza odmowa dostępu lub weryfikacja kończy pobieranie kolejnych źródeł i zachowuje znalezione linki. Nie ponawia się zablokowanego żądania ani nie rozwiązuje CAPTCHA automatycznie. Log użytkownika potwierdził znalezienie jednego źródła MixDrop dla **Bitwy o Pandorę (32972)**; dla **Powrotu (26101)** wskazał formularz weryfikacji w ramce. Nie potwierdzono odtwarzania Powrotu.

V6 dodaje odczyt statycznych źródeł MP4/HLS i zagnieżdżonych ramek dla hostingów bez ekstraktora CloudStream, w tym domen Upzone. Rozpoznaje elementy HTML video/source i deklaracje file/src w konfiguracji odtwarzacza, także po statycznym rozpakowaniu skryptu. Nie wykonuje JavaScriptu ani kliknięć. Rozpoznany ekstraktor, który nie zwrócił wideo, nie powoduje ponownego pobrania strony innym klientem. Odczyt ma ograniczenie liczby stron i głębokości ramek; odmowy dostępu zatrzymują dalsze żądania do danego hostingu. Cookies hostingów mają osobny, tymczasowy magazyn. Komunikat błędu pokazuje wyłącznie domeny hostingów i etap niepowodzenia, bez ścieżek, parametrów i danych sesji. Dla filmu **5x2 (43318)** potwierdzono zakładki player i upzone, ale nie potwierdzono odtwarzania w środowisku programistycznym. Przykłady formatów źródeł oraz przepływ żądań zweryfikowano testami.

V5 przywraca sposób działania wersji 3, sprzed automatycznego okna weryfikacji dodanego w v4. Weryfikacja nie otwiera już dodatkowego okna podczas odtwarzania. Gdy serwis wymaga Cloudflare, dodatek pokazuje komunikat o nieudanej weryfikacji połączenia i sugeruje sprawdzenie innej sieci, np. Wi-Fi zamiast danych komórkowych. Wskazówka nie oznacza, że każdy błąd HTTP 403 ma przyczynę w sieci użytkownika. Nie wykonuje się automatycznych zmian połączenia ani ponowień.

Numer wersji jest wyższy niż 4, aby standardowy aktualizator CloudStream zainstalował przywrócone zachowanie bez ręcznego cofania paczki.

V3 zachowuje anonimową sesję PHP pomiędzy pobraniem strony materiału a jego odtwarzaczami. Cookies takiej operacji pozostają wyłącznie w pamięci i są ograniczone do dokładnego pochodzenia Ekino oraz właściwych ścieżek. Dodano rozpoznanie zwykłego odnośnika z ramki `/watch/f/`, osobny odczyt publicznej strony pośredniej `play.ekino.link` i przekazanie właściwego referera do ekstraktora hostingu. Strona pośrednia nie otrzymuje cookies Ekino. Odpowiedź 401/403/429 lub interaktywna weryfikacja zatrzymuje operację bez automatycznych ponowień i rozwiązywania CAPTCHA. W razie braku wideo komunikat podaje liczbę źródeł, brakujących odnośników, błędów strony pośredniej i prób ekstraktorów, bez adresów i danych sesji.

V2 naprawia błąd „brak zwykłych odtwarzaczy” dla stron takich jak **The List (33512)**. Ich funkcja `ShowPlayer` osadza ramkę od razu, bez licznika `time`; V1 błędnie odrzucała ten poprawny wariant. Parser rozpoznaje teraz zarówno natychmiastowe osadzenie, jak i ograniczone odliczanie podane przez stronę. Nieznany sposób odmierzania czasu lub zmieniona trasa nadal oznaczają brak obsługi, bez zgadywania opóźnienia.

## Instalacja i korzystanie

1. Odśwież dodatki w repozytorium **Dedziek CloudStream** i zainstaluj **EkinoProvider**.
2. Wybierz źródło **Ekino**. Dostępne są najnowsze filmy z kolejnymi stronami, bieżący katalog seriali, wyszukiwanie filmów i seriali oraz listy sezonów i odcinków.
3. Gdy błąd wskazuje CAPTCHA odtwarzacza, otwórz zębatkę EkinoProvider i wybierz **Otwórz materiał do weryfikacji**. Wybierz na stronie ten sam odtwarzacz, wykonaj CAPTCHA i wybierz **Zapisz sesję**. Wróć do dodatku i uruchom materiał ponownie. Jeżeli aplikacja została zrestartowana, najpierw ponów próbę materiału, aby ponownie zapamiętać jego stronę. Jeżeli WebView nie przechodzi weryfikacji, sprawdź ten sam odtwarzacz w aktualnym Chrome; zapis sesji nie usuwa blokady witryny.
4. Katalog nie wymaga konta. Jeżeli materiał wymaga zalogowania, otwórz zębatkę EkinoProvider, wybierz **Otwórz logowanie**, zaloguj się w formularzu strony i wybierz **Zapisz sesję**. Weryfikację strony wykonujesz samodzielnie. Zapis cookies nie jest dowodem poprawnego logowania; sprawdź wybrany materiał.

Wyszukiwanie korzysta z aktualnego formularza `POST /search/qf` i pola `q`. Wyniki pochodzą z tego formularza i mają jego ograniczenia; dodatek nie wysyła zapytań do zewnętrznej wyszukiwarki pokazywanej jako alternatywa przez stronę. Filmy mają stronicowanie w formacie `strona[N]+`; lista seriali na stronie głównej nie ma fikcyjnego stronicowania. Odcinki są odczytywane z `#list-series`, sortowane po sezonie i numerze oraz deduplikowane.

## Odtwarzanie i ograniczenia

Dodatek odczytuje wyłącznie zwykłe źródła `ShowPlayer` reklamowane przez stronę i ich publiczne ramki/odnośniki. Zachowuje wersję językową z zakładki odtwarzacza. Przed załadowaniem źródeł uwzględnia odliczanie, jeśli strona je podaje; wariant bez licznika nie dodaje sztucznego oczekiwania. Gdy układ funkcji odtwarzacza zmieni się, zgłasza brak obsługi zamiast zgadywać nowy adres.

Zewnętrzne hostingi obsługują ekstraktory CloudStream oraz opisany wyżej odczyt statycznych źródeł dla hostingów bez ekstraktora. Ich dostępność i zgodność zależą również od hostingu. Moduł nie promuje odtwarzacza Premium ani ograniczonych podglądów do pełnych źródeł.

**Status: wersja testowa.** Potwierdzono odpowiedzi strony głównej, katalogu filmów, wyszukiwania, szczegółów filmu, serialu i odcinka. Parser sprawdzono na pobranych stronach i syntetycznych przykładach struktury. Żądanie do serwera darmowego odtwarzacza ze środowiska programistycznego otrzymało HTTP 403 Cloudflare. Nie ponawiano go z inną tożsamością, domeną ani przeglądarką. Pełne odtwarzanie oraz rzeczywiste logowanie wymagają próby na urządzeniu użytkownika; sama kompilacja ich nie potwierdza. W v3 strukturę dodatkowej strony pośredniej zweryfikowano na podstawie publicznego kodu integracji Ekino, a izolację cookies i przekazywanie referera sprawdzono testami. Odmowa dostępu ze środowiska programistycznego nie była obchodzona.

### Gdy weryfikacja się zapętla

Zgodnie z [instrukcją Cloudflare](https://developers.cloudflare.com/cloudflare-challenges/troubleshooting/challenge-solve-issues/) sama pętla weryfikacji nie dowodzi awarii internetu. Sprawdź ten sam materiał w aktualnym Chrome, aktualizacje Android System WebView, działanie JavaScriptu oraz ewentualne blokowanie skryptów przez rozszerzenia lub VPN/proxy. Dopiero porównanie z inną siecią pomaga rozróżnić problem połączenia i przeglądarki. Nie zmieniaj sieci w trakcie tej samej weryfikacji.

Okno logowania ma włączone JavaScript, DOM storage, cookies i cookies ramek zewnętrznych. Dopuszcza zasoby challenges.cloudflare.com oraz about:blank/about:srcdoc, nie zmienia identyfikatora User-Agent w trakcie sesji i nie traktuje błędów HTTP/DNS podzasobów jako błędu całej strony. Odpowiada to [wymaganiom dla implementacji mobilnej](https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/). Oba okna pozostają opcjonalne i otwierają się tylko po wybraniu opcji w ustawieniach. Po błędzie CAPTCHA można otworzyć stronę materiału zamiast samego formularza logowania. Sam zapis cookies po zalogowaniu nie potwierdza rozwiązania oddzielnej CAPTCHA konkretnego odtwarzacza.

HTTP 401 wymaga logowania, 403 oznacza odmowę dostępu, a 429 ograniczenie liczby żądań. Te odpowiedzi przerywają pobieranie źródeł bez automatycznych ponowień. Jeśli ten sam materiał nie działa na stronie na danym urządzeniu, sam dodatek nie przywróci dostępu. Anulowanie odtwarzania przerywa oczekiwanie.

## Sesja i aktualizacje

Hasło wpisywane jest wyłącznie w oryginalnym formularzu witryny. Sesja jest zapisywana osobno od pozostałych dodatków, w zaszyfrowanym AES-GCM pliku Android Keystore poza kopią zapasową. Sesja i rzeczywisty identyfikator WebView są wysyłane wyłącznie do dokładnej domeny HTTPS `ekino.ws`; cookies nie trafiają do hostingów wideo ani do starej domeny `ekino-tv.pl`. Przekierowania HTTP są sprawdzane przed kolejnym żądaniem. Nie eksportuje się haseł, cookies ani pełnych adresów odtwarzaczy do raportów. **Usuń zapisaną sesję** usuwa zapis Ekino i jego cookies, bez globalnego czyszczenia magazynu CloudStream.

Stała nazwa wewnętrzna `EkinoProvider` i rosnący numer wersji pozwalają korzystać ze standardowych automatycznych aktualizacji rozszerzeń CloudStream. GitHub Actions buduje, testuje i weryfikuje paczkę przed publikacją razem z pozostałymi własnymi dodatkami; synchronizacja ObejrzyjTo pozostaje osobnym katalogiem.

## Walidacja

Testy parsera obejmują aktualny układ wyszukiwania i katalogu, plakaty, metadane, nawiasy w adresach odcinków, sezony, duplikaty, paginację, wersje językowe, brak zgadywania nowych tras, pomijanie Premium, niebezpieczne adresy i rozpoznanie odmowy dostępu. Osobny test układu logowania sprawdza widoczny obszar strony i przyciski. Przykłady testowe nie zawierają danych sesji ani surowych stron logowania.
