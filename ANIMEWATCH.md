# AnimeWatch dla CloudStream — wersja testowa 3

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

Dodatek nie klika reklam ani weryfikacji. Jeżeli Byse wyświetla bramkę CAPTCHA, zwykła próba odtwarzania kończy się komunikatem wskazującym ręczną weryfikację. Nie otwiera dodatkowego okna automatycznie.

### Ręczna weryfikacja na żądanie

1. Spróbuj odtworzyć wybrany odcinek. Dodatek zapamięta odcinek, dla którego Byse zażądał weryfikacji.
2. Otwórz ustawienia zainstalowanego **AnimeWatchProvider** i wybierz **Otwórz weryfikację**.
3. Samodzielnie naciśnij przycisk odtwarzacza i dokończ wymaganą weryfikację na stronie Byse.
4. Po wykryciu źródła okno zamknie się. W ciągu minuty uruchom ponownie ten sam odcinek w CloudStream.

Uzyskany adres wraz z nagłówkami jest przekazywany jednorazowo przez pamięć aplikacji, wyłącznie dla zweryfikowanego odcinka, maksymalnie przez 60 sekund. Zamknięcie okna anuluje próbę. Po restarcie aplikacji trzeba ponownie wybrać odcinek. Bez ukończenia wymaganej przez hosting weryfikacji źródło może pozostać niedostępne.

Wymagany jest aktualny **CloudStream pre-release** z ekstraktorem ByseSX. Starsza aplikacja może nie załadować dodatku. Android 6.0 / API 23 jest minimalną wersją kompilacji.

## Weryfikacja i ograniczenia

Wersja 3 poprawia błąd inicjalizacji ramki obecny w wersji 2. Wewnętrzny adres z `embed_frame_url` oczekuje kontekstu od rodzica i samodzielnie nie uruchamiał odtwarzacza. Powodowało to 45-sekundowe oczekiwanie. Dla „Zombie Sagashitemasu”, odcinek 1, publiczna odpowiedź ustawień Byse w środowisku testowym dodatkowo zgłaszała `captcha_required: true`; skrypt hostingu rozpoczyna tę weryfikację dopiero po kliknięciu użytkownika. Wersja 3 rozpoznaje tę bramkę w rzeczywistej ramce i udostępnia ręczną weryfikację w ustawieniach.

Kompilacja kodu Androida, **31 testów JUnit** oraz **13 testów skryptu** wersji 3 przeszły poprawnie (4 listopada 2026). Nowe przypadki obejmują syntetyczny układ rodzic–ramka z opóźnioną inicjalizacją, zakres dołączania obserwatora, rozpoznanie CAPTCHA bez jej klikania, weryfikację wiadomości i zakończenie obserwacji. Testy sesji sprawdzają przypisanie źródła do właściwego odcinka, jednorazowe użycie i wygaśnięcie wyniku. Nadal wymagane jest sprawdzenie ręcznej weryfikacji i pełnego odtwarzania na urządzeniu; testy skryptów i kompilacja nie potwierdzają dostępności filmu w danej sieci.

W wersji 2 dodano 5 testów rozpoznawania i walidacji linków oraz 8 testów wykonywanego skryptu odczytującego źródła. Lokalna kompilacja kodu Androida, wszystkie **23 testy JUnit** dodatku i **8 testów skryptu** przeszły poprawnie (4 listopada 2026). Skrypt jest sprawdzany poleceniem `node --test scripts/test-animewatch-playback.mjs`, także w GitHub Actions. Testy obejmują gotową i opóźnioną inicjalizację odtwarzacza, linki z tokenem, HLS bez rozszerzenia w adresie, źródła MP4 i odrzucanie nieprawidłowych linków. Nie zastępują próby odtwarzania na fizycznym urządzeniu.

Wersja 1: kompilacja `.cs3` i **18 testów regresji** parsera oraz odczytu odtwarzaczy przeszły poprawnie (3 listopada 2026). Testy używają krótkich syntetycznych fragmentów HTML. Osobna próba publicznych stron potwierdziła: 20 pozycji i plakatów w katalogu, 10 wyników wyszukiwania, 170 odcinków „Black Clover” w kolejności 1–170 oraz prawidłową stronę filmu „Spy x Family Movie: Code: White”. Wynik bieżącej kompilacji udostępnia zakładka [Actions](https://github.com/dedziek/dedziek-cloudstream/actions).

Publiczny interfejs metadanych Byse odpowiada poprawnie, ale dotychczasowe żądanie GET danych odtwarzania zwracało HTTP 405. Aktualne skrypty strony korzystają z własnej sesji i żądania POST, dlatego dodatek odczytuje wynik inicjalizacji odtwarzacza. W przeglądarce testowej hosting kierował przykład „Black Clover”, odcinek 1, na `blocked.html` również na stronie AnimeWatch. Nie potwierdzono pełnego odtwarzania na telefonie ani telewizorze. Awaria hostingu, usunięty odcinek albo zmiana strony może wymagać poprawki. Puste karty zapowiedzi bez opublikowanych odcinków zgłaszają brak dostępnych odcinków.

Przy zgłaszaniu problemu podaj tytuł anime, numer odcinka, wersję CloudStream i dokładny komunikat.
