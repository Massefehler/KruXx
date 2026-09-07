# KruXx notices and provenance

KruXx is an independent modification of the GPL-3.0-licensed
[Kreate](https://github.com/knighthat/Kreate) project. Kreate is itself derived from
[RiMusic](https://github.com/fast4x/RiMusic). The KruXx project is maintained
independently and is not an official Kreate or RiMusic release.

KruXx modifications are Copyright © 2026 KruXx contributors. Existing copyright,
authorship and license notices remain the property of their respective authors.
Distribution of KruXx is governed by the [GNU GPL version 3](LICENSE); separately
licensed bundled components remain governed by the licenses shipped with them and
listed in the app's Licenses screen.

Substantial inherited work includes Kreate by Knight Hat, RiMusic by fast4x and code
originating from the Metrolist project and InnerTubeX. The pinned
[`modules/innertube`](modules/innertube) dependency is maintained for KruXx through the
public `Massefehler/KruXx-innertube` mirror of the MIT-licensed innertube-kotlin project;
its original attribution and license are retained. The pinned
[`modules/metrolist`](modules/metrolist) dependency is likewise maintained through the public
`Massefehler/KruXx-metrolist` mirror of Knight Hat's Metrolist-innertube fork; its upstream history,
copyright and license notices remain intact. A mirror or local compatibility patch does not
transfer authorship of the underlying work to KruXx.

The project and its maintainers are not affiliated with, funded, authorized,
endorsed by or sponsored by Google LLC, YouTube, Kreate or RiMusic. YouTube,
YouTube Music, Android and other names or marks belong to their respective owners.

KruXx uses unofficial interfaces to communicate with third-party services. No
guarantee is made that those services will remain compatible or available. The
software is supplied without warranty as described in GPL-3.0.

The complete corresponding source for official KruXx APKs is available at
<https://github.com/Massefehler/KruXx>, identified by the release's matching Git tag.
The current feature and verification status is documented in
[`docs/KRUXX-IST-STAND.md`](docs/KRUXX-IST-STAND.md); historical release notes remain
available under [`docs/changelogs/kruxx`](docs/changelogs/kruxx).

KruXx's download feature bundles the LAME 3.100 encoder, licensed under the GNU Library
General Public License 2.0 or later. Original source notices and the complete license are in
[`composeApp/src/androidMain/cpp/lame`](composeApp/src/androidMain/cpp/lame).
The encoder and JNI bridge are separate shared libraries; source provenance, archive checksum
and rebuilding instructions are included in that directory. The APK retains the license resource
and identifies LAME in its Licenses screen.
