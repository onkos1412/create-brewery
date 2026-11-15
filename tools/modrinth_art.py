"""Draws the Modrinth page art (banner, section headlines, divider) from the mod's own sprites.

Everything is drawn on a small pixel canvas and scaled up with nearest-neighbour, so it matches the
mod's pixel art. Output: docs/modrinth/img/*.png. Run: python tools/modrinth_art.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ITEMS = ROOT / "src/main/resources/assets/brewery/textures/item"
LOGO = ROOT / "src/main/resources/brewery_logo.png"
OUT = ROOT / "docs/modrinth/img"
FONT = "C:/Windows/Fonts/arialbd.ttf"

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


def pixel_text(text, size):
    """Text rendered without anti-aliasing, as a 1-bit mask, so it scales up as crisp pixels."""
    font = ImageFont.truetype(FONT, size)
    left, top, right, bottom = font.getbbox(text)
    mask = Image.new("1", (right - left + 2, bottom - top + 2), 0)
    ImageDraw.Draw(mask).text((-left, -top), text, font=font, fill=1)
    return mask


def draw_text(canvas, text, size, center_x, top, fill_top=BRASS_LIGHT, fill_bottom=BRASS):
    mask = pixel_text(text, size)
    w, h = mask.size
    x = center_x - w // 2 if center_x is not None else 0
    shadow = Image.new("RGBA", mask.size, SHADOW)
    canvas.paste(shadow, (x + 1, top + 1), mask)
    gradient = Image.new("RGBA", mask.size)
    gd = ImageDraw.Draw(gradient)
    for y in range(h):
        t = y / max(1, h - 1)
        gd.line([0, y, w, y], fill=tuple(int(a + (b - a) * t) for a, b in zip(fill_top, fill_bottom)) + (255,))
    canvas.paste(gradient, (x, top), mask)
    return w, h


def banner():
    w, h, scale = 320, 80, 5
    im = planks(w, h)
    brass_frame(im)
    logo = Image.open(LOGO).convert("RGBA").resize((48, 48), Image.NEAREST)
    im.alpha_composite(logo, (10, 16))
    im.alpha_composite(logo.transpose(Image.FLIP_LEFT_RIGHT), (w - 58, 16))
    draw_text(im, "CREATE BREWERY", 17, w // 2, 14)
    tag = pixel_text("BREW  -  DISTILL  -  AGE  -  TOAST", 9)
    im.paste(Image.new("RGBA", tag.size, SHADOW), (w // 2 - tag.size[0] // 2 + 1, 41), tag)
    im.paste(Image.new("RGBA", tag.size, CREAM), (w // 2 - tag.size[0] // 2, 40), tag)
    shelf = ["beer", "stout", "kvass", "cider", "mead", "wine", "whiskey", "rum", "calvados", "absinthe"]
    x0 = w // 2 - len(shelf) * 16 // 2
    ImageDraw.Draw(im).rectangle([x0 - 3, 70, x0 + len(shelf) * 16 + 2, 71], fill=BRASS_DARK)
    for i, name in enumerate(shelf):
        im.alpha_composite(sprite(f"{name}_bottle", 16), (x0 + i * 16, 54))
    return im.resize((w * scale, h * scale), Image.NEAREST)


def headline(text, icon):
    w, h, scale = 160, 24, 5
    im = planks(w, h)
    brass_frame(im)
    im.alpha_composite(icon, (6, 4))
    mask = pixel_text(text, 12)
    top = (h - mask.size[1]) // 2
    im.paste(Image.new("RGBA", mask.size, SHADOW), (27, top + 1), mask)
    im.paste(Image.new("RGBA", mask.size, BRASS_LIGHT), (26, top), mask)
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
