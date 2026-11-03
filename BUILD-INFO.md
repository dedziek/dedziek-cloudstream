# Weryfikacja wersji 1

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
| CloudStream Gradle plugin | `com.github.recloudstream:gradle:-SNAPSHOT` |

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
