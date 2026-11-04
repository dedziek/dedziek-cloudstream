# Plan wdrożenia AnimeGateProvider

Data: 4 listopada 2026. Repozytorium: `dedziek/dedziek-cloudstream`.

Status: realizacja etapu 1. Raport v3 wykazał kolejny serwer logowania `auth.animegate.pl` oraz rzeczywiste żądanie API sesji. V4 dopuszcza tę dokładną domenę HTTPS i wykorzystuje zaobserwowany adres endpointu sesji API z jego własnym zakresem cookies. Logowanie i sesja pozostają niepotwierdzone; katalog i odtwarzanie czekają na wynik próby. Instrukcja prototypu: [ANIMEGATE.md](ANIMEGATE.md).

## Cel i docelowa obsługa

Użytkownik instaluje AnimeGateProvider, wybiera **Zaloguj przez Discord**, kończy zwykłe logowanie AnimeGate i wraca do CloudStream. Dodatek potwierdza dostęp do konta i zapisuje potrzebną sesję AnimeGate na urządzeniu. Kolejne wejścia korzystają z niej, dopóki pozostaje ważna. Po wygaśnięciu sesji pojawia się jeden przycisk ponownego logowania.

MVP obejmuje katalog anime, wyszukiwanie, opis tytułu, odcinki i odtwarzanie w CloudStream. Synchronizacja historii, listy użytkownika, komentarze oraz parowanie telefonu z telewizorem są osobnymi, późniejszymi funkcjami.

## Co jest ustalone, a czego jeszcze nie potwierdzono

- Publiczna strona logowania informuje o Discord OAuth i wymaganym członkostwie na serwerze Discord AnimeGate. Użytkownik musi spełniać warunki dostępu serwisu.
- Publiczne wejście kieruje do `/auth/login?next=/`. To jedyna potwierdzona w tym rozpoznaniu ścieżka logowania; adresów API i callbacku nie należy zgadywać.
- Bezpośrednie żądanie z obecnego środowiska otrzymało HTTP 403 z tekstem `error code: 1010`. Nie wykonano kolejnych prób obchodzenia tej odmowy dostępu.
- Nie ustalono rodzaju sesji AnimeGate, jej czasu życia, zakresu cookie, działania Discord OAuth w WebView ani struktury katalogu i odtwarzaczy po zalogowaniu.
- Custom Tabs korzystają ze stanu zwykłej przeglądarki, a WebView nie współdzieli z nią automatycznie sesji. Samo zalogowanie w Chrome nie zapewnia więc sesji dodatku.

## Kolejność i warunki przejścia

| Etap | Zakres | Warunek zakończenia |
|---|---|---|
| 1. Próba logowania | Normalny proces AnimeGate → Discord → AnimeGate na urządzeniu, sprawdzenie dopuszczalnego sposobu użycia w dodatku i wymogu członkostwa | Działający, wspierany sposób uzyskania sesji AnimeGate dostępnej dodatkowemu klientowi |
| 2. Sesja i ekran logowania | Zapis sesji, sprawdzenie zalogowania, wylogowanie z dodatku, obsługa błędów | Sesja działa po ponownym uruchomieniu aplikacji i jest wysyłana tylko do właściwych domen |
| 3. Katalog | Strona główna, wyszukiwanie, szczegóły, sezony i odcinki | Poprawne wyniki i kolejność odcinków dla ustalonych przypadków testowych |
| 4. Odtwarzanie | Rozpoznanie odtwarzaczy, ekstraktory, wymagane nagłówki i napisy | Co najmniej kilka rzeczywistych odcinków odtwarza się po zalogowaniu |
| 5. Testy wersji próbnej | Korutyny, sesja, nawigacja, układ UI i testy na urządzeniu | Brak wycieku danych logowania; anulowanie i wygaśnięcie sesji mają poprawną obsługę |
| 6. Publikacja | Nowy moduł, katalog dodatków, CI i paczka v1 | Paczka i metadane zgodne, pozostałe dodatki zachowane, instalacja z dotychczasowego repozytorium działa |

**Etap 1 jest warunkiem rozpoczęcia pełnej implementacji katalogu.** Nie należy najpierw budować całego dodatku z założeniem, że logowanie na pewno uda się przenieść.

## Etap 1: prototyp logowania i decyzja o wykonalności

1. Na urządzeniu użytkownika sprawdzić normalne logowanie, zgodę OAuth, ewentualne 2FA i wymagane członkostwo. Przejrzeć warunki korzystania i dostępne informacje o integracji serwisu.
2. Ustalić rzeczywisty przycisk startujący logowanie, dozwolone domeny, przekierowania, callback, sposób utworzenia sesji i sygnał poprawnego zalogowania. Nie zapisywać w publicznych logach pełnych URL callbacku, kodów OAuth ani ciasteczek.
3. Sprawdzić, czy oryginalny proces logowania jest obsługiwany w WebView. Hasło i potwierdzenia użytkownik wprowadza bezpośrednio na stronie Discorda. Nie wstrzykiwać skryptów ani mostka JavaScript na stronach Discorda.
4. Jeżeli wymagane jest użycie zewnętrznej przeglądarki, ustalić wspierany przez AnimeGate sposób przekazania wyniku do klienta. Custom Tab samodzielnie nie przeniesie sesji do WebView lub klienta HTTP dodatku.
5. Potwierdzić dostęp do zasobu wymagającego zalogowania, następnie powtórzyć sprawdzenie po ponownym uruchomieniu aplikacji.

Decyzja po próbie:

- **Kontynuować**, jeżeli dozwolony proces logowania tworzy sesję AnimeGate, którą dodatek może poprawnie wykorzystywać.
- **Wymagana współpraca AnimeGate lub zmiana CloudStream**, jeżeli potrzebny jest oficjalny callback aplikacji, API klienta mobilnego albo parowanie urządzeń.
- **Wstrzymać implementację**, jeżeli jedynym wynikiem jest sesja zewnętrznej przeglądarki bez obsługiwanego przekazania jej dodatkowemu klientowi.

Utworzenie własnej aplikacji Discord nie rozwiązuje automatycznie tego problemu: jej autoryzacja nie jest sesją AnimeGate. Wariant wymiany kodu OAuth, jeżeli taki byłby uzgodniony z serwisem, wymaga prawidłowo zarejestrowanego redirect URI i zabezpieczeń odpowiednich dla klienta publicznego. Sekret klienta nie może trafić do `.cs3`.

## Etap 2: sesja i logowanie

- Korzystać z istniejącego procesu OAuth AnimeGate; parametry autoryzacji i ich walidacja po stronie serwisu muszą pozostać nienaruszone.
- Zapisywać wyłącznie potrzebne dane sesji AnimeGate. Nie eksportować do pliku dodatku haseł, tokenów konta Discord ani cookies Discorda obsługiwanych przez przeglądarkę.
- Wykorzystać wzorzec szyfrowania Android Keystore z Filmana, ale utworzyć osobny plik, alias klucza i przestrzeń ustawień dla AnimeGate. Zachować ograniczenia domeny, ścieżki i wygaśnięcia sesji; wykluczyć zapis z kopii zapasowej.
- Używać rzeczywistego User-Agent i poprawnej walidacji HTTPS. Wyłącznie potwierdzone domeny AnimeGate i Discord OAuth mają uczestniczyć w nawigacji logowania. Obsługę ewentualnych systemowych przekierowań ustalić w prototypie.
- Potwierdzać zalogowanie odpowiedzią zasobu chronionego, a nie samą obecnością cookie lub zamknięciem okna.
- Rozróżniać brak sesji, jej wygaśnięcie, brak wymaganej przynależności, odmowę dostępu, ograniczenie liczby żądań i błąd sieci. Sam HTTP 403 nie wystarcza do automatycznego wyczyszczenia sesji.
- Przycisk **Wyloguj z dodatku** usuwa jego sesję AnimeGate. Nie usuwa globalnie cookies Filmana, Byse i pozostałych stron ani nie odwołuje samoczynnie autoryzacji Discorda.
- Ekran logowania zajmuje dostępną wysokość okna, pozostawia miejsce stronie i reaguje na klawiaturę. Anulowanie kończy próbę i nie uruchamia jej ponownie w pętli.

## Etap 3: katalog

Po pomyślnej próbie logowania ustalić rzeczywisty interfejs strony: HTML, API lub połączenie obu. Dopiero wtedy wybrać parser i konkretne endpointy.

Zakres MVP:

- Strona główna i stronicowanie, zgodne z sekcjami faktycznie dostępnymi w serwisie.
- Wyszukiwanie oraz karta tytułu: nazwa, plakat, opis, rok i gatunki, o ile są dostępne.
- Sezony i odcinki z poprawnym sortowaniem, bez gubienia odcinków specjalnych i ułamkowych oznaczeń.
- Wersje językowe zgodne z serwisem; nie zakładać z góry, że każdy odcinek ma dubbing lub lektora.
- Kontrolowany komunikat logowania zamiast interpretowania strony logowania jako pustego katalogu.

Fixture testowe mają być krótkie i syntetyczne lub zanonimizowane, bez danych konta, tokenów i pełnych prywatnych odpowiedzi.

## Etap 4: odtwarzanie

1. Ustalić dostępne hostingi i sposób udostępniania źródła dla zalogowanego użytkownika.
2. W pierwszej kolejności użyć właściwych ekstraktorów CloudStream. Osobny mechanizm WebView dodać dopiero dla konkretnego, potwierdzonego przypadku.
3. Przekazywać adres, typ HLS/MP4, napisy i wymagane nagłówki bez modyfikowania podpisanych parametrów adresu.
4. Dane sesji AnimeGate wysyłać wyłącznie do właściwych domen. Zewnętrzny hosting nie otrzymuje cookie AnimeGate; korzysta z własnych danych lub adresu udostępnionego przez serwis.
5. Jeśli hosting wymaga potwierdzenia przez użytkownika, wykorzystać wzorzec AnimeWatch v5: jedna interaktywna próba, zwrot źródła do tego samego wywołania i poprawne anulowanie. Nie automatyzować rozwiązywania weryfikacji.
6. Nie zapisywać na stałe krótkotrwałych adresów wideo ani nie uznawać pojedynczego błędu CDN za wygaśnięcie konta.

## Proponowane elementy kodu

| Element | Odpowiedzialność |
|---|---|
| `AnimeGatePlugin.kt` | Rejestracja źródła i ustawień dodatku |
| `AnimeGateProvider.kt` | `MainAPI`, katalog, wyszukiwanie, szczegóły i linki |
| `AnimeGateLoginUi.kt` | Dozwolony proces logowania i widoczny stan sesji |
| `AnimeGateAuthPolicy.kt` | Walidacja domen, przekierowań i zakresu wysyłania sesji |
| `AnimeGateSession.kt` | Zaszyfrowany zapis danych AnimeGate i sprawdzanie ważności |
| `AnimeGateParser.kt` | Rzeczywisty format HTML/API ustalony po zalogowaniu |
| `AnimeGatePlayers.kt` | Rozpoznanie źródeł i integracja ekstraktorów |

Nie kopiować bez zmian polityki nawigacji Filmana: dopuszcza ona tylko Filman, podczas gdy logowanie AnimeGate musi poprawnie przejść przez Discorda.

## Etap 5: kryteria odbioru i testy

- Logowanie od stanu bez sesji, odmowa zgody OAuth, 2FA, brak wymaganego członkostwa i anulowanie.
- Dostęp po restarcie CloudStream, obsługa wygaśnięcia sesji i wylogowania z dodatku.
- Testy polityki domen: brak przekazywania sesji do hostingu, podobnie zapisanej domeny, HTTP lub obcego callbacku.
- Pomiary UI w pionie, poziomie i po zmniejszeniu wysokości; testy Robolectric nie zastępują próby rzeczywistego silnika WebView i Discorda.
- Katalog, wyszukiwanie, odcinki i kilka prób rzeczywistego odtwarzania na telefonie użytkownika, z aktualnym CloudStream i WebView.
- Brak danych logowania w logach, fixture, repozytorium i artefaktach CI. CI sprawdza kod oraz syntetyczne przypadki, bez używania konta użytkownika.
- Osobno sprawdzić Android TV. Nie deklarować parowania telefonu z TV bez mechanizmu obsługiwanego przez AnimeGate; Discord device flow dla własnej aplikacji nie tworzy automatycznie sesji serwisu.

## Etap 6: integracja z istniejącym repozytorium

1. Dodać moduł `AnimeGateProvider` i jego wpis w `settings.gradle.kts`.
2. W głównym `build.gradle.kts` dodać osobny namespace, np. `pl.animegatecloudstream`. Obecny warunek przypisuje namespace Filmana wszystkim modułom innym niż AnimeWatch, więc wymaga rozszerzenia.
3. W `scripts/prepare-publication.mjs` rozszerzyć listę własnych dodatków o AnimeGate i walidację jego manifestu.
4. W `.github/workflows/build.yml` dodać kompilację, publikację i dołączenie paczki `AnimeGateProvider.cs3` do sum kontrolnych.
5. Zachować aktualny katalog Phishera i standardowy mechanizm aktualizacji CloudStream. AnimeGate otrzymuje własny numer wersji, zaczynając od próbnego v1; inne dodatki nie wymagają zwiększenia wersji bez zmian ich paczek.
6. Przygotować PR z opisem faktycznie potwierdzonego logowania, odtwarzania i ograniczeń. Opublikować paczkę dopiero po spełnieniu kryteriów MVP i sprawdzić jej SHA-256, manifest i instalację z dotychczasowego adresu repozytorium.

## Pierwszy rezultat do przygotowania

Mały prototyp logowania zakończony jednym z trzech wyników: działająca sesja AnimeGate po restarcie, konkretnie opisany brak obsługi przekazania sesji albo wskazanie potrzebnej współpracy serwisu. To powinno poprzedzać zobowiązanie do pełnego wdrożenia i szacowanie jego terminu.

## Źródła

- AnimeGate: https://animegate.pl/auth/login?next=%2F — publiczna strona logowania; wymagania Discord i członkostwa widoczne w indeksowanej treści. Bezpośredni dostęp z obecnego środowiska otrzymał HTTP 403.
- Discord OAuth2: https://docs.discord.com/developers/topics/oauth2 — aplikacje, redirect URI, authorization code, state i bezpieczeństwo.
- Android Custom Tabs: https://developer.chrome.com/docs/android/custom-tabs — współdzielenie stanu z przeglądarką oraz odrębność WebView.
- Android WebView: https://developer.android.com/develop/ui/views/layout/webapps/managing-webview — zarządzanie WebView i zachowanie Safe Browsing.
- Istniejący kod repozytorium: wzorce sesji Filmana, obsługa weryfikacji AnimeWatch i skrypty publikacji, sprawdzone względem `0882d4d997176d69caa9bfce39a340416f67758d`.
