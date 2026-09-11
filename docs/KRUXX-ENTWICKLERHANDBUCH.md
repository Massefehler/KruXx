# KruXx – Entwicklerhandbuch (Wiedereinstieg, Weiterentwicklung, Bugfixing)

Stand: 11.09.2026 · KruXx `1.2.1` veröffentlicht · `1.2.2` vorbereitet ·
historische Basis: Kreate `main` @ `f02577e8` (v2.2.3)

Der verbindliche lokale Produkt-, Prüf- und Freigabestand steht in
[`KRUXX-IST-STAND.md`](KRUXX-IST-STAND.md); die Einordnung aller Dokumente in
[`README.md`](README.md). KruXx `1.2.1` (`1000005`) ist veröffentlicht.
Alle 204 Unit-Tests, vollständiger Release-Lint und CI sind bestanden. Die exakt archivierte,
signierte APK ist auf eigenen Android-7-/API-24- und Android-16-/API-36-Emulatoren geprüft,
einschließlich Updates von 1.2.0 mit erhaltenen Testplaylists. Die vorherigen Samsung-Nachweise
betreffen den Debug-Code; das Handy war für die finale signierte Prüfung nicht per ADB erreichbar.
Quellcommit `756f5fb8a8a4282c136128569c7d5f46c759c949`, annotiertes Tag `v1.2.1`, Submodul-Pins
und die bytegenau geprüfte öffentliche APK sind unter
<https://github.com/Massefehler/KruXx/releases/tag/v1.2.1> verfügbar.

`1.2.1` ergänzt die unten dokumentierten Navigations- und Download-Korrekturen, den gemeinsamen
Startseiten-Klick auf Header-Icon und Wortmarke sowie die sichere Bereinigung des Wiedergabedienstes.
Auf beiden APIs sind drei vollständige Dienst-Neuerstellungen im selben Prozess ohne verwaiste
Mediensitzung bestanden; anschließende Wiedergabe, Benachrichtigung und Vordergrundbetrieb sind geprüft.
Artefakte, Hashes und konkrete Nachweise stehen im [IST-Stand](KRUXX-IST-STAND.md).

Die Download-/Dateifunktionen und grundlegenden Glass-Ergänzungen stammen aus `1.2.0`. Die Veröffentlichung
ist nach Offenlegung der übrigen Gerätefälle ausdrücklich beauftragt; insbesondere Android Auto,
physische SD-/USB-Anbieter und die breitere Interaktionsmatrix gelten dadurch nicht als bestanden.
Der IST-Stand hält Freigabeentscheidung, Artefakte und konkrete Nachweise fest.

Seit der Supportentscheidung vom 07.09.2026 benötigt KruXx ab `1.2.0` Android 7.0 / API 24.
`composeApp/build.gradle.kts` setzt dafür `minSdk = 24` im Flavor `kruxx`; die gemeinsame Vorgabe
im Versionskatalog bleibt für geerbte Flavors und Bibliotheksmodule bei API 23. Der letzte
veröffentlichte Android-6-kompatible Release ist `1.1.0`. Die API-23-Diagnose bleibt historisch;
der dortige Float-/NaN-Fehler wird nicht weiter für KruXx umgangen. Die aktuelle Geräteabnahme
beginnt mit API 24 und muss insbesondere die optimierte Release-Ausführung prüfen.

Die API-23-Abnahme für 1.2.0 fand außerdem einen ANR bei der bisherigen Kermit-IO-Log-Rotation.
`RuntimeFileLogWriter` rotiert deshalb über Androids `Os.rename`; `NonBlockingLogWriter` hält
Dateizugriffe in einem einzelnen Hintergrundthread mit höchstens 64 wartenden Einträgen.
Bei Rückstau werden ältere wartende Dateilog-Einträge verworfen; der unabhängige Plattformlogger
erhält weiterhin seine Meldungen. Vier Regressionstests und ein erneuter Start samt vollständigem
MP3-Download auf Android 6 sind bestanden. Die bestehende Log-Größen-/Anzahleinstellung gilt weiter.

KruXx ist ein eigenständiger, öffentlicher Fork von
[Kreate](https://github.com/knighthat/Kreate) (RiMusic/ViMusic-Linie). Quellcode und Releases liegen
unter <https://github.com/Massefehler/KruXx>. Drei zentrale Unterschiede zum Original:

1. **Playback läuft über die Bibliothek [InnerTubeX](https://github.com/MetrolistGroup/innertubex)**
   statt über den eingefrorenen WebView-Cipher-Port – YouTube-Änderungen werden durch ein
   Versions-Update der Bibliothek behoben, nicht durch Handarbeit am Cipher-Code.
2. **Eigenes Branding als Produkt-Flavor `kruxx`**: App-ID `de.kruxx.music`, Name „KruXx“ und
   eigenes Icon. Installiert sich neben der offiziellen Kreate.
3. **Eigene Release-Linie und eigener Updater**: SemVer ab `1.0.0`; ausschließlich Releases aus
   `Massefehler/KruXx`, deren Hash, Paket-ID, Versionscode und Signatur vor der Installation geprüft werden.

---

## 0. Grundregeln

- **Niemals etwas zu `knighthat/Kreate` hochladen.** Das schreibgeschützte Remote heißt `upstream`
  und hat die deaktivierte Push-URL `DISABLED`. `origin` zeigt ausschließlich auf
  `Massefehler/KruXx`. Keine KruXx-Logs, Issues, PRs oder Builds an Upstream senden.
- Kreate/KruXx ist **GPL-3.0**. Zu jeder an Dritte verteilten offiziellen APK wird der passende
  vollständige Quellstand über das gleichnamige Git-Tag öffentlich zugänglich gemacht. `LICENSE`,
  `NOTICE.md` und die Kreate/RiMusic-Credits bleiben erhalten.
- `.ignore.d/` (Keystore, Passwörter) und `local.properties` sind gitignored und dürfen nie
  committet werden. Das gilt ebenso für GitHub-Tokens, Cookies, OAuth-Client-Secrets, private
  API-Schlüssel und manuell exportierte Crashlogs wie `docs/crash-report.txt`. Letztere sind keine
  Dokumentation und werden ohne ausdrückliche, lokale Freigabe weder geöffnet noch weitergegeben.
- GitHub-Secret-Scanning-Alarme immer klassifizieren (§3.2). Bei einem gültigen Geheimnis zuerst
  widerrufen/rotieren; bloßes Entfernen aus der aktuellen Datei beseitigt den Fund in alten Commits
  nicht. Schlüsselwerte niemals in Logs, Dokumentation, Issues oder Chat kopieren.
- Aktuellen Funktions- und Prüfstatus zuerst in `docs/KRUXX-IST-STAND.md` pflegen. Historische
  Changelogs bleiben unverändert; geerbte Kreate-/Drittanbieter-Dokumente werden nicht als aktueller
  KruXx-Stand umgeschrieben.

---

## 1. Repo-Struktur und Branch-Modell

```
main             = eigenständige KruXx-Entwicklung und öffentlicher Release-Branch
kreate-upstream  = optionale lokale Referenz auf upstream/main; nie veröffentlichen oder direkt mergen
```

Kreate-Änderungen werden nicht automatisch übernommen. Bei Bedarf werden einzelne Änderungen erst
geprüft und anschließend bewusst für KruXx nachimplementiert oder selektiv übernommen.

Wichtige Pfade:

| Pfad | Zweck |
|---|---|
| `composeApp/` | Die App (Kotlin Multiplatform; Android in `src/androidMain`, Flavor-Quellen in `src/android<Flavor>`) |
| `composeApp/src/androidKruxx/` | Nur KruXx: sicherer GitHub-Updater (`kotlin/`) und Icon-Ressourcen (`res/`, generiert) |
| `modules/innertube` | Submodul: `me.knighthat.innertube` (öffentlicher KruXx-Spiegel) – anonymer Playlist-Browse, Charts und Song-Metadaten; **nicht** die sichtbare Suchergebnisseite |
| `modules/metrolist/innertube` | Submodul: KruXx-Spiegel von Metrolists altem InnerTube-Modul (Basis `kreate-old`) – YouTube-Login, Bibliothek/Alben/Künstler, `YouTube`-Session und angemeldete Playlistseiten |
| `modules/kizzy` | Submodul: Discord-RPC |
| `scripts/strings-modifier` | Submodul: Dev-Skript, nicht Teil des Builds |
| `extensions/` | Kreates eigene Module; `extensions/innertube` („oldtube“, Gradle-Modul `:oldtube`) liefert die sichtbare YTM-Suche und Suchvorschläge, daneben kugou/lrclib/discord |
| `icons/KruXx_App_Icon.png` | Freigestelltes RGBA-Quellbild des KruXx-Icons (1254², rotes „K“ mit blauer Audiowelle auf dunkler Metallkachel) |
| `icons/animation/` | Zehn hochauflösende, freigestellte RGBA-Quellframes des KruXx-Startscreens; der Unterordner `old/` enthält verworfene lokale Vorstufen und ist bewusst gitignored |
| `scripts/build-local-release.sh` | Release bauen **und** signieren |
| `scripts/make-kruxx-icon.py` | Alle Icon-Ressourcen aus dem Quellbild erzeugen |
| `patches/innertube/` | Sicherung und nachvollziehbarer Diff der KruXx-Änderung im Submodul (§2.3) |
| `docs/changelogs/kruxx/<versionName>.txt` | Versionshinweise der KruXx-Release-App; werden nach `androidKruxx/res/raw/release_notes.txt` kopiert. Debug überlagert diese Ressource mit eigenen Platzhalterhinweisen. Format: Abschnitt als `Überschrift:`, Einträge als `- Text`; Details im [Changelog-Verzeichnis](changelogs/kruxx/README.md) |
| `docs/KRUXX-IST-STAND.md` | verbindlicher aktueller Funktions-, Prüf-, Artefakt- und Freigabestatus |
| `docs/KRUXX-ENTWICKLERHANDBUCH.md` | diese Datei |
| `docs/README.md` | Dokumentationsindex und Abgrenzung zu historischen/geerbten Dateien |

Submodule nach einem frischen Clone: `git submodule update --init` (oder direkt
`git clone --recurse-submodules …`).

---

## 2. Was gegenüber Kreate geändert wurde (und warum)

### 2.1 Playback → InnerTubeX (Commit „playback: …“)

Hintergrund: Kreate 2.2.3 nutzte einen Port von Metrolists WebView-Cipher (`YTPlayerUtils`,
`cipher/`), dessen Selbstheilung Konfigurationen von `MetrolistGroup/Metrolist` nachlud.
Metrolist hat diese Dateien am 21./25.08.2026 gelöscht und ist auf InnerTubeX umgestiegen –
Kreates Wiedergabe brach ab, Upstream-`main` ist seit 10.07.2026 eingefroren
(Maintainer arbeitet an einem „backend overhaul“, siehe Issue #1918).

| Datei | Änderung |
|---|---|
| `gradle/libs.versions.toml` | `innertubex = "v0.3.0"` (JitPack `com.github.MetrolistGroup.innertubex:innertubex`), Ktor 3.5.0 → 3.5.2 (Vorgabe der Bibliothek), `nanojson` entfernt |
| `composeApp/build.gradle.kts` | `implementation(libs.innertubex)` in `androidMain` |
| `gradle.properties` | `android.experimental.disableCompileSdkChecks=true` – das InnerTubeX-AAR deklariert `minCompileSdk=37`, Google hat aber noch kein `platforms;android-37` veröffentlicht. **Entfernen, sobald compileSdk ≥ 37 möglich ist.** |
| `composeApp/proguard-rules.pro` | `-keep` für `com.metrolist.innertubex.**` und `com.dokar.quickjs.**` |
| `com/metrolist/music/utils/InnerTubeXPlayer.kt` | **neu** – einziger Einstieg zur Stream-Auflösung (Spiegel von Metrolists gleichnamiger Datei, damit Diffs gegen Metrolist einfach bleiben) |
| `app/kreate/di/InnertubeResolvingDataSource.kt`, `PlaybackResolutionPolicy.kt` | neu geschrieben: Cache mit Ablauf und 30-s-Sicherheitsabstand, CDN-Header, getestetes Fehler-Mapping – **ohne** Bounded-Range-Chunking (§4). Playback-URLs werden zusätzlich nach Qualitäts-/Netzrichtlinie getrennt; der Resolver übernimmt das Explicit-Flag des erzeugenden `MediaItem`, bevor ein paralleler Room-Upsert beendet sein muss. Das itag der gecachten Bytes liegt in den Cache-Metadaten (`kruxx_itag`); bei Formatwechsel wird der Byte-Cache per `CachedFormatMismatchException` verworfen |
| `app/kreate/di/PlayerModule.kt`, `ErrorHandlingPolicy.kt` | CDN-User-Agent als Default-Header (`setDefaultRequestProperties`) statt `setUserAgent()`; die InnerTubeX-Header je Client (User-Agent/Referer/Origin) überschreiben ihn. Der CDN-Transport verwendet eine vom HTTP-Logger befreite Kopie des App-Clients, behält aber Proxy, DNS und funktionale Interzeptoren. Eine MediaSource-Hülle registriert den Explicit-Hint synchron vor der ersten Auflösung. HTTP 403/410/416 werden nicht gegen dieselbe signierte URL wiederholt, sondern sofort an die Recovery gemeldet |
| `app/kreate/android/service/player/ExoPlayerListener.kt`, `StatefulPlayerImpl.kt` | `tryRecoverPlaybackError()` verwirft bei HTTP 403/410/416 die URL und löst neu auf; bei Parser-/Cache-Fehlern (`PARSING_CONTAINER_*`, `READ_POSITION_OUT_OF_RANGE`, `FILE_NOT_FOUND`, `CachedFormatMismatchException`) löscht es den Byte-Cache des Songs. Danach folgen `prepare()` und `seekTo()` an gleicher Position (max. 3×/Song, Budget wird bei Songwechsel zurückgesetzt). Ein Wechsel von Audioqualität oder Datensparrichtlinie invalidiert alle gespeicherten Playback-URLs; Download-URLs bleiben getrennt |
| `com/metrolist/music/utils/potoken/*` | auf Metrolists aktuellen Stand gebracht (Renderer-Tod, Timeouts), Timber → Kermit, OkHttp via Koin |
| `it/fast4x/rimusic/MainApplication.kt` | `InnerTubeXPlayer.initialize()` + Prewarm im Hintergrund |
| gelöscht | `YTPlayerUtils.kt`, `cipher/*`, `assets/solver/*`, `player_configs.json`, `player_dates.json`, totes `it/fast4x/rimusic/extensions/webpotoken/*` |

### 2.2 KruXx-Flavor (Commit „kruxx: …“)

| Datei | Änderung |
|---|---|
| `composeApp/build.gradle.kts` | Flavor `kruxx` (Dimension `platform`): `applicationId = "de.kruxx.music"`, `APP_NAME = "KruXx"`; Label über `androidComponents.onVariants` gepinnt (Build-Type-/Env-Placeholder würden sonst gewinnen); APK-Name `KruXx-*.apk`; `BuildConfig.REPO_NAME`; Flavor-Schalter deaktivieren den Upstream-CrashReport-Dialog, wählen die Startseite als Standard, aktivieren die lokale Spracheingabe und setzen Größe/Breite der Header-Wortmarke |
| `composeApp/build.gradle.kts` (Version) | **Eigene KruXx-Version**: `KRUXX_VERSION_NAME` folgt `MAJOR.MINOR.PATCH`; `KRUXX_VERSION_CODE` steigt bei jedem veröffentlichten APK strikt an. Startwert `1_000_000` liegt über allen verteilten `2.2.3-kruxx.x`-Builds. `copyKruxxReleaseNote` kopiert `docs/changelogs/kruxx/<versionName>.txt` ins APK und bricht bei fehlender Notiz ab |
| `me/knighthat/utils/Repository.kt` | Repository-Links werden je Flavor aus `BuildConfig.REPO_OWNER`/`REPO_NAME` gebildet; KruXx verweist nur auf `Massefehler/KruXx` |
| `composeApp/src/androidKruxx/kotlin/…/updater` | Eigener Updater: stabile GitHub-Releases, SemVer-Vergleich, exakter Assetname, HTTPS-/Repo-Bindung, SHA-256-, Paket-, Versions- und Signaturprüfung. Automatische Prüfungen laufen foreground- und netzgebunden, reagieren auf Netzrückkehr, wiederholen nur vorübergehende Fehler begrenzt und setzen das 24-Stunden-Intervall erst nach erfolgreicher Antwortprüfung; parallele Jobs und doppelte Dialoge werden verhindert |
| `composeApp/src/androidKruxx/res/…` | generierte Icon-Ressourcen (siehe §5) |
| `StartupSplash.kt`, `MainActivity.kt`, `KruxxGlass.kt`, `androidKruxx/res/drawable-nodpi/kruxx_startup_frame_*.webp` | KruXx zeigt nur beim kalten Activity-Start einen blockierenden Vollbild-Overlay mit zehn vorab decodierten Frames. Ein Frame dauert 120 ms; 2,4 Sekunden ergeben exakt zwei Durchläufe, anschließend blendet der Overlay 420 ms aus. Die App komponiert darunter bereits weiter. Der Overlay zeichnet über eine explizit mit den aktuellen Palettenfarben versorgte `kruxxAppBackground`-Variante denselben Graphit-/Aura-Hintergrund wie die App, da er außerhalb des `LocalAppearance`-Providers liegt. Die 600²-WebP-Frames besitzen echte Transparenz statt schwarzer Außenflächen. Frame 1 erscheint beim Start unmittelbar; alle folgenden Wechsel blenden ausgehendes und eingehendes Bild über die vollständigen 120 ms linear ineinander. Eine gedämpfte Skalierungs-/Leuchtbewegung bleibt erhalten |
| `Preferences.kt`, `MainApplication.kt` | KruXx-Standardseite ist `HomeScreenTabs.QuickPics` („Startseite“). Eine einmalige Migration setzt auch den bisher gespeicherten Standard `Songs` („Titel“) um; spätere manuelle Änderungen bleiben erhalten |
| `Preferences.kt`, `Player.kt`, `PlayerAppearance.kt` | Bei neuen App-Daten startet der Vollbild-Player mit `PLAYER_SHOW_THUMBNAIL=true`, `PLAYER_BACKGROUND=BlurredCoverColor` und `PLAYER_BACKGROUND_BLUR=true`: vollständiges Cover als Vordergrund vor dem unscharfen Coverhintergrund. Der Schalter „Cover im Player anzeigen“ und der Doppeltipp auf den Player-Hintergrund ändern den ersten Wert dauerhaft. Updates setzen bestehende Werte nicht zurück; Debug und Release speichern sie paketbedingt getrennt |
| `CrashReportDialog.kt`, `AppNavigation.kt` | Für KruXx wird der CrashReport-Dialog samt Link zum Kreate-Issue-Tracker nicht aktiviert. Der `CrashHandler` schreibt weiterhin ausschließlich ein lokales Log für die eigene Diagnose |
| `ChangelogsDialog.kt`, `ChangelogParser.kt` | Parser akzeptiert Kreates Format sowie ältere KruXx-Notizen (`•` und Folgezeilen); bei null Abschnitten wird kein Pager gerendert, damit fehlerhafte Release-Notes den App-Start nicht mehr abstürzen lassen |
| `SearchScreen.kt`, `OnDeviceVoiceSearch.kt` | KruXx zeigt bei leerer Online-/Bibliothekssuche ein Mikrofon. Es wird ausschließlich `createOnDeviceSpeechRecognizer()` (Android 12+) verwendet; **kein** allgemeiner bzw. Cloud-fähiger Fallback. Die Berechtigung wird erst beim Tippen angefragt und das Ergebnis nur editierbar eingesetzt, nicht abgeschickt. Bei fehlendem lokalen Sprachmodell erscheint ein Hinweis |
| `src/kruxx/AndroidManifest.xml` | KruXx-spezifische Package-Visibility-Abfrage für Spracheingabe sowie Installationsberechtigung und eng begrenzter `FileProvider` für verifizierte Update-APKs |
| `SearchResultScreen.kt`, `SearchResultProvider.kt` | Such-Cache berücksichtigt Sprache/Region und Konto/Anonym-Modus. Der Reiter „Titel“ führt die YTM-Filter **Titel und Videos parallel** aus, hängt abspielbare Video-/UGC-Treffer hinter die originale Titelrangfolge und entfernt Dubletten per `videoId`; der separate Video-Reiter bleibt unverändert nutzbar |
| `extensions/innertube/...` | YTM-Suchfilter und Parser aktualisiert; `playlistItemData.videoId` dient als zusätzlicher Endpunkt-Fallback. Suche und Vorschläge verwenden App-Sprache + eingestellte Region sowie – wenn aktiv – die angemeldete Sitzung; eine ungültige Sitzung fällt auf eine anonyme Anfrage zurück |
| `HomeQuickPicks.kt`, `ItemUtils.kt` | „Vorschläge“ lädt zur aktuellen Empfehlung eine deduplizierte Radio-Liste mit bis zu 18 Titeln und stellt sie abhängig von der Menge in ein bis drei gleich großen Reihen dar; der Play-Button startet die gesamte Liste. „Top Artists“ navigiert beim Tippen zur YTM-Interpretenseite. Die angemeldete Home-Sektion „From your Library“ reicht `useLogin=true` weiter und hängt alle dort fehlenden lokalen DB-Playlists an; fehlt die YTM-Sektion, erscheint ein eigenständiger lokaler Abschnitt. Pull-to-refresh belässt Compose- und Preference-Schreibzugriffe im Main-Kontext; erwartete Effekt-Abbrüche werden nicht als Ladefehler protokolliert. YTM-Home-Antworten werden nicht als Rohdaten ausgegeben |
| `HomeLibraryViewModel.kt`, `PlaylistCollections.kt`, `HomeLibrary.kt`, `PlaylistItem.kt`, `YouTubePlaylistViewModel.kt` | Der Bibliotheksreiter lädt bei aktivierter Konto-Synchronisation die vollständige Seite `FEmusic_liked_playlists` samt Continuations, vereinigt sie mit Room und dedupliziert normalisierte Browse-IDs. Lokale Datensätze gewinnen beim Merge und bleiben editierbar; reine Konto-Playlists erhalten `id=-1` und werden zur angemeldeten YTM-Seite geroutet. Login-/Sync-Wechsel laden automatisch neu, Fehlversuche behalten die letzte erfolgreiche Antwort und erklärende Leerzustände unterscheiden Konto-, Sync-, Filter- und Netzfälle. Angemeldete erste Seite und Fortsetzungen laufen über Metrolists `YouTube.playlist()`-Pfad; öffentliche/anonyme Listen bleiben beim leichten `me.knighthat.innertube`-Browse |
| `Thumbnails.kt`, `HomeArtistsViewModel.kt` | Fehlende `thumbnail.thumbnails`-Arrays werden als leere Liste deserialisiert. Online- und lokale Interpreten werden per ID zusammengeführt, Metadaten erhalten, Dubletten entfernt und **nach** dem Merge erneut nach der gewählten Titel-Sortierung geordnet |
| `AppTitle.kt`, `scripts/make-kruxx-icon.py` | Der KruXx-Header verwendet die einzeilige Wortmarke „KruXx – The core of your music“; „KruXx“ ist fett und größer, der Zusatz kleiner und regulär gesetzt. Im KruXx-Flavor belegt sie 205 × 42 dp |
| `DownloadHelperImpl.kt`, `DownloadAllDialog.kt`, `DownloadState.kt`, `InnertubeResolvingDataSource.kt`, `PlayerModule.kt` | Einzel- und Massendownloads laufen über denselben dedizierten Resolver: stets InnerTubeX `HIGH`, exakte Dateilänge und begrenzter HTTP-Range-Abruf. Anfragen werden dedupliziert/geordnet an `DownloadService` übergeben; KruXx lädt bis zu fünf Titel parallel, hält den Rest sichtbar in `STATE_QUEUED` und begrenzt Liedtext-Nebenabrufe auf einen. Wartende/laufende Downloads lassen sich per Tipp abbrechen |
| `PlayerModule.kt`, `StatefulPlayerImpl.kt`, `StatsForNerds.kt` | Kein Channel-Mapping/Downmix: ExoPlayers Standard-Audiopfad bleibt erhalten. Hall ist bei Preset „Keiner“ wirklich deaktiviert; die Decoder-Kanalzahl wird zur Mono-/Stereo-Diagnose angezeigt |
| `PlaybackNotificationSilencer.kt`, `PlayerServiceModern.kt`, `PlayerSettings.kt`, `AndroidManifest.xml` | Optionale, standardmäßig ausgeschaltete Stummschaltung fremder Benachrichtigungstöne und Unterdrückung von Heads-up-Pop-ups exakt während nativer ExoPlayer- oder eingebetteter Videowiedergabe. Nach einmaligem „Nicht stören“-Zugriff bleiben Benachrichtigungseinträge sichtbar sowie Medien, Wecker und Anrufe hörbar; Pause/Stop/Dienstende/App-Neustart/Crash stellen den Zustand wieder her. Sämtliche dabei ausgelösten Media3-Player-Zugriffe laufen über `Dispatchers.Main.immediate`, da ein Zugriff vom IO-Thread den Wiedergabedienst beim App-Start beendet hatte |
| `VideoItem`, `FromMusicShelfRendererContent.kt`, `ChartsPageComplete.kt`, `Utils.kt`, `YoutubePlayer.kt`, `MainActivity.kt` | YTM-Videotreffer werden semantisch getrennt: `OMV`/`UGC` dürfen nach direktem Tipp den eingebetteten YouTube-Player öffnen; `ATV` (Art Track/Standbild) und unbekannte Typen bleiben Audio. Warteschlange und Menüs fordern nie ungefragt Video an. Audio-/Videowechsel behalten die Position; Embed-Fehler fallen auf Audio zurück |
| `AppearanceSettings.kt`, `PictureInPicture.kt`, `MainActivity.kt`, `Preferences.kt` | Android-PiP heißt in der Oberfläche „Schwebender Player“ und liegt unter „Darstellung“. Es ist ab Android 7 verfügbar, wird nur mit aktuellem MediaItem aktiviert, hält Parameter/Aktionen/Quellrechteck synchron und unterstützt das automatische Verlassen auf Android 7–11 explizit sowie ab Android 12 über `setAutoEnterEnabled`; die Auto-Unteroption ist standardmäßig an |

### 2.3 KruXx-Spiegel und Submodul-Patches

Das ursprüngliche Modul (GitLab tannguyen047/innertube-kotlin, Branch `dev`) ist seit Juli 2026
eingefroren. Der von KruXx benötigte Fix liegt daher in einem öffentlichen, MIT-lizenzierten Spiegel
unter `Massefehler/KruXx-innertube`; das Hauptrepo pinnt genau diesen Commit. Zusätzlich bleibt der
Patch unter `patches/innertube/` erhalten, damit Änderung und Basis unabhängig nachvollziehbar sind.

| Patch | Grund |
|---|---|
| `0001-charts-make-menu-item-accessibility-optional.patch` | YouTube liefert in den Ländereinträgen des Charts-Menüs (`musicMultiSelectMenuItemRenderer`) nur noch `accessibility`; die Pflichtfelder `selectedAccessibility`/`deselectedAccessibility` warfen `MissingFieldException` → „Failed to get charts“ auf der Startseite. Felder jetzt optional; Test `InnertubeChartsLiveResponseTest` mit der Live-Antwort vom 02.09.2026 |
| `0002-tests-update-fixtures-for-current-client-API.patch` | Veralteten Test-Provider auf `KtorProvider` und dieselbe fehlertolerante JSON-Konfiguration wie die App umgestellt; strukturierte Album-Unterzeilen und bewusst verworfene leere Künstlerbereiche korrekt geprüft, obsoleten Test der entfernten Request-API gelöscht |
| `0003-auth-forward-login-headers-and-skip-unavailable-song.patch` | `useLogin` erreicht Browse/Next tatsächlich; Cookie, Visitor-ID und ein exakt geformter `SAPISIDHASH` werden gesendet. Nicht abspielbare Playlistzeilen ohne Video-ID werden übersprungen statt die ganze Liste zu verwerfen. Der Release-Stand umfasst damit 58 grüne Modul-Tests |

Ein frischer Clone benötigt keine manuelle Patch-Anwendung: `git submodule update --init` muss den
gepinnten Commit direkt laden können. Die Upstream-Basis `9e5f3ac` steht als `base-commit` in den Patches.

Patches neu erzeugen, wenn sich der Branch `kruxx` im Submodul ändert:
`git -C modules/innertube format-patch --base=9e5f3ac 9e5f3ac..kruxx -o ../../patches/innertube`.
Nur in den KruXx-Spiegel pushen; das ursprüngliche GitLab-Remote bleibt schreibgeschützt.

Auch `modules/metrolist` liegt für 1.0.1 in einem öffentlichen KruXx-Spiegel. Basis ist der bewusst
gehaltene Metrolist-Commit aus `kreate-old`; KruXx ergänzt lediglich die fehlertolerante
Deserialisierung einer leeren `thumbnail`-Struktur. Der einzelne Diff bleibt zusätzlich unter
`patches/metrolist/` erhalten. Das Modul kompiliert und läuft im Root-Build als
`:metrolistInnertube`. Sein historischer eigenständiger App-Build ist mit dem gepinnten
Versionskatalog unter Gradle 9.4.1 bereits im Plugin-Block inkompatibel; dieser Upstream-Buildfehler
entsteht vor der geänderten Kotlin-Datei und ist kein Ersatz für den erfolgreichen Root-Buildtest.

---

## 3. Bauen, Signieren, Installieren

Voraussetzungen (auf diesem Rechner vorhanden): JDK 21, Android SDK unter `/home/kruxx/android-sdk`
(Platform 36, Build-Tools 36.0.0, NDK 27.3.13750724 und CMake 3.22.1),
`local.properties` mit `sdk.dir=/home/kruxx/android-sdk`,
Python 3 + Pillow, ImageMagick (nur für SVG-Icons).

```bash
# Debug und Release aus demselben Quellbaum bauen, Release signieren und beide archivieren
# → composeApp/build/outputs/apk/kruxxUniversalProd/release/KruXx-release-signed.apk
scripts/build-local-release.sh kruxx
# vorhandenes unsigned Release nur neu signieren; Debug wird dabei bewusst nicht archiviert
scripts/build-local-release.sh kruxx --skip-build
```

Fertige APKs werden von `build-local-release.sh` automatisch nach
`/home/kruxx/Schreibtisch/Android/Kreate-APKs/` kopiert und heißen `KruXx-<versionName>-release.apk` bzw.
`-debug.apk`. Ein normaler Lauf baut beide Varianten gemeinsam und archiviert daher nur eine
nachweislich zum selben Quellbaum gehörende Debug-APK. `--skip-build` archiviert grundsätzlich keine
Debug-Datei unbekannter Herkunft. Der Zielpfad ist absichtlich fest und kann nicht per
Umgebungsvariable umgeleitet werden. Das Skript verweigert andere Produkt-Flavors, damit nicht
versehentlich ein Kreate-Paket mit dem KruXx-Schlüssel veröffentlicht wird. Zusätzlich verlangt es
einen vollständig sauberen Git-Stand und gleicht die von AGP im APK eingebettete Git-Revision mit
`HEAD` ab. So kann kein Release-Artefakt unbemerkt uncommitteten oder veralteten Quellen zugeordnet
werden.

Für den signierten Release ist ein gewöhnlicher sauberer Clone mit eigenem `.git`-Verzeichnis
zu verwenden. Beim 1.2.0-Kandidaten schrieb AGP 8.13 aus einem Git-Worktree lediglich
`generate_error_reason: NO_VALID_GIT_FOUND` in `version-control-info.textproto`; das Build-Skript
wies dieses APK korrekt zurück und archivierte es nicht. Derselbe Commit in einem gewöhnlichen
Clone enthält seine vollständige Git-Revision und besteht die Herkunftsprüfung. Worktrees bleiben
für Entwicklung und Tests nutzbar; die Prüfung im Release-Skript darf dafür nicht abgeschaltet werden.

Erster Build dauert 5–10 min (Downloads), danach 1–4 min. Kotlin 2.4 benötigt
[mindestens R8 9.1.29](https://developer.android.com/build/kotlin-support). Da AGP 8.13.2 noch
R8 8.13.19 mitliefert, pinnt `settings.gradle.kts` R8 9.1.43 aus Google Maven im
`pluginManagement.buildscript` ([offizieller Override](https://r8.googlesource.com/r8/+/refs/heads/main/README.md)).
Die tatsächlich verwendete Version steht am Anfang der Release-`mapping.txt`; die R8-Warnungen
zu unbekannten Kotlin-Metadaten entfallen damit. Bei einem künftigen AGP-Update diesen Pin erneut prüfen.

Der separate Lint-Analysator von AGP 8.13 kann weiterhin Diagnosezeilen zur erwarteten
Metadatenversion 2.2 statt 2.4 ausgeben. Maßgeblich ist der separat ausgeführte vollständige
`:composeApp:lintKruxxUniversalProdRelease`: Die versionierte `composeApp/lint-baseline.xml` friert
nur geerbte Altbefunde ein. Neue Fehler lassen den Gate fehlschlagen; zusätzliche Warnungen sind
inhaltlich zu prüfen. Für 1.0.1 lief dieser Gate ohne neue Befunde durch; der Release-Build endete
anschließend mit `>> done:`.

Mit KruXx-Minimum API 24 meldet Lint zusätzliche `ObsoleteSdkInt`-Warnungen für bereits vorhandene
Prüfungen und Annotationen im gemeinsamen Flavor-Code. Insbesondere API-24-Prüfungen bleiben für
geerbte API-23-Varianten erforderlich; sie dürfen nicht allein anhand des KruXx-Lints entfernt
werden. Der konkrete Prüflauf und die Warnungszahlen stehen im
[IST-Stand](KRUXX-IST-STAND.md#supportentscheidung-vom-07092026-android-7-als-neue-untergrenze).

**Signatur / Keystore**

- `.ignore.d/keystores/kreate-local.jks` + `kreate-local.properties` (Passwörter). Gitignored.
- Backup-Kopie: `/home/kruxx/Schreibtisch/Android/Kreate-APKs/keystore-backup/` – zusätzlich auf USB/Cloud sichern.
- Geht der Keystore verloren, lässt sich KruXx nicht mehr per Update installieren
  (nur nach Deinstallation → Datenverlust).
- Die offiziellen Kreate-APKs sind mit knighthats Key signiert. Unsere `github`-Variante hat
  dieselbe App-ID, aber unseren Key → Android meldet „Paket in Konflikt“. Deshalb der Flavor `kruxx`.
- Fest im KruXx-Updater hinterlegter SHA-256-Zertifikatfingerabdruck:
  `5dc08df341c5d5b56aa9fe9ebc58eb02e0a25bc4a27b48d83a4fbe31ccbdd673`.
  Bei einem beabsichtigten Schlüsselwechsel braucht es einen ausdrücklich geplanten Migrationspfad;
  diesen Wert nie still ändern.

**Aufs Handy** (ohne ADB): APK per LocalSend senden, in „Downloads“ antippen, „Unbekannte Apps
installieren“ einmalig erlauben. Fertige APKs liegen zusätzlich in
`/home/kruxx/Schreibtisch/Android/Kreate-APKs/`.

**Daten migrieren** (Kreate → KruXx): Kreate → Einstellungen → Daten → „SICHERN UND
WIEDERHERSTELLEN“ → „In Backup speichern“ + „Einstellungen exportieren“; in KruXx entsprechend
importieren, danach YouTube neu anmelden (Anmeldedaten sind vom Export ausgeschlossen).

### 3.1 Eigener Updatekanal

- API: `https://api.github.com/repos/Massefehler/KruXx/releases/latest`
- Tag: ausschließlich `vMAJOR.MINOR.PATCH`, aktuell `v1.2.1`
- APK: exakt `KruXx-MAJOR.MINOR.PATCH-release.apk`
- Standard: nachfragen; alternativ automatische Installation oder vollständig deaktiviert
- automatische Prüfung: bei foreground-bereiter Oberfläche und validiertem Netzwerk; Netzrückkehr
  oder ein neuer Vordergrundstart stößt sie erneut an, solange noch kein erfolgreicher Abruf innerhalb
  der letzten 24 Stunden gespeichert ist
- vorübergehende Netz-/HTTP-Fehler: höchstens zwei automatische Retries nach 2 und 8 Sekunden; eine
  zukünftige gespeicherte Uhrzeit blockiert nach einer Systemzeitkorrektur nicht dauerhaft
- manuelle Prüfung: umgeht das 24-Stunden-Intervall, ersetzt nötigenfalls einen laufenden
  automatischen Job und bleibt ein einzelner, sichtbarer Versuch
- Nebenläufigkeit: höchstens ein Prüfjob; bereits sichtbarer Nachfrage-/Downloaddialog wird nicht
  dupliziert. `LAST_SUCCESSFUL_CHECK` wird ausschließlich nach erfolgreichem Abruf und vollständiger
  Releasevalidierung geschrieben
- vor der Installation: GitHub-Asset-Digest (SHA-256), Paket-ID, `versionName`, höherer
  `versionCode`, installierte Signatur und gepinnter Zertifikatfingerabdruck

Die bisher verteilten Builds `2.2.3-kruxx.x` enthalten noch keinen funktionsfähigen KruXx-Updater.
Sie benötigen deshalb genau einmal die manuelle Installation eines eigenständig versionierten
KruXx-Releases (`1.0.0` oder neuer). Weil Paket-ID und Signaturschlüssel gleich bleiben und dessen
`versionCode` mindestens `1_000_000` beträgt, aktualisiert Android die bestehende Installation ohne
Löschen der App-Daten. Danach steht der eigene Updatekanal zur Verfügung, sofern die neue APK
die Android-Version des Geräts unterstützt. Ein Gerät kann den manuellen Zwischenschritt `1.0.0`
überspringen, wenn bereits eine neuere, kompatible und korrekt signierte 1.x-APK verfügbar ist.
Android 6 bleibt bei 1.1.0; ab 1.2.0 gilt API 24 als Untergrenze.

**Prüfstand 1.1.0 → 1.2.0:** Der vollständige automatische Erkennungs-, Download- und Installerweg
ist auf API 36 mit Datenerhalt bestanden. Auf API 24 funktionieren Erkennung und manuelle Prüfung;
der System-Downloadmanager des Testemulators lehnt jedoch die TLS-Zertifikatskette des APK-Downloads
ab (`CertPathValidatorException: Trust anchor for certification path not found`). Bei diesem
Fehler die signierte APK auf einem aktuellen Rechner herunterladen, übertragen und über die
bestehende App installieren. Dieser manuelle Upgrade-Weg ist auf API 24 bestanden. Einzelversuche
und verbleibende Nachtests stehen im
[Self-Updater-Prüfstand](KRUXX-IST-STAND.md#self-updater-nachtest-nach-veröffentlichung).

GitHub Actions baut nur eine **unsignierte Debug-APK** und führt die App- sowie Innertube-Tests aus. Der Produktionsschlüssel
bleibt ausschließlich lokal. Ein Release wird erst nach lokalem signiertem Build, Prüfung und Tag
hochgeladen. Crashlogs, `.ignore.d/` und `local.properties` gehören nie in ein Release oder Commit.

### 3.2 Geheimnisse und GitHub-Secret-Scanning

Die öffentlichen Repositories haben getrennte Aufgaben: `Massefehler/KruXx` enthält die App,
`Massefehler/KruXx-innertube` den gepinnten Spiegel des Browse-Moduls und
`Massefehler/KruXx-metrolist` den gepinnten Spiegel des Login-/Bibliotheksmoduls. Ein Fund kann
deshalb in der Historie mehrerer Repositories liegen und muss im jeweils betroffenen Projekt geprüft
werden. Niemals einen Schlüsselwert zur Abstimmung in Issue, Committext, Dokumentation oder Chat
kopieren; Pfad, Commit und Schlüsselklasse reichen zur Zuordnung.

Vorgehen bei einem Alarm:

1. Fund als **privates Geheimnis**, **öffentliche Client-Kennung** oder **Fehlalarm** klassifizieren.
   Herkunft, Anbieter, Berechtigungen und mögliche Abrechnung sind entscheidend – der Variablenname
   allein genügt nicht. Aus Upstream geerbte bzw. für einen öffentlichen Client bestimmte Kennungen
   können heuristisch als API-Key erkannt werden, sind aber trotzdem bewusst zu dokumentieren.
2. Ist der Wert geheim oder seine Einordnung unsicher, beim Anbieter zuerst sperren/rotieren. Eine
   Codeänderung macht einen bereits kopierten Wert nicht wieder sicher.
3. Den aktuellen Quellstand auf eine Konfiguration außerhalb von Git umstellen und alle Logs,
   Fixtures, Release-Artefakte sowie beide Repository-Historien auf denselben Fundpfad prüfen – ohne
   den Wert in der Terminalausgabe offenzulegen.
4. Ein History-Rewrite nur geplant durchführen: Tags, Forks und Klone müssen koordiniert werden;
   bereits veröffentlichte Kopien können nicht zuverlässig zurückgerufen werden. Rotation bleibt
   deshalb die eigentliche Sicherheitsmaßnahme.
5. Einen GitHub-Alarm erst schließen, wenn Klassifikation und Maßnahme nachvollziehbar festgehalten
   sind. „Aus aktuellem Code gelöscht“ reicht bei einem Fund in einem alten Commit nicht.

Nicht in Git gehören insbesondere Signierdateien/-passwörter, GitHub-Zugriffstokens, Cookies,
OAuth-Client-Secrets, private API-Schlüssel, `local.properties` und Crash-/Diagnosedaten. Der fest im
Updater enthaltene **Zertifikatfingerabdruck** ist dagegen eine öffentliche Prüfinformation, kein
Signiergeheimnis; der private Schlüssel selbst bleibt ausschließlich lokal.

---

## 4. Architektur von Startseite, Bibliothek, Wiedergabe, Suche und Downloads

### 4.1 Wiedergabe

```
ExoPlayer ── CacheDataSource (Downloads) ── CacheDataSource (Cache) ── ResolvingDataSource
                                                                          │ app/kreate/di/PlayerModule.kt
                                                                          ▼
                                            resolveInnertubeMedia()  (InnertubeResolvingDataSource.kt)
                                              ├─ upsertSongInfo()   → me.knighthat.innertube (NEXT) → Room
                                              ├─ streamCache[videoId, Zweck, Qualitäts-/Netzvariante]
                                              │                  (Ablauf −30 s)
                                              └─ InnerTubeXPlayer.playerResponseForPlayback()
                                                   ├─ syncSession(): Cookie/visitorData/dataSyncId aus Preferences,
                                                   │                  anonyme Sessions: eigene visitorData (SharedPrefs "innertubex_session")
                                                   ├─ InnerTubeExtractor.extract(videoId, hints, excluded, quality)
                                                   │     ├─ Client-Auswahl (Katalog in InnerTubeX, VISIONOS/WEB_REMIX/…)
                                                   │     ├─ PO-Token via TokenProvider → PoTokenGenerator (WebView/BotGuard)
                                                   │     ├─ Cipher: RemotePlayerConfigStore (faraday) → EJS/QuickJS lokal
                                                   │     └─ liefert ExtractedStream (URL, Header, itag, expiresAt …)
                                                   └─ PlaybackData → DataSpec (URL, Header) + Format in Room
Fehler zur Laufzeit → ErrorHandlingPolicy + ExoPlayerListener.tryRecoverPlaybackError():
   403/410/416: keine Wiederholung derselben URL; URL verwerfen, WEB_REMIX ggf. sperren,
                InnerTubeXPlayer.refreshAfterStreamRejection(), prepare()+seekTo()
   Parser-/Cache-Fehler: playerCache.removeResource(videoId), prepare()+seekTo()
```

Wichtige Konstanten/Stellen:

- Remote-Konfigtabelle (Player-Hashes): `InnerTubeXPlayer.AndroidPlayerConfigRepository.PLAYER_CONFIG_URL`
  = `https://raw.githubusercontent.com/MetrolistGroup/faraday/master/registry/player_configs.json`.
  **InnerTubeX akzeptiert nur `MetrolistGroup/faraday`-URLs** (`RemotePlayerConfigStore.validatedSourceUrlOrNull`);
  andere URLs werden stillschweigend ignoriert.
- HLS/SABR sind ausgeschaltet (`withStreamCapabilities(allowHls=false, allowSabr=false)`) – ExoPlayer
  bekommt nur direkte HTTPS-Streams.
- Bounded-Range-Clients sind ebenfalls ausgeschlossen (`allowBoundedRange=false`; in InnerTubeX 0.3:
  ANDROID_VR, IOS, TVHTML5_SIMPLY – TVHTML5_SIMPLY steht in der automatischen Fallback-Kette). Grund: In
  Kreates Kette ist der Resolver der *Upstream* des `CacheDataSource`; liefert er einen begrenzten
  Sub-Range, trägt media3 `position + Chunk` als Gesamtlänge in den Cache-Index ein und meldet danach
  Stream-Ende – der Song bricht ab und bleibt im Cache dauerhaft abgeschnitten. Metrolist chunked, weil
  dort der Resolver *außerhalb* des Caches liegt; sein `withResolvedStream` darf deshalb nicht 1:1
  übernommen werden. `InnerTubeXPlayer` prüft zusätzlich, dass kein solcher Stream zurückkommt.
  Log beim Überspringen: `bounded-range client skipped by request`.
- **Der Cache für signierte URLs ist auswahlabhängig:** Sein Schlüssel umfasst Video-ID, Zweck und bei
  Playback Audioqualität, aktuellen Metered-Status sowie Datensparrichtlinie. Bei einem Wechsel von
  `AUDIO_QUALITY` oder `IS_CONNECTION_METERED` werden ausschließlich die Playback-URLs invalidiert;
  Downloads bleiben getrennt und immer `HIGH`. Die Ablaufprüfung betrachtet eine URL bereits 30 Sekunden
  vor ihrem Server-Ablauf als veraltet.
- **Der Byte-Cache bleibt absichtlich über die videoId adressiert, das Format ist aber nicht fix:** Qualität
  und der liefernde Client bestimmen das itag – z. B. 140 (m4a) im Mobilnetz, 251 (webm) im WLAN.
  `CacheDataSource` würde sonst gecachte Bytes des einen Files mit Netzwerk-Bytes des anderen
  zusammenkleben und Extractor-Fehler wie „Skipping atom with length > 2147483647“ erzeugen. Deshalb
  schreibt der Resolver das itag in die Cache-Metadaten (`kruxx_itag`) und verwirft bei Abweichung die
  gecachten Spans (Abbruch per `CachedFormatMismatchException`, der Listener bereitet neu vor). Alt-Einträge
  ohne Metadaten fängt die Parser-Fehler-Recovery ab.
- **Explicit beim ersten Abspielen:** Die MediaSource registriert `is_explicit` unmittelbar aus dem
  `MediaItem`, bevor der Resolver arbeiten kann. Der parallele `upsertSongInfo` muss somit nicht abgewartet
  werden; für wiederhergestellte oder ältere Queue-Einträge bleibt Room der Fallback.
- CDN-Abruf (`PlayerModule.kt`): Der Chrome-User-Agent ist nur Default-Header; die Header aus
  `ExtractedStream.headers` (User-Agent/Referer/Origin je Client) gewinnen. **Nie `setUserAgent()`
  verwenden** – media3 hängt den per `addHeader` zusätzlich an, es gingen zwei User-Agent-Zeilen raus.
- **Den App-Client nie unverändert an `OkHttpDataSource` übergeben:** Im Debug-Build protokolliert der
  anwendungsweite Client Response-Bodies. Der Logging-Interceptor liest dafür einen gedrosselten
  Medien-Response vollständig, bevor Media3 das erste Byte erhält; dann bleibt jeder Titel scheinbar
  bei Position null hängen. `forMediaTransport()` entfernt `HttpLoggingInterceptor` aus Application-
  und Network-Kette, behält aber Proxy, DNS und alle funktionalen Interzeptoren. Das gilt auch für
  Downloads und verhindert zusätzlich, dass signierte CDN-URLs im HTTP-Log landen.
- Qualität: `Preferences.AUDIO_QUALITY` (High/Low/Auto) → InnerTubeX `HIGH/LOW/AUTO`; bei Auto und
  getaktetem Netz + `IS_CONNECTION_METERED` → `LOW`. Genau diese atomar erfassten Eingaben werden sowohl
  zur Auflösung als auch für die URL-Cache-Identität verwendet.
- **Kanalbelegung:** `PlayerModule` baut einen normalen `ExoPlayer` ohne `AudioProcessor`,
  `ChannelMappingAudioProcessor`, Mixer, Balance/Pan oder Mono-Downmix. Decoder und Android-`AudioTrack`
  erhalten daher die Kanalbelegung des YTM-/lokalen Quellformats unverändert. LoudnessEnhancer und
  BassBoost sind standardmäßig aus; `PresetReverb` wird bei Preset 0 („Keiner“) nun ebenfalls komplett
  deaktiviert und vom Aux-Ausgang getrennt. In „Stats for Nerds“ steht die vom Decoder gemeldete Zahl als
  `1 (Mono)` bzw. `2 (Stereo)`. Meldet der Decoder 2, aber beide Seiten klingen gleich, liegt die
  Zusammenführung nach dem App-Decoder (Android-Bedienungshilfe „Mono-Audio“, Geräte-EQ, Bluetooth-
  Profil/Empfänger, Kabel/Adapter oder tatsächlich mittig gemischte Aufnahme).
- **Audio-Fokus und fremde Benachrichtigungstöne:** ExoPlayer verwendet weiterhin `USAGE_MEDIA`,
  `AUDIO_CONTENT_TYPE_MUSIC` und Androids regulären Audio-Fokus. Optional kann unter
  Einstellungen → Wiedergabe „Benachrichtigungstöne bei Wiedergabe stummschalten“ aktiviert werden;
  die besondere Systemfreigabe `ACCESS_NOTIFICATION_POLICY` wird erst dann vom Nutzer erteilt. Auf
  Android 15+ schaltet `PlaybackNotificationSilencer` nur KruXx’ implizite DND-Regel. Auf Android
  6–14 wird der vorherige globale Filter samt Policy synchron gesichert; ein schon aktiver DND-Modus
  wird nie überschrieben. Wiederherstellung erfolgt bei Pause/Stop, Dienstende, nächstem App-Start und
  im lokalen Crash-Handler. `SUPPRESSED_EFFECT_PEEK` unterdrückt Heads-up-Pop-ups, lässt die Einträge
  aber im Benachrichtigungsbereich sichtbar; erlaubt sind Calls sowie – ab Android 9 explizit –
  Alarme, Medien und Systemtöne. Der Dienst verbindet dafür
  `ExoPlayer.isPlaying` mit dem tatsächlichen `PLAYING`-/`PAUSED`-Zustand des eingebetteten
  Videoplayers; ein pausiertes Video hält die Stummschaltung also nicht unnötig aktiv.
  Debug (`de.kruxx.music.debug`) und Release (`de.kruxx.music`) sind aus Android-Sicht getrennte
  Apps; weder der Schalterzustand noch der besondere „Nicht stören“-Zugriff werden zwischen ihnen
  übernommen. Bei einem Variantentest müssen beide Voraussetzungen daher für das gerade laufende
  Paket kontrolliert werden.
- **Media3-Hauptthread-Invariante:** `StatefulPlayer`/Media3 erzwingt Zugriffe auf dem
  Application-Looper. Der Service behält seinen IO-Scope für Datenbank/Netz, sammelt Player-nahe
  Flows aber über `playerCoroutineScope` mit `Dispatchers.Main.immediate`; auch
  `updatePlaybackNotificationSilencing()` ist `@MainThread`. Diese Trennung nicht zurückbauen: Das
  Lesen von `player.isPlaying` aus dem IO-Scope löste beim Dienststart eine
  `IllegalStateException` aus und ließ die App direkt nach dem Start wieder schließen. Ein Fehler
  beim Registrieren oder Verarbeiten des Policy-Receivers darf den Playerdienst ebenfalls nicht
  beenden.
- **Vollbild-Player-Basisdarstellung:** Ohne gespeicherte App-Daten sind
  `PLAYER_SHOW_THUMBNAIL=true`, `PLAYER_BACKGROUND=BlurredCoverColor` und
  `PLAYER_BACKGROUND_BLUR=true`. Damit bleibt das vollständige Cover als eigene Fläche sichtbar und
  nur der Hintergrund verwendet das Cover vergrößert und unscharf. Der Schalter „Cover im Player
  anzeigen“ sowie ein Doppeltipp auf den Hintergrund schreiben die Auswahl dauerhaft. Ein Update
  darf sie nicht ungefragt überschreiben. Weil `de.kruxx.music` und `de.kruxx.music.debug` getrennte
  Preference-Speicher besitzen, kann nur eine Variante scheinbar abweichen, obwohl beide denselben
  Quellstandard enthalten.
- Log-Tags (adb logcat): `InnerTubeXPlayer`, `dataspec`, `ExoPlayerListener`, `InnerTube`,
  `InnerTubeExtractor`, `YouTubeCipherService`, `RemotePlayerConfigStore`, `PoTokenGenerator`, `PoTokenWebView`.

Was **nicht** über InnerTubeX läuft: die sichtbare Suche und ihre Vorschläge
(`it.fast4x.innertube`, `extensions/innertube` bzw. `:oldtube`), Browse/Charts/Song-Metadaten
(`me.knighthat.innertube`, `modules/innertube`) sowie YouTube-Login und Bibliothek
(`com.metrolist.innertube.YouTube`, `modules/metrolist`). Die Clients haben eigene
Session-Verwaltungen; KruXx synchronisiert deshalb Cookie, `visitorData` und `dataSyncId` aus denselben
`Preferences` (`YOUTUBE_COOKIES`, `YOUTUBE_VISITOR_DATA`, `YOUTUBE_SYNC_ID`).

### 4.2 Suche und Vorschläge

```text
SearchScreen (Text oder lokale Spracheingabe)
  ├─ OnlineSearch → searchSuggestionsWithItems()
  └─ SearchResultScreen
       ├─ Titel: Song-Filter ─┐
       │                     ├─ Song-Rangfolge zuerst → Videos ergänzen → per videoId deduplizieren
       │          Video-Filter┘
       └─ Alben/Künstler/Videos/Playlists/Podcasts: jeweiliger YTM-Filter
```

- Die Anfragen laufen über `extensions/innertube` mit dem WEB_REMIX-Client. `MainActivity` setzt
  `LocalePreferences` aus App-Sprache (`CURRENT_LOCALE`) und App-Region (`APP_REGION`);
  `Context.DefaultWebWithLocale` wird für jede Anfrage neu gebildet, damit kein alter `en/US`- oder
  `visitorData`-Wert im Prozess hängen bleibt.
- Ist YouTube-Login aktiviert und ein Cookie vorhanden, signiert `setLogin()` Suche und Vorschläge.
  Bei einer abgelaufenen/fehlerhaften Kontositzung wird dieselbe Anfrage einmal anonym wiederholt,
  damit die Oberfläche nicht nur deshalb leer bleibt.
- YTM ordnet abspielbare Inhalte nicht immer dem Filter „Titel“ zu. Offizielle Musikvideos, manche
  Singles und UGC-Treffer erscheinen teils ausschließlich unter „Videos“. KruXx lädt daher auf der
  ersten Titelseite beide Shelves parallel, übernimmt die ursprüngliche Titelrangfolge und ergänzt
  nur noch fehlende `videoId`s aus Videos. Fortsetzungen stammen vom Titel-Shelf; vollständige
  Video-Paginierung gibt es weiterhin im Reiter „Videos“.
- Der Parser akzeptiert den Wiedergabe-Endpunkt im Titel, in der Zeile oder in
  `playlistItemData.videoId`. YTM-Zeilen ohne Endpunkt im Titel werden dadurch nicht mehr verworfen.
  Explizit-Markierungen der Video-Fallbacks bleiben für die Kindersicherung erhalten.
- Persistierte Ergebnis-Keys beginnen mit `searchResults/v3` und enthalten Sprache, Region sowie
  Konto-/Anonym-Modus. Ältere leere oder für eine andere Region gespeicherte Ergebnisse werden damit
  nicht wiederverwendet. Suchtexte werden nicht geloggt.

Die App kann sich damit deutlich näher wie YTM verhalten, aber dessen proprietäre Rangfolge nicht
exakt nachbauen: Server-Experimente, Verfügbarkeit nach Land/Konto, Alters-/Inhaltsbeschränkungen und
persönliche Uploads können weiterhin andere Treffer erzeugen. Bei einem verbleibenden Einzelfall immer
exakten Titel, Interpret und möglichst die YTM-URL/`videoId` festhalten.

`SearchSkeleton` ist der gemeinsame Rahmen aller drei Suchseiten. Seine Leiste wählt die eigene
Kategorie: Sucheingabe und `SearchTypeScreen` zeigen Online/Bibliothek/Link, die Ergebnisseite
Titel/Alben/Künstler/Videos/Playlists. Damit gilt wieder der Stand vor 1.2.1; die dort eingeführte
zweite Filterzeile über dem Inhalt entfällt. Die Suche zeigt bewusst keine Home-Reiter, weil der
Weg zurück seit 1.2.1 am Header-Icon samt Wortmarke hängt (`HomeNavigation.goHome`).

KruXx bietet die geerbten Featured-/Podcast-Kategorien weiterhin nicht an; ein gespeicherter
ungültiger Index fällt über `tabs.indices` auf 0 zurück. `searchContentWidth` bleibt die eine
Stelle, an der eingebettete Suchseiten den Platz einer rechten Navigationsleiste reservieren.

Ebenfalls seit 1.2.1 veröffentlicht: `AppTitle` verbindet Icon und Wortmarke in KruXx zu einer
gemeinsamen Startseiten-Schaltfläche. `HomeNavigation.goHome` wählt `QuickPics` ausdrücklich aus,
statt nur zur `home`-Route mit der bisherigen Reiterauswahl zurückzukehren. Bei deaktivierter
Startseite wird wie beim App-Start `Songs` gewählt. Die konfigurierte `STARTUP_SCREEN` wird nicht
verändert. Beim Verlassen einer Unterseite räumt `popUpTo(navController.graph.id)` den Stapel auf;
die Graph-ID berücksichtigt auch einen direkten Start in der Suche. Auf einer bereits alleinigen
Home-Route reicht das Umschalten des Reiters, ohne einen neuen Navigationseintrag anzulegen.
Die Aktion gilt für alle Ansichten mit diesem gemeinsamen Header. Geerbte
Mehrfachklick-/Langdruck-Spielaktionen bleiben ausschließlich in anderen Produkt-Flavors bestehen.

### 4.3 Downloads

**Download-Erweiterung vom 05.09.2026, veröffentlicht mit 1.2.0:** Vor dem Einreihen der
Media3-Audioqueue führt `DownloadCenter` alle manuellen Einzel- und Sammelaktionen durch dieselbe
Format-/Speicherabfrage. `addDownloadsInternal()` ist ausschließlich der interne Queue-Einstieg
nach Auswahl bzw. für Hintergrundaufträge. Neue UI-Aktionen müssen `addDownload(s)` verwenden.
`SongItem.Render` bietet für nicht lokale Audiotitel in KruXx einen rechten `AudioDownloadButton`
mit explizitem Audioformat-Dialog. Seit der mit 1.2.1 veröffentlichten Korrektur vom 08.09.2026 entfernt
ein kurzer Tipp auf fertige/laufende Downloads wieder alle internen Varianten des Tracks;
langes Drücken öffnet mit `forceDialog=true` die Format-/Speicherauswahl auch bei gemerkten Vorgaben.
Videotreffer verwenden dieselbe Umschaltlogik mit Video-/MP3-Auswahl. Die `MediaItem`- und
Innertube-Überladungen von `SongItem.Render` reichen Quellmetadaten bis zum Downloadknopf weiter.
„Nur in KruXx“ bleibt die anfängliche Vorgabe. Zusätzlich kann in den automatisch angelegten
öffentlichen Ordner `Download/KruXx-Downloads` oder einen anderen Ordner gespeichert werden.
Der Standardordner trennt `Audio` (MP3 und Originalaudio) und `Video` nach MIME-Typ. `DownloadsScreen`
ist KruXx-Hauptreiter 5 und liest die öffentlichen Dateien bei Änderungen/Rückkehr erneut ein;
eine bestehende SAF-Freigabe ergänzt die sichtbaren Altdateien. Androids WebM-MIME-Fehler wird über
Zielordner und generierten Dateinamen abgefangen. Eine weitere Kategorie zeigt interne Downloads.
`DownloadRemovalViewModel` hält die bestätigte Dateiauswahl über Konfigurations- und Reiterwechsel
und entfernt sie auf dem IO-Dispatcher. Einzelentfernung, Mehrfachauswahl und „Alle auswählen“
verwenden dieselbe Bestätigung mit Dateiliste und Speicherort. Die Auswahl gilt nur für den
aktuellen Bereich; Erfolge und Fehler werden je Datei erfasst und die Übersicht anschließend
aktualisiert. `SharedDownloads.remove()` prüft URI, Pfad, Name, Größe und gegebenenfalls Pending-Status;
MediaStore-Löschung, SAF-Dokumentlöschung und ältere Dateipfade bleiben auf den Standardordner
und seine zwei Unterordner begrenzt. Verzeichnisse und geänderte Auswahlen werden abgewiesen.
Bei fehlenden Schreibrechten kann der passende Standardordner über Android erneut freigegeben
werden. Die neue interne Einzelentfernung verwendet `DownloadCenter.removeAsset()` für genau
ein Format bzw. den Media3-Entfernungsbefehl für Originalaudio. Der bestehende Bibliothekshelfer
zur titelweiten Entfernung bleibt davon getrennt. Beide internen Wege erhöhen die Generation
des Titels, sodass ältere Dateiaufträge ihn nicht wiederherstellen; öffentliche Kopien bleiben erhalten.
`removeFiles()` verwendet dieselbe geprüfte Dateientfernung wie die Übersicht und vergisst
fehlgeschlagene Dateien nicht. Titelweite Media3-Entfernungen laufen durch dieselbe Befehlssperre
wie Hinzufügungen; wartende Hinzufügungen mit älterer Generation werden verworfen.
Die horizontale Navigation misst den tatsächlichen Überlauf und reserviert dafür Richtungshinweise.
Seit der mit 1.2.1 veröffentlichten Leistenkorrektur vom 08.09.2026 übernimmt `HorizontalScrollWithArrows`
diese Logik gemeinsam für `HorizontalNavigationBar` und alle `ButtonsRow`-Filter. Die Messung der
Inhaltsbreite liegt hinter `horizontalScroll`; verglichen wird mit der gesamten verfügbaren
Scrollspur, bevor Pfeilflächen abgezogen werden. So verschwinden die Pfeile nach einer
Verbreiterung wieder, wenn die Inhalte passen. Ein Pfeilklick bewegt die Leiste um 75 Prozent
ihres sichtbaren Bereichs; an den Grenzen bleiben die Randflächen stabil. Beschriftung und Richtung
beachten auch RTL. `DownloadsScreen` reserviert wie die Bibliotheksseiten den Platz der rechten Leiste.
`UniformNavigationRow` ermittelt die maximale intrinsische Breite der beschrifteten Reiter und misst
alle mit derselben Breite einschließlich seitlichem Abstand. Die umgebende Box gibt diese Breite
an `TextIconButton` weiter, damit Text und Symbol zentriert werden. Die Messung verwendet die
tatsächliche Schrift und Schriftgröße und setzt keine feste Reiterzahl voraus. Die deutsche
KruXx-Ressource überschreibt `artists` mit „Künstler“.
`DownloadProgressCard` zeigt einen animierten Verlaufsbalken samt Prozentwert, Phase, Format und
Speicherziel direkt nach dem Start sowie später im Downloads-Reiter und in den Einstellungen.
`SwipeableDownloadProgressCard` blendet Dialog/Karte in beide Wischrichtungen aus; das Glas-×
ist bereits während des Downloads bedienbar. `DownloadCenter.hideStatus()` persistiert dafür die
Auftrags-UUID in `hidden_status_jobs`, ohne den Worker oder seine Dateien anzufassen. Die Übersicht
wählt zuerst den aktiven/neuesten Auftrag und prüft dann dessen Sichtbarkeit, damit ältere Ergebnisse
nicht nachrücken. Die Download-Einstellungen zeigen weiterhin den vollständigen bisherigen Verlauf
(bis zu 20 Aufträge). „App weiter nutzen“ schließt nur den Startdialog und behält die Übersichtskarte.
Der Swipe-State nutzt wie die bestehenden App-Gesten den Konstruktor mit `confirmValueChange`:
Der neuere Fling-Modus von Material3 1.4.0 ließ die Karte im Startdialog beim Loslassen zurückspringen.
Die Details scrollen innerhalb der Karte; der Schließen-Button des Dialogs bleibt fest erreichbar.
`FileProgressPlan` gewichtet die benötigten Schritte aus Byte-/Medienfortschritt. WorkManager
persistiert die Abschlussdaten; erst nach fertiger, geprüfter Zieldatei sind 100 % möglich.
Manuelle interne Originaldownloads verwenden ebenfalls den Worker, ohne zusätzliche Datei; für
automatische Downloads öffnet sich kein Fortschrittsdialog. Details und Regressionstests stehen
in `DOWNLOADS.md`.
`DownloadNames` trennt sichtbare Künstler-/Titeldaten von Video-Kanalmetadaten. Das Schema
`Künstler - Titel` gilt seit der Korrektur vom 08.09.2026 unabhängig vom Video-Herkunftsflag;
der erste von Leerzeichen umgebene Bindestrich/Gedankenstrich trennt Künstler und Titel.
Weitere Titelbestandteile bleiben erhalten. `source_tracks` bewahrt rohe Quellmetadaten auch für
Originalaudio, damit ein bereits aufgeteilter Bibliothekstitel nicht nochmals aufgeteilt wird.
Dieselbe Auflösung
versorgt Download-Dateinamen, Fortschritt, Kopierauswahl, interne Download-Zeilen, Wiedergabemetadaten
beim Start aus der Downloads-Übersicht und neue MP3-Tags.
Technische IDs bleiben in privaten Dateipfaden; öffentliche Namen enthalten nur Künstler, Titel
und Endung. Bekannte alte Namen werden mit kollisionssicherer Nummerierung umbenannt; dabei
bleiben Mediendaten und bereits eingebettete Tags unverändert. Musikversionen bleiben erhalten,
unbekannte Videokünstler werden nicht aus einem Kanalnamen geraten.
`Mp3Tags` schreibt die UTF-16-ID3v2.3-Tags des Encoders. Vor Wiederverwendung einer internen MP3
prüft der Worker diese Tags und erstellt bei geänderten Künstler-/Titeldaten eine korrigierte
temporäre Datei mit identischen Audiobytes. Nur die bekannten KruXx-Frames TIT2/TPE1 werden ersetzt;
unerwartete oder beschädigte Tags werden abgewiesen. Registrierung und Generationsprüfung erfolgen
wie bei einer neuen Konvertierung, ohne erneute Kompression. Öffentliche Altdateien werden nicht
überschrieben und Originalaudio wird nicht nachträglich umkodiert oder getaggt.
`FileDownloadWorker` erzeugt MP3/Video und kopiert verifiziert über MediaStore (`SharedDownloads`,
API 29+) beziehungsweise SAF für andere Ordner; API 24–28 verwendet für den öffentlichen Ordner
die abgefragte Schreibberechtigung. Dieselben Dateiziele gibt es beim Kopieren. `OfflineFiles.snapshot()`
darf keinen Netzwerk-Upstream haben. Der Videotransfer verwendet einen eigenen Reader außerhalb
der Audio-Caches. Architektur, Native-Build, Tests und Geräteabnahme stehen in
[`DOWNLOADS.md`](DOWNLOADS.md). Die nachstehende Audioqueue und ihre Qualitätsregeln gelten weiterhin.

`DownloadAllDialog` übergibt die vollständige, aktuell geladene Songliste in einem Aufruf an
`DownloadHelper.addDownloads()`. Dort werden lokale Titel, Dubletten sowie bereits fertige/laufende
Einträge herausgefiltert und alle übrigen `DownloadRequest`s geordnet über `MyDownloadService` gesendet.
Ein Fehler bei einem Titel darf das Einreihen der folgenden Titel nicht abbrechen.

Einzel- und Massendownload verwenden danach denselben, von der Wiedergabe getrennten
`InnertubeDataSourceType.DOWNLOAD`. Dessen URL-Cache ist ebenfalls getrennt und ruft
`InnerTubeXPlayer.playerResponseForDownload()` fest mit `AudioQualityFormat.High` auf. Die
Wiedergabe-Einstellung High/Low/Auto und Androids Kennzeichnung eines WLAN-/Mobilfunknetzes als
„getaktet“ können die Downloadqualität daher nicht absenken. InnerTubeX `HIGH` wählt das am höchsten
bewertete verfügbare Audioformat unter den mit KruXx' direktem HTTPS-Pfad kompatiblen Streams. Das ist
die bestmögliche Qualität, die der jeweilige YTM-Titel und die aktuell funktionierenden Clients
anbieten; YTM kann für verschiedene Titel trotzdem unterschiedliche Codecs und Bitraten liefern.

KruXx setzt `DownloadManager.maxParallelDownloads = 5` (Media3s Ausgangswert ist drei): Bis zu fünf
Einträge stehen in `STATE_DOWNLOADING`, alle weiteren in `STATE_QUEUED`. Das ist ein vorsichtiger
Kompromiss für CDNs, die einzelne Verbindungen begrenzen; wesentlich höhere Werte erhöhen dagegen das
Risiko für Drosselung, Fehlversuche sowie Akku- und Funklast. Beide Zustände sowie `STATE_RESTARTING`
zeigen in der Oberfläche dasselbe Fortschrittssymbol. Vorher erkannte `SongItem` ausschließlich
`STATE_DOWNLOADING`; dadurch sah es exakt so aus, als seien nur drei Titel markiert worden. Der Status
wird auch offline aus dem lokalen Download-Index gelesen und nicht mehr durch einen hart codierten
„fertig“-Wert ersetzt. Media3s Fehlerversuche bleiben auf fünf gesetzt. Liedtexte/Nebenressourcen laufen
höchstens einzeln, damit bei einem großen Album oder einer Playlist die Audiostreams Netzpriorität
erhalten.

Der Download-Resolver übernimmt die von InnerTubeX gemeldete vollständige Dateilänge. Fehlt sie,
ermittelt er sie mit genau einem `Range: bytes=0-0`-Probeabruf aus `Content-Range`. Anschließend trägt
er die noch fehlende exakte Länge in den `DataSpec` ein; Media3s OkHttp-Quelle erzeugt daraus einen
begrenzten `Range: bytes=start-end`-Abruf. Damit wird die beim InnerTubeX-Umbau verlorene
Längenangabe wiederhergestellt: Der YT-CDN erhält nicht mehr für jeden Titel einen offenen Abruf mit
unbekanntem Fortschritt. Bereits im normalen Player-Cache vorhandene Bytes werden nur übernommen,
wenn itag und – sofern bekannt – Gesamtlänge zum gewählten High-Stream passen; Low-/Auto-Bytes werden
gezielt umgangen.

Lehnt der CDN eine signierte Download-URL mit HTTP 403, 410 oder 416 ab, entfernt der nur für
Downloads verwendete OkHttp-Interceptor exakt diese URL aus dem Download-Resolver-Cache. Media3s
nächster zulässiger Versuch löst den Titel dadurch neu auf, statt dieselbe abgelaufene oder abgelehnte
Adresse wiederholt zu verwenden. Die signierte URL wird dabei weder von dieser Logik protokolliert
noch gespeichert.

Im Download-Cache stehen zusätzlich `kruxx_itag`, Gesamtlänge und die Richtlinie
`kruxx_download_policy=1`. Unfertige Alt-Downloads ohne diese High-Richtlinie werden vor der
Initialisierung des `DownloadManager` sauber auf Byte null zurückgesetzt. Fertige Alt-Downloads
werden nie ungefragt gelöscht oder neu übertragen; um einen bereits vollständig gespeicherten Titel
auf High zu aktualisieren, muss er einmal entfernt und neu heruntergeladen werden. Ändert YTM bei
einem laufenden Download unerwartet itag oder Länge, bricht KruXx ab, statt zwei Dateien zu
vermischen, verwirft den unvereinbaren Teil nach dem Stopp und startet beim manuellen Wiederholen
sauber von vorn.

### 4.4 Musikvideos und Art Tracks

Die sichtbare YTM-Suche liefert bei Videotreffern neben `videoId` und Vorschaubild nach Möglichkeit
`watchEndpointMusicConfig.musicVideoType`. KruXx verwendet `playlistItemData.videoId` – falls vorhanden –
als Identität der Zeile und sucht dazu den vollständigsten passenden Wiedergabe-Endpunkt in Titel und
Zeile. Ein Typ von einer abweichenden sekundären Video-ID wird ausdrücklich nicht übernommen. Fehlt
ein vollständiger Endpunkt, bleibt die Video-ID als konservativer Audio-Fallback erhalten. Die Typen
werden wie folgt eingeordnet:

| YTM-Typ | Anzeige | Verhalten beim direkten Antippen |
|---|---|---|
| `MUSIC_VIDEO_TYPE_OMV` | Offizielles Musikvideo | eingebetteten YouTube-Player öffnen |
| `MUSIC_VIDEO_TYPE_UGC` | Nutzer-Video | eingebetteten YouTube-Player öffnen |
| `MUSIC_VIDEO_TYPE_ATV` | Audio (Standbild) | ausschließlich Audiostream abspielen |
| fehlend/unbekannt | Audio (Typ nicht erkannt) | konservativ ausschließlich Audio |

Ein erfolgreich ladbarer YouTube-Embed beweist nicht, dass ein Video bewegte Bilder enthält: Auch ein
Art Track besitzt eine Video-ID und kann sein festes Cover als Videospur ausgeben. Unbekannte Typen
werden daher nicht probeweise als Video geöffnet. Die normale KruXx-Wiedergabekette bleibt immer
audio-only; nur ein direkter Tipp auf einen sicher erkannten `OMV`-/`UGC`-Treffer erzeugt ein
MediaItem mit dem flüchtigen Auftrag `isVideo=true`. „Als Nächstes“, Warteschlange und Kontextmenüs
verwenden dasselbe Ergebnis ohne diesen Auftrag und spielen es als Audio. Die Einstellung
„Schaltfläche ‚Musikvideo suchen‘ anzeigen“ steuert nur die zusätzliche Suche im Player und ist kein
globaler Video-Schalter.

Das Videofenster verwendet `YouTubePlayerView` (IFrame) mit der `videoId`. Währenddessen bleibt der
native ExoPlayer pausiert; dessen Position wird jedoch aus den IFrame-Zeitereignissen mitgeführt. Bei
„Nur Audio abspielen“ wird das MediaItem mit `isVideo=false` an den Audio-Resolver übergeben und an
dieser Position vorbereitet. Jeder vom IFrame gemeldete Wiedergabefehler sowie eine fehlgeschlagene
Player-Initialisierung führen automatisch zum selben Audio-Rückfall und zu einem lokalen Hinweis.
Das Videofenster meldet seinen echten Wiedergabe-/Pausezustand außerdem an den optionalen
Benachrichtigungsschutz. Beim Verlassen wird es aus dem Lifecycle entfernt und freigegeben.

Wichtig für spätere Änderungen: Media3 berücksichtigt den **Inhalt** von `MediaMetadata.extras` bei
`MediaItem.equals()` nicht. `isVideo=true` und `isVideo=false` können bei derselben `videoId` deshalb
für ein `StateFlow<MediaItem>` gleich aussehen. `MainActivity` übernimmt Video-/Audio-Varianten
absichtlich direkt aus `Player.Listener.onMediaItemTransition` und speichert sie mit referenzieller
Gleichheit; diesen Übergang nicht wieder ausschließlich aus `currentMediaItemState` ableiten.

### 4.5 Startseite, Konto-Bibliothek und Interpreten

Die Startseite kombiniert mehrere Backend-Wege, die bei der Fehlersuche getrennt betrachtet werden
müssen:

```text
HomeQuickPicks
  ├─ Charts/Radio/Related/Discover → me.knighthat.innertube (`modules/innertube`)
  ├─ angemeldete YTM-Home-Sektionen → YtMusic/HomePage (`extensions/innertube`)
  └─ lokale Playlists für Home-Vereinigung → Room/Database

HomeLibraryViewModel / HomeArtistsViewModel
  ├─ YTM-Konto-Bibliothek → com.metrolist.innertube.YouTube (`modules/metrolist`)
  └─ gespeicherte lokale Einträge → Room/Database
```

**Vorschläge:** `HomeQuickPicks` wählt den aktuellen Trending-/Quick-Pick-Titel als Radio-Seed und
verwendet nur im leeren Ausgangszustand einen festen Fallback. Die Radio-Antwort wird um den Seed
bereinigt, per Song-ID dedupliziert und auf 18 Einträge begrenzt. Für die Anzeige werden Seed,
Radio-Ergebnisse und vorhandene Related-Songs erneut zusammengeführt, Kindersicherung und Dubletten
angewendet und je nach Ergebniszahl ein bis drei `LazyHorizontalGrid`-Reihen mit der normalen
`SongItem`-Höhe erzeugt. Der Spinner erscheint nur, solange höchstens der einzelne Seed sichtbar ist.
Der kleine Play-Button startet den ersten Eintrag und hängt den Rest als zusammenhängende Queue an;
das Antippen eines einzelnen Vorschlags behält das bisherige Radio-Verhalten.

**Aktualisieren:** `HomeQuickPicks.refresh()` setzt Anzeigezustände und
`Preferences.IS_DATA_KEY_LOADED` ausschließlich im Main-Kontext des gebundenen Compose-Scopes
zurück; die aufgerufenen suspendierenden Netz- und Datenbankfunktionen übernehmen ihre
I/O-Umschaltung selbst. Das ist erforderlich, weil der zentrale Preference-Schutz Schreibzugriffe
von Hintergrundthreads ablehnt und dafür bewusst das rote Meldebanner anzeigt. Wird der
Vorschlags-`LaunchedEffect` durch einen neuen Seed regulär ersetzt, wird seine
`CancellationException` weitergereicht und nicht als echter Ladefehler protokolliert. Der
`refreshing`-Zustand wird auch bei Fehler oder Abbruch in `finally` zuverlässig beendet.

**Top Artists:** Die komplette Rangzeile einschließlich Rang, Bild, Name und Abonnentenzahl ist
klickbar und navigiert über `NavRoutes.YT_ARTIST` mit der jeweiligen Artist-ID. Eine fehlende
Thumbnail-URL verhindert die Navigation nicht.

**„From your Library“ und Bibliotheks-Playlists:** `HomeQuickPicks` erkennt die YTM-Kontosektion über
deren stabilen Browse-Endpunkt (`FEmusic_liked_playlists`, mit dem älteren
`FEmusic_library_landing` als kompatible Variante). `ItemUtils.LazyRowItem` hängt lokale
`PlaylistPreview`-Einträge an dieselbe Reihe an, sofern ihre normalisierte Browse-ID nicht bereits in
der Serverantwort vorkommt. Gibt YTM keine solche Sektion zurück, zeigt die Startseite nach Abschluss
des Home-Abrufs einen eigenen lokalen Playlist-Abschnitt. So bleiben lokale Listen auch angemeldet
sichtbar, ohne die Konto-Einträge zu verdoppeln.

Der Bibliotheksreiter arbeitet in Gegenrichtung: `HomeLibraryViewModel` ruft bei Anmeldung und
aktiviertem Schalter „Wiedergabelisten synchronisieren“ nicht mehr einmalig die generische Landing-
Seite ab, sondern `YouTube.library("FEmusic_liked_playlists").completed()`. `completed()` folgt bis
zum Ende durch die Continuation-Tokens; „Gespeicherte Folgen“ (`SE`) bleibt als Podcast-Sonderfall
außen vor. `mergeAndSortPlaylists()` vereinigt die Live-Antwort mit Room. IDs mit und ohne
Browse-Präfix `VL` bilden dieselbe Identität. Bei einer Dublette gewinnt der lokale Datensatz samt
lokaler ID, Songanzahl und Editierbarkeit; ein fehlendes lokales Cover wird aus der Live-Antwort
ergänzt. Titel- und Songanzahl-Sortierung werden nach dem Merge global angewendet. Da YTM weder
lokales Hinzufügedatum noch lokale Hörzeit liefert, bleiben bei diesen Sortierungen Serverreihenfolge
und bereits sortierte DB-Reihenfolge erhalten. Anmeldung, Abmeldung, Änderung der Sync-ID und der
Playlist-Sync-Schalter werden als Statusfluss beobachtet und führen zusammen mit Pull-to-refresh in
denselben `collectLatest`-Pfad. Eine neue Anforderung bricht damit einen veralteten Abruf ab; bei
Abmeldung oder ausgeschaltetem Sync verschwindet nur der Kontoanteil. Ein Requestfehler behält die
letzte erfolgreiche Live-Liste. Ist nach Suche und Filter nichts sichtbar, unterscheidet der
Leerzustand zwischen Such-/Filtertreffer, fehlender Anmeldung, deaktiviertem Sync, Abruffehler und
einer tatsächlich leeren Bibliothek.

Die YTM-Home-Sektionen selbst werden nicht in Logcat ausgegeben. Fehlerlogs nennen weiterhin nur den
technischen Pfad, nicht Playlist-Inhalte, Suchtexte, Cookies oder Kontodaten.

Die manuelle Kernabnahme dieses Pfads war am 04.09.2026 auf einem Samsung SM-S931B mit Android 16
erfolgreich: Nach der Anmeldung und Aktivierung des Playlist-Syncs erschienen alle erwarteten
YTM-Kontolisten; auch eine im isolierten Debug-Profil angelegte lokale Testliste wurde gemeinsam mit
ihnen angezeigt. Der danach aus dem finalen Arbeitsstand erzeugte Debug-Build wurde per
`adb install -r` mit erhaltenem Datenstand eingespielt, startete ohne Crash oder ANR und wurde auf
demselben Gerät für beide Playlist-Quellen erneut als funktionierend bestätigt. Die weitergehenden
Filter-, Dubletten-, Sync-aus- und Fehlerfälle bleiben Bestandteil des folgenden Release-Testplans.
Auf demselben finalen Debug-Stand wurden anschließend zwei direkte Aktualisierungen der Startseite
per Wischgeste ausgeführt: Die Vorschläge wurden jeweils neu aufgebaut, das rote Meldebanner blieb
aus und der Prozess aktiv. Logcat enthielt weder einen Hintergrundthread-Schreibzugriff auf
`IsDataKeyLoaded` noch den erwartbaren Effekt-Abbruch als Fehler, `AndroidRuntime`, Crash oder ANR.

Nur nicht lokal repräsentierte Konto-Playlists erhalten bewusst `Playlist.id = -1L` und
`isYoutubePlaylist = true`; `PlaylistItem` routet sie zu `YT_PLAYLIST` statt zur lokalen Datenbank und
hängt den Login-Parameter an.

`YouTubePlaylistViewModel` trennt danach bewusst zwei Transportwege: Mit `useLogin=true` werden erste
Seite und Fortsetzungen über Metrolists `YouTube.playlist()` beziehungsweise
`playlistContinuation()` geladen. Das ist derselbe Client samt Sitzung, Cookies, `visitorData` und
`dataSyncId`, den KruXx bereits für Login und Bibliothek nutzt und der im direkten Vergleich die
betroffenen Listen vollständig lieferte. Weil Metrolist intern selbst `VL` ergänzt, übergibt das
ViewModel dort die nackte Playlist-ID; intern und für Teilen/Export hält es weiterhin die
normalisierte Browse-ID mit exakt einem `VL`. Öffentliche/anonyme Listen verwenden unverändert
`me.knighthat.innertube`, einschließlich dessen `visitorData` für Fortsetzungen. Beide Antworten
werden auf dasselbe schlanke Header- und `Song`-Modell abgebildet; ein Mutex verhindert doppelte
Continuation-Abrufe am Listenende.

Der inzwischen ebenfalls korrigierte Login-Pfad in `me.knighthat.innertube` bleibt durch
`InnertubeImplAuthenticationTest` abgesichert: `ytmBrowse()`/`ytmNext()` reichen `useLogin` bis
`post()` weiter und senden Cookie, Visitor-Header sowie exakt `SAPISIDHASH <Zeit>_<SHA1>` ohne das
frühere `_u`-Suffix. Er ist jedoch nicht mehr der Produktionspfad für angemeldete Playlistseiten.
Die Dateiendung eines enthaltenen Elements (`mp4`, Video oder Audio) entscheidet nicht, ob die
Playlistseite geladen werden kann; nicht abspielbare Zeilen ohne Video-ID werden übersprungen.

**Interpreten-Synchronisation:** YTM kann `musicThumbnailRenderer` ohne das Array `thumbnails`
liefern. `Thumbnails.thumbnails` hat deshalb `emptyList()` als Default; ein einzelner Eintrag ohne
Bild darf nicht mehr die komplette Browse-Antwort mit `MissingFieldException` verwerfen und ein
rotes „Synchronisation fehlgeschlagen“-Banner auslösen. Das Banner ist nur noch bei einem echten
Fehlschlag der Browse-Anfrage/Deserialisierung berechtigt.

`HomeArtistsViewModel` hält die aktuelle Online-Antwort und die bereits sortierte lokale
Datenbankliste getrennt. `mergeAndSortArtists()` führt beide nach `Artist.id` zusammen, übernimmt
nützliche Namen-, Bild-, Zeit- und Bookmark-Metadaten, entfernt Dubletten und wendet danach Filter
und Sortierung global an. Bei `TITLE` wird `cleanName` verwendet und anschließend die gewählte
auf-/absteigende Reihenfolge angewendet. Bei `DATE_ADDED` bleibt die Server-/DB-Reihenfolge erhalten,
weil YTM für diese Antwort keinen verlässlichen Hinzugefügt-Zeitpunkt liefert; `RANDOM` mischt wie
gewählt. Das bedeutet: Alphabetische Anzeige ist garantiert, wenn in der Oberfläche
„Titel / aufsteigend“ ausgewählt ist, nicht bei den beiden anderen Sortiermodi.

### 4.6 Schwebender Player (Android Picture-in-Picture)

Die Nutzeroptionen liegen unter **Einstellungen → Darstellung**:

- `IS_PIP_ENABLED`: „Schwebenden Player erlauben“, standardmäßig aus;
- `IS_AUTO_PIP_ENABLED`: „Beim Verlassen automatisch öffnen“, standardmäßig an und nur sichtbar,
  wenn der Hauptschalter aktiv ist.

PiP ist ab Android 7 verfügbar. `MainActivity` bindet `PipEventContainer` nur dann aktiv an, wenn der
Hauptschalter gesetzt und ein aktuelles `MediaItem` vorhanden ist; ein leerer Player erzeugt bewusst
kein Fenster. `PictureInPictureParams` werden bei Änderungen an Quellrechteck, Seitenverhältnis,
Aktionen oder Auto-Option erneut gesetzt. Android 12+ übernimmt den automatischen Eintritt über
`setAutoEnterEnabled`; Android 7–11 benötigen den registrierten `OnUserLeaveHintListener`, der beim
echten Verlassen `maybeEnterPip()` aufruft. So funktioniert sowohl Gesten-/Home-Navigation als auch
das klassische Verlassen auf älteren unterstützten Versionen.

Das PiP-Fenster bildet den aktuellen Player-Container ab: bei normaler Audiowiedergabe das Cover,
bei aktivem eingebettetem Musikvideo dessen Player. Play/Pause-/Beenden-Aktionen und das
Quellrechteck bleiben synchron. Android beziehungsweise die Herstelleroberfläche kann PiP pro App
dennoch sperren; das ist zusätzlich in den System-App-Einstellungen zu erlauben.

Nicht verwechseln: Die Einstellung „Cover im Player anzeigen“ steuert ausschließlich das
separate Cover im großen KruXx-Player. Sie aktiviert Android-PiP nicht. Um beim Wegwischen/Verlassen ein
kleines Fenster zu erhalten, müssen „Schwebenden Player erlauben“ und die Auto-Unteroption aktiv
sein, ein Titel muss laufen und das Gerät muss PiP für KruXx zulassen.

### 4.7 Android Auto und MediaLibrary

`AndroidManifest.xml` exportiert `PlayerServiceModern` als Media3-`MediaLibraryService` mit dem
Legacy-`MediaBrowserService`-Intent, Media-Playback-Foreground-Service und `MediaButtonReceiver`.
`res/xml/automotive_app_desc.xml` deklariert die App als `media`; Android Auto rendert daraus seine
eigene fahrgerechte Oberfläche. Eine separate Car-App-Template-UI ist für diesen Medientyp nicht der
Steuerungspfad.

`MediaLibrarySessionCallback` stellt unter dem browsbaren Root genau vier Haupteinstiege bereit:
Titel, Interpreten, Alben und Playlists. Container- und Titel-IDs werden ausschließlich über
`MediaLibraryId` gebaut und URI-kodiert; ein Leaf trägt seinen Parent und seine echte Song-ID. Beim
Antippen kann `onSetMediaItems` deshalb dieselbe vollständige, unpaginierte Liste erneut laden, den
gewählten Titel finden und die komplette Queue mit korrektem Startindex an den Player geben. Die
Datenquellen und Reihenfolgen liegen zentral in `loadSongsForParent`, damit Browse und Play nicht
auseinanderlaufen.
Ältere `searched/<songId>`- und `song/<songId>`-IDs bleiben lesbar. `onGetItem` löst Root, Kategorie,
Container, Leaf und rohe Datenbank-ID auf; bei einem inzwischen veralteten Leaf wird nach Möglichkeit
der gespeicherte oder mitgelieferte Titel gespielt statt still auf Index 0 auszuweichen.

`onGetChildren` und `onGetSearchResult` beachten für moderne Media3-Browser `page`/`pageSize` und
rechnen den Offset über `Long`, damit große Werte nicht überlaufen. Android Auto selbst paginiert den
Browse-Baum nicht; Media3s Legacy-Adapter fordert dafür Seite 0 mit maximaler Seitengröße an und
erhält weiterhin die vollständige Liste. Nicht vorhandene Parent-/Item-IDs melden
`ERROR_BAD_VALUE` statt eine scheinbar erfolgreiche leere Antwort.

Die Suche arbeitet asynchron: `onSearch` lädt lokale und YTM-Titel parallel, dedupliziert nach
Song-ID, speichert höchstens acht normalisierte Anfragen und meldet danach die echte Trefferzahl über
`notifySearchResultChanged`. `onGetSearchResult` nutzt den zur Anfrage gehörenden Cache statt einer
globalen veränderlichen Ergebnisliste. Suchtexte werden nicht geloggt. Android-Auto-Sprachaktionen
kommen zusätzlich als `requestMetadata.searchQuery` in `onSetMediaItems`/`onAddMediaItems`; eine
leere Anfrage fällt bewusst auf die Top-Titel zurück. Der eigene Handy-Suchbutton startet eine
Activity und wird deshalb mit `isAutomotiveController`/`isAutoCompanionController` aus den für Autos
verfügbaren Commands entfernt. Die native MediaLibrary-Suche bleibt davon unberührt.

Für Wiedergabe-Wiederaufnahme wird die aktuelle Callback-Variante mit `isForPlayback` verwendet. Bei
einer reinen Metadatenabfrage erhält Android Auto genau den zuletzt aktiven Titel samt Media3-
Fortschrittsstatus; bei echter Wiedergabe die gesamte persistente Queue, Startindex und Position. Eine
leere Queue ist ein normaler leerer Rückgabewert. `PlayerServiceModern.onDestroy()` ruft `release()`
am Library-Callback auf, damit laufende Browse-/Suchjobs nicht über das Dienstende hinaus leben.

Ergänzung für 1.2.1: Der Dienst gibt auch seinen eigenen Controller, seine Mediensitzung, Listener,
Beobachter und periodischen Queue-Job frei. Der Controller verbindet sich direkt mit dem Token
der vorhandenen Sitzung; eine Bindung an den eigenen Dienst würde dessen Ende verhindern.
`addSession(mediaSession)` registriert die Sitzung ausdrücklich beim Dienst, da der direkte
Controller nicht über `onGetSession()` kommt. Diese Registrierung ist für Medienbenachrichtigung,
deren Steueraktionen und den Vordergrunddienst während der Wiedergabe erforderlich.
Jeder Bereinigungsschritt wird unabhängig ausgeführt,
damit ein Fehler nicht die Sitzungsfreigabe überspringt und beim nächsten Dienststart
`Session ID must be unique` auslöst. Player und Caches sind derzeit Koin-Singletons mit
Anwendungslebensdauer: Der Dienst darf die Wiedergabe stoppen, aber diese gemeinsam verwendeten
Instanzen nicht freigeben. `StatefulPlayerImpl.release()` berücksichtigt zusätzlich, dass vor der
ersten Wiedergabe noch keine Audioeffekte existieren; dafür gibt es einen Robolectric-Regressionstest.

Automatisierte Parser-/Paging-Tests sichern Sonderzeichen, leere Suche, Alt-ID-Kompatibilität,
Fehleingaben und Integer-Überlauf. Sie ersetzen nicht den Sicherheits-/Darstellungstest im Desktop
Head Unit und echten Fahrzeug; dieser steht in §7.3 und bleibt Release-Gate.

---

### 4.8 Gemeinsame Glasflächen für Menüs und Tracklisten

Nacharbeit vom 06.09.2026, veröffentlicht mit 1.2.0:

- `CustomModalBottomSheet` ist der gemeinsame Host für ältere `Menu`-/`GridMenu`-Aufrufe und
  `BottomMenu`. Der Glas-Modifier gehört an die innere Inhalts-Column einschließlich Griff:
  Material3 setzt seine Sheet-Verschiebung erst hinter den öffentlichen Modifier. Ein Clip dort
  schnitt auf dem Samsung die aufklappenden Playlist-Menüs ab. Der Host berücksichtigt bereits
  verbrauchte Navigations-Inset-Flächen; Ausklappen, Wischen, Zurück und Griff-Semantik bleiben erhalten.
- `LocalKruxxGlassSheet` lässt verschachtelte Menüs die Host-Fläche wiederverwenden. Außerhalb dieses
  Hosts behalten `Menu` und `GridMenu` ihre eigene Glasfläche, einschließlich des separaten
  Vollbild-Player-Menüs. Vollbild-Audio-/Video-Sheets und die deckende Queue bleiben gesondert behandelt.
- `ThemedAlertDialog` verbindet das Material-Dialoglayout mit `kruxxDialogSurface` und einer lokal
  angepassten Material-Farbpalette. Download-/Kopierauswahl, Verlauf, Fortschritt, Dateientfernen und
  der Live-Hintergrund-Hinweis verwenden diesen Baustein. `KruxxDialogBackdrop` setzt ab API 31
  einen `RenderEffect` auf die Activity-Ansicht hinter dem separaten Dialogfenster (16 dp, höchstens
  64 Pixel). Referenzzählung hält überlappende Dialoge korrekt; nach dem letzten Schließen wird
  der Effekt entfernt. Kein systemweiter Fenster-Blur nötig, ältere APIs nutzen nur die Tönung.
  Die übrigen Dialogbasen, Künstlerauswahl,
  Darstellungsvorschau, Dropdowns und der Spiel-Dialog sind ebenfalls auf gemeinsame Flächen geprüft.
- `KruxxGlass.modalBackdropAlpha` (`0.88f`) hält schwebende Menüs und Dialoge über Bildern und Schrift
  lesbar. `kruxxTrackCard()` liefert dagegen leichte Karten ohne Einzelschatten/Blur mit 8 dp
  horizontalem und 3 dp vertikalem Außenabstand. Aufrufer sind Künstler-Online-/Bibliothekslisten,
  lokale/Online-Playlists, Online-Titel-/Videosuche, Suchvorschläge und Bibliothekssuche sowie
  Titel-/Geräte- und Statistiklisten. Den Modifier nicht global auf `SongItem.Render`
  anwenden: andere Aufrufer wie Quick Picks besitzen bereits eigene Karten.
- Die beiden `ButtonsRow`-Signaturen verwenden dieselbe Implementierung. `kruxxFilterBar()` gibt
  Titel-, Künstler-, Alben-, Playlist-, Downloads-, Verlaufs- und Statistikfiltern eine gemeinsame
  Glasfläche mit 8 dp horizontalem und 4 dp vertikalem Außenabstand. Nur die innere Chip-Row scrollt;
  Kontur und optionales `trailingContent` bleiben stehen. Die Aufrufer setzen in KruXx keine weiteren
  Seitenränder. Die separate `TabRow` im Release-Änderungsdialog verwendet denselben Modifier.
  Quellenfilter in `HomeArtist` und `HomeAlbum` nutzen `trailingContent` mit begrenzter Breite statt
  einer über den Chips liegenden Box. Beschriftungen bleiben einzeilig; bei Platzmangel wird die
  Chip-Leiste horizontal gescrollt. Die Glasfläche erzeugt keinen zusätzlichen Blur oder Schatten.
  Die mit 1.2.1 veröffentlichte Ergänzung vom 08.09.2026 nutzt `HorizontalScrollWithArrows` für antippbare
  Richtungshinweise in allen diesen Filtern sowie den Suchfiltern. Ein `BringIntoViewRequester`
  je stabilem Chip-Schlüssel hält die aktive Auswahl nach Auswahl- und Breitenwechseln sichtbar.
  Werkzeugleisten bleiben eigenständige Aktionsleisten mit ihrem bestehenden Überlaufmenü.
  `HomeSongsScreen` berücksichtigt beim Merken der sichtbaren Filter nun auch `showOnDevice`.
- `StatisticsPage` verwendet `kruxxCardSurface()` für Titelanzahl/Wiedergabezeit und
  `kruxxTrackCard()` für die Rangliste in allen Zeiträumen. Die separate Boolean-Präferenz
  `STATISTICS_GRID_VIEW` (`StatisticsGridView`, Vorgabe `false`) speichert die Ansicht unabhängig
  von Bibliotheks- oder Menüoptionen. KruXx verwendet eine Spalte für die Liste und zwei für das
  Raster; die übrigen Statistik-Kategorien behalten ihre bisherigen Raster.
  `StatisticsViewSwitch` bietet beschriftete, auswählbare Schaltflächen mit mindestens 48 dp
  Höhe. `StatisticsSongGridItem` ordnet Cover/Rang, Dauer und Downloadaktion in einer oberen Zeile
  an, darunter Titel und Künstler. Thumbnail, Badges und Downloadknopf werden wiederverwendet;
  Liste und Raster teilen dieselben Wiedergabe- und Kontextmenüaktionen.

- `kruxxItemCard()` ist seit dem 11.09.2026 die gemeinsame Kachelfläche für Alben, Künstler und
  Playlists. Sie wird **einmalig** in `AlbumItem.VerticalStructure`/`HorizontalStructure`,
  `ArtistItem.Structure` und `PlaylistItem.VerticalStructure`/`HorizontalStructure` angewendet,
  nicht mehr je Aufrufstelle: Genau diese Verteilung hatte dazu geführt, dass nur Startseite und
  Bibliotheksreiter eine Fläche besaßen. Den Modifier deshalb nicht zusätzlich an eine dieser
  Komponenten übergeben. Der Baustein prüft `LocalKruxxGlassSheet`, damit Kacheln innerhalb einer
  Menü- oder Sheet-Glasfläche deren Fläche weiterverwenden; die Kopfkacheln der Alben- und
  Playlist-Kontextmenüs stellen dieses Local dafür ausdrücklich auf `true`. Song-Kacheln behalten
  ihren eigenen Weg (`kruxxTrackCard()` in Listen, `ItemUtils.LazyRowItem` in gemeinsamen Reihen),
  weil `SongItem` auch von Aufrufern mit eigener Karte verwendet wird.
- `kruxxTrackCard()` prüft seit dem 11.09.2026 ebenfalls `LocalKruxxGlassSheet`. Für alle
  bisherigen Aufrufer ändert das nichts, weil sie auf Bildschirmen und nicht in Sheets liegen;
  Queue und die Videosuche des Players behalten dadurch aber ihre eine durchgehende Fläche, falls
  dort später Karten ergänzt werden. Neu erhalten Albumseite, beide Verlaufslisten und die
  Podcast-Episodenliste diesen Modifier; sie hatten ihn als einzige Titellisten nie bekommen.
- `HorizontalStructure` in `AlbumItem` und `PlaylistItem` reichte den übergebenen `modifier`
  zusätzlich an die innere `Column` weiter. Jede dort übergebene Fläche wäre doppelt gezeichnet
  worden; die innere Spalte verwendet jetzt ein eigenes `Modifier`.

Der detaillierte Umfang steht in [`Design.md`](Design.md#lokale-menü--und-listennacharbeit-vom-06092026),
der nachgewiesene Prüfstand in [`KRUXX-IST-STAND.md`](KRUXX-IST-STAND.md). System-Dialoge und eingebettete
Anmeldeseiten haben ihre eigene Darstellung.

## 5. Icon-Pipeline

```bash
python3 scripts/make-kruxx-icon.py icons/KruXx_App_Icon.png \
  --glyph-ratio 0.66 --bg '#000000' --mono text
```

erzeugt in `composeApp/src/androidKruxx/res/`: adaptives Icon (`mipmap-anydpi-v26/ic_launcher*.xml`
→ `@mipmap/kruxx_ic_launcher_foreground`, `@color/kruxx_ic_launcher_background`,
`@mipmap/kruxx_ic_launcher_monochrome`), Legacy-Icons (`mipmap-*/ic_launcher*.png`), TV-Banner
(`ic_banner.png`), Benachrichtigungs-Glyph (`drawable-*/app_icon_monochrome.png`), die Wortmarke
(`drawable/app_logo_text.png`, „KruXx – The core of your music“ im Header; fettes „KruXx“ größer als
der reguläre Zusatz) und
`drawable/ic_banner_foreground.png` als KruXx-Platzhalter für Titel ohne Cover. Wortmarke und
Cover-Platzhalter werden von der App eingefärbt.

Besonderheiten des aktuellen Icons: Das 1254²-Quellbild besitzt bereits einen echten Alpha-Kanal;
seine abgerundete dunkle Metallkachel wurde sauber vom früheren schwarzen Außenbereich freigestellt.
Die Pipeline behandelt sie als Glyph (~71 dp), bewahrt die Transparenz in klassischen/Legacy-Icons
und verwendet für adaptive Icons explizit Schwarz als Hintergrund. Für Themed Icons und
Benachrichtigungen wird ein fettes „K“ gerendert (`--mono text`), weil die glänzende 3D-Grafik keine
brauchbare einfarbige Silhouette liefert. Der Generator entfernt ausschließlich die von ihm
verwalteten Zieldateien; Startanimation, Theme-Werte und andere Flavor-Ressourcen bleiben erhalten.
Optionen: `--bg '#RRGGBB'`,
`--mode glyph|cover`, `--mono alpha|luminance|text|none`, `--mono-text`, `--wordmark`, `--tagline`,
`--font`, `--regular-font`.
Nach dem Generieren: `git add composeApp/src/androidKruxx/res` und neu bauen.

---

## 6. Upgrades

### 6.1 YouTube hat sich geändert → InnerTubeX aktualisieren (Normalfall)

1. Releases prüfen: <https://github.com/MetrolistGroup/innertubex/releases> (Changelog lesen – 0.x-API kann brechen).
2. `gradle/libs.versions.toml`: `innertubex = "vX.Y.Z"`.
3. `./gradlew :composeApp:assembleKruxxUniversalProdDebug` – kompiliert es? Bei API-Änderungen betroffen sind nur
   `InnerTubeXPlayer.kt` und `InnertubeResolvingDataSource.kt`; Metrolists aktueller
   `app/src/main/kotlin/com/metrolist/music/utils/InnerTubeXPlayer.kt` ist die Referenz dafür, wie die neue API benutzt wird.
4. Prüfen, ob InnerTubeX neue Ktor/Kotlin-Versionen verlangt (`gradle/libs.versions.toml` im InnerTubeX-Repo) und ggf. nachziehen.
5. Kreate-Besonderheiten gegen die neue Version prüfen: `requiresBoundedMediaRange`/`usesChunkedMediaRanges` in
   `InnerTubeExtractor.kt` (welche Clients fallen durch `allowBoundedRange=false` weg?) und ob `buildHeaders`
   weiterhin den User-Agent je Client liefert (§4). Metrolists Resolver-Code **nicht** 1:1 übernehmen – die
   Datenquellen-Kette ist anders (§4).
6. `scripts/build-local-release.sh kruxx`, APK installieren, testen (§7.3).

### 6.2 Kreate nur als Referenz prüfen

```bash
git fetch upstream
git log --oneline main..upstream/main
git diff main...upstream/main -- <betroffener Pfad>
```

Es gibt keinen automatischen Merge/Rebase mehr. Eine nützliche Kreate-Korrektur wird auf Lizenz,
Abhängigkeiten und Auswirkungen auf KruXx geprüft und dann als eigener KruXx-Commit umgesetzt oder
gezielt per `git cherry-pick -x <commit>` übernommen. Besonders kritisch sind
`composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, Player, Suche, Downloads und App-Start.

### 6.3 Submodule aktualisieren

```bash
git -C modules/metrolist fetch upstream
git -C modules/metrolist log --oneline HEAD..upstream/kreate-old
git -C modules/metrolist checkout <commit>   # dann KruXx-Patch neu anwenden/testen/committen
git add modules/metrolist                    # Gitlink im Hauptrepo gemeinsam aktualisieren
```
`origin` des Submoduls zeigt auf `Massefehler/KruXx-metrolist`; das fremde Repository ist dort nur
als schreibgeschütztes `upstream` eingetragen. Neben `kreate-old` gibt es dort auch neuere
Metrolist-Zweige. Ein Wechsel ist eine Migration: API, Parser und Versionskatalog müssen zuerst im
KruXx-Root-Build zusammenpassen.

`modules/innertube` zeigt auf den KruXx-Spiegel (§2.3). Vor einem Update dort den ursprünglichen
GitLab-Stand prüfen, den KruXx-Fix neu testen und danach Spiegel-Pin sowie Patch-Datei gemeinsam
aktualisieren.

### 6.4 Toolchain

- **compileSdk 37**: sobald `sdkmanager --list | grep platforms` ein `platforms;android-37` zeigt →
  installieren, `compileSdk = "37"` (targetSdk explizit bei 36 lassen), Flag
  `android.experimental.disableCompileSdkChecks` aus `gradle.properties` entfernen.
- **AGP 9 / Kotlin-Updates**: Upstream hat Dependabot-Branches (`dependabot/gradle/agp-9.3.2`,
  `kotlin-2.4.10`, …). AGP 9 beseitigt die D8-Warnungsflut; danach `disableCompileSdkChecks`
  erneut prüfen. Immer zuerst Debug bauen, dann Release.
- **SDK-Pakete**: `~/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=~/android-sdk --update`.

---

## 7. Bugfixing-Leitfaden

### 7.1 Logs holen

```bash
adb logcat -v time | grep -E 'InnerTubeX|dataspec|ExoPlayerListener|InnerTube|Cipher|PoToken|PlayerConfig'
```
Ohne ADB: KruXx schreibt Crashlogs ausschließlich lokal nach
`Android/data/de.kruxx.music/files/crashlogs/` (bei vorübergehend nicht verfügbarem externem
App-Speicher in den internen App-Speicher); in der App unter Einstellungen → Sonstiges → Debug
lassen sie sich exportieren/kopieren. Es gibt keinen automatischen Versand und im KruXx-Flavor auch
keinen CrashReport-Dialog oder Link zum Upstream-Issue-Tracker.

### 7.2 Typische Fehlerbilder

| Symptom | Wahrscheinliche Ursache | Wo ansetzen |
|---|---|---|
| „Kein abspielbares Format“ / `StreamResolveException NO_PLAYABLE_STREAM` bei allen Songs | YouTube hat Clients geändert; InnerTubeX-Version veraltet | §6.1 – Bibliothek aktualisieren |
| Jeder Online-Titel bleibt trotz erfolgreicher Auflösung und HTTP 200 bei Position 0 in `BUFFERING` und startet erst nach langer Zeit | Ein `HttpLoggingInterceptor` mit Body-Protokollierung puffert den gedrosselten Medien-Response vollständig vor Media3 | `OkHttpDataSource` nur mit `forMediaTransport()` bauen. Bei einem Debug-Test dürfen keine HTTP-Logger-Zeilen für CDN-Medienabrufe erscheinen; signierte URLs nicht in Testnotizen übernehmen |
| HTTP 403 kurz nach Start, Song springt/stoppt | Signatur/`pot` abgelehnt; Recovery läuft (max. 3× je Song) | Log `Stream of … rejected`; wenn dauerhaft: InnerTubeX-Update, ggf. `refreshAfterStreamRejection` |
| Song endet nach ~30 s oder bricht mit „unbekanntem Fehler“ ab, danach bei jedem Abspielen | Cache-Index hat eine zu kleine Gesamtlänge (begrenzter Sub-Range im Cache, §4) | Seit 02.09.2026 ausgeschlossen; Altlasten: Player-Cache leeren (Einstellungen → Daten) |
| „Skipping atom with length > 2147483647“ / „Unrecognized input format“ / „unbekannter Wiedergabefehler“ bei Position 0, nur bei bestimmten Songs | gecachte Bytes gehören zu einem anderen itag als der aufgelöste Stream (§4: Qualität/Client gewechselt) | Seit 02.09.2026 heilt sich das selbst (Log `Cached data of … unusable`, `Cached bytes of … are itag`); wenn nicht: Player-Cache leeren (Einstellungen → Daten) |
| Nur bestimmte Songs: `AGE_RESTRICTED` / `LoginRequiredException` | Altersbeschränkung; braucht Login + PO-Token | YouTube-Login in der App; PoToken-Logs prüfen |
| `PoToken … timed out` / `BadWebViewException` | System-WebView fehlt/kaputt; InnerTubeX fällt auf tokenfreie Clients zurück | Android System WebView aktualisieren |
| Suchergebnisse/Vorschläge leer, Wiedergabe geht | YTM-Filter/Antwort hat sich geändert oder die `:oldtube`-Sitzung ist veraltet; nicht InnerTubeX | `extensions/innertube`, `SearchResultScreen`, Innertube-Logs; anonym und angemeldet vergleichen |
| Track ist nachweislich auf YTM, fehlt aber unter „Titel“ | YTM sortiert ihn nur als Video/UGC ein; Region/Konto/Restriktion weicht ab; Parser kannte den Endpunkt nicht | Seit 2.2.3-kruxx.5 werden Titel+Videos zusammengeführt, App-Sprache/-Region und Login genutzt sowie weitere Endpunktformen erkannt. Bleibt er weg: YTM-URL/`videoId` mit beiden Kontomodi prüfen |
| „Vorschläge“ zeigt nur einen anders großen Titel und dauerhaft einen gestrichelten Kreis | Nur der Seed war vorhanden; die alte Darstellung reservierte keine normalen Song-Zeilen oder der Radio-Aufruf scheiterte | Seit 1.0.1: Seed + deduplizierte Radio-/Related-Ergebnisse, bis zu 18 Titel in 1–3 normalen Reihen; Loader nur bei höchstens einem Ergebnis. Bei erneutem Auftreten Log-Tag `HomeQuickPicks` und `Innertube.radio()` prüfen (§4.5) |
| Beim Herunterziehen auf der Startseite erscheint das rote Banner „Error occurs! Please submit report …“, obwohl die Inhalte geladen werden | `HomeQuickPicks.refresh()` lief im IO-Kontext und schrieb anschließend `Preferences.IS_DATA_KEY_LOADED` vom Hintergrundthread; der zentrale Preference-Schutz zeigte deshalb das allgemeine Meldebanner | Seit 1.1.0 läuft der gebundene Refresh im Main-Kontext; suspendierende Backends schalten intern auf I/O. Bei erneutem Auftreten nach `Preferences: IsDataKeyLoaded is being written on thread` und `HomeQuickPicks` filtern (§4.5) |
| „Top Artists“ reagiert nicht auf Tippen | Rangzeile hatte keine Navigation | Seit 1.0.1 navigiert die ganze Zeile über `NavRoutes.YT_ARTIST`; bei erneutem Auftreten Artist-ID und überlagernde Modifier prüfen (§4.5) |
| Android Auto zeigt eine leere Bibliothek, startet einen anderen Titel oder nur einen einzelnen Titel statt der gewählten Liste | Root/Leaf war nicht browsbar beziehungsweise Media-ID, Suchergebnis und Wiedergabe-Queue wurden unterschiedlich interpretiert | Seit 1.0.2 zentralisieren `MediaLibraryId` und `loadSongsForParent` ID-Auflösung, Reihenfolge und Startindex (§4.7). Im DHU Parent-/Leaf-ID und `onSetMediaItems` prüfen; Suchtexte oder Kontodaten nicht loggen |
| Automatischer Updatehinweis erscheint nicht | Debug-Build (Self-Updater absichtlich aus), Modus deaktiviert, noch kein validiertes Netz oder erfolgreicher Abruf liegt weniger als 24 Stunden zurück | Mit signiertem Release-Build prüfen. Seit 1.0.2 wartet KruXx im Vordergrund auf Netz, reagiert auf Netzrückkehr, wiederholt transiente Fehler begrenzt und speichert das Intervall erst nach erfolgreicher Validierung. Bleibt der Hinweis aus: Modus, `last_successful_check` und Log-Tag `KruXxUpdater` prüfen, keine URL-/Kontogeheimnisse kopieren (§3.1, §7.3 Nr. 20) |
| Update wird erkannt, APK-Download bleibt auf Android 7 bei null Bytes und wartet auf Wiederholung | Auf dem API-24-Testemulator lehnt Androids Downloadmanager die Zertifikatskette ab: `CertPathValidatorException: Trust anchor for certification path not found`, Status 194 (`WAITING_TO_RETRY`) | Signierte APK auf einem aktuellen Rechner herunterladen, übertragen und über die bestehende App installieren; App-Daten erhalten. API-24-Upgrade per APK und vollständiger Self-Updater auf API 36 sind bestanden (§3.1) |
| „From your Library“ zeigt nur YTM-/YT-Listen, im Reiter „Playlists“ fehlen dagegen alle Konto-Listen | Die Startseite zeigte eine reine YTM-Home-Sektion; der Reiter kombinierte Room mit einem einmaligen, für diesen Zweck falschen `FEmusic_library_landing`-Abruf | Seit 1.0.2 ergänzt Home fehlende lokale Listen und der Reiter lädt `FEmusic_liked_playlists` samt Continuations. `VL`-IDs werden dedupliziert; Login-/Sync-Wechsel lösen automatisch neu aus. Der eingeblendete Leertext unterscheidet Anmeldung, Sync-Schalter und Abruffehler (§4.5) |
| Von mehreren YTM-Playlists lädt nur eine; die übrigen öffnen leer oder melden HTTP 401 | Synchronisierte Konto-Playlist wurde als lokale DB-ID behandelt oder der angemeldete Abruf lief fälschlich über `me.knighthat.innertube`. Enthaltene Videos/`mp4` verhindern nicht das Laden der Playlistseite | Seit 1.0.1: Online-ID `-1`, `isYoutubePlaylist=true`; angemeldete Seite samt Continuation über Metrolists `YouTube.playlist()`, anonyme Listen über `me.knighthat.innertube`. Bleibt eine einzelne Liste leer: dieselbe Playlist im YTM-Konto prüfen und normalisierte Browse-ID/Session untersuchen (§4.5) |
| Interpreten sind sichtbar, Pull-to-refresh zeigt trotzdem das rote Banner „Synchronisation … fehlgeschlagen“ | Ein YTM-Eintrag ohne `thumbnail.thumbnails` ließ die gesamte neue Antwort beim Deserialisieren scheitern; sichtbar blieb die lokale/alte Liste | Seit 1.0.1 wird die Thumbnail-Liste optional als leer behandelt. Das Banner darf nur bei echtem Browse-/Parserfehler kommen; Log-Tag `HomeArtists` prüfen (§4.5) |
| Interpreten sind nach Aktualisierung nicht mehr alphabetisch | Online- und lokal bereits sortierte Listen wurden erst danach aneinandergehängt | Seit 1.0.1 wird nach dem Merge global sortiert und dedupliziert. In der Oberfläche „Titel / aufsteigend“ wählen; `DATE_ADDED` und `RANDOM` sind bewusst nicht alphabetisch (§4.5) |
| Treffer aus „Videos“ zeigt nur ein Cover | YTM kennzeichnet ihn als `MUSIC_VIDEO_TYPE_ATV` (Art Track); das ist beabsichtigt kein Musikvideo | Kennzeichnung in der dritten Zeile prüfen: „Audio (Standbild)“ bleibt im Audioplayer. Nur `OMV`/`UGC` öffnet Video (§4.4) |
| Musikvideo kann nicht eingebettet werden | Video gelöscht, regional/alterstechnisch gesperrt, Einbettung vom Rechteinhaber untersagt oder IFrame/WebView-Fehler | KruXx fällt automatisch bei gleicher Position auf Audio zurück. Bei dauerhaftem Fehler Android System WebView und Netz prüfen |
| Browse/Charts leer, Wiedergabe geht | Problem in `me.knighthat.innertube` (Submodul), nicht InnerTubeX | `modules/innertube`, Innertube-Logs |
| „Failed to get charts“ beim Öffnen der Startseite (Log-Tag `HomeQuickPicks`) | YouTube hat die Charts-Antwort geändert; das Modul deserialisiert strikt (`MissingFieldException`) | Live-Antwort holen (`POST youtubei/v1/browse`, `browseId=FEmusic_charts`, `formData.selectedValues=["DE"]`), als Fixture nach `modules/innertube/src/test/resources/ytm/browse/`, `InnertubeChartsLiveResponseTest` nennt das fehlende Feld, Modell anpassen (§2.3) |
| App stürzt nur beim ersten Start nach einem Update mit `IndexOutOfBoundsException: Index 0 out of bounds for length 0` ab; beim zweiten Start erscheint ein CrashReport | Release-Notes wurden nicht in Abschnitte geparst, der Changelog-Pager hatte deshalb null Seiten (trat in 2.2.3-kruxx.1/.2 durch `•` statt `-` und eine Überschrift ohne `:` auf) | Seit 2.2.3-kruxx.3: robuster Parser plus Schutz vor leeren Abschnitten. Neue Notizen immer im Format `Überschrift:` und `- Eintrag` schreiben |
| App schließt direkt beim Start; Log meldet, der Media3-Player werde vom falschen Thread angesprochen | Der Flow des Benachrichtigungsschutzes lief im IO-Scope und las `player.isPlaying` außerhalb des Application-Loopers | Player-nahe Flows ausschließlich in `playerCoroutineScope`/`Dispatchers.Main.immediate`; `updatePlaybackNotificationSilencing()` bleibt `@MainThread` (§4.1) |
| Im Vollbild-Player fehlt das separate vollständige Cover und das Cover füllt nur den Hintergrund | `PLAYER_SHOW_THUMBNAIL` ist in diesem App-Paket gespeichert ausgeschaltet, etwa nach einem Doppeltipp auf den Player-Hintergrund. Debug und Release können deshalb verschieden aussehen; dies ist kein Beleg für eine abweichende APK | Unter Einstellungen → Darstellung „Cover im Player anzeigen“ einschalten oder den Hintergrund doppelt antippen. Frische App-Daten verwenden `true`; Updates bewahren die vorhandene Auswahl. Danach Player verkleinern und erneut öffnen (§4.1) |
| „Miniaturansicht“ ist aktiv, beim Verlassen erscheint aber kein kleines Fenster | Thumbnail im großen Player wurde mit Android-PiP verwechselt; PiP-Hauptschalter/Systemfreigabe aus, Auto-Unteroption aus oder kein aktueller Titel | Einstellungen → Darstellung → „Schwebenden Player erlauben“ plus „Beim Verlassen automatisch öffnen“ aktivieren; Androids PiP-Freigabe für KruXx und laufenden Titel prüfen (§4.6) |
| Fremde Benachrichtigung ertönt oder erscheint als Heads-up-Pop-up trotz aktiviertem Schutz | „Nicht stören“-Zugriff fehlt/wurde entzogen, Schalter oder Zugriff gelten nur für die andere Build-Variante, Wiedergabe ist pausiert oder der Ton ist Medien-, Wecker-, Anruf- bzw. Systemaudio und daher bewusst erlaubt | Systemzugriff und KruXx-Schalter für das tatsächlich laufende Paket prüfen; Debug und Release sind getrennte Apps. Danach Log/Status von `PlaybackNotificationSilencer` kontrollieren. Normale Einträge im Benachrichtigungsbereich bleiben sichtbar; der Schutz ist kein allgemeiner Audio-Mute (§4.1) |
| Bei „Alle Tracks downloaden“ werden nur drei Titel markiert oder der Gesamtdurchsatz ist trotz gutem Netz gering | Media3s Standard sind drei aktive Downloads; weitere Einträge waren korrekt `QUEUED`, aber `SongItem` zeigte diesen Zustand früher nicht. Liedtext-Nebenabrufe können zusätzlich konkurrieren. Nach dem InnerTubeX-Umbau fehlte außerdem die Gesamtlänge im `DataSpec`, weshalb der CDN offene statt exakt begrenzter Range-Abrufe erhielt | Queue-/Restart-Zustände sind sichtbar, die Bulk-Übergabe ist geordnet, bis zu fünf Audiodownloads laufen parallel und höchstens ein Nebenabruf. Der eigene Download-Resolver setzt nun High-Qualität und exakte Länge. Bei echtem Stillstand Download-Benachrichtigung und Logs `DownloadHelperImpl`, `MyDownloadService`, `dataspec`, `InnerTubeXPlayer` prüfen; CDN-Drosselung bleibt extern möglich |
| Wiedergabe wirkt mono | Quelle ist selbst mono/zentriert oder Android/Gerät mischt nach dem Decoder zusammen; KruXx enthält keinen Downmix. Ein gewählter Hall/EQ kann die Räumlichkeit verändern | „Stats for Nerds“ aufklappen: `Decoder-Kanäle: 2 (Stereo)` belegt ein Stereo-Quellformat. Dann Android → Bedienungshilfen → Audio → Mono-Audio, System-/Hersteller-EQ, Bluetooth-Gerät und Kabel/Adapter prüfen; Hall in KruXx auf „Keiner“ setzen |
| YouTube-Login/Bibliothek defekt | `modules/metrolist` (Metrolist-Innertube) | Submodul-Update (§6.3) |
| Build: `Dependency … requires compileSdk 37` | Flag in `gradle.properties` fehlt | §2.1 / §6.4 |
| Build: Lint-`e:`-Zeilen „expected version 2.2.0“ | Lint-Werkzeug kann Kotlin-Metadaten 2.4 noch nicht vollständig lesen | Nicht isoliert bewerten: vollständiges `lintKruxxUniversalProdRelease` ausführen. Nur wenn dieser Gate ohne **neue** Befunde und der Build erfolgreich endet, ist die bekannte Metadaten-Diagnose unkritisch |
| „Paket in Konflikt mit bestehendem Paket“ | gleiche App-ID, andere Signatur | KruXx-Flavor verwenden bzw. alte App deinstallieren |

### 7.3 Schnelltest nach Änderungen

1. `:innertube:test` und `:composeApp:testKruxxUniversalProdDebugUnitTest` ausführen; anschließend
   Debug-APK bauen und parallel zur Release-App installieren.
2. Frische **Debug**-Installation beziehungsweise gezielt gelöschte Debug-Daten: erster Start öffnet
   die Startseite ohne Absturz; zweiter Start zeigt keinen CrashReport-Dialog. Daten der installierten
   Release-App niemals für einen Test löschen.
3. Zusätzlich die bestehende Release-App kalt starten und `adb logcat` auf `AndroidRuntime`,
   „Player is accessed on the wrong thread“ und `PlayerServiceModern` prüfen. Wiedergabe einmal vor
   und einmal nach dem Neustart starten; der optionale Benachrichtigungsschutz darf den Dienststart
   auch ohne Policy-Freigabe nicht beenden.
4. Startseite: Unter „Vorschläge“ müssen nach dem Laden mehrere normal große Songzeilen erscheinen
   (sofern der Radio-Endpunkt Ergebnisse liefert), höchstens 18 und ohne doppelte Song-ID. Bei 1/2/3+
   Ergebnissen die Höhe von 1/2/3 Reihen und den verschwindenden Loader prüfen. Der Play-Button muss
   die vollständige Liste einreihen; ein einzelner Tipp startet weiterhin das Radio dieses Songs.
5. „Top Artists“ an Bild, Text und Rand antippen; jede Stelle muss dieselbe Interpretenseite öffnen.
   Danach einen Eintrag ohne Bild simulieren/verwenden und die Navigation erneut prüfen.
6. Vor der Anmeldung mindestens zwei rein lokale Listen anlegen. Danach anmelden und
   „Wiedergabelisten synchronisieren“ aktivieren: Der Reiter „Playlists“ muss ohne manuelles
   Aktualisieren nachladen; „From your Library“ nach dessen normalem Home-Refresh prüfen. Beide
   Ansichten müssen dieselben lokalen und YTM-/YT-Kontolisten ohne Dubletten enthalten; im
   Reiter zusätzlich „Wiedergabelisten“ (alle), „YT-Wiedergabelisten“ sowie – falls eingeblendet –
   Angepinnt/Monatlich prüfen. Eine Kontobibliothek mit mehr als einer
   Ergebnisseite verwenden, damit auch die Library-Continuation nachgewiesen ist. Mindestens fünf
   unterschiedliche Listen und eine mit Video-/Art-Track-Einträgen öffnen; erste Playlistseite und
   deren Continuation müssen laden, keine Konto-Liste darf als lokale leere Playlist geöffnet werden.
   Synchronisationsschalter aus: Der Home-Abschnitt behält lokale Inhalte, der Kontoanteil des Reiters
   verschwindet automatisch; die passenden Leerhinweise für Suche, Filter, abgemeldetes Konto,
   deaktivierten Sync und simulierten Abruffehler kontrollieren. Erneutes Aktivieren muss ohne Pull-
   to-refresh laden. Anonym darf eine private Konto-Liste nicht fälschlich als erfolgreich gelten.
7. Interpreten auf „Titel / aufsteigend“ stellen und Pull-to-refresh zweimal ausführen. Es darf kein
   rotes Fehlerbanner erscheinen; die zusammengeführte Liste bleibt über Online-/Lokal-Grenzen hinweg
   alphabetisch und ohne Dubletten. Danach absteigend, `DATE_ADDED`, `RANDOM` und die Filter
   Alle/YTM/Lokal jeweils auf ihr beabsichtigtes Verhalten prüfen.
8. Spracheingabe (Android 12+): Suche öffnen, Mikrofon antippen, Berechtigung erteilen und sprechen.
   Text muss im Feld stehen und darf erst nach manueller Bestätigung gesucht werden. Abbrechen,
   Reiterwechsel und „Berechtigung verweigern“ ebenfalls prüfen. Flugmodus-Test mit installiertem
   lokalem Sprachmodell bestätigt den Offline-Betrieb; ein Gerät ohne Modell muss verständlich
   ablehnen statt einen Cloud-Erkenner zu verwenden.
9. Suche anonym prüfen: App-Sprache/Region Deutschland, bekannter Song sowie ein Treffer, den YTM nur
   als Musikvideo führt. Letzterer muss unter „Titel“ **und** „Videos“ erscheinen; keine doppelte
   `videoId`. Vorschläge, Reiter, Titel-Fortsetzung und Kindersicherung mit explizitem Video testen.
10. Suche angemeldet wiederholen; anschließend nur in einer isolierten Debug-Testinstallation eine
    ungültige Sitzung herstellen und den anonymen Rückfall prüfen. Wechsel von Sprache/Region oder
    Login darf keine alten Suchergebnisse aus dem Cache zeigen. Konto-/Cookie-Werte nie loggen.
11. Im Reiter „Videos“ je einen `OMV`-, `UGC`- und `ATV`-Treffer prüfen: Typbeschriftung muss stimmen;
    nur OMV/UGC öffnet nach direktem Tipp das 16:9-Videofenster, ATV startet Audio. Einen echten
    Videotreffer über „Als Nächstes“ einreihen – beim automatischen Übergang muss er Audio bleiben.
    Im Video vorspulen, auf „Nur Audio“ wechseln und dieselbe Position kontrollieren. Ein nicht
    einbettbares Video muss ohne App-Absturz an derselben Stelle als Audio weiterlaufen.
12. In einem frischen Debug-Profil zuerst den Vollbild-Player prüfen: Das vollständige Cover muss
    als eigene Fläche vor dem coverbasierten unscharfen Hintergrund stehen. „Cover im Player anzeigen“
    aus- und wieder einschalten, alternativ den Hintergrund doppelt antippen; nach Verkleinern und
    erneutem Öffnen muss die letzte Auswahl erhalten bleiben. Daten der Release-App dafür nicht
    löschen. Danach PiP unter Einstellungen → Darstellung aktivieren. Mit Audio und echtem Video
    die App über Home/Geste verlassen: Cover beziehungsweise Video muss im kleinen Fenster erscheinen;
    Play/Pause und Schließen prüfen. Auto-Unteroption aus: kein selbstständiger Eintritt. Hauptschalter
    aus oder kein MediaItem: kein PiP. Wenn verfügbar je ein Gerät Android 7–11 und Android 12+
    verwenden und außerdem Androids app-spezifische PiP-Freigabe testen.
13. Branding prüfen: Launcher-Icon (normal/rund), Themed Icon, Benachrichtigungssymbol,
    Ersatzgrafik bei fehlendem Cover und Header „KruXx – The core of your music“; „KruXx“ muss
    sichtbar größer als der Zusatz sein, auf schmalem Display dürfen die rechten Header-Aktionen
    nicht abgeschnitten werden; auf Android TV zusätzlich das Banner.
14. Album/Playlist mit mindestens 10 Titeln: „Alle Tracks downloaden“. Sofort müssen alle fünf
    aktiven **und alle wartenden** Titel markiert sein; nach Abschluss alle offline abspielen. Einen
    wartenden und einen laufenden Titel per Tipp abbrechen; ein einzelner Fehler darf die restliche
    Queue nicht stoppen. Einen früher vollständig geladenen Testtitel zuerst entfernen, dann je
    einmal über WLAN und ein als getaktet erkanntes Mobilfunknetz laden: `adb logcat` muss jeweils
    `Download: … itag=… bitrate=…` mit derselben höchsten verfügbaren Variante zeigen. Für einen
    belastbaren Geschwindigkeitsvergleich dasselbe Album bei stabilem Netz und leerem Download-Cache
    vor/nach der Änderung messen; zusätzlich einen begonnenen Download durch App-Neustart fortsetzen.
15. Stereo-Testdatei beziehungsweise bekannten YTM-Stereotest: „Stats for Nerds“ aufklappen und
    `Decoder-Kanäle: 2 (Stereo)` prüfen; links/rechts getrennt über kabelgebundene Kopfhörer testen.
    Danach optional Mono-Audio in Android aktivieren, um den Unterschied eindeutig gegenzuprüfen.
16. Benachrichtigungsschutz aktivieren, Systemzugriff zunächst abbrechen (Schalter muss
    zurückspringen), dann erteilen und während Audio sowie eingebettetem Video eine normale
    Benachrichtigung aus einer anderen App auslösen: weder Heads-up-Pop-up noch Ton, der Eintrag muss
    aber im Benachrichtigungsbereich sichtbar bleiben. Pause/Stop müssen den vorherigen Zustand sofort
    herstellen. Zusätzlich Dienstende/Neustart sowie einen schon vorher manuell aktivierten
    „Nicht stören“-Modus prüfen; letzterer darf nicht überschrieben oder ausgeschaltet werden.
    Den Test getrennt für die tatsächlich verwendete Debug- oder Release-App autorisieren;
    gespeicherte Crashreports werden dafür nicht geöffnet.
17. Anonym (ausgeloggt): 3–4 Songs aus Suche/Charts abspielen, dazwischen seeken; einen explizit
    markierten Song direkt als ersten, noch nicht lokal bekannten Titel einschließen. Eingeloggt einen
    Song aus der eigenen Bibliothek spielen. Zwischen zwei nicht vollständig gepufferten Online-Titeln
    Audioqualität beziehungsweise Auto-Datensparen wechseln und prüfen, dass der folgende Abruf die neue
    Auswahl nutzt. Das Cache-Verhalten beim zweiten, offline gestarteten Abspielen separat prüfen. Ein
    nicht gepufferter Online-Titel muss innerhalb weniger Sekunden von Position null nach `PLAYING`
    wechseln; eine Verzögerung ungefähr in Titellänge weist auf Body-Logging im Medientransport hin.
18. `adb logcat` auf `Playback: client=…` prüfen – welcher Client liefert? VISIONOS ist typischerweise
    tokenfrei, WEB_REMIX verwendet den PO-Token-Pfad. Signierte URLs, Cookies und Tokens dürfen in
    Testnotizen nicht übernommen werden. Zusätzlich sicherstellen, dass der HTTP-Logger keine CDN-
    Medienanfrage ausgibt; funktionale Resolver-/Recovery-Meldungen müssen erhalten bleiben.
19. Android Auto zuerst im Desktop Head Unit, danach mit demselben Debug-Build im realen Fahrzeug
    prüfen: kalt verbinden, Root sowie Titel/Interpreten/Alben/alle Playlistarten öffnen und jeweils
    ersten, mittleren und letzten Titel wählen. Angezeigte Reihenfolge, gestarteter Titel,
    Vor/Zurück-Queue und Cover/Metadaten müssen übereinstimmen. Sprachbefehle für einen konkreten
    Titel sowie „Musik abspielen“ (leere Anfrage), Suchtext mit `/`, `%` und Umlaut, Play/Pause,
    Vor/Zurück, Favorit/Download/Shuffle/Repeat/Radio, erneutes Verbinden und Wiederaufnahme testen.
    Eine leere persistente Queue darf nicht abstürzen; der Handy-Suchbutton darf im Auto nicht
    erscheinen. Währenddessen `AndroidRuntime`, `MediaSession` und `PlayerServiceModern` beobachten,
    aber keine Sprachsuchtexte oder Kontodaten in Testnotizen übernehmen.
20. Den Self-Updater mit signierten Release-Kandidaten prüfen – Debug-Builds haben ihn absichtlich
    deaktiviert. Vorherigen Release frisch installieren und den Standard „Nachfragen“ unverändert
    lassen: einmal mit vorhandenem Netz kalt starten, einmal offline starten und erst bei sichtbarer
    Oberfläche verbinden. Der neue Release muss in beiden Fällen ohne „Jetzt überprüfen“ erscheinen.
    Danach einen vorübergehenden Netzfehler/HTTP-5xx und wiederholte Vordergrundwechsel prüfen: keine
    doppelten Dialoge, nach erfolgreichem Abruf keine zweite automatische Prüfung innerhalb von 24
    Stunden. „Jetzt überprüfen“ muss das Intervall umgehen und bei fehlendem Netz verständlich
    reagieren; Download, Digest-/APK-Prüfung und Android-Installer anschließend vollständig testen.

### 7.4 InnerTubeX ohne Handy testen (JVM)

InnerTubeX hat ein JVM-Target; die Extraktion lässt sich in einem Mini-Gradle-Projekt (Kotlin JVM,
Repos `mavenCentral()`, `google()`, `maven("https://jitpack.io")`) direkt gegen YouTube prüfen:

```kotlin
val http = HttpClient(OkHttp) {
    expectSuccess = false
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }) }
    install(HttpTimeout) { requestTimeoutMillis = 30_000 }
}
val innerTube = InnerTube(http)
val store = RemotePlayerConfigStore(http, PlayerConfigRepository.disabled())
val cipher = YouTubeCipherService(http, store)
val extractor = InnerTubeExtractor(YtConfigParserImpl(http, innerTube, store), cipher, innerTube)
val stream = extractor.extract("dQw4w9WgXcQ", ContentHints().withStreamCapabilities(allowHls = false, allowSabr = false))
println("${stream?.clientName} ${stream?.itag} ${stream?.mimeType}")   // dann Range-GET auf stream.audioUrl → 206 erwartet
```
Dependencies: `innertubex`, `ktor-client-okhttp`, `ktor-client-content-negotiation`,
`ktor-serialization-kotlinx-json` (Ktor-Version wie in InnerTubeX). Ohne TokenProvider liefert
InnerTubeX tokenfreie Clients (VISIONOS); PO-Token-Pfade lassen sich nur in der App testen.

---

### Nacharbeit an Suchansicht und Dialogen für 1.2.0

Die Online-Suchtreffer (Titel und Videos), Song-Vorschläge und Bibliothekssuche tragen die
bestehende `kruxxTrackCard`-Fläche. Die Bibliothekssuche erzeugt ihren Datenbank-Flow mit
`remember(textFieldValue.text)` erneut, wenn der Text geändert wird; ohne den Schlüssel blieb
die erste Abfrage aktiv. Der Samsung-Debugtest wechselt im selben Bibliotheksreiter erfolgreich
zwischen „Maddix“ (drei Treffer) und „Noma“ (ein Treffer).

`ThemedAlertDialog` setzt `KruxxDialogBackdrop` innerhalb des Dialogfensters ein. Auf dem Samsung
ist der systemweite Fenster-Blur nicht unterstützt; der lokale `RenderEffect` der Activity
verwischt trotzdem die darunterliegende Liste. Abbrechen, Zurück und erneutes Öffnen sind mit der
Debug-App geprüft. Der signierte Nachtest vom 07.09.2026 bestätigt diese Abläufe ebenfalls;
im selben Bibliotheksreiter wechseln die Ergebnisse zwischen „Maddix“ und „Whitechapel“ korrekt.
Die [signierte Geräteprüfung](KRUXX-IST-STAND.md#signierte-geräteprüfung-vom-07092026)
belegt zusätzlich Update, optimierte API-24-Ausführung, Wiedergabe und MP3-Export auf beiden Geräten.

Bei der damaligen API-23-Abnahme war Debug-Erfolg allein kein ausreichender Nachweis.
Die Android-6-x86-Testlaufzeit ließ in der maschinenoptimierten Release-Ausführung bereits eine
isolierte `MutableFloatState`-Zuweisung von `NaN` auf `0f` wirkungslos; daraus folgte der
`AnchoredDraggableState.requireOffset`-Absturz. Derselbe APK-Inhalt funktioniert im Interpreter.
Ein solcher Diagnoselauf ersetzt die Abnahme unter unveränderten Laufzeiteinstellungen nicht.
Details und der nachfolgende Gerätevergleich stehen im IST-Stand; keine der wirkungslosen
Wisch-/Float-Keep-Regeln und keine Diagnoseausgabe gehören zum ausgelieferten App-Code.
Seit dem Supportende für Android 6 wird dieser Fehler nicht mehr für KruXx behoben. Der
Unterschied zwischen Debug- und Release-Nachweis bleibt für die unterstützten Versionen relevant;
Suche, Dialoge, Wiedergabe und Downloads sind jetzt ab API 24 gemeinsam praktisch abzuprüfen.

## 8. Release-Checkliste

- [ ] Branch `main`; `origin` ist `Massefehler/KruXx`, `upstream` hat Push-URL `DISABLED`. Vor dem
      finalen Build ist `git status` sauber und jedes Submodul auf den beabsichtigten Commit gepinnt
- [ ] `KRUXX_VERSION_NAME` in `composeApp/build.gradle.kts` nach SemVer erhöhen und
      `KRUXX_VERSION_CODE` **bei jedem APK-Release** um mindestens 1 erhöhen (nie wiederverwenden/senken)
- [ ] `docs/changelogs/kruxx/<versionName>.txt` schreiben: jeder Abschnitt beginnt mit
      `Überschrift:`, jeder Eintrag mit `- `; Kreates eigene Notizen unter
      `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` bleiben unangetastet
- [ ] `docs/KRUXX-IST-STAND.md`, Entwicklerhandbuch, Root-README und gegebenenfalls `NOTICE.md`
      aktualisieren; historische Changelogs nicht umschreiben
- [ ] `cmp docs/changelogs/kruxx/<versionName>.txt composeApp/src/androidKruxx/res/raw/release_notes.txt`
      ist erfolgreich; Changelog-Parser-Test deckt alle verwendeten Abschnitte ab
- [ ] Geheimnis-/Datenschutzprüfung in **App- und Innertube-Repository**: kein Crashlog, Cookie,
      Token, Keystore, Passwort oder privater API-Schlüssel; offene GitHub-Scanning-Alarme nach §3.2
      klassifiziert und bei gültigen Geheimnissen zuerst rotiert
- [ ] Finalen Quellstand auf `main` committen; anschließend aus exakt diesem sauberen Commit
      `./gradlew :innertube:test :metrolistInnertube:test :composeApp:testKruxxUniversalProdDebugUnitTest`
      sowie `./gradlew :composeApp:lintKruxxUniversalProdRelease` ausführen. Die Lint-Baseline darf
      nur geerbte Altbefunde enthalten; neue eigene Befunde werden behoben, nicht aufgenommen
- [ ] Vollständigen Debug-/Geräte-Schnelltest nach §7.3 abschließen. Für den nächsten Release
      insbesondere Android Auto in DHU und realem Fahrzeug sowie Konto-Playlists,
      Einzel-/Massendownload, Video/Audio-Rückfall, PiP, Benachrichtigungsschutz und
      Links-/Rechts-Stereo praktisch prüfen
- [ ] `scripts/build-local-release.sh kruxx` → `>> done:` und
      „Signer #1 certificate DN: CN=Kreate local build …“
- [ ] `aapt2 dump badging …/KruXx-release-signed.apk` → erwartete Paket-ID, exakter
      `versionCode` und `versionName`, `minSdkVersion:'24'` für Release und Debug;
      `apksigner verify --print-certs` → erwarteter Fingerabdruck
- [ ] Optimierte Release-Ausführung auf Android 7 / API 24 und dem aktuellen Zielgerät prüfen:
      Suche, Dialoge, Wiedergabe und Downloads einschließlich ihrer Wechselwirkungen. API 23 ist
      seit `1.2.0` nicht mehr Teil des Support- oder Freigabeumfangs
- [ ] Im Archiv `/home/kruxx/Schreibtisch/Android/Kreate-APKs/` prüfen, dass das Build-Skript Release und
      passende Debug-APK als `KruXx-<versionName>-release.apk` / `-debug.apk` abgelegt hat (LocalSend)
- [ ] Annotiertes Tag `v<versionName>` auf exakt dem gebauten Commit; Push ausschließlich zu `origin`.
      AGP bettet die Git-Revision in das APK ein, daher wird kein selbstbezüglicher Vorab-Hash in
      diesen Commit geschrieben
- [ ] GitHub-Release mit exakt dem archivierten APK-Namen erstellen; Größe und SHA-256 aus dem finalen
      Build in der Release-Beschreibung festhalten und die API anschließend auf Tag, Assetname, Größe
      und `sha256:`-Digest prüfen. Keine ältere Datei mit demselben Versionsnamen hochladen
- [ ] Upgrade über die vorherige installierte KruXx-Version testen: Im Standardmodus „Nachfragen“
      muss ein Kaltstart mit Netz sowie ein Offline-Start mit anschließendem Verbinden den neuen
      Release ohne manuelle Aktion erkennen und genau einen Update-Dialog anzeigen. Vordergrundwechsel
      und ein vorübergehender Fehler dürfen die Prüfung weder dauerhaft verlieren noch Dialoge
      verdoppeln. Zusätzlich muss Einstellungen → Allgemein → „Jetzt überprüfen“ das Intervall
      umgehen, das neue Release finden und nach der Prüfung Androids Installer öffnen (§7.3 Nr. 20)

---

## 9. Offene Punkte, Ideen und Release-Nachweise

Die [Roadmap](../ROADMAP.md) bündelt die künftigen Vorhaben ohne feste Versions- oder
Terminzusage. Dort ist seit 08.09.2026 auch die optionale Friends-Funktion vorgemerkt:
zuerst Musikteilen verbessern, später persönliche Nachrichten und Musikkarten. Vor einer
Messenger-Integration steht ein begrenzter technischer Prototyp; SimpleX bleibt ein Kandidat.

- **1.2.0 – verbleibende Download-Gerätefälle:** Die Matrix in [`DOWNLOADS.md`](DOWNLOADS.md)
  umfasst Video mit Ton, Android-MP3-Konvertierung, alle Download-Einstiege, SD/USB, Abbruch,
  Neustart und Dateiintegrität. Signierte MP3-Exporte und Offline-Neustarts auf API 24 und 36 sind
  belegt; insbesondere physische SD-/USB-Anbieter und die übrigen unbelegten Kombinationen bleiben offen.

- **Nachtest zu 1.0.2 – Android Auto praktisch abnehmen:** Browse-/Queue-/Such-/Resumption-Logik ist
  im Arbeitsstand vom 04.09.2026 überarbeitet, durch sechs gezielte ID-/Paging-Tests abgesichert und
  zusammen mit allen 88 KruXx-App-Tests sowie dem Debug-APK erfolgreich gebaut. Offen bleibt die
  vollständige Matrix aus §7.3 im Desktop Head Unit und danach in einem realen Fahrzeug. Erst wenn
  Auswahlposition, Vor/Zurück, Sprachsuche, Custom Controls, Wiederverbinden und leere persistente
  Queue dort fehlerfrei sind, darf die Steuerung als freigegeben gelten.
- **Self-Updater – geprüfter Weg von 1.1.0 zu 1.2.0 und verbleibende Grenzen:** Ein früherer Praxistest auf einem
  zweiten Gerät zeigte nach frischer Installation von `1.0.0` keinen automatischen Hinweis auf das bereits
  veröffentlichte `1.0.1`. Seit 1.0.2 ist der Pfad foreground-/netzgebunden,
  reagiert auf Netzrückkehr und neue Vordergrundstarts, wiederholt transiente Fehler zweimal,
  dedupliziert Job/Dialog und setzt das 24-Stunden-Intervall erst nach erfolgreicher
  Releasevalidierung. Vier Policy-Tests sichern Intervallgrenze, korrigierte Systemzeit, Retrybudget
  und HTTP-Klassifikation. Der Nachtest `1.1.0` → `1.2.0` am 07.09.2026 belegt auf API 24
  Offline→Online-Erkennung, manuellen Intervall-Bypass, Offline-Fehlermeldung und Dialog-Deduplizierung.
  Dabei lehnt Androids System-Downloadmanager die TLS-Zertifikatskette des APK-Downloads ab und
  wartet auf Wiederholung. Der manuelle APK-Upgrade-Test auf API 24 ist bestanden; bei diesem
  Fehler das APK auf einem aktuellen Rechner herunterladen und übertragen, ohne App-Daten zu löschen.
  Auf einem frischen API-36-Emulator ist auch der Online-Kaltstart mit automatischem Hinweis und
  der vollständige Download-/Prüf-/Installerpfad bestanden; Playlist und Erstinstallationszeit bleiben
  erhalten, die installierte APK ist bytegleich mit dem öffentlichen Asset. Der IST-Stand dokumentiert
  Einzelversuche und Grenzen; HTTP-5xx-Injektion und die volle 24-Stunden-Grenze bleiben Nachtests.
- **Nachtest zu 1.0.2 – Wiedergabe-Härtung breiter abnehmen:** Die technische Umsetzung ist abgeschlossen:
  HTTP 403/410/416 umgehen die Wiederholung derselben signierten URL, der Explicit-Hint steht schon
  beim ersten Auflösen bereit und Änderungen an Qualität/Datensparen invalidieren den Playback-URL-
  Cache. Der Medientransport entfernt HTTP-Logger, ohne funktionale Interzeptoren zu verlieren. Neun
  neue Tests decken HTTP-Policy, verschachtelte Statuscodes, Fehlermapping, Offline-/Timeout-
  Klassifikation, Ablaufgrenze, MediaItem-Hint und Client-Trennung ab. Der zuvor bei jedem Track
  beobachtete Stillstand bei Position null ist auf einem Samsung SM-S931B mit Android 16 reproduziert
  und behoben; ein nicht gepufferter Online-Titel startete im finalen Debug-Build nach 1,713 Sekunden.
  Offen bleibt die breitere Geräte-Matrix aus §7.3 Nr. 17–18.
- **Nachtest zu 1.0.2 – Playlist-Randfälle abnehmen:** Der gemeldete Kernfehler ist auf dem Zielgerät behoben.
  Zehn Merge-Tests sichern unter anderem VL-/Nicht-VL-Dubletten, lokale Metadatenpriorität, reine
  Cloud-/Local-Fälle, lokalisierte Songzahlen und Sortierung. Als Nachtests bleiben Filter,
  Login-/Sync-Wechsel und ein fehlgeschlagener Refresh mit Erhalt der letzten erfolgreichen
  Kontoantwort praktisch zu prüfen.
- **Nach 1.0.2 – eigenständiger Podcast-Bereich statt `RDPN`-Einzelfix:** KruXx besitzt bereits
  YTM-Suchfilter, eine einfache Podcast-Detailseite und abspielbare Episode-MediaItems; ihm fehlen
  aber ein eigener Bibliotheksreiter, Abonnements, Episodenfortschritt/-status und eine zuverlässige
  Aktualisierung. Der Ausbau soll sich funktional an
  [Tsacdop](https://github.com/tsacdop/tsacdop) orientieren: neue/gespeicherte/heruntergeladene
  Folgen, Fortsetzen, Abonnements und optional automatische Downloads/Aufräumen. Schlaf-Timer,
  Wiedergabegeschwindigkeit und Stille-Überspringen existieren im KruXx-Player bereits und sollen
  wiederverwendet werden. Tsacdops Flutter/Dart-Code ist keine direkte Abhängigkeit; Designideen oder
  portierte Logik müssen nachvollziehbar attribuiert werden. Vor Umsetzung ist festzulegen, ob der
  erste Schritt nur YTM oder zusätzlich RSS/PodcastIndex und OPML umfasst.
- **1.1.0 „Glass Update“ – visuelle Eigenständigkeit durch [„KruXx Glass“](Design.md):** Die zwei
  veröffentlichten Umsetzungsstufen lassen Navigation, Screen-Architektur, Datenfluss, ViewModels,
  Player-/Bibliothekslogik und Android Auto funktional unverändert. `KruxxGlass` bündelt die
  KruXx-Markenfarben, Formen und Compose-Modifier; aktiviert wird die Schicht nur bei
  `BuildConfig.INDEPENDENT_FORK`. Graphit-Hintergrund mit rot-blauer Lichtaura, Header, untere
  Navigation, Mini-Player und leichte Startseitenkarten verwenden die gemeinsamen Bausteine. Die
  Kaltstartanimation erhält denselben Hintergrund über die explizite, außerhalb von
  `LocalAppearance` sichere `kruxxAppBackground`-Variante. Die zehn Logo-Quellen und ihre
  600²-WebP-Runtime-Frames besitzen echte Transparenz. Der erste Frame steht sofort; jeder weitere
  Wechsel löst die Bilder über die vollständigen 120 ms linear ineinander auf und wird von einer
  ruhigen Skalierungs- und Leuchtbewegung begleitet. In der zweiten Etappe kamen
  Vollbild-Player-Steuerung und -Aktionsleiste, Einstellungszeilen und klebende
  Überschriften, die zentralen Dialog-/Menü-/Bottom-Sheet-Systeme sowie transparente gemeinsame
  Screen-Gerüste für geöffnete Rubriken hinzu. Die Queue entfernt im KruXx-Build ihre getrennten
  internen Listen-, Such- und Fußleistenhintergründe und verwendet damit die gemeinsame
  Bottom-Sheet-Glasfläche. Deren opt-in `glassSurfaceOpaque` legt eine deckende Basis unter den
  Glasverlauf, damit der Vollbild-Player nicht durch die Queue hindurchscheint.
  Das Drei-Punkte-Trackmenü des Vollbild-Players verwendet stattdessen den bewusst kleinen Token
  `KruxxGlass.playerMenuBackdropAlpha` (`0.08f`). `PlayerMenu` reicht ihn über beide Menüvarianten
  bis `Menu` beziehungsweise `GridMenu` durch, sodass auch die Playlist-Unteransicht konsistent
  bleibt, ohne die Deckung anderer Menüs global anzuheben.
  `TabToolBar`,
  `Search` und `ButtonsRow` liefern den vier unteren
  Bibliotheks-Reitern eine einheitliche gläserne Werkzeugleiste und Suche sowie transparente
  Filterchips; Titel-, Interpreten-, Alben- und Playlist-Inhalte verwenden leichte Glaskarten.
  `HomeScreen` verwendet dafür die eigene Navigationsressource `tab_playlists`: Im Deutschen heißt
  nur der Hauptreiter platzsparend „Playlists“, während Inhaltsüberschriften und Filter weiterhin
  „Wiedergabelisten“ verwenden. Das verhindert abgeschnittene Randreiter und reserviert Breite für
  den geplanten Podcast-Reiter. Die vorhandene horizontale `HorizontalNavigationBar` bleibt ohne
  Zusatzhinweise, solange alle Reiter passen. Erst wenn der gemessene Scrollbereich einen Überlauf
  ausweist, zeigt sie seit 1.2.0 abhängig von aktueller Position und Scrollgrenzen `<<` beziehungsweise
  `>>` an: nur links bei verborgenem Inhalt links, nur rechts bei verborgenem Inhalt rechts und
  beide zwischen den Grenzen. Die Logik darf nicht an eine feste Reiterzahl gekoppelt werden.
  `SongItem` reicht seinen Zeilen-Modifier nicht mehr an den Vorschlags-Downloadbutton durch.
  Vorschläge und „Top Artists“ verwenden im Hochformat
  rund 65 Prozent der Inhaltsbreite und entsprechen damit „Forgotten favorites“. Die Höhe der
  zweizeiligen „Top Artists“-Darstellung wird aus dem dort tatsächlich verwendeten
  `SongItem.thumbnailSize()` plus Innen- und Zeilenabstand berechnet. Das Vollbild-Player-Sheet
  bleibt als einzige dieser gemeinsamen Flächen undurchsichtig, damit die darunterliegende
  Startseiten-Kopfzeile nicht hinter seinen Bedienelementen durchscheint. `SettingHeader` versorgt
  alle Einstellungsreiter und ihre Untermenüs zentral mit vollständig gerundeten 14-dp-Köpfen;
  16 dp horizontaler sowie symmetrischer vertikaler Innenabstand zentrieren Überschrift und
  optionalen Untertitel und richten sie an den Eintragstexten aus. Diese Geometrie bleibt über
  `BuildConfig.INDEPENDENT_FORK` auf KruXx begrenzt. KruXx 1.1.0 setzt
  versionsübergreifend auf den performanten Transparenz-/Verlauf-/Kontur-Fallback und vermeidet Blur
  pro Listenelement; der Vollbild-Player verwendet zusätzlich seine vorhandene echte
  Cover-Unschärfe. Frische App-Daten zeigen dabei das vollständige Cover als eigene Vordergrundfläche;
  Updates erhalten eine zuvor abweichend gespeicherte Auswahl. Nach der Veröffentlichung wurde ein
  scheinbar fehlendes separates Cover in der stabilen App auf genau diesen gespeicherten Zustand
  zurückgeführt, wieder eingeschaltet und nach Verkleinern sowie erneutem Öffnen bestätigt. Der
  signierte GitHub-Build und der Quellstandard wichen nicht voneinander ab. Die KruXx-Debug-APK und
  die gemeinsame Kreate-/GitHub-Variante bauen erfolgreich; die KruXx-Debug-Suite bestand 88 von 88
  Unit-Tests. Auf dem Samsung-Zielgerät SM-S931B mit Android 16 bestanden Installation, Start,
  Tabwechsel, beide
  Playlist-Reiter mit jeweils 11 sichtbaren lokalen beziehungsweise YT-/YTM-Listen sowie
  Online-Wiedergabe und Pause/Fortsetzen über Mini- und Vollbild-Player ohne Crash. Vollbild-Player,
  Einstellungsseite und -Unterseite, Auswahldialog, Kopfzeilen-Dropdown, geöffnete Albumseite und
  Titel-Bottom-Sheet bestanden den aktuellen Gerätecheck. Ein kurzer
  Startseiten-Scrolltest ergab 5 von 675 als ruckelig bewertete Frames (0,74 %), ein 95. Perzentil
  von 14 ms und keine verpassten VSync-Ereignisse. Die gemeinsame Sichtabnahme auf dem
  Samsung-Zielgerät ist erfolgt. Als erweiterte Nachtests nach der Veröffentlichung bleiben
  die breitere Darstellungsmatrix für 1.2.0 mit Hellmodus, großer Systemschrift und API 24 bis 30
  sowie weitere Performanceprüfungen offen. Dialog-Backdrop-Blur ab API 31 und der API-24-Fallback
  sind inzwischen mit der signierten 1.2.0 geprüft (siehe §4.8 und IST-Stand); weitere ruhende
  Oberflächen bleiben mögliche Designnacharbeiten. Die zu großen
  Vorschlagskarten, die überhöhten und überlappenden „Top Artists“-Zeilen sowie das Durchscheinen
  der Startseiten-Kopfzeile hinter dem Vollbild-Player wurden auf dem Gerät reproduziert. Die
  Korrekturen kompilieren und sind automatisiert geprüft; „Top Artists“ und die Abgrenzung des
  Vollbild-Players wurden mit der neu gebauten APK direkt nachgeprüft. Im abschließenden Sichttest
  wurde auch die korrigierte Breite der Vorschlagskarten bestätigt. Queue-Sheet einschließlich Suche,
  Mini-Player und Werkzeugleiste sowie alle vier unteren Bibliotheks-Reiter wurden auf dem Samsung
  SM-S931B direkt geprüft. Elf YT-/YTM-Playlists waren sichtbar; Wiedergabe sowie Öffnen,
  Durchsuchen und Schließen der Queue funktionierten ohne Absturz oder ANR. Nach der Korrektur war
  der darunterliegende Vollbild-Player in der geöffneten Queue nicht mehr sichtbar.
  Die Hintergrundintegration der Startanimation bestand mit der zuvor installierten Debug-APK
  mehrere echte Kaltstarts. Die am 04.09.2026 ersetzten, echt freigestellten Frames
  und das neue App-Icon sind als Quell- und Runtime-Ressourcen auf Alpha, Abmessungen und Reihenfolge
  geprüft; die APK baut erfolgreich und enthält alle zehn transparenten WebPs. Nach
  wiederhergestellter ADB-Verbindung wurde die aktuelle Debug-APK erneut installiert und geöffnet;
  der sichtbare Gesamtstand einschließlich der leicht erhöhten Deckung des Drei-Punkte-Trackmenüs
  wurde auf dem Zielgerät bestätigt.
  Der als `1.1.0`/`1000003` veröffentlichte Stand bestand 88 App- und 58 Innertube-Tests
  ohne Fehler oder übersprungene Tests; Metrolist meldete erwartungsgemäß `NO-SOURCE`. Der
  vollständige Release-Lint meldete nach Korrektur der neuen Ressourcen keine neuen Befunde.
  Debug- und Release-Variante wurden gebaut, auf Paketversion und ZIP-Integrität geprüft, und die
  englischen Release Notes sind in Changelog, generierter Ressource und Release-APK bytegleich. Das
  signierte Archiv entstand aus Release-Commit `39398cc4684c91a7737da36af85f34232606f52d`;
  Paket, Version, Zertifikat, v1/v2/v3 und eingebettete Quellrevision wurden geprüft. Der
  `1.1.0`-Debug-Build wurde mit erhaltenen App-Daten auf dem
  Samsung-Zielgerät installiert und startete kalt ohne Fatal-/ANR-Treffer im prozessgefilterten Log.
  Danach bestand das exakt archivierte signierte APK auch das Upgrade der stabilen App von 1.0.2 auf
  1.1.0 mit erhaltenem ursprünglichem Installationszeitpunkt und einem Kaltstart in 489 ms. Der
  öffentliche GitHub-Asset ist bytegleich: 22.883.301 Bytes und SHA-256
  `8c1f57bf4bb2f4e41b12fc0ceb024594a1784ef0843ef8b6831ccbdbd13aa741`.
  Bestehende Theme-, Farb-, Schrift- und Rundungsoptionen bleiben verbindliche Freigabekriterien.
- **1.0.1-Freigabegate bestanden:** Das aus dem sauberen Release-Commit archivierte APK wurde über
  die bestehende Installation installiert; Paket, Version und erhaltener Datenstand stimmten. Ein
  per ADB bestätigter Kaltstart zeigte die zehn wechselnden Animationsframes, den Übergang zur
  Startseite und keinen `AndroidRuntime`-/Crash-Eintrag. Der breite manuelle Nutzertest
  einschließlich schneller Downloads sowie Konto-Playlist-, App-Start- und Interpretenprüfungen
  war ebenfalls ohne Auffälligkeit.
- Release und Debug werden im selben normalen Lauf des gehärteten Build-Skripts aus demselben
  Quellbaum erzeugt. Signatur, Paket, Version und eingebettete Git-Revision der Release-APK werden
  automatisch geprüft; die Debug-APK enthält keinen entsprechenden Git-Revisionsnachweis.
  Der erst danach feststehende finale Release-APK-Hash wurde im GitHub-Release und über dessen API
  verifiziert.
- Die GitHub-API meldete am 03.09.2026 nach der Veröffentlichung in allen drei Spiegeln null offene
  Secret-Scanning-Alarme. Im übernommenen Metrolist-Verlauf
  wurden sechs bewusst öffentliche YouTube-Clientschlüssel als erforderlich klassifiziert; ein
  GitHub-App-Installationstoken aus einem alten, im aktuellen Baum gelöschten Upstream-Core-Dump war
  automatisch abgelaufen. Lokaler Commit-/Pfadscan fand daneben nur den absichtlich synthetischen
  Auth-Wert eines Unit-Tests; weder Schlüsselwerte noch der gespeicherte Crashreport wurden geöffnet.
- Monochrom-Glyph (Themed Icons, Benachrichtigung) ist derzeit ein Text-„K“; ein flaches
  Weiß-auf-transparent-Motiv würde besser zum Icon passen (`--mono alpha` mit eigener Datei ergänzen).
- AGP 9 / Kotlin 2.4.10 nachziehen (Dependabot-Branches upstream), danach `disableCompileSdkChecks` prüfen.
- Upstream-Branch `preferences` beobachten: wenn der „backend overhaul“ in `main` landet,
  InnerTubeX-Anbindung in die neue Modulstruktur (`extensions/player`) übernehmen.
- Chunking-DataSource zwischen Resolver und OkHttp, falls Bounded-Range-Clients (ANDROID_VR/IOS/TVHTML5_SIMPLY)
  als zusätzliche Reserve gebraucht werden (§4).
