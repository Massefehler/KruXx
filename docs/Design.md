# KruXx Glass – Designkonzept

Stand: 08.09.2026 · zwei Umsetzungsstufen veröffentlicht als KruXx `1.1.0`
„Glass Update“ · weitere Menü-, Listen- und Statistikkorrekturen veröffentlicht mit
[KruXx `1.2.0` „Downloads Update“](https://github.com/Massefehler/KruXx/releases/tag/v1.2.0).
Die Leisten- und Header-Korrekturen vom 08.09.2026 sind für `1.2.1` vorbereitet.
Die vollständige Darstellungsmatrix bleibt als Nachtest dokumentiert.

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

KruXx setzt ab `1.2.0` API 24 / Android 7 voraus; `1.1.0` bleibt der letzte veröffentlichte
Android-6-kompatible Release. Echter Backdrop-Blur wird nur eingesetzt,
wenn Plattform und Gerät ihn zuverlässig unterstützen. Android 7 bis 11 erhalten einen
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

Die beiden grundlegenden Umsetzungsstufen sind seit Release `1.1.0` veröffentlicht. Release `1.2.0`
ergänzt die unten beschriebenen Menü-, Tracklisten-, Such-, Dialog-, Filter- und Statistikänderungen
sowie die erweiterte Hauptnavigation. Der folgende Grundumfang und seine Prüfnachweise beziehen
sich auf 1.1.0; die Nachweise für 1.2.0 stehen in den anschließenden Ergänzungen. Die Designschicht wird
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
die breitere Darstellungsmatrix mit Hellmodus, großer Systemschrift und API 24 bis 30 sowie weitere
Geräte-/Performanceprüfungen für 1.2.0 offen.
Release 1.1.0 verwendete außerhalb der vorhandenen echten Cover-Unschärfe des Vollbild-Players
auf allen Versionen den Glas-Fallback aus Transparenz, Verlauf, Kontur und Schatten. Seit 1.2.0
verwischen die gemeinsamen Material-Dialoge den App-Hintergrund ab API 31; diese Ergänzung und
der API-24-Fallback sind [mit der signierten APK geprüft](#ergänzung-suchtreffer-und-dialoghintergrund).
Backdrop-Blur auf weiteren geeigneten ruhenden Oberflächen bleibt eine mögliche, gesondert
zu prüfende Verfeinerung.

## Lokale Menü- und Listennacharbeit vom 06.09.2026

Diese Nacharbeit und die folgenden Ergänzungen sind mit 1.2.0 veröffentlicht.

Die Prüfung aller app-eigenen Dialog-, Dropdown- und Sheet-Einstiegspunkte hat fehlende Glasflächen
bei der Künstlerauswahl, Darstellungsvorschau, den neuen Download-Dialogen und dem separaten
Spiel-Dialog ergänzt. Die Material-Dialoge verwenden `ThemedAlertDialog`, einschließlich des
Live-Hintergrund-Hinweises. Ihre Auswahlkreise, Checkboxen und Aktionen erhalten Farben passend zu
Hell-/Dunkelmodus; die roten Entfernen-Aktionen bleiben erkennbar.

Auf dem Samsung ließ sich außerdem ein abgeschnittenes Playlist-Überlaufmenü reproduzieren:
Material3 verschiebt das Sheet erst nach seinem öffentlichen `modifier`. Die dort angebrachte
Glasfläche samt Clip blieb deshalb am oberen Fensterrand stehen. `CustomModalBottomSheet` zeichnet
Glasfläche und Griff jetzt innerhalb des bewegten Inhalts. Auch `BottomMenu` verwendet diese
Komponente. `LocalKruxxGlassSheet` verhindert eine zweite Fläche und zusätzliche Kopfabstände in
`Menu` und `GridMenu`. Navigationseinrückungen beachten bereits verbrauchte System-Inset-Flächen.

Schwebende Menüs und Dialoge verwenden eine getönte Unterlage (`modalBackdropAlpha = 0.88f`), damit
Schrift und Bilder dahinter die Menüeinträge nicht überlagern. Verlauf und Kontur bleiben erhalten.
Das Queue-Sheet behält seine deckende Unterlage; Vollbild-Audio-/Video-Player behalten ihre eigene
Hintergrunddarstellung. Androids Ordnerauswahl, Freigabe- und Berechtigungsdialoge bleiben System-UI.

Tracks in Künstleransichten (Online/Bibliothek), lokalen und Online-Playlists, „Titel“ und
Gerätetiteln verwenden `kruxxTrackCard()`: 8 dp Seitenrand, 3 dp oben und unten, 16-dp-Rundungen und
feine Kontur. Es gibt keinen zusätzlichen Schatten oder Blur-Durchlauf je Track. Das vereinheitlicht
die Listen, ohne die Karten optisch so stark zu gewichten wie Header oder Menüs. Diese erste
Nacharbeit verwendete den gemeinsamen Glas-Fallback; der anschließend ergänzte Dialog-Backdrop-Blur
ist im folgenden Unterabschnitt beschrieben.

133 App-Unit-Tests sowie der abschließende Debug-Build und Release-Lint sind erfolgreich. Die finale
Debug-APK ist auf dem Samsung SM-S931B/Android 16 installiert. Die praktische Stichprobe umfasst
Listen-/Rastermenüs von Künstlern und Playlists, verschachtelte Playlist-/Eingabedialoge, Track-Kacheln
und die Download-Auswahl für einzelne und mehrere Titel, Kopieren, Verlauf und Entfernen-Bestätigung.
Auch die Download-Auswahl im Hellmodus bei Systemschriftfaktor 1,3 bleibt vollständig bedienbar.
Die ursprünglichen Einstellungen (Systemmodus, Listenmenü, Schriftfaktor 0,8) sind wiederhergestellt.
Die vollständige Geräte-/Darstellungsmatrix für 1.2.0, insbesondere auf API 24–30,
bleibt ein ergänzender Nachtest;
Details stehen im
[`IST-Stand`](KRUXX-IST-STAND.md#lokale-glass-nacharbeit-vom-06092026--veröffentlicht-mit-120).

### Ergänzung: Suchtreffer und Dialoghintergrund

Bei der zusätzlichen Release-Sichtprüfung wurden die Titel-Suchergebnisse mit genau derselben
Suche in der Debug-App verglichen. Beide ließen die Track-Flächen aus. Online-Titel-/Videotreffer,
Bibliothekssuche und empfohlene Titel während der Suche verwenden deshalb ebenfalls
`kruxxTrackCard()` mit den bestehenden Abständen.

Die Material-Dialoge ergänzen jetzt `KruxxDialogBackdrop`: ein einzelner `RenderEffect` auf der
Activity-Ansicht hinter dem separaten Dialogfenster ab API 31. Radius: 16 dp, auf 64 Pixel begrenzt.
Die Schrift und Bedienelemente des Dialogs bleiben scharf. Eine Zählung aktiver Dialoge verhindert,
dass ein geschlossener Dialog die Unschärfe eines weiteren entfernt; der letzte Dialog gibt die
scharfe App-Ansicht wieder frei. Ältere Geräte behalten die getönte Glasunterlage. Anders als
[systemweiter Fenster-Blur](https://source.android.com/docs/core/display/window-blurs) benötigt
dieser Effekt keine gesonderte WindowManager-Unterstützung; diese ist auf dem Samsung nicht verfügbar.
Diese Ergänzung ist auf die gemeinsamen Material-Dialoge begrenzt; scrollende Kacheln erhalten
weiterhin keinen eigenen Blur-Durchlauf. Der
[signierte Nachtest vom 07.09.2026](KRUXX-IST-STAND.md#signierte-geräteprüfung-vom-07092026)
bestätigt auf dem Samsung die Track-Kacheln, den Dialog-Blur und dessen Entfernung nach
Abbrechen beziehungsweise Zurück. Auf API 24 funktionieren Suche und Download-Dialog mit dem
Fallback auch bei vollständig optimierter Release-Ausführung; die breitere Darstellungsmatrix
bleibt offen.

### Ergänzung: einheitliche Filterleisten

Die Leisten mit „Titel/Favoriten/Zwischengespeichert“ beziehungsweise
„Audio/Video/Nur in KruXx“ besaßen bislang nur transparente Material-Chips. Sie erhalten jetzt eine
durchgehende leichte Glasfläche, 16-dp-Rundungen und eine blaue Auswahlfläche mit feiner Kontur.
`ButtonsRow` verwendet dafür `kruxxFilterBar()` mit 8 dp Seitenrand und 4 dp vertikalem Abstand;
zusätzliche Einrückungen der einzelnen Aufrufer entfallen. Das betrifft auch Künstler-, Alben-,
Playlist-, Verlaufs- und Statistikfilter. Die Release-Änderungsreiter nutzen denselben Glasbaustein.

Nur die Auswahlbuttons scrollen seitlich, die Glasfläche bleibt stehen. Quellenfilter bei
Künstlern und Alben erhalten einen eigenen Platz rechts innerhalb der Leiste und können lange
Reiter nicht überdecken. Es entsteht kein zusätzlicher Blur-Durchlauf pro Button.

Unveröffentlichte Ergänzung vom 08.09.2026: Alle `ButtonsRow`-Filter verwenden dieselben
antippbaren `<<`-/`>>`-Hinweise wie die horizontale Hauptnavigation. Das umfasst Titel, Künstler,
Alben, Playlists, Downloads, Verlauf, Statistik sowie Suchquellen und Suchergebnisse. Die Hinweise
erscheinen nur bei tatsächlichem Überlauf und nur in noch erreichbare Richtungen. Beim Wechsel
wird der gewählte Filter in den sichtbaren Bereich gescrollt. Die Messung berücksichtigt
Schriftgröße, Fensterbreite und den Platz des festen Quellenfilters; nach einer Verbreiterung
verschwinden die Pfeile, wenn alle Einträge passen. Die obere Werkzeugleiste und die darunterliegende
Filterleiste bleiben getrennt: Aktionen verwenden ihr vorhandenes Drei-Punkte-Menü, Kategorien
die Scrollpfeile. Die Downloads-Ansicht hält außerdem Platz für eine rechte Hauptnavigation frei.

In der Statistik sind zusätzlich die Karte mit Titelanzahl/Wiedergabezeit und die Titel der
Rangliste angeglichen. Die leichten Track-Kacheln gelten für jeden Zeitraum einschließlich „Gesamt“.
Die Auswahl „Liste / Raster“ neben der Zeitraumüberschrift bietet einen Titel je Zeile oder
zwei kompakte Karten nebeneinander. Liste ist die Voreinstellung; die Auswahl wird für alle
Statistikzeiträume gespeichert. Im Raster stehen Cover/Rang, Dauer und Downloadknopf über Titel
und Künstler, damit die Namen nicht zwischen Cover und Aktion eingequetscht werden. Beide
Ansichten behalten die leichten Glasflächen und die vorhandenen Titelaktionen.
Filterleisten und Statistik-Karten sind auf dem Samsung-Zielgerät nachgeprüft; Debug-Build und
Release-Lint sind erfolgreich. Der ergänzende Prüfstand steht im
[IST-Stand](KRUXX-IST-STAND.md#lokale-filterleisten-nacharbeit-vom-06092026--veröffentlicht-mit-120).
Die Ansichtsauswahl ist einschließlich Wiederherstellung nach App-Neustart auf dem Samsung
nachgeprüft. Die Nachweise stehen im
[Statistik-Prüfstand](KRUXX-IST-STAND.md#auswählbare-statistikansicht-vom-06092026--veröffentlicht-mit-120).

## Bedingte Erweiterung der Hauptnavigation

Unveröffentlichte Ergänzung vom 08.09.2026: App-Icon und Wortmarke im Header sind gemeinsam als
„Zur Startseite“-Schaltfläche bedienbar. Ein Tipp wählt die Startseite ausdrücklich aus und verlässt
offene Unterseiten. Die zusammenhängende Trefferfläche ist mindestens 48 dp hoch. Wiederholtes
Tippen legt keine weiteren Seiten an; die geerbten versteckten Spielaktionen des Icons entfallen
in KruXx. Bei bewusst ausgeschalteter Startseite gilt der vorhandene Titel-Fallback.

Unveröffentlichte Korrektur vom 08.09.2026: Startseite, Sucheingabe und Suchergebnisse verwenden
dieselbe Hauptleiste einschließlich „Playlists“ und „Downloads“. Suchquellen und Ergebniskategorien
stehen als separate Filterleiste oberhalb des Inhalts. Die übernommenen, nicht unterstützten
Suchkategorien „Vorgestellt“ und „Podcasts“ entfallen in KruXx. Ein Klick auf die Hauptleiste öffnet
den jeweiligen Bereich der Startseite; die Suche gibt keinen Home-Reiter als ausgewählt aus.

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
nicht nach einer fest angenommenen Reiterzahl. Die Hinweise haben eigene Randflächen und zugängliche Inhaltsbeschreibungen.
Weitere Nachtests betreffen insbesondere kleine Displays, große Systemschrift und einen späteren Podcast-Reiter.
Diese Erweiterung ist seit 1.2.0 veröffentlicht: „Downloads“ ergänzt die Hauptleiste als sechster
Reiter. Die Hinweise reservieren eigene Randflächen, lassen sich zusätzlich zum Wischen antippen und verschwinden
richtungsabhängig an den Scrollgrenzen. Alle beschrifteten Reiter einer Leiste sind gleich breit:
Die längste gerenderte Beschriftung bestimmt mit seitlichem Abstand die gemeinsame Breite
(mindestens 76 dp). Symbole und Texte werden innerhalb dieser Flächen zentriert. Schriftwechsel,
Schriftgröße und zusätzliche Reiter fließen in die Messung ein, auch der spätere Podcast-Reiter.
„Interpreten“ heißt in der deutschen KruXx-Oberfläche seit 1.2.0 „Künstler“.

Die mit 1.2.0 veröffentlichte Downloaderweiterung zeigt eine kompakte Fortschrittskarte mit 10-dp-Balken,
vollständig gerundeten Enden, dezentem Farbverlauf und sanften Übergängen. Prozentzahl,
Arbeitsschritt, Format und Speicherziel sind getrennt lesbar; Fehler verwenden zusätzlich zur
Farbe einen erklärenden Text. Der Balken hat eine zugängliche Fortschrittssemantik. Seit dem lokalen
Stand vom 06.09.2026 lassen sich Startanzeige und Übersichtskarte von Beginn an über ein dezentes
rötliches Glas-× oder durch Wischen nach links/rechts ausblenden. Der 48-dp-Tippbereich enthält eine
34-dp-Glasfläche mit feinem Rand und weichem roten Blur-Licht hinter dem scharfen Symbol;
ältere Android-Versionen behalten den transparenten Verlauf ohne Blur-Pass. Das Ausblenden ist
zusätzlich als Accessibility-Aktion verfügbar, stoppt keinen Auftrag und bleibt nach einem Neustart
erhalten. Das Zahnrad im Downloads-Reiter öffnet den Statusverlauf. Erfolgreiche
100 % stehen für die vollständig gespeicherte und geprüfte Ausgabe.

Der Downloads-Reiter bietet außerdem ein Papierkorbsymbol mit 48-dp-Tippbereich je Datei.
„Auswählen“ oder langes Drücken aktiviert Checkboxen; eine gemeinsame Auswahlleiste zeigt Anzahl,
„Alle auswählen“, Entfernen und Schließen. Die Hauptnavigation erhält dafür keine weiteren Elemente.
Ein Bestätigungsdialog zeigt Dateinamen und Speicherort vor der dauerhaften Entfernung. Die
Ergebnisanzeige unterscheidet Erfolg und Fehler mit Text; Auswahlknöpfe verwenden den blauen
App-Akzent, Entfernen zusätzlich Rot. Checkboxen und Dateiaktionen besitzen zugängliche Beschriftungen.

## Abnahmekriterien

- Keine funktionale Änderung oder Regression in Navigation, Wiedergabe und Datenanzeige.
- Flüssiges Scrollen und stabile Animationen auf dem Zielgerät.
- Konsistenter Fallback ab Android 7 und echter Blur nur auf unterstützten Geräten.
- Lesbare Darstellung in Hell- und Dunkelmodus sowie mit großer Systemschrift.
- Bestehende Appearance-Einstellungen bleiben bedienbar und führen nicht zu unlesbaren Flächen.
- Eine Frischinstallation zeigt im Vollbild-Player das vollständige Cover vor dem unscharfen
  Coverhintergrund; ein Update darf eine bewusst abweichende Nutzereinstellung nicht überschreiben.
- Gemeinsame Designbausteine statt voneinander abweichender Einzellösungen pro Screen.
