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

CENTER = (86, 168, 236)
EDGE = (24, 84, 166)
GRID = (150, 205, 250)
RING = (214, 236, 255)
BORDER = (12, 32, 62)


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
    n = SIZE * SS
    c = n / 2
    r_outer = n / 2 - 4 * SS
    border, ring = 9 * SS, 10 * SS
    r_fill = r_outer - border - ring
    bg = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    grad = Image.new("RGBA", (n, n), EDGE + (255,))
    gd = ImageDraw.Draw(grad)
    steps = 120
    for i in range(steps, 0, -1):
        t = i / steps
        col = tuple(int(CENTER[k] + (EDGE[k] - CENTER[k]) * t) for k in range(3))
        rr = r_fill * t
        gd.ellipse([c - rr - n * 0.06, c - rr - n * 0.08, c + rr - n * 0.06, c + rr - n * 0.08], fill=col + (255,))
    grid = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    gr = ImageDraw.Draw(grid)
    step = n / 8
    for k in range(1, 8):
        p = k * step
        gr.line([p, 0, p, n], fill=GRID + (70,), width=3 * SS)
        gr.line([0, p, n, p], fill=GRID + (70,), width=3 * SS)
        gr.line([p - step / 2, 0, p - step / 2, n], fill=GRID + (30,), width=SS)
        gr.line([0, p - step / 2, n, p - step / 2], fill=GRID + (30,), width=SS)
    grad.alpha_composite(grid)
    mask = Image.new("L", (n, n), 0)
    ImageDraw.Draw(mask).ellipse([c - r_fill, c - r_fill, c + r_fill, c + r_fill], fill=255)
    d = ImageDraw.Draw(bg)
    d.ellipse([c - r_outer, c - r_outer, c + r_outer, c + r_outer], fill=BORDER + (255,))
    d.ellipse([c - r_outer + border, c - r_outer + border, c + r_outer - border, c + r_outer - border],
              fill=RING + (255,))
    bg.paste(grad, (0, 0), mask)
    return bg.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    if len(sys.argv) > 1:
        cut_out(sys.argv[1]).save(RENDER)
    item = Image.open(RENDER).convert("RGBA")
    alpha = item.split()[3]
    item = ImageEnhance.Contrast(ImageEnhance.Brightness(item.convert("RGB")).enhance(1.3)).enhance(1.1).convert("RGBA")
    item.putalpha(alpha)
    icon = badge()
    target = int(SIZE * 0.62)
    scale = target / max(item.size)
    item = item.resize((round(item.width * scale), round(item.height * scale)), Image.NEAREST)
    x = (SIZE - item.width) // 2
    y = (SIZE - item.height) // 2 + 4
    shadow = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    shadow.paste((8, 20, 40, 150), (x + 10, y + 16), item.split()[3])
    icon.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(10)))
    icon.alpha_composite(item, (x, y))
    icon.save(OUT / "icon.png")


if __name__ == "__main__":
    main()
