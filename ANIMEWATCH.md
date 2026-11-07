# AnimeWatch dla CloudStream — wersja testowa 7

Wersja 7 używa oryginalnej ikony AnimeWatch w katalogu dodatków. Zachowuje krótki opis i sposób odtwarzania z v6.

Dodatek **AnimeWatchProvider** korzysta z publicznych stron `https://animewatch.pl`. Udostępnia katalog, wyszukiwanie, opisy, plakaty, gatunki, filmy anime i listy odcinków. Materiał na stronie jest opisywany jako polski lektor IVO; w CloudStream odcinki znajdują się pod wariantem „Dubbing”. Logowanie nie jest wymagane.

## Instalacja

W istniejącym repozytorium Dedziek CloudStream odśwież listę, zainstaluj **AnimeWatchProvider**, następnie wybierz źródło **AnimeWatch** na ekranie głównym. Nazwa repozytorium może nadal pokazywać wcześniejszą nazwę z Filmanem do następnego odświeżenia metadanych.

Nowa instalacja: **Ustawienia → Rozszerzenia → Dodaj repozytorium**:

```text
https://github.com/dedziek/dedziek-cloudstream/raw/refs/heads/main/repo.json
```

## Odtwarzanie

Dodatek odczytuje osadzenie odtwarzacza i dodatkowe pozycje „Wybierz serwer wideo”. Dla domeny Byse używanej przez AnimeWatch rejestruje alias oficjalnego ekstraktora CloudStream oraz rozpoznaje kod w adresie `/e/kod/tytuł`. Pozostałe hostingi obsługują ekstraktory aplikacji.

Jeśli bezpośredni ekstraktor Byse nie uzyska wideo, dodatek otwiera w tle stronę nadrzędną `/e/kod`, która inicjalizuje własną ramkę odtwarzacza w innej domenie. Następnie odczytuje gotową listę źródeł JW Playera wewnątrz tej ramki. Link trafia do odtwarzacza CloudStream bez dodatkowego okna i naciskania **Play** na stronie. Awaryjny mechanizm wymaga działającego Android System WebView. Pojedyncza próba trwa maksymalnie 45 sekund; błąd jednego serwera nie kończy prób z pozostałymi.

Obserwator źródeł jest dołączany do żądania skryptu startowego `/assets/index-....js` wyłącznie dla odtwarzacza wskazanego przez Byse. Wiadomość z ramki wymaga losowego identyfikatora bieżącej próby oraz zgodnego adresu dokumentu. Osadzony odtwarzacz może używać własnych ciasteczek w tej instancji WebView. Żądania POST, inicjalizację sesji i komunikację między ramkami wykonują skrypty hostingu.

Wersja 4 może ustalić adres ramki także z normalnej odpowiedzi metadanych otrzymanej przez stronę nadrzędną w WebView. Nie zależy wtedy wyłącznie od wcześniejszego, osobnego żądania dodatku. Odpowiedź jest odczytywana z kopii, bez zmiany oryginalnej odpowiedzi, nagłówków, ciasteczek ani treści żądań POST. Ramka musi odpowiadać temu samemu kodowi filmu; zgłoszenie jej adresu jest przyjmowane wyłącznie od właściwej strony nadrzędnej i z identyfikatorem bieżącej próby.

Dodatek nie klika reklam ani weryfikacji. Od wersji 5, jeżeli Byse zgłosi bramkę CAPTCHA, dodatek otwiera właściwą stronę weryfikacji podczas tej samej próby odtwarzania. Użytkownik naciska przycisk strony i samodzielnie wykonuje wymagane potwierdzenie. Po uzyskaniu źródła okno zamyka się, a wynik trafia bezpośrednio do CloudStream. Nie trzeba otwierać ustawień, ponownie wybierać odcinka ani korzystać z minutowego limitu zapisanego wyniku.

Okno pojawia się dopiero po rozpoznaniu wymogu weryfikacji. Zwykły brak źródła, HTTP 403 i błędy połączenia nie otwierają go automatycznie. Anulowanie przerywa bieżącą próbę; dodatek nie ponawia okna po anulowaniu. Przeglądarka i przycisk anulowania mają własny układ zajmujący dostępną wysokość okna.

### Awaryjne otwarcie odtwarzacza z ustawień

Ta ścieżka pozostaje dostępna, gdy automatyczne rozpoznanie weryfikacji lub inicjalizacja odtwarzacza się nie powiedzie. Przy normalnie rozpoznanej bramce wersja 5 otwiera weryfikację bez tych kroków.

1. Spróbuj odtworzyć wybrany odcinek. Dodatek zapamięta ostatnią próbę Byse, także gdy zakończy się oczekiwaniem 45 sekund bez wykrycia CAPTCHA.
2. Otwórz ustawienia zainstalowanego **AnimeWatchProvider** i wybierz **Otwórz weryfikację**.
3. Samodzielnie naciśnij przycisk odtwarzacza i dokończ wymaganą weryfikację na stronie Byse.
4. Po wykryciu źródła okno zamknie się. W ciągu minuty uruchom ponownie ten sam odcinek w CloudStream.

Uzyskany adres wraz z nagłówkami jest przekazywany jednorazowo przez pamięć aplikacji, wyłącznie dla zweryfikowanego odcinka, maksymalnie przez 60 sekund. Zamknięcie okna anuluje próbę. Po restarcie aplikacji trzeba ponownie wybrać odcinek. Bez ukończenia wymaganej przez hosting weryfikacji źródło może pozostać niedostępne.

Wymagany jest aktualny **CloudStream pre-release** z ekstraktorem ByseSX. Starsza aplikacja może nie załadować dodatku. Android 6.0 / API 23 jest minimalną wersją kompilacji.

## Weryfikacja i ograniczenia

Wersja 5 przekazuje wynik wymaganej weryfikacji bezpośrednio do pierwotnego wywołania pobierającego źródło. Okno otwiera się najwyżej raz dla rozpoznanej bramki w danej próbie; zwykłe błędy i anulowanie nie uruchamiają tej ścieżki. Hint limitu pobierania linków CloudStream ustawiono na pięć minut, aby standardowy limit dwóch minut nie skracał oczekiwania na użytkownika. Poszczególne próby WebView zachowują ograniczenia 45 sekund w tle i 180 sekund z interakcją. Nadrzędna aplikacja może nadal anulować próbę.

Nowe testy sprawdzają wynik bez okna, brak źródła bez okna, oczekiwanie na użytkownika i zwrócenie wyniku temu samemu wywołaniu, błędy i anulowanie bez ponawiania oraz pomiary okna w pionie, poziomie i przy zmniejszonej wysokości na API 28 i 35. W testach układu zwykły widok Androida zajmuje miejsce przeglądarki, ponieważ atrapa WebView w Robolectric nie wykonuje pomiaru. Testy nie potwierdzają renderowania strony ani rzeczywistej weryfikacji i odtwarzania na urządzeniu.

Wersja 4 usuwa lukę, w której niedostępna odpowiedź osobnego żądania metadanych pozostawiała obserwator wyłącznie w stronie nadrzędnej, bez dostępu do źródła w ramce. Adres ramki może teraz zostać zgłoszony przed utworzeniem jej przez stronę. Komunikat po upływie czasu wskazuje opcjonalną ręczną próbę w ustawieniach. Zwykłe odtwarzanie nadal nie otwiera okna ani nie klika przycisków strony.

Zgłoszenie z 4 listopada 2026 dotyczy „Kyouran Reijou Nia Liston…”, odcinek 1. Publiczna strona AnimeWatch zawierała jedno źródło Byse, `mpbwc02rmz6f`. Z tego środowiska Byse odpowiedział HTTP 403 zarówno dla strony osadzenia, jak i metadanych. Nie ponawiano tych żądań w celu obejścia blokady; nie potwierdzono rzeczywistego odtwarzania tego odcinka. Nie ustalono, czy błąd zgłoszony z urządzenia miał tę samą przyczynę sieciową.

Dodano testy obserwacji zwykłej odpowiedzi metadanych, kolejności zgłoszenia ramki, nienaruszonej treści odpowiedzi, obiektów Request, żądań POST, błędów sieci/HTTP/JSON i awarii mostka. Testy Kotlin sprawdzają dostęp do skryptu i źródła po odkryciu ramki bez początkowej podpowiedzi oraz odrzucanie wiadomości z innych dokumentów, domen, kodów filmu i niepublicznych adresów. Testy skryptów wykonuje polecenie `node --test scripts/test-animewatch-playback.mjs`; pełną kompilację i testy uruchamia GitHub Actions.

Wersja 3 poprawia błąd inicjalizacji ramki obecny w wersji 2. Wewnętrzny adres z `embed_frame_url` oczekuje kontekstu od rodzica i samodzielnie nie uruchamiał odtwarzacza. Powodowało to 45-sekundowe oczekiwanie. Dla „Zombie Sagashitemasu”, odcinek 1, publiczna odpowiedź ustawień Byse w środowisku testowym dodatkowo zgłaszała `captcha_required: true`; skrypt hostingu rozpoczyna tę weryfikację dopiero po kliknięciu użytkownika. Wersja 3 rozpoznaje tę bramkę w rzeczywistej ramce i udostępnia ręczną weryfikację w ustawieniach.

Kompilacja kodu Androida, **31 testów JUnit** oraz **13 testów skryptu** wersji 3 przeszły poprawnie (4 listopada 2026). Nowe przypadki obejmują syntetyczny układ rodzic–ramka z opóźnioną inicjalizacją, zakres dołączania obserwatora, rozpoznanie CAPTCHA bez jej klikania, weryfikację wiadomości i zakończenie obserwacji. Testy sesji sprawdzają przypisanie źródła do właściwego odcinka, jednorazowe użycie i wygaśnięcie wyniku. Nadal wymagane jest sprawdzenie ręcznej weryfikacji i pełnego odtwarzania na urządzeniu; testy skryptów i kompilacja nie potwierdzają dostępności filmu w danej sieci.

W wersji 2 dodano 5 testów rozpoznawania i walidacji linków oraz 8 testów wykonywanego skryptu odczytującego źródła. Lokalna kompilacja kodu Androida, wszystkie **23 testy JUnit** dodatku i **8 testów skryptu** przeszły poprawnie (4 listopada 2026). Skrypt jest sprawdzany poleceniem `node --test scripts/test-animewatch-playback.mjs`, także w GitHub Actions. Testy obejmują gotową i opóźnioną inicjalizację odtwarzacza, linki z tokenem, HLS bez rozszerzenia w adresie, źródła MP4 i odrzucanie nieprawidłowych linków. Nie zastępują próby odtwarzania na fizycznym urządzeniu.

Wersja 1: kompilacja `.cs3` i **18 testów regresji** parsera oraz odczytu odtwarzaczy przeszły poprawnie (3 listopada 2026). Testy używają krótkich syntetycznych fragmentów HTML. Osobna próba publicznych stron potwierdziła: 20 pozycji i plakatów w katalogu, 10 wyników wyszukiwania, 170 odcinków „Black Clover” w kolejności 1–170 oraz prawidłową stronę filmu „Spy x Family Movie: Code: White”. Wynik bieżącej kompilacji udostępnia zakładka [Actions](https://github.com/dedziek/dedziek-cloudstream/actions).

Publiczny interfejs metadanych Byse odpowiada poprawnie, ale dotychczasowe żądanie GET danych odtwarzania zwracało HTTP 405. Aktualne skrypty strony korzystają z własnej sesji i żądania POST, dlatego dodatek odczytuje wynik inicjalizacji odtwarzacza. W przeglądarce testowej hosting kierował przykład „Black Clover”, odcinek 1, na `blocked.html` również na stronie AnimeWatch. Nie potwierdzono pełnego odtwarzania na telefonie ani telewizorze. Awaria hostingu, usunięty odcinek albo zmiana strony może wymagać poprawki. Puste karty zapowiedzi bez opublikowanych odcinków zgłaszają brak dostępnych odcinków.

Przy zgłaszaniu problemu podaj tytuł anime, numer odcinka, wersję CloudStream i dokładny komunikat.
