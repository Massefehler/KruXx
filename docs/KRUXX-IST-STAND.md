# KruXx – aktueller IST-Stand

Stand: 04.09.2026 · maßgeblich für den lokalen Entwicklungsstand

Dieses Dokument trennt implementierte Funktionen, bereits nachgewiesene Tests und noch offene
Freigabeprüfungen. Architektur- und Wartungsdetails stehen im
[`KRUXX-ENTWICKLERHANDBUCH.md`](KRUXX-ENTWICKLERHANDBUCH.md).

## 1. Identität und Release-Status

| Merkmal | Aktueller Stand |
|---|---|
| Produkt | KruXx – The core of your music |
| Öffentlicher Release | `1.1.0` „Glass Update“ |
| Öffentlicher Android-Versionscode | `1_000_003` |
| Nächster Release | noch nicht versioniert; eigenständiger Podcast-Bereich als separates späteres Vorhaben |
| Release-Paket | `de.kruxx.music` |
| Debug-Paket | `de.kruxx.music.debug` |
| Android-Untergrenze | API 23 / Android 6.0 |
| Compile-/Target-SDK | API 36 / Android 16 |
| App-Repository | `Massefehler/KruXx` |
| Innertube-Spiegel | `Massefehler/KruXx-innertube` für den bewusst gepinnten Modul-Fix |
| Metrolist-Spiegel | `Massefehler/KruXx-metrolist` für den bewusst gepinnten Login-/Parser-Fix |
| Lizenz | GPL-3.0 für KruXx/Kreate; eingebundene Komponenten behalten ihre eigenen Lizenzen |

KruXx ist ein eigenständiger Fork. Es gibt keine automatische Übernahme neuer Kreate-Versionen und
keinen Push, Crashbericht oder Supportlink zum Kreate-Projekt. Kreate, RiMusic, Metrolist und weitere
Urheber bleiben entsprechend ihrer Beiträge genannt; diese Danksagung und Lizenzpflicht bedeutet
keine organisatorische Verbindung oder Mitverantwortung für KruXx.

Der Stand wurde vollständig automatisiert getestet, gelintet, gebaut, signiert und als `v1.1.0`
veröffentlicht. Das exakt archivierte Release-APK wurde als Upgrade über `1.0.2` installiert und
gestartet; die App-Daten blieben erhalten. Tag, Submodul-Pins, APK-Größe und APK-Digest wurden
anschließend noch einmal gegen den veröffentlichten Stand abgeglichen. Dadurch bleibt die APK
eindeutig ihrem Quellcode zugeordnet.

### In v1.0.2 veröffentlichte Änderungen

Die folgenden Änderungen gehören zum öffentlichen APK `1.0.2`:

- Die vorhandene Android-Auto-/MediaLibrary-Anbindung wurde gehärtet. Der Bibliothekswurzelknoten ist
  korrekt als browsbar markiert; Titel, Interpreten, Alben und Playlists liefern stabile,
  URI-kodierte IDs.
  Auch IDs mit reservierten Zeichen und ältere bereits ausgegebene Such-/Titel-IDs bleiben auflösbar.
- Ein in Android Auto ausgewählter Titel startet nun exakt in der sichtbar gewählten Liste und an der
  richtigen Position. Browse- und Wiedergabereihenfolge sind für Top-Titel, Favoriten, Cache,
  Downloads, Gerätetitel sowie eigene Playlists vereinheitlicht; ungültige oder inzwischen veraltete
  Einträge führen nicht mehr fälschlich zum ersten Titel (Index 0).
- Media3-Suche blockiert keinen Callback-Thread mehr und hält Ergebnisse getrennt pro Suchanfrage in
  einem begrenzten Cache. Lokale und YTM-Treffer werden zusammengeführt und dedupliziert; Androids
  Sprachbefehl über `requestMetadata.searchQuery` wird einschließlich leerer Anfrage unterstützt.
  Suchtexte werden nicht geloggt.
- `onGetItem`, modernes Paging und die aktuelle dreiparametrige Wiedergabe-Wiederaufnahme sind
  umgesetzt. Eine leere persistente Queue verursacht keinen Indexzugriff mehr; Metadatenabfragen zur
  Wiederaufnahme liefern genau einen Eintrag mit Fortschrittsstatus, echte Wiedergabe die volle Queue.
- Der Handy-Suchbutton, der eine Activity startet, wird Automotive-Controllern nicht mehr angeboten;
  die native Android-Auto-Suche bleibt verfügbar. Unbekannte Custom Commands melden einen Fehler und
  der Coroutine-Scope des Callbacks wird beim Dienstende beendet.
- Die zuvor gegensätzlichen Playlist-Quellen sind vereinheitlicht: Die YTM-Startsektion „From your
  Library“ wird um noch nicht enthaltene lokale Datenbanklisten ergänzt. Fehlt diese Kontosektion,
  erscheint ein eigener lokaler Playlist-Abschnitt. Der Bibliotheksreiter lädt bei aktivierter
  Konto-Synchronisation jetzt `FEmusic_liked_playlists` vollständig einschließlich Fortsetzungen
  statt nur die unvollständige Library-Landing-Seite. `VL`-Varianten derselben ID werden dedupliziert;
  der lokale Datensatz behält Editierbarkeit und Metadaten. Ein fehlgeschlagener Refresh löscht die
  letzte erfolgreiche Kontoantwort nicht mehr.
- Anmeldung, Abmeldung und der Schalter „Wiedergabelisten synchronisieren“ lösen im Bibliotheksreiter
  nun selbstständig denselben abbrechbaren Ladepfad aus wie Pull-to-refresh. Bei null sichtbaren
  Treffern erklärt die Ansicht, ob Suche/Filter, fehlende Anmeldung, ausgeschaltete Synchronisation,
  eine fehlgeschlagene Kontoabfrage oder eine wirklich leere Bibliothek ursächlich ist. Die früheren
  Rohdatenausgaben der YTM-Startsektionen wurden entfernt.
- Die automatische Updateprüfung startet erst bei sichtbarer Oberfläche und nutzbarem Netzwerk,
  reagiert auf eine später verfügbare Verbindung und erhält beim nächsten Vordergrundstart erneut
  eine Chance. Vorübergehende Netz-/Serverfehler werden zweimal mit begrenztem Abstand wiederholt;
  nur ein erfolgreicher, validierter GitHub-Abruf setzt das 24-Stunden-Intervall. Parallele Prüfungen
  und doppelte Update-Dialoge werden verhindert, die manuelle Prüfung bleibt unabhängig erzwingbar.
- Die Wiedergabe-Recovery behandelt abgelehnte signierte URLs mit HTTP 403/410/416 sofort, ohne zuvor
  dieselbe URL nach Media3s Standardabständen erneut anzufragen. Der Explicit-Hinweis aus dem
  `MediaItem` steht bereits beim allerersten Auflösen zur Verfügung. Playback-URLs sind nach der
  tatsächlichen Qualitäts-/Netzrichtlinie getrennt und werden bei Änderungen an Audioqualität oder
  Datensparen invalidiert; der eigenständige High-Quality-Downloadpfad bleibt davon unberührt.
  Playback- und Download-CDN-Abrufe verwenden eine vom HTTP-Logger befreite Kopie des gemeinsamen
  OkHttp-Clients. Proxy, DNS und funktionale Interzeptoren bleiben erhalten; Media3 erhält die ersten
  Streambytes dadurch sofort, statt im Debug-Build auf den vollständig protokollierten und vom CDN
  gedrosselten Response zu warten.

Die sechs gezielten JVM-/Robolectric-Tests für ID-Roundtrips, leere und zeichenreiche Suchanfragen,
Alt-ID-Kompatibilität, ungültige IDs und überlaufsicheres Paging sowie zehn Playlist-Merge-Tests,
zwei Konto-/Sync-Status-, vier Update-Policy- und neun Playback-Policy-Tests sind bestanden. Die
vollständige KruXx-App-Suite umfasst 88 Tests ohne Fehler oder übersprungene
Tests; auch `assembleKruxxUniversalProdDebug` und der vollständige Release-Lint ohne neue Befunde sind
erfolgreich. Die manuelle Kernabnahme der Playlist-Vereinigung war am 04.09.2026 auf einem Samsung
SM-S931B mit Android 16 erfolgreich: Nach Anmeldung und aktiviertem Sync waren alle erwarteten
YTM-Listen sowie eine lokale Testliste sichtbar. Der anschließend aus dem finalen Arbeitsstand
erzeugte Debug-Build wurde per ADB mit erhaltenen App-Daten installiert, startete ohne Crash oder ANR
und die Playlist-Darstellung wurde auf demselben Gerät erneut als funktionierend bestätigt. Auf
demselben Gerät wurde außerdem die allgemeine Startverzögerung jedes Online-Titels reproduziert und
mit dem bereinigten Medientransport behoben: Ein nicht gepufferter Titel wechselte nach 1,713 Sekunden
in `PLAYING`, ohne Playback-Fehler oder CDN-HTTP-Logger-Ausgabe. Die praktischen Playlist- und
Playback-Randfälle sowie Android Auto im Desktop Head Unit und anschließend in einem realen Fahrzeug
bleiben als ausdrücklich dokumentierte Nachtests offen.

### In v1.1.0 „Glass Update“ veröffentlichte Änderungen

KruXx `1.1.0` mit Android-Versionscode `1_000_003` ist als stabiler „Glass Update“-Release
veröffentlicht. Der englische, unveränderlich in die APK übernommene Changelog liegt unter
[`changelogs/kruxx/1.1.0.txt`](changelogs/kruxx/1.1.0.txt).

Der Release umfasst:

- „KruXx Glass“ als eigenständige visuelle Schicht für Startseite, Bibliotheksreiter, Vollbild- und
  Mini-Player, Einstellungen, Detailseiten, Dialoge, Menüs und Sheets;
- kompaktere Vorschlagskarten und Downloadbedienung, korrigierte „Top Artists“-Geometrie sowie
  sauber getrennte Ebenen von Vollbild-Player, Queue und Trackmenü;
- vollständig gerundete und ausgerichtete Einstellungsköpfe sowie platzsparende deutsche
  Navigationsbezeichnungen „Darstellung“ und „Playlists“;
- das neue, durchgängig erzeugte App-Icon und die freigestellte zehnteilige Startanimation mit
  kontinuierlichen 120-ms-Überblendungen auf dem KruXx-Hintergrund;
- die Korrektur des irreführenden Fehlerbanners beim Startseiten-Refresh und den wiederhergestellten
  optionalen Schutz vor Benachrichtigungstönen und Heads-up-Pop-ups während aktiver Wiedergabe.

Der geplante eigenständige Podcast-Bereich ist ausdrücklich **nicht** Teil von `1.1.0`; er bleibt ein
separates späteres Funktionsvorhaben.

## 2. Aktueller Funktionsumfang

### Eigenständigkeit, Updates und Datenschutz

- Eigene Paket-ID, Versionierung, Signatur, GitHub-Quelle und eigener Updatekanal.
- Der Updater akzeptiert nur stabile Releases aus `Massefehler/KruXx`, den exakten APK-Namen und eine
  höhere Version. Vor dem Android-Installer prüft er GitHubs SHA-256-Digest, APK-Paket, Version und den
  fest gepinnten KruXx-Signaturfingerabdruck.
- Eine Installation aus der alten Linie `2.2.3-kruxx.x` benötigt einmalig eine manuelle, korrekt
  signierte 1.x-APK (`1.0.0` oder neuer). Paket und Schlüssel bleiben gleich, daher aktualisiert
  Android ohne Datenlöschung; anschließend steht der KruXx-Updater zur Verfügung.
- Automatische Prüfungen laufen nach einem erfolgreichen Abruf höchstens einmal in 24 Stunden und
  lassen sich auf „nachfragen“, automatisch oder aus stellen. Sie warten im Vordergrund auf eine
  nutzbare Verbindung und wiederholen vorübergehende Netz-/Serverfehler begrenzt; manuelle Prüfungen
  umgehen das Intervall.
- KruXx sendet keine Telemetrie und keinen Absturzbericht. Ein Crashlog wird nur lokal für einen
  ausdrücklich vom Nutzer ausgelösten Export aufbewahrt; der Kreate-CrashReport-Dialog ist im
  KruXx-Flavor deaktiviert.
- Release-Note-Dateien ohne verwertbare Abschnitte können den App-Start nicht mehr durch einen leeren
  Changelog-Pager zum Absturz bringen.

### Startseite und Konto-Inhalte

- Neue und migrierte Standardinstallationen öffnen „Startseite/Quick Picks“ statt „Titel“. Eine später
  vom Nutzer gewählte andere Startseite bleibt erhalten.
- Der Header zeigt die einzeilige Wortmarke „KruXx – The core of your music“; „KruXx“ ist größer und
  fett, der Zusatz kleiner und regulär gesetzt.
- „Vorschläge“ bildet aus Ausgangstitel, Radio- und Related-Ergebnissen eine deduplizierte Liste mit
  insgesamt bis zu 18 Titeln. Je nach Menge werden ein bis drei gleichmäßig dimensionierte Reihen
  angezeigt; der Ladeindikator bleibt nur sichtbar, solange noch keine brauchbare Liste vorhanden ist.
  Die Wiedergabetaste startet die gesamte Vorschlagsliste.
- „Top Artists“ ist antippbar und öffnet die zugehörige YTM-Interpretenseite.
- Angemeldete Bereiche der Startseite verwenden die aktuelle YTM-Sitzung.
- „From your Library“ enthält die von YTM gelieferte Kontosektion plus alle dort noch fehlenden
  lokalen Datenbanklisten; ohne Kontosektion erscheint ein eigener lokaler Playlist-Abschnitt. Der
  Bibliotheksreiter vereinigt umgekehrt bei aktivierter Synchronisation die vollständige, paginierte
  YTM-/YT-Playlist-Bibliothek mit den lokalen Listen. Gleiche Browse-IDs mit und ohne `VL` werden nur
  einmal angezeigt, wobei der lokale Datensatz editierbar bleibt. Reine Online-Einträge öffnen die
  angemeldete YTM-Seite samt Fortsetzungen; öffentliche/anonyme Listen bleiben beim bisherigen
  leichten Browse-Modul. Login- und Sync-Wechsel laden den Kontoanteil automatisch neu; ein leerer
  Reiter zeigt die konkrete Ursache statt nur einer leeren Fläche.
- Die Interpreten-Synchronisation toleriert YTM-Einträge ohne Thumbnail-Liste. Online- und lokale
  Einträge werden anhand der ID zusammengeführt, ohne Dubletten angezeigt und anschließend gemäß der
  gewählten Sortierung geordnet. Bei „Titel / aufsteigend“ ist die Gesamtliste alphabetisch.

### Suche und Spracheingabe

- Bei leerem Suchfeld steht auf Android 12 oder neuer eine lokale Spracheingabe zur Verfügung, sofern
  das Gerät einen On-Device-Spracherkenner und das benötigte Sprachmodell anbietet.
- KruXx verwendet keinen Cloud-Fallback. Die Aufnahme startet erst nach Tippen und Berechtigungsfreigabe;
  das erkannte Ergebnis wird nur in das bearbeitbare Suchfeld eingesetzt und nicht automatisch
  abgeschickt.
- Der Reiter „Titel“ fragt YTM-Titel und abspielbare Video-/UGC-Treffer parallel ab, erhält die
  originale Titelrangfolge, ergänzt fehlende Video-IDs und entfernt Dubletten.
- Sprache, Region sowie Konto-/Anonym-Modus sind Bestandteil des Suchkontexts und Cache-Schlüssels.
  Eine fehlerhafte angemeldete Anfrage wird einmal anonym wiederholt.
- Das verbessert die Trefferabdeckung deutlich, kann aber YTMs serverseitige, personalisierte
  Rangfolge und regionale Freigaben nicht vollständig nachbilden.

### Wiedergabe, Musikvideo und schwebender Player

- Audio-Streams werden über InnerTubeX aufgelöst. Abgelaufene oder abgelehnte URLs sowie unvereinbare
  Cache-Formate werden begrenzt neu aufgelöst, ohne einen Song an Position null festzuhalten.
- HTTP 403/410/416 lösen sofort die frische Auflösung aus. Beim ersten Start eines expliziten Titels
  kommt dessen Hinweis direkt aus dem `MediaItem`; ein noch laufender Datenbank-Upsert ist nicht mehr
  relevant. Ein Wechsel von Audioqualität oder Datensparrichtlinie verwirft alte Playback-URLs,
  während Downloads weiterhin unabhängig in höchster kompatibler Qualität auflösen.
- Für den eigentlichen CDN-Transport werden `HttpLoggingInterceptor` aus den Application- und
  Network-Interceptor-Ketten entfernt. Das verhindert im Debug-Build das vollständige Puffern des
  gedrosselten Responses vor Media3 und schützt zugleich signierte Medien-URLs vor HTTP-Logs.
- Direkt ausgewählte YTM-Treffer vom Typ offizielles Musikvideo (`OMV`) oder Nutzer-Video (`UGC`)
  öffnen einen eingebetteten 16:9-Player. Art Tracks (`ATV`) mit Standbild und unbekannte Typen bleiben
  in der ressourcenschonenden Audiowiedergabe.
- Video-zu-Audio-Wechsel und der automatische Audio-Rückfall bei nicht einbettbaren Videos behalten
  die Position bei. Warteschlange, „Als Nächstes“ und Kontextmenüs starten niemals ungefragt Video.
- Bei neuen App-Daten zeigt der Vollbild-Player standardmäßig das vollständige Cover als eigene
  Vordergrundfläche vor einem coverbasierten, unscharfen Hintergrund. Technisch wird dies durch
  `PLAYER_SHOW_THUMBNAIL=true`, `PLAYER_BACKGROUND=BlurredCoverColor` und
  `PLAYER_BACKGROUND_BLUR=true`.
- Ein Update setzt diese Darstellung nicht zwangsweise zurück, sondern erhält die bisherige
  Nutzerauswahl. „Cover im Player anzeigen“ und ein Doppeltipp auf den Player-Hintergrund können das
  separate Cover ein- oder ausblenden; Stable- und Debug-Paket besitzen getrennte Einstellungen.
- Unter **Einstellungen → Darstellung** kann „Schwebenden Player erlauben“ aktiviert werden.
  Die Unteroption „Beim Verlassen automatisch öffnen“ ist standardmäßig an. PiP arbeitet ab Android 7,
  benötigt einen aktuellen Media-Eintrag und unterliegt zusätzlich den PiP-/Fensterregeln des Geräts.
- „Miniaturansicht/Thumbnail im Player“ und Androids schwebender Player sind getrennte Funktionen:
  Die Thumbnail-Einstellung steuert das Cover im großen Player, nicht das PiP-Fenster.
- Alle Media3-Player-Zugriffe des Wiedergabediensts, einschließlich des optionalen
  Benachrichtigungsschutzes, laufen auf dem Android-Hauptthread. Damit ist der zuletzt aufgetretene
  Startabsturz durch einen Player-Zugriff vom IO-Thread behoben.

### Audio und Benachrichtigungen

- KruXx enthält keinen Mono-Downmix, keine Pan-/Balance-Stufe und kein Channel Mapping. Die vom Decoder
  gemeldete Kanalzahl erscheint in „Stats for Nerds“ als Mono oder Stereo.
- Die Quelle, Androids Bedienungshilfe „Mono-Audio“, Geräte-EQ, Bluetooth-Profil, Empfänger oder Adapter
  können ein Stereosignal nach dem App-Decoder dennoch zusammenführen.
- Optional kann unter **Einstellungen → Wiedergabe** die Stummschaltung fremder
  Benachrichtigungstöne aktiviert werden. Sie benötigt den einmalig vom Nutzer vergebenen
  „Nicht stören“-Zugriff und gilt nur während tatsächlich laufender Audio- oder Videowiedergabe.
- Heads-up-Pop-ups und Benachrichtigungstöne anderer Apps werden dabei unterdrückt; die Einträge
  bleiben im Benachrichtigungsbereich sichtbar. Medien, Wecker und Anrufe bleiben hörbar. Pause,
  Stopp, Dienstende, nächster App-Start und lokaler Crash-Wiederanlauf stellen den vorherigen Zustand
  wieder her; ein bereits vor KruXx aktiver „Nicht stören“-Modus wird nicht abgeschaltet.
- Release- und Debug-Paket sind für Android zwei eigenständige Apps. Schalterzustand und besonderer
  „Nicht stören“-Zugriff werden deshalb nicht von `de.kruxx.music` auf
  `de.kruxx.music.debug` oder umgekehrt übertragen.

### Downloads

- Einzel- und Massendownloads verwenden denselben dedizierten Download-Resolver. Die vollständige
  geladene Songliste wird geordnet eingereiht; ein Fehler bei einem Titel stoppt nicht die restliche
  Queue.
- Bis zu fünf Audiodateien werden parallel übertragen. Weitere Titel bleiben sichtbar als „wartend“
  markiert; wartende und laufende Einträge lassen sich abbrechen. Nebenabrufe wie Liedtexte sind auf
  einen gleichzeitigen Vorgang begrenzt.
- Downloads fordern unabhängig von Wiedergabequalität, WLAN, LTE/5G oder Androids Kennzeichnung als
  getaktetes Netz immer InnerTubeX `HIGH` an. Das bedeutet die höchste aktuell angebotene und mit dem
  direkten KruXx-Pfad kompatible YTM-Audioqualität, nicht Lossless-Garantie oder identische Bitrate
  für jeden Titel.
- Exakte Inhaltslänge und begrenzte HTTP-Range-Anfragen verbessern Fortschrittsanzeige und CDN-Abruf.
  HTTP 403/410/416 invalidiert nur die betreffende Download-URL, damit der nächste Versuch frisch
  auflöst. Unvereinbare Teilstücke aus älteren Qualitätsrichtlinien werden nicht vermischt.
- Fünf Verbindungen garantieren keinen bestimmten Durchsatz: YTM/CDN-Drosselung, Serverwahl,
  Funkqualität, Energiesparmodi und parallele Systemlast bleiben außerhalb der App kontrolliert.

### Branding

- Nur bei einem kalten App-Start liegt ein animierter KruXx-Vollbildscreen über der bereits
  komponierenden Oberfläche. Zehn Frames à 120 ms laufen in 2,4 Sekunden exakt zweimal durch;
  anschließend folgt eine kurze 420-ms-Ausblendung. Die freigestellten RGBA-Quellen werden als
  transparente 600²-WebPs ausgeliefert: Frame 1 steht sofort, jeder spätere Wechsel löst beide
  Bilder über die vollständigen 120 ms linear und damit ohne Haltepunkt ineinander auf.
- Das freigestellte Quellbild `icons/KruXx_App_Icon.png` wurde über die KruXx-Icon-Pipeline in adaptive,
  runde und klassische Launcher-Icons, Themed Icon, Benachrichtigungssymbol, TV-Banner sowie
  Cover-Platzhalter überführt.
- Das Icon muss nach jeder Änderung des Quellbilds erneut generiert und anschließend in einer neu
  gebauten APK auf Gerät, Launcher, Benachrichtigung und Android TV geprüft werden.

## 3. Prüfstand vom 04.09.2026

### Öffentlicher Release 1.1.0

| Bereich | Nachweis | Status |
|---|---|---|
| Version und Release Notes | Paketversion `1.1.0`/`1000003`; englischer Quell-Changelog, generierte Ressource und optimierte Raw-Ressource im Release-APK bytegleich | bestanden |
| KruXx-App-Unit-Tests | 88 Tests, 0 Fehler, 0 übersprungen | bestanden |
| `modules/innertube` | 58 Tests, 0 Fehler, 0 übersprungen | bestanden |
| `modules/metrolist` | Root-Integration kompiliert; das Modul besitzt keine eigenen Tests (`NO-SOURCE`) | bestanden |
| Release-Lint | vollständiges `lintKruxxUniversalProdRelease`; nach Korrektur der neuen Ressourcen keine neuen Befunde gegenüber der versionierten Baseline | bestanden |
| Release-Artefakte | Debug- und signierte Release-Variante aus demselben sauberen Commit gebaut; ZIP-Integrität, Paketmetadaten, KruXx-Zertifikat und Signaturschemata v1/v2/v3 geprüft | bestanden |
| Debug-Geräteinstallation | `de.kruxx.music.debug` per ADB mit Datenerhalt auf `1.1.0`/`1000003` aktualisiert; bestätigter Kaltstart in 1.238 ms, laufender Prozess und kein Fatal-/ANR-Treffer im prozessgefilterten Startlog | auf Gerät bestanden |
| Signierte Update-Installation | Exakt archiviertes Release-APK über die stabile App 1.0.2 installiert; ursprünglicher Installationszeitpunkt erhalten, `1.1.0`/`1000003`, Kaltstart in 489 ms und kein Fatal-/ANR-Treffer | auf Gerät bestanden |
| Release-Provenienz | Signiertes APK mit Quellrevision `39398cc4684c91a7737da36af85f34232606f52d`; annotierter Tag `v1.1.0`, öffentlicher Latest-Release, erneut heruntergeladener Asset bytegleich | veröffentlicht und geprüft |
| Vollbild-Player-Basisdarstellung | Quellvorgaben und getrennte Debug-Frischinstallation zeigen vollständiges Cover vor unscharfem Coverhintergrund. In der aktualisierten stabilen Installation wurde ein gespeicherter „Cover aus“-Zustand als Ursache erkannt; nach dem Wiedereinschalten blieb das Cover beim Verkleinern und erneuten Öffnen erhalten | Sollzustand bestätigt; keine Artefaktabweichung |

Die finalen Dateien `KruXx-1.1.0-release.apk` und `KruXx-1.1.0-debug.apk` liegen im lokalen
Archivverzeichnis `/home/kruxx/Schreibtisch/Android/Kreate-APKs/`. Das Release-APK ist 22.883.301
Bytes groß und besitzt SHA-256
`8c1f57bf4bb2f4e41b12fc0ceb024594a1784ef0843ef8b6831ccbdbd13aa741`.

Der öffentliche [GitHub-Release v1.1.0](https://github.com/Massefehler/KruXx/releases/tag/v1.1.0)
zeigt über den annotierten Tag auf den geprüften Release-Commit. Sein einziges APK-Asset ist
bytegleich mit dem lokal installierten Archiv; GitHub-Asset-Digest, erneuter Download und Bytegröße
wurden nach der Veröffentlichung verifiziert.

### Öffentlicher Release 1.0.2

| Bereich | Nachweis | Status |
|---|---|---|
| KruXx-App-Unit-Tests | 88 Tests, 0 Fehler, 0 übersprungen | bestanden |
| `modules/innertube` | 58 Tests einschließlich aktueller Charts-Antwort und Auth-Header-Weitergabe für Browse/Next, 0 Fehler | bestanden |
| `modules/metrolist` | Root-Integration kompiliert; das Modul besitzt in diesem Stand keine eigenen Tests (`NO-SOURCE`) | bestanden |
| Release-Lint | vollständiges `lintKruxxUniversalProdRelease`; keine neuen Befunde gegenüber der versionierten Baseline | bestanden |
| Release-APK | Paket `de.kruxx.music`, Version `1.0.2`/`1000002`, erwartete Signatur und Quellrevision `d679b4ccf8c75024032835437bbcd1af70e97c9f` | geprüft |
| Update-Installation | Exakt archiviertes 1.0.2-Release-APK per ADB über 1.0.1 installiert, App-Daten erhalten | bestanden |
| App-Start | Von ADB bestätigter Start nach Installation; laufender Vordergrundprozess und kein neuer Crash-/ANR-Eintrag | auf Gerät bestanden |
| Breiter Nutzertest | Navigation und zentrale Funktionen auf dem Zielgerät ohne beobachtete Fehler; Downloads ausdrücklich mit sehr hohem Durchsatz bestätigt | auf Gerät bestanden |
| Reguläre Konto-Playlists | Fünf Listen geöffnet, darunter 4 sowie 52 nutzbare Songs (54 YTM-Einträge einschließlich zwei nicht abspielbarer); Titel und vorhandene Songzeilen sichtbar, kein 401/`UNAUTHENTICATED` | auf Gerät bestanden |
| „Neue Folgen“ (`RDPN`) | Authentifizierter Browse antwortet, verwendet aber ein Podcast-Multirow-Format außerhalb des Musik-Playlist-Parsers | für den künftigen Podcast-Bereich vorgemerkt; kein isolierter 1.0.2-Parserpatch |
| Interpreten aktualisieren | zweimal Pull-to-refresh, kein rotes Fehlerbanner | auf Gerät bestanden |
| Interpreten sortieren | neun sichtbare Einträge bei „Titel / aufsteigend“ alphabetisch | auf Gerät bestanden |
| Suche/Sprache/Video | Parser-, Kontext-, Fallback- und Klassifikations-Unit-Tests | automatisiert bestanden; im allgemeinen Gerätetest keine Auffälligkeit gemeldet |
| Downloads | Queue-/Statuslogik automatisiert, Resolver und Build geprüft; Downloads auf Gerät schnell und fehlerfrei | bestanden |
| PiP | Implementierung für Android 7–11 und 12+ geprüft | kein bekannter Fehler; Einzelmatrix nicht separat protokolliert |
| Benachrichtigungsschutz | Lifecycle-/Wiederherstellungslogik und Hauptthread-Fix geprüft | kein bekannter Fehler; Fremd-App-Ton nicht separat protokolliert |
| Stereo | Kein Downmix im Audiopfad; Decoderdiagnose vorhanden | kein bekannter Fehler; Links-/Rechts-Hörtest nicht separat protokolliert |

Die finalen Dateien heißen `KruXx-1.0.2-release.apk` und `KruXx-1.0.2-debug.apk` und werden unter
`/home/kruxx/Schreibtisch/Android/Kreate-APKs/` archiviert. Ein normaler Lauf von
`scripts/build-local-release.sh kruxx` erzeugt beide aus demselben Quellbaum, signiert das Release
mit v1/v2/v3 und prüft Paket, Version, Zertifikat sowie die von AGP in
`META-INF/version-control-info.textproto` eingebettete Git-Revision.

Der finale SHA-256-Wert wird bewusst **erst nach dem Build aus dem endgültigen sauberen
Release-Commit** in der GitHub-Release-Beschreibung und als GitHub-Asset-Digest festgehalten: Die
eingebettete Git-Revision ändert das APK bei jedem neuen Commit, deshalb könnte ein Hash im selben
Commit nie sich selbst korrekt beschreiben. Der für 1.0.2 gebaute Release-Kandidat erfüllte sämtliche
Metadaten- und Signaturprüfungen. Das exakt archivierte Release-APK wurde anschließend per ADB als
Update über 1.0.1 installiert und gestartet; App-Daten blieben erhalten, `MainActivity` lief im
Vordergrund und der neue Crash-/ANR-Logfilter blieb leer.

Der öffentliche [GitHub-Release v1.0.2](https://github.com/Massefehler/KruXx/releases/tag/v1.0.2)
zeigt auf den geprüften Commit `d679b4ccf8c75024032835437bbcd1af70e97c9f`. Sein einziges
APK-Asset ist bytegleich mit dem lokal getesteten Archiv; GitHub-Asset-Digest
`sha256:ed142f16c17f14c815f8882163fed7119eb3aab233e87655e59e4feb5a0f231d`, erneuter Download
und Dateigröße von 22.666.213 Bytes wurden nach der Veröffentlichung verifiziert.

## 4. Bekannte Grenzen und Freigabekriterien

- **Android Auto ist seit 1.0.2 technisch überarbeitet, aber noch nicht vollständig praktisch
  abgenommen:** Als Nachtest müssen Browse-Baum, Auswahlposition und Queue,
  Sprachsuche (konkret und leer), Media-Buttons sowie Wiederaufnahme im Desktop Head Unit und in einem
  realen Fahrzeug geprüft werden. Bis dahin ist „perfekte Steuerung“ kein bestätigter Status.
- **Die gemeldete Playlist-Lücke ist mit dem betroffenen Konto und dem finalen Arbeitsstand praktisch
  bestätigt behoben:** Auf einem Samsung SM-S931B mit Android 16 lud der Debug-Build nach Anmeldung
  und aktiviertem Sync alle erwarteten YTM-Listen; eine anschließend angelegte lokale Testliste
  erschien ebenfalls. Nach Installation des finalen Debug-Artefakts mit Datenerhalt wurden beide
  Quellen erneut als funktionierend bestätigt. Zehn automatisierte Merge-Tests decken außerdem
  ID-Dubletten, lokale Priorität, reine Cloud-/Local-Fälle, Songzahlangaben und Sortierung ab. Als
  Nachtests bleiben insbesondere Filter, automatische Login-/Sync-Wechsel und Fehler-Refresh offen.
  Der Kontoschalter „Wiedergabelisten synchronisieren“ bleibt für den YTM-Anteil des
  Bibliotheksreiters maßgeblich.
- **Die Wiedergabe-Härtung ist technisch und automatisiert abgeschlossen; der allgemeine Online-
  Erststart ist auf dem Gerät bestanden:** Neun Policy-Tests sichern die sofortige 403/410/416-
  Recovery, verschachtelte HTTP-Fehler, Fehlerklassifikation, Ablaufgrenze, Explicit-Hint und die
  Trennung des Media-Transports vom HTTP-Logger. Ein nicht gepufferter Online-Titel erreichte auf dem
  Samsung-Testgerät nach 1,713 Sekunden `PLAYING`. In der erweiterten Gerätematrix bleiben der
  explizite Erststart, Qualitäts-/Datensparwechsel, Offline-Cache und ein gefilterter
  Recovery-Loglauf praktisch zu prüfen.
- **Die automatische Updateprüfung ist technisch gehärtet, aber noch praktisch freizugeben:** Auf
  einem zweiten Gerät erschien nach frischer Installation von `1.0.0` und anschließendem App-Start
  kein automatischer Hinweis auf das bereits
  veröffentlichte `1.0.1`. Version 1.0.2 behebt die dabei erkannten Schwächen:
  foreground- und netzgebundener Start, erneuter Versuch nach Netzrückkehr beziehungsweise neuem
  Vordergrundstart, zwei begrenzte Retries für vorübergehende Fehler, keine doppelten Dialoge und ein
  24-Stunden-Zeitstempel erst nach erfolgreicher Antwortprüfung. Nach Veröffentlichung bleibt der
  echte Erkennungstest von der vorherigen signierten Version offen – einmal online beim Kaltstart und
  einmal offline gestartet mit anschließend hergestellter Verbindung, jeweils ohne „Jetzt
  überprüfen“ anzutippen. Debug-Builds deaktivieren den Self-Updater und können diesen Nachweis nicht
  ersetzen.
- YTM, GitHub, Metadaten- und Liedtextdienste sind externe Dienste. Antwortformate, regionale
  Verfügbarkeit, Kontoversuche und CDN-Tempo können sich ohne App-Update ändern.
- Die Podcast-Autoplaylist „Neue Folgen“ (`RDPN`) ist keine reguläre Musik-Playlist. Ihr
  Multirow-Antwortformat wird vom derzeitigen Playlist-Parser nicht abgebildet. Sie soll nicht als
  Einzelfall in den Musik-Playlistpfad gedrückt, sondern später Bestandteil eines eigenständigen
  Podcast-Bereichs mit Episodenstatus und Wiederaufnahme werden.
- Der öffentliche Release `1.1.0` enthält die beiden Umsetzungsstufen des
  [„KruXx Glass“-Redesigns](Design.md):
  zentrale KruXx-Designbausteine, Graphit-Hintergrund mit rot-blauer Lichtaura, halbtransparenter
  Header, schwebende untere Navigation, Mini-Player und leichte Karten der Startansicht. Auch die
  Kaltstartanimation verwendet jetzt diesen gemeinsamen Hintergrund. Sämtliche Logo-Quellen und
  Runtime-Frames sind echt freigestellt; durchgehende 120-ms-Überblendungen sowie eine ruhigere
  Skalierungs- und Leuchtbewegung verbinden die zehn Zustände ohne schwarze Außenflächen. Zusätzlich
  nutzen nun die Steuerung und Aktionsleiste des Vollbild-Players, Einstellungen und ihre
  Unterseiten, die zentralen Dialoge, Dropdowns, Menüs und Bottom-Sheets sowie die gemeinsamen
  Gerüste geöffneter Rubriken dieselbe Designsprache. Auch das Queue-/Playlist-Sheet des
  Vollbild-Players lässt seine Glasfläche nicht mehr durch getrennte interne Listen-, Such- oder
  Fußleistenflächen verschwinden. Eine deckende Basis unter der gemeinsamen Glasgestaltung
  verhindert zugleich, dass der Vollbild-Player durch die Queue hindurchscheint.
  Das Drei-Punkte-Trackmenü des Vollbild-Players nutzt unabhängig davon eine nur leicht deckendere
  Glasbasis (Alpha 0,08); die Anpassung gilt gezielt für Listen-, Raster- und Playlist-Unteransicht
  dieses Menüs und verändert keine anderen Dropdowns oder Sheets. Die vier unteren
  Bibliotheks-Reiter „Titel“, „Interpreten“,
  „Alben“ und „Playlists“ besitzen nun einheitliche gläserne Werkzeugleisten und Suchfelder,
  transparente Filterchips und leichte Inhaltskarten. Die deutsche Hauptreiter-Beschriftung
  „Playlists“ hält alle vorhandenen Reiter vollständig sichtbar und schafft Breitenreserve für den
  geplanten Podcast-Reiter. Die bereits horizontal scrollbar ausgelegte Navigation soll erst bei
  tatsächlich gemessenem Überlauf seitliche `<<`-/`>>`-Hinweise erhalten: jeweils nur dort, wo
  weitere Reiter verborgen sind, beide zwischen den Scrollgrenzen und keinen, solange sämtliche
  Reiter passen. Diese Kennzeichnung ist vorgemerkt und derzeit bewusst nicht aktiv. Der
  Vorschlags-Downloadbutton beansprucht
  nicht mehr die Titelzeile. Vorschläge und „Top Artists“ sind im Hochformat auf rund 65 Prozent der
  Inhaltsbreite und damit auf das Maß von „Forgotten favorites“ abgestimmt; „Top Artists“ berechnet
  seine zweizeilige Höhe aus dem tatsächlich dargestellten Song-Thumbnail samt Innen- und
  Zeilenabstand. Das Vollbild-Player-Sheet besitzt eine undurchsichtige Unterlage, sodass die
  Startseiten-Kopfzeile nicht hinter seinen Bedienelementen durchscheint. Die Schicht
  ist auf den unabhängigen KruXx-Build begrenzt; Navigation, Screen-Zuschnitt, Datenfluss,
  ViewModels, Wiedergabe, Bibliothekslogik und Android Auto bleiben strukturell und funktional
  unverändert. Sämtliche gemeinsamen Abschnittsköpfe der Einstellungsreiter und -Untermenüs sind
  nun an allen vier Ecken mit 14 dp gerundet; Überschrift und optionaler Untertitel stehen vertikal
  zentriert und fluchten mit den Einstellungseinträgen. Die KruXx-Debug-APK sowie der gemeinsame
  Kreate-/GitHub-Build wurden erfolgreich gebaut; die aktuelle KruXx-Debug-Suite bestand 88 von 88
  Unit-Tests. Auf dem Samsung-Zielgerät
  SM-S931B mit Android 16 bestanden Installation, Start, Tabwechsel, beide Playlist-Reiter mit
  jeweils 11 sichtbaren lokalen beziehungsweise YT-/YTM-Listen sowie Online-Wiedergabe und
  Pause/Fortsetzen über Mini- und Vollbild-Player ohne Crash. Vollbild-Player, Einstellungsseite und
  -Unterseite, Auswahldialog, Kopfzeilen-Dropdown, geöffnete Albumseite und Titel-Bottom-Sheet
  bestanden den aktuellen Gerätecheck. Die korrigierten Abschnittsköpfe wurden zusätzlich in
  „Allgemein“, „UI“, „Darstellung“, „Daten“ und „Sonstiges“ einschließlich ihres angehefteten
  Scrollzustands geprüft. Ein kurzer Startseiten-Scrolltest ergab 5 von 675
  als ruckelig bewertete Frames (0,74 %), ein 95. Perzentil von 14 ms und keine verpassten
  VSync-Ereignisse. KruXx 1.1.0 verwendet auf allen Versionen den performanten
  Transparenz-/Farbverlauf-/Kontur-Fallback ohne Blur pro Listenelement; nur der Vollbild-Player
  verwendet weiterhin seine vorhandene echte Cover-Unschärfe. Bei neuen App-Daten ist dabei das
  vollständige Cover vor dem unscharfen Coverhintergrund sichtbar. Ein Update erhält eine zuvor
  abweichend gespeicherte Auswahl; das nach Veröffentlichung gemeldete fehlende separate Cover war
  auf dem Zielgerät genau ein solcher Zustand und kein Unterschied zwischen Quellstand und GitHub-APK.
  Nach dem Wiedereinschalten blieb die Basisdarstellung beim Verkleinern und erneuten Öffnen erhalten.
  Als erweiterte Nachtests nach der Veröffentlichung bleiben Hellmodus, große Systemschrift,
  API 23 bis 30, breitere
  Performanceprüfung sowie echter Backdrop-Blur für weitere geeignete ruhende Oberflächen offen.
  Die zu großen Vorschlagskarten, die überhöhten und überlappenden „Top Artists“-Zeilen sowie das
  Durchscheinen der Startseiten-Kopfzeile hinter dem Vollbild-Player wurden auf dem Zielgerät
  reproduziert. Die Korrekturen kompilieren und sind automatisiert geprüft; „Top Artists“ und die
  Abgrenzung des Vollbild-Players wurden mit der neu gebauten APK direkt nachgeprüft. Im
  abschließenden Sichttest wurde auch die korrigierte Breite der Vorschlagskarten bestätigt.
  Queue-Sheet einschließlich
  Suche, Mini-Player und Werkzeugleiste sowie die ergänzten Flächen aller vier unteren
  Bibliotheks-Reiter wurden dagegen auf dem Samsung SM-S931B direkt geprüft. Elf YT-/YTM-Playlists
  waren sichtbar; Wiedergabe sowie Öffnen, Durchsuchen und Schließen der Queue funktionierten ohne
  Absturz oder ANR. Nach der Korrektur war der darunterliegende Vollbild-Player in der geöffneten
  Queue nicht mehr sichtbar.
  Die Hintergrundintegration der Startdarstellung wurde zuvor bei echten Kaltstarts mit frühem und
  spätem Animations-Screenshot geprüft. Die am 04.09.2026 ersetzte, echt freigestellte
  Frame-Serie und das neue App-Icon sind als Quell- und Runtime-Ressourcen auf Alpha, Abmessungen und
  Reihenfolge geprüft; die Debug-APK baut erfolgreich und enthält alle zehn transparenten WebPs.
  Nach wiederhergestellter ADB-Verbindung wurde die aktuelle Debug-APK erneut installiert und
  geöffnet; der sichtbare Gesamtstand einschließlich der leicht erhöhten Deckung des
  Drei-Punkte-Trackmenüs wurde auf dem Zielgerät bestätigt.
  Pull-to-refresh auf der Startseite wurde mit dem danach korrigierten Debug-Build zweimal direkt
  auf demselben Zielgerät ausgeführt. Die Vorschläge wurden neu aufgebaut, ohne das zuvor sichtbare
  rote Banner „Error occurs! Please submit report …“. Der Preference-Hintergrundthreadfehler und die
  irreführende Protokollierung regulärer Effekt-Abbrüche traten nicht mehr auf; Prozess, Crash-/ANR-
  und AndroidRuntime-Prüfung blieben unauffällig.
- Die Spracheingabe fehlt auf Android 11 und älter sowie auf Geräten ohne lokalen Erkenner/Sprachmodell.
- Ein erkannter OMV-/UGC-Typ garantiert keine Einbettung; Rechteinhaber, Region, Altersschutz oder
  WebView können Video verhindern. Dann ist Audio der beabsichtigte Rückfall.
- PiP kann vom Android-/Hersteller-System global deaktiviert oder eingeschränkt sein. Ohne aktiven
  Titel öffnet KruXx bewusst kein leeres schwebendes Fenster.
- Der Benachrichtigungsschutz kann nur Android-Benachrichtigungstöne und Heads-up-Pop-ups über die
  Notification Policy steuern. Er blockiert weder Einträge im Benachrichtigungsbereich noch Medien,
  Wecker oder Anrufe und umgeht keine Gerätevorgaben.
- Für den öffentlichen `v1.1.0` wurden geprüfter Quellstand, beide Submodul-Commits und das passende
  Tag veröffentlicht und exakt das lokal geprüfte Archiv-APK an den GitHub-Release gehängt. Die
  nicht separat protokollierten Detailfälle in der Tabelle bleiben Bestandteil der
  Regressionstest-Matrix, sind aber derzeit keine bekannten Defekte.

## 5. Sicherheit und GitHub-Secret-Scanning

- Die GitHub-API meldete am 03.09.2026 nach der Veröffentlichung für `Massefehler/KruXx`,
  `Massefehler/KruXx-innertube` und `Massefehler/KruXx-metrolist` jeweils **0 offene
  Secret-Scanning-Alarme**. Sechs Metrolist-Funde sind bewusst öffentliche, für den
  Innertube-/PoToken-Abruf erforderliche YouTube-Clientschlüssel. Ein siebter Fund lag in einem alten,
  im aktuellen Baum gelöschten Upstream-Core-Dump; der dort erkannte kurzlebige
  GitHub-App-Installationstoken war automatisch abgelaufen. Alle Funde wurden mit dieser Begründung
  klassifiziert. Der lokale Scan fand daneben nur einen ausdrücklich synthetischen
  Authentifizierungswert im Unit-Test.
- Keystore, Signierpasswörter, GitHub-Tokens, Cookies, OAuth-Client-Secrets, private API-Schlüssel,
  lokale Konfiguration und Crashlogs dürfen nie in Commit, Changelog, Issue oder Buildartefakt landen.
- Ein GitHub-Alarm auf einen alten Commit verschwindet nicht dadurch, dass nur die aktuelle Datei
  geändert wird: Git-Historie bleibt erreichbar. Ist der Fund ein gültiges Geheimnis, wird es zuerst
  beim Anbieter widerrufen/rotiert, dann aus dem aktuellen Code entfernt und bei Bedarf mit einem
  bewusst geplanten History-Rewrite bereinigt.
- Öffentliche Client-Kennungen oder aus Upstream übernommene Konstanten können ebenfalls von
  heuristischem Secret-Scanning markiert werden. Jeder Alarm wird anhand Herkunft, Schlüsseltyp und
  Berechtigungen klassifiziert; er wird weder blind ignoriert noch durch Veröffentlichung des Werts
  „erklärt“.
- Werte werden in Dokumentation und Kommunikation ausschließlich geschwärzt behandelt. Diese
  Dokumentationsprüfung hat weder gespeicherte Crashreports noch Schlüsselwerte geöffnet.
