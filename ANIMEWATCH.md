# AnimeWatch dla CloudStream — wersja testowa 1

Dodatek **AnimeWatchProvider** korzysta z publicznych stron `https://animewatch.pl`. Udostępnia katalog, wyszukiwanie, opisy, plakaty, gatunki, filmy anime i listy odcinków. Materiał na stronie jest opisywany jako polski lektor IVO; w CloudStream odcinki znajdują się pod wariantem „Dubbing”. Logowanie nie jest wymagane.

## Instalacja

W istniejącym repozytorium Dedziek CloudStream odśwież listę, zainstaluj **AnimeWatchProvider**, następnie wybierz źródło **AnimeWatch** na ekranie głównym. Nazwa repozytorium może nadal pokazywać wcześniejszą nazwę z Filmanem do następnego odświeżenia metadanych.

Nowa instalacja: **Ustawienia → Rozszerzenia → Dodaj repozytorium**:

```text
https://github.com/dedziek/dedziek-cloudstream/raw/refs/heads/main/repo.json
```

## Odtwarzanie

Dodatek odczytuje osadzenie odtwarzacza i dodatkowe pozycje „Wybierz serwer wideo”. Dla domeny Byse używanej przez AnimeWatch rejestruje alias oficjalnego ekstraktora CloudStream oraz rozpoznaje kod w adresie `/e/kod/tytuł`. Pozostałe hostingi obsługują ekstraktory aplikacji.

Jeśli bezpośredni ekstraktor Byse nie uzyska wideo, pojawi się okno zwykłego odtwarzacza strony. Naciśnij w nim **Play** i samodzielnie wykonaj ewentualną weryfikację. Po wykryciu żądania wideo dodatek przekazuje je do odtwarzacza CloudStream. Zamknięcie okna anuluje próbę; wymagany jest Android System WebView.

Wymagany jest aktualny **CloudStream pre-release** z ekstraktorem ByseSX. Starsza aplikacja może nie załadować dodatku. Android 6.0 / API 23 jest minimalną wersją kompilacji.

## Weryfikacja i ograniczenia

Kompilacja `.cs3` i **18 testów regresji** parsera oraz odczytu odtwarzaczy przeszły poprawnie (3 listopada 2026). Testy używają krótkich syntetycznych fragmentów HTML. Osobna próba publicznych stron potwierdziła: 20 pozycji i plakatów w katalogu, 10 wyników wyszukiwania, 170 odcinków „Black Clover” w kolejności 1–170 oraz prawidłową stronę filmu „Spy x Family Movie: Code: White”. Kompilacja wspólna zachowuje również 29 poprawnych testów Filmana. Wynik automatycznej publikacji udostępnia zakładka [Actions](https://github.com/dedziek/dedziek-cloudstream/actions).

Publiczny interfejs metadanych Byse odpowiada poprawnie, ale interfejs odtwarzania w próbie z tego środowiska zwracał HTTP 405. Stąd awaryjne okno odtwarzacza. Nie przeprowadzono próby tego okna i odtwarzania na fizycznym telefonie lub telewizorze. Awaria hostingu, usunięty odcinek albo zmiana strony może wymagać poprawki. Puste karty zapowiedzi bez opublikowanych odcinków zgłaszają brak dostępnych odcinków.

Przy zgłaszaniu problemu podaj tytuł anime, numer odcinka, wersję CloudStream i dokładny komunikat.
