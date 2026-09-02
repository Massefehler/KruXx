# KruXx – Entwicklerhandbuch (Wiedereinstieg, Weiterentwicklung, Bugfixing)

Stand: 02.09.2026 · KruXx `1.0.0` · historische Basis: Kreate `main` @ `f02577e8` (v2.2.3)

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
  committet werden. Das gilt ebenso für manuell exportierte Crashlogs wie `docs/crash-report.txt`.

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
| `modules/innertube` | Submodul: `me.knighthat.innertube` (öffentlicher KruXx-Spiegel mit lokalem Charts-Fix) – Browse, Charts und Song-Metadaten; **nicht** die sichtbare Suchergebnisseite |
| `modules/metrolist/innertube` | Submodul: Metrolists altes InnerTube-Modul (knighthat-Fork, Branch `kreate-old`) – YouTube-Login, Bibliothek/Alben/Künstler, `YouTube`-Session |
| `modules/kizzy` | Submodul: Discord-RPC |
| `scripts/strings-modifier` | Submodul: Dev-Skript, nicht Teil des Builds |
| `extensions/` | Kreates eigene Module; `extensions/innertube` („oldtube“, Gradle-Modul `:oldtube`) liefert die sichtbare YTM-Suche und Suchvorschläge, daneben kugou/lrclib/discord |
| `icons/KruXx_App_Icon.png` | Quellbild des KruXx-Icons (1254², rotes „K“ mit blauer Audiowelle auf schwarzer Kachel) |
| `scripts/build-local-release.sh` | Release bauen **und** signieren |
| `scripts/make-kruxx-icon.py` | Alle Icon-Ressourcen aus dem Quellbild erzeugen |
| `patches/innertube/` | Sicherung und nachvollziehbarer Diff der KruXx-Änderung im Submodul (§2.3) |
| `docs/changelogs/kruxx/<versionName>.txt` | Release-Notes je KruXx-Version; landen als `release_notes` im Flavor `kruxx` (Changelog-Dialog nach dem Update). Format: Abschnitt als `Überschrift:`, Einträge als `- Text` |
| `docs/KRUXX-ENTWICKLERHANDBUCH.md` | diese Datei |

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
| `app/kreate/di/InnertubeResolvingDataSource.kt` | neu geschrieben: Cache mit Ablauf, CDN-Header, Fehler-Mapping – **ohne** Bounded-Range-Chunking (§4); merkt sich das itag der gecachten Bytes in den Cache-Metadaten (`kruxx_itag`) und verwirft den Cache-Eintrag bei Formatwechsel (`CachedFormatMismatchException`, §4) |
| `app/kreate/di/PlayerModule.kt` | CDN-User-Agent als Default-Header (`setDefaultRequestProperties`) statt `setUserAgent()`; die InnerTubeX-Header je Client (User-Agent/Referer/Origin) überschreiben ihn – `setUserAgent()` hängt media3 per `addHeader` zusätzlich an, es gingen zwei User-Agent-Zeilen raus |
| `app/kreate/android/service/player/ExoPlayerListener.kt` | `tryRecoverPlaybackError()` – bei HTTP 403/410/416 URL verwerfen und neu auflösen; bei Parser-/Cache-Fehlern (`PARSING_CONTAINER_*`, `READ_POSITION_OUT_OF_RANGE`, `FILE_NOT_FOUND`, `CachedFormatMismatchException`) den Cache-Eintrag des Songs löschen; danach `prepare()`+`seekTo()` an gleicher Position (max. 3×/Song, Budget wird bei Songwechsel zurückgesetzt) |
| `com/metrolist/music/utils/potoken/*` | auf Metrolists aktuellen Stand gebracht (Renderer-Tod, Timeouts), Timber → Kermit, OkHttp via Koin |
| `it/fast4x/rimusic/MainApplication.kt` | `InnerTubeXPlayer.initialize()` + Prewarm im Hintergrund |
| gelöscht | `YTPlayerUtils.kt`, `cipher/*`, `assets/solver/*`, `player_configs.json`, `player_dates.json`, totes `it/fast4x/rimusic/extensions/webpotoken/*` |

### 2.2 KruXx-Flavor (Commit „kruxx: …“)

| Datei | Änderung |
|---|---|
| `composeApp/build.gradle.kts` | Flavor `kruxx` (Dimension `platform`): `applicationId = "de.kruxx.music"`, `APP_NAME = "KruXx"`; Label über `androidComponents.onVariants` gepinnt (Build-Type-/Env-Placeholder würden sonst gewinnen); APK-Name `KruXx-*.apk`; `BuildConfig.REPO_NAME`; Flavor-Schalter deaktivieren den Upstream-CrashReport-Dialog, wählen die Startseite als Standard, aktivieren die lokale Spracheingabe und setzen Größe/Breite der Header-Wortmarke |
| `composeApp/build.gradle.kts` (Version) | **Eigene KruXx-Version**: `KRUXX_VERSION_NAME` folgt `MAJOR.MINOR.PATCH`; `KRUXX_VERSION_CODE` steigt bei jedem veröffentlichten APK strikt an. Startwert `1_000_000` liegt über allen verteilten `2.2.3-kruxx.x`-Builds. `copyKruxxReleaseNote` kopiert `docs/changelogs/kruxx/<versionName>.txt` ins APK und bricht bei fehlender Notiz ab |
| `me/knighthat/utils/Repository.kt` | Repository-Links werden je Flavor aus `BuildConfig.REPO_OWNER`/`REPO_NAME` gebildet; KruXx verweist nur auf `Massefehler/KruXx` |
| `composeApp/src/androidKruxx/kotlin/…/updater` | Eigener Updater: stabile GitHub-Releases, SemVer-Vergleich, exakter Assetname, HTTPS-/Repo-Bindung, SHA-256-, Paket-, Versions- und Signaturprüfung; höchstens eine automatische Prüfung pro 24 Stunden |
| `composeApp/src/androidKruxx/res/…` | generierte Icon-Ressourcen (siehe §5) |
| `Preferences.kt`, `MainApplication.kt` | KruXx-Standardseite ist `HomeScreenTabs.QuickPics` („Startseite“). Eine einmalige Migration setzt auch den bisher gespeicherten Standard `Songs` („Titel“) um; spätere manuelle Änderungen bleiben erhalten |
| `CrashReportDialog.kt`, `AppNavigation.kt` | Für KruXx wird der CrashReport-Dialog samt Link zum Kreate-Issue-Tracker nicht aktiviert. Der `CrashHandler` schreibt weiterhin ausschließlich ein lokales Log für die eigene Diagnose |
| `ChangelogsDialog.kt`, `ChangelogParser.kt` | Parser akzeptiert Kreates Format sowie ältere KruXx-Notizen (`•` und Folgezeilen); bei null Abschnitten wird kein Pager gerendert, damit fehlerhafte Release-Notes den App-Start nicht mehr abstürzen lassen |
| `SearchScreen.kt`, `OnDeviceVoiceSearch.kt` | KruXx zeigt bei leerer Online-/Bibliothekssuche ein Mikrofon. Es wird ausschließlich `createOnDeviceSpeechRecognizer()` (Android 12+) verwendet; **kein** allgemeiner bzw. Cloud-fähiger Fallback. Die Berechtigung wird erst beim Tippen angefragt und das Ergebnis nur editierbar eingesetzt, nicht abgeschickt. Bei fehlendem lokalen Sprachmodell erscheint ein Hinweis |
| `src/kruxx/AndroidManifest.xml` | KruXx-spezifische Package-Visibility-Abfrage für Spracheingabe sowie Installationsberechtigung und eng begrenzter `FileProvider` für verifizierte Update-APKs |
| `SearchResultScreen.kt`, `SearchResultProvider.kt` | Such-Cache berücksichtigt Sprache/Region und Konto/Anonym-Modus. Der Reiter „Titel“ führt die YTM-Filter **Titel und Videos parallel** aus, hängt abspielbare Video-/UGC-Treffer hinter die originale Titelrangfolge und entfernt Dubletten per `videoId`; der separate Video-Reiter bleibt unverändert nutzbar |
| `extensions/innertube/...` | YTM-Suchfilter und Parser aktualisiert; `playlistItemData.videoId` dient als zusätzlicher Endpunkt-Fallback. Suche und Vorschläge verwenden App-Sprache + eingestellte Region sowie – wenn aktiv – die angemeldete Sitzung; eine ungültige Sitzung fällt auf eine anonyme Anfrage zurück |
| `AppTitle.kt`, `scripts/make-kruxx-icon.py` | Der KruXx-Header verwendet die einzeilige Wortmarke „KruXx – The core of your music“; „KruXx“ ist fett und größer, der Zusatz kleiner und regulär gesetzt. Im KruXx-Flavor belegt sie 205 × 42 dp |
| `DownloadHelperImpl.kt`, `DownloadAllDialog.kt`, `DownloadState.kt` | Massendownloads werden dedupliziert und geordnet an `DownloadService` übergeben. KruXx lädt bis zu fünf Titel parallel und hält den Rest sichtbar in `STATE_QUEUED`; Liedtext-Nebenabrufe sind auf einen begrenzt. Wartende/laufende Downloads lassen sich per Tipp abbrechen |
| `PlayerModule.kt`, `StatefulPlayerImpl.kt`, `StatsForNerds.kt` | Kein Channel-Mapping/Downmix: ExoPlayers Standard-Audiopfad bleibt erhalten. Hall ist bei Preset „Keiner“ wirklich deaktiviert; die Decoder-Kanalzahl wird zur Mono-/Stereo-Diagnose angezeigt |

### 2.3 KruXx-Spiegel und Patch für `modules/innertube`

Das ursprüngliche Modul (GitLab tannguyen047/innertube-kotlin, Branch `dev`) ist seit Juli 2026
eingefroren. Der von KruXx benötigte Fix liegt daher in einem öffentlichen, MIT-lizenzierten Spiegel
unter `Massefehler/KruXx-innertube`; das Hauptrepo pinnt genau diesen Commit. Zusätzlich bleibt der
Patch unter `patches/innertube/` erhalten, damit Änderung und Basis unabhängig nachvollziehbar sind.

| Patch | Grund |
|---|---|
| `0001-charts-make-menu-item-accessibility-optional.patch` | YouTube liefert in den Ländereinträgen des Charts-Menüs (`musicMultiSelectMenuItemRenderer`) nur noch `accessibility`; die Pflichtfelder `selectedAccessibility`/`deselectedAccessibility` warfen `MissingFieldException` → „Failed to get charts“ auf der Startseite. Felder jetzt optional; Test `InnertubeChartsLiveResponseTest` mit der Live-Antwort vom 02.09.2026 |
| `0002-tests-update-fixtures-for-current-client-API.patch` | Veralteten Test-Provider auf `KtorProvider` und dieselbe fehlertolerante JSON-Konfiguration wie die App umgestellt; strukturierte Album-Unterzeilen und bewusst verworfene leere Künstlerbereiche korrekt geprüft, obsoleten Test der entfernten Request-API gelöscht. Ergebnis: 54 Innertube-Tests grün |

Ein frischer Clone benötigt keine manuelle Patch-Anwendung: `git submodule update --init` muss den
gepinnten Commit direkt laden können. Die Upstream-Basis `9e5f3ac` steht als `base-commit` in den Patches.

Patches neu erzeugen, wenn sich der Branch `kruxx` im Submodul ändert:
`git -C modules/innertube format-patch --base=9e5f3ac 9e5f3ac..kruxx -o ../../patches/innertube`.
Nur in den KruXx-Spiegel pushen; das ursprüngliche GitLab-Remote bleibt schreibgeschützt.

---

## 3. Bauen, Signieren, Installieren

Voraussetzungen (auf diesem Rechner vorhanden): JDK 21, Android SDK unter `/home/kruxx/android-sdk`
(Platform 36, Build-Tools 36.0.0), `local.properties` mit `sdk.dir=/home/kruxx/android-sdk`,
Python 3 + Pillow, ImageMagick (nur für SVG-Icons).

```bash
# Release (R8) bauen + signieren  →  composeApp/build/outputs/apk/kruxxUniversalProd/release/KruXx-release-signed.apk
scripts/build-local-release.sh kruxx
# nur neu signieren, ohne Build
scripts/build-local-release.sh kruxx --skip-build
# Debug (unoptimiert, App-ID de.kruxx.music.debug, läuft parallel zur Release-Version)
./gradlew :composeApp:assembleKruxxUniversalProdDebug
```

Fertige APKs werden von `build-local-release.sh` automatisch nach
`/home/kruxx/Schreibtisch/Android/Kreate-APKs/` kopiert und heißen `KruXx-<versionName>-release.apk` bzw.
`-debug.apk`. Eine passende, zuvor gebaute Debug-APK wird mit archiviert; eine veraltete Debug-APK
mit abweichender Version wird bewusst ausgelassen. Der Zielpfad ist absichtlich fest und kann nicht
per Umgebungsvariable umgeleitet werden. Das Skript verweigert andere Produkt-Flavors, damit nicht
versehentlich ein Kreate-Paket mit dem KruXx-Schlüssel veröffentlicht wird.

Erster Build dauert 5–10 min (Downloads), danach 1–4 min. Das Log ist voller
`WARNING: D8: Unexpected error during rewriting of Kotlin metadata` (AGP 8.13 vs. Kotlin 2.4)
und beim Release `e: … metadata is 2.4.0, expected version is 2.2.0` (Lint-Vital) – **beides
harmlos**, der Build ist trotzdem erfolgreich, solange am Ende `>> done:` steht.

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
- Tag: ausschließlich `vMAJOR.MINOR.PATCH`, z. B. `v1.0.1`
- APK: exakt `KruXx-MAJOR.MINOR.PATCH-release.apk`
- Standard: nachfragen; alternativ automatische Installation oder vollständig deaktiviert
- automatische Prüfung: höchstens einmal in 24 Stunden, manuelle Prüfung umgeht das Intervall
- vor der Installation: GitHub-Asset-Digest (SHA-256), Paket-ID, `versionName`, höherer
  `versionCode`, installierte Signatur und gepinnter Zertifikatfingerabdruck

Die bisher verteilten Builds `2.2.3-kruxx.x` enthalten noch keinen funktionsfähigen KruXx-Updater.
Sie benötigen deshalb genau einmal die manuelle Installation von `1.0.0`. Weil Paket-ID und
Signaturschlüssel gleich bleiben und der neue `versionCode` `1_000_000` höher ist, aktualisiert
Android die bestehende Installation ohne Löschen der App-Daten. Alle späteren Releases können dann
über den eigenen Updatekanal installiert werden.

GitHub Actions baut nur eine **unsignierte Debug-APK** und führt die App- sowie Innertube-Tests aus. Der Produktionsschlüssel
bleibt ausschließlich lokal. Ein Release wird erst nach lokalem signiertem Build, Prüfung und Tag
hochgeladen. Crashlogs, `.ignore.d/` und `local.properties` gehören nie in ein Release oder Commit.

---

## 4. Architektur von Wiedergabe, Suche und Downloads

### 4.1 Wiedergabe

```
ExoPlayer ── CacheDataSource (Downloads) ── CacheDataSource (Cache) ── ResolvingDataSource
                                                                          │ app/kreate/di/PlayerModule.kt
                                                                          ▼
                                            resolveInnertubeMedia()  (InnertubeResolvingDataSource.kt)
                                              ├─ upsertSongInfo()   → me.knighthat.innertube (NEXT) → Room
                                              ├─ streamCache[videoId] (Ablauf −30 s)
                                              └─ InnerTubeXPlayer.playerResponseForPlayback()
                                                   ├─ syncSession(): Cookie/visitorData/dataSyncId aus Preferences,
                                                   │                  anonyme Sessions: eigene visitorData (SharedPrefs "innertubex_session")
                                                   ├─ InnerTubeExtractor.extract(videoId, hints, excluded, quality)
                                                   │     ├─ Client-Auswahl (Katalog in InnerTubeX, VISIONOS/WEB_REMIX/…)
                                                   │     ├─ PO-Token via TokenProvider → PoTokenGenerator (WebView/BotGuard)
                                                   │     ├─ Cipher: RemotePlayerConfigStore (faraday) → EJS/QuickJS lokal
                                                   │     └─ liefert ExtractedStream (URL, Header, itag, expiresAt …)
                                                   └─ PlaybackData → DataSpec (URL, Header) + Format in Room
Fehler zur Laufzeit → ExoPlayerListener.tryRecoverPlaybackError():
   403/410/416: URL verwerfen, WEB_REMIX ggf. sperren, InnerTubeXPlayer.refreshAfterStreamRejection(), prepare()+seekTo()
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
- **Cache-Key ist nur die videoId, das Format aber nicht fix**: Qualität (High/Low, Auto im getakteten Netz →
  Low) und der liefernde Client bestimmen das itag – z. B. 140 (m4a) im Mobilnetz, 251 (webm) im WLAN; beim
  allerersten Abspielen eines expliziten Titels fehlt das Explicit-Flag in der DB noch (`upsertSongInfo` läuft
  parallel) und InnerTubeX nimmt VISIONOS statt WEB_REMIX. `CacheDataSource` würde gecachte Bytes des einen
  Files mit Netzwerk-Bytes des anderen zusammenkleben → Extractor-Fehler wie „Skipping atom with length >
  2147483647“. Deshalb schreibt der Resolver das itag in die Cache-Metadaten (`kruxx_itag`) und verwirft bei
  Abweichung die gecachten Spans (Abbruch per `CachedFormatMismatchException`, der Listener bereitet neu vor);
  Alt-Einträge ohne Metadaten fängt die Parser-Fehler-Recovery im Listener ab (Cache-Eintrag löschen, neu laden).
- CDN-Abruf (`PlayerModule.kt`): Der Chrome-User-Agent ist nur Default-Header; die Header aus
  `ExtractedStream.headers` (User-Agent/Referer/Origin je Client) gewinnen. **Nie `setUserAgent()`
  verwenden** – media3 hängt den per `addHeader` zusätzlich an, es gingen zwei User-Agent-Zeilen raus.
- Qualität: `Preferences.AUDIO_QUALITY` (High/Low/Auto) → InnerTubeX `HIGH/LOW/AUTO`; bei Auto und
  getaktetem Netz + `IS_CONNECTION_METERED` → `LOW`.
- **Kanalbelegung:** `PlayerModule` baut einen normalen `ExoPlayer` ohne `AudioProcessor`,
  `ChannelMappingAudioProcessor`, Mixer, Balance/Pan oder Mono-Downmix. Decoder und Android-`AudioTrack`
  erhalten daher die Kanalbelegung des YTM-/lokalen Quellformats unverändert. LoudnessEnhancer und
  BassBoost sind standardmäßig aus; `PresetReverb` wird bei Preset 0 („Keiner“) nun ebenfalls komplett
  deaktiviert und vom Aux-Ausgang getrennt. In „Stats for Nerds“ steht die vom Decoder gemeldete Zahl als
  `1 (Mono)` bzw. `2 (Stereo)`. Meldet der Decoder 2, aber beide Seiten klingen gleich, liegt die
  Zusammenführung nach dem App-Decoder (Android-Bedienungshilfe „Mono-Audio“, Geräte-EQ, Bluetooth-
  Profil/Empfänger, Kabel/Adapter oder tatsächlich mittig gemischte Aufnahme).
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

### 4.3 Downloads

`DownloadAllDialog` übergibt die vollständige, aktuell geladene Songliste in einem Aufruf an
`DownloadHelper.addDownloads()`. Dort werden lokale Titel, Dubletten sowie bereits fertige/laufende
Einträge herausgefiltert und alle übrigen `DownloadRequest`s geordnet über `MyDownloadService` gesendet.
Ein Fehler bei einem Titel darf das Einreihen der folgenden Titel nicht abbrechen.

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

---

## 5. Icon-Pipeline

```bash
python3 scripts/make-kruxx-icon.py icons/KruXx_App_Icon.png --glyph-ratio 0.66
```

erzeugt in `composeApp/src/androidKruxx/res/`: adaptives Icon (`mipmap-anydpi-v26/ic_launcher*.xml`
→ `@mipmap/kruxx_ic_launcher_foreground`, `@color/kruxx_ic_launcher_background`,
`@mipmap/kruxx_ic_launcher_monochrome`), Legacy-Icons (`mipmap-*/ic_launcher*.png`), TV-Banner
(`ic_banner.png`), Benachrichtigungs-Glyph (`drawable-*/app_icon_monochrome.png`), die Wortmarke
(`drawable/app_logo_text.png`, „KruXx – The core of your music“ im Header; fettes „KruXx“ größer als
der reguläre Zusatz) und
`drawable/ic_banner_foreground.png` als KruXx-Platzhalter für Titel ohne Cover. Wortmarke und
Cover-Platzhalter werden von der App eingefärbt.

Besonderheiten des aktuellen Icons: das rote „K“ mit blauer Audiowelle liegt als Kachel auf schwarzem
Grund → der schwarze Rand wird per Flood-Fill transparent „gekeyt“ und die Kachel als Glyph (~71 dp)
mittig platziert; Hintergrundfarbe = gekeyte Farbe. Für Themed Icons/Benachrichtigung wird ein fettes
„K“ gerendert (`--mono text`),
weil die glänzende 3D-Grafik keine brauchbare Silhouette liefert. Optionen: `--bg '#RRGGBB'`,
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
git -C modules/metrolist fetch && git -C modules/metrolist log --oneline HEAD..origin/kreate-old
git -C modules/metrolist checkout <commit>   # dann im Hauptrepo: git add modules/metrolist && commit
```
Bei `modules/metrolist` gibt es auch Branches `upstream`/`metrolist-v3` (näher an Metrolist); das
Build-Skript des Moduls muss zu Kreates Versionskatalog passen.

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
| HTTP 403 kurz nach Start, Song springt/stoppt | Signatur/`pot` abgelehnt; Recovery läuft (max. 2×) | Log `Stream of … rejected`; wenn dauerhaft: InnerTubeX-Update, ggf. `refreshAfterStreamRejection` |
| Song endet nach ~30 s oder bricht mit „unbekanntem Fehler“ ab, danach bei jedem Abspielen | Cache-Index hat eine zu kleine Gesamtlänge (begrenzter Sub-Range im Cache, §4) | Seit 02.09.2026 ausgeschlossen; Altlasten: Player-Cache leeren (Einstellungen → Daten) |
| „Skipping atom with length > 2147483647“ / „Unrecognized input format“ / „unbekannter Wiedergabefehler“ bei Position 0, nur bei bestimmten Songs | gecachte Bytes gehören zu einem anderen itag als der aufgelöste Stream (§4: Qualität/Client gewechselt) | Seit 02.09.2026 heilt sich das selbst (Log `Cached data of … unusable`, `Cached bytes of … are itag`); wenn nicht: Player-Cache leeren (Einstellungen → Daten) |
| Nur bestimmte Songs: `AGE_RESTRICTED` / `LoginRequiredException` | Altersbeschränkung; braucht Login + PO-Token | YouTube-Login in der App; PoToken-Logs prüfen |
| `PoToken … timed out` / `BadWebViewException` | System-WebView fehlt/kaputt; InnerTubeX fällt auf tokenfreie Clients zurück | Android System WebView aktualisieren |
| Suchergebnisse/Vorschläge leer, Wiedergabe geht | YTM-Filter/Antwort hat sich geändert oder die `:oldtube`-Sitzung ist veraltet; nicht InnerTubeX | `extensions/innertube`, `SearchResultScreen`, Innertube-Logs; anonym und angemeldet vergleichen |
| Track ist nachweislich auf YTM, fehlt aber unter „Titel“ | YTM sortiert ihn nur als Video/UGC ein; Region/Konto/Restriktion weicht ab; Parser kannte den Endpunkt nicht | Seit 2.2.3-kruxx.5 werden Titel+Videos zusammengeführt, App-Sprache/-Region und Login genutzt sowie weitere Endpunktformen erkannt. Bleibt er weg: YTM-URL/`videoId` mit beiden Kontomodi prüfen |
| Browse/Charts leer, Wiedergabe geht | Problem in `me.knighthat.innertube` (Submodul), nicht InnerTubeX | `modules/innertube`, Innertube-Logs |
| „Failed to get charts“ beim Öffnen der Startseite (Log-Tag `HomeQuickPicks`) | YouTube hat die Charts-Antwort geändert; das Modul deserialisiert strikt (`MissingFieldException`) | Live-Antwort holen (`POST youtubei/v1/browse`, `browseId=FEmusic_charts`, `formData.selectedValues=["DE"]`), als Fixture nach `modules/innertube/src/test/resources/ytm/browse/`, `InnertubeChartsLiveResponseTest` nennt das fehlende Feld, Modell anpassen (§2.3) |
| App stürzt nur beim ersten Start nach einem Update mit `IndexOutOfBoundsException: Index 0 out of bounds for length 0` ab; beim zweiten Start erscheint ein CrashReport | Release-Notes wurden nicht in Abschnitte geparst, der Changelog-Pager hatte deshalb null Seiten (trat in 2.2.3-kruxx.1/.2 durch `•` statt `-` und eine Überschrift ohne `:` auf) | Seit 2.2.3-kruxx.3: robuster Parser plus Schutz vor leeren Abschnitten. Neue Notizen immer im Format `Überschrift:` und `- Eintrag` schreiben |
| Bei „Alle Tracks downloaden“ werden nur drei Titel markiert oder der Gesamtdurchsatz ist trotz gutem Netz gering | Media3s Standard sind drei aktive Downloads; weitere Einträge waren korrekt `QUEUED`, aber `SongItem` zeigte diesen Zustand früher nicht. Liedtext-Nebenabrufe können zusätzlich konkurrieren; außerdem kann der YT-CDN einzelne Verbindungen drosseln | Seit 2.2.3-kruxx.6 sind Queue-/Restart-Zustände sichtbar und die Bulk-Übergabe geordnet; seit 2.2.3-kruxx.7 laufen bis zu fünf Audiodownloads, aber höchstens ein Nebenabruf. Bei echtem Stillstand Download-Benachrichtigung und Logs `DownloadHelperImpl`, `MyDownloadService`, `dataspec`, `InnerTubeXPlayer` prüfen |
| Wiedergabe wirkt mono | Quelle ist selbst mono/zentriert oder Android/Gerät mischt nach dem Decoder zusammen; KruXx enthält keinen Downmix. Ein gewählter Hall/EQ kann die Räumlichkeit verändern | „Stats for Nerds“ aufklappen: `Decoder-Kanäle: 2 (Stereo)` belegt ein Stereo-Quellformat. Dann Android → Bedienungshilfen → Audio → Mono-Audio, System-/Hersteller-EQ, Bluetooth-Gerät und Kabel/Adapter prüfen; Hall in KruXx auf „Keiner“ setzen |
| YouTube-Login/Bibliothek defekt | `modules/metrolist` (Metrolist-Innertube) | Submodul-Update (§6.3) |
| Build: `Dependency … requires compileSdk 37` | Flag in `gradle.properties` fehlt | §2.1 / §6.4 |
| Build: Lint-`e:`-Zeilen „expected version 2.2.0“ | Lint-Vital mit altem Kotlin | ignorieren (nur Release), Build läuft weiter |
| „Paket in Konflikt mit bestehendem Paket“ | gleiche App-ID, andere Signatur | KruXx-Flavor verwenden bzw. alte App deinstallieren |

### 7.3 Schnelltest nach Änderungen

1. Debug-APK bauen, installieren (koexistiert).
2. Frische Installation/gelöschte App-Daten: erster Start öffnet die Startseite ohne Absturz; zweiter Start zeigt keinen CrashReport-Dialog.
3. Spracheingabe (Android 12+): Suche öffnen, Mikrofon antippen, Berechtigung erteilen und sprechen. Text muss im Feld stehen und darf erst nach manueller Bestätigung gesucht werden. Abbrechen, Reiterwechsel und „Berechtigung verweigern“ ebenfalls prüfen. Flugmodus-Test mit installiertem lokalem Sprachmodell bestätigt den Offline-Betrieb.
4. Suche anonym prüfen: App-Sprache/Region Deutschland, bekannter Song sowie ein Treffer, den YTM nur als Musikvideo führt. Letzterer muss unter „Titel“ **und** „Videos“ erscheinen; keine doppelte `videoId`. Vorschläge, Reiter, Titel-Fortsetzung und Kindersicherung mit explizitem Video testen.
5. Suche angemeldet wiederholen; danach Cookie absichtlich ungültig machen und den anonymen Rückfall prüfen. Wechsel von Sprache/Region oder Login darf keine alten Suchergebnisse aus dem Cache zeigen.
6. Branding prüfen: Launcher-Icon (normal/rund), Themed Icon, Benachrichtigungssymbol, Ersatzgrafik bei fehlendem Cover und Header „KruXx – The core of your music“; „KruXx“ muss sichtbar größer als der Zusatz sein, auf schmalem Display dürfen die rechten Header-Aktionen nicht abgeschnitten werden; auf Android TV zusätzlich das Banner.
7. Album/Playlist mit mindestens 10 Titeln: „Alle Tracks downloaden“. Sofort müssen alle fünf aktiven **und alle wartenden** Titel markiert sein; nach Abschluss alle offline abspielen. Einen wartenden und einen laufenden Titel per Tipp abbrechen; ein einzelner Fehler darf die restliche Queue nicht stoppen. Für einen belastbaren Geschwindigkeitsvergleich dasselbe Album bei stabilem Netz einmal mit leerem Download-Cache messen.
8. Stereo-Testdatei bzw. bekannter YTM-Stereotest: „Stats for Nerds“ aufklappen und `Decoder-Kanäle: 2 (Stereo)` prüfen; links/rechts getrennt über kabelgebundene Kopfhörer testen. Danach optional Mono-Audio in Android aktivieren, um den Unterschied eindeutig gegenzuprüfen.
9. Anonym (ausgeloggt): 3–4 Songs aus Suche/Charts abspielen, dazwischen seeken; 1 explizit markierter Song.
10. Eingeloggt: Song aus der eigenen Bibliothek; Cache-Verhalten (zweites Abspielen offline).
11. `adb logcat` auf `Playback: client=…` prüfen – welcher Client liefert? (VISIONOS ohne Token, WEB_REMIX mit PO-Token.)

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

## 8. Release-Checkliste

- [ ] `git status` sauber, Branch `main`; `origin` ist `Massefehler/KruXx`, `upstream` hat Push-URL `DISABLED`
- [ ] `KRUXX_VERSION_NAME` in `composeApp/build.gradle.kts` nach SemVer erhöhen und
      `KRUXX_VERSION_CODE` **bei jedem APK-Release** um mindestens 1 erhöhen (nie wiederverwenden/senken)
- [ ] `docs/changelogs/kruxx/<versionName>.txt` schreiben: jeder Abschnitt beginnt mit
      `Überschrift:`, jeder Eintrag mit `- `; Kreates eigene Notizen unter
      `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` bleiben unangetastet
- [ ] `./gradlew :innertube:test :composeApp:testKruxxUniversalProdDebugUnitTest` und Debug-Schnelltest (§7.3)
- [ ] `scripts/build-local-release.sh kruxx` → `>> done:` und
      „Signer #1 certificate DN: CN=Kreate local build …“
- [ ] `aapt2 dump badging …/KruXx-release-signed.apk` → erwartete Paket-ID, exakter
      `versionCode` und `versionName`; `apksigner verify --print-certs` → erwarteter Fingerabdruck
- [ ] Im Archiv `/home/kruxx/Schreibtisch/Android/Kreate-APKs/` prüfen, dass das Build-Skript Release und
      passende Debug-APK als `KruXx-<versionName>-release.apk` / `-debug.apk` abgelegt hat (LocalSend)
- [ ] Geheimnis-/Datenschutzprüfung: insbesondere kein Crashlog, Cookie, Token, Keystore oder Passwort
- [ ] Release-Commit auf `main`, annotiertes Tag `v<versionName>`, Push ausschließlich zu `origin`
- [ ] GitHub-Release mit exakt dem archivierten APK-Namen erstellen; API anschließend auf Tag,
      Assetname, Größe und `sha256:`-Digest prüfen
- [ ] Upgrade über die vorherige installierte KruXx-Version testen; Einstellungen → Allgemein →
      „Jetzt überprüfen“ muss das neue Release finden und nach Prüfung Androids Installer öffnen

---

## 9. Offene Punkte / Ideen

- Monochrom-Glyph (Themed Icons, Benachrichtigung) ist derzeit ein Text-„K“; ein flaches
  Weiß-auf-transparent-Motiv würde besser zum Icon passen (`--mono alpha` mit eigener Datei ergänzen).
- Unit-Tests für `mapExtractionFailure` und das Cache-Ablaufverhalten im Resolver.
- AGP 9 / Kotlin 2.4.10 nachziehen (Dependabot-Branches upstream), danach `disableCompileSdkChecks` prüfen.
- Upstream-Branch `preferences` beobachten: wenn der „backend overhaul“ in `main` landet,
  InnerTubeX-Anbindung in die neue Modulstruktur (`extensions/player`) übernehmen.
- In `ErrorHandlingPolicy` HTTP 403/410/416 sofort fatal melden statt media3s drei Standard-Retries (0/1/2 s),
  damit die Recovery schneller anspringt.
- `isExplicit`-Hint beim allerersten Abspielen: Song-Info wird parallel geladen, der Hint ist dann `null` und
  InnerTubeX nimmt VISIONOS (§4). Option: vor der Auflösung kurz auf `upsertSongInfo` warten oder das Flag aus
  dem MediaItem mitgeben, dann wählt InnerTubeX von Anfang an den passenden Client.
- `streamCache` bei Wechsel der Audio-Qualität invalidieren (Metrolist: Bypass-Flag), sonst läuft die alte URL bis zum Ablauf.
- Chunking-DataSource zwischen Resolver und OkHttp, falls Bounded-Range-Clients (ANDROID_VR/IOS/TVHTML5_SIMPLY)
  als zusätzliche Reserve gebraucht werden (§4).
