# KruXx-Dokumentation

Stand: 11.09.2026 · KruXx `1.2.2` veröffentlicht

[KruXx `1.2.2`](https://github.com/Massefehler/KruXx/releases/tag/v1.2.2) / `1000006`
ist veröffentlicht. Der Release vervollständigt die Glasflächen von Kachel- und Titellisten und
gibt der Hauptleiste in der Suche wieder ihre eigenen Kategorien. 204 Tests, Release-Lint und CI
sind bestanden; die signierte APK ist auf dem Samsung-Zielgerät mit Android 16 als Update über
1.2.1 geprüft und der öffentliche Download bytegleich mit dem lokalen Archiv.
Der verbindliche Artefakt-, Prüf- und Nachteststatus steht in `KRUXX-IST-STAND.md`.
Offene Gerätefälle bleiben ausdrücklich dokumentiert.
Ab `1.2.0` setzt KruXx Android 7.0 / API 24 voraus. Android 6 wird nicht mehr unterstützt;
der letzte dafür veröffentlichte Release bleibt `1.1.0`. Die historischen API-23-Prüfungen bleiben
nachvollziehbar dokumentiert, gehören aber nicht mehr zur aktuellen Abnahmematrix.

Diese Übersicht legt fest, welche Dokumente den aktuellen KruXx-Stand beschreiben und welche Dateien
bewusst nur historische, geerbte oder buildtechnische Informationen enthalten.

## Verbindliche KruXx-Dokumente

| Dokument | Zweck |
|---|---|
| [`../ROADMAP.md`](../ROADMAP.md) | Künftige Vorhaben, einschließlich Musikteilen und optionaler Friends-Funktion; ohne feste Release-Termine |
| [`KRUXX-IST-STAND.md`](KRUXX-IST-STAND.md) | Release-, Prüf- und Nachteststatus von KruXx, einschließlich der Veröffentlichung von 1.2.2 |
| [`KRUXX-ENTWICKLERHANDBUCH.md`](KRUXX-ENTWICKLERHANDBUCH.md) | Architektur, Wartung, Fehlersuche, Build-, Test- und Release-Ablauf |
| [`DOWNLOADS.md`](DOWNLOADS.md) | Download-/MP3-/Dateikopier-Funktionen, Architektur und verbleibende Geräteprüfungen |
| [`Design.md`](Design.md) | Konzept, Umsetzungsstatus und Abnahmekriterien des „KruXx Glass“-Redesigns |
| [`../README.md`](../README.md) | Öffentliche Projektübersicht, Installation, Kernfunktionen und Einstieg in den Build |
| [`../NOTICE.md`](../NOTICE.md) | Herkunft, Lizenzen, Abgrenzung und Danksagungen |
| [`changelogs/kruxx/`](changelogs/kruxx/) | Unveränderliche Versionshistorie und die Release Notes der jeweils gebauten KruXx-Version |

Bei einem Widerspruch gilt der Quellcode als technische Wahrheit. Der aktuelle Soll-/Prüfstand wird
anschließend zuerst in `KRUXX-IST-STAND.md` und dann in den übrigen KruXx-Dokumenten berichtigt.

Die rein visuelle Modernisierung [„KruXx Glass“](Design.md) bei unveränderter App-Architektur ist
als öffentlicher Release `1.1.0` „Glass Update“ veröffentlicht. Zwei Umsetzungsstufen umfassen
Grundlayout und Startseite, Vollbild-Player, Einstellungen, gemeinsame Menüs/Dialoge und geöffnete
Rubriken sowie Queue-Sheet, Werkzeugleisten, Suche, Filter und Inhaltskarten aller unteren
Bibliotheks-Reiter. Der Stand ist auf dem Samsung-Zielgerät geprüft; weiterführende Geräte- und
Darstellungsmatrizen bleiben im Statusdokument transparent als Nachtests festgehalten. Der
eigenständige Podcast-Bereich mit vollständiger Podcast-Funktion bleibt
als separates späteres Vorhaben vorgemerkt und ist auch kein Bestandteil von `1.2.2`.
Die [Roadmap](../ROADMAP.md) bündelt künftige Vorhaben. Produktstatus und detaillierte Nachtests
stehen in `KRUXX-IST-STAND.md` §4 und im Entwicklerhandbuch §9; das Designkonzept konkretisiert
Implementierungsstand, Abgrenzung und Abnahmekriterien. Historische Release Notes werden dafür
nicht nachträglich geändert.

Der veröffentlichte Release `1.2.0` ergänzt Downloads/MP3/Dateikopien sowie weitere Glass-Korrekturen
an Menüs, Untermenüs, Dialogen, Tracklisten, Statistiken und horizontalen Filterleisten.
Umfang und konkrete Nachweise stehen im IST-Stand sowie in `DOWNLOADS.md` und `Design.md`.
`1.2.2` schließt die verbliebenen Lücken dieser Glasschicht bei Alben-, Künstler-, Playlist- und
Titellisten und nimmt die Suchnavigation aus `1.2.1` zurück; Details stehen in `Design.md`.

## Bewusst nicht als aktueller KruXx-Stand behandeln

- Frühere Dateien unter `docs/changelogs/kruxx/` dokumentieren genau den damaligen Release-Umfang und
  werden nicht nachträglich an neue Funktionen angepasst.
- `composeApp/src/androidKruxx/res/raw/release_notes.txt` ist die in die KruXx-Release-APK eingebettete Kopie
  von `docs/changelogs/kruxx/<versionName>.txt`. Beide müssen beim Build bytegleich sein.
- `composeApp/src/androidMain/res/raw/release_notes.txt`,
  `composeApp/src/androidDebug/res/raw/release_notes.txt` und die Dateien unter
  `fastlane/metadata/android/` gehören zu geerbten Kreate-Buildvarianten beziehungsweise deren
  Store-Metadaten. Sie sind keine KruXx-Release Notes und bleiben unverändert. Die Debug-Ressource
  überlagert auch im KruXx-Debug-Build die Flavor-Kopie; die eingebetteten Versionshinweise deshalb
  an der Release-APK prüfen.
- Dokumentation in Submodulen und übernommenen Komponenten beschreibt die jeweilige Fremdkomponente.
  KruXx-spezifische Abweichungen werden im Entwicklerhandbuch festgehalten, ohne fremde Historie
  umzuschreiben.
- Generierte Buildberichte, Cache-Dateien und Dateien unter `build/` sind keine Projektdokumentation.
- `docs/crash-report.txt` ist ein möglicherweise personenbezogener lokaler Diagnoseexport, kein
  Dokumentationsbestandteil. Er darf weder gelesen noch committet oder veröffentlicht werden.

## Pflege bei Änderungen

1. Funktions- und Prüfstatus in `KRUXX-IST-STAND.md` aktualisieren.
2. Architektur, Fehlerbilder, Tests und Release-Ablauf im Entwicklerhandbuch nachziehen.
3. Öffentliche Änderungen knapp in der Root-README zusammenfassen.
4. Release Notes für die nächste unveröffentlichte Version unter
   `docs/changelogs/kruxx/<versionName>.txt` ergänzen und die eingebettete Kopie aktualisieren.
   Bereits veröffentlichte Versionsdateien bleiben unverändert.
5. Interne Links, Markdown-Format und die Bytegleichheit der beiden Release-Note-Dateien prüfen.
6. Erst nach vollständigem Gerätetest, reproduzierbarem Commit und passendem Tag veröffentlichen.
