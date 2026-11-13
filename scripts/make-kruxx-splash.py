#!/usr/bin/env python3
"""
Generate the two artwork layers of the KruXx cold-start animation from the ten source frames.

    scripts/make-kruxx-splash.py [--frames icons/animation] [--size 600]

The ten frames in icons/animation/ show the same tile, "K" and waveform; only a light point
travels along the waveform (and jumps irregularly between frames). The app therefore no longer
flips through the frames. It shows one still layer and draws the travelling light itself at the
display refresh rate (StartupSplash.kt):

  kruxx_startup_base.webp  per-pixel median of all frames: the artwork without the light point
  kruxx_startup_wave.webp  white waveform bars with their coverage as alpha; the app adds it
                           near the light so the bars it passes brighten

Both are written to composeApp/src/androidKruxx/res/drawable-nodpi/. The script also prints the
height of the waveform's centre line, which must match WAVE_CENTER_Y in StartupSplash.kt.
Requires Pillow and NumPy.
"""
import argparse
import glob
import os
import re
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "composeApp", "src", "androidKruxx", "res", "drawable-nodpi")
SPLASH_SOURCE = os.path.join(
    ROOT, "composeApp", "src", "androidMain", "kotlin", "it", "fast4x", "rimusic", "ui",
    "components", "StartupSplash.kt",
)


def frame_number(path):
    match = re.search(r"\((\d+)\)", os.path.basename(path))
    return int(match.group(1)) if match else sys.maxsize


def smoothstep(x, edge0, edge1):
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def save_webp(image, size, path):
    # Resize premultiplied so the transparent tile corners do not leave dark fringes.
    resized = image.convert("RGBa").resize((size, size), Image.LANCZOS).convert("RGBA")
    resized.save(path, "WEBP", quality=90, method=6, alpha_quality=100)
    return os.path.getsize(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--frames", default=os.path.join(ROOT, "icons", "animation"))
    parser.add_argument("--size", type=int, default=600)
    args = parser.parse_args()

    paths = sorted(glob.glob(os.path.join(args.frames, "*.png")), key=frame_number)
    if len(paths) < 3:
        sys.exit(f"need at least three source frames in {args.frames}, found {len(paths)}")
    frames = [Image.open(p).convert("RGBA") for p in paths]
    if len({f.size for f in frames}) != 1 or frames[0].size[0] != frames[0].size[1]:
        sys.exit("source frames must be square and share one size")
    stack = np.stack([np.asarray(f, dtype=np.float32) for f in frames])

    alpha_spread = (stack[..., 3].max(axis=0) - stack[..., 3].min(axis=0)).max()
    if alpha_spread > 2:
        sys.exit(f"tile outlines differ between frames (alpha spread {alpha_spread:.0f}); cannot stack them")

    # The light point covers any pixel in at most a few frames, so the median removes it.
    base = np.median(stack, axis=0)

    red, blue = base[..., 0], base[..., 2]
    wave = smoothstep(blue - red, 60.0, 160.0) * smoothstep(blue, 90.0, 200.0)
    rows = np.where(wave.max(axis=1) > 0.5)[0]
    if rows.size == 0:
        sys.exit("no waveform found in the source frames")
    margin = int(0.02 * wave.shape[0])
    wave[: max(0, rows[0] - margin)] = 0.0
    wave[rows[-1] + margin + 1:] = 0.0

    strong = wave > 0.5
    centres = [
        (np.flatnonzero(strong[:, x]).min() + np.flatnonzero(strong[:, x]).max()) / 2.0
        for x in range(strong.shape[1]) if strong[:, x].sum() > 8
    ]
    centre_y = float(np.median(centres)) / wave.shape[0]

    os.makedirs(OUT_DIR, exist_ok=True)
    base_image = Image.fromarray(np.clip(base + 0.5, 0, 255).astype(np.uint8), "RGBA")
    wave_rgba = np.zeros(base.shape, dtype=np.uint8)
    wave_rgba[..., :3] = 255
    wave_rgba[..., 3] = np.clip(wave * 255.0 + 0.5, 0, 255).astype(np.uint8)
    wave_image = Image.fromarray(wave_rgba, "RGBA")

    base_path = os.path.join(OUT_DIR, "kruxx_startup_base.webp")
    wave_path = os.path.join(OUT_DIR, "kruxx_startup_wave.webp")
    base_bytes = save_webp(base_image, args.size, base_path)
    wave_bytes = save_webp(wave_image, args.size, wave_path)

    print(f"{len(paths)} frames, {frames[0].size[0]} px -> {args.size} px")
    print(f"{os.path.relpath(base_path, ROOT)}: {base_bytes} bytes")
    print(f"{os.path.relpath(wave_path, ROOT)}: {wave_bytes} bytes")
    print(f"waveform centre line at {centre_y:.4f} of the height")

    if os.path.exists(SPLASH_SOURCE):
        match = re.search(r"WAVE_CENTER_Y\s*=\s*([0-9.]+)f", open(SPLASH_SOURCE, encoding="utf-8").read())
        if match and abs(float(match.group(1)) - centre_y) > 0.003:
            print(f"warning: StartupSplash.kt uses WAVE_CENTER_Y = {match.group(1)}; update it", file=sys.stderr)


if __name__ == "__main__":
    main()
