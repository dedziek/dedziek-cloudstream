# Filman dla CloudStream — wersja testowa 1

Dodatek dla **darmowego konta Filman**, przygotowany dla CloudStream na Androidzie.
Ma katalog filmów i seriali, wyszukiwanie, listę odcinków i obsługę zwykłych zewnętrznych odtwarzaczy Filmana przez ekstraktory CloudStream.

**Status:** kompilacja i testy parsera zostały wykonane. Logowanie w Android System WebView i odtwarzanie po zalogowaniu wymagają jeszcze próby na fizycznym urządzeniu. Obecność pliku `.cs3` nie oznacza, że każdy zewnętrzny hosting zadziała.

## Instalacja przez repozytorium GitHub

Otwórz **CloudStream → Ustawienia → Rozszerzenia → Dodaj repozytorium** i wklej:

```text
https://raw.githubusercontent.com/dedziek/dedziek-cloudstream/builds/repo.json
```

Następnie otwórz dodane repozytorium, zainstaluj **FilmanProvider** i wybierz zębatkę dodatku → **Otwórz logowanie**. Zaloguj się samodzielnie na stronie i naciśnij **Zapisz sesję**. Na ekranie głównym wybierz **Filman — konto darmowe (test)**. Potrzebny jest aktualny CloudStream pre-release.

Przy przejściu z wcześniejszej instalacji lokalnej usuń lokalną kopię `FilmanProvider-v1.cs3` z `Cloudstream3/plugins/` i uruchom CloudStream ponownie, aby nie ładować dwóch kopii tego samego dodatku. Sesja logowania jest przechowywana osobno na urządzeniu.

## Instalacja lokalna na telefonie

1. Zainstaluj aktualny **CloudStream pre-release** z [oficjalnych wydań projektu](https://github.com/recloudstream/cloudstream/releases/tag/pre-release). Dodatek kompilowano z API tego wydania. Nie potwierdzono zgodności ze starszymi wersjami aplikacji.
2. Pobierz `FilmanProvider-v1.cs3` z plików dostarczonych razem z tą instrukcją. To rozszerzenie, więc nie instaluje się go jak aplikacji APK.
3. W menedżerze plików utwórz w **głównym katalogu pamięci wewnętrznej** folder `Cloudstream3`, a w nim `plugins`. Skopiuj plik `.cs3` do `Cloudstream3/plugins/`. Folder nie powinien znajdować się wewnątrz `Download`.
4. Zamknij i ponownie uruchom CloudStream. W **Ustawienia → Rozszerzenia → Lokalne** poszukaj `FilmanProvider`. Nazwy sekcji mogą się różnić zależnie od tłumaczenia aplikacji. Jeżeli system wymaga uprawnienia do odczytu plików, nadaj je CloudStream.
5. Otwórz **zębatkę przy rozszerzeniu**. Wybierz **Otwórz logowanie**. W oknie strony wpisz dane **swojego** konta i samodzielnie ukończ wymagane przez stronę potwierdzenia.
6. Po zalogowaniu naciśnij **Zapisz sesję**. Sam komunikat zapisu potwierdza zapis ciasteczek, nie poprawność logowania. Następnie sprawdź wyszukiwanie.
7. Na stronie głównej wybierz źródło **Filman — konto darmowe (test)**.

Źródło sposobu instalacji lokalnej: [PluginManager CloudStream](https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt).

## Pierwsza próba na urządzeniu

- Otwórz katalog i sprawdź, czy pojawiają się plakaty.
- Wyszukaj znany tytuł. Jeżeli pojawia się prośba o logowanie, wróć do zębatki dodatku i zapisz nową sesję po zalogowaniu.
- Otwórz serial i sprawdź listę sezonów oraz odcinków.
- Spróbuj odtworzyć film i odcinek. Przy zgłaszaniu problemu podaj wersję CloudStream, model telefonu, etap i dokładny komunikat. Nie udostępniaj hasła, cookies ani pliku sesji.

## Sesja i telewizor

Dodatek nie odczytuje hasła z formularza. Zapisuje ciasteczka sesji i identyfikator WebView w zaszyfrowanym pliku, z kluczem Android Keystore, w `noBackupFilesDir` aplikacji. Nie dodaje ich do konfiguracji repozytorium ani do kopii ustawień CloudStream. Żądania z tymi ciasteczkami są ograniczone do `https://filman.cc`; nie są przekazywane zewnętrznym odtwarzaczom.

Przycisk **Usuń zapisaną sesję** usuwa lokalny zapis i ciasteczka Filmana dostępne dla tego okna. Wylogowanie z konta po stronie serwera można dodatkowo wykonać na stronie Filmana.

Na Android TV/Google TV możesz dodać ten sam link repozytorium. Logowanie wymaga dostępnego **Android System WebView** i wygodnego sposobu wpisania danych. Niektóre urządzenia TV nie mają odpowiedniego WebView. Sesję trzeba utworzyć osobno na każdym urządzeniu; przeniesienie backupu telefonu jej nie przenosi. Zgodność z TV nie była testowana.

## Obsługiwany zakres i ograniczenia

- Android 6.0 / API 23 lub nowszy, aktualny CloudStream pre-release i działający Android System WebView. To wymagania kodu; nie jest to deklaracja testów na wszystkich wersjach Androida.
- Obsługiwane są zwykłe linki w tabeli `#links`: obecny mechanizm `data-id`/`data-link-id` i starszy `data-iframe`.
- Pomijane są linki `data-mp4` z czasowych podglądów „free premium”. Dodatek jest przygotowany do darmowego konta i zwykłych źródeł, a nie API Premium.
- Weryfikacja TLS pozostaje włączona. CAPTCHA i inne weryfikacje strony obsługuje użytkownik w oknie logowania; nie ma automatycznego omijania blokad.
- Zmiana układu strony, wygaśnięcie sesji, blokada WebView przez serwis albo nieobsługiwany hosting mogą wymagać poprawki. Jeżeli strona odmawia logowania w WebView, ta wersja dodatku nie gwarantuje alternatywnego sposobu wejścia.
- Źródła są dostępne w repozytorium [dedziek/dedziek-cloudstream](https://github.com/dedziek/dedziek-cloudstream). Pliki instalacyjne publikuje automat na gałęzi `builds`; status kolejnych kompilacji jest widoczny w [Actions](https://github.com/dedziek/dedziek-cloudstream/actions).

## Kompilacja kodu

Potrzebne są JDK 17 lub 21, Android SDK z platformą 35 i dostęp do Google Maven, Maven Central, JitPack oraz wydań GitHub CloudStream. Wskaż SDK przez `ANDROID_HOME` lub lokalny `local.properties`.

Windows:

```powershell
.\gradlew.bat :FilmanProvider:make :FilmanProvider:testDebugUnitTest --no-daemon
```

Linux/macOS:

```sh
sh gradlew :FilmanProvider:make :FilmanProvider:testDebugUnitTest --no-daemon
```

Wynik: `FilmanProvider/build/FilmanProvider.cs3`. Testy: `FilmanProvider/build/reports/tests/testDebugUnitTest/index.html`.

Konfiguracja korzysta z oficjalnego Androidowego API `com.lagradost:cloudstream3:pre-release`, dostarczanego przez plugin Gradle CloudStream. D8/R8 przypięto do 8.13.19, żeby poprawnie obsługiwał metadane Kotlin 2.3 ([tabela zgodności Androida](https://developer.android.com/build/kotlin-support)). Zależności `pre-release` i `-SNAPSHOT` mogą się zmieniać; parametry użytej kompilacji są opisane w `BUILD-INFO.md`.

Jeżeli downloader biblioteki w Gradle zgłasza problem połączenia z GitHub, można pobrać oficjalny plik [classes.jar](https://github.com/recloudstream/cloudstream/releases/download/pre-release/classes.jar) i umieścić go jako `caches/cloudstream/cloudstream/cloudstream.jar` we własnym `GRADLE_USER_HOME`. Nie trzeba umieszczać biblioteki w kodzie dodatku.

## Publikowanie kolejnych wersji

Workflow **Build and publish CloudStream repository** uruchamia się po zmianach kodu na `main`/`master` albo ręcznie przez **Actions → Run workflow**. Buduje `.cs3`, uruchamia testy, generuje `plugins.json`, sprawdza SHA-256 i aktualizuje gałąź `builds` zwykłym commitem. Własny token ani hasło Filmana nie są potrzebne w GitHub Actions; używany jest standardowy `GITHUB_TOKEN` z uprawnieniem `contents: write`.

Przed publikacją nowej wersji podnieś całkowitą liczbę `version` w `FilmanProvider/build.gradle.kts`, aby CloudStream rozpoznał aktualizację. Zmiana samego kodu przy tej samej wersji może nie spowodować aktualizacji na urządzeniach.

Lokalne przygotowanie plików do publikacji:

```powershell
$env:GITHUB_REPOSITORY = 'dedziek/dedziek-cloudstream'
.\gradlew.bat :FilmanProvider:make :FilmanProvider:testDebugUnitTest makePluginsJson --no-daemon
node scripts/prepare-publication.mjs dedziek/dedziek-cloudstream
```

Wynik trafia do `build/publication/`. Skrypt sprawdza, czy metadane, adres repozytorium, wersja i suma kontrolna odpowiadają skompilowanemu plikowi.
