# AnimeGateProvider v11

Dodatek udostępnia katalog anime, wyszukiwanie, opisy, odcinki i odtwarzanie przez Playmogo. Użytkownik potwierdził działanie odtwarzania w v10; v11 upraszcza ustawienia i usuwa narzędzia diagnostyczne prototypu.

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