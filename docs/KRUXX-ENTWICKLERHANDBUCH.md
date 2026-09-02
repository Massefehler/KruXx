# KruXx – Entwicklerhandbuch (Wiedereinstieg, Weiterentwicklung, Bugfixing)

Stand: 02.09.2026 · Basis: Kreate `main` @ `f02577e8` (v2.2.3) · Branch: `kruxx`

KruXx ist ein privater Fork von [Kreate](https://github.com/knighthat/Kreate) (RiMusic/ViMusic-Linie).
Zwei Dinge unterscheiden ihn vom Original:

1. **Playback läuft über die Bibliothek [InnerTubeX](https://github.com/MetrolistGroup/innertubex)**
   statt über den eingefrorenen WebView-Cipher-Port – YouTube-Änderungen werden durch ein
   Versions-Update der Bibliothek behoben, nicht durch Handarbeit am Cipher-Code.
2. **Eigenes Branding als Produkt-Flavor `kruxx`**: App-ID `de.kruxx.music`, Name „KruXx“,
   eigenes Icon, kein In-App-Updater. Installiert sich neben der offiziellen Kreate.

---

## 0. Grundregeln

- **Niemals etwas zu `knighthat/Kreate` hochladen.** Das Remote heißt absichtlich `upstream` und
  hat eine deaktivierte Push-URL (`git remote -v` → `DISABLED (push)`). Keine Issues, PRs oder
  Pushes dorthin. Wenn ein eigenes Remote gewünscht ist (z. B. privates GitLab), unter dem Namen
  `origin` hinzufügen – niemals die Upstream-URL als Push-Ziel eintragen.
- Kreate ist **GPL-3.0**. Für den privaten Gebrauch ist alles erlaubt. Sobald die APK an Dritte
  weitergegeben wird, muss ihnen auch der Quellcode (dieses Repo) zugänglich gemacht werden.
  Die Credits im „Über“-Bereich (Kreate/RiMusic) bleiben deshalb absichtlich erhalten.
- `.ignore.d/` (Keystore, Passwörter) und `local.properties` sind gitignored und dürfen nie
  committet werden.

---

## 1. Repo-Struktur und Branch-Modell

```
main    = unveränderter Spiegel von upstream/main (nur fetch/rebase, keine eigenen Commits)
kruxx   = unsere Version: InnerTubeX-Playback + KruXx-Flavor + Tooling + diese Doku
```

Wichtige Pfade:

| Pfad | Zweck |
|---|---|
| `composeApp/` | Die App (Kotlin Multiplatform; Android in `src/androidMain`, Flavor-Quellen in `src/android<Flavor>`) |
| `composeApp/src/androidKruxx/` | Nur KruXx: No-Op-Updater (`kotlin/`) und Icon-Ressourcen (`res/`, generiert) |
| `modules/innertube` | Submodul: `me.knighthat.innertube` (GitLab tannguyen047/innertube-kotlin) – Suche, Browse, Song-Metadaten |
| `modules/metrolist/innertube` | Submodul: Metrolists altes InnerTube-Modul (knighthat-Fork, Branch `kreate-old`) – YouTube-Login, Bibliothek/Alben/Künstler, `YouTube`-Session |
| `modules/kizzy` | Submodul: Discord-RPC |
| `scripts/strings-modifier` | Submodul: Dev-Skript, nicht Teil des Builds |
| `extensions/` | Kreates eigene Module (altes Innertube „oldtube“, kugou, lrclib, discord) |
| `icons/KruXx_App_Icon.png` | Quellbild des KruXx-Icons (1254², Kachel auf Schwarz) |
| `scripts/build-local-release.sh` | Release bauen **und** signieren |
| `scripts/make-kruxx-icon.py` | Alle Icon-Ressourcen aus dem Quellbild erzeugen |
| `docs/KRUXX-ENTWICKLERHANDBUCH.md` | diese Datei |

Submodule nach einem frischen Clone: `git submodule update --init` (nicht `--recursive`, wie Kreates CI).

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
| `composeApp/build.gradle.kts` | Flavor `kruxx` (Dimension `platform`): `applicationId = "de.kruxx.music"`, `APP_NAME = "KruXx"`; Label über `androidComponents.onVariants` gepinnt (Build-Type-/Env-Placeholder würden sonst gewinnen); APK-Name `KruXx-*.apk`; neues `BuildConfig.REPO_NAME` |
| `me/knighthat/utils/Repository.kt` | GitHub-Links nutzen `REPO_NAME` („Kreate“), nicht den App-Namen |
| `composeApp/src/androidKruxx/kotlin/…` | No-Op-`UpdateHandler`/`updateSection` (Kopie von F-Droid) – kein In-App-Updater |
| `composeApp/src/androidKruxx/res/…` | generierte Icon-Ressourcen (siehe §5) |

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
# Stock-Kreate-Variante (App-ID me.knighthat.kreate – kollidiert mit der offiziellen App!)
scripts/build-local-release.sh github
```

Erster Build dauert 5–10 min (Downloads), danach 1–4 min. Das Log ist voller
`WARNING: D8: Unexpected error during rewriting of Kotlin metadata` (AGP 8.13 vs. Kotlin 2.4)
und beim Release `e: … metadata is 2.4.0, expected version is 2.2.0` (Lint-Vital) – **beides
harmlos**, der Build ist trotzdem erfolgreich, solange am Ende `>> done:` steht.

**Signatur / Keystore**

- `.ignore.d/keystores/kreate-local.jks` + `kreate-local.properties` (Passwörter). Gitignored.
- Backup-Kopie: `~/Schreibtisch/Android/Kreate-APKs/keystore-backup/` – zusätzlich auf USB/Cloud sichern.
- Geht der Keystore verloren, lässt sich KruXx nicht mehr per Update installieren
  (nur nach Deinstallation → Datenverlust).
- Die offiziellen Kreate-APKs sind mit knighthats Key signiert. Unsere `github`-Variante hat
  dieselbe App-ID, aber unseren Key → Android meldet „Paket in Konflikt“. Deshalb der Flavor `kruxx`.

**Aufs Handy** (ohne ADB): APK per LocalSend senden, in „Downloads“ antippen, „Unbekannte Apps
installieren“ einmalig erlauben. Fertige APKs liegen zusätzlich in
`~/Schreibtisch/Android/Kreate-APKs/`.

**Daten migrieren** (Kreate → KruXx): Kreate → Einstellungen → Daten → „SICHERN UND
WIEDERHERSTELLEN“ → „In Backup speichern“ + „Einstellungen exportieren“; in KruXx entsprechend
importieren, danach YouTube neu anmelden (Anmeldedaten sind vom Export ausgeschlossen).

---

## 4. Architektur der Wiedergabe

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
- Log-Tags (adb logcat): `InnerTubeXPlayer`, `dataspec`, `ExoPlayerListener`, `InnerTube`,
  `InnerTubeExtractor`, `YouTubeCipherService`, `RemotePlayerConfigStore`, `PoTokenGenerator`, `PoTokenWebView`.

Was **nicht** über InnerTubeX läuft: Suche/Browse/Charts (`me.knighthat.innertube`, Submodul
`modules/innertube`), YouTube-Login und Bibliothek (`com.metrolist.innertube.YouTube`, Submodul
`modules/metrolist`). Beide Module haben ihre eigene Session-Verwaltung; die Werte kommen aus denselben
`Preferences` (`YOUTUBE_COOKIES`, `YOUTUBE_VISITOR_DATA`, `YOUTUBE_SYNC_ID`).

---

## 5. Icon-Pipeline

```bash
python3 scripts/make-kruxx-icon.py icons/KruXx_App_Icon.png --glyph-ratio 0.66
```

erzeugt in `composeApp/src/androidKruxx/res/`: adaptives Icon (`mipmap-anydpi-v26/ic_launcher*.xml`
→ `@mipmap/kruxx_ic_launcher_foreground`, `@color/kruxx_ic_launcher_background`,
`@mipmap/kruxx_ic_launcher_monochrome`), Legacy-Icons (`mipmap-*/ic_launcher*.png`), TV-Banner
(`ic_banner.png`), Benachrichtigungs-Glyph (`drawable-*/app_icon_monochrome.png`) und die Wortmarke
(`drawable/app_logo_text.png`, ersetzt den „Kreate“-Schriftzug im Header, wird von der App eingefärbt).

Besonderheiten des aktuellen Icons: die Kachel liegt auf schwarzem Grund → der schwarze Rand wird per
Flood-Fill transparent „gekeyt“ und die Kachel als Glyph (~71 dp) mittig platziert; Hintergrundfarbe
= gekeyte Farbe. Für Themed Icons/Benachrichtigung wird ein fettes „K“ gerendert (`--mono text`),
weil die glänzende 3D-Grafik keine brauchbare Silhouette liefert. Optionen: `--bg '#RRGGBB'`,
`--mode glyph|cover`, `--mono alpha|luminance|text|none`, `--mono-text`, `--wordmark`, `--font`.
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

### 6.2 Upstream-Kreate holen (Bugfixes, Features)

```bash
git fetch upstream
git switch main && git merge --ff-only upstream/main && git submodule update --init
git switch kruxx && git rebase main          # oder: git merge main
```

Konfliktkandidaten: `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, `PlayerModule.kt`,
`ExoPlayerListener.kt`, `MainApplication.kt`. Achtung: Upstream arbeitet auf dem Branch
`preferences` an einer Modul-Neuordnung (`extensions/player`, `extensions/gateway`, …). Landet das in
`main`, ist ein Rebase aufwendig – dann besser `InnerTubeXPlayer.kt` + Resolver-Logik in die neue
Struktur **neu einhängen**, statt Konflikte Zeile für Zeile zu lösen. Nach jedem Rebase: Submodule
mit `git submodule update --init` auf die neuen Pins bringen.

### 6.3 Submodule aktualisieren

```bash
git -C modules/metrolist fetch && git -C modules/metrolist log --oneline HEAD..origin/kreate-old
git -C modules/metrolist checkout <commit>   # dann im Hauptrepo: git add modules/metrolist && commit
```
Bei `modules/metrolist` gibt es auch Branches `upstream`/`metrolist-v3` (näher an Metrolist); das
Build-Skript des Moduls muss zu Kreates Versionskatalog passen.

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
Ohne ADB: Kreate schreibt Crashlogs nach `Android/data/de.kruxx.music/files/crashlogs/`; in der App
unter Einstellungen → Sonstiges → Debug lassen sich Logs exportieren/kopieren.

### 7.2 Typische Fehlerbilder

| Symptom | Wahrscheinliche Ursache | Wo ansetzen |
|---|---|---|
| „Kein abspielbares Format“ / `StreamResolveException NO_PLAYABLE_STREAM` bei allen Songs | YouTube hat Clients geändert; InnerTubeX-Version veraltet | §6.1 – Bibliothek aktualisieren |
| HTTP 403 kurz nach Start, Song springt/stoppt | Signatur/`pot` abgelehnt; Recovery läuft (max. 2×) | Log `Stream of … rejected`; wenn dauerhaft: InnerTubeX-Update, ggf. `refreshAfterStreamRejection` |
| Song endet nach ~30 s oder bricht mit „unbekanntem Fehler“ ab, danach bei jedem Abspielen | Cache-Index hat eine zu kleine Gesamtlänge (begrenzter Sub-Range im Cache, §4) | Seit 02.09.2026 ausgeschlossen; Altlasten: Player-Cache leeren (Einstellungen → Daten) |
| „Skipping atom with length > 2147483647“ / „Unrecognized input format“ / „unbekannter Wiedergabefehler“ bei Position 0, nur bei bestimmten Songs | gecachte Bytes gehören zu einem anderen itag als der aufgelöste Stream (§4: Qualität/Client gewechselt) | Seit 02.09.2026 heilt sich das selbst (Log `Cached data of … unusable`, `Cached bytes of … are itag`); wenn nicht: Player-Cache leeren (Einstellungen → Daten) |
| Nur bestimmte Songs: `AGE_RESTRICTED` / `LoginRequiredException` | Altersbeschränkung; braucht Login + PO-Token | YouTube-Login in der App; PoToken-Logs prüfen |
| `PoToken … timed out` / `BadWebViewException` | System-WebView fehlt/kaputt; InnerTubeX fällt auf tokenfreie Clients zurück | Android System WebView aktualisieren |
| Suche/Browse leer, Wiedergabe geht | Problem in `me.knighthat.innertube` (Submodul), nicht InnerTubeX | `modules/innertube`, Innertube-Logs |
| YouTube-Login/Bibliothek defekt | `modules/metrolist` (Metrolist-Innertube) | Submodul-Update (§6.3) |
| Build: `Dependency … requires compileSdk 37` | Flag in `gradle.properties` fehlt | §2.1 / §6.4 |
| Build: Lint-`e:`-Zeilen „expected version 2.2.0“ | Lint-Vital mit altem Kotlin | ignorieren (nur Release), Build läuft weiter |
| „Paket in Konflikt mit bestehendem Paket“ | gleiche App-ID, andere Signatur | KruXx-Flavor verwenden bzw. alte App deinstallieren |

### 7.3 Schnelltest nach Änderungen

1. Debug-APK bauen, installieren (koexistiert).
2. Anonym (ausgeloggt): 3–4 Songs aus Suche/Charts abspielen, dazwischen seeken; 1 explizit markierter Song.
3. Eingeloggt: Song aus der eigenen Bibliothek; Cache-Verhalten (zweites Abspielen offline).
4. `adb logcat` auf `Playback: client=…` prüfen – welcher Client liefert? (VISIONOS ohne Token, WEB_REMIX mit PO-Token.)

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

- [ ] `git status` sauber, Branch `kruxx`
- [ ] Versionsstand notieren (`gradle/libs.versions.toml` → `versionName`; bei Bumps auch
      `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` anlegen, sonst fehlt `R.raw.release_notes`)
- [ ] `scripts/build-local-release.sh kruxx` → `>> done:` und „Signer #1 certificate DN: CN=Kreate local build …“
- [ ] Debug-Schnelltest (§7.3), dann Release installieren
- [ ] APK nach `~/Schreibtisch/Android/Kreate-APKs/` kopieren (LocalSend)
- [ ] Commit auf `kruxx`; **kein Push zu upstream**

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
