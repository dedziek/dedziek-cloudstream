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

## Ekino TV v5 — test Androida i odmowa serwera

Po udostępnieniu `/dev/kvm` emulator Androida uruchomił się w około 44 sekundy. Zainstalowano oficjalny CloudStream 4.8.0-PRE i publiczny dodatek v4; hash pliku załadowanego przez aplikację był zgodny z katalogiem GitHub. Menu v4 sprawdzono także przy zapisanym stanie CAPTCHA: trzy akcje są widoczne w rzeczywistym oknie.

Pierwszy test nie miał połączenia Wi-Fi i zgłosił WebView -2; po naprawieniu sieci ponowiono test. O 12:34 UTC test aplikacji przeszedł katalog, wyszukiwanie „Jego” i szczegóły materiału, a następnie zatrzymał się na CAPTCHA ramki `player`. Podczas dalszego porównania w tej samej sieci Chrome również otrzymał z `/watch/verify.php` odpowiedź HTTP 200, `{"error":true}`. Nie przechwytywano ani nie podstawiano tokenów weryfikacji. Sam wynik nie ustala, dlaczego witryna odrzuciła potwierdzenie.

Odczyt rzeczywistego DOM po zmianie hostingu pokazał jednocześnie ukryty `player` (`getClientRects().length=0`) i aktywny `dood` (`=1`). V4 oceniała oba dokumenty jako równorzędne. V5 uwzględnia aktywność, aktywuje wybraną kartę, zachowuje w pamięci adres zatrzymanej ramki i przerywa ponawianie po komunikacie błędu z procedury weryfikacji witryny. Dopuszcza też renderowanie HTTP 403 w ręcznym oknie. Nie oznacza to usunięcia odmowy po stronie Ekino ani potwierdzenia odtwarzania.

Walidacja v5: **50 testów Ekino TV, 0 błędów i pominięć**, kompilacja paczki. Rzeczywisty test dostawcy v5 z 13:37:24–13:37:39 UTC odczytał katalog, wyszukał „Cena” i otworzył „Cena kłamstwa”; zakończył się CAPTCHA odtwarzacza, bez źródła wideo. Ręczne okno otworzyło bezpośrednio zatrzymaną ramkę z widocznym przyciskiem „Wczytaj film”. Kontrolowane wywołanie tego samego komunikatu błędu w rzeczywistym WebView sprawdziło obsługę odmowy: widoczny opis zatrzymania i zakończenie dokumentu przez `about:blank`. To kontrola reakcji aplikacji, nie dowód naturalnego błędu ani zaakceptowania CAPTCHA. Test używał Androida 15 i WebView 124.0.6367.219; wiek silnika ogranicza ocenę zgodności z bieżącą weryfikacją. Nie potwierdzono pełnego odtwarzania.

## Ekino TV v6 — ustąpienie CAPTCHA po restarcie routera

Użytkownik zgłosił, że po restarcie routera CAPTCHA nie pojawia się. Doprecyzował, że po restarcie nie uruchamiał jeszcze v5. Nie porównano adresu IP przed i po restarcie ani logów właściciela witryny, dlatego nie ustalono konkretnej reguły odmowy. Cloudflare opisuje wiele możliwych powodów pętli, w tym sieć, zgodność przeglądarki i detekcję zachowania: [Challenge solve issues](https://developers.cloudflare.com/cloudflare-challenges/troubleshooting/challenge-solve-issues/). Nie wykonuje się serii żądań w celu celowego wywołania kolejnej blokady.

Audyt klienta wykazał cztery odtwarzalne problemy. Przed poprawką cztery nowe testy regresji zakończyły się błędem: po automatycznej bramce spóźnione GET/POST nie były odrzucane; zamknięcie ręcznego okna nie blokowało późnych żądań; gotowy katalog pozostawał aktywnym dokumentem bez limitu bezczynności; po ponownym wczytaniu filmu używano identyfikatora kontrolki z wcześniejszego DOM. Zapisane wcześniej strony tego samego materiału zawierały różne identyfikatory kontrolek. To potwierdza konieczność powiązania kontrolki z bieżącym dokumentem, nie regułę blokady serwera.

V6 odrzuca żądania zamkniętego dokumentu w obu trybach, wygasza bezczynne strony i zamyka je przy przejściu aplikacji w tło. Wybiera aktualną kontrolkę hostingu i języka po przeładowaniu materiału. Wyszukiwanie używa `WebView.postUrl` z kodowaniem formularza i istniejącym profilem przeglądarki; po ustaleniu domeny sesji nie potrzebuje dodatkowego GET strony głównej. Nie przenosi sesji do natywnego klienta HTTP ani nie modyfikuje mechanizmu weryfikacji.

Pierwsza rzeczywista próba kandydata v6 (14:11:41–14:12:51 UTC) zakończyła się `Success Ekino TV` dla „De Premier”. Źródło pojawiło się o 14:12:05, ale operacja skończyła się 46 sekund później. Dalszy audyt zapisanego DOM wykazał ikony języka z atrybutem `original-title`, podczas gdy parser odczytywał tylko `title`. Utrata opisu języka wyłączała pomijanie równoważnych hostingów po sukcesie. W finalnym v6 parser uwzględnia atrybuty po inicjalizacji podpowiedzi, normalizuje równoważne polskie etykiety i traktuje brak języka jako jedną grupę zamiast sprawdzać każdy taki hosting po znalezieniu wideo. To konkretny mechanizm zbędnych prób do odtwarzaczy. Nie dowodzi jednak, jaki próg lub reguła strony wywołały wcześniejszą CAPTCHA.

Raport pierwszego kandydata potwierdził skalę zbędnych prób w realnej aplikacji: `strony=6, ramki=4, weryfikacja=0`. Po odczycie źródeł Byse z `player` uruchomiono jeszcze `dood`, `file1` i `upzone`, mimo że nie dostarczyły kolejnego wideo. Stan nie był wstrzymany. Finalna poprawka pomijania równoważnych języków usuwa te dodatkowe próby; nie jest mechanizmem obchodzenia CAPTCHA.

Walidacja finalnego v6: **60 testów, 0 błędów i pominięć**, kompilacja CS3. Rzeczywisty test dostawcy CloudStream 4.8.0-PRE na Androidzie 15 z WebView 124.0.6367.219, 14:18:42–14:18:59 UTC, wyszukał „W”, otworzył „W moim stylu” i zwrócił `Video loaded: player · Lektor PL`, `Links loaded: 1`, `Success Ekino TV`. Raport: `strony=4, ramki=1, weryfikacja=0, zatrzymane=0`; źródła Byse odczytane, bez dalszych hostingów. Próba dotyczyła innego filmu niż pierwszy kandydat, więc nie jest pomiarem wydajności tego samego filmu. Nie zmieniano sieci emulatora ani jego cookies pomiędzy tymi próbami. Przed pierwszą próbą usunięto jedynie zapisane wstrzymanie w testowym emulatorze po wcześniejszych kontrolowanych testach; nie jest to automatyczna migracja ani mechanizm akceptacji CAPTCHA w wydaniu dla użytkownika.

Potwierdzono sukces testu dostawcy w tej chwili i w tej sieci. Nie testowano całego filmu na telefonie użytkownika i nie dowiedziono, że witryna nigdy nie uruchomi ponownej weryfikacji. Decyzji serwera nie da się odtworzyć wyłącznie z raportu klienta.
