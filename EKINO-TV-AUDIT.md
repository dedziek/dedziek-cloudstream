# Ekino: analiza historii i zgłoszeń, 5 listopada 2026

Zakres: wszystkie 13 zmian EkinoProvider w historii `main`, od `3b3b1c4` do `37b5d82`, źródła i testy tych wersji oraz dostarczone raporty użytkownika. Nowy EkinoTvProvider nie jest kontynuacją numeracji starej paczki.

## Historia

| Wersja | Commit | Zmiana i jej znaczenie |
|---|---|---|
| 1 | [3b3b1c4](https://github.com/dedziek/dedziek-cloudstream/commit/3b3b1c44c36d0bcb78d33e8c31c1b4ae3d58488d) | Katalog, POST wyszukiwania, seriale, zwykłe odtwarzacze, osobne logowanie WebView. Odczyt strony klientem HTTP. |
| 2 | [195d84e](https://github.com/dedziek/dedziek-cloudstream/commit/195d84eb27e743b43c709c3ceb0574c3d9632b93) | Rozpoznanie odtwarzacza bez odliczania; v1 zakładała obowiązkowe `time`. |
| 3 | [2056910](https://github.com/dedziek/dedziek-cloudstream/commit/2056910f72feaff033801f5d59fc535c9751c26f) | Zachowanie anonimowej sesji PHP w obrębie operacji, obsługa publicznej strony pośredniej. |
| 4 | [0116b9b](https://github.com/dedziek/dedziek-cloudstream/commit/0116b9b765d3c63670be350ad03481f79c028b58) | Automatyczne otwarcie ręcznej weryfikacji i pojedyncze ponowienie. Nie usuwało różnic klient HTTP / WebView. |
| 5 | [ac906b3](https://github.com/dedziek/dedziek-cloudstream/commit/ac906b369014e86aa68a2aee35bad5f9100b4d2c) | Powrót do zachowania v3, bez automatycznie otwieranego okna. |
| 6 | [fc47a7d](https://github.com/dedziek/dedziek-cloudstream/commit/fc47a7d9345e8bc8f047fc830f84929022c716bb) | Odczyt zwykłych źródeł HTML i dokładniejsze błędy hostingów. |
| 7 | [525ccce](https://github.com/dedziek/dedziek-cloudstream/commit/525ccce18618a8b6a837e85e9db4147e1ef2748a) | Oddzielna CAPTCHA ramki odtwarzacza; zachowanie już znalezionych linków. |
| 8 | [b91cd9b](https://github.com/dedziek/dedziek-cloudstream/commit/b91cd9b039932da2259136b30c81e1ca04aea051) | Ręczne otwarcie materiału, cookies i User-Agent z WebView. Zapis sesji nie potwierdzał uzyskania dostępu do ramki. |
| 9 | [ae8ebd6](https://github.com/dedziek/dedziek-cloudstream/commit/ae8ebd6072017d932c76541a64dc06886149cd99) | Kolejkowanie pojedynczych żądań oraz trwała pauza po CAPTCHA/403/429. Operacje mogły nadal przeplatać żądania. |
| 10 | [a0ee029](https://github.com/dedziek/dedziek-cloudstream/commit/a0ee029d265c1c35ceb7a5b42092cf0493dd4046) | Usunięcie podwójnego User-Agent z klienta NiceHttp; raport odtwarzania. Naprawa nie dowodziła usunięcia problemów sieciowych. |
| 11 | [2ead45f](https://github.com/dedziek/dedziek-cloudstream/commit/2ead45f041b88a0735d8f458594f949af067071b) | Obsługa rzeczywistego publicznego API Byse, AES-GCM v5 i opisy niedostępnych hostingów. |
| 12 | [45d4092](https://github.com/dedziek/dedziek-cloudstream/commit/45d4092e71f79702bc5788a6f024101763d49c35) | Pauza po błędzie transportu oraz dziennik DNS/TCP/TLS/HTTP. |
| 13 | [37b5d82](https://github.com/dedziek/dedziek-cloudstream/commit/37b5d8264a729166b81f007dbd1cc240385956c9) | Pomijanie kolejnych odtwarzaczy tej samej rozpoznanej wersji językowej po znalezieniu wideo. |

## Raporty i rozdzielenie przyczyn

- **15:33, Zabójcza gra:** wyszukiwanie działało, dostęp zatrzymał się na CAPTCHA `mix`. Sam wynik wyszukiwania nie potwierdza dostępu do ramki.
- **16:17, Powrót, v10:** strona materiału i cztery ramki odpowiadały 200; utworzono sesję PHP. Problem dotyczył hostingów. `mfw09.org` był aplikacją Byse wymagającą inicjalizacji JavaScript/API, nie statycznego parsowania HTML. Dood przekierowywał do niedostępnego filmu, onlystream nie rozwiązywał DNS, upzone zgłaszał wyczerpany darmowy transfer. To oddzielne awarie od Turnstile.
- **17:07:** strona główna przekroczyła czas, kolejne wyszukiwania nie dały wyników. Końcowy komunikat testera CloudStream nie zachowywał przyczyny każdego wyjątku wyszukiwania. Brak wyników nie dowodzi uszkodzenia parsera.
- **22:06, Gang-over i Zabójcza gra:** dwa testy załadowały po jednym źródle. To potwierdza działający odczyt linku w tej chwili, nie pełne odtwarzanie filmu ani trwałe rozwiązanie CAPTCHA.
- **22:08, Gang-over, v12:** sesja była zapisana, User-Agent pochodził z WebView. Strona filmu była zwykła, ramka zwróciła 200 z Turnstile. Odpowiedź zajęła 103 ms; nie był to timeout DNS/TLS. `clearance=nie` nie dowodzi błędnego logowania: Turnstile nie musi wydawać `cf_clearance`.
- **22:34, v13:** ten sam rodzaj bramki przy odpowiedzi 200 w 76 ms, tym razem bez zapisanej sesji. Poprawa kolejności odtwarzaczy nie mogła usunąć bramki występującej już przy pierwszym z nich.
- **Chrome zwykły i incognito:** po zamknięciu CloudStream pętla pozostała na Wi‑Fi, a na danych komórkowych strona działała. Wskazuje to na czynnik sieciowy; nie znamy reguł serwera, okresu ograniczenia ani tego, czy dotyczyło wyłącznie adresu IP. Nie ma podstaw do obiecywania odblokowania po konkretnej liczbie minut.
- **Okno pomocy Cloudflare:** użytkownik otworzył je z problematycznego widgetu w tej samej przeglądarce i niczego tam nie klikał. Zbieżność z późniejszym sukcesem nie wykazuje przyczyny. Kod strony pomocy rozdziela odczyt informacji od świadomego polecenia odświeżenia.
- **Ray ID:** podane identyfikatory `a55eb9c1bf3cff54`, `a55ed522ae7a03be-HAM` i `a55efb4c3936b4c8-HAM` identyfikują żądania. Nie stanowią samodzielnej diagnozy. Dostęp do logów i reguł właściciela witryny nie był dostępny.

W kontrolowanym wcześniejszym odczycie zwykłych ramek, przy tym samym PHP i User-Agent oraz wymaganym odliczaniu, pięć odpowiedzi było zwykłych, a szósta zawierała Turnstile. Próby zatrzymano. Nie jest to dowód stałego limitu sześciu żądań i nie odtwarza interaktywnej pętli zgłoszonej na telefonie.

Odczytany skrypt bramki wysyła wynik Turnstile do `/watch/verify.php` i odświeża dokument zarówno po sukcesie, jak i po odpowiedzi błędnej. Dlatego zielony widget i odświeżenie nie potwierdzają akceptacji przez backend Ekino. Naprawa logiki serwera należy do właściciela witryny.

## Decyzje nowej implementacji

Usunięto osobny klient HTTP Ekino, ręczne kopiowanie cookies, zapisywanie User-Agent innego silnika, szyfrowany eksport sesji oraz niezależny przycisk wznowienia bez sprawdzenia strony. Zastąpiła je jedna przeglądarka i kolejka całych operacji. Nowy kod zachowuje wyłącznie rozwiązania wynikające z protokołu strony: POST wyszukiwania, zwykłe kontrolki ShowPlayer, opis języka, format katalogu/odcinków i protokół zewnętrznego Byse. Algorytm inicjalizacji Byse ma osobną informację MIT.

Pauza i publiczny adres odzyskania dostępu są trwałe. Weryfikacja odbywa się w tej samej instancji i profilu WebView. Wznowienie wymaga strony bez bramki, a po CAPTCHA odtwarzacza również rzeczywistego odnośnika w jego ramce. Pętla nawigacji i skrypty opuszczanego dokumentu są zatrzymywane. Nie są automatycznie przesyłane tokeny Turnstile, ponawiane odmówione żądania ani zmieniane sieci czy tożsamości przeglądarki.

Podczas rozpoczęcia przebudowy `ekino-tv.pl` odpowiedziało przekierowaniem 301 do `ekino.ws`, a domena docelowa zwróciła 403 z środowiska roboczego. Dalszych automatycznych prób nie wykonywano. Nowa paczka wymaga próby odtwarzania i ręcznej weryfikacji na urządzeniu użytkownika; lokalne testy i kompilacja nie potwierdzają akceptacji przez Cloudflare w jego sieci.


## Ekino TV v2 — timeout po pierwszym filmie

Użytkownik potwierdził działanie pierwszego filmu w nowym dodatku. Przy zmianie materiału wystąpił błąd. W kolejnym teście (23:47:46) strona główna i wszystkie wyszukiwania zostały odrzucone w tej samej sekundzie. Komunikat pochodzi z kontroli zapisanego stanu przed nawigacją, więc nie jest wynikiem nowego żądania do serwera ani dowodem CAPTCHA. Log nie podaje etapu wcześniejszego timeoutu.

W kodzie v1 potwierdzono trzy problemy: odczyt katalogu zależał od `onPageFinished`, zwykły timeout zapisywał tę samą trwałą pauzę co weryfikacja, a obsługa `TimeoutCancellationException` przechwytywała również krótszy limit aplikacji nadrzędnej. Dodatkowo po przejściu do `about:blank` sam adres WebView nie był wystarczającym potwierdzeniem aktywnego dokumentu do kolejnego formularza wyszukiwania.

V2 sprawdza gotowość i identyfikator DOM niezależnie od `onPageFinished`, odrzuca poprzedni dokument i spóźnione callbacki zamkniętej strony, a przed wyszukiwaniem przygotowuje rzeczywistą stronę w tej samej sesji. Własny limit czasu daje jedynie 10 sekund przerwy. Zewnętrzne anulowanie nie zapisuje przerwy. Aktualizacja usuwa dokładnie stary zwykły timeout zapisany przez v1; nie usuwa rozpoznanej CAPTCHA.

Nowe testy obejmują trzy kolejne materiały i ich ramki bez `onPageFinished`, stary callback i DOM poprzedniego filmu, wyszukiwanie po zakończeniu odtwarzania i po poprzednim wyszukiwaniu, własny i nadrzędny timeout, migrację stanu v1 oraz zachowanie rzeczywistej bramki. Osobny test Node wykonuje faktyczny skrypt odczytu DOM, w tym jego identyfikator nawigacji i pomijanie niedostępnych ramek. To kontrolowana próba kodu, bez wysyłania kolejnych żądań do Ekino.

Walidacja lokalna v2: kompilacja paczki i **38 testów Ekino TV — 0 błędów, 0 pominięć**; trzy nowe testy Node skryptu DOM również przeszły. Metadane i manifest paczki potwierdzają wersję 2. Akceptacja strony na telefonie użytkownika po tej aktualizacji wymaga jego próby.


## Ekino TV v3 — stary błąd podczas ładowania nowego filmu

Dalsza kontrola v2 ujawniła lukę w obsłudze zdarzeń: test v2 obejmował błąd starej strony po jej zamknięciu, ale przed rozpoczęciem nowej nawigacji. Nie obejmował tego samego błędu już w czasie ładowania następnego filmu, gdy `WebView.getUrl()` nadal zwracał stary adres. Jest to dopuszczone zachowanie opisane w [dokumentacji Android WebView](https://developer.android.com/reference/android/webkit/WebView#getUrl()).

Nowy test w tym stanie otrzymał na v2 trwałe wstrzymanie po HTTP 403 poprzedniego materiału, mimo rozpoczętej nawigacji do innego filmu. V3 ocenia zgodność z żądaną stroną, zamiast uznawać stary `getUrl()` za potwierdzenie bieżącego żądania. Doprecyzowano również przypisywanie błędu certyfikatu do aktualnej strony lub ramki. Każdy wadliwy certyfikat jest odrzucany; błąd poprzedniego filmu nie przerywa kolejnego.

Walidacja v3: kompilacja i **40 testów Ekino TV — 0 błędów i pominięć**. Nowe przypadki sprawdzają zarówno ignorowanie starego HTTP/TLS, jak i zatrzymanie przy rzeczywistym błędzie nowego materiału. Test HTTP najpierw wykazał usterkę na v2, a po poprawce przeszedł. Są to kontrolowane zdarzenia przeglądarki; nie ustalono, czy ten dodatkowy scenariusz występował na telefonie użytkownika. Nie ponawiano żądań do Ekino.


## Ekino TV v4 — akcje niewidoczne w ustawieniach

Zgłoszenie z 6 listopada, 08:36:24: zachowane wstrzymanie „CAPTCHA odtwarzacza” blokowało automatyczny test; użytkownik widział w ustawieniach tylko tytuł v3, status i „Zamknij”. W kodzie nowego menu znaleziono jednoczesne `setMessage()` i `setItems()`. [Dokumentacja Androida](https://developer.android.com/develop/ui/views/components/dialogs#AddingAList) wyjaśnia, że komunikat i lista zajmują ten sam obszar i nie mogą być wyświetlone razem.

Wcześniejszy test pobierał `listView` i wywoływał `performItemClick()` programowo, mimo że lista nie była dołączona do widocznego okna. Test nie dowodził dostępności opcji dla człowieka. Nowy test widoczności najpierw wykazał błąd na v3. V4 zastępuje listę osobnymi przyciskami w przewijanym widoku. Testy klikają te same dołączone przyciski, sprawdzają ich rzeczywisty widoczny prostokąt, obszar dotyku i możliwość ustawienia fokusu.

Sprawdzane są układy pionowy i poziomy na API 28 i 35 w Robolectric, zapisane wstrzymanie z poprzedniego uruchomienia, skopiowanie raportu bez uruchamiania strony oraz przejście: widoczny przycisk → zapamiętany film → nieukończona CAPTCHA → rzeczywisty link odtwarzacza → zamknięcie okna i zwolnienie pauzy. To test kontrolowanej zawartości WebView, nie wykonanie CAPTCHA na telefonie użytkownika.

Kontrola kodu i paczki nie wykazała starego modułu `pl.ekinocloudstream`. Pozostawiony klucz danych `dedziek_ekino_tv_v1` należy do nowego `pl.dedziek.ekinotv`; celowo zachowuje jego stan między aktualizacjami. Nie migruje danych ani klienta HTTP starego EkinoProvider.

Walidacja lokalna v4: **46 testów Ekino TV, 0 błędów i pominięć**, kompilacja i kontrola manifestu v4. Sprawdzono nowe menu w DEX oraz brak starego modułu w źródłach i paczce.
