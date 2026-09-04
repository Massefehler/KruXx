# KruXx Glass – Designkonzept

Stand: 04.09.2026 · zwei Umsetzungsstufen implementiert · veröffentlicht als KruXx `1.1.0`
„Glass Update“

## Ziel und Abgrenzung

„KruXx Glass“ soll KruXx moderner und auf den ersten Blick klar von Kreate unterscheidbar machen.
Das Vorhaben ist ein ausschließlich visuelles Skin. Folgende Bereiche bleiben in Architektur und
Verhalten unverändert:

- Navigation und Screen-Zuschnitt
- Datenfluss und ViewModels
- Wiedergabe- und Bibliothekslogik
- Suche, Downloads und Updatefunktion
- Android-Auto-Schnittstelle und MediaLibrary-Verhalten

Eine Designänderung darf daher keine neue fachliche Logik in UI-Komponenten verlagern und keine
bestehenden Bedienabläufe verändern. Der umgesetzte Umfang wurde nach bestandenem Release-Gate als
KruXx `1.1.0` „Glass Update“ veröffentlicht.

## Visuelle Richtung

- Tiefes Graphit als Hintergrund, ergänzt durch sehr dezente rot-blaue Lichtverläufe.
- Halbtransparente Glasflächen mit feiner Kontur und weichem Schatten.
- Echter Blur auf unterstützten Geräten und ein optisch gleichwertiger transparenter Fallback auf
  älteren Android-Versionen.
- Glas gezielt für Header, Navigation, Mini-Player, Player-Steuerung, Menüs und Dialoge.
- Leichte transparente Karten in Listen, aber kein kostspieliger Blur pro Listenelement.
- Blau für Navigation und Interaktion, Rot gezielt für Wiedergabe und Markenakzente.
- Modernere Rundungen und kurze, ruhige Animationen.

Die Farben leiten sich aus dem vorhandenen KruXx-Icon ab: Schwarz beziehungsweise Graphit bildet die
Basis, das rote „K“ den Markenakzent und die blaue Wellenform die primäre Interaktionsfarbe.

## Technische Leitplanken

Technisch bleibt die Änderung eine zusätzliche Designschicht über der bestehenden Compose-UI:

```text
Player / Daten / Navigation / ViewModels
                    │
                    └── bestehende Compose-Oberfläche
                                  │
                                  └── neue KruXx-Designschicht
                                      Farben · Glas · Formen · Animation
```

Die vorhandenen Bausteine `Appearance`, `ColorPalette`, Header, Navigation, Mini-Player und Player
sind bereits ausreichend zentral aufgebaut. Auch der vorhandene Blur-Code des Players kann
wiederverwendet werden. Neue Glasflächen sollen als gemeinsame Compose-Komponenten beziehungsweise
Modifier entstehen, damit Transparenz, Kontur, Form und Fallback nicht pro Screen dupliziert werden.

KruXx unterstützt weiterhin API 23 / Android 6. Echter Backdrop-Blur wird deshalb nur eingesetzt,
wenn Plattform und Gerät ihn zuverlässig unterstützen. Android 6 bis 11 erhalten einen
performanten Fallback aus Transparenz, Farbverlauf, Kontur und Schatten. Rechenintensiver Blur bleibt
auf wenige ruhende Oberflächen begrenzt; scrollende Listen dürfen dadurch nicht ruckeln.

Bestehende Einstellungen für Hell-/Dunkelmodus, Farben, Schrift und Rundungen müssen weiter
funktionieren. Lesbarkeit, ausreichender Kontrast, große Systemschrift, Touch-Ziele und reduzierte
Animationen sind Teil der Designabnahme.

## Umsetzungsstufen

Die erste Etappe umfasst:

1. Zentrale KruXx-Glass-Komponenten und Markenfarben.
2. Atmosphärischer App-Hintergrund.
3. Header und untere Navigation.
4. Mini-Player.
5. Startseite und ihre Karten.

Die zweite Etappe überträgt die gemeinsame Designsprache auf:

1. Vollbild-Player einschließlich Steuerungs- und Aktionsflächen.
2. Einstellungen, Unterseiten und klebende Abschnittsüberschriften.
3. Dialoge, Auswahlmenüs und Bottom-Sheets.
4. Geöffnete Rubriken und Detailseiten über die gemeinsamen Screen-Gerüste.
5. Gezielte Layoutkorrekturen an zu breiten Vorschlagskarten und sich überlagernden
   Interpretenkacheln.

Beide Etappen bilden den visuellen Umfang des öffentlichen Releases `1.1.0`.

## Aktueller Implementierungsstand

Die beiden Umsetzungsstufen sind im öffentlichen Release `1.1.0` umgesetzt. Die neue
Designschicht wird
nur für den unabhängigen KruXx-Build aktiviert; der gemeinsame Kreate-/GitHub-Build behält seine
bisherige Darstellung.

Bereits umgesetzt sind:

- zentrale Markenfarben, Formen sowie wiederverwendbare Modifier für App-Hintergrund, Glasflächen
  und leichte Karten;
- ein gemeinsamer Graphit-Hintergrund mit statischer rot-blauer Lichtaura;
- die Kaltstartanimation auf demselben App-Hintergrund; zehn echt freigestellte RGBA-Quellen und
  transparente Runtime-WebPs, durchgehende 120-ms-Überblendungen ab dem zweiten Frame sowie eine ruhigere
  Skalierungs-/Leuchtbewegung betten das Logo ohne schwarze Außenflächen ein;
- halbtransparenter Header und schwebende untere Navigation mit blau markiertem aktivem Ziel;
- ein kompakter gläserner Mini-Player mit blauem Fortschritt und rotem Play-/Pause-Akzent;
- transparente Inhaltsflächen sowie leichte Glaskarten in der Startansicht;
- eine gemeinsame gläserne Werkzeugleiste, Suche und transparente Filterchips in den über die
  untere Navigation erreichbaren Reitern „Titel“, „Interpreten“, „Alben“ und
  „Playlists“ sowie leichte Glaskarten für deren Inhalte einschließlich Gerätetitel;
- die platzsparende deutsche Hauptreiter-Beschriftung „Playlists“, damit alle vorhandenen Reiter
  vollständig sichtbar bleiben und für den geplanten Podcast-Reiter mehr Breitenreserve entsteht;
- die vorhandene echte Cover-Unschärfe im Vollbild-Player mit einer gemeinsamen gläsernen
  Steuerungsfläche und gläserner Aktionsleiste; eine eigene undurchsichtige Unterlage des
  Vollbild-Sheets verhindert dabei, dass Header und Bedienelemente der Startseite durchscheinen;
- die verbindliche Basisdarstellung des Vollbild-Players bei einer Frischinstallation: Das
  vollständige Cover bleibt als eigene Vordergrundfläche sichtbar, während eine davon abgeleitete,
  unscharfe Coverdarstellung den Hintergrund füllt. Ein Update bewahrt eine bereits gewählte
  Coverdarstellung; Stable- und Debug-Paket speichern diese Auswahl getrennt;
- das Drei-Punkte-Trackmenü des Vollbild-Players mit einer nur dort verwendeten, sehr schwachen
  zusätzlichen Basis (Alpha 0,08) unter dem Glasverlauf; Listen-, Raster- und Playlist-Unteransicht
  decken das Cover damit einen Hauch stärker, ohne andere Menüs oder den Glascharakter zu verändern;
- ein optisch durchgängiges Queue-/Playlist-Sheet des Vollbild-Players: Titelliste, Suchbereich und
  Fußleiste verdecken die gemeinsame Bottom-Sheet-Fläche im KruXx-Build nicht mehr; eine deckende
  Basis unter der Glasgestaltung verhindert zugleich, dass der Vollbild-Player hindurchscheint;
- leichte Glaskarten und lesbare klebende Überschriften in den Einstellungen und ihren Unterseiten;
  die gemeinsamen Abschnittsköpfe sind an allen vier Ecken mit 14 dp gerundet, ihre ein- oder
  zweizeiligen Inhalte vertikal zentriert und mit 16 dp Innenabstand auf die Textkante der
  Einstellungseinträge ausgerichtet;
- gemeinsame Glasflächen für die zentralen Dialog-, Dropdown-, Menü- und Bottom-Sheet-Systeme;
- transparente gemeinsame Screen-Gerüste und angepasste Detailkarten für geöffnete Rubriken,
  Suche, Bibliothek, Wiedergabelisten, Alben, Interpreten, Verlauf, Statistik und Podcasts;
- ein kompakter Downloadbutton in den Vorschlägen, der nicht länger die gesamte Titelzeile belegt;
- auf rund 65 Prozent der Hochformatbreite vereinheitlichte Karten für „Vorschläge“ und
  „Top Artists“, passend zur vorhandenen Größe von „Forgotten favorites“;
- eine aus dem tatsächlich dargestellten Song-Thumbnail berechnete zweizeilige Höhe samt festem
  Abstand für „Top Artists“, damit sich die Zeilen nicht mehr überlagern;
- ein performanter Transparenz-/Verlauf-/Kontur-Fallback ohne Blur pro Listenelement.

Der als `1.1.0`/`1000003` veröffentlichte KruXx-Stand besteht 88 App- und 58 Innertube-Tests ohne
Fehler oder übersprungene Tests; das Metrolist-Modul besitzt keine eigene Testsuite (`NO-SOURCE`).
Der vollständige Release-Lint meldet keine neuen Befunde. Debug- und Release-Variante bauen, ihre
APK-Metadaten und ZIP-Integrität sind geprüft, und die englischen Release Notes liegen bytegleich in
Changelog, generierter Ressource und Release-APK. Das signierte Archiv entstand aus dem sauberen
Release-Commit `39398cc4684c91a7737da36af85f34232606f52d`; Paket, Version, KruXx-Zertifikat,
Signaturschemata v1/v2/v3 und eingebettete Quellrevision sind verifiziert.

Auf dem
Samsung-Testgerät SM-S931B mit Android 16 wurden Installation, Start und Tabwechsel geprüft; in den
beiden Playlist-Reitern waren jeweils 11 lokale beziehungsweise YT-/YTM-Listen sichtbar. Eine
Online-Wiedergabe ließ sich über Mini- und Vollbild-Player pausieren und fortsetzen, der
Media-Session-Status wechselte dabei fehlerfrei zwischen `PLAYING` und `PAUSED`. Vollbild-Player,
Einstellungen, Einstellungs-Unterseite, Auswahldialog, Kopfzeilen-Dropdown, geöffnete Albumseite und
lang gedrücktes Titelmenü wurden außerdem visuell beziehungsweise auf Bedienbarkeit geprüft. Der
kompakte Downloadbutton der Vorschläge ist auf dem Gerät bestätigt. In einem späteren Nachtest
waren auch die zuvor zu großen Vorschlagskarten, die überhöhten und überlappenden
„Top Artists“-Zeilen sowie das Durchscheinen des Startseiten-Headers hinter dem Vollbild-Player auf
dem Gerät reproduzierbar. Die zugehörigen Korrekturen kompilieren und sind automatisiert geprüft;
„Top Artists“ und die Abgrenzung des Vollbild-Players wurden mit der neu gebauten APK direkt
nachgeprüft. Im abschließenden Sichttest wurde auch die korrigierte Breite der Vorschlagskarten
bestätigt. Die anschließend ergänzte Queue-Fläche einschließlich Suche, Mini-Player und
Werkzeugleiste sowie die vier unteren Bibliotheks-Reiter wurden auf dem Samsung SM-S931B ebenfalls
direkt geprüft. Alle vier Reiter öffneten sich mit ihrer KruXx-Glasdarstellung; elf YT-/YTM-Playlists
waren vorhanden, ein Titel ließ sich daraus abspielen und die Queue ließ sich öffnen, durchsuchen
und wieder schließen. Nach der ergänzten deckenden Sheet-Basis war der darunterliegende
Vollbild-Player dabei nicht mehr sichtbar. Es trat weder ein Absturz noch ein ANR auf. Der im Ruhezustand
durchscheinende Wischaktions-Layer des Mini-Players wurde während der ersten Geräteprüfung erkannt
und korrigiert.

Die anschließend leicht erhöhte Deckung des Drei-Punkte-Trackmenüs wurde nach wiederhergestellter
ADB-Verbindung mit der neu installierten KruXx-Debug-APK auf dem Samsung-Gerät geprüft und in der
gemeinsamen Sichtabnahme bestätigt.

Nach der Veröffentlichung wurde auf derselben Hardware in der stabilen App ein fehlendes separates
Cover untersucht. Ursache war kein Unterschied des Release-Artefakts, sondern die dort bereits
gespeicherte, auch per Doppeltipp auf den Player-Hintergrund umschaltbare Coveroption. Nach dem
Wiedereinschalten blieb das vollständige Cover auch nach Verkleinern und erneutem Öffnen des Players
sichtbar. Der Quellstand von `1.1.0` setzt für neue App-Daten weiterhin vollständiges Cover,
coverbasierten unscharfen Hintergrund und aktive Hintergrundunschärfe als Standard. Bestehende
App-Daten werden bei einem Update absichtlich nicht auf diese Werte zurückgesetzt.

Die anschließend zentral korrigierten Einstellungs-Abschnittsköpfe wurden mit einer neu installierten
Debug-APK in den Reitern „Allgemein“, „UI“, „Darstellung“, „Daten“ und „Sonstiges“ geprüft.
Vollständig gerundete ein- und zweizeilige Köpfe, Textausrichtung und der angeheftete Zustand beim
Scrollen waren auf dem Samsung SM-S931B korrekt; der Prozess blieb aktiv und der neue Crash-/ANR-Filter
leer.

Die Hintergrundintegration der Startanimation wurde mit derselben Debug-APK bei echten Kaltstarts
auf dem Samsung SM-S931B geprüft. Die am 04.09.2026 ersetzte, echt freigestellte Frame-Serie und das
neue App-Icon sind in Quelle und Runtime auf Alpha, Abmessungen und Reihenfolge geprüft; die APK
baut erfolgreich und enthält alle zehn transparenten WebPs. Nach wiederhergestellter ADB-Verbindung
wurde der aktuelle visuelle Gesamtstand auf dem Zielgerät erneut geöffnet und bestätigt.

Der abschließend versionierte `1.1.0`-Debug-Build wurde per ADB mit Datenerhalt installiert. Android
bestätigte einen Kaltstart, Paketversion `1.1.0`/`1000003` und einen laufenden App-Prozess; im auf
diesen neuen Prozess begrenzten Startlog trat kein Fatal- oder ANR-Eintrag auf.

Das exakt archivierte signierte Release-APK wurde anschließend als Update über die stabile
Installation `1.0.2` installiert. Android behielt deren ursprünglichen Installationszeitpunkt bei,
meldete `1.1.0`/`1000003` und bestätigte einen Kaltstart in 489 ms ohne Fatal-/ANR-Treffer. Der
öffentliche GitHub-Asset ist bytegleich mit diesem Archiv: 22.883.301 Bytes und
SHA-256 `8c1f57bf4bb2f4e41b12fc0ceb024594a1784ef0843ef8b6831ccbdbd13aa741`.

Ein kurzer, nach einem Statistik-Reset gemessener Startseiten-Scrolltest mit mehreren Auf- und
Abwärtsbewegungen ergab 675 gerenderte Frames, davon 5 vom System als ruckelig bewertet (0,74 %),
ein 95. Perzentil von 14 ms und keine verpassten VSync-Ereignisse. Das ist ein positiver Erstnachweis
auf diesem Gerät, ersetzt aber keine breitere Performance- und Geräteprüfung.

Die gemeinsame Sichtabnahme auf dem Samsung-Zielgerät ist erfolgt. Als erweiterte Nachtests bleiben
Hellmodus, große Systemschrift, API 23 bis 30 und eine breitere Geräte-/Performanceprüfung offen.
Der veröffentlichte Stand verwendet außerhalb der vorhandenen echten
Cover-Unschärfe des Vollbild-Players deshalb auf allen Versionen den sicheren Glas-Fallback aus
Transparenz, Verlauf, Kontur und Schatten. Echter Backdrop-Blur auf weiteren geeigneten ruhenden
Oberflächen bleibt eine spätere, gesondert zu prüfende Verfeinerung.

## Bedingte Erweiterung der Hauptnavigation

Die untere Hauptnavigation bleibt horizontal scrollbar. Solange alle Reiter vollständig in die
verfügbare Breite passen, werden keine zusätzlichen Richtungshinweise angezeigt. Erst bei einem
tatsächlich gemessenen horizontalen Überlauf kennzeichnen seitliche Doppel-Chevrons die noch
erreichbaren Inhalte:

- `<<` erscheint nur, wenn links weitere Reiter verborgen sind;
- `>>` erscheint nur, wenn rechts weitere Reiter verborgen sind;
- zwischen beiden Scrollgrenzen werden beide Hinweise angezeigt;
- an einer erreichten Scrollgrenze verschwindet der Hinweis dieser Seite;
- wenn alle Reiter sichtbar sind, erscheinen keine Hinweise.

Die Entscheidung richtet sich nach der verfügbaren Breite sowie dem tatsächlichen Scrollzustand,
nicht nach einer fest angenommenen Reiterzahl. Die Hinweise dürfen keine Reiter überdecken und
müssen bei einer späteren Umsetzung mit zugänglichen Inhaltsbeschreibungen versehen werden. Zu
prüfen sind dann insbesondere kleine Displays, große Systemschrift und der geplante Podcast-Reiter.
Diese Erweiterung ist vorgemerkt und derzeit bewusst noch nicht aktiv, weil alle vorhandenen Reiter
vollständig in die Leiste passen.

## Abnahmekriterien

- Keine funktionale Änderung oder Regression in Navigation, Wiedergabe und Datenanzeige.
- Flüssiges Scrollen und stabile Animationen auf dem Zielgerät.
- Konsistenter Fallback ab Android 6 und echter Blur nur auf unterstützten Geräten.
- Lesbare Darstellung in Hell- und Dunkelmodus sowie mit großer Systemschrift.
- Bestehende Appearance-Einstellungen bleiben bedienbar und führen nicht zu unlesbaren Flächen.
- Eine Frischinstallation zeigt im Vollbild-Player das vollständige Cover vor dem unscharfen
  Coverhintergrund; ein Update darf eine bewusst abweichende Nutzereinstellung nicht überschreiben.
- Gemeinsame Designbausteine statt voneinander abweichender Einzellösungen pro Screen.
