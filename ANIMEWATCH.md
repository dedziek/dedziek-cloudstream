# AnimeWatch dla CloudStream — wersja testowa 2

Dodatek **AnimeWatchProvider** korzysta z publicznych stron `https://animewatch.pl`. Udostępnia katalog, wyszukiwanie, opisy, plakaty, gatunki, filmy anime i listy odcinków. Materiał na stronie jest opisywany jako polski lektor IVO; w CloudStream odcinki znajdują się pod wariantem „Dubbing”. Logowanie nie jest wymagane.

## Instalacja

W istniejącym repozytorium Dedziek CloudStream odśwież listę, zainstaluj **AnimeWatchProvider**, następnie wybierz źródło **AnimeWatch** na ekranie głównym. Nazwa repozytorium może nadal pokazywać wcześniejszą nazwę z Filmanem do następnego odświeżenia metadanych.

Nowa instalacja: **Ustawienia → Rozszerzenia → Dodaj repozytorium**:

```text
https://github.com/dedziek/dedziek-cloudstream/raw/refs/heads/main/repo.json
```

## Odtwarzanie

Dodatek odczytuje osadzenie odtwarzacza i dodatkowe pozycje „Wybierz serwer wideo”. Dla domeny Byse używanej przez AnimeWatch rejestruje alias oficjalnego ekstraktora CloudStream oraz rozpoznaje kod w adresie `/e/kod/tytuł`. Pozostałe hostingi obsługują ekstraktory aplikacji.

Jeśli bezpośredni ekstraktor Byse nie uzyska wideo, dodatek uruchamia skrypty hostingu w tle i odczytuje gotową listę źródeł JW Playera. Link trafia do odtwarzacza CloudStream bez dodatkowego okna i naciskania **Play** na stronie. Awaryjny mechanizm wymaga działającego Android System WebView. Pojedyncza próba trwa maksymalnie 45 sekund; błąd jednego serwera nie kończy prób z pozostałymi.

Dodatek nie klika reklam ani weryfikacji. Gdy hosting odrzuca połączenie, wymaga CAPTCHA albo nie udostępnia filmu, wyświetlany jest komunikat o braku wideo.

Wymagany jest aktualny **CloudStream pre-release** z ekstraktorem ByseSX. Starsza aplikacja może nie załadować dodatku. Android 6.0 / API 23 jest minimalną wersją kompilacji.

## Weryfikacja i ograniczenia

W wersji 2 dodano 5 testów rozpoznawania i walidacji linków oraz 8 testów wykonywanego skryptu odczytującego źródła. Lokalna kompilacja kodu Androida, wszystkie **23 testy JUnit** dodatku i **8 testów skryptu** przeszły poprawnie (4 listopada 2026). Skrypt jest sprawdzany poleceniem `node --test scripts/test-animewatch-playback.mjs`, także w GitHub Actions. Testy obejmują gotową i opóźnioną inicjalizację odtwarzacza, linki z tokenem, HLS bez rozszerzenia w adresie, źródła MP4 i odrzucanie nieprawidłowych linków. Nie zastępują próby odtwarzania na fizycznym urządzeniu.

Wersja 1: kompilacja `.cs3` i **18 testów regresji** parsera oraz odczytu odtwarzaczy przeszły poprawnie (3 listopada 2026). Testy używają krótkich syntetycznych fragmentów HTML. Osobna próba publicznych stron potwierdziła: 20 pozycji i plakatów w katalogu, 10 wyników wyszukiwania, 170 odcinków „Black Clover” w kolejności 1–170 oraz prawidłową stronę filmu „Spy x Family Movie: Code: White”. Wynik bieżącej kompilacji udostępnia zakładka [Actions](https://github.com/dedziek/dedziek-cloudstream/actions).

Publiczny interfejs metadanych Byse odpowiada poprawnie, ale dotychczasowe żądanie GET danych odtwarzania zwracało HTTP 405. Aktualne skrypty strony korzystają z własnej sesji i żądania POST, dlatego wersja 2 odczytuje wynik inicjalizacji odtwarzacza. W przeglądarce testowej hosting kierował przykład „Black Clover”, odcinek 1, na `blocked.html` również na stronie AnimeWatch. Nie potwierdzono pełnego odtwarzania na telefonie ani telewizorze. Awaria hostingu, usunięty odcinek albo zmiana strony może wymagać poprawki. Puste karty zapowiedzi bez opublikowanych odcinków zgłaszają brak dostępnych odcinków.

Przy zgłaszaniu problemu podaj tytuł anime, numer odcinka, wersję CloudStream i dokładny komunikat.
