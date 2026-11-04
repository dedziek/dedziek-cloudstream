# AnimeGateProvider v4 — prototyp logowania

Pierwszy etap [planu wdrożenia](ANIMEGATE-PLAN.md). Ta wersja dodaje ustawienia logowania i test sesji. **Nie rejestruje źródła katalogu ani odtwarzania**, ponieważ dostęp po Discord OAuth trzeba najpierw potwierdzić na urządzeniu. Publiczne żądanie z środowiska programistycznego otrzymało HTTP 403/1010; nie próbowano obchodzić tej odmowy.

## Próba na telefonie

1. Odśwież repozytorium Dedziek CloudStream i zainstaluj **AnimeGateProvider**.
2. Otwórz jego zębatkę. Strona AnimeGate ma wyświetlić się w oknie **AnimeGate — test logowania**.
3. Zaloguj się przez oryginalny formularz Discorda i wykonaj wymagane potwierdzenia. Strona AnimeGate podaje również wymóg członkostwa na swoim serwerze Discord.
4. Po powrocie do katalogu AnimeGate wybierz **Zapisz i sprawdź**.
5. Uruchom CloudStream ponownie, otwórz zębatkę i wybierz **Sprawdź zapis**. Prototyp ogranicza czas przechowywania kandydata sesji do jednej godziny; serwis może zakończyć sesję wcześniej.
6. Wybierz **Raport** i przekaż skopiowany tekst przy dalszej pracy nad dodatkiem. Raport nie zawiera haseł, wartości cookies, tokenów, kodów OAuth, adresów e-mail ani pełnych adresów stron.

Do normalnego logowania użytkownik używa własnego konta w formularzu strony. Nie przekazuje hasła ani tokenu Discorda w czacie.

## Co oznacza wynik

W v4 naprawiono kolejną blokadę `HOST_NOT_ALLOWED`, tym razem dla `auth.animegate.pl`. Logowanie i callback Discord OAuth dopuszczają teraz dokładne domeny HTTPS: `animegate.pl`, `api.animegate.pl` i `auth.animegate.pl`. Pozostałe niepotwierdzone subdomeny pozostają zablokowane. Parametry autoryzacji nie są przepisywane ani odczytywane do raportu.

Pola `api_seen` i `auth_seen` wskazują, że przeglądarka dotarła do odpowiednich serwerów. Wiersze `api_route` obejmują obserwowane żądania z trzech domen. Wartości query i fragmenty są usuwane, a zmienne segmenty ścieżek maskowane.

Raport z telefonu pokazał też żądanie do trasy API o wzorcu `/api/{param}/auth/session`. V4 zapamiętuje dokładny adres, jeżeli WebView faktycznie wysłał do niego GET, a adres nie zawiera query ani fragmentu. Po wybraniu **Zapisz i sprawdź** ten zaobserwowany endpoint ma pierwszeństwo przed frontendem. Numer wersji ani adres API nie są zgadywane lub konstruowane. Nagłówek cookies jest pobierany dla właściwego adresu API; nagłówek strony głównej nie jest tam przekazywany.

Pola `observed_session_origin` i `saved_session_origin` przyjmują `API`, `SITE` albo `NONE`. Pierwsze opisuje obserwacje bieżącego okna, drugie zapis używany po restarcie. Obserwacja endpointu nie oznacza zalogowania. Działanie logowania i użytecznej sesji po restarcie wymaga ponownej próby na urządzeniu.

W v2 poprawiono obsługę pustej strony pośredniej `about:blank` w głównej ramce WebView. Taka strona nie jest traktowana jako domena AnimeGate i nigdy nie służy do pobierania cookies ani zapisu sesji. Nadal nie potwierdzono rzeczywistego logowania na urządzeniu.

Jeżeli pojawi się `REDIRECT_BLOCKED`, wybierz **Raport**. Pola `first_block_host`, `first_block_reason`, `first_block_source` i `first_block_callback_host` pokazują domenę celu, powód, miejsce blokady i ewentualną domenę callbacku OAuth. Pierwsza blokada jest zachowana także po późniejszych przejściach. Pole `last_block_*` pojawia się, jeśli ostatnia blokada ma inne dane. Nie przesyłaj pełnego adresu przekierowania — wystarczy raport.

Raport v1 nie zawierał celu przekierowania i nie pozwalał ustalić, co dokładnie zatrzymało logowanie. Nowe dane pozwolą odróżnić pustą stronę, nieobsługiwaną domenę logowania, zewnętrzny schemat aplikacji i callback do innej domeny. Nie rozszerzono automatycznie uprawnień na niepotwierdzone domeny.

- `VERIFIED_SESSION_API`: klient dodatku otrzymał z zaobserwowanego endpointu sesji niepusty obiekt `user`. Inny lub nieznany format odpowiedzi pozostaje niepotwierdzony. Dane użytkownika są odczytywane wyłącznie jako sygnał i nie trafiają do raportu.
- `PAGE_CANDIDATE`: klient dostał HTML z elementem wylogowania i odnośnikiem katalogu. To wskazówka do dalszej analizy, nie potwierdzenie działania API.
- `UNCONFIRMED`: odpowiedź może być samą powłoką JavaScript albo innym niepotwierdzonym formatem. Sam HTTP 200 i obecność cookie nie potwierdzają logowania.
- `LOGIN_REQUIRED`: zasób odrzucił sesję lub skierował do logowania.
- `ACCESS_DENIED` / HTTP 403: serwis odmówił dostępu. Nie oznacza to automatycznie, że hasło jest błędne lub zapis należy usunąć.
- `REDIRECT_BLOCKED`, błąd sieci, certyfikatu lub oczekiwania: potrzebny jest raport; prototyp nie ponawia automatycznie prób i nie omija zabezpieczeń.

Endpoint sesji nie jest zakładany jako istniejący: żądanie do niego jest dozwolone dopiero po zaobserwowaniu normalnego żądania GET wysłanego przez stronę w WebView. W przeciwnym razie test dotyczy tylko znanego adresu głównego AnimeGate. Klient HTTP nie podąża za przekierowaniami z nagłówkiem Cookie.

## Dane sesji i ograniczenia prototypu

Zapis obejmuje cookie stosowne do jednego dokładnego adresu testu, rzeczywisty User-Agent WebView i czas zapisu. Jest zaszyfrowany AES-GCM kluczem Android Keystore w osobnym pliku poza kopią zapasową. Po zmianie User-Agent test wymaga nowego logowania zamiast podszywania się pod poprzednią wersję przeglądarki.

Nie są eksportowane tokeny konta Discord, jego cookies ani zawartość formularzy. Zapisany nagłówek cookies klienta HTTP jest wysyłany wyłącznie do dokładnego zapisanego adresu testu, bez podążania za przekierowaniami. Nie jest przenoszony pomiędzy stroną główną, API, serwerem logowania i Discordem. Usunięcie sesji dotyczy pliku prototypu i cookies dla obsługiwanych adresów testu strony głównej oraz zaobserwowanego lub zapisanego API; nie czyści całego magazynu cookies CloudStream ani sesji Discorda. Pełny model wygaśnięcia, usuwania i rotacji sesji będzie zależeć od potwierdzonego interfejsu serwisu.

Raport zawiera stałe kategorie stanu, kody HTTP, wartości logiczne, domeny zablokowanych przekierowań oraz uproszczone ścieżki API i nawigacji. Nie zachowuje pełnych adresów ani danych użytkownika w adresie. Segmenty ścieżek spoza zamkniętego słownika są zastępowane `{param}`; query i fragmenty nie są kopiowane. Konsola JavaScript stron logowania nie jest przekazywana do logów dodatku. Nie ma mostka JavaScript ani wstrzykiwania kodu na stronach Discorda.

Otwarcie Chrome lub aplikacji Discord przez zewnętrzny schemat nie przenosi automatycznie sesji do klienta dodatku. Jeśli proces wymaga takiego przejścia, trzeba ustalić obsługiwany sposób przekazania wyniku — prototyp nie deklaruje go jako działający.

## Następny etap

Po potwierdzeniu logowania i ponownego użycia sesji można ustalić rzeczywisty format katalogu i odcinków. Nie zostały dodane zgadywane endpointy katalogu ani pozorne wyniki. Docelowy moduł zachowa nazwę wewnętrzną AnimeGateProvider, aby kolejne wersje mogły aktualizować tę instalację.

Testy kodu nie zastępują próby rzeczywistego Discord OAuth, członkostwa i serwisu na telefonie.
