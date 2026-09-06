# LAME 3.100 in KruXx

Unmodified encoder C sources/headers from the official LAME 3.100 release:
https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz

Archive SHA-256: `ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e`.

Only `libmp3lame/*.c`, `libmp3lame/*.h`, `libmp3lame/vector/lame_intrin.h`,
`include/lame.h` and the license are included. No decoder, assembler or frontend.
The Android build configuration and JNI bridge live one directory above and are
KruXx code. This version is pinned for reproducible encoding; updates require
rerunning the encoder checks.

LAME is distributed under LGPL 2.0 or later; see [COPYING](COPYING). Authors and
copyright notices are retained in the individual source files. It is built as
`libmp3lame.so`, separately from the small `libkruxx_mp3.so` JNI bridge. Both
libraries can be rebuilt/replaced using the supplied CMake project and Android NDK.
The APK includes the license at `res/raw/lame_license.txt` and lists the library
in Settings → About → Licenses.

KruXx uses CBR 320 kbit/s, LAME quality 0, original mono/stereo channels and an
output sample rate of 44.1/48 kHz. Lower sample rates are resampled because
320 kbit/s requires MPEG-1. Transcoding cannot restore information missing from
the input. No loudness normalization, silence trimming or audio effects are applied.
