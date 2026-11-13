# KruXx-Dokumentation

Stand: 13.11.2026 · Öffentlich `1.2.7`

[KruXx `1.2.7`](https://github.com/Massefehler/KruXx/releases/tag/v1.2.7) / `1000011`
ist seit dem 13.11.2026 der neueste stabile Release. Ein Video im Videofenster läuft bei
ausgeschaltetem Display oder verlassener App ohne Lücke als Ton weiter; beim Zurückkehren bleibt der
Ton, und das Video folgt ihm ohne eigenen Ton. Dazu kommen die überarbeitete Autoradio-Darstellung,
Android Auto nach den aktuellen Google-Vorgaben und die flüssige Startanimation. 331 Tests,
Release-Lint und CI sind bestanden; die signierte APK ist am Samsung als Update über `1.2.6` geprüft
und der anonyme öffentliche Download bytegleich. Video-Downloads scheitern derzeit an InnerTubeX
`v0.7.4`; ein Update ist als nächster Schritt vorgesehen
([IST-Stand](KRUXX-IST-STAND.md#release-127-vom-13112026)).

Davor: [KruXx `1.2.6`](https://github.com/Massefehler/KruXx/releases/tag/v1.2.6) / `1000010`
ist als stabiler Release veröffentlicht. Er ergänzt die native Autoradio-Darstellung
für direkt installierte Android-Apps:
Fenstererkennung, Auswahl `Automatisch / Handy / Autoradio`, seitliche Navigation, größere
Schrift und Player-Tasten, adaptive Bibliotheksraster und vollständiges Cover vor echtem Blur,
auch unter Android 11. Die erste Wiedergabe nach Frischinstallation und der HTTPS-Fehler bei
Google-Video-Weiterleitungen sind korrigiert. 240 Tests, Release-Lint und CI sind bestanden.
Die signierte APK ist im Android-11-Emulator bei 1280 × 720 und 1024 × 600 einschließlich großer
Schrift geprüft; der anonyme öffentliche Download ist bytegleich mit dem Archiv.
GitHub und App enthalten deutsche und englische Versionshinweise. Prüfstand und Artefakte stehen im
[IST-Stand](KRUXX-IST-STAND.md#lokaler-autoradio-build-126-vom-20102026), die Architektur im
[Entwicklerhandbuch](KRUXX-ENTWICKLERHANDBUCH.md#native-autoradio-oberfläche-seit-126-überarbeitet-am-23102026).
Das physische Autoradio ist nicht angeschlossen; dessen Abnahme bleibt offen.
Nach der Nutzerrückmeldung vom 23.10.2026 war die Autoradio-Darstellung von `1.2.6` so nicht
abgenommen: Bedienelemente waren zu groß, und der Player drängte die Inhalte rechts neben dem
Cover zusammen. `1.2.7` bringt dafür größenabhängige Maße, eine neue Player-Aufteilung und einen
Größenregler; die Automatik schaltet Handys nicht mehr um. Offen ist die Abnahme am Dynavin D9
([IST-Stand](KRUXX-IST-STAND.md#lokale-überarbeitung-der-autoradio-darstellung-vom-23102026--veröffentlicht-mit-127)).
Ebenfalls mit `1.2.7` veröffentlicht sind die Android-Auto-Steuerung nach den aktuellen
Google-Vorgaben (Cover über signierte lokale Adressen, Künstlerzeile, Raster für Alben und
Künstler, Kennzeichen, Empfehlungen, Standardsymbole und genauere Sprachbefehle; im
Android-Automotive-Emulator geprüft, offen ist der Test im echten Android Auto,
[IST-Stand](KRUXX-IST-STAND.md#lokale-android-auto-überarbeitung-vom-23102026--veröffentlicht-mit-127)),
die Startanimation mit selbst gezeichnetem Lichtpunkt
([IST-Stand](KRUXX-IST-STAND.md#lokale-überarbeitung-der-startanimation-vom-06112026--veröffentlicht-mit-127))
und die Hintergrundwiedergabe von Videos mit stummer Rückkehr
([IST-Stand](KRUXX-IST-STAND.md#lokale-hintergrundwiedergabe-von-videos-vom-13112026--veröffentlicht-mit-127)).
Die Startseiten-Diagnose am zweiten Handy ist zurückgestellt.

`1.2.4` brachte zuvor InnerTubeX `v0.7.4` und einen anonymen Wiedergabe-Rückfall für abgelehnte
gespeicherte Sitzungen, ohne die Anmeldung zu löschen. Seine signierte APK ist auf dem Samsung
mit Android 17 geprüft; Nachweise stehen im
[IST-Stand](KRUXX-IST-STAND.md#lokaler-wiedergabe-patch-124-vom-07102026).

[KruXx `1.2.3`](https://github.com/Massefehler/KruXx/releases/tag/v1.2.3) / `1000007`
stellte zuvor die Wiedergabereihenfolge geöffneter Alben wieder her,
trennte Zufallsmodus und Warteschlangen-Nachschub sauber und gab der Aktionsleiste des
Vollbild-Players einen sichtbaren Zufallszustand samt vollen Trefferflächen. Seine 217 Tests,
Release-Lint und CI sind bestanden; die damalige signierte APK ist auf dem Samsung-Zielgerät mit
Android 16 als Update über 1.2.2 geprüft und der öffentliche Download bytegleich mit dem lokalen Archiv.
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
| [`KRUXX-IST-STAND.md`](KRUXX-IST-STAND.md) | Release-, Prüf- und Nachteststatus von KruXx, einschließlich der Veröffentlichung von 1.2.7 |
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
als separates späteres Vorhaben vorgemerkt und ist bis einschließlich `1.2.6` in keinem Release
enthalten.
Die [Roadmap](../ROADMAP.md) bündelt künftige Vorhaben. Produktstatus und detaillierte Nachtests
stehen in `KRUXX-IST-STAND.md` §4 und im Entwicklerhandbuch §9; das Designkonzept konkretisiert
Implementierungsstand, Abgrenzung und Abnahmekriterien. Historische Release Notes werden dafür
nicht nachträglich geändert.

Der veröffentlichte Release `1.2.0` ergänzt Downloads/MP3/Dateikopien sowie weitere Glass-Korrekturen
an Menüs, Untermenüs, Dialogen, Tracklisten, Statistiken und horizontalen Filterleisten.
Umfang und konkrete Nachweise stehen im IST-Stand sowie in `DOWNLOADS.md` und `Design.md`.
`1.2.2` schließt die verbliebenen Lücken dieser Glasschicht bei Alben-, Künstler-, Playlist- und
Titellisten und nimmt die Suchnavigation aus `1.2.1` zurück; Details stehen in `Design.md`.
`1.2.3` ist ein reiner Wiedergabe-Patch ohne Designänderung: Albumreihenfolge, Zufallsmodus und
die Trefferflächen der Player-Aktionsleiste; Details stehen im IST-Stand und im Entwicklerhandbuch.

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
