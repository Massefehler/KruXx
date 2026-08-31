#!/usr/bin/env python3
"""
Generate all launcher-icon resources for the "kruxx" flavor from ONE source image.

    scripts/make-kruxx-icon.py ICON [--bg '#RRGGBB'] [--mode auto|glyph|cover]
                                    [--wordmark KruXx] [--font FILE.ttf] [--no-wordmark]

ICON      square PNG (ideally 1024x1024) or SVG.
--mode    glyph : ICON is a symbol on a transparent background -> centred in the adaptive
                  icon's safe zone on a solid --bg colour (default when corners are transparent)
          cover : ICON is a full-bleed square design -> stretched over the whole adaptive canvas
                  (default when corners are opaque); --bg defaults to the average border colour
--bg      background colour of the adaptive icon (and of legacy/TV icons)
--wordmark text rendered as white-on-transparent header logo (replaces Kreate's "app_logo_text")

Writes into composeApp/src/androidKruxx/res/ (flavor resources override the main ones):
  mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml     adaptive icon (API 26+)
  mipmap-*dpi/kruxx_ic_launcher_foreground.png (108dp)         adaptive foreground layer
  mipmap-*dpi/kruxx_ic_launcher_monochrome.png (108dp)         themed-icon layer (Android 13+)
  values/colors.xml (kruxx_ic_launcher_background)             adaptive background colour
  mipmap-*dpi/ic_launcher.png, ic_launcher_round.png (48dp)    legacy icons (API < 26)
  mipmap-*dpi/ic_banner.png (320x180dp)                         Android TV banner
  drawable-*dpi/app_icon_monochrome.png (24dp)                  notification / settings glyph
  drawable/app_logo_text.png                                    header wordmark (tinted by the app)
Requires Pillow; SVG input additionally needs ImageMagick (`convert`).
"""
import argparse, os, shutil, subprocess, sys, tempfile
from PIL import Image, ImageDraw, ImageFont, ImageOps, ImageStat

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "composeApp", "src", "androidKruxx", "res")
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf",
    "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
    "/usr/share/fonts/truetype/ubuntu/Ubuntu-B.ttf",
]


def load_source(path: str) -> Image.Image:
    if path.lower().endswith(".svg"):
        tmp = tempfile.NamedTemporaryFile(suffix=".png", delete=False).name
        subprocess.run(["convert", "-background", "none", "-density", "384", path,
                        "-resize", "1024x1024", tmp], check=True)
        path = tmp
    img = Image.open(path).convert("RGBA")
    # make it square (pad with transparency), then normalise to 1024
    side = max(img.size)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    return canvas.resize((1024, 1024), Image.LANCZOS)


def key_border(img: Image.Image, tolerance: int = 48) -> tuple[Image.Image, str] | None:
    """
    Many icons are an opaque rounded tile on a plain (often black) background. Flood-fill that
    background from the four corners into transparency so the tile becomes a real glyph.
    Returns (image, keyed colour) or None when the corners are not a uniform opaque colour.
    """
    w, h = img.size
    corners = [img.getpixel(p) for p in [(1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)]]
    if any(c[3] < 250 for c in corners):
        return None
    ref = corners[0][:3]
    if any(sum(abs(a - b) for a, b in zip(c[:3], ref)) > 24 for c in corners):
        return None
    from PIL import ImageDraw as _ID
    work = img.copy()
    # Fill straight to alpha 0. (Pillow's floodfill returns early when the fill colour is within
    # `thresh` of the seed colour, so the fill value must differ clearly from the background.)
    transparent = (ref[0], ref[1], ref[2], 0)
    for seed in [(1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)]:
        if work.getpixel(seed)[3] != 0:
            _ID.floodfill(work, seed, transparent, thresh=tolerance)
    return work, "#%02X%02X%02X" % ref


def luminance_mask(img: Image.Image, threshold: int | None) -> Image.Image:
    """White-on-transparent mask of the bright parts of an opaque design (Otsu threshold by default)."""
    luma = ImageOps.grayscale(img)
    alpha = img.split()[3]
    if threshold is None:
        hist = luma.histogram()
        # ignore transparent pixels
        masked = Image.composite(luma, Image.new("L", luma.size, 0), alpha.point(lambda a: 255 if a > 128 else 0))
        hist = masked.histogram(); hist[0] = 0
        total = sum(hist); sumB = 0; wB = 0; maximum = 0.0; threshold = 128
        sum1 = sum(i * hist[i] for i in range(256))
        for i in range(256):
            wB += hist[i]
            if wB == 0: continue
            wF = total - wB
            if wF == 0: break
            sumB += i * hist[i]
            mB = sumB / wB; mF = (sum1 - sumB) / wF
            between = wB * wF * (mB - mF) ** 2
            if between > maximum: maximum = between; threshold = i
    mask = luma.point(lambda v: 255 if v > threshold else 0)
    from PIL import ImageFilter
    k = max(3, (img.width // 90) | 1)  # odd kernel, ~1% of the image
    mask = mask.filter(ImageFilter.MaxFilter(k)).filter(ImageFilter.MinFilter(k))  # closing
    return Image.composite(mask, Image.new("L", mask.size, 0), alpha.point(lambda a: 255 if a > 128 else 0))


def corners_transparent(img: Image.Image) -> bool:
    w, h = img.size
    pts = [(2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)]
    return all(img.getpixel(p)[3] < 16 for p in pts)


def border_colour(img: Image.Image) -> str:
    w, h = img.size
    border = Image.new("RGBA", img.size, (0, 0, 0, 0))
    m = 8
    for box in [(0, 0, w, m), (0, h - m, w, h), (0, 0, m, h), (w - m, 0, w, h)]:
        border.paste(img.crop(box), box[:2])
    rgb = border.convert("RGB")
    mask = border.split()[3].point(lambda a: 255 if a > 128 else 0)
    stat = ImageStat.Stat(rgb, mask)
    r, g, b = (int(round(c)) for c in stat.mean)
    return "#%02X%02X%02X" % (r, g, b)


def parse_colour(s: str):
    s = s.lstrip("#")
    if len(s) == 6:
        return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
    raise SystemExit(f"invalid colour {s!r}, expected #RRGGBB")


def fit(img: Image.Image, size: int) -> Image.Image:
    return img.resize((size, size), Image.LANCZOS)


def px(dp: float, scale: float) -> int:
    return int(round(dp * scale))


def rounded_mask(size: int, radius_ratio: float) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * radius_ratio), fill=255)
    return mask


def circle_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    return mask


def compose(base: Image.Image, mode: str, bg, canvas_px: int, glyph_ratio: float) -> Image.Image:
    """Full 108dp adaptive canvas at the given pixel size."""
    canvas = Image.new("RGBA", (canvas_px, canvas_px), bg)
    if mode == "cover":
        canvas.alpha_composite(fit(base, canvas_px))
    else:
        g = fit(base, int(canvas_px * glyph_ratio))
        off = (canvas_px - g.width) // 2
        canvas.alpha_composite(g, (off, off))
    return canvas


def monochrome(base: Image.Image, canvas_px: int, glyph_ratio: float, use_alpha: bool,
               threshold: int | None) -> Image.Image:
    """White glyph with alpha, for Android 13 themed icons / notification icon (system tints it)."""
    g = fit(base, int(canvas_px * glyph_ratio))
    alpha = g.split()[3] if use_alpha else luminance_mask(g, threshold)
    out = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    white = Image.new("RGBA", alpha.size, (255, 255, 255, 255))
    white.putalpha(alpha)
    off = (canvas_px - white.width) // 2
    out.alpha_composite(white, (off, off))
    return out


def text_glyph(text: str, font_path: str | None, canvas_px: int, ratio: float) -> Image.Image:
    """Bold white letter(s) centred on a transparent canvas - a clean monochrome fallback for
    glossy/3D artwork that has no usable silhouette (themed icons, notification icon)."""
    font_path = font_path or next((f for f in FONT_CANDIDATES if os.path.exists(f)), None)
    if not font_path:
        raise SystemExit("no bold TTF font found; pass --font FILE.ttf")
    out = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    draw = ImageDraw.Draw(out)
    target = canvas_px * ratio
    fs = int(target)
    while fs > 8:
        font = ImageFont.truetype(font_path, fs)
        l, t, r, b = draw.textbbox((0, 0), text, font=font)
        if max(r - l, b - t) <= target:
            break
        fs -= 4
    x = (canvas_px - (r - l)) // 2 - l
    y = (canvas_px - (b - t)) // 2 - t
    draw.text((x, y), text, font=font, fill=(255, 255, 255, 255))
    return out


def wordmark(text: str, font_path: str | None, size=(1429, 512)) -> Image.Image:
    font_path = font_path or next((f for f in FONT_CANDIDATES if os.path.exists(f)), None)
    if not font_path:
        raise SystemExit("no bold TTF font found; pass --font FILE.ttf")
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    fs = size[1]
    while fs > 10:
        font = ImageFont.truetype(font_path, fs)
        l, t, r, b = draw.textbbox((0, 0), text, font=font)
        if r - l <= size[0] * 0.96 and b - t <= size[1] * 0.92:
            break
        fs -= 8
    x = (size[0] - (r - l)) // 2 - l
    y = (size[1] - (b - t)) // 2 - t
    draw.text((x, y), text, font=font, fill=(255, 255, 255, 255))
    return img


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("icon")
    ap.add_argument("--bg")
    ap.add_argument("--mode", choices=["auto", "glyph", "cover"], default="auto")
    ap.add_argument("--glyph-ratio", type=float, default=0.62,
                    help="glyph size relative to the 108dp canvas (safe zone is 66dp = 0.61)")
    ap.add_argument("--wordmark", default="KruXx")
    ap.add_argument("--no-wordmark", action="store_true")
    ap.add_argument("--font")
    ap.add_argument("--no-key-border", action="store_true",
                    help="don't turn a uniform opaque border colour (e.g. black corners around a tile) transparent")
    ap.add_argument("--mono-threshold", type=int,
                    help="luminance 0-255 above which opaque designs count as 'glyph' for the monochrome layers (default: Otsu)")
    ap.add_argument("--mono", choices=["auto", "alpha", "luminance", "text", "none"], default="auto",
                    help="source of the monochrome layers (themed icon, notification): alpha of a transparent glyph, "
                         "luminance of an opaque design, a bold text glyph (--mono-text), or none. "
                         "auto = alpha for transparent sources, text for opaque/keyed tiles")
    ap.add_argument("--mono-text", help="text for --mono text (default: first letter of the wordmark)")
    args = ap.parse_args()

    base = load_source(args.icon)
    keyed_colour = None
    if not args.no_key_border and not corners_transparent(base):
        keyed = key_border(base)
        if keyed:
            base, keyed_colour = keyed
            print(f"keyed uniform border colour {keyed_colour} to transparent")
    mode = args.mode
    if mode == "auto":
        mode = "glyph" if corners_transparent(base) else "cover"
    bg_hex = args.bg or keyed_colour or ("#08041D" if mode == "glyph" else border_colour(base))
    bg = parse_colour(bg_hex)
    mono_mode = args.mono
    if mono_mode == "auto":
        mono_mode = "alpha" if (keyed_colour is None and mode == "glyph") else "text"
    mono_text = args.mono_text or (args.wordmark[:1] if args.wordmark else "K")
    print(f"mode={mode} background={bg_hex} source={base.size} mono={mono_mode}" + (f" ({mono_text!r})" if mono_mode == "text" else ""))

    if os.path.isdir(RES):
        for sub in os.listdir(RES):
            if sub.startswith(("mipmap", "drawable", "values")):
                shutil.rmtree(os.path.join(RES, sub))
    for d in DENSITIES:
        os.makedirs(os.path.join(RES, f"mipmap-{d}"), exist_ok=True)
        os.makedirs(os.path.join(RES, f"drawable-{d}"), exist_ok=True)
    os.makedirs(os.path.join(RES, "mipmap-anydpi-v26"), exist_ok=True)
    os.makedirs(os.path.join(RES, "values"), exist_ok=True)
    os.makedirs(os.path.join(RES, "drawable"), exist_ok=True)

    # high-resolution master renders
    full = compose(base, mode, bg, 1024, args.glyph_ratio)                 # 108dp canvas
    fg = compose(base, mode, (0, 0, 0, 0), 1024, args.glyph_ratio)         # foreground only
    if mono_mode == "text":
        mono = text_glyph(mono_text, args.font, 1024, 0.58)
    elif mono_mode == "none":
        mono = None
    else:
        mono = monochrome(base, 1024, args.glyph_ratio, mono_mode == "alpha", args.mono_threshold)
    if keyed_colour is not None:
        # a keyed tile already has its own shape/corners: use it directly for the legacy icons
        legacy_src = base
    else:
        legacy_src = full.crop((int(1024 * 0.14), int(1024 * 0.14), int(1024 * 0.86), int(1024 * 0.86)))  # visible 72dp

    for d, scale in DENSITIES.items():
        mm = os.path.join(RES, f"mipmap-{d}")
        p = px(108, scale)
        fit(fg, p).save(os.path.join(mm, "kruxx_ic_launcher_foreground.png"))
        if mono is not None:
            fit(mono, p).save(os.path.join(mm, "kruxx_ic_launcher_monochrome.png"))

        legacy = fit(legacy_src, px(48, scale))
        sq = legacy.copy()
        if keyed_colour is None:
            sq.putalpha(ImageChops_multiply(legacy.split()[3], rounded_mask(legacy.width, 0.18)))
        sq.save(os.path.join(mm, "ic_launcher.png"))
        rd = legacy.copy(); rd.putalpha(ImageChops_multiply(legacy.split()[3], circle_mask(legacy.width)))
        rd.save(os.path.join(mm, "ic_launcher_round.png"))

        bw, bh = px(320, scale), px(180, scale)
        banner = Image.new("RGBA", (bw, bh), bg)
        icon = fit(legacy_src, int(bh * 0.8))
        banner.alpha_composite(icon, ((bw - icon.width) // 2, (bh - icon.height) // 2))
        banner.save(os.path.join(mm, "ic_banner.png"))

        if mono is not None:
            # notification / settings glyph: the mono master cropped to its visible 72dp area
            fit(mono.crop((int(1024 * 0.14),) * 2 + (int(1024 * 0.86),) * 2), px(24, scale)) \
                .save(os.path.join(RES, f"drawable-{d}", "app_icon_monochrome.png"))

    with open(os.path.join(RES, "values", "colors.xml"), "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                f'    <color name="kruxx_ic_launcher_background">{bg_hex}</color>\n</resources>\n')
    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/kruxx_ic_launcher_background"/>\n'
                '    <foreground android:drawable="@mipmap/kruxx_ic_launcher_foreground"/>\n'
                + ('    <monochrome android:drawable="@mipmap/kruxx_ic_launcher_monochrome"/>\n' if mono is not None else '')
                + '</adaptive-icon>\n')
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(RES, "mipmap-anydpi-v26", name), "w") as f:
            f.write(adaptive)

    if not args.no_wordmark:
        wordmark(args.wordmark, args.font).save(os.path.join(RES, "drawable", "app_logo_text.png"))

    n = sum(len(files) for _, _, files in os.walk(RES))
    print(f"wrote {n} resource files into {os.path.relpath(RES, ROOT)}")


def ImageChops_multiply(a: Image.Image, b: Image.Image) -> Image.Image:
    from PIL import ImageChops
    return ImageChops.multiply(a, b)


if __name__ == "__main__":
    main()
