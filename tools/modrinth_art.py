"""Draws the Modrinth page art (banner, section headlines, divider) from the mod's own sprites.

Everything is drawn on a small pixel canvas and scaled up with nearest-neighbour, so it matches the
mod's pixel art. Output: docs/modrinth/img/*.png. Run: python tools/modrinth_art.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

from pixel_font import render as font_mask

ROOT = Path(__file__).resolve().parent.parent
ITEMS = ROOT / "src/main/resources/assets/brewery/textures/item"
LOGO = ROOT / "src/main/resources/brewery_logo.png"
OUT = ROOT / "docs/modrinth/img"

# Warm tavern palette: dark oak, brass, amber.
OAK_DARK = (43, 29, 20)
OAK = (66, 45, 30)
OAK_LIGHT = (92, 63, 41)
BRASS_DARK = (122, 82, 34)
BRASS = (201, 146, 62)
BRASS_LIGHT = (246, 205, 118)
CREAM = (250, 236, 205)
SHADOW = (20, 12, 8)


def sprite(name, size):
    im = Image.open(ITEMS / f"{name}.png").convert("RGBA")
    return im.resize((size, size), Image.NEAREST)


def planks(w, h):
    """Dark oak planks: horizontal boards with seams and a little grain."""
    im = Image.new("RGBA", (w, h), OAK)
    d = ImageDraw.Draw(im)
    board = 8
    for y in range(0, h, board):
        shade = OAK if (y // board) % 2 == 0 else (62, 42, 28)
        d.rectangle([0, y, w, y + board - 1], fill=shade)
        d.line([0, y + board - 1, w, y + board - 1], fill=OAK_DARK)
        offset = 37 * (y // board) % 53
        for x in range(offset, w, 53):
            d.line([x, y, x, y + board - 2], fill=OAK_DARK)
        for x in range((offset * 7) % 19, w, 19):
            d.point((x, y + 3), fill=OAK_LIGHT)
    return im


def brass_frame(im, inset=0):
    d = ImageDraw.Draw(im)
    w, h = im.size
    d.rectangle([inset, inset, w - 1 - inset, h - 1 - inset], outline=BRASS_DARK)
    d.rectangle([inset + 1, inset + 1, w - 2 - inset, h - 2 - inset], outline=BRASS)
    d.line([inset + 1, inset + 1, w - 2 - inset, inset + 1], fill=BRASS_LIGHT)
    for x, y in ((inset + 4, inset + 4), (w - 5 - inset, inset + 4),
                 (inset + 4, h - 5 - inset), (w - 5 - inset, h - 5 - inset)):
        d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=BRASS_DARK)
        d.point((x - 1, y - 1), fill=BRASS_LIGHT)


def draw_text(canvas, mask, x, top, fill_top=BRASS_LIGHT, fill_bottom=BRASS):
    """Paints a font mask with a vertical brass gradient and a one-pixel drop shadow."""
    w, h = mask.size
    canvas.paste(Image.new("RGBA", mask.size, SHADOW), (x + 1, top + 1), mask)
    gradient = Image.new("RGBA", mask.size)
    gd = ImageDraw.Draw(gradient)
    for y in range(h):
        t = y / max(1, h - 1)
        gd.line([0, y, w, y], fill=tuple(int(a + (b - a) * t) for a, b in zip(fill_top, fill_bottom)) + (255,))
    canvas.paste(gradient, (x, top), mask)


def plaque(size, inner):
    """A small stone tile in a brass frame with a sprite in the middle and five stars below it."""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([1, 1, size - 2, size - 2], fill=(118, 116, 112))
    for i in range(0, size * size, 7):
        x, y = (i * 13) % size, (i * 29) % size
        if 2 < x < size - 3 and 2 < y < size - 3:
            d.point((x, y), fill=(98, 96, 93) if i % 2 else (138, 135, 130))
    brass_frame(im)
    im.alpha_composite(inner, ((size - inner.size[0]) // 2, 3))
    star = ["00100", "11111", "01110", "01010"]
    total = 5 * 6 - 1
    for n in range(5):
        x0 = (size - total) // 2 + n * 6
        for yy, row in enumerate(star):
            for xx, bit in enumerate(row):
                if bit == "1":
                    d.point((x0 + xx, size - 9 + yy), fill=BRASS_LIGHT if yy < 2 else BRASS)
    return im


def banner():
    w, h, scale = 320, 80, 5
    im = planks(w, h)
    brass_frame(im)
    beer = plaque(50, sprite("beer_bottle", 32))
    im.alpha_composite(beer, (10, 15))
    im.alpha_composite(beer, (w - 60, 15))
    title = font_mask("CREATE BREWERY", 2)
    draw_text(im, title, (w - title.size[0]) // 2, 12)
    tag = font_mask("BREW - DISTILL - AGE - TOAST", 1)
    tx = (w - tag.size[0]) // 2
    im.paste(Image.new("RGBA", tag.size, SHADOW), (tx + 1, 35), tag)
    im.paste(Image.new("RGBA", tag.size, CREAM), (tx, 34), tag)
    shelf = ["beer", "stout", "kvass", "cider", "mead", "wine", "whiskey", "rum", "calvados", "absinthe"]
    x0 = w // 2 - len(shelf) * 16 // 2
    ImageDraw.Draw(im).rectangle([x0 - 3, 70, x0 + len(shelf) * 16 + 2, 71], fill=BRASS_DARK)
    for i, name in enumerate(shelf):
        im.alpha_composite(sprite(f"{name}_bottle", 16), (x0 + i * 16, 54))
    return im.resize((w * scale, h * scale), Image.NEAREST)


def headline(text, icon):
    w, h, scale = 232, 24, 5
    im = planks(w, h)
    brass_frame(im)
    im.alpha_composite(icon, (6, 4))
    mask = font_mask(text, 2)
    draw_text(im, mask, 28, (h - mask.size[1]) // 2)
    return im.resize((w * scale, h * scale), Image.NEAREST)


def divider():
    w, h, scale = 320, 8, 5
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for x in range(40, w - 40):
        fade = min(x - 40, w - 41 - x, 40) / 40
        c = tuple(int(BRASS_DARK[i] + (BRASS[i] - BRASS_DARK[i]) * fade) for i in range(3))
        d.point((x, 3), fill=c + (int(255 * fade),))
        d.point((x, 4), fill=BRASS_DARK + (int(200 * fade),))
    im.alpha_composite(sprite("mug", 8), (w // 2 - 4, 0))
    return im.resize((w * scale, h * scale), Image.NEAREST)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    banner().save(OUT / "banner.png")
    divider().save(OUT / "divider.png")
    sections = {
        "about": ("About", sprite("brewer_book", 16)),
        "machines": ("Machines", sprite("distillate", 16)),
        "drinks": ("Drinks", sprite("wine_bottle", 16)),
        "drunk": ("Being drunk", sprite("spoiled_bottle", 16)),
        "friends": ("Brewers & friends", sprite("drink_mug", 16)),
        "datapacks": ("Data packs", sprite("mash_bottle", 16)),
        "config": ("Server config", sprite("whiskey_bottle", 16)),
        "requirements": ("Requirements", sprite("beer_bottle", 16)),
        "gallery": ("Gallery", sprite("mead_bottle", 16)),
    }
    for key, (text, icon) in sections.items():
        headline(text, icon).save(OUT / f"h_{key}.png")


if __name__ == "__main__":
    main()
