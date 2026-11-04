# Weryfikacja Filmana

## Wersja 4 — widoczny obszar logowania

4 listopada 2026: zastąpiono panel logowania `AlertDialog` własnym układem w `Dialog`. Poprzedni układ umieszczał WebView o wysokości `0` i wadze `1` wewnątrz panelu, który zależnie od motywu nie przydzielał mu wolnej przestrzeni. Zrzut z urządzenia pokazywał nagłówek, stan strony i przyciski nad pustą częścią okna.

Nowy układ zajmuje całą dostępną wysokość okna, przydziela pozostałe miejsce przeglądarce i umieszcza przyciski pod nią. Zachowano dostosowanie do klawiatury przez `SOFT_INPUT_ADJUST_RESIZE`. Długi komunikat stanu zajmuje najwyżej trzy wiersze. Ustawienia sieci, weryfikacji Cloudflare i zapisu sesji pochodzą z wersji 3.

Testy Robolectric sprawdzają rzeczywiste pomiary widoków Androida w pionie, poziomie i przy zmniejszonej wysokości okna z długim błędem, na API 28 i 35. W miejscu przeglądarki używają zwykłego widoku Androida, ponieważ atrapa silnika WebView w Robolectric nie wykonuje pomiaru. Obejmują miejsce przydzielone stronie, położenie przycisków oraz zamknięcie okna. Nie potwierdzają renderowania strony, logowania kontem ani działania serwisu na fizycznym urządzeniu.

[GitHub Actions](https://github.com/dedziek/dedziek-cloudstream/actions/runs/41799645278): kompilacja obu dodatków, testy i walidacja paczek zakończone poprawnie. Filman: **43 testy, 0 błędów**, w tym sześć prób układu na dwóch wersjach Androida. Po dwóch nieudanych próbach pobrania agregującego POM JitPack konfiguracja wskazuje bezpośrednio opublikowany moduł narzędzia CloudStream (`com.github.recloudstream.gradle:gradle:-SNAPSHOT`).

## Wersja 3 — zgodność okna logowania z Cloudflare

4 listopada 2026: kompilacja kodu Androida i **37 testów Filmana, 0 błędów** zakończone poprawnie. Dodano cztery testy polityki nawigacji ramek i zakresu przekazywania zapisanych ciasteczek. Parametry lokalnego sprawdzenia: JDK 21.0.13, Android SDK 35, Kotlin 2.3.0.

Okno logowania akceptuje ciasteczka potrzebne osadzonej weryfikacji, zachowuje rzeczywisty identyfikator WebView i dopuszcza ramki `challenges.cloudflare.com`, `about:blank` i `about:srcdoc` w starszym callbacku nawigacji. Główny dokument i zapisane ciasteczka pozostają ograniczone do HTTPS `filman.cc`. Konfigurację porównano z [oficjalnymi wymaganiami Cloudflare dla aplikacji mobilnych](https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/).

Dodano widoczny stan ładowania, komunikaty błędów HTTP/połączenia/certyfikatu i ręczne odświeżanie. Nie jest wykonywane automatyczne rozwiązywanie weryfikacji. Walidacja certyfikatów i blokada dostępu do plików pozostają aktywne.

Nie potwierdzono przejścia Cloudflare ani logowania na fizycznym urządzeniu. Jedna publiczna próba adresu `/logowanie` z tego środowiska zwróciła HTTP 403 i stronę „Sorry, you have been blocked”; nie ponawiano jej w celu obejścia tej blokady. Poprawka zgodności WebView nie gwarantuje zniesienia odmowy dostępu nałożonej przez serwis.

## Wersja 2 — oznaczenia źródeł

3 listopada 2026: lokalna kompilacja `:FilmanProvider:make` zakończona poprawnie; **33 testy parsera, 0 błędów**. Nowe przypadki obejmują wersje językowe, brak oznaczenia, odczyt oznaczenia z klasy wiersza oraz wspólny adres z różnymi wersjami. Nazwa widoczna w odtwarzaczu zawiera wersję i jakość z tabeli Filmana, przy zachowaniu danych ekstraktora potrzebnych do odtwarzania. Wersję dodatku podniesiono do 2. Wyświetlanie na fizycznym urządzeniu wymaga próby użytkownika.

## Wersja 1

Data przygotowania: 3 listopada 2026.

## Wyniki

- `:FilmanProvider:make` — zakończone poprawnie; paczka zawiera `manifest.json` i `classes.dex`.
- `:FilmanProvider:testDebugUnitTest` — **29 testów, 0 błędów, 0 pominiętych**.
- D8/R8 8.13.19 — końcowa kompilacja bez ostrzeżeń o przepisywaniu metadanych Kotlin.
- Próba parsera na zapisanych, publicznie dostępnych stronach Filmana: katalog filmów 30 pozycji i 30 plakatów; katalog seriali 30 pozycji i 30 plakatów; poprawnie odczytany tytuł i rok filmu; rozpoznane 32 zwykłe wiersze źródeł; 17 odcinków przykładowego serialu; rozpoznana strona wymagająca logowania przy wyszukiwaniu.
- Dane testowe są krótkimi, syntetycznymi fragmentami odpowiadającymi publicznemu układowi strony. Osobna próba rzeczywistych zapisów strony nie jest próbą odtwarzania.

**Nie wykonano:** logowania kontem użytkownika w Android System WebView, próby odtworzenia po zalogowaniu ani testów na telefonie/TV. Na tym etapie plik należy traktować jako wersję testową.

Publikacja na GitHubie została zakończona: [kompilacja, testy i publikacja w Actions](https://github.com/dedziek/dedziek-cloudstream/actions/runs/41726379239) zakończyły się poprawnie. Anonimowe pobranie `repo.json`, `plugins.json` i `.cs3` z gałęzi `builds` zwróciło HTTP 200; rozmiar i SHA-256 pobranej paczki zgadzają się z metadanymi i poniższą lokalną kompilacją. Weryfikację wykonano 3 listopada 2026.

## Parametry kompilacji

| Składnik | Wersja |
|---|---|
| JDK w użytym środowisku | Oracle JDK 21.0.8 |
| Gradle Wrapper | 8.12 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.3.0 |
| Java/Kotlin bytecode target | 11 |
| D8/R8 | 8.13.19 |
| Android compile/target SDK | 35 |
| Android min SDK | 23 |
| CloudStream Android API | oficjalny `classes.jar` z wydania `pre-release` |
| CloudStream Gradle plugin | `com.github.recloudstream.gradle:gradle:-SNAPSHOT` |

API CloudStream pobrano z [oficjalnego wydania](https://github.com/recloudstream/cloudstream/releases/download/pre-release/classes.jar).
SHA-256 użytego API:

```text
38eed38126fde550fc30212455207aae7ec0641fae73af8bd3b6ea475fc35343
```

SHA-256 końcowego `FilmanProvider-v1.cs3`:

```text
c5d705993518fe3bd059dc8a8d863ed94a600efa6141c1c2a2a76c8847590d30
```

Plik `.cs3` ma 48 196 bajtów. Zależności `pre-release` i `-SNAPSHOT` są zmienne, dlatego późniejsza kompilacja może użyć innego API i dać inny hash.

## Organizacja kodu

- `FilmanPlugin.kt` — rejestracja źródła i przycisku ustawień.
- `FilmanLoginUi.kt` — ręczne logowanie w oknie oficjalnej strony.
- `FilmanSession.kt` — lokalny, zaszyfrowany zapis sesji; aktualizacja ciasteczek z właściwej domeny.
- `FilmanParser.kt` — parsowanie katalogów, odcinków i danych zwykłych odtwarzaczy.
- `FilmanProvider.kt` — żądania do strony i współpraca z ekstraktorami CloudStream.
- `src/test` — 29 przypadków regresji i 6 krótkich przykładowych stron HTML.
