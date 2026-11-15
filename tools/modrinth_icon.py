"""Builds the Modrinth project icon (512x512) in the style of Create's own icon: a round blueprint
badge with the mod's Brewing Kettle item on it.

The kettle is the real in-game item render, cut out of a magenta-keyed screenshot and kept as
docs/modrinth/img/kettle_render.png. Pass the screenshot path to refresh it:
    python tools/modrinth_icon.py [run/client/screenshots/icon_kettle.png]
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

OUT = Path(__file__).resolve().parent.parent / "docs/modrinth/img"
RENDER = OUT / "kettle_render.png"
SIZE = 512
SS = 2  # supersampling for the smooth circle

# Colours measured from Create's own icon background.
FILL = (83, 151, 210)
DARK_EDGE = (55, 110, 158)
HIGHLIGHT = (118, 190, 236)
GRID = (104, 178, 232)
RIM = (255, 255, 255)
GRID_LINES = (0.25, 0.535, 0.82)  # line positions as a share of the diameter


def cut_out(screenshot):
    """Removes the magenta key and crops to the item."""
    im = Image.open(screenshot).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, _ = px[x, y]
            if r > 200 and b > 200 and g < 60:
                px[x, y] = (0, 0, 0, 0)
    return im.crop(im.getbbox())


def badge():
    """Create-style round badge: flat blue, darker edge band, soft top-left highlight, a sparse
    light grid and a thin white rim; transparent outside."""
    n = SIZE * SS
    c = n / 2
    r_out = n / 2 - 2 * SS
    r_in = r_out - 7 * SS
    disc = Image.new("RGBA", (n, n), DARK_EDGE + (255,))
    inner = Image.new("L", (n, n), 0)
    ri = r_in * 0.83
    ImageDraw.Draw(inner).ellipse([c - ri, c - ri, c + ri, c + ri], fill=255)
    disc.paste(Image.new("RGBA", (n, n), FILL + (255,)), (0, 0), inner.filter(ImageFilter.GaussianBlur(n * 0.035)))
    glow = Image.new("L", (n, n), 0)
    hx, hy, hr = n * 0.35, n * 0.32, n * 0.17
    ImageDraw.Draw(glow).ellipse([hx - hr, hy - hr, hx + hr, hy + hr], fill=255)
    disc.paste(Image.new("RGBA", (n, n), HIGHLIGHT + (255,)), (0, 0), glow.filter(ImageFilter.GaussianBlur(n * 0.07)))
    lines = Image.new("L", (n, n), 0)
    ld = ImageDraw.Draw(lines)
    w = n * 0.026
    for f in GRID_LINES:
        p = n * f
        ld.rectangle([p - w / 2, 0, p + w / 2, n], fill=150)
        ld.rectangle([0, p - w / 2, n, p + w / 2], fill=150)
    disc.paste(Image.new("RGBA", (n, n), GRID + (255,)), (0, 0), lines.filter(ImageFilter.GaussianBlur(SS)))
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    d.ellipse([c - r_out, c - r_out, c + r_out, c + r_out], fill=RIM + (255,))
    mask = Image.new("L", (n, n), 0)
    ImageDraw.Draw(mask).ellipse([c - r_in, c - r_in, c + r_in, c + r_in], fill=255)
    out.paste(disc, (0, 0), mask)
    return out.resize((SIZE, SIZE), Image.LANCZOS)


def sticker(item, width):
    """The item with a white outline around its silhouette, like the items on Create add-on icons."""
    alpha = item.split()[3]
    pad = width + 2
    big = Image.new("L", (item.width + 2 * pad, item.height + 2 * pad), 0)
    big.paste(alpha, (pad, pad))
    outline = big.filter(ImageFilter.MaxFilter(2 * width + 1))
    out = Image.new("RGBA", big.size, (0, 0, 0, 0))
    out.paste((255, 255, 255, 255), (0, 0), outline)
    out.alpha_composite(item, (pad, pad))
    return out


def main():
    if len(sys.argv) > 1:
        cut_out(sys.argv[1]).save(RENDER)
    item = Image.open(RENDER).convert("RGBA")
    alpha = item.split()[3]
    item = ImageEnhance.Contrast(ImageEnhance.Brightness(item.convert("RGB")).enhance(1.3)).enhance(1.1).convert("RGBA")
    item.putalpha(alpha)
    icon = badge()
    target = int(SIZE * 0.66)
    scale = target / max(item.size)
    item = item.resize((round(item.width * scale), round(item.height * scale)), Image.NEAREST)
    item = sticker(item, 7)
    x = (SIZE - item.width) // 2
    y = (SIZE - item.height) // 2 + 6
    icon.alpha_composite(item, (x, y))
    icon.save(OUT / "icon.png")


if __name__ == "__main__":
    main()
