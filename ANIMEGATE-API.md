# AnimeGate: kontrakt odczytany z zalogowanej karty

Obserwacja: 4 listopada 2026, strona `https://animegate.pl/`, połączenie przez oficjalne rozszerzenie przeglądarki. Dane poniżej pochodzą z normalnej nawigacji strony. Nie pobrano ani nie zapisano cookies, nagłówków autoryzacji czy odpowiedzi profilu użytkownika.

## Katalog i wyszukiwanie

Potwierdzone żądanie GET do `https://api.animegate.pl`:

```text
/api/v1/anime/catalog-search?mode=preview&page=1&limit=8&search=One+Punch+Man&sortBy=rating&sortOrder=desc
```

Wyszukiwanie zwróciło trzy tytuły. Koperta ma dwa poziomy `data`:

```text
{success:boolean,message:string,code:number,data:{
  data:[{id,title,slug,year,status,coverImage,totalEpisodes,effectiveRating,genres,episodeCount}],
  meta:{total,page,limit,totalPages,hasNextPage,hasPrevPage},mode:string
}}
```

`genres` jest tablicą obiektów z `name` i `slug`. `effectiveRating` używa skali 0–100: odpowiedź 83 odpowiada 8.3 w interfejsie. `coverImage` jest bezpośrednim adresem HTTPS obrazu; adres `/_next/image` w HTML jest tylko pośrednikiem obrazów Next.js.

Podpowiedzi wyszukiwania używają GET `/api/v1/anime/search-suggestions?q=One+Punch+Man&limit=10`. Zaobserwowano adres i HTTP 200; nie odczytano koperty odpowiedzi, więc parser listy nie jest stosowany do tej trasy.

Interfejs katalogu bez filtrów pokazał 338 dopasowań, podgląd do ośmiu pozycji oraz pełny widok z 15 stronami po wybraniu „Pokaż wyniki”. Parametry żądania pełnego widoku i drugiej strony nie zostały odczytane. `mode` pełnego widoku i dopuszczalne limity pozostają niepotwierdzone; nie należy ich zgadywać na podstawie wyglądu strony. Parser zachowuje metadane paginacji otrzymane w odpowiedzi.

## Szczegóły anime

GET `/api/v1/anime/slug/{slug}`, potwierdzone dla `one-punch-man` (HTTP 200).

Koperta `{success,message,code,data:{...}}`. Dane zawierają `id`, `title`, `slug`, `synopsis`, `year`, `status`, `duration`, `totalEpisodes`, `episodeCount`, `coverImage`, `bannerImage`, `effectiveRating`, `ageRating`, `season`, `anilistId`, `genres`, `themes`, `groups` i `episodes`.

`duration` ma typ string (`"24"`). `format`, `trailerUrl`, `airingStatus` i część identyfikatorów zewnętrznych mogą być null. `genres` i `themes` zawierają obiekty z polem `name`. Nie wyprowadzamy formatu filmu/serialu z liczby odcinków. Parser szczegółów sprawdza zgodność sluga z żądanym tytułem.

## Odcinki

GET `/api/v1/episodes/anime/{animeId}?publicOnly=true`, HTTP 200. `animeId` pochodzi ze szczegółów, a nie ze sluga. Dla „One Punch Man” odpowiedź zawierała 12 odcinków zgodnych z listą strony.

```text
{success,message,code,data:[{
  id,animeId,number,title,malEpisodeId,totalViews,createdAt,updatedAt,
  anime:{id,title,slug},links:[{id,label,order}]
}]}
```

`title` może być null. Potwierdzono `links[].label = "Lektor"`; jest to etykieta grupy linków, nie adres wideo. Istniejący parser odcinków poprawnie odczytuje tę odpowiedź. Linki interfejsu mają postać `/watch/{slug}/{number}`.

## Odtwarzanie — kontrakt potwierdzony ręcznym odczytem

Użytkownik dostarczył odpowiedzi dwóch żądań z HTTP 201:

```text
POST /api/v1/episodes/{episodeId}/playback
żądanie: {linkId:string}
odpowiedź: {success:true,message:string,code:201,data:{ticket:string,expiresIn:60}}

POST /api/v1/episodes/{episodeId}/playback/resolve
żądanie: {ticket:string}
odpowiedź: {success:true,message:string,code:201,data:{iframeUrl:string}}
```

Potwierdzony iframeUrl ma postać `https://playmogo.com/e/{id}`. Nie zapisano wartości przesłanego biletu. CloudStream ma wbudowany ekstraktor `Playmogo : DoodLaExtractor`, zarejestrowany w extractorApis; potwierdzono to w oficjalnych plikach `library/src/commonMain/kotlin/com/lagradost/cloudstream3/extractors/DoodExtractor.kt` i `utils/ExtractorApi.kt` repozytorium recloudstream/cloudstream. V10 przekazuje do niego sam zweryfikowany adres ramki. Bilety i cookies nie są przekazywane hostingowi. Działanie ekstraktora na urządzeniu pozostaje do sprawdzenia.

Poniższe wpisy zachowują historię obserwacji poprzedzających potwierdzenie kontraktu.

Otwarcie strony odcinka uruchomiło normalny mechanizm witryny:

| Trasa API | Zaobserwowany wynik |
| --- | --- |
| `/api/v1/episodes/anime/{animeId}/episode/1?publicOnly=true` | HTTP 200 |
| `/api/v1/episodes/{episodeId}/playback` | HTTP 201; osobne żądanie wstępne 204 |
| `/api/v1/episodes/{episodeId}/playback/resolve` | HTTP 201; osobne żądanie wstępne 204 |
| `/api/v1/episodes/{episodeId}/view` | HTTP 201 |

Kolejna obserwacja 4 listopada 2026, odcinek 1 „Sousou no Frieren 2nd Season”, potwierdziła:

- GET szczegółów odcinka z `publicOnly=true` zwraca HTTP 200.
- POST `playback` ma JSON z jednym polem `linkId` i zwraca HTTP 201.
- POST `playback/resolve` ma JSON z jednym polem `ticket` i zwraca HTTP 201.
- POST `view` nie ma zaobserwowanej treści i zwraca HTTP 201. Nie jest potrzebny do parsowania źródła i nie należy dodawać go jako sztucznego wymogu odtwarzania.
- Po rozstrzygnięciu playback strona umieszcza iframe z adresem `https://playmogo.com/e/{id}`. Wybrana wersja w interfejsie: „Lektor IVO”. Jest to adres osadzonego odtwarzacza, nie potwierdzony bezpośredni strumień wideo.

Nie eksportowano wartości `ticket`, cookies ani nagłówków autoryzacji. Odpowiedzi POST pozostają nieodczytane: przy `Network.getResponseBody` narzędzie ponownie zgłosiło wstrzymaną odpowiedź dokumentu, a ramka była widoczna jako `about:blank`. Nie potwierdzono, czy przyczyną jest oczekiwanie na zgodę dla domeny odtwarzacza. Nie obchodzono tego ograniczenia. Nadal brakuje nazw pól odpowiedzi, sposobu uzyskania i czasu ważności biletu oraz kontraktu źródła wideo w Playmogo. Kod dodatku nie wykonuje nowych żądań do tych tras.

Dalsza weryfikacja w Edge: otwarcie dokładnego adresu ramki w osobnej karcie pokazało blokadę Microsoft Defender SmartScreen „Ta witryna została zgłoszona jako niebezpieczna”, dotyczącą `playmogo.com`. Nie przechodzono przez ostrzeżenie ani nie zmieniano ochrony przeglądarki. Wszystkie trzy wersje tego odcinka — Lektor IVO, Napisy i Lektor AI — wskazywały ramki `/e/{id}` na tej samej domenie. Po sprawdzeniu przywrócono wybór Lektor IVO. Użytkownik nie widział w Codex prośby o zgodę na tę domenę. Sama obserwacja blokady SmartScreen nie ustala przyczyny wewnętrznego komunikatu CDP, ale uniemożliwia dalszą automatyczną analizę tego hostingu w tej przeglądarce.

## Zmiany v8 i walidacja

Dodano parser potwierdzonego katalogu i szczegółów. Test diagnostyczny na urządzeniu uruchamia go wyłącznie dla zaobserwowanych GET `/api/v1/anime/catalog-search` i `/api/v1/anime/slug/{slug}`. Raport dodaje same liczniki `parsed_anime` i `parsed_details`, podobnie jak istniejący `parsed_episodes`. Nie eksportuje tytułów, identyfikatorów, adresów obrazów ani treści odpowiedzi. Błędy HTTP i ucięte odpowiedzi nie są raportowane jako poprawne parsowanie.

Pliki w `AnimeGateProvider/src/test/resources/animegate` są krótkimi, syntetycznymi odpowiednikami zaobserwowanych kopert. Nie są zrzutem sesji przeglądarki. Próby na tych plikach potwierdzają parsery; nie potwierdzają działania klienta Androida ani odtwarzania.

Następne kroki: ustalić pełny wariant i paginację katalogu, odczytać kontrakt odtwarzania przy działającym dostępie diagnostycznym, a następnie podłączyć klienta i `MainAPI` z cookies pobieranymi dla właściwych adresów. Obecny zapis sesji jest ograniczony do dokładnego endpointu testowego i nie może być automatycznie kopiowany do całego API.
