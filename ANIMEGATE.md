# AnimeGateProvider v12

Dodatek udostępnia katalog anime, wyszukiwanie, opisy, odcinki i odtwarzanie przez Playmogo. Użytkownik potwierdził działanie odtwarzania w v10; v11 upraszcza ustawienia i usuwa narzędzia diagnostyczne prototypu.

## V12 — błąd kontenera 3003

Przed przekazaniem adresu do odtwarzacza dodatek odczytuje do 8 KB odpowiedzi hostingu. Rozpoznaje playlistę HLS i manifest DASH po zawartości, również gdy adres nie ma rozszerzenia lub nagłówek serwera wskazuje MP4. Przekazuje właściwy typ do CloudStream, zachowując adres, referer i nagłówki ekstraktora. Odpowiedzi HTML, JSON, puste lub z nierozpoznanym kontenerem nie są już zgłaszane jako poprawne wideo; dodatek próbuje pozostałych wersji i podaje zwięzłą przyczynę braku źródła.

Próbka używa żądania zakresu i zamyka strumień po 8 KB, także gdy serwer zignoruje zakres. Obsługuje ograniczoną liczbę przekierowań i jedną próbę bez zakresu tylko po HTTP 416. Nie ponawia HTTP 401/403/429. Dane sesji AnimeGate nie są przekazywane hostingowi.

Zgłoszony błąd 3003 oznacza nierozpoznany lub nieobsługiwany kontener. W kontrolowanym teście prawdziwego odtwarzacza CloudStream publiczna próbka HLS bez rozszerzenia, oznaczona jako MP4, zakończyła się błędem `UnrecognizedInputFormatException`; po oznaczeniu HLS odtwarzała się ze stanem `PLAYING`. To test mechanizmu rozpoznawania formatu, nie test zalogowanego odcinka AnimeGate. Do potwierdzenia konkretnego zgłoszenia nadal potrzebny jest tytuł i numer odcinka lub jego adres.

## Logowanie

1. Zainstaluj lub zaktualizuj AnimeGateProvider w repozytorium Dedziek CloudStream.
2. Otwórz ustawienia dodatku i zaloguj się przez Discord na stronie AnimeGate. Serwis może wymagać członkostwa na swoim serwerze Discord.
3. Po powrocie do AnimeGate wybierz **Zapisz**. Przycisk sprawdza sesję i zapisuje ją na urządzeniu.
4. Zamknij okno i wybierz źródło **AnimeGate** na ekranie głównym CloudStream.

W ustawieniach są tylko trzy przyciski: **Zapisz**, **Wyloguj**, **Zamknij**. Działającego dodatku nie trzeba diagnozować przez F12 ani kopiować raportów. Nie ma przycisków testowania katalogu, sprawdzania zapisu i eksportu raportu.

## Sesja i odtwarzanie

Zapis sesji jest szyfrowany na urządzeniu i ważny najwyżej godzinę. Jeśli dodatek poprosi o logowanie, wróć do ustawień i odnowisz zapis przyciskiem **Zapisz**. Zapisana sesja otwiera stronę AnimeGate bez ponownego wymuszania logowania. Serwis może zakończyć sesję wcześniej.

**Wyloguj** usuwa zapis dodatku i cookies obsługiwanych adresów AnimeGate, po czym otwiera logowanie. Pozostałe serwisy i konto Discord zachowują swoje sesje.

Dla odcinka pobierany jest świeży, krótkotrwały bilet. Bilety nie są zapisywane ani raportowane. Cookies AnimeGate nie są przekazywane zewnętrznemu hostingowi. Wybór źródła zawiera nazwy wersji, np. Lektor IVO, Napisy i Lektor AI.

Katalog i wyszukiwanie nadal pokazują do ośmiu wyników; pełna paginacja pozostaje do wdrożenia. Dostępność wideo zależy od serwisu i hostingu. Nie trzeba wyłączać ochrony systemu ani zmieniać ustawień zabezpieczeń przeglądarki.

[Techniczny kontrakt API i historia rozpoznania](ANIMEGATE-API.md).