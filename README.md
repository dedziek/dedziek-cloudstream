# Dedziek CloudStream

Repozytorium dodatków do **CloudStream na Androidzie i Android TV / Google TV**. Do korzystania z opublikowanego dodatku potrzebny jest aktualny [CloudStream pre-release (Beta)](https://github.com/recloudstream/cloudstream/releases/tag/pre-release).

## Dostępne dodatki

| Dodatek | Źródło w CloudStream | Wymagania |
|---|---|---|
| **FilmanProvider** | **Filman — konto darmowe (test)** | Darmowe konto Filman, Android 6.0 lub nowszy i działający Android System WebView do logowania |
| **AnimeWatchProvider** | **AnimeWatch** | Aktualny CloudStream Beta, Android 6.0 lub nowszy; WebView do awaryjnego okna odtwarzacza |

Filman obsługuje katalog filmów i seriali, wyszukiwanie, listę odcinków oraz zwykłe zewnętrzne odtwarzacze przez ekstraktory CloudStream. Dodatek ma status wersji testowej. Dostępność odtwarzania zależy również od wybranego hostingu i aktualnego działania strony.

Od wersji **FilmanProvider 2** nazwy źródeł w odtwarzaczu zawierają wersję językową i jakość ze strony, np. **Voe · Lektor_AI · 720p** lub **Streamtape · Napisy · 1080p**. Oznaczenia takie jak `ENG`, `Lektor`, `Lektor_AI`, `Napisy` i `Napisy_Tansl` są zachowywane zgodnie z opisem Filmana. Jeśli strona nie podaje wersji, dodatek jej nie zgaduje.

**AnimeWatchProvider** udostępnia katalog anime, wyszukiwanie, filmy i listy odcinków z animewatch.pl bez logowania. Ma 18 poprawnych testów parsera i odczytu odtwarzaczy; wymaga jeszcze próby odtwarzania na urządzeniu. Przy problemie z ekstraktorem Byse otwiera zwykły odtwarzacz strony do ręcznego uruchomienia. [Szczegóły i ograniczenia](ANIMEWATCH.md).

## Instalacja repozytorium

1. W CloudStream otwórz **Ustawienia → Rozszerzenia → Dodaj repozytorium**. Nazwy opcji mogą się nieznacznie różnić zależnie od wersji i układu aplikacji.
2. W polu adresu wklej cały poniższy link. Jeśli aplikacja pyta o nazwę, wpisz **Dedziek CloudStream**.

   ```text
   https://github.com/dedziek/dedziek-cloudstream/raw/refs/heads/main/repo.json
   ```

3. Otwórz dodane repozytorium i zainstaluj wybrany dodatek: **FilmanProvider** lub **AnimeWatchProvider**.
4. Dla Filmana skonfiguruj logowanie według instrukcji poniżej i wybierz źródło **Filman — konto darmowe (test)**. Dla AnimeWatch wybierz źródło **AnimeWatch** — konfiguracja konta nie jest wymagana.

Jeśli repozytorium jest już dodane i działa, wystarczy odświeżyć jego listę dodatków. Ponowne dodawanie repozytorium nie jest potrzebne. Aktualizacje dodatków są publikowane pod tym samym adresem.

## Logowanie do Filmana

1. Otwórz ustawienia **FilmanProvider** — ikonę zębatki przy rozszerzeniu.
2. Wybierz **Otwórz logowanie**.
3. Zaloguj się na swoje darmowe konto w formularzu strony i wykonaj wymagane przez stronę potwierdzenia.
4. Po zakończeniu logowania naciśnij **Zapisz sesję**.
5. Sprawdź wyszukiwanie i odtwarzanie. Jeśli dodatek ponownie prosi o logowanie, wróć do jego ustawień i zapisz nową sesję po zalogowaniu.

Komunikat zapisu potwierdza zapis ciasteczek; poprawność logowania sprawdzisz, korzystając ze źródła. Dodatek korzysta ze zwykłych źródeł dla darmowego konta. Nie obsługuje API Premium ani czasowych podglądów „free premium”.

## Telefon i telewizor

Na Android TV / Google TV dodaj ten sam link repozytorium. Do logowania potrzebny jest działający **Android System WebView** i możliwość wpisania danych konta; część urządzeń TV nie udostępnia odpowiedniego WebView. Zgodność z konkretnymi modelami telewizorów nie została przetestowana.

Sesję Filmana trzeba utworzyć osobno na każdym urządzeniu. Kopia ustawień CloudStream nie przenosi zapisanej sesji.

## Najczęstsze problemy

### „Nie znaleziono tego repozytorium”, „Niepoprawny URL” lub „404: Not Found”

- Skopiuj cały adres z sekcji instalacji i wklej go do pola adresu repozytorium.
- Otwórz ten sam link w przeglądarce na telefonie. Powinien wyświetlić tekst JSON z polami `name`, `manifestVersion` i `pluginLists`.
- Po wcześniejszej nieudanej próbie aplikacja może nadal korzystać z zapamiętanej odpowiedzi. W ustawieniach Androida wybierz **Aplikacje → CloudStream Beta → Pamięć → Wyczyść pamięć podręczną**, uruchom CloudStream ponownie i spróbuj dodać link jeszcze raz. Do tego kroku służy opcja czyszczenia pamięci podręcznej; zachowaj dane aplikacji.

### Katalog działa, ale wyszukiwanie lub odtwarzanie prosi o logowanie

Otwórz zębatkę **FilmanProvider**, zaloguj się ponownie i wybierz **Zapisz sesję**. Sesja może wygasnąć po stronie serwisu.

### Nie działa wybrany odtwarzacz

Wybierz inne dostępne źródło. Działanie poszczególnych hostingów zależy od ekstraktorów CloudStream i może zmieniać się niezależnie od dodatku. Sprawdź też, czy ten sam materiał jest dostępny bezpośrednio na stronie.

### Zainstalowano wcześniej lokalny plik `.cs3`

Przy przejściu na instalację z repozytorium usuń wcześniejszą lokalną kopię dodatku z `Cloudstream3/plugins/` i uruchom aplikację ponownie, aby uniknąć ładowania dwóch kopii. Zapisana sesja jest przechowywana osobno.

## Prywatność sesji Filmana

Hasło wpisujesz w formularzu strony. Dodatek nie odczytuje go z formularza. Ciasteczka sesji i identyfikator WebView są zapisywane lokalnie w zaszyfrowanym pliku z kluczem Android Keystore, poza kopią zapasową aplikacji.

Żądania z ciasteczkami są ograniczone do `https://filman.cc`; ciasteczka nie są przekazywane zewnętrznym odtwarzaczom ani publikowane w repozytorium. Przycisk **Usuń zapisaną sesję** usuwa lokalny zapis i ciasteczka Filmana dostępne dla okna logowania.

## Zgłaszanie problemów

W [Issues](https://github.com/dedziek/dedziek-cloudstream/issues) podaj wersję CloudStream, model urządzenia, nazwę dodatku, etap wystąpienia problemu i dokładny komunikat błędu. Pomocny będzie zrzut ekranu bez danych logowania. Nie publikuj hasła ani ciasteczek sesji.

## Kod, kompilacja i publikacja

- Gałąź **`main`** zawiera kod źródłowy, instrukcję i manifest repozytorium.
- Gałąź **`builds`** zawiera opublikowane pliki `.cs3`, `plugins.json`, `repo.json` i sumy kontrolne.
- [GitHub Actions](https://github.com/dedziek/dedziek-cloudstream/actions) kompiluje dodatki, uruchamia testy i publikuje pliki instalacyjne.
- Parametry kompilacji i wyniki testów Filmana opisuje [BUILD-INFO.md](BUILD-INFO.md).

Do lokalnej kompilacji przygotuj JDK 21 i Android SDK z platformą 35. Wskaż SDK przez `ANDROID_HOME` lub lokalny plik `local.properties`. Zależności są pobierane z Google Maven, Maven Central, JitPack i oficjalnego wydania CloudStream.

Windows:

```powershell
.\gradlew.bat :FilmanProvider:make :AnimeWatchProvider:make testDebugUnitTest makePluginsJson --no-daemon
```

Linux / macOS:

```sh
sh gradlew :FilmanProvider:make :FilmanProvider:testDebugUnitTest makePluginsJson --no-daemon
```

Wynik kompilacji Filmana: `FilmanProvider/build/FilmanProvider.cs3`. Raport testów: `FilmanProvider/build/reports/tests/testDebugUnitTest/index.html`.

Workflow **Build and publish CloudStream repository** uruchamia się po zmianach kodu na `main` / `master` albo ręcznie przez **Actions → Run workflow**. Zmiany wyłącznie w plikach Markdown nie uruchamiają kompilacji.

Przed publikacją zmienionego dodatku zwiększ całkowitą liczbę `version` w jego `build.gradle.kts`, aby CloudStream rozpoznał aktualizację. Do publikacji automat używa standardowego `GITHUB_TOKEN` z uprawnieniem `contents: write`; dane konta Filman nie są potrzebne.
