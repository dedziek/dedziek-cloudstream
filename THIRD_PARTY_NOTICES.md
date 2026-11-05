# Źródła i zależności

Obsługa publicznego odtwarzacza Byse w EkinoProvider v11 adaptuje obliczenia inicjalizacyjne i format odpowiedzi z [MoonGetter](https://github.com/darkryh/MoonGetter/tree/master/server-src/moongetter-filemoon), MIT. Informacja licencyjna jest również zachowana jako stała `BYSE_LICENSE` w pliku DEX dodatku:

MIT License

Copyright (c) 2024 Xavier Alexander Torres Calderón

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


Kod dodatku jest dostarczony wraz ze źródłami i testami. Projekt nie jest oficjalnym produktem Filmana ani CloudStream.

- [CloudStream](https://github.com/recloudstream/cloudstream): API hosta, GPL-3.0. API jest zależnością kompilacyjną i nie jest pakowane jako kopia aplikacji w pliku `.cs3`.
- [CloudStream TestPlugins](https://github.com/recloudstream/TestPlugins) i [extensions](https://github.com/recloudstream/extensions): wzorzec konfiguracji kompilacji i rejestracji providera.
- [CloudStream Gradle](https://github.com/recloudstream/gradle): narzędzie kompilacji, LGPL-3.0.
- Gradle Wrapper: Apache-2.0, [licencja Gradle](https://github.com/gradle/gradle/blob/master/LICENSE).
- Jsoup: MIT, [repozytorium](https://github.com/jhy/jsoup).
- NiceHttp: LGPL-3.0, [repozytorium](https://github.com/Blatzar/NiceHttp).
- Jackson: Apache-2.0, [repozytorium](https://github.com/FasterXML/jackson-module-kotlin).
- Kotlin: Apache-2.0, [repozytorium](https://github.com/JetBrains/kotlin).
- JUnit4: EPL-1.0, wyłącznie testy, [repozytorium](https://github.com/junit-team/junit4).

Aktualny sposób odczytu zwykłych źródeł porównano z publicznym HTML i JavaScriptem Filmana z 3 listopada 2026 oraz ze scraperem Filman w [FanFilm 2026.10.03.1](https://github.com/fanfilm-pl/repository.fanfilm). Nie wykorzystano poświadczeń z cudzych dodatków. Zewnętrzny scraper nie jest częścią pliku `.cs3`.

W zakresie adaptacji mechanizmu odczytu linków zachowano poniższą informację licencyjną źródła FanFilm:

Copyright (C) 2026 :)

MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
