# KruXx 1.0.1 – aktueller IST-Stand

Stand: 03.09.2026 · maßgeblich für den lokalen Entwicklungsstand

Dieses Dokument trennt implementierte Funktionen, bereits nachgewiesene Tests und noch offene
Freigabeprüfungen. Architektur- und Wartungsdetails stehen im
[`KRUXX-ENTWICKLERHANDBUCH.md`](KRUXX-ENTWICKLERHANDBUCH.md).

## 1. Identität und Release-Status

| Merkmal | Aktueller Stand |
|---|---|
| Produkt | KruXx – The core of your music |
| Version | `1.0.1` |
| Android-Versionscode | `1_000_001` |
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

Der Stand ist als lokaler 1.0.1-Release-Kandidat vollständig getestet, gelintet, gebaut, signiert und
archiviert, aber noch **nicht veröffentlicht**. Vor der Veröffentlichung wird exakt das unten
bezeichnete Release-APK noch einmal installiert und kalt gestartet. Danach werden der freigegebene
Quellstand samt Submodul-Pins und das dazugehörige Tag `v1.0.1` gemeinsam veröffentlicht. Nur so
lässt sich die veröffentlichte APK eindeutig ihrem Quellcode zuordnen.

## 2. Aktueller Funktionsumfang

### Eigenständigkeit, Updates und Datenschutz

- Eigene Paket-ID, Versionierung, Signatur, GitHub-Quelle und eigener Updatekanal.
- Der Updater akzeptiert nur stabile Releases aus `Massefehler/KruXx`, den exakten APK-Namen und eine
  höhere Version. Vor dem Android-Installer prüft er GitHubs SHA-256-Digest, APK-Paket, Version und den
  fest gepinnten KruXx-Signaturfingerabdruck.
- Eine Installation aus der alten Linie `2.2.3-kruxx.x` benötigt einmalig eine manuelle, korrekt
  signierte 1.x-APK (`1.0.0` oder neuer). Paket und Schlüssel bleiben gleich, daher aktualisiert
  Android ohne Datenlöschung; anschließend steht der KruXx-Updater zur Verfügung.
- Automatische Prüfungen laufen höchstens einmal in 24 Stunden und lassen sich auf „nachfragen“,
  automatisch oder aus stellen.
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
- Wiedergabelisten unter „From your Library“ und in der Bibliotheksansicht werden ausdrücklich als
  YTM-Online-Playlists geöffnet. Angemeldete Seite und Fortsetzungen laufen über denselben
  Metrolist-Client wie Login und Bibliothek; öffentliche/anonyme Listen bleiben beim bisherigen
  leichten Browse-Modul. Die Einträge werden nicht irrtümlich wie lokale Datenbank-Playlists
  behandelt.
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
- Direkt ausgewählte YTM-Treffer vom Typ offizielles Musikvideo (`OMV`) oder Nutzer-Video (`UGC`)
  öffnen einen eingebetteten 16:9-Player. Art Tracks (`ATV`) mit Standbild und unbekannte Typen bleiben
  in der ressourcenschonenden Audiowiedergabe.
- Video-zu-Audio-Wechsel und der automatische Audio-Rückfall bei nicht einbettbaren Videos behalten
  die Position bei. Warteschlange, „Als Nächstes“ und Kontextmenüs starten niemals ungefragt Video.
- Unter **Einstellungen → Erscheinungsbild** kann „Schwebenden Player erlauben“ aktiviert werden.
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
- Sichtbare Pop-ups, Medien, Wecker und Anrufe bleiben erlaubt. Pause, Stopp, Dienstende, nächster
  App-Start und lokaler Crash-Wiederanlauf stellen den vorherigen Zustand wieder her; ein bereits vor
  KruXx aktiver „Nicht stören“-Modus wird nicht abgeschaltet.

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
  anschließend folgt eine kurze 420-ms-Ausblendung.
- Das aktuelle Quellbild `icons/KruXx_App_Icon.png` wurde über die KruXx-Icon-Pipeline in adaptive,
  runde und klassische Launcher-Icons, Themed Icon, Benachrichtigungssymbol, TV-Banner sowie
  Cover-Platzhalter überführt.
- Das Icon muss nach jeder Änderung des Quellbilds erneut generiert und anschließend in einer neu
  gebauten APK auf Gerät, Launcher, Benachrichtigung und Android TV geprüft werden.

## 3. Prüfstand vom 03.09.2026

| Bereich | Nachweis | Status |
|---|---|---|
| KruXx-App-Unit-Tests | 57 Tests, 0 Fehler, 0 übersprungen | bestanden |
| `modules/innertube` | 58 Tests einschließlich aktueller Charts-Antwort und Auth-Header-Weitergabe für Browse/Next, 0 Fehler | bestanden |
| `modules/metrolist` | Root-Integration kompiliert; das Modul besitzt in diesem Stand keine eigenen Tests (`NO-SOURCE`) | bestanden |
| Release-Lint | vollständiges `lintKruxxUniversalProdRelease`; keine neuen Befunde gegenüber der versionierten Baseline | bestanden |
| Release-APK | Paket `de.kruxx.music`, Version `1.0.1`/`1000001`, erwartete Signatur | geprüft |
| Update-Installation | Exakt archiviertes Release-APK über bestehende KruXx-Installation per ADB, App-Daten erhalten | bestanden |
| App-Start | Von ADB bestätigter Kaltstart nach Installation; zehn wechselnde Splash-Frames, Übergang zur Startseite, laufender Prozess und kein `AndroidRuntime`-/Crash-Eintrag | auf Gerät bestanden |
| Breiter Nutzertest | Navigation und zentrale Funktionen auf dem Zielgerät ohne beobachtete Fehler; Downloads ausdrücklich mit sehr hohem Durchsatz bestätigt | auf Gerät bestanden |
| Reguläre Konto-Playlists | Fünf Listen geöffnet, darunter 4 sowie 52 nutzbare Songs (54 YTM-Einträge einschließlich zwei nicht abspielbarer); Titel und vorhandene Songzeilen sichtbar, kein 401/`UNAUTHENTICATED` | auf Gerät bestanden |
| „Neue Folgen“ (`RDPN`) | Authentifizierter Browse antwortet, verwendet aber ein Podcast-Multirow-Format außerhalb des Musik-Playlist-Parsers | separater Parserpunkt offen |
| Interpreten aktualisieren | zweimal Pull-to-refresh, kein rotes Fehlerbanner | auf Gerät bestanden |
| Interpreten sortieren | neun sichtbare Einträge bei „Titel / aufsteigend“ alphabetisch | auf Gerät bestanden |
| Suche/Sprache/Video | Parser-, Kontext-, Fallback- und Klassifikations-Unit-Tests | automatisiert bestanden; im allgemeinen Gerätetest keine Auffälligkeit gemeldet |
| Downloads | Queue-/Statuslogik automatisiert, Resolver und Build geprüft; Downloads auf Gerät schnell und fehlerfrei | bestanden |
| PiP | Implementierung für Android 7–11 und 12+ geprüft | kein bekannter Fehler; Einzelmatrix nicht separat protokolliert |
| Benachrichtigungsschutz | Lifecycle-/Wiederherstellungslogik und Hauptthread-Fix geprüft | kein bekannter Fehler; Fremd-App-Ton nicht separat protokolliert |
| Stereo | Kein Downmix im Audiopfad; Decoderdiagnose vorhanden | kein bekannter Fehler; Links-/Rechts-Hörtest nicht separat protokolliert |

Die finalen Dateien heißen `KruXx-1.0.1-release.apk` und `KruXx-1.0.1-debug.apk` und werden unter
`/home/kruxx/Schreibtisch/Android/Kreate-APKs/` archiviert. Ein normaler Lauf von
`scripts/build-local-release.sh kruxx` erzeugt beide aus demselben Quellbaum, signiert das Release
mit v1/v2/v3 und prüft Paket, Version, Zertifikat sowie die von AGP in
`META-INF/version-control-info.textproto` eingebettete Git-Revision.

Der finale SHA-256-Wert wird bewusst **erst nach dem Build aus dem endgültigen sauberen
Release-Commit** in der GitHub-Release-Beschreibung und als GitHub-Asset-Digest festgehalten: Die
eingebettete Git-Revision ändert das APK bei jedem neuen Commit, deshalb könnte ein Hash im selben
Commit nie sich selbst korrekt beschreiben. Der Release-Kandidat erfüllte sämtliche Metadaten- und
Signaturprüfungen. Das exakt archivierte Release-APK wurde anschließend per ADB als Update
installiert und kalt gestartet; App-Daten blieben erhalten, die Splash-Animation wechselte sichtbar
durch ihre Frames, die Startseite erschien und der Crash-Logfilter blieb leer.

Der öffentliche [GitHub-Release v1.0.1](https://github.com/Massefehler/KruXx/releases/tag/v1.0.1)
zeigt auf den geprüften Commit `c3f478dae45bb0cd69c66d0c53cd7ff3fd32e9dd`. Sein einziges
APK-Asset ist bytegleich mit dem lokal getesteten Archiv; GitHub-Asset-Digest, erneuter Download
und Dateigröße wurden nach der Veröffentlichung verifiziert.

## 4. Bekannte Grenzen und Freigabekriterien

- YTM, GitHub, Metadaten- und Liedtextdienste sind externe Dienste. Antwortformate, regionale
  Verfügbarkeit, Kontoversuche und CDN-Tempo können sich ohne App-Update ändern.
- Die Podcast-Autoplaylist „Neue Folgen“ (`RDPN`) ist keine reguläre Musik-Playlist. Ihr
  Multirow-Antwortformat wird vom derzeitigen Playlist-Parser noch nicht abgebildet; dieser offene
  Sonderfall ist unabhängig von der jetzt geprüften Konto-Authentifizierung.
- Die Spracheingabe fehlt auf Android 11 und älter sowie auf Geräten ohne lokalen Erkenner/Sprachmodell.
- Ein erkannter OMV-/UGC-Typ garantiert keine Einbettung; Rechteinhaber, Region, Altersschutz oder
  WebView können Video verhindern. Dann ist Audio der beabsichtigte Rückfall.
- PiP kann vom Android-/Hersteller-System global deaktiviert oder eingeschränkt sein. Ohne aktiven
  Titel öffnet KruXx bewusst kein leeres schwebendes Fenster.
- Der Benachrichtigungsschutz kann nur Töne über Androids Notification Policy steuern. Er blockiert
  weder sichtbare Pop-ups noch Medien, Wecker oder Anrufe und umgeht keine Gerätevorgaben.
- Für den öffentlichen `v1.0.1` wurden geprüfter Quellstand, beide Submodul-Commits und das passende
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
