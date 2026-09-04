# KruXx – The core of your music

KruXx is an independent, open-source Android music player based on
[Kreate](https://github.com/knighthat/Kreate) and its
[RiMusic](https://github.com/fast4x/RiMusic) heritage. It has its own package ID
(`de.kruxx.music`), release line, branding, source repository and update channel.

KruXx is not affiliated with, funded, authorized or endorsed by Google, YouTube,
Kreate or RiMusic. YouTube and YouTube Music are trademarks of their respective owners.

The current stable release is KruXx `1.0.2` (`versionCode 1000002`, Android 6.0+).
Its 146 unit tests, full release lint, signed release build and in-place upgrade/cold-start check of
the exact archived APK have passed. The signed APK, source, submodule pins and matching tag are
published in the
[KruXx 1.0.2 release](https://github.com/Massefehler/KruXx/releases/tag/v1.0.2). The exact
implementation, verification status and remaining extended device checks are recorded in the
[KruXx status report](docs/KRUXX-IST-STAND.md); the immutable in-app notes are available in
[`docs/changelogs/kruxx/1.0.2.txt`](docs/changelogs/kruxx/1.0.2.txt).

The next local release candidate is KruXx `1.1.0` (`versionCode 1000003`), titled
**KruXx Glass Update**. It is a visual feature update with new branding and targeted reliability
fixes; the planned dedicated podcast section remains outside this release. Its automated tests,
release lint, Debug build and unsigned Release build pass locally. Until a signed artifact from the
clean release commit is verified and published, `1.0.2` remains the latest stable download.

## Install and update

Download only the signed APK attached to the
[latest KruXx release](https://github.com/Massefehler/KruXx/releases/latest).

From version 1.0.0 onward, KruXx can check that release channel once per day. Before
opening Android's installer, it verifies all of the following:

- stable semantic version and the exact expected asset name;
- GitHub's SHA-256 asset digest;
- package ID `de.kruxx.music` and a higher Android version code;
- the pinned KruXx release signing certificate.

Existing `2.2.3-kruxx.x` installations need one manual installation of an independently
versioned KruXx release (`1.0.0` or newer) because those builds did not yet contain the
updater. The package ID, signing key and higher Android version code are retained, so the
installation updates in place; later KruXx versions can then be installed through the app.

Update checks can be changed or disabled under **Settings → General → Update**. No
crash report is uploaded automatically or offered to the Kreate project.

## KruXx changes

### Next release candidate: 1.1.0 — Glass Update

- A distinct graphite backdrop with restrained blue and red light accents and reusable glass-style
  surfaces throughout home, library tabs, players, settings, details, dialogs, menus and sheets.
- A new transparent app icon set and a smoother ten-frame cold-start animation integrated into the
  same KruXx backdrop.
- More compact Quick Picks cards and download controls, corrected Top Artists spacing, refined
  player/queue layering and consistently rounded settings headers.
- A home refresh fix and restored optional suppression of notification sounds and heads-up pop-ups
  during active playback.

### Included in the current stable release

- Quick Picks is the default start page. Its suggestions can show up to 18 deduplicated results
  assembled from the seed, radio and related items in consistently sized rows, and Top Artists now
  open their artist pages.
- Signed-in YTM home and library playlists retain their account context, including continuation
  requests. Their playlist pages use the same Metrolist client as login and library synchronization;
  public anonymous playlists retain the lightweight browse path. Artist synchronization tolerates
  missing artwork, removes duplicates and reapplies the selected sorting after online and local
  results are merged.
- Privacy-first, on-device voice input for search on supported Android 12+ devices; recognized text
  remains editable and is never submitted automatically.
- Broader YTM search combines the original song ranking with additional playable video/UGC results
  and keys its cache by language, region and account mode.
- Directly selected official music videos and user videos can open in a 16:9 embedded player. Art
  Tracks with a still image remain audio-only, and failed embeds resume as audio at the same point.
- Android Picture-in-Picture is available as the floating player on Android 7+, with separate
  controls for allowing it and entering it automatically when leaving the app.
- Ordered single and bulk download queues with up to five simultaneous audio transfers. Downloads
  always request the best currently available compatible YTM audio quality, independently of the
  playback quality or network type.
- Current InnerTubeX playback integration, expired-stream recovery and cache-format protection.
- Optional suppression of other apps' notification sounds only during active playback, while media,
  alarms, calls and visible pop-ups remain allowed.
- No app-side mono downmix; decoder-channel diagnostics in “Stats for Nerds” help distinguish a mono
  source from later Android/device processing.
- KruXx branding across launcher, themed, notification and TV assets, plus the larger
  “KruXx – The core of your music” header wordmark and a ten-frame animated cold-start screen whose
  two loops run for 2.4 seconds before a short fade into the app.

The [documentation index](docs/README.md) links the current status, detailed architecture, build
steps, test matrix, release process and immutable version history.

## Build from source

Prerequisites are JDK 21 and an Android SDK configured in `local.properties`.

```bash
git clone --recurse-submodules https://github.com/Massefehler/KruXx.git
cd KruXx
./gradlew :innertube:test :composeApp:testKruxxUniversalProdDebugUnitTest
./gradlew :composeApp:assembleKruxxUniversalProdDebug
```

Release signing material is intentionally not part of the repository. Local signed
release builds use `scripts/build-local-release.sh kruxx`; see the handbook before
creating or publishing one. The script always archives finished KruXx APKs in
`/home/kruxx/Schreibtisch/Android/Kreate-APKs/` on the maintained local build machine.

## Privacy and service notice

KruXx has no project telemetry and does not submit crash reports. Diagnostic crash logs remain on
the device and can be explicitly exported by the user. They must not be committed or attached to an
upstream report without deliberate local review and redaction. Normal music features must contact
YouTube Music and optional metadata/lyrics services; GitHub is contacted only when update checks are
enabled or manually requested.

KruXx uses unofficial interfaces to access third-party services. Availability can
change without notice. Users are responsible for complying with applicable service
terms and local law.

## License and credits

KruXx is distributed under the [GNU General Public License v3.0](LICENSE). The source
corresponding to each official APK is published with its release tag. Copyright and
license notices from Kreate, RiMusic and included dependencies are retained.

See [NOTICE.md](NOTICE.md) for provenance and attribution. Important upstream work
includes Kreate by Knight Hat, RiMusic by fast4x, Metrolist/InnerTubeX, Ionicons,
HypnoticCanvas, KuGou and LRCLIB. Their own licenses and notices continue to apply.

## Support

Please report KruXx-specific problems in this repository's
[issue tracker](https://github.com/Massefehler/KruXx/issues). Do not send KruXx crash
reports or support requests to the Kreate project.
