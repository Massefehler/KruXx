# KruXx – aktueller IST-Stand

Stand: 08.09.2026 · Release-Kandidat `1.2.1`; öffentlich weiterhin `1.2.0`

Dieses Dokument trennt implementierte Funktionen, bereits nachgewiesene Tests und noch offene
Freigabeprüfungen. Architektur- und Wartungsdetails stehen im
[`KRUXX-ENTWICKLERHANDBUCH.md`](KRUXX-ENTWICKLERHANDBUCH.md).

## 1. Identität und Release-Status

| Merkmal | Aktueller Stand |
|---|---|
| Produkt | KruXx – The core of your music |
| Öffentlicher Release | [`1.2.0` „Downloads Update“](https://github.com/Massefehler/KruXx/releases/tag/v1.2.0) |
| Öffentlicher Android-Versionscode | `1_000_004` |
| Nächster Release | `1.2.1` / `1_000_005`: Navigation und Download-Korrekturen, in Vorbereitung |
| Release-Paket | `de.kruxx.music` |
| Debug-Paket | `de.kruxx.music.debug` |
| Android-Untergrenze | Ab `1.2.0`: API 24 / Android 7.0; letzter veröffentlichter Release für Android 6: `1.1.0` |
| Compile-/Target-SDK | API 36 / Android 16 |
| App-Repository | `Massefehler/KruXx` |
| Innertube-Spiegel | `Massefehler/KruXx-innertube` für den bewusst gepinnten Modul-Fix |
| Metrolist-Spiegel | `Massefehler/KruXx-metrolist` für den bewusst gepinnten Login-/Parser-Fix |
| Lizenz | GPL-3.0 für KruXx/Kreate; eingebundene Komponenten behalten ihre eigenen Lizenzen |

KruXx ist ein eigenständiger Fork. Es gibt keine automatische Übernahme neuer Kreate-Versionen und
keinen Push, Crashbericht oder Supportlink zum Kreate-Projekt. Kreate, RiMusic, Metrolist und weitere
Urheber bleiben entsprechend ihrer Beiträge genannt; diese Danksagung und Lizenzpflicht bedeutet
keine organisatorische Verbindung oder Mitverantwortung für KruXx.

### Release-Vorbereitung 1.2.1 vom 08.09.2026

Der beauftragte Patch-Release bündelt die folgenden Korrekturen. Versionsname und Android-Code
sind auf `1.2.1` / `1000005` erhöht; eigene [Versionshinweise](changelogs/kruxx/1.2.1.txt) liegen vor.
Historische Changelogs, Paket-ID, Android-Untergrenze und Release-Zertifikat bleiben erhalten.
Die bisherigen 203 Tests und Debug-Geräteprüfungen sind unten belegt. Die erneuten Prüfungen aus
dem sauberen Release-Commit, der signierte Build, dessen Geräteabnahme, CI, Tag und Veröffentlichung
stehen zu diesem Quellstand noch aus. Finale Prüfergebnisse und APK-Hash werden anschließend ergänzt.

Die USB-Verbindung zum Samsung war zuletzt nicht verfügbar. Für die signierte Abnahme sind eigene
API-24- und API-36-Emulatoren vorgesehen; die vorhandenen Samsung-Nachweise betreffen den Debug-Code.
Die erweiterten offenen Gerätefälle bleiben als Nachtests ausgewiesen.

Die signierte Android-16-Abnahme hat zusätzlich einen Absturz beim erneuten Erstellen des
Wiedergabedienstes aufgedeckt: Beim Schließen vor der ersten Wiedergabe verhinderte ein noch nicht
initialisierter Audioeffekt die Freigabe der Mediensitzung. Die Dienstbereinigung führt ihre Schritte
jetzt unabhängig aus und gibt Controller, Sitzung und dienstgebundene Listener frei. Der
anwendungsweit geteilte Player wird gestoppt, aber ebenso wie die gemeinsam verwendeten Caches
nicht durch eine einzelne Dienstinstanz zerstört. Der periodische Queue-Job endet mit dem Dienst;
der doppelte Statistik-Listener entfällt. Ein Regressionstest deckt die Freigabe vor der ersten
Audiositzung ab. Der interne Controller verwendet das Sitzungstoken direkt, damit keine Bindung
an den eigenen Dienst dessen Ende verhindert. Im Android-16-Debugtest sind drei vollständige
Activity-/Dienst-Neuerstellungen im selben Prozess bestanden: nach dem Schließen keine Sitzung,
nach dem Öffnen genau eine Sitzung, keine Bereinigungsfehler oder Abstürze. Die signierte Prüfung
dieses zusätzlichen Fixes steht noch aus.

Die Sitzung wird zusätzlich ausdrücklich beim Media3-Dienst registriert; beim direkten Token
entfällt der automatische Weg über `onGetSession()`. Der ergänzende Debugtest bestätigt nach drei
Dienst-Neuerstellungen Online-Wiedergabe im selben Prozess, Medienbenachrichtigung samt
Steueraktionen, den aktiven Vordergrunddienst und fortlaufende Wiedergabe hinter der Android-Startseite.

### Korrekturen für 1.2.1 vom 08.09.2026 – Suche und Downloads

- Startseite, Sucheingabe und Suchergebnisse verwenden dieselbe Hauptnavigation einschließlich
  „Playlists“ und „Downloads“. Suchquellen beziehungsweise Ergebniskategorien stehen als eigene
  Filter oberhalb des Inhalts. Die übernommenen Kategorien „Vorgestellt“ und „Podcasts“ werden
  in KruXx nicht mehr angeboten; eine gespeicherte Auswahl dieser Kategorien fällt auf Titel zurück.
- Kurzes Tippen auf das Downloadsymbol eines fertigen oder laufenden Tracks entfernt wieder
  internes Originalaudio und sämtliche internen MP3-/Videovarianten. Langes Drücken öffnet
  die Format-/Speicherauswahl auch bei gemerkten Vorgaben. Exportierte Ordnerkopien werden weiterhin
  gezielt in der Downloads-Übersicht entfernt. Fehlgeschlagene interne Dateilöschungen bleiben
  sichtbar; wartende Media3-Befehle berücksichtigen die Entfernungsgeneration.
- Das Schema `Künstler - Titel` (einschließlich Gedankenstrichen mit Leerzeichen) gilt für alle
  Downloadwege, auch Titelkacheln, Sammelaktionen und Bibliotheksdaten ohne Video-Herkunftsflag.
  `Kilophil - Protoporn` mit Uploader `dejanprogtrens` wird zu Künstler `Kilophil`, Titel `Protoporn`
  und beispielsweise `Kilophil - Protoporn.mp3`. Weitere Titelbestandteile wie `- Extended Mix`
  bleiben erhalten. Ohne dieses Schema werden vorhandene Musikmetadaten verwendet;
  bei bekannten Videos ohne Künstlerangabe wird kein Künstler aus dem Kanalnamen erfunden.
  Auch beim Abspielen von Originalaudio aus der Downloads-Übersicht übernimmt der Player die
  aufgelösten Künstler-/Titeldaten statt der alten Bibliotheksangaben.
- Rohe Quellangaben werden auch für Originaldownloads gespeichert. Bereits aufgeteilte
  Bibliothekstitel werden bei erneuten Download-/Kopieraktionen damit nicht nochmals zerlegt.
  Suchtreffer behalten ihre Video-/Art-Track-Kennung bis zum Downloadknopf.
- Neue MP3s verwenden die korrigierten ID3-Tags. Beim erneuten Verwenden einer älteren internen
  Konvertierung werden deren von KruXx erzeugte Titel-/Künstler-Tags bei Bedarf ersetzt;
  die MP3-Audiodaten werden unverändert kopiert. Bestehende öffentliche Dateien werden dabei
  nicht überschrieben. Originalaudio bleibt unverändert im Quellformat.
- Automatische Prüfung dieses Arbeitsstands: **145 App-Tests und 58 Innertube-Tests bestanden**,
  ohne Fehler oder übersprungene Tests. Universal-Debug erfolgreich gebaut; Release-Lint
  erfolgreich ohne Fehler, mit 48 Warnungen und zwei Baseline-Hinweisen. Die Warnungen betreffen
  Dependency-Versionen und vorhandene API-Prüfungen; die Baseline wurde nicht erweitert.
  Nach der Ergänzung für direkte Suchrouten und die rechte Navigationsleiste wurden App-Tests,
  Debug-Build und Release-Lint erneut erfolgreich ausgeführt.
- Bedienprüfung auf einem eigens angelegten API-36-Emulator: Die unveränderte Debug-APK von
  1.2.0 zeigt beim Suchbegriff `Kilophil Protoporn` die alte Kategorienleiste und den Uploader
  `dejanprogtrens`. Nach Installation des Arbeitsstands bleiben Hauptnavigation und „Downloads“
  während der Suche erreichbar. Originalaudio wurde erfolgreich heruntergeladen und unter
  `Kilophil - Protoporn` in „Nur in KruXx“ angezeigt. Langes Drücken auf dessen Downloadsymbol
  öffnet die Formatauswahl; die anschließende MP3-Konvertierung mit öffentlicher Kopie ist fertig.
  `ffprobe` bestätigt `Kilophil - Protoporn.mp3`, MP3 mit 320.000 bit/s, Titel `Protoporn` und
  Künstler `Kilophil`. Ein kurzer Tipp auf denselben Knopf entfernt danach die internen MP3-
  und Originalaudio-Dateien vollständig (beide Speicherbereiche leer); das Symbol bietet wieder
  einen Download an. Die öffentliche Kopie bleibt wie vorgesehen erhalten.
  Sucheingabe und Suchergebnisse wurden außerdem mit rechter Navigationsleiste geprüft:
  Inhalts-/Filterbreite berücksichtigen die Leiste; „Playlists“ und „Downloads“ bleiben sichtbar.
  Der App-Crashpuffer blieb leer. Der eigens angelegte Emulator wurde beendet und gelöscht;
  bestehende Geräteinstallationen wurden für diese Bedienprüfung nicht verändert.
  Diese Prüfungen ersetzen nicht die unten genannten offenen Gerätefälle.

### Ergänzende Leistenkorrektur für 1.2.1 vom 08.09.2026

- Die zwei Leisten in den Bibliotheksrubriken haben unterschiedliche Aufgaben: oben die Aktionen
  mit Drei-Punkte-Menü, darunter die Kategorienfilter. Die Filter besaßen noch keine Pfeile.
  `HorizontalScrollWithArrows` stellt nun die gemeinsame Überlaufmessung und antippbare `<<`-/`>>`-
  Hinweise für Hauptnavigation und sämtliche `ButtonsRow`-Filter bereit: Titel, Künstler, Alben,
  Playlists, Downloads, Verlauf, Statistik, Suchquellen und Suchergebnisse.
- Pfeile erscheinen nur bei tatsächlich verdeckten Einträgen und nur in noch erreichbare
  Richtungen. Die Randflächen bleiben an den Scrollgrenzen stabil. Die aktive Filterauswahl
  wird nach Auswahl- und Breitenänderungen sichtbar gehalten. Quellenfilter bei Künstlern und
  Alben behalten ihren eigenen Platz. Schriftgröße und verfügbare Breite fließen in die Messung ein.
- Die sichtbaren Titelfilter reagieren nun auch auf Änderungen von „Lokal“/„Auf dem Gerät“;
  dessen Zustand fehlte bisher unter den `remember`-Schlüsseln. `DownloadsScreen` reserviert
  außerdem den Platz für eine rechts angeordnete Hauptnavigation.
- Erneute automatische Prüfung des endgültigen Codes: **145 App-Tests bestanden**, keine Fehler
  oder übersprungenen Tests. Universal-Debug und Release-Lint erfolgreich; unverändert 48 Warnungen
  und zwei Baseline-Hinweise, keine Lint-Fehler und keine Erweiterung der Baseline.
- USB-Prüfung auf Samsung SM-S931B / Android 16: bestehende Debug-App per Update installiert,
  Release-App und gespeicherte Dateien erhalten. Der vorherige Stand zeigt die Filter unter
  „Titel“ ohne Pfeile. Nach Update sind Beginn, Zwischenposition und Ende mit korrekten
  Richtungshinweisen belegt; „Lokal“ wird erreicht. Künstler-, Alben-, Playlist- und Downloadleisten
  sind geöffnet und geprüft; Audio, Video und „Nur in KruXx“ lassen sich wechseln.
  Bei Schriftfaktor 1,5 sind überlaufende Künstler-/Albenfilter per Pfeil erreichbar, der feste
  Quellenfilter behält exakt seine Position. Die Statistikfilter und sämtliche sieben Zeiträume
  von „Heute“ bis „Gesamt“ sind erreichbar. Die Verlaufsfilter passen auch bei Schriftfaktor 1,5
  vollständig in die Leiste und benötigen dann keine Pfeile.
- Beide Verlaufsquellen, die Titelfilter Favoriten/Zwischengespeichert/Offline-Songs/Top sowie
  sämtliche neun Einstellungsrubriken sind gewechselt. Auch die Einstellungsleiste erreicht ihre
  letzte Rubrik „Info“ mit dem korrekten linken Richtungshinweis an der rechten Scrollgrenze.
- Abschließende Prüfung mit der zuletzt gebauten Debug-APK: Im Querformat passen beide
  Künstlerfilter und alle sechs Hauptreiter; die Pfeile verschwinden. Nach Rückkehr ins Hochformat
  bleibt „Bibliothek“ ausgewählt und sichtbar, die notwendigen Pfeile kehren zurück. Mit rechter
  Navigation ist auch der letzte Downloads-Filter „Nur in KruXx“ auswählbar, ohne die Hauptleiste
  zu verdecken. Alle drei Suchquellen und alle fünf Ergebniskategorien sind gewechselt; danach ist
  „Downloads“ über die weiterhin sichtbare Hauptleiste erreichbar. „Vorgestellt“ und „Podcasts“
  sind nicht mehr vorhanden. Ursprünglicher Schriftfaktor 0,8, Ausrichtungssperre/Hochformat und
  untere Navigationsposition sind wiederhergestellt. Der neue Crash-/ANR-Filter für das Debug-Paket
  liefert im Prüfzeitraum keine Treffer. Zum Abschluss steht die Debug-App auf „Titel“ mit der
  normalen Gesamtauswahl; die temporäre UI-Dump-Datei auf dem Handy ist entfernt.

### App-Icon als Startseiten-Schaltfläche für 1.2.1 vom 08.09.2026

- Das App-Icon und der KruXx-Schriftzug im gemeinsamen Header bilden eine zusammenhängende,
  mindestens 48 dp hohe Schaltfläche mit der zugänglichen Beschriftung „Zur Startseite“.
  Beide öffnen ausdrücklich den Startseiten-Reiter, auch wenn zuletzt Titel oder Downloads aktiv war.
  Die geerbten Spielaktionen durch Mehrfach- oder Langdruck auf das Icon entfallen in KruXx.
- `HomeNavigation.goHome` setzt die Reiterauswahl und entfernt untergeordnete Seiten aus dem
  Navigationsstapel. Das gilt auch für einen direkten App-Start in der Suche. Wiederholtes Tippen
  auf der bereits geöffneten Startseite erzeugt keine weiteren Seiten. Die konfigurierbare
  Startansicht beim App-Start bleibt unabhängig davon; bei bewusst deaktivierter Startseite greift
  wie beim App-Start der vorhandene Titel-Fallback.
- **145 App-Tests bestanden**, keine Fehler oder übersprungenen Tests. Universal-Debug und
  Release-Lint erfolgreich; unverändert 48 Warnungen und zwei Baseline-Hinweise, keine Lint-Fehler.
  Der neue Debug-Build ist mit Datenerhalt auf dem Samsung SM-S931B / Android 16 installiert.
  Die neue Header-Schaltfläche ist dort sichtbar. Die USB-Debugging-Verbindung brach danach ab;
  der vollständige Interaktionstest auf dem Handy konnte deshalb nicht abgeschlossen werden.
- Ersatzprüfung mit derselben APK in einem eigenen Android-16-AVD: Rückkehr über Icon und
  Schriftzug aus Titel, Künstlern, Alben, Playlists, Downloads, Einstellungen, Verlauf, Statistik,
  Profilen und einer geöffneten Albumdetailseite bestanden. Auch Einstellungen → Suche →
  Suchergebnisse → Startseite entfernt den gesamten Unterseitenstapel. Der Header zeigt danach
  keine Zurück-Schaltfläche mehr.
- Ein Kaltstart über `it.fast4x.rimusic.action.search` öffnet zunächst die Suche; Icon → Startseite
  und anschließend Einstellungen → Schriftzug → Startseite funktionieren auch bei diesem
  abweichenden Graph-Startziel. Zwölf weitere Icon-Tipps und ein langer Druck öffnen weder Spiele
  noch zusätzliche Seiten. Im abschließenden Emulatorlauf keine App-Abstürze oder ANRs.
  Der erfolgreiche Lauf verwendet Host-Grafik mit deaktiviertem Vulkan; der Software-Renderer
  des Emulators war zuvor auf dem Entwicklungsrechner abgestürzt.

### Veröffentlichung von 1.2.0 am 07.09.2026

KruXx `1.2.0` ist seit **13:13 Uhr MESZ** als neuester stabiler GitHub-Release veröffentlicht.
Nach den unten dokumentierten Kernprüfungen, grünem CI und Offenlegung der übrigen Gerätefälle
wurde die Veröffentlichung ausdrücklich beauftragt. Das ist keine nachträgliche vollständige
Abnahme der noch offenen Matrix; Release-Beschreibung und diese Dokumentation nennen deren Grenzen.

- Annotiertes Tag `v1.2.0` zeigt exakt auf den gebauten und in der APK eingebetteten Commit
  `f195aed1b80348f5355ea129ee4c15e1b1d89bf7`. Die nachfolgenden Commits auf `main` ändern nur
  Dokumentation und die Fehlerbericht-Vorlage.
- [CI-Lauf 34113950567](https://github.com/Massefehler/KruXx/actions/runs/34113950567) für
  `4410fda407aa03647bba4f83b6401c3b8d9c884b` ist bestanden. Der signierte Build und alle 195 Tests
  sind in der Geräteprüfung unten belegt.
- Auch [CI-Lauf 34116708466](https://github.com/Massefehler/KruXx/actions/runs/34116708466) für den
  Dokumentationscommit `2450da36e923d4665cdef78475032047e9a0dcc0` ist bestanden: App-/Innertube-Tests
  und Debug-Build, abgeschlossen am 07.09.2026 um **13:37 Uhr MESZ**.
- GitHub-Release zuerst als Entwurf erstellt; Tagobjekt, Quellcommit, Beschreibung und genau ein
  vollständig hochgeladenes Asset vor Veröffentlichung über die API geprüft.
- Öffentliche Latest-API bestätigt `v1.2.0`, weder Entwurf noch Vorabversion, Asset
  `KruXx-1.2.0-release.apk`, **23.669.671 Bytes** und
  SHA-256 `bc0d6d6b1f88b470965fa86f3b87a52475e11a088f56667631e523e3d8304a3d`.
  Ein erneuter anonymer Download ist bytegleich mit dem getesteten Archiv; seine Signatur ist gültig
  und das Zertifikat stimmt mit 1.1.0 überein. Die Debug-APK bleibt im lokalen Archiv.
- Kein Crashbericht, Schlüsselmaterial oder Geräteexport wurde veröffentlicht; offene
  Secret-Scanning-Alarme in App-, Innertube- und Metrolist-Spiegel waren vor Veröffentlichung leer.

Die detaillierten Vorprüfungen und historischen Kandidatenbefunde folgen unverändert in ihrer
zeitlichen Einordnung. Android Auto in DHU/Fahrzeug, physische SD-/USB-Anbieter, die übrigen
unbelegten Funktionskombinationen und subjektive Hör-/Synchronitätsprüfungen bleiben Nachtests.

### Self-Updater-Nachtest nach Veröffentlichung

Zwei eigens angelegte API-24-Emulatoren verwenden jeweils die unveränderte signierte 1.1.0 mit
Standard „Nachfragen“; weder Samsung-Daten noch bestehende Testinstallationen wurden gelöscht.

- Beim unmittelbar nach Veröffentlichung ausgeführten Online-Erststart lag der Changelog-Dialog
  oben. Ein erfolgreicher Prüfzeitpunkt wurde gespeichert, ein automatischer Updatehinweis wurde
  dabei nicht separat beobachtet. Dieser Versuch zählt daher nicht als eindeutiger Online-Nachweis.
- „Jetzt überprüfen“ findet 1.2.0 mit korrektem Asset trotz bereits gesetztem Prüfzeitpunkt.
  Ein Vordergrundwechsel bei offenem Hinweis zeigt weiterhin genau einen Dialog. Nach „Später“
  öffnet der nächste Vordergrundwechsel keinen neuen Hinweis.
- Ohne Standardnetz meldet „Jetzt überprüfen“ sichtbar „No Connection“. Nach Netzrückkehr findet
  die manuelle Prüfung wieder 1.2.0; der Fehler setzt keinen neuen erfolgreichen Prüfzeitpunkt.
- Auf dem zweiten Emulator wurde 1.1.0 offline gestartet und der erste Changelog geschlossen.
  Vor Netzrückkehr existiert kein erfolgreicher Prüfzeitpunkt. Nach Verbinden bei sichtbarer
  Oberfläche erscheint 1.2.0 automatisch ohne „Jetzt überprüfen“. Nach „Später“ und Rückkehr
  bleibt derselbe Prüfzeitpunkt erhalten und kein weiterer Dialog erscheint.
- **Belegte Einschränkung:** Der System-Downloadmanager des API-24-Emulators kann das APK nicht
  herunterladen. Wiederholte Versuche melden `CertPathValidatorException: Trust anchor for
  certification path not found`; Status 194 (`WAITING_TO_RETRY`), null empfangene Bytes.
  Die Zertifikatsprüfung wurde nicht umgangen. Der schon bestandene manuelle API-24-Upgrade-Test
  bleibt gültig. Als veröffentlichter Ausweichweg: signierte APK auf einem aktuellen Rechner
  laden, übertragen und über die bestehende App installieren. Die beiden Emulatoren wurden beendet.
- Ein gezielt injizierter HTTP-5xx-Fehler und die vollständige 24-Stunden-Grenze sind hier nicht
  praktisch geprüft; dafür bleiben die vorhandenen Policy-Tests und weitere Geräte-Nachtests.

**Vollständiger Updateweg auf Android 16 / API 36:**

- Ein weiterer frischer Emulator mit unveränderter signierter 1.1.0 wurde offline eingerichtet:
  ersten Changelog geschlossen und leere lokale Playlist „Self-Updater 1.2.0“ angelegt.
  Der Modus „Nachfragen“ blieb unverändert. Anschließend Prozess beendet, Netz verbunden und
  die App kalt gestartet. 1.2.0 erscheint automatisch ohne „Jetzt überprüfen“.
- „Download and install“ lädt die öffentliche APK erfolgreich über Androids Downloadmanager.
  Die auf dem Gerät heruntergeladene Datei hat exakt den oben genannten SHA-256. Die App durchläuft
  Digest-, Paket-, Versionscode- und Signaturprüfung und öffnet den Systemzugriff „Install unknown apps“.
- Diesen Zugriff nur für die Testinstallation erteilt, in KruXx „Install“ und im Android-Installer
  „Update“ gewählt. Android bestätigt „App installed“, Version `1.2.0` / `1000004`, `minSdk=24`.
  Erstinstallationszeit bleibt **13:21:39**, Updatezeit **13:24:55** am 07.09.2026.
- Nach Öffnen zeigt die App die eingebetteten 1.2.0-Release-Notes. Die Playlist
  „Self-Updater 1.2.0“ ist weiterhin sichtbar. Auch die vom installierten Paket erneut gelesene
  APK ist bytegleich mit dem Release-Asset. Keine App-Crash-/ANR-Ereignisse im geprüften Zeitraum.
- Der eigens gestartete Emulator wurde nach Abschluss beendet. Das angeschlossene Samsung wurde
  für diesen Nachtest nicht heruntergestuft oder verändert.

### Supportentscheidung vom 07.09.2026: Android 7 als neue Untergrenze

Auf Nutzerentscheidung endet die Unterstützung für Android 6 / API 23 mit Release `1.2.0`.
Der KruXx-Flavor setzt ab `1.2.0` `minSdk = 24`, für Debug und Release sowie alle Architekturen.
Der veröffentlichte Release `1.1.0` bleibt die letzte Android-6-kompatible Version; bestehende
Installationen können ihn weiter verwenden, erhalten aber keine weiteren unterstützten Updates.

Der unten dokumentierte Android-6-Float-/NaN-Vergleichsfehler wird für KruXx nicht weiter behoben.
Die Diagnose und bisherigen API-23-Testergebnisse bleiben historische Nachweise. Das Supportende
ist keine technische Behebung des Fehlers und keine Freigabe der übrigen Funktionskonflikte.
Der anschließende signierte Gerätetest unten prüft die Such-/Dialogkorrekturen, Wiedergabe und
Downloads auf Android 7 / API 24 und dem Samsung-Zielgerät mit optimierter Release-Ausführung.
Die weiteren offenen Gerätefälle aus `DOWNLOADS.md` bleiben bestehen.

Lokale Prüfung der Supportumstellung am 07.09.2026:

- Alle 137 App-Unit-Tests bestanden, ohne Fehler oder übersprungene Tests.
- Universal-Debug und die mit R8 optimierte, unsignierte Universal-Release-APK erfolgreich gebaut.
  Beide APKs bestätigen `minSdkVersion:'24'`, Target-SDK 36 und Version `1.2.0` / `1000004`.
- Vollständiger Release-Lint erfolgreich: keine Fehler außerhalb der bestehenden Baseline,
  16 Warnungen. Davon betreffen zwei neuere Media3-Versionen und 14 vorhandene Versionsprüfungen
  beziehungsweise API-Annotationen (`ObsoleteSdkInt`), die mit der höheren KruXx-Untergrenze
  überflüssig werden. Sie liegen im gemeinsamen Code der Flavors; die API-23-Kompatibilität
  der geerbten Varianten bleibt erhalten. Die Lint-Baseline wurde nicht erweitert.
- Die Release Notes stimmen zwischen Dokumentation, generierter Ressource und gebauter
  Release-APK bytegenau überein.
- Zu diesem ersten Prüflauf war kein Gerät verbunden und noch keine neue signierte APK erstellt.
  Der nachfolgende Prüflauf ergänzt die signierten Artefakte und die API-24-/Samsung-Kernabnahme.

### Signierte Geräteprüfung vom 07.09.2026

Der Support-Commit `f195aed1b80348f5355ea129ee4c15e1b1d89bf7` wurde in einem frischen, sauberen
normalen Clone einschließlich der gepinnten Submodule geprüft und mit
`scripts/build-local-release.sh kruxx` gebaut. Alle 137 App- und 58 Innertube-Tests sind bestanden;
Metrolist besitzt weiterhin keine eigenen Tests (`NO-SOURCE`). Der vollständige Release-Lint ist
erfolgreich, ohne Erweiterung der Baseline: Im frischen Lauf erscheinen 48 Warnungen, davon
34 Versionshinweise und 14 vorhandene `ObsoleteSdkInt`-Befunde im gemeinsamen Flavor-Code.

Beide Universal-APKs liegen im lokalen Archiv als `KruXx-1.2.0-debug.apk` und
`KruXx-1.2.0-release.apk`; beide benötigen API 24. Die exakt archivierte Release-APK wurde auf
beiden Testgeräten installiert. Ihre Prüfung bestätigt Paket `de.kruxx.music`, Version
`1.2.0` / `1000004`, Target-SDK 36, vier ABIs, ZIP-Integrität, 16-KiB-Zipalignment,
gültige v2-/v3-Signaturen und die oben genannte eingebettete Quellrevision. Die Release Notes
stimmen bytegleich mit dem Changelog überein.

- Release-APK: **23.669.671 Bytes**.
- SHA-256: `bc0d6d6b1f88b470965fa86f3b87a52475e11a088f56667631e523e3d8304a3d`.
- Zertifikat-SHA-256: `5dc08df341c5d5b56aa9fe9ebc58eb02e0a25bc4a27b48d83a4fbe31ccbdd673`;
  identisch mit der vorher installierten Release-App.

**Samsung SM-S931B, Android 16 / API 36, arm64:**

- Update über den zuvor installierten 1.2.0-Kandidaten `b6cda8025`, ohne Löschen der App-Daten.
  Erstinstallationszeit, Darstellung und der zuvor sichtbare Favoritenfilter mit 112 Titeln
  bleiben erhalten. Das ist kein erneuter direkter Samsung-Upgrade-Test von 1.1.0.
- Online- und Bibliothekssuche zeigen die korrigierten Track-Kacheln. Der Wechsel im selben
  Bibliotheksreiter von „Maddix“ (vier Treffer) zu „Whitechapel“ und zurück aktualisiert die
  Ergebnisse sofort. Der Download-Dialog verwischt den Hintergrund; nach Abbrechen und Zurück
  wird die Liste wieder scharf. Erneutes Öffnen funktioniert.
- „Cipher“ läuft online und wird während der Wiedergabe als MP3 nach
  `Download/KruXx-Downloads/Audio` exportiert: 100 %, eine neue Datei. MediaStore bestätigt
  `owner_package_name=de.kruxx.music` und `is_pending=0`.
- Nach Prozessende und Kaltstart bei deaktiviertem WLAN und mobilen Daten findet die
  Bibliothekssuche den Titel; lokale Wiedergabe und Vorspulen funktionieren ohne Standardnetz.
  Die ursprünglichen Netzwerkzustände sind wiederhergestellt, die Wiedergabe ist pausiert.
- Alle 13 vor dem Update erfassten öffentlichen Mediendateien bleiben auch nach den Tests
  per SHA-256 unverändert. Die neue Testdatei „Kevin MacLeod - Cipher.mp3“ bleibt zusätzlich erhalten.

**Android-7.0-Emulator, API 24, x86_64:**

- Zuerst die signierte stabile 1.1.0 installiert und eine lokale Playlist „API24 Upgradecheck“
  angelegt. Das Update auf die archivierte 1.2.0 behält diese Playlist und die Erstinstallationszeit.
- Die Release-App wurde mit `cmd package compile -m speed -f de.kruxx.music` vollständig
  optimiert; Android bestätigt `compilation_filter=speed`, `kOatUpToDate`. Kein Interpreter-Zwang
  und keine geänderte JIT-Einstellung. Suche, Online-Treffer, Player und Download-Dialog funktionieren.
- „Monkeys Spinning Monkeys“ spielt online. Der Download durchläuft Speicherberechtigung,
  native MP3-Umwandlung und öffentlichen Export. Nach einem Hintergrundwechsel endet er bei
  100 % mit einer gespeicherten Datei. Auch nach App-Neustart funktionieren lokale Wiedergabe
  ohne Standardnetz und Vorspulen. Der eigens gestartete Emulator wurde danach beendet.

Beide exportierten Dateien wurden außerhalb der App vollständig mit ffmpeg dekodiert, ohne Fehler.
ffprobe bestätigt MP3, 48 kHz, Stereo, etwa 320 kbit/s sowie korrekte Titel-/Interpret-Tags;
Laufzeiten: Samsung 231,360 Sekunden, API 24 125,064 Sekunden. In den geprüften App-Prozessen
vor und nach dem Neustart sowie den verfügbaren ANR-/Crash-Ereignissen gab es keine Treffer
für das Release-Paket.

**Bewertung vor Push und Veröffentlichung:** Die gezielt nachgeprüften Such-/Dialogkonflikte und die Kernabläufe ab Android 7
sind mit dieser signierten Release-APK bestanden. Der Entwicklungsstand kann zur CI gepusht werden;
ein öffentlicher Release ist damit noch nicht vollständig abgenommen. Offen bleiben insbesondere
Android Auto in DHU und Fahrzeug, physische SD-/USB-Anbieter und die übrigen nicht belegten
Kombinationen der Download-/Gerätematrix sowie der subjektive Hör-/Bild-Ton-Synchronitätstest.
Ein Tag, Push oder GitHub-Release wurde in diesem Prüflauf nicht erstellt.

### Vorbereitung von 1.2.0 „Downloads Update“ – historische Prüffolge

Die Vorbereitung bündelte Downloads, MP3 und Dateikopien mit den nachfolgenden Glass-Korrekturen
und der auswählbaren Statistikansicht unter `1.2.0` / `1000004`. Während dieser Prüfungen blieb
`1.1.0` öffentlich; die spätere Veröffentlichung von `1.2.0` ist oben dokumentiert.
Der eigenständige Podcast-Bereich bleibt ein separates späteres Vorhaben.

Nach dem ersten signierten Updatekandidaten wurden Titel-Suchergebnisse und Downloadauswahl
direkt zwischen Release und Debug auf dem Samsung verglichen: In beiden Paketen fehlen die
Track-Kacheln in der Suche; der Download-Dialog besitzt die Glaskontur, lässt aber unverwischte
Schrift der darunterliegenden Liste durchscheinen. Die Nacharbeit ergänzt die gemeinsame
Track-Fläche in Online-/Bibliothekssuche und Videotreffern sowie einen Blur der App-Ansicht hinter
gemeinsamen Material-Dialogen ab API 31. Dafür wird `RenderEffect` verwendet: Der Samsung-Test
meldet systemweiten Fenster-Blur als nicht unterstützt. Der getönte Fallback für ältere Geräte
bleibt erhalten. Der Debug-Nachtest auf dem Samsung bestätigt die Suchkacheln, die echte
Unschärfe hinter der Download-Auswahl und die wieder scharfe Liste nach Abbrechen beziehungsweise
Zurück; erneutes Öffnen funktioniert ebenfalls. Debug-Build und Release-Lint sind bestanden.
Der signierte Nachtest dieser Korrekturen ist in der Geräteprüfung vom 07.09.2026 oben dokumentiert.

Die Bibliothekssuche bindet ihre Datenbankabfrage jetzt an den aktuellen Suchtext. Zuvor blieb
die beim ersten Öffnen erzeugte Abfrage durch ein schlüsselloses `remember` unverändert, auch
wenn anschließend ein anderer Text eingegeben wurde. Der Samsung-Debugtest bestätigt die
laufende Aktualisierung: von drei Maddix-Treffern zu „Noma – Sleepwalker“, ohne den Bibliotheksreiter
zu verlassen; beide Listen tragen die gemeinsamen Track-Kacheln.

Die zusätzliche Kernabnahme, der Quellcommit, der signierte Build und dessen Updateprüfung
sind in der Geräteprüfung vom 07.09.2026 oben belegt. Noch nicht verfügbare USB-/SD-Medien und
weitere Geräte werden nicht als geprüft gewertet.

Bei der zusätzlichen Abnahme auf Android 6 / API 23 wurde ein Start-ANR gefunden: Die
Dateiprotokollierung beendet ihren Leser bei einer nicht unterstützten atomaren Log-Rotation;
der nächste Log-Aufruf wartet dadurch unbegrenzt, auch auf dem Hauptthread. Die Vorbereitung
ergänzt deshalb Androids native Umbenennung und eine begrenzte, nicht blockierende Übergabe
an den Dateilogger. Der erneute Start und die Bedienung auf API 23 sind bestanden. Vier neue
Regressionstests prüfen begrenzte Übergabe bei blockiertem Schreiben, Weiterverarbeitung nach
Schreibfehlern, Log-Rotation und den Erhalt vorhandener Logs beim Neustart.

Zusätzliche Abnahme des Kandidaten am 06.09.2026 mit dem Debug-Paket `de.kruxx.music.debug`:

Die nachfolgend genannten internen Downloads und bisherigen öffentlichen Testkopien stammen
aus der Debug-App. Sie sind kein Nachweis für Downloads mit der signierten Release-App.
Beide Pakete haben getrennte interne Speicher und Android-Dateiberechtigungen; der öffentliche
Ordnername ist gemeinsam. Die abschließende Release-Abnahme verwendet deshalb neue, direkt
mit `de.kruxx.music` heruntergeladene Dateien, ohne bestehende Benutzerdateien zu löschen.

- 137 App-Unit-Tests, Debug-Build und Release-Lint bestanden; weiterhin nur die zwei bekannten
  Media3-Versionshinweise. Die ausgediente Kermit-IO-Dateiprotokollierung wurde entfernt.
- Samsung / Android 16: „Alle Titel herunterladen“ im Filter „Offline-Songs“ mit sieben bereits
  lokal verfügbaren Originalen als MP3 nach `KruXx-Downloads/Audio` gestartet, während der
  Umwandlung abgebrochen und über „Erneut versuchen“ wiederholt. Auch nach zwischenzeitlichem
  Wechsel in den Hintergrund erreicht der Auftrag 100 %: zwei gespeichert, fünf bereits vorhanden.
  Beide neuen Dateien sind vollständig dekodierbares MP3 mit 320 kbit/s, 48 kHz, Stereo und
  passenden Künstler-/Titel-Metadaten. Alle acht bisherigen öffentlichen Dateien und 16 erfassten
  privaten Mediendateien bleiben laut SHA-256 unverändert. Das ist ein vollständiger Sammelauftrag
  mit wiederverwendeten Quellen; ein großer Auftrag mit ausschließlich neuen Netzwerkquellen ist
  dadurch nicht abgedeckt.
- Samsung: gespeichertes Caminandes-Audio spielt im Flugmodus mit deaktiviertem WLAN und
  fortschreitender Wiedergabeposition. Das gespeicherte Video zeigt unter denselben Bedingungen
  laufende Bilder und 00:40 von 01:35. Die ursprünglichen Netzwerkeinstellungen sind wiederhergestellt;
  ein subjektiver Hörtest der Bild-/Tonsynchronität ist dadurch nicht ersetzt.
- Frischer Android-6-Emulator (API 23, x86): regulärer Download von „The Emptiness Machine“ über
  den Format-/Speicherdialog, Androids Speicherberechtigung, native MP3-Umwandlung und öffentliche
  Speicherung vollständig bis 100 % bestanden. Die Datei ist vollständig dekodierbar:
  MP3 / 320 kbit/s / 48 kHz / Stereo, 200,52 Sekunden, Künstler und Titel korrekt.
  Der nach der Loggerkorrektur gestartete Prozess enthält keine Fatal-/Rotationsfehler.

Abnahme des ersten signierten 1.2.0-Kandidaten vor der Such-/Blur-Nacharbeit:

- Der vollständige Testlauf im sauberen Release-Clone besteht 137 App- und 58 Innertube-Tests
  ohne Fehler. Der frische Release-Lint enthält ausschließlich 34 Hinweise auf neuere
  Werkzeug-/Bibliotheksversionen, keine Fehler. Die Zahl unterscheidet sich durch die erneute
  Versionsabfrage vom lokalen Lint mit zwei Media3-Hinweisen.
- Das Update über 1.1.0 auf dem Samsung behält Paketidentität, Erstinstallation und die
  112 vorhandenen Bibliothekstitel. Vor dem neuen Download sind 50 interne Originale vorhanden.
- Mit der tatsächlichen Release-App neu heruntergeladen: „Kevin MacLeod – Fluffing a Duck“ als
  MP3 nach `Download/KruXx-Downloads/Audio`. MediaStore bestätigt `de.kruxx.music` als Eigentümer.
  Die Datei hat 2.696.732 Bytes, 67,416 Sekunden, 320 kbit/s, 48 kHz, zwei Kanäle und korrekte
  Künstler-/Titel-Metadaten; die vollständige Dekodierung ist fehlerfrei. Bestehende Debug-Dateien
  wurden dafür nicht gelöscht.
- Ein Suchaufruf auf API 23 beendete den ersten signierten Kandidaten. Der Fehler wurde mit einem
  ausschließlich lokalen Diagnose-Build eingegrenzt; ein Erfolg mit der
  Debug-App wird hier nicht als bestandener Release-Test gewertet.
- Bei der Diagnose wurde eine unpassende Werkzeugkombination erkannt: Kotlin 2.4 benötigt
  laut Google mindestens R8 9.1.29; AGP 8.13.2 bringt noch R8 8.13.19 mit. Der Build verwendet
  deshalb über `settings.gradle.kts` R8 9.1.43 aus Google Maven. Dessen Mapping bestätigt die
  Version, und die R8-Metadatenwarnungen entfallen. Der API-23-Suchabsturz tritt weiterhin auf;
  diese Anpassung allein wird ausdrücklich nicht als dessen Behebung gewertet. Die separate
  Metadaten-Diagnose des älteren Lint-Analysators ist davon zu unterscheiden.
- Die weitere Diagnose grenzt den Suchabsturz auf die Maschinenoptimierung der verwendeten
  Android-6-x86-Laufzeit ein: Schon `mutableFloatStateOf(Float.NaN)` bleibt nach einer Zuweisung
  von `0f` ungültig, sowohl außerhalb als auch innerhalb einer Composition. Derselbe signierte
  Diagnose-APK-Inhalt liefert mit `interpret-only` korrekte Zahlenwerte und öffnet die Suche.
  Die Emulator-Einstellung wurde anschließend zurückgesetzt. Ausnahmen für R8 an Wisch- oder
  Float-Zustandsklassen waren wirkungslos und werden nicht übernommen; die Wisch-Komponente
  bleibt unverändert. Ein zweites, unverändertes API-23-Systemabbild (x86_64) bestätigt den Fehler.
  Der erzeugte Maschinencode (`UCOMISS` / `JNE` ohne Berücksichtigung des Unordered-Flags)
  überspringt bei `NaN` fälschlich die Float-Zuweisung. Ein Entwurf für einen semantisch gleichen
  Vergleich der IEEE-754-Bits wurde ausschließlich als Diagnose gesichert; sein Build wurde
  auf Nutzerwunsch abgebrochen. Dieser ungeprüfte Entwurf ist nicht im App-Quellbaum enthalten.

Arbeitsunterbrechung auf Nutzerwunsch am 06.09.2026:

- Die Such-/Dialog-Korrekturen und die R8-Werkzeuganpassung sind in `b917ba14a` eingecheckt.
  Dieser Stand besteht im sauberen Release-Clone alle 137 App- und 58 Innertube-Tests;
  Release-Lint: keine Fehler, 34 Versionshinweise und zwei Baseline-Hinweise.
- Die Glass-Korrekturen sind mit Debug auf dem Samsung geprüft. Die installierte stabile
  Release-App und das bisherige Archiv-APK stammten zu diesem Zeitpunkt aus `b6cda8025`; die
  korrigierte endgültige Release-APK war noch nicht erstellt oder installiert. `1.2.0` war damals
  noch nicht veröffentlicht.
- Der damalige Fortsetzungsplan sah zuerst die Behebung des Android-6-Vergleichsfehlers vor.
  Dieser Schritt entfällt durch die Supportentscheidung vom 07.09.2026. Der anschließende
  Prüflauf oben ergänzt die Kernabnahme ab API 24 mit optimierten Builds, beide archivierten APKs
  aus einem sauberen Clone, Herkunfts-/Signaturprüfung und den Samsung-Nachtest.
- Diagnosequellen, das reproduzierende Diagnose-APK und die Maschinencode-Auswertung liegen lokal
  unter `/home/kruxx/Schreibtisch/Android/KruXx-release-diagnostics-20260906`.
  Das Diagnose-APK enthält Prüfcode und darf nicht veröffentlicht werden. Die Android-6-Emulatoren
  wurden beendet; Downloads und Daten auf dem Samsung bleiben erhalten.

### Technische Release-Vorprüfung vom 06.09.2026

- Der aktuelle Arbeitsbaum besteht erneut alle 133 App-Unit-Tests. Der unveränderte Innertube-Stand
  bleibt mit 58 erfolgreichen Tests `UP-TO-DATE`. Vollständiger Release-Lint und erstmals auch der
  optimierte Universal-Release-Build einschließlich der lokalen Download-/MP3-/Glass-Erweiterungen
  sind erfolgreich. Die zwei bestehenden Media3-Versionshinweise und die bekannte
  Kotlin-Metadaten-Diagnose des Build-Werkzeugs bleiben bestehen; neue Lint-Befunde gibt es nicht.
- Am erzeugten Release-APK sind ZIP-Integrität, Paketmetadaten, erhaltene native MP3-Schnittstellen
  im optimierten DEX-Code sowie beide MP3-Bibliotheken für alle vier ABIs geprüft. ELF- und
  APK-Ausrichtung erfüllen jeweils 16 KiB. LAME-Lizenz und die bisherigen Release Notes sind trotz
  Resource-Shrinking bytegleich mit ihren Quellen enthalten.
- Das war eine lokale Build-Vorprüfung vor der Versionierung des nächsten Releases: Versionsname
  und Versionscode standen dabei noch auf `1.1.0` / `1000003`, neue Release Notes und ein sauberer
  Release-Commit fehlten. Für diese Vorprüfung wurde keine neue signierte APK erstellt oder auf
  dem Handy installiert und nichts veröffentlicht. Vor Freigabe folgen der signierte Build aus dem
  finalen Commit und dessen Update-Installation mit Datenerhalt.
- Zum Zeitpunkt dieser Vorprüfung standen die praktischen Downloadfälle aus
  [`DOWNLOADS.md`](DOWNLOADS.md#prüfung-und-geräteabnahme) noch aus. Die zusätzliche Abnahme für
  1.2.0 ist oben separat beschrieben; physische USB-/SD-Medien und die übrigen Fehlerkombinationen
  sind weiterhin nicht pauschal als geprüft gewertet.

### Lokale Glass-Nacharbeit vom 06.09.2026 – veröffentlicht mit 1.2.0

- Alle app-eigenen Dialog-, Dropdown- und Bottom-Sheet-Einstiegspunkte sind im Quellcode auf
  Glasflächen und verdeckende Hintergrundflächen geprüft. Künstlerauswahl, Darstellungsvorschau,
  Download-/Kopierauswahl, Downloadverlauf, Fortschrittsdialog und Dateientfernen verwenden jetzt
  die gemeinsamen Glasbausteine; auch der separate Spiel-Dialog ist angeglichen.
  Material-Dialoge übernehmen lesbare Auswahl-/Aktionsfarben aus der KruXx-Darstellung; das gilt
  auch für den Hinweis zum Zurücksetzen des Live-Hintergrunds.
- Ein auf dem Samsung reproduzierter Fehler schnitt aufklappende Menüs ab: Die äußere Glasfläche
  blieb oberhalb des verschobenen Menüinhalts stehen. Glasfläche und Griff liegen jetzt zusammen
  im bewegten Sheet. Listen-, Raster- und Playlist-Untermenüs teilen dessen Fläche, statt mehrere
  Glaslagen übereinanderzuzeichnen. Schwebende Dialoge und Menüs erhalten eine getönte Unterlage
  für lesbare Schrift über Bildern und Listen.
- Künstlerlisten (Online/Bibliothek), lokale und Online-Playlists verwenden dieselben leichten
  Track-Kacheln wie „Titel“ und Gerätetitel: 8 dp Seitenrand, 3 dp Abstand je Ober-/Unterkante,
  feine Kontur, kein eigener Schatten oder Blur-Durchlauf pro Track. Die lokale Playlist-Kopfkarte
  ist ebenfalls angeglichen.
- Prüfung erfolgreich: 133 App-Unit-Tests sowie abschließender Debug-Build und Release-Lint.
  Die finale Debug-APK ist mit Datenerhalt auf dem Samsung SM-S931B/Android 16 installiert.
  Geprüft sind Künstler-/Playlist-Kontext- und Überlaufmenüs in Listen- und Rasterdarstellung,
  Playlist-Untermenü und neuer Playlist-Dialog, Künstlertext-Bearbeitung, Track-Kacheln sowie
  Einzel-/Sammeldownload, Kopierauswahl, Downloadverlauf und Entfernen-Bestätigung. Die Bestätigungen
  wurden abgebrochen; dafür wurden keine Medien heruntergeladen, kopiert oder entfernt.
  Die Download-Auswahl ist auch im Hellmodus mit Systemschriftfaktor 1,3 lesbar und bedienbar.
  Systemmodus, Listenmenü und ursprünglicher Schriftfaktor 0,8 wurden wiederhergestellt.
  API 24–30 sowie weitere Kombinationen aus Gerät, Darstellung und seltenen Dialogwegen bleiben
  ergänzende Nachtests; der Quellcode-Audit ist umfassender als die praktische Stichprobe.

### Lokale Filterleisten-Nacharbeit vom 06.09.2026 – veröffentlicht mit 1.2.0

- Die horizontalen Auswahlleisten hatten bisher nur transparente Standard-Chips. `ButtonsRow`
  verwendet jetzt eine gemeinsame Glasfläche mit 8 dp Seitenrand und 4 dp vertikalem Außenabstand.
  Eine blaue Auswahlfläche und Kontur markieren den aktiven Filter; die Reiter bleiben horizontal
  scrollbar. Die beiden bisher duplizierten Varianten teilen dieselbe Implementierung.
- Das gilt für Titel (Titel/Favoriten/Zwischengespeichert usw.), Künstler, Alben, Playlists,
  Downloads (Audio/Video/Nur in KruXx), Verlauf und Statistik. Die gesonderte Reiterleiste im
  Release-Änderungsdialog verwendet ebenfalls den Glasbaustein. Die horizontale Haupt-/Detailnavigation
  verwendet bereits ihre gemeinsame Glasfläche.
- Bei Künstlern und Alben liegt der zusätzliche Quellenfilter innerhalb derselben Leiste und
  reserviert eigenen Platz rechts. Er überlagert die scrollbaren Reiter nicht mehr. Zusätzliche
  Außenabstände der einzelnen Aufrufer entfallen in KruXx zugunsten einheitlicher Ränder.
- Es bleibt beim vorhandenen Glas-Fallback aus Transparenz, Verlauf und Kontur ohne zusätzlichen
  Blur-Durchlauf. Die Statistik-Karte für Titelanzahl/Wiedergabezeit und ihre Titel verwenden jetzt
  ebenfalls die gemeinsamen Glasflächen. Die Listenansicht bietet einen Titel je Zeile mit Platz
  für Künstler, Dauer und Downloadknopf. Das gilt für alle Statistikzeiträume einschließlich
  „Gesamt“; die ergänzte Ansichtsauswahl ist im folgenden Abschnitt beschrieben.
- Abschließender Debug-Build und Release-Lint sind erfolgreich; die beiden bestehenden
  Gradle-Abhängigkeitswarnungen bleiben unverändert. Die aktuelle Debug-APK ist mit Datenerhalt
  auf dem Samsung SM-S931B/Android 16 installiert. Titel, Künstler, Alben, Playlists, Downloads
  (alle drei Bereiche), Verlauf und Statistik sind geprüft. Seitliches Scrollen bei Titel und
  der reservierte Quellenfilter bei Künstlern sind auch mit Schriftfaktor 1,3 geprüft.
- Die Statistik-Karten sind in „Gesamt“ und „1 Jahr“ nachgeprüft. Alle zehn vorhandenen Ranglisten-
  Titel sind durch Scrollen erreichbar; Titelanzahl und Wiedergabezeit bleiben unverändert.
  Im aktuellen App-Prozess gab es während dieser Stichprobe keinen Fatal-/ANR-Eintrag.
  Schriftfaktor 0,8, Themenmodus „System“ und ursprüngliche Bibliotheksfilter sind wiederhergestellt.
  Die Release-Änderungsreiter sind im Debug-Build nicht erreichbar und nur im Quellcode/Build
  geprüft. API 24–30 und weitere Geräte-/Darstellungskombinationen bleiben ergänzende Nachtests.

### Auswählbare Statistikansicht vom 06.09.2026 – veröffentlicht mit 1.2.0

- Die KruXx-Titelstatistik bietet neben der Zeitraumüberschrift die Auswahl „Liste / Raster“.
  Liste zeigt einen Titel je Zeile und ist die Voreinstellung; Raster zeigt zwei kompakte
  Glaskarten nebeneinander. Die Auswahl wird unabhängig von anderen Darstellungsoptionen
  gespeichert und gilt für alle Statistikzeiträume, auch nach einem App-Neustart.
- Rasterkarten ordnen Cover mit Rangnummer, Dauer und Downloadaktion oberhalb von Titel und
  Künstler an. Downloadstatus, Favoriten-/Playlist-/Explicit-Markierungen, Wiedergabe und
  langes Drücken für das Titelmenü verwenden weiterhin die vorhandenen Bausteine und Aktionen.
  Künstler-, Alben- und Playliststatistiken behalten ihre jeweilige Darstellung.
- Abschließender Debug-Build und Release-Lint sind erfolgreich, ohne neue Lint-Warnungen.
  Die aktuelle Debug-APK ist mit Datenerhalt auf dem Samsung SM-S931B/Android 16 installiert.
  Liste und zweispaltiges Raster, Scrollen bis zum letzten Titel, Zeitraumwechsel sowie
  Downloadauswahl und Titelmenü sind geprüft; beide Dialoge wurden ohne Medienänderung geschlossen.
  Nach einem kalten App-Neustart wurde das gespeicherte Raster wiederhergestellt; anschließend
  wurde auf die voreingestellte Liste zurückgeschaltet. Statistikwerte, Schriftfaktor 0,8,
  Themenmodus „System“ und Bibliotheksfilter sind unverändert. Der aktuelle App-Prozess zeigt
  während der Stichprobe keine Fatal-/ANR-Einträge. Weitere Gerätegrößen bleiben Nachtests.

### Lokale Weiterentwicklung vom 05.09.2026 – veröffentlicht mit 1.2.0

- Alle manuellen Downloadaktionen einschließlich „Alle Tracks downloaden“, Album, Künstler,
  Playlist und Auswahl verwenden einen gemeinsamen Dialog einmal je Auftrag: „Nur in KruXx“,
  zusätzlich im automatisch angelegten `Download/KruXx-Downloads` oder zusätzlich in einem anderen
  Ordner speichern; Originalaudio oder MP3 mit festen 320 kbit/s. „Nur in KruXx“ bleibt die anfängliche
  Vorgabe, der öffentliche Ordner ist optional. Die Auswahl lässt sich
  merken und unter Einstellungen → Daten → Downloads und Dateikopien zurücksetzen.
- Videos besitzen ein Downloadsymbol mit Video inklusive Ton oder MP3. Gespeicherte Videos laufen
  lokal über ExoPlayer; gespeicherte MP3s und Videos sind über die Download-Einstellungen abrufbar.
- Normale Audiotitel besitzen rechts einen gleichwertigen Downloadknopf für Originalaudio/MP3 und
  alle drei Speicheroptionen, auch in Such-, Künstler-, Album- und Playlistlisten. Bereits fertige
  Downloads können darüber nachträglich konvertiert/kopiert werden; der Knopf entfernt sie nicht.
- Der öffentliche Downloadordner trennt jetzt `Audio` (MP3 und Originalaudio) und `Video`.
  Ein zusätzlicher Hauptreiter „Downloads“ zeigt den aktuellen Ordnerinhalt und interne Downloads.
  Die Hauptleiste bleibt horizontal scrollbar; `<<`/`>>` erscheinen nur bei tatsächlich verborgenen
  Reitern und erhalten eigene Randflächen. Fertige eigene Altdateien werden auf Android 10+ aus
  dem Stammordner übernommen; fremde Dateien bleiben erhalten.
- Im Downloads-Reiter können Dateien direkt über das Papierkorbsymbol entfernt werden.
  „Auswählen“ oder langes Drücken öffnet die Mehrfachauswahl; „Alle auswählen“ gilt für den
  aktuellen Bereich. Vor dem dauerhaften Entfernen werden Anzahl, Dateien und Speicherort
  bestätigt. Öffentliche Kopien, internes Originalaudio, MP3 und Video sind getrennt auswählbar.
  Teilerfolge und fehlgeschlagene Dateien werden angezeigt; eine Wiederholung betrifft die
  fehlgeschlagene Auswahl. Automatische Prüfung und Samsung-Gerätetest sind erfolgreich:
  Einzel-/Mehrfachlöschung öffentlicher Dateien und interner MP3s/Videos, Abbrechen sowie
  „Alle auswählen“ wurden mit eigenen Testdateien geprüft. Andere Kopien und Formate bleiben
  erhalten. Alle Testdateien sind entfernt; Namen und SHA-256 der vorhandenen Downloads sind
  unverändert (7 öffentliche, 5 interne Mediendateien und 9 Dateien im Originalaudio-Cache).
  Der abschließend geprüfte Debug-Build ist auf dem Samsung-Gerät installiert; Auswahl,
  Bestätigung und Abbrechen sind auch nach kaltem App-Start geprüft.
- Die Breite aller beschrifteten Reiter richtet sich nun gemeinsam nach der längsten Beschriftung
  und der aktuellen Schriftgröße. Symbol und Text sind mittig ausgerichtet; spätere Reiter wie
  Podcast werden ohne feste Reiterzahl berücksichtigt. Der deutsche Reiter heißt jetzt „Künstler“.
  Nachtest auf dem Samsung-Gerät: gleiche Reiterbreiten und zentrierte Inhalte bei Systemschrift
  0,8 und 1,3; Wischen, Richtungspfeile und Sichtbarkeit ausgewählter Randreiter geprüft.
  Debug-Update installiert, Build und Release-Lint ohne neue Befunde erfolgreich.
- „Dateien kopieren …“ neben vorhandenen Sammeldownload-Aktionen und im Titelmenü bietet eine
  Auswahl fertiger Downloads aus dem jeweiligen Kontext. Androids Ordnerauswahl ermöglicht
  Gerätespeicher, SD-Karte und angeschlossene USB-Laufwerke, soweit deren Anbieter dies unterstützt.
- Kopien lesen ausschließlich vollständige Download-Dateien. Sie erhalten App-Downloads und
  vorhandene andere Zieldateien, prüfen SHA-256 und überspringen identische Kopien. Dateiaufträge
  laufen persistent über WorkManager, mit Fortschritt, Abbruch und Wiederholung fehlgeschlagener
  oder abgebrochener Aufträge. Die Media3-Audioqueue wird unabhängig verwaltet.
- Manuellen Einzel-, Sammel- und Kopieraufträgen folgt eine moderne Fortschrittskarte mit
  animiertem Verlaufsbalken, Prozentzahl, Arbeitsschritt, Format und Speicherziel. Sie lässt sich
  von Beginn an über ein dezent rötliches Glas-× oder seitliches Wischen ausblenden. Die Entscheidung
  bleibt pro Auftrag über Reiterwechsel und Neustarts erhalten; neue Aufträge dürfen wieder erscheinen.
  Ausgeblendete Aufträge bleiben über das Zahnrad im Downloads-Reiter abrufbar und laufen weiter.
  „Abbrechen“ ist eine separate Aktion. 100 % erscheinen erst nach
  vollständiger Speicherung einschließlich Prüfung; Fehler und Abbruch bleiben unterscheidbar.
  Die Android-Benachrichtigung zeigt denselben Fortschritt. Automatische Aufträge öffnen keinen Dialog.
  Auf dem Samsung-Gerät wurde Noma über die Videosuche erneut in MP3 umgewandelt: laufende
  Prozentwerte und korrekter Zielpfad, anschließend 100 % mit bestätigter vorhandener Kopie.
  Die private Ausgabe ist bytegleich mit der bisherigen öffentlichen 320-kbit/s-MP3. Diese war
  bereits vorher in `Audio` vorhanden und ist auch im Android-Dateimanager sichtbar. Zusätzlich
  wurde eine neue Originalaudio-Kopie mit erfolgreicher Veröffentlichung und Abschlussanzeige geprüft.
  Nachtest vom 06.09.2026: × und beide Wischrichtungen geprüft. Die laufende Umwandlung von
  „Sons Of Hidden - Nirvana“ wurde bei 53 % ausgeblendet, zeigte im Verlauf anschließend 59 %
  und wurde erfolgreich im Audio-Ordner abgeschlossen. Kurze Wischgesten behalten die Karte;
  vollständiges Ausblenden bleibt nach einem kalten App-Neustart bestehen. Der Verlauf bleibt
  über das Zahnrad erreichbar, die Audio-Übersicht enthält einschließlich Nirvana fünf Dateien.
- Download-Beschriftungen und Dateinamen verwenden Künstler und Titel, z. B.
  `Noma - Sleepwalker.mp3`. Bei Videotiteln im entsprechenden Schema entfällt der Uploader;
  normale Musikmetadaten und musikalische Versionsangaben bleiben erhalten. Technische IDs und
  Format-Zusätze entfallen im Namen. Bekannte Altdateien werden unter Wahrung von Schreibrechten,
  Inhalt und Namenskonflikten umbenannt; Format und Speicherpfad stehen separat in der Übersicht.
  Auf dem Samsung-Gerät wurden sechs bestehende öffentliche Dateien bytegleich umbenannt;
  `Audio/Noma - Sleepwalker.mp3`, Download-Anzeige und Kopierauswahl sind geprüft. Erneutes
  MP3-Kopieren erkennt die umbenannte Datei und erzeugt keine zusätzliche Kopie. Debug-Update installiert.
- MP3 nutzt Androids Decoder und den separat gebauten LAME-Encoder 3.100 (LGPL-2.0-or-later),
  CBR 320, Qualität 0, Mono/Stereo ohne Normalisierung. Beste verfügbare Quelle bedeutet keine
  Wiederherstellung bereits verlorener Audiodetails. Fertige Alt-Downloads werden weiterverwendet.
- Prüfung dieses Arbeitsstands: nativer JNI-Encoder für 24/44,1/48 kHz einschließlich Dekodierung,
  Bitrate, Kanalzahl und Dauer erfolgreich. Alle 133 App-Tests (darunter 45 neue Download-/Benennungs-/Fortschritts-/Lösch-/SAF-/MediaStore-Tests)
  und 58 Innertube-Tests erfolgreich; Universal-Debug-APK gebaut. Release-Lint erfolgreich ohne
  neue Fehler; zwei Versionshinweise betreffen die weiterhin einheitlich auf 1.10.1 gepinnten
  Media3-Komponenten. Die Lint-Baseline wurde nicht verändert. APK-Lizenztext und 16-KiB-Ausrichtung
  beider nativer Bibliotheken für alle vier ABIs wurden geprüft.
- Gerätetest auf Samsung SM-S931B / Android 16: Debug-Update mit erhaltenen App-Daten installiert;
  drei Speicheroptionen und Kopierauswahl geprüft. Video-Audio auf dem Gerät nach MP3 umgewandelt,
  `Download/KruXx-Downloads` automatisch angelegt, Datei außerhalb der App ausgelesen und vollständig
  dekodiert: 320 kbit/s, 48 kHz, Stereo, 94,8 Sekunden, Titel und Interpret erhalten. Erneutes Kopieren
  überspringt die identische Datei. Video mit Ton (H.264 1080p / Opus Stereo) vollständig dekodiert,
  lokale Videowiedergabe gestartet und eine über Androids Ordnerauswahl erzeugte Kopie per SHA-256
  gegen die App-Datei geprüft. Weitere Gerätefälle und physisches USB/SD bleiben offen;
  dies ist noch keine vollständige Gerätefreigabe.
- Audiotitel-Ergänzung auf demselben Handy geprüft: Neuer Knopf in Titelsuche und Titelreiter;
  vorhandenes Originalaudio nachträglich als MP3 in `KruXx-Downloads` gespeichert, extern mit
  320 kbit/s / 48 kHz / Stereo und erhaltenen Metadaten vollständig dekodiert. Sammeldialog mit
  51 Titeln und allen Speicheroptionen geöffnet und vor dem Start abgebrochen. Aktualisierte
  Debug-App installiert; 103 App-Tests, Debug-Build und Release-Lint erneut erfolgreich.

- Ordner-Nachtest: Vorhandene MP3s nach `Audio` übernommen und per SHA-256 unverändert bestätigt.
  Originalaudio landet ebenfalls dort, Videokopien bytegleich unter `Video`; Öffnen in VLC geprüft.
  Alte über die Ordnerauswahl erstellte Kopien bleiben über die bestehende Freigabe sichtbar.
  Die neue Übersicht und die Richtungsanzeigen der Hauptleiste wurden auf dem Handy geprüft.

Architektur, Grenzen und konkrete Abnahmematrix: [`DOWNLOADS.md`](DOWNLOADS.md).
Diese Änderungen sind mit 1.2.0 veröffentlicht. Öffentliche APK und historische
Release Notes der veröffentlichten Version 1.1.0 bleiben unverändert.

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

Dieser Abschnitt bewahrt die historischen Nachweise für 1.1.0 und 1.0.2. Maßgeblich für 1.2.0 sind
die [signierte Geräteprüfung vom 07.09.2026](#signierte-geräteprüfung-vom-07092026) und der
[Self-Updater-Nachtest](#self-updater-nachtest-nach-veröffentlichung) in §1.

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
- **Der Self-Updater ist für 1.1.0 → 1.2.0 praktisch geprüft, mit einer belegten API-24-Grenze:**
  Auf API 36 sind automatische Erkennung beim Online-Kaltstart, Download, APK-Prüfung und
  Installation mit erhaltener Playlist und Erstinstallationszeit bestanden. Auf API 24 sind
  automatische Erkennung nach Offline→Online-Wechsel, manuelle Prüfung trotz Intervall,
  Offline-Fehlermeldung und Dialog-Deduplizierung belegt. Der System-Downloadmanager des
  API-24-Emulators lehnt jedoch die TLS-Zertifikatskette des APK-Downloads ab. Bei diesem Fehler
  die signierte APK auf einem aktuellen Rechner laden, übertragen und über die bestehende App
  installieren; dieser manuelle Upgrade-Weg ist auf API 24 bestanden. HTTP-5xx-Injektion,
  vollständige 24-Stunden-Grenze und weitere reale Geräte bleiben Nachtests. Die Einzelversuche
  und ihre Grenzen stehen im [Self-Updater-Nachtest](#self-updater-nachtest-nach-veröffentlichung).
  Debug-Builds deaktivieren den Self-Updater und können diese Nachweise nicht ersetzen.
- YTM, GitHub, Metadaten- und Liedtextdienste sind externe Dienste. Antwortformate, regionale
  Verfügbarkeit, Kontoversuche und CDN-Tempo können sich ohne App-Update ändern.
- Die Podcast-Autoplaylist „Neue Folgen“ (`RDPN`) ist keine reguläre Musik-Playlist. Ihr
  Multirow-Antwortformat wird vom derzeitigen Playlist-Parser nicht abgebildet. Sie soll nicht als
  Einzelfall in den Musik-Playlistpfad gedrückt, sondern später Bestandteil eines eigenständigen
  Podcast-Bereichs mit Episodenstatus und Wiederaufnahme werden.
- **Historischer Glass-Nachweis für Release `1.1.0`:** Er umfasst die beiden Umsetzungsstufen des
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
  geplanten Podcast-Reiter. Für die bereits horizontal scrollbar ausgelegte Navigation waren erst bei
  tatsächlich gemessenem Überlauf seitliche `<<`-/`>>`-Hinweise vorgesehen: jeweils nur dort, wo
  weitere Reiter verborgen sind, beide zwischen den Scrollgrenzen und keinen, solange sämtliche
  Reiter passen. Diese Kennzeichnung war in 1.1.0 noch nicht aktiv; seit 1.2.0 ist sie mit dem
  sechsten Reiter „Downloads“ veröffentlicht
  (siehe [Hauptnavigation](Design.md#bedingte-erweiterung-der-hauptnavigation)). Der Vorschlags-Downloadbutton
  beansprucht nicht mehr die Titelzeile. Vorschläge und „Top Artists“ sind im Hochformat auf rund 65 Prozent der
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
  Für 1.2.0 bleiben die breitere Darstellungsmatrix mit Hellmodus, großer Systemschrift und
  API 24 bis 30 sowie weitere Performanceprüfungen offen. Der inzwischen veröffentlichte
  Dialog-Backdrop-Blur ab API 31 und der API-24-Fallback sind in der
  [signierten Geräteprüfung](#signierte-geräteprüfung-vom-07092026) belegt; weitere geeignete
  ruhende Oberflächen bleiben mögliche Designnacharbeiten.
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
- Für den öffentlichen `v1.2.0` sind Quellstand, beide Submodul-Commits, Tag und das bytegleich
  veröffentlichte Archiv-APK in [§1](#veröffentlichung-von-120-am-07092026) belegt. Die nicht
  separat protokollierten Detailfälle bleiben Bestandteil der Regressionstest-Matrix.
  Der oben beschriebene API-24-Downloadfehler ist eine bekannte Einschränkung.

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
