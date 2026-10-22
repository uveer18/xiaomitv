#!/usr/bin/env python3
"""
Generates the key art used by the recommendation shelf cards.

Each recommendation gets one 16:9 poster in the brand hue of its app. The art is
pure geometry (no text, no copyrighted artwork) so the repo stays self-contained
and offline: the card UI overlays the real title, tag and subtitle on top.

    python tools/make_posters.py

Writes PNGs into launcher/app/src/main/res/drawable-nodpi/poster_*.png
"""

import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

W, H = 640, 360
OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "launcher", "app", "src", "main", "res", "drawable-nodpi",
)

# slug, base hue, motif
POSTERS = [
    # Amazon Prime Video - cyan/blue
    ("mirzapur",        199, "horizon"),
    ("panchayat",       193, "arch"),
    ("the_boys",        205, "rings"),
    ("farzi",           187, "diamond"),
    # Hotstar - deep blue
    ("hotstar_cricket", 219, "arc"),
    ("hotstar_arya",    213, "venn"),
    ("hotstar_loki",    225, "rays"),
    ("hotstar_specialops", 207, "waves"),
    # YouTube - red
    ("yt_trending",     358, "triangle"),
    ("yt_news",         352, "globe"),
    ("yt_music",        4,   "waves"),
    ("yt_tech",         345, "grid"),
    # Netflix - red
    ("netflix_stranger", 352, "rings"),
    ("netflix_squid",   358, "triangle"),
    ("netflix_money",   344, "arch"),
    ("netflix_kota",    5,   "grid"),
    # SonyLIV - orange
    ("sony_scam",       24,  "arc"),
    ("sony_gullak",     14,  "venn"),
    ("sony_rocket",     30,  "rays"),
    ("sony_sharktank",  8,   "horizon"),
    # Extra app - magenta
    ("did_taj",         332, "arch"),
    ("did_bandaa",      326, "diamond"),
    ("did_sunflower",   340, "disc"),
    ("did_saregamapa",  318, "waves"),
    # Generic fallback shelf
    ("generic_launch",  215, "disc"),
    ("generic_info",    205, "grid"),
    ("generic_actions", 222, "arc"),
    ("generic_customize", 210, "triangle"),
]


def hsv(h, s, v):
    """HSV (0-360, 0-1, 0-1) -> RGB tuple."""
    h = h % 360 / 60.0
    i = int(h) % 6
    f = h - int(h)
    p = v * (1 - s)
    q = v * (1 - f * s)
    t = v * (1 - (1 - f) * s)
    return [(v, t, p), (q, v, p), (p, v, t),
            (p, q, v), (t, p, v), (v, p, q)][i % 6]


def rgba(h, s, v, a=255):
    r, g, b = hsv(h, s, v)
    return (int(r * 255), int(g * 255), int(b * 255), int(a))


def vertical_gradient(hue):
    """Deep, rich base: saturated at the top, near black at the bottom."""
    img = Image.new("RGB", (1, H))
    px = img.load()
    for y in range(H):
        t = y / (H - 1)
        # ease the falloff so the bottom reads as deep shadow
        e = t ** 1.25
        s = 0.70 - 0.36 * e
        v = 0.33 - 0.262 * e
        px[0, y] = tuple(int(c * 255) for c in hsv(hue, s, v))
    return img.resize((W, H), Image.BILINEAR)


def soft_light(img, hue, cx, cy, radius, strength):
    """A blurred radial bloom painted over the base."""
    layer = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(layer)
    d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius],
              fill=int(255 * strength))
    layer = layer.filter(ImageFilter.GaussianBlur(radius * 0.55))
    glow = Image.new("RGB", (W, H), tuple(int(c * 255) for c in hsv(hue, 0.75, 0.95)))
    out = img.copy()
    out.paste(glow, (0, 0), layer)
    return out


def sheen(img):
    """A wide, faint diagonal band so the art does not read as flat vinyl."""
    layer = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(layer)
    d.polygon([(-120, H), (W * 0.35, -40), (W * 0.62, -40), (W * 0.15, H)], fill=26)
    layer = layer.filter(ImageFilter.GaussianBlur(26))
    out = img.copy()
    out.paste(Image.new("RGB", (W, H), (255, 255, 255)), (0, 0), layer)
    return out


def vignette(img):
    layer = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(layer)
    d.ellipse([-W * 0.30, -H * 0.55, W * 1.30, H * 1.55], fill=255)
    layer = layer.filter(ImageFilter.GaussianBlur(120)).point(lambda v: 255 - v)
    out = img.copy()
    out.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 0), layer.point(lambda v: int(v * 0.55)))
    return out


# --- motifs ---------------------------------------------------------------------------------

def motif_horizon(d, hue, rng):
    """A low sun over a soft horizon line - no hard edges."""
    cx, cy, r = W * 0.76, H * 0.46, H * 0.30
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=rgba(hue + 14, 0.55, 0.98, 54))
    for i in range(4):
        yy = cy + i * (H * 0.075)
        half = W * (0.34 - i * 0.07)
        d.rectangle([cx - half, yy, cx + half, yy + 2], fill=rgba(hue + 20, 0.70, 0.92, 80 - i * 18))
    d.line([(0, cy), (W, cy)], fill=rgba(hue + 24, 0.80, 0.95, 70), width=2)


def motif_arch(d, hue, rng):
    x0, y0, x1, y1 = W * 0.60, H * 0.10, W * 0.95, H * 0.90
    d.rounded_rectangle([x0, y0, x1, y1], radius=int((x1 - x0) * 0.48),
                        fill=rgba(hue + 12, 0.60, 0.85, 44))
    d.rounded_rectangle([x0 + 26, y0 + 26, x1 - 26, y1], radius=int((x1 - x0 - 52) * 0.48),
                        outline=rgba(hue + 30, 0.80, 1.0, 120), width=3)


def motif_rings(d, hue, rng):
    cx, cy = W * 0.72, H * 0.50
    for i in range(5):
        r = H * (0.16 + i * 0.115)
        d.ellipse([cx - r, cy - r, cx + r, cy + r],
                  outline=rgba(hue + i * 6, 0.75, 0.95, 120 - i * 16),
                  width=max(2, 8 - i * 2))


def motif_diamond(d, hue, rng):
    cx, cy, s = W * 0.74, H * 0.50, H * 0.40
    pts = [(cx, cy - s), (cx + s, cy), (cx, cy + s), (cx - s, cy)]
    d.polygon(pts, fill=rgba(hue + 10, 0.60, 0.80, 60))
    s2 = s * 0.52
    pts2 = [(cx, cy - s2), (cx + s2, cy), (cx, cy + s2), (cx - s2, cy)]
    d.polygon(pts2, outline=rgba(hue + 26, 0.85, 1.0, 130), width=3)


def motif_arc(d, hue, rng):
    cx, cy, r = W * 0.70, H * 0.58, H * 0.52
    d.pieslice([cx - r, cy - r, cx + r, cy + r], start=195, end=345,
               fill=rgba(hue + 8, 0.62, 0.82, 58))
    d.pieslice([cx - r, cy - r, cx + r, cy + r], start=195, end=345,
               outline=rgba(hue + 28, 0.85, 1.0, 120), width=3)


def motif_venn(d, hue, rng):
    r = H * 0.36
    c1 = (W * 0.66, H * 0.50)
    c2 = (W * 0.86, H * 0.50)
    d.ellipse([c1[0] - r, c1[1] - r, c1[0] + r, c1[1] + r],
              outline=rgba(hue + 16, 0.80, 0.98, 120), width=4)
    d.ellipse([c2[0] - r, c2[1] - r, c2[0] + r, c2[1] + r],
              outline=rgba(hue - 14, 0.80, 0.98, 120), width=4)


def motif_rays(d, hue, rng):
    cx, cy = W * 0.86, H * 0.30
    for i in range(14):
        a = math.radians(i * (360 / 14) + 8)
        r0, r1 = H * 0.10, H * 0.95
        d.line([(cx + r0 * math.cos(a), cy + r0 * math.sin(a)),
                (cx + r1 * math.cos(a), cy + r1 * math.sin(a))],
               fill=rgba(hue + 12, 0.70, 0.95, 34), width=9)


def motif_waves(d, hue, rng):
    for i in range(4):
        base = H * (0.28 + i * 0.16)
        amp = H * (0.07 + 0.02 * i)
        pts = []
        for x in range(0, W + 8, 8):
            pts.append((x, base + math.sin(x / (W / 2.2) * math.pi + i * 0.8) * amp))
        d.line(pts, fill=rgba(hue + i * 8, 0.75, 0.96, 105 - i * 14),
               width=max(2, 7 - i * 2), joint="curve")


def motif_triangle(d, hue, rng):
    cx, cy, s = W * 0.76, H * 0.50, H * 0.40
    d.polygon([(cx, cy - s), (cx + s * 0.9, cy + s * 0.7), (cx - s * 0.9, cy + s * 0.7)],
              fill=rgba(hue + 10, 0.62, 0.85, 58))
    s2 = s * 0.5
    d.polygon([(cx, cy - s2), (cx + s2 * 0.9, cy + s2 * 0.7), (cx - s2 * 0.9, cy + s2 * 0.7)],
              outline=rgba(hue + 28, 0.85, 1.0, 125), width=3)


def motif_grid(d, hue, rng):
    cols, rows = 5, 3
    cw, ch = W * 0.10, H * 0.24
    ox, oy = W * 0.60, H * 0.16
    filled = {(0, 1), (2, 0), (3, 2), (4, 1)}
    for r in range(rows):
        for c in range(cols):
            x0 = ox + c * cw * 1.28
            y0 = oy + r * ch * 1.18
            box = [x0, y0, x0 + cw, y0 + ch]
            if (c, r) in filled:
                d.rectangle(box, fill=rgba(hue + 14, 0.60, 0.88, 70))
            else:
                d.rectangle(box, outline=rgba(hue + 20, 0.80, 0.96, 95), width=2)


def motif_disc(d, hue, rng):
    cx, cy, r = W * 0.72, H * 0.48, H * 0.34
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=rgba(hue + 8, 0.60, 0.85, 62))
    r2 = H * 0.16
    d.ellipse([cx - r2 - W * 0.30, cy - r2, cx - r2 - W * 0.30 + r2 * 2, cy + r2],
              fill=rgba(hue + 30, 0.70, 0.95, 46))


def motif_globe(d, hue, rng):
    cx, cy, r = W * 0.76, H * 0.48, H * 0.30
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=rgba(hue + 18, 0.80, 0.98, 120), width=4)
    d.ellipse([cx - r * 0.45, cy - r, cx + r * 0.45, cy + r],
              outline=rgba(hue + 18, 0.80, 0.98, 80), width=3)
    d.line([(cx - r, cy), (cx + r, cy)], fill=rgba(hue + 18, 0.80, 0.98, 80), width=3)
    d.arc([cx - r, cy - r * 1.9, cx + r, cy + r * 0.1], 0, 360,
          fill=rgba(hue + 18, 0.70, 0.95, 45), width=2)


MOTIFS = {
    "horizon": motif_horizon, "arch": motif_arch, "rings": motif_rings,
    "diamond": motif_diamond, "arc": motif_arc, "venn": motif_venn,
    "rays": motif_rays, "waves": motif_waves, "triangle": motif_triangle,
    "grid": motif_grid, "disc": motif_disc, "globe": motif_globe,
}


def build(slug, hue, motif, seed):
    rng = random.Random(seed)
    img = vertical_gradient(hue)
    img = soft_light(img, hue + 10, W * 0.78, H * 0.16, H * 0.62, 0.55)
    img = soft_light(img, hue - 18, W * 0.18, H * 0.92, H * 0.70, 0.30)

    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    MOTIFS[motif](d, hue, rng)
    img = Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")

    img = sheen(img)
    img = vignette(img)

    # Keep the file small: the art is smooth gradients, so 96 colours is plenty.
    q = img.quantize(colors=96, method=Image.MEDIANCUT, dither=Image.FLOYDSTEINBERG)
    path = os.path.join(OUT, "poster_%s.png" % slug)
    q.save(path, optimize=True)
    return path, os.path.getsize(path)


def main():
    os.makedirs(OUT, exist_ok=True)
    total = 0
    for i, (slug, hue, motif) in enumerate(POSTERS):
        path, size = build(slug, hue, motif, 1000 + i)
        total += size
        print("%-24s %6.1f KB" % (os.path.basename(path), size / 1024.0))
    print("total %.1f KB in %d files" % (total / 1024.0, len(POSTERS)))


if __name__ == "__main__":
    main()
