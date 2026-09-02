# KruXx – The core of your music

KruXx is an independent, open-source Android music player based on
[Kreate](https://github.com/knighthat/Kreate) and its
[RiMusic](https://github.com/fast4x/RiMusic) heritage. It has its own package ID
(`de.kruxx.music`), release line, branding, source repository and update channel.

KruXx is not affiliated with, funded, authorized or endorsed by Google, YouTube,
Kreate or RiMusic. YouTube and YouTube Music are trademarks of their respective owners.

## Install and update

Download only the signed APK attached to the
[latest KruXx release](https://github.com/Massefehler/KruXx/releases/latest).

From version 1.0.0 onward, KruXx can check that release channel once per day. Before
opening Android's installer, it verifies all of the following:

- stable semantic version and the exact expected asset name;
- GitHub's SHA-256 asset digest;
- package ID `de.kruxx.music` and a higher Android version code;
- the pinned KruXx release signing certificate.

Existing `2.2.3-kruxx.x` installations need one manual installation of `1.0.0` because
those builds did not yet contain the updater. The package ID, signing key and higher
Android version code are retained, so this installation updates in place; later KruXx
versions can then be installed through the app.

Update checks can be changed or disabled under **Settings → General → Update**. No
crash report is uploaded automatically or offered to the Kreate project.

## KruXx changes

- Start page opens on Quick Picks instead of the Songs tab.
- On-device voice input for search on supported Android devices.
- Broader YTM search by combining song and playable video/UGC results.
- Improved queued and parallel bulk downloads.
- Current InnerTubeX playback integration and recovery for expired streams.
- Stereo decoder-channel diagnostics in “Stats for Nerds”.
- KruXx branding, launcher icons and header wordmark.

The detailed architecture, build steps and release process are documented in the
[KruXx developer handbook](docs/KRUXX-ENTWICKLERHANDBUCH.md).

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
creating or publishing one.

## Privacy and service notice

KruXx has no project telemetry and does not submit crash reports. Diagnostic crash
logs remain on the device and can be explicitly exported by the user. Normal music features must
contact YouTube Music and optional metadata/lyrics services; GitHub is contacted only
when update checks are enabled or manually requested.

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
