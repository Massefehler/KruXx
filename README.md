# KruXx – The core of your music

KruXx is an independent, open-source Android music player based on
[Kreate](https://github.com/knighthat/Kreate) and its
[RiMusic](https://github.com/fast4x/RiMusic) heritage. It has its own package ID
(`de.kruxx.music`), release line, branding, source repository and update channel.

KruXx is not affiliated with, funded, authorized or endorsed by Google, YouTube,
Kreate or RiMusic. YouTube and YouTube Music are trademarks of their respective owners.

The current local development line is KruXx `1.0.1` (`versionCode 1000001`, Android 6.0+).
Its unit tests, full release lint, signed release build, broad on-device acceptance test and the
installation/cold-start check of the exact archived APK have passed. It is not public yet: the
source, submodule pins and matching `v1.0.1` tag still need to be published. The exact
implementation, verification and release status is recorded in the
[KruXx 1.0.1 status report](docs/KRUXX-IST-STAND.md).

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
