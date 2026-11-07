# Ekino TV

Dodatek **EkinoTvProvider v9**, źródło **Ekino TV**. Napisany od nowa dla `https://ekino-tv.pl`; przeglądarka obsługuje oficjalne przekierowanie do `https://ekino.ws`. To osobna paczka i klasa dodatku. Kod starego EkinoProvider v1–v13 został usunięty z aktualnej gałęzi, a historia zmian pozostaje dostępna w Git.

Wersja 9 używa oryginalnej ikony Ekino TV w katalogu dodatków. Zachowuje krótki opis z v8 oraz odliczanie i obsługę weryfikacji z v7.

## Instalacja

1. Usuń **EkinoProvider / Ekino** w rozszerzeniach CloudStream. Zainstalowanej kopii na telefonie nie usuwa wycofanie paczki z GitHuba.
2. Odśwież repozytorium **Dedziek CloudStream** i zainstaluj **EkinoTvProvider**. Możesz też pobrać [nową paczkę](https://raw.githubusercontent.com/dedziek/dedziek-cloudstream/builds/EkinoTvProvider.cs3).
3. Wybierz źródło **Ekino TV**. Wymagany jest Android 6+ z działającym Android System WebView oraz aktualny CloudStream Beta. Zgodność z konkretnym Android TV nie została sprawdzona.

## V7 — widoczne odliczanie odtwarzacza

Podczas ładowania bezpłatnego odtwarzacza komunikat w CloudStream pokazuje „Oczekiwanie na odtwarzacz: … s”. Wartość pochodzi z widocznego licznika Ekino, więc komunikat pojawia się tylko wtedy, gdy strona faktycznie każe czekać. Odtwarzacze bez licznika uruchamiają się tak jak dotychczas; dodatek nie ustala stanu zalogowania na podstawie samych cookies.

W obserwowanym skrypcie strony licznik zaczyna od `time = 10` i od razu pokazuje 9 sekund. Dlatego oczekiwanie trwa około 10 sekund, a pierwszy widoczny komunikat może wskazywać 9 s. Dodatek nie dokłada własnego odliczania. Po nim pozostaje czas na pobranie ramki i źródła z hostingu. Komunikat nie przechwytuje dotyku ani klawisza Wstecz/pilota; znika po uzyskaniu ramki, błędzie, anulowaniu lub przejściu aplikacji w tło.

Walidacja v7: **63 testy Androida i 5 testów skryptu DOM — wszystkie przeszły**, kompilacja oraz rzeczywisty test dostawcy CloudStream zakończony `Success Ekino TV`. W aplikacji sprawdzono widoczny licznik (8 s → 1 s), a następnie jego zniknięcie i jedno źródło wideo.

## V6 — wygaszanie ruchu i aktualne kontrolki odtwarzaczy

Po odczycie danych przeglądarka kończy bezczynny dokument po dwóch sekundach; nowa operacja anuluje ten licznik. Przejście aplikacji w tło, anulowanie, błąd oraz zamknięcie ręcznego okna również kończą dokument. Po zamknięciu dokumentu WebView odrzuca spóźnione żądania przed siecią, także POST do weryfikacji. V5 stosowała taką blokadę tylko po zatrzymaniu ręcznej weryfikacji, a po samym odczycie katalogu pozostawiała dokument działający.

W rzeczywistym DOM skrypt podpowiedzi przenosi opis języka z `title` do `original-title`. V6 odczytuje oba warianty oraz `data-original-title`, rozpoznaje równoważne polskie opisy i po sukcesie pomija kolejne hostingi tego samego języka. Gdy opisu w ogóle nie ma, znalezienie wideo również kończy sprawdzanie kolejnych odtwarzaczy bez opisu. Odrębne, jawnie opisane języki pozostają dostępne. Poprzednia wersja mogła bez potrzeby otwierać kolejne ramki po znalezieniu wideo.

Identyfikatory kontrolek odtwarzaczy należą do konkretnego wczytania materiału. Po ponownym otwarciu filmu V6 wybiera aktualną kontrolkę tego samego hostingu i języka; przy niejednoznacznym wyniku kończy operację. Nie uruchamia funkcji strony ze starym identyfikatorem z pamięci metadanych. Zachowuje odliczanie witryny i nie dodaje automatycznych ponowień. Wyszukiwanie używa formularza POST w tym samym WebView; znając domenę sesji, nie otwiera dodatkowo strony głównej tylko po to, by utworzyć formularz.

Walidacja: **60 testów, 0 błędów** i zbudowana paczka. Finalny v6 przeszedł rzeczywisty **Test dostawcy** CloudStream: „W moim stylu”, jedno źródło `player · Lektor PL`, `Success Ekino TV`. Raport odnotował jedną ramkę odtwarzacza, cztery dokumenty główne i brak żądań weryfikacji. To wynik tego testu, bez gwarancji dla każdej sieci i filmu.

Aktualizacja zachowuje rzeczywiste wcześniejsze wstrzymanie. Jeśli po zmianie sieci lub restarcie routera strona znów działa, otwórz **Otwórz weryfikację**, uruchom odtwarzacz na stronie i wybierz **Sprawdź i zamknij** przy gotowym źródle. Sam restart routera nie usuwa zapisanego stanu dodatku.

**Kopiuj raport** podaje zbiorcze liczniki od uruchomienia: dopuszczone dokumenty główne, ramki odtwarzacza, żądania trasy weryfikacji oraz żądania zatrzymane lokalnie. Są to zdarzenia zaobserwowane przez WebView, nie pełny pomiar sieci ani liczba odpowiedzi serwera. Adresów IP, cookies i tokenów nie zapisuje.

Po restarcie routera użytkownik zgłosił ustąpienie CAPTCHA, ale nie uruchamiał jeszcze v5 ponownie. Nie ustalono, czy zmienił się publiczny adres IP. W połączeniu z wcześniejszym powodzeniem na danych komórkowych wskazuje to na zależność od sieci; nie dowodzi konkretnej reguły Cloudflare ani tego, że v5 ponownie spowodowała blokadę. Dokładną regułę odmowy może potwierdzić właściciel strony w logach serwera. V6 usuwa odtworzone błędy po stronie klienta; nie obiecuje, że serwer nigdy nie wyświetli weryfikacji.

## V5 — aktywna ramka i zatrzymanie odmowy weryfikacji

Dodatek aktywuje kartę wybranego hostingu przed uruchomieniem funkcji strony. W odczycie DOM rozróżnia ramki aktywne od ukrytych, także przy zagnieżdżaniu. CAPTCHA pozostawiona w poprzedniej karcie nie blokuje poprawnego źródła w bieżącej karcie; ukryty odtwarzacz nie może też sam potwierdzić weryfikacji.

Po wykryciu CAPTCHA w bieżącej sesji **Otwórz weryfikację** kieruje bezpośrednio do zatrzymanej ramki. Jej adres pozostaje tylko w pamięci tej sesji; po restarcie nadal dostępny jest zapisany film. Przycisk **Wczytaj film** pozwala wrócić do wyboru hostingu. Widoczna strona weryfikacji może wyświetlić HTTP 403, zamiast być od razu zastąpiona pustą stroną. HTTP 429 nadal zatrzymuje próby.

Gdy strona odtwarzacza zgłasza znany komunikat o odrzuceniu weryfikacji, dodatek kończy jej dokument i blokuje spóźnione ponowienia do świadomego ponownego otwarcia. Raport rozróżnia tę odmowę od samej obecności CAPTCHA i podaje wersję WebView.

W rzeczywistym CloudStream 4.8.0-PRE na Androidzie 15 katalog, wyszukiwanie i szczegóły filmu „Jego ostatnie życzenie” zostały odczytane. Oba zwykłe hostingi (`player` i `dood`) wymagały CAPTCHA. W niezależnej sesji Chrome żądanie strony do `/watch/verify.php` zwróciło HTTP 200 i `{"error":true}`. V5 naprawia błędy obsługi po stronie dodatku; nie gwarantuje przyjęcia potwierdzenia przez Ekino i nie uznaje samego „Success” widgetu za dostęp do wideo. Odtwarzanie w tej sieci pozostaje niepotwierdzone.

## V4 — widoczne ustawienia i dostęp do weryfikacji

W v1–v3 ustawienia łączyły `setMessage()` z `setItems()`. Android wyświetlał komunikat i „Zamknij”, a lista akcji była niewidoczna. To błąd nowego menu. V4 używa jednego przewijanego widoku z osobnymi przyciskami **Otwórz weryfikację**, **Otwórz stronę / logowanie** i **Kopiuj raport**.

Po aktualizacji do v4 i ponownym uruchomieniu CloudStream otwórz zębatkę dodatku i wybierz **Otwórz weryfikację**. Otworzy się zapamiętany film; gdy nie ma zapamiętanego materiału, otworzy się strona główna. Wybierz odtwarzacz na stronie i wykonaj weryfikację. Dopiero gdy dostęp do ramki jest uzyskany, wybierz **Sprawdź i zamknij**. Samo otwarcie okna lub zielony widget CAPTCHA nie zwalniają pauzy. **Kopiuj raport** działa również wtedy, gdy automatyczne próby są wstrzymane.

Stary moduł `EkinoProvider` został usunięty z bieżącego kodu, katalogu i paczki. Nazwa lokalnego zapisu `dedziek_ekino_tv_v1` dotyczy danych pierwszej wersji nowego Ekino TV. Pozostaje stabilna, aby aktualizacja zachowała publiczny adres materiału i stan wymaganej weryfikacji. Migracja zwykłego timeoutu v1 usuwa błędne wstrzymanie nowego dodatku; nie pobiera sesji starego EkinoProvider.

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

Otwórz zębatkę dodatku → **Otwórz weryfikację**. Wybierz zwykły odtwarzacz na stronie i wykonaj weryfikację. Naciśnij **Sprawdź i zamknij**, dopiero gdy odtwarzacz udostępni link. Dodatek sprawdza zawartość ramki, zamiast uznawać sam zielony znacznik CAPTCHA lub obecność cookies za potwierdzenie dostępu. Przycisk **Otwórz stronę / logowanie** udostępnia zwykły formularz witryny; konto nie jest wymagane przez dodatek.

Przy powtarzającej się nawigacji do tego samego dokumentu przeglądarka zatrzymuje ładowanie. Ograniczenie wynosi trzy żądania danego dokumentu w ciągu 15 sekund i najwyżej sześć do ręcznego ponowienia; wewnętrzne ramki Cloudflare nie są do tego wliczane. Okno przerywa oczekiwanie także po trzech minutach. **Wczytaj ponownie** to świadoma, ręczna próba użytkownika. Zamknięcie okna bez zakończenia weryfikacji pozostawia pauzę. Pauza i publiczny adres ostatniego materiału przetrwają restart aplikacji.

Jeśli także Chrome na Wi‑Fi potwierdza CAPTCHA i natychmiast ją odświeża, przerwij próby w tej sieci. W zgłoszonym przypadku ten sam telefon działał na danych komórkowych. To wskazuje na udział sieci, adresu IP lub trasy; nie jest dowodem konkretnego typu ani czasu blokady. Dodatek nie może zagwarantować akceptacji weryfikacji przez witrynę. Zewnętrzny Chrome i WebView CloudStream nadal mają odrębne sesje.

Link pomocy Cloudflare można otworzyć przez świadome kliknięcie w widocznej weryfikacji. Osobne okno dopuszcza tylko `challenges.cloudflare.com`. Samo otwarcie tej pomocy nie jest traktowane jako zakończenie weryfikacji.

## Raport i sprawdzenie

**Kopiuj raport** pokazuje etapy nawigacji, faktycznie otrzymane błędy HTTP/WebView, wysłanie żądania do trasy weryfikacji, numeryczne kody klienta Turnstile oraz wynik odczytu hostingu. Raport nie zapisuje cookies, tokenów CAPTCHA, treści formularzy ani pełnych adresów wideo. Nie podaje wymyślonego HTTP 200 dla nawigacji WebView, której statusu Android nie ujawnił.

Walidacja historyczna v5: **50 testów, 0 błędów**; ówczesny test aplikacji zatrzymał się na CAPTCHA. V6 sprawdzono osobno — szczegóły w analizie zgłoszenia.

Testy lokalne obejmują parser katalogu i seriali, zwykłe odtwarzacze, CAPTCHA w ramce przy HTTP 200, warunki wznowienia sesji, utrwalanie pauzy, limit ponownej nawigacji, zachowanie znalezionego źródła, protokół Byse i uwierzytelnienie jego odpowiedzi. Nie zastępują odtwarzania filmu na fizycznym telefonie.

Walidację kolejnych wersji opisano w [analizie zgłoszenia](EKINO-TV-AUDIT.md). Testy sterują rzeczywistym kodem dodatku i callbackami WebView; nie są próbą odtwarzania filmu na fizycznym telefonie.

[Analiza całej historii v1–v13 i zgłoszeń](EKINO-TV-AUDIT.md).

Dokumentacja użyta przy projektowaniu sesji: [Cloudflare — WebView i spójność środowiska](https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/), [Android WebView](https://developer.android.com/reference/android/webkit/WebView), [Android WebChromeClient](https://developer.android.com/reference/android/webkit/WebChromeClient). Dodatek pozostawia weryfikację użytkownikowi i nie implementuje rozwiązywania Turnstile.
