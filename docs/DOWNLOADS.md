# Downloads, MP3 und Dateikopien

Stand: 07.09.2026. Veröffentlicht mit [KruXx 1.2.0](https://github.com/Massefehler/KruXx/releases/tag/v1.2.0).
Die unten aufgeführten offenen Gerätefälle bleiben Nachtests und gelten nicht als bestanden.
Der verbindliche Prüfstatus steht in [KRUXX-IST-STAND.md](KRUXX-IST-STAND.md).
KruXx `1.2.0` setzt Android 7.0 / API 24 voraus; Android 6 gehört seit der Supportentscheidung
vom 07.09.2026 nicht mehr zur aktuellen Abnahmematrix.

## Bedienung

Alle manuellen Downloadaktionen laufen durch dieselbe Auswahl, einschließlich „Alle Tracks
downloaden“ und Downloads aus Künstler-, Album-, Playlist- und Auswahllisten. Ein Sammelauftrag
zeigt einen Dialog für alle enthaltenen Titel. Lokale Gerätedateien und doppelte IDs entfallen.

| Auswahl | Ergebnis |
|---|---|
| Nur in KruXx | Offline-Audio bleibt im Download-Cache; erzeugte MP3s/Videos zusätzlich im privaten App-Speicher |
| Auch in „KruXx-Downloads“ speichern | Zusätzlich eine normale Datei unter `Download/KruXx-Downloads/Audio` oder `Video`; die App legt den jeweiligen Ordner beim Speichern an |
| Auch in einem anderen Ordner speichern | Zusätzlich eine normale Datei im gewählten Android-Ordner, auch auf unterstützten SD-/USB-Laufwerken |
| Originalaudio | Tatsächlich heruntergeladener Container und Codec, ohne erneute Kompression |
| MP3 · 320 kbit/s | LAME CBR 320, Qualität 0, aus der vollständig heruntergeladenen Audioquelle |
| Video mit Ton | Beste kompatible Videoauswahl bis 2160p, mit der Audioquelle ohne erneute Kompression zusammengeführt |

In Videotreffern öffnet das Downloadsymbol die Auswahl Video/MP3. Normale Musikdownloads bieten
Originalaudio/MP3. Normale Titel besitzen rechts denselben gut erreichbaren Downloadknopf, sowohl
im Titelreiter und in Suchtreffern als auch in Künstler-, Album- und Playlistlisten. Ein Tipp auf
einen bereits heruntergeladenen Titel öffnet ebenfalls die Format-/Speicherauswahl beziehungsweise
wendet die gemerkte Vorgabe an; der Knopf entfernt keinen bestehenden Download. Die Statussymbole
für laufende und fertige Downloads bleiben sichtbar. Lokale Gerätedateien erhalten diesen Knopf nicht.
Speichermodus und Formate für Musik und Video lassen sich merken. Hintergrund-
Autodownloads verwenden gemerkte Vorgaben; ohne Vorgabe speichern sie wie bisher Audio in der App
und öffnen keinen Dialog während der Wiedergabe.
„Nur in KruXx“ ist die anfängliche Vorgabe. Der öffentliche Ordner ist eine zusätzliche Auswahl,
kein verpflichtendes Ziel für alle Downloads.

Unter **Einstellungen → Daten → Downloads und Dateikopien** sind Vorgaben, Ordnerwechsel,
Dateiaufträge und gespeicherte MP3s/Videos erreichbar. „Beim nächsten Download wieder fragen“
ermöglicht die erneute Auswahl. Gespeicherte Videos lassen sich dort offline öffnen; normale
Videowiedergabe verwendet ebenfalls die lokale Datei, sobald sie vorhanden ist.

Der zusätzliche Hauptreiter **Downloads** zeigt den aktuellen Inhalt von **Audio** (MP3 und
Originalaudio) und **Video** mit Dateinamen, Größe, tatsächlichem Pfad und Öffnen-Aktion. Die
Dateiansicht wird bei Rückkehr in die App, MediaStore-Änderungen, abgeschlossenen Dateiaufträgen und
über Aktualisieren neu eingelesen. **Nur in KruXx** zeigt daneben die internen Audio-Downloads
sowie gespeicherte MP3s/Videos mit Wiedergabe und Kopieren. Download-Vorgaben und Dateiaufträge
bleiben über das Zahnrad erreichbar. Dateien in frei gewählten anderen Ordnern werden in dieser
Standardordner-Ansicht nicht aufgeführt.

Das **Papierkorbsymbol** entfernt eine einzelne Datei. **Auswählen** oder langes Drücken öffnet
die Mehrfachauswahl; **Alle auswählen** markiert die Dateien im aktuellen Bereich (Audio, Video
oder Nur in KruXx). Beim Bereichswechsel wird die Auswahl zurückgesetzt. Der Bestätigungsdialog
nennt die Dateien, ihre tatsächlichen Speicherorte und die dauerhafte Entfernung. Abbrechen
verändert keine Datei. Öffentliche Kopien werden unabhängig von den internen Downloads entfernt;
intern lassen sich Originalaudio, MP3 und Video einzeln auswählen. Anschließend zeigt ein Ergebnis
die entfernte Anzahl und gegebenenfalls die fehlgeschlagenen Dateien mit Wiederholungsmöglichkeit.
Die Übersicht liest den Ordner erneut ein; die Ordner selbst werden nicht gelöscht.

Die Hauptleiste lässt sich horizontal wischen. Alle beschrifteten Reiter einer Leiste erhalten
dieselbe Breite: Maßgeblich ist die breiteste Beschriftung in der aktuellen Schrift und Schriftgröße,
mit zusätzlichem seitlichem Abstand und mindestens 76 dp. Symbol und Text werden darin zentriert.
Die Berechnung berücksichtigt auch später ergänzte Reiter; eine feste Anzahl wird nicht vorausgesetzt.
Der deutsche Künstler-Reiter heißt in KruXx „Künstler“.
Bei tatsächlich gemessenem Überlauf reserviert sie Platz für **<<** und **>>**. Nur die Richtung
mit weiteren verborgenen Reitern wird angezeigt; ein Tipp scrollt ebenfalls weiter. Bei ausreichend
Platz entfallen beide Hinweise. Der aktive Reiter wird beim Wechsel in den sichtbaren Bereich geholt.

**Dateien kopieren …** erscheint neben Sammeldownload-Werkzeugen (gegebenenfalls im Überlaufmenü)
und im Titelmenü. Es zeigt fertige Downloads aus dem aktuellen Kontext mit Mehrfachauswahl.
Das ausgewählte Format bestimmt die verfügbare Liste. MP3 kann aus vorhandenem Originalaudio
erzeugt werden; für Videokopien muss bereits eine fertige Videodatei vorliegen. Kopieren startet
keinen Netzabruf. App-Dateien bleiben erhalten.
Auch beim Kopieren kann zwischen „KruXx-Downloads“ und einem anderen Ordner gewählt werden.

Für „KruXx-Downloads“ verwendet Android 10+ die eigene MediaStore-Downloads-Sammlung ohne
Ordnerfreigabe oder allgemeine Speicherberechtigung. Android 7–9 benötigt dafür die beim Start
des Auftrags abgefragte Schreibberechtigung. Dateien werden erst nach vollständiger Prüfung
öffentlich sichtbar. Die öffentliche Kopie bleibt auch bei Entfernen des App-Downloads erhalten.
Auf Android 10+ werden frühere fertige KruXx-Dateien im Stammordner anhand von App-Eigentümer und
generiertem Namen in den passenden Unterordner verschoben. Fremde oder unfertige Dateien werden
nicht verschoben. Eine bestehende Android-Ordnerfreigabe für `KruXx-Downloads`, `Audio` oder `Video`
wird zusätzlich zum Lesen genutzt; dadurch erscheinen auch ältere, über die Ordnerauswahl erstellte
Kopien. Nicht verschiebbare zugängliche Altdateien bleiben mit ihrem tatsächlichen Pfad sichtbar;
auf Android 7–9 werden Altdateien weiterhin im Stammordner gefunden. Dort benötigt die Übersicht
die Leseberechtigung. Öffnen verwendet eine einzelne URI-Freigabe; der FileProvider ist auf den
KruXx-Downloadordner begrenzt.

Für andere Ordner stellt Androids Ordnerauswahl Gerätespeicher, SD-Karten und angeschlossene USB-Laufwerke bereit,
soweit der jeweilige Dokumentanbieter Zugriff und Dateiumbenennung unterstützt. KruXx merkt eine
persistente Ordnerberechtigung. Ein entfernter Datenträger oder entzogener Zugriff führt zu einem
sichtbaren Fehler; der ursprüngliche Auftrag behält sein Ziel für eine Wiederholung. Ein anderer
Zielordner wird mit einer neuen Kopier-/Downloadaktion gewählt.

## Grafischer Fortschritt

Nach manuellen Einzel-, Sammel- und Kopieraufträgen öffnet sich eine Fortschrittskarte mit
abgerundetem Verlaufsbalken, Prozentzahl, aktuellem Arbeitsschritt, Format und Zielordner.
„App weiter nutzen“ schließt die Anzeige; der Auftrag läuft weiter. Der Downloads-Reiter zeigt
den aktiven beziehungsweise neuesten Auftrag, die Download-Einstellungen die weiteren Aufträge.
Ein dezent rötliches Glas-× ist bereits während des Downloads sichtbar. Es blendet den Status
aus, ebenso Wischen nach links oder rechts auf der Karte. Der Auftrag läuft weiter; „Abbrechen“
bleibt eine separate Aktion. Ausgeblendete Aufträge erscheinen auch nach Reiterwechsel oder
App-Neustart nicht erneut als Übersichtskarte, und ältere Ergebnisse rücken nicht nach. Neue
Aufträge dürfen ihren Status wieder zeigen. Über das Zahnrad im Downloads-Reiter bleibt der
Verlauf einschließlich ausgeblendeter Aufträge abrufbar.
Automatische Aufträge öffnen keinen Dialog. Interne Audioübertragungen zeigen zusätzlich in
„Nur in KruXx“ ihren Byte-Fortschritt.

`FileProgressPlan` gewichtet nur die benötigten Schritte. Audio-/Videotransfer verwenden empfangene
Bytes, MP3 und Muxing Medienzeitstempel, Kopieren und Leseprüfung die übertragenen Bytes.
Fehlt die Gesamtlänge, wird kein zeitbasierter Fortschritt erfunden. Prozentwerte beschreiben den
Gesamtauftrag einschließlich aller Dateien; sie sind keine Restzeitprognose. Laufende Aufträge
bleiben bei höchstens 99 %. Erst nach erfolgreicher Fertigstellung aller Zieldateien meldet der
Worker 100 %. Ein bereits fertiges internes Original oder eine private MP3 beendet einen
zusätzlich angeforderten Export nicht vorzeitig. Identische vorhandene Zieldateien zählen erst
nach Inhaltsvergleich als erledigt. Fehler nennen den betroffenen Titel und Arbeitsschritt;
Abbruch und Fehler erscheinen nicht als erfolgreicher Abschluss.

Die Fortschrittsdaten werden gedrosselt an WorkManager und die Android-Benachrichtigung gemeldet;
Abschlussdaten bleiben nach einem App-Neustart abrufbar. Manuelles Originalaudio „Nur in KruXx“
verwendet dieselbe Auftragsanzeige, ohne eine zusätzliche Cachekopie anzulegen. Für bereits
vollständig vorhandene Quellen wird keine unnötige Netzwerkbedingung gesetzt.

## Architektur und Dateiintegrität

- Die Auswahl-, Kopier-, Verlaufs-, Fortschritts- und Entfernungsdialoge verwenden seit der lokalen
  Glass-Nacharbeit vom 06.09.2026 `ThemedAlertDialog`. Glasfläche, Rundungen und lesbare Material-
  Auswahl-/Aktionsfarben werden zentral bereitgestellt; Auftrags-, Bestätigungs- und Dateilogik
  bleiben in den bestehenden Komponenten.
  Die anschließende Sichtprüfung desselben Suchtreffers in Debug und Release zeigte noch scharf
  durchscheinenden Hintergrundtext. `KruxxDialogBackdrop` ergänzt deshalb ab API 31 einen einzelnen
  `RenderEffect` auf der App-Ansicht hinter dem separaten Dialogfenster. Der Effekt benötigt keine
  systemweite Fenster-Blur-Unterstützung, die auf dem Samsung als nicht verfügbar gemeldet wird.
  Der letzte geschlossene Dialog entfernt den Effekt wieder; ältere Geräte behalten die getönte
  Glasunterlage. Es gibt keinen Blur pro Auswahlzeile oder Track.
- `DownloadCenter` sammelt Anfragen und Vorgaben. `DownloadHelper.addDownload(s)` ist der
  öffentliche Einstieg; `addDownloadsInternal` darf nur nach der Auswahl bzw. aus dem Worker
  verwendet werden. Bestehende Download-Entfernung entfernt auch zugehörige private MP3-/Videodateien.
  `hideStatus()` speichert ausschließlich Auftrags-UUIDs in `hidden_status_jobs`; Worker,
  Fortschrittsdaten und Dateien bleiben erhalten. Die Statusauswahl prüft die Sichtbarkeit erst
  nach der Wahl des aktiven/neuesten Auftrags. Beide Wischrichtungen verwenden Material3s
  `SwipeToDismissBox`, das × hat einen 48-dp-Tippbereich und eine zugängliche Beschriftung.
- `DownloadRemovalViewModel` bewahrt die bestätigte Auswahl über Rotation und Reiterwechsel.
  Dateioperationen laufen außerhalb des UI-Threads; ein Fehler beendet nicht die übrige Auswahl.
  Eine Wiederholung verwendet ausschließlich fehlgeschlagene Einträge. `SharedDownloads.remove()`
  teilt die Sperre mit Export und Migration, prüft den ausgewählten Datei-Snapshot und löscht
  ausschließlich einzelne Dateien im Stammordner, `Audio` oder `Video`. MediaStore erhält die
  konkrete Zeilen-URI und zusätzliche Bedingungen für Pfad, Name, Größe und Pending-Status;
  SAF verwendet `DocumentsContract.deleteDocument`. Alte Android-Versionen prüfen den kanonischen
  Dateipfad und benötigen Schreibberechtigung. Bei fehlenden Rechten bietet der Ergebnisdialog
  die erneute Freigabe des zugehörigen Standardordners an; fremde Downloads benötigen laut
  [Android-Speicherdokumentation](https://developer.android.com/training/data-storage/shared/media#storage-permissions)
  das Storage Access Framework. `removeAsset()` entfernt intern genau eine registrierte Konvertierung.
  Originalaudio wird über den Media3-Dienst entfernt; erst dessen Entfernungsmeldung zählt als
  Abschluss. Andere Formate und öffentliche Kopien werden dabei nicht mitgelöscht. Interne
  Entfernungen invalidieren ältere Dateiaufträge für den betroffenen Titel.
- `SongItem.Render` verwendet für KruXx-Audiotitel `AudioDownloadButton` mit explizitem `video=false`.
  Er ersetzt dort das bisherige kleine Cache-/Download-Umschaltsymbol. Das Öffnen der Auswahl
  verändert weder Cache noch Formatmetadaten; fertiges Originalaudio bleibt für MP3 und Kopien nutzbar.
- `FileDownloadWorker` nutzt persistente JSON-Eingaben außerhalb des WorkManager-Data-Limits und
  getrennte eindeutige Aufträge. Ein Semaphore begrenzt Konvertieren/Kopieren auf einen Auftrag;
  die bestehende Media3-Audioqueue lädt bis zu fünf Quellen gleichzeitig. Alle Media3-Queuezugriffe
  des Workers erfolgen auf dem Main-Looper. Dateiaufträge zeigen Fortschritt und Abschlussmeldung;
  Fehler einzelner Titel verhindern die Bearbeitung der übrigen Titel nicht.
- Abbrechen beendet den Dateiauftrag. Die separat verwaltete Media3-Audioqueue und fertig gespeicherte
  App-Downloads bleiben bestehen. Ein erneuter Auftrag verwendet fertige Dateien wieder. Entfernen
  eines Downloads erhöht dessen persistente Generation, damit ein älterer Worker ihn nicht
  ungewollt wieder als fertige MP3/Videodatei registriert.
- `OfflineFiles.snapshot` liest ausschließlich vollständige Download-Cachebereiche über eine
  `CacheDataSource` ohne Netzwerk-Upstream. Teilstücke werden in Byte-Reihenfolge gestreamt und
  Länge/Dateisignatur geprüft. Es gibt kein Einlesen ganzer Titel oder Playlists in den Arbeitsspeicher.
- `DownloadNames` bildet Dateinamen und Download-Beschriftungen aus Künstler und Titel:
  `Noma - Sleepwalker.mp3`, ohne Uploader, ID-Hash oder Format-Zusatz im Namen. Bei Audiotracks
  zählen die Musikmetadaten; Videotitel im Schema `Künstler - Titel` (auch Gedankenstriche) werden
  aufgeteilt. Ohne solche Angaben bleibt der Videotitel ohne erfundenen Künstler stehen. Offizielle
  Art-Tracks behalten ihre Musikmetadaten. Gängige Video-/Qualitäts-Klammern entfallen, musikalische
  Versionsangaben wie Remix, Live und feat. bleiben erhalten. Unicode bleibt erhalten;
  problematische Pfadzeichen werden ersetzt und der UTF-8-Namensteil auf 160 Bytes begrenzt.
- Quellmetadaten und Video-Herkunft werden getrennt von der sichtbaren Benennung persistiert;
  Queue-/Kopierrundläufe behalten sie. Eine aufgeteilte Künstlerangabe übernimmt keine Kanal-ID als
  Künstlerverknüpfung. Alte lokale JSON-Assets ohne Herkunftsflag erhalten eine Kompatibilitätsregel
  für Kanalverknüpfungen ohne Album. Normale neue Audiotracks durchlaufen diese Regel nicht.
- Bekannte alte Exportnamen werden anhand von Hash **und exaktem früheren Namen** erkannt und
  umbenannt. MediaStore-Zeilen müssen der App gehören; SAF-/Legacy-Dateien benötigen eine bestehende
  Schreibfreigabe. Unterschiedliche Dateien gleichen Namens bleiben als `(2)`, `(3)` usw. erhalten.
  Die Umbenennung verändert keine Mediendaten oder bestehenden eingebetteten Tags. Neu erzeugte
  MP3-Dateien verwenden die aufgelösten Künstler-/Titeldaten auch für ID3.
- Exporte erhalten einen eigenen temporären Namen und Marker. Erst nach vollständigem Schreiben
  und SHA-256-Leseprüfung wird die Datei umbenannt. Identische bestehende Dateien, einschließlich
  nummerierter Kopien, werden übersprungen; andere Dateien bekommen keinen Überschreibzugriff.
  Ein abgebrochener Auftrag bereinigt nur seine eigene temporäre Datei. Eine nach Umbenennung
  unveränderte Dokument-URI führt nicht zur Löschung einer bereits fertigen Kopie.
- `SharedDownloads` adressiert den öffentlichen Standardordner über die interne Ziel-URI
  `kruxx-downloads://public`. Ab API 29 bleiben neue MediaStore-Dateien mit `IS_PENDING=1` bis zur
  erfolgreichen Leseprüfung unveröffentlicht; ältere Android-Versionen verwenden den öffentlichen
  Downloadpfad. Abbruchbereinigung prüft Zielordner, temporären Namen und gegebenenfalls Pending-Status.
  Beim Export trennen MIME-Typen Audio und Video, auch bei der gemeinsamen WebM-Endung.
  Meldet Androids Scanner anschließend reines WebM-Audio als Video, behält die Übersicht anhand
  des Zielordners beziehungsweise des alten generierten Dateinamens die korrekte Zuordnung.
  Umbenannte WebM-Altdateien im Stammordner werden anhand ihrer tatsächlichen Medienspuren erkannt. Migration und Exporte
  sind gegeneinander gesperrt; die Dateiliste wird aus MediaStore beziehungsweise dem tatsächlichen
  Verzeichnis gelesen. Das Verschieben verwendet
  [MediaStore.RELATIVE_PATH](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns#RELATIVE_PATH).
- MP3/Video liegen unter `filesDir/offline_media`, Arbeitsdateien und Aufträge unter
  `noBackupFilesDir/file_download_jobs`. Die normale Offline-Kennzeichnung berücksichtigt erzeugte
  Dateien. Bei fehlendem vollständigem Originalaudio kann die Audio-Wiedergabe auf die gespeicherte
  MP3/Videodatei zurückgreifen; dieser Zugriff umgeht die Audio-Cachekeys.
- `InnerTubeXPlayer.videoForDownload` erlaubt nur direkte Videostreams. HLS, SABR und Livestreams
  sind ausgeschlossen. Byte-Range-Videotransfers laufen außerhalb der Audio-Resolver-/Cachekette;
  ignorierte oder unvollständige Range-Antworten werden abgelehnt. Media3 muxed VP8/VP9 plus
  Opus/Vorbis nach WebM, andere unterstützte Kombinationen nach MP4.
- `OfflineVideoPlayer` verwendet einen lokalen ExoPlayer mit Android-Steuerung, Positionsübergabe
  und Lifecycle-Pause. Für fertige Videos werden weder IFrame noch Netzwerkauflösung gestartet.

Die Oberfläche ist auf `BuildConfig.INDEPENDENT_FORK` begrenzt. Gemeinsame Android-Abhängigkeiten
und native Bibliotheken werden auch in den geerbten Android-Flavors mitgebaut.

## MP3 und Native-Build

Android `MediaExtractor`/`MediaCodec` dekodiert die Quelle nach PCM. Die JNI-Brücke übergibt
Mono/Stereo an LAME 3.100. Die Ausgabe ist MPEG-1 MP3 mit festen 320 kbit/s und 44,1/48 kHz;
niedrigere Eingangs-Sampleraten werden dafür resampled. Titel und Interpret werden als UTF-16
ID3v2.3 gespeichert. Es gibt keinen Stereo-Downmix, keine Normalisierung und kein Abschneiden
von Stille. Bereits verlorene Quelldetails werden durch 320 kbit/s nicht wiederhergestellt.

Originalaudio bleibt die Option ohne weitere verlustbehaftete Umwandlung. Fertige Alt-Downloads
werden nicht ungefragt erneuert; für eine neue High-Quelle muss ein Alt-Download wie bisher
entfernt und neu geladen werden.

Der Android-Build benötigt NDK `27.3.13750724` und CMake `3.22.1`. CMake baut `libmp3lame.so` und
die separate `libkruxx_mp3.so` für die vorhandenen ABIs mit 16-KiB-Linker-Ausrichtung.
Der LAME-Quellstand, Archivhash, Herkunft und LGPL-2.0-or-later liegen unter
[`composeApp/src/androidMain/cpp/lame`](../composeApp/src/androidMain/cpp/lame/README.kruxx.md).
Das APK behält den Lizenztext trotz Resource-Shrinking und nennt LAME im Lizenzbildschirm.

## Prüfung und Geräteabnahme

Reproduzierbare lokale Prüfungen:

```sh
./gradlew :composeApp:testKruxxUniversalProdDebugUnitTest :innertube:test
./gradlew :composeApp:lintKruxxUniversalProdRelease :composeApp:assembleKruxxUniversalProdDebug
python3 scripts/check-mp3-encoder.py
```

Prüfergebnis vom 06.09.2026: 133 App-Tests erfolgreich; 58 Innertube-Tests im vorigen Prüflauf vom
05.09.2026 erfolgreich (unveränderter Modulstand). Universal-Debug-APK
gebaut; Release-Lint ohne neue Fehler erfolgreich (zwei Versionshinweise zum bestehenden Media3-Pin).
Beide nativen Bibliotheken liegen für arm64-v8a, armeabi-v7a, x86 und x86_64 mit mindestens
16-KiB-LOAD-Ausrichtung im APK. Der enthaltene Lizenztext und die unveränderten vendorten LAME-Dateien
wurden geprüft. Die Lint-Baseline und veröffentlichten Release-Dateien wurden nicht geändert.

Zusätzliche Release-Vorprüfung vom 06.09.2026: Der optimierte Universal-Release-Build des gesamten
lokalen Arbeitsstands ist ebenfalls erfolgreich. Auch darin bleiben beide MP3-Bibliotheken für
alle vier ABIs, ihre nativen DEX-Schnittstellen und der unveränderte LAME-Lizenztext erhalten;
ELF-/APK-Ausrichtung sind mit 16 KiB geprüft. Die 133 App-Tests wurden erneut bestanden, die
58 Innertube-Tests waren bei unverändertem Stand `UP-TO-DATE`. Das APK trägt weiterhin die alte
Version `1.1.0` / `1000003` und wurde für diese Vorprüfung weder signiert noch installiert.
Die damals noch offene signierte Kernabnahme ist inzwischen in der
[Geräteprüfung vom 07.09.2026](KRUXX-IST-STAND.md#signierte-geräteprüfung-vom-07092026) belegt.
Die übrigen unten aufgeführten offenen Gerätefälle bleiben erforderlich.

`DownloadNamesTest` prüft in sieben Fällen Uploader-Abgrenzung, normale Musikmetadaten,
Bindestriche/Versionen, fehlende Künstlerangaben, Persistenz/Kopierrundläufe, Art-Tracks und alte
Dateinamen. Ein zusätzlicher MediaStore-Test prüft Umbenennen bei Namenskonflikt, unveränderte Bytes,
fremde Dateien, wiederholte Migration und Erkennung der umbenannten Kopie.
`OfflineFilesTest` prüft echte Media3-Cachespans, Lücken, Containererkennung, portable Namen,
Metadaten, Videomenü-Hints und SAF-Dateioperationen einschließlich Gleichheit, Namenskonflikt,
fehlgeschlagener Umbenennung und Neustartbereinigung. Der SAF-Testanbieter hat stabile Dokument-IDs;
ein Test-Shadow bildet Androids Weiterleitung alter Query-Aufrufe auf die moderne Bundle-API ab.
Sieben `DownloadProgressTest`-Fälle prüfen die Trennung von internem Download, Umwandlung und
fertigem Export, wiederverwendete Quellen, interne Originale, Videomuxing, Sammelfortschritt und
unbekannte Dateilängen. Der MediaStore-Test prüft zusätzlich, dass selbst bei vollständig
zurückgelesenen Bytes die Datei bis zur erfolgreichen Veröffentlichung noch `IS_PENDING=1` hat.
Die drei `DownloadStatusTest`-Fälle prüfen das dauerhaft gespeicherte Ausblenden eines laufenden
Auftrags bis nach seinem Abschluss, die unabhängige Sichtbarkeit neuer Aufträge, erhaltene
Verlaufseinträge ohne Nachrücken älterer Ergebnisse und die Trennung gleichzeitig geöffneter Aufträge.
Zehn bisherige `SharedDownloadsTest`-Fälle prüfen MediaStore-Veröffentlichung, identische und unterschiedliche
Zieldateien, Fehler-/Neustartbereinigung, den öffentlichen Ordner auf API 28, Audio-/Video-Trennung,
externes Entfernen, eigentümergebundene Migration und Androids falsche WebM-MIME-Zuordnung.
Sechs zusätzliche Fälle prüfen gezielte öffentliche Löschung, inzwischen geänderte/verschobene
oder unfertige Dateien, Abbruch vor Bestätigung, Teilerfolg mit selektiver Wiederholung,
Providerfehler, ältere Dateipfade und SAF-Dokumentlöschung unter Erhalt anderer Dateien und Ordner.
Zwei `DownloadRemovalTest`-Fälle prüfen die getrennte interne Formatentfernung einschließlich
Persistenz/Generationen und den Erhalt geänderter Dateien bei veralteter Auswahl. Der SAF-Query-Shadow
erbt von `ShadowContentProvider`, damit auch providerseitige Löschaufrufe realistisch ausgeführt werden.
Der native Hosttest baut dieselbe C/JNI-Implementierung und prüft 24-kHz-Stereo, 44,1-kHz-Mono und
48-kHz-Stereo mit ffprobe und tatsächlicher Dekodierung durch ffmpeg. Das ersetzt keinen Test des
Android-MediaCodec-Decoders oder der realen USB-Anbieter.

Gerätetest auf Samsung SM-S931B mit Android 16: Debug-APK als Update mit erhaltenen App-Daten
installiert. Die drei Speicheroptionen erscheinen im Videodownload und die beiden Dateiziele auch
beim Kopieren. Ein 95-Sekunden-Testvideo wurde auf dem Gerät in MP3 umgewandelt und zusätzlich im
automatisch angelegten `Download/KruXx-Downloads` gespeichert. Die außerhalb der App ausgelesene
Datei hat laut ffprobe 320.000 bit/s, 48 kHz, zwei Kanäle und 94,8 Sekunden; Titel und Interpret
sind erhalten. ffmpeg dekodiert die vollständige Datei ohne Fehler. Erneutes Kopieren meldet
„0 gespeichert · 1 bereits vorhanden“ und erzeugt keine zweite Datei.
Das gleiche Video wurde als H.264 in 1920×1080 mit Opus-Stereoton gespeichert (94,761 Sekunden).
Die vollständige Audio-/Videodekodierung mit ffmpeg ist fehlerfrei; die lokale Wiedergabe in KruXx
zeigt laufendes Bild und die passende Gesamtdauer. Eine über Androids Ordnerauswahl erstellte
Videokopie stimmt laut SHA-256 mit der privaten App-Datei überein. Der Ordnerpicker wurde dabei
auf den bereits angelegten Testordner gerichtet; ein anderes physisches Medium wurde nicht getestet.
Ein subjektiver Hörtest zur Bild-/Tonsynchronität bleibt offen. Die spätere Prüfung im Flugmodus
ist bei der zusätzlichen 1.2.0-Abnahme unten beschrieben.

Zusätzlicher Audiotest mit dem aktualisierten Debug-APK: Downloadknöpfe in der Titelsuche und im
Titelreiter öffnen Originalaudio/MP3 mit allen drei Speicheroptionen. Ein bereits vollständig als
Originalaudio gespeicherter Track („Monkeys Spinning Monkeys“, Kevin MacLeod) wurde über den neuen
Knopf nachträglich als MP3 nach `Download/KruXx-Downloads` gespeichert. ffprobe bestätigt
320.000 bit/s, 48 kHz, Stereo, 125,064 Sekunden sowie Titel und Interpret; die vollständige
Dekodierung mit ffmpeg ist fehlerfrei. „Alle Tracks downloaden“ zeigt für die 51 Titel der
Testbibliothek dieselben Optionen und die richtige Anzahl; dieser Sammelauftrag wurde vor dem
Start abgebrochen. Nach der UI-Ergänzung wurden alle 103 App-Tests, Debug-Build und Release-Lint
erneut erfolgreich ausgeführt.

Nachtest der getrennten Ordner auf demselben Handy: Zwei vorhandene MP3-Dateien wurden nach
`Audio` übernommen; SHA-256 bestätigt unveränderte Inhalte. Eine ältere, über die Ordnerauswahl
erstellte Videokopie bleibt im Stammordner und wird über die vorhandene Ordnerfreigabe mit ihrem
tatsächlichen Pfad angezeigt. Eine neue Kopie desselben Videos liegt bytegleich unter `Video` und
lässt sich aus der Übersicht in VLC abspielen. Ein kopierter Originaltrack liegt unter `Audio`;
ffprobe bestätigt reines Opus-Audio, 48 kHz, Stereo, 125,041 Sekunden. Die neue Übersicht zeigt
MP3, Originalaudio, Videos und interne Downloads; die Hauptleiste lässt sich über die Randhinweise
und durch Wischen bedienen.

Nachtest der gleichmäßigen Reiter: Bei Systemschrift 0,8 messen alle Reiter auf dem Samsung-Gerät
224 Pixel, bei 1,3 gemeinsam 331 Pixel. Die Mittelpunkte vollständig sichtbarer Beschriftungen
stimmen mit den Reiterzentren überein (höchstens ein halbes Pixel Rundung). Wischen, beide
Richtungshinweise und automatisches Sichtbarmachen eines ausgewählten Randreiters funktionieren.
Die ursprüngliche Schriftgröße wurde wiederhergestellt; der korrigierte Debug-Build ist installiert.
Debug-Build und Release-Lint sind erfolgreich, ohne neue Lint-Befunde.

Nachtest des grafischen Fortschritts: „Noma – Sleepwalker“ war bereits vor der Änderung als
17.801.380-Byte-Datei in `Download/KruXx-Downloads/Audio` vorhanden und ist sowohl in der App als
auch im Android-Dateimanager sichtbar. Die beiden internen Einträge sind Originalaudio und MP3.
ffprobe bestätigt 320.000 bit/s, 48 kHz, Stereo und 445,032 Sekunden; ffmpeg dekodiert die gesamte
Datei fehlerfrei. Eine erneute Umwandlung über die Videosuche zeigte unter anderem 45, 51, 60 und
76 Prozent mit „In MP3 umwandeln“ und dem korrekten Zielpfad. Beim App-/Dateimanagerwechsel lief
sie weiter. Der Abschluss zeigte 100 Prozent und „0 gespeichert · 1 bereits vorhanden“; die
neu erzeugte private MP3 ist bytegleich mit der vorherigen privaten und öffentlichen Datei.
Die für diesen Nachtest gesicherte private Datei wurde danach entfernt, die geprüfte neue private
MP3 und sämtliche vorherigen öffentlichen Dateien bleiben erhalten.
Eine zusätzliche Originalaudio-Kopie aus den internen Downloads wurde tatsächlich veröffentlicht
und meldete „1 gespeichert · 0 bereits vorhanden“ mit 100 Prozent. MediaStore und ffprobe
bestätigten Audio-Ordner, `IS_PENDING=0`, 94,761 Sekunden, Opus, 48 kHz und Stereo. Die nur für
diesen Nachtest erzeugte öffentliche Originalkopie wurde anschließend wieder entfernt.
Nach Installation des abschließend geprüften Debug-Builds und erneutem App-Start bleibt die
Abschlussanzeige mit 100 Prozent und Zielpfad sichtbar; die Audio-Übersicht zeigt weiterhin alle
vier vorherigen Dateien einschließlich Noma. Im AndroidRuntime-Log erscheint kein Absturz.

Nachtest der Benennung auf demselben Samsung-Gerät: Das Debug-Update benennt alle sechs bekannten
öffentlichen Dateien um, einschließlich der über SAF angelegten Kopie im Stammordner.
`Audio/Noma - Sleepwalker.mp3` liegt weiterhin mit 17.801.380 Bytes vor. Der SHA-256-Vergleich aller
sechs Dateien vor/nach dem Update bestätigt unveränderte Inhalte. Audio-Übersicht, interner
Originaldownload, Kopierauswahl und Abschlusskarte zeigen „Noma - Sleepwalker“ ohne Uploader.
Erneutes Kopieren der MP3 über den internen Originaleintrag meldet 100 Prozent und
„0 gespeichert · 1 bereits vorhanden“; es bleiben vier öffentliche Audiodateien. Der damals
installierte Stand hatte 122 erfolgreiche App-Tests und keine neuen Release-Lint-Befunde.

Gesten-Nachtest vom 06.09.2026 auf demselben Gerät: Das rötliche Glas-× wurde an der laufenden
Umwandlung „Sons Of Hidden - Nirvana“ bei 53 % betätigt. Der Statusverlauf zeigte danach 59 %
und später einen erfolgreichen Abschluss; die Datei liegt im öffentlichen Audio-Ordner.
× im Startdialog, Wischen nach links/rechts, Zurückfedern einer kurzen Geste und neue Aufträge
nach einem ausgeblendeten Ergebnis sind geprüft. Die vorhandene Noma-MP3 wurde bei den
Kopiertests jeweils erkannt und übersprungen. Ein kalter App-Neustart lässt die ausgeblendete
Karte verborgen; das Zahnrad öffnet weiterhin den Verlauf mit dem fertigen Ergebnis.
Die Übersicht zeigt alle fünf öffentlichen Audiodateien einschließlich Nirvana. Der damals
installierte Debug-Build hatte 125 erfolgreiche App-Tests; Release-Lint meldete keine neuen Befunde.
Die unten stehende Veröffentlichungsmatrix bleibt zusätzlich erforderlich.

Löschtest vom 06.09.2026 auf demselben Samsung-Gerät: Eigens erzeugte kurze MP3-/Videodateien
wurden über die App in den öffentlichen Ordner kopiert. Einzel- und Mehrfachlöschung unter
`Audio`/`Video` sowie die gezielte Löschung einer SAF-Datei im bisherigen Stammordner sind
erfolgreich. „Alle auswählen“ erfasst den aktuellen Bereich; Abbrechen der Bestätigung erhält
alle Dateien. Bei der Einzelentfernung einer internen MP3 bleibt das Video desselben Titels
erhalten; die anschließende Mehrfachlöschung entfernt nur die ausgewählten internen Testdateien.
Alle Testdateien wurden über die App entfernt. Ein Vergleich der Dateinamen und SHA-256 vor/nach
dem Test bestätigt unveränderte 7 öffentliche Dateien, 5 interne Mediendateien und 9 Dateien im
Originalaudio-Cache. Das Register der gespeicherten MP3s/Videos entspricht exakt dem Ausgangsstand;
die Audio-Übersicht zeigt wieder die 5 vorhandenen Dateien. Die Entfernung eines internen
Media3-Originaldownloads wurde auf dem Gerät dabei nicht ausgeführt. Der abschließend installierte
Debug-Stand besteht alle 133 App-Tests und Release-Lint ohne neue Befunde. Nach kaltem App-Start
sind die blaue Auswahl, rote Entfernen-Aktion, Bestätigung aller 5 Audiodateien und Abbrechen
erneut geprüft; im AndroidRuntime-Log des Debug-Prozesses erscheint kein Absturz.

**Zusätzliche Debug-Abnahme für 1.2.0 am 06.09.2026:** Auf dem Samsung wurde der Sammelauftrag für sieben
bereits lokal verfügbare Titel als MP3 nach `KruXx-Downloads/Audio` während der Umwandlung
abgebrochen und danach über die Oberfläche wiederholt. Trotz zwischenzeitlichem Hintergrundwechsel
endete er bei 100 % mit zwei neuen und fünf bereits vorhandenen Dateien. Beide neuen Dateien sind
vollständig dekodierbar (320 kbit/s, 48 kHz, Stereo, korrekte ID3-Metadaten). Alle acht zuvor
erfassten öffentlichen und 16 privaten Mediendateien blieben per SHA-256 unverändert. Gespeichertes
Audio und Video wurden im Flugmodus mit deaktiviertem WLAN wiedergegeben; die ursprünglichen
Netzwerkeinstellungen sind wiederhergestellt. Der Test ersetzt keinen subjektiven Synchronitätstest.

Im frischen API-23-Emulator wurde außerdem ein normaler Netzwerkdownload einschließlich
Speicherberechtigungsdialog, nativer MP3-Umwandlung und öffentlichem Export vollständig ausgeführt:
„The Emptiness Machine“, 200,52 Sekunden, MP3 / 320 kbit/s / 48 kHz / Stereo, vollständig dekodierbar.
Dabei fiel ein ANR in der bisherigen Dateiprotokollierung auf, der vor dem Release-Build korrigiert
wurde. Erneuter Start und Bedienung sowie insgesamt 137 App-Tests und Release-Lint sind bestanden.
Der [IST-Stand](KRUXX-IST-STAND.md#release-kandidat-120-downloads-update) dokumentiert die Details.

Die API-23-Ergebnisse bleiben historische Nachweise. Sie ersetzen keinen praktischen Test auf der
neuen Untergrenze Android 7 / API 24 mit einer optimierten Release-APK.

Der [signierte Nachtest vom 07.09.2026](KRUXX-IST-STAND.md#signierte-geräteprüfung-vom-07092026)
ergänzt diese Prüfung: Die archivierte 1.2.0 aus `f195aed1b` erzeugt auf Samsung / Android 16
und im vollständig optimierten API-24-Emulator neue MP3-Dateien einschließlich öffentlichem Export.
Beide Dateien sind vollständig dekodierbar (48 kHz, Stereo, etwa 320 kbit/s, korrekte ID3-Tags).
Download und Wiedergabe funktionieren zusammen; auf API 24 endet der Export nach einem
Hintergrundwechsel. Auf beiden Geräten funktionieren lokale Wiedergabe und Vorspulen nach
App-Neustart ohne Standardnetz. Alle 13 vorher vorhandenen öffentlichen Samsung-Dateien bleiben
unverändert. Die unten aufgeführten übrigen Kombinationen sind dadurch nicht vollständig abgedeckt.

Abnahmematrix (oben belegte Fälle sind teilweise abgedeckt, übrige Kombinationen bleiben Nachtests):

| Fall | Erwartung |
|---|---|
| Einzeltrack, „Alle Tracks“, Album, Künstler, Playlist, Mehrfachauswahl | Ein Speicher-/Formatdialog je Auftrag; richtige Titelanzahl und Reihenfolge |
| Vorgabe merken, Neustart, zurücksetzen, automatischer Download | Vorgabe greift; ohne Vorgabe keine Hintergrunddialoge; Änderung wieder möglich |
| Video → MP3 und Video mit Ton | MP3 320, Ton vorhanden, Laufzeit vollständig, Bild/Ton synchron |
| Android 7 / API 24 und aktuelles Android; Mono/Stereo; AAC/Opus | Dekodierbarer Ton, korrekte Kanäle, keine Effekte oder Lautheitsänderung |
| Fertiges Video/MP3 im Flugmodus öffnen | Lokale Wiedergabe, Suche/Download-Kennzeichnung und Positionswechsel korrekt |
| Interner Ordner, SD, USB/FAT/exFAT | Dateien außerhalb der App abspielbar; Namen, Endungen und ID3 korrekt |
| Identische Kopie und gleiche Namen mit anderen Inhalten | Überspringen beziehungsweise neue Nummer; keine fremden Dateien überschrieben |
| Unvollständiger/entfernter Download beim Kopieren | Fehler ohne Netzwerkabruf oder irreführende fertige Datei |
| Einzel-/Mehrfachlöschung, alle auswählen, Bestätigung abbrechen | Nur bestätigte Dateien im angezeigten Speicher entfernt; andere Formate/Kopien bleiben erhalten |
| Löschung bei geänderter Datei, entzogenen Rechten oder Providerfehler | Keine falsche Erfolgsmeldung; übrige Auswahl bearbeitet, fehlgeschlagene Dateien sichtbar |
| Großer Auftrag, wenig Speicher, USB abziehen, Berechtigung entziehen | Speicher bleibt begrenzt; Fehler sichtbar; Wiederholung möglich |
| Auftrag abbrechen, Prozess beenden, App/Telefon neu starten | Wiederholung/WorkManager-Wiederaufnahme; eigene Teilkopien bereinigt; fertige Dateien bleiben |
| Lange Hintergrundaufträge auf Android 15/16 | Foreground-Fortschritt und OS-Abbruchverhalten prüfen; keine dauerhafte blockierte Warteschlange |

Die oben aufgeführten Geräteprüfungen decken einen Teil der Matrix ab. Ein physisches SD-/USB-Medium
war nicht angeschlossen; dessen Anbieter und die übrigen offenen Kombinationen benötigen weiterhin
eine praktische Abnahme. Der frühere 1.1.0-Gerätetest deckt diese Erweiterung nicht ab.
