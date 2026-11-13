# KruXx – Roadmap

Stand: 13.11.2026 · nach Veröffentlichung von `1.2.6`

Diese Roadmap hält künftige Vorhaben fest. Versionsnummern und Termine sind noch offen.
Der aktuelle Funktions-, Prüf- und Release-Status steht im
[IST-Stand](docs/KRUXX-IST-STAND.md); technische Details und Nachtests stehen im
[Entwicklerhandbuch, §9](docs/KRUXX-ENTWICKLERHANDBUCH.md#9-offene-punkte-ideen-und-release-nachweise).

## Musik teilen und optionale Friends-Funktion

**Status:** Am 08.09.2026 vorgemerkt; noch nicht umgesetzt. Zuerst Musikteilen verbessern und
die offenen Stabilitätsprüfungen angehen. Der integrierte Bereich „Freunde“ ist ein größeres
Vorhaben für ein späteres Feature-Release.

Ziel ist der persönliche Austausch über Musik: einen Track entdecken, mit einer kurzen
Nachricht empfehlen und beim Empfänger direkt in KruXx abspielen oder einer Playlist hinzufügen.

### Stufe 1: Musik teilen verbessern

- Tracks und Playlists mit Künstler, Titel und Link über das Android-Teilen-Menü verschicken.
- Empfangene Musiklinks bequem in KruXx öffnen.

### Stufe 2: Optionaler Bereich „Freunde“

- Kontakte per Einladungslink oder QR-Code hinzufügen.
- Kurze persönliche Nachrichten sowie Tracks und Playlists als Musikkarten mit Cover und
  passenden Künstler-/Titelangaben austauschen.
- Geteilte Musik direkt abspielen, in die Warteschlange übernehmen oder einer Playlist hinzufügen.

### Vor einer Messenger-Integration

Ein begrenzter technischer Prototyp soll die Einbindung sowie Hintergrundzustellung,
Benachrichtigungen, Akkuverbrauch, Chat-Sicherung und Wartung bei Updates untersuchen.
Erst danach erfolgt die Entscheidung über die technische Grundlage und den konkreten Umfang.

[SimpleX](https://github.com/simplex-chat/simplex-chat#for-developers) ist ein möglicher Kandidat.
Die Bibliothek ist für die Einbindung in mobile Apps vorgesehen; der eigene Android-Client
verwendet einen nativen Haskell-Kern
([Architektur](https://github.com/simplex-chat/simplex-chat/blob/stable/apps/multiplatform/README.md#architecture)).
Die Eignung für KruXx bleibt zu prüfen; eine Festlegung auf SimpleX besteht noch nicht.

## Weitere bereits vorgemerkte Vorhaben

Die Reihenfolge dieser Übersicht legt keine Release-Reihenfolge fest. Der jeweilige Umfang
und offene Entscheidungen sind im Entwicklerhandbuch, §9, beschrieben.

| Vorhaben | Gespeicherter Umfang |
|---|---|
| Autoradio-Darstellung überarbeiten | Nach Nutzerrückmeldung vom 23.10.2026 waren die Bedienelemente von `1.2.6` zu groß und der Player rechts neben dem Cover zu gedrängt. Lokal umgesetzt: größenabhängige Maße, neue Player-Aufteilung, Größenregler und eine Erkennung, die Handys auslässt. Offen sind die Abnahme am Dynavin D9 und die Veröffentlichung; Details im [IST-Stand](docs/KRUXX-IST-STAND.md#lokale-überarbeitung-der-autoradio-darstellung-vom-23102026--unveröffentlicht) |
| Android Auto auf aktuellem Standard | Lokal umgesetzt am 23.10.2026: Cover über signierte lokale Adressen, Künstlerzeile, Raster- und Listendarstellung, Kennzeichen, Empfehlungen, Standardsymbole und genauere Sprachbefehle. Offen sind der Test im echten Android Auto, die Veröffentlichung und danach das Update auf Media3 `1.11.1`; Details im [IST-Stand](docs/KRUXX-IST-STAND.md#lokale-android-auto-überarbeitung-vom-23102026--unveröffentlicht) |
| Flüssige Startanimation | Lokal umgesetzt am 06.11.2026: ruhendes Motiv, über das die App den Lichtpunkt bei jeder Bildwiederholung gleichmäßig von links nach rechts zeichnet. Auf dem Samsung angenommen; Bestandteil des nächsten Releases. Zurückgestellt: eine Render-Thread-Animation gegen die kurzen Hänger, während die Startseite darunter lädt; Details im [IST-Stand](docs/KRUXX-IST-STAND.md#lokale-überarbeitung-der-startanimation-vom-06112026--unveröffentlicht) |
| Videos im Hintergrund weiterhören | Lokal umgesetzt am 13.11.2026: Ein Video im Videofenster läuft bei ausgeschaltetem Display oder verlassener App ohne Lücke als Audio weiter. Beim Zurückkehren bleibt der Ton, und das Video folgt ihm ohne eigenen Ton; seine Bedienelemente steuern den Ton. Am Samsung vom Nutzer abgenommen, Bestandteil von `1.2.7`. Offen: ein langer Lauf mit ausgeschaltetem Display und die Pause von außen bei selbst klingendem Video; Details im [IST-Stand](docs/KRUXX-IST-STAND.md#lokale-hintergrundwiedergabe-von-videos-vom-13112026--unveröffentlicht) |
| Eigenständiger Podcast-Bereich | Abonnements, Episodenstatus, Fortsetzen und optional automatische Downloads/Aufräumen; vor Umsetzung YTM-only oder zusätzlich RSS/PodcastIndex und OPML festlegen |
| Monochromes Icon | Eigenes Motiv für Themed Icons und Benachrichtigungen |
| InnerTubeX-Update | Von `v0.7.4` auf aktuell `v0.11.7`: Abhängigkeiten (Ktor 3.6.0 / OkHttp 5.5.0) auflösen, API anpassen, Wiedergabe und Downloads am Samsung prüfen; Anlass sind seit Herbst 2026 scheiternde Video-Downloads. Details im [Entwicklerhandbuch, §9](docs/KRUXX-ENTWICKLERHANDBUCH.md#9-offene-punkte-ideen-und-release-nachweise) |
| Toolchain-Wartung | Vorgemerkten Wechsel auf AGP 9 / Kotlin 2.4.10 prüfen und bestehende Kompatibilitätsausnahmen danach neu bewerten |
| Upstream und Streaming | Änderungen an der Backend-Struktur beobachten; zusätzliche Streaming-Clients und Chunking nur bei Bedarf prüfen |
| Glass-Nacharbeiten | Weitere Darstellungs- und Performanceprüfungen sowie mögliche zusätzliche ruhende Glasflächen; Details im [Designkonzept](docs/Design.md) |
| Verbleibende Geräteprüfungen | Physisches Autoradio, Android Auto, physische SD-/USB-Speicher, Download-Abbrüche, Wiedergabe, Playlist-Randfälle und Updater sowie die zurückgestellte leere Startseite am zweiten Testhandy; Details in IST-Stand, Entwicklerhandbuch und [Download-Matrix](docs/DOWNLOADS.md) |
