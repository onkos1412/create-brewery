"""Draws the Modrinth page art (banner, section headlines, divider) from the mod's own sprites.

Everything is drawn on a small pixel canvas and scaled up with nearest-neighbour, so it matches the
mod's pixel art. Output: docs/modrinth/img/*.png. Run: python tools/modrinth_art.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

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


def plaque(inner):
    """A stone tile in a brass frame, cut to fit the sprite, with five stars below it."""
    box = inner.getbbox()
    sprite_w, sprite_h = box[2] - box[0], box[3] - box[1]
    w, h = max(sprite_w + 12, 39), sprite_h + 18
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([1, 1, w - 2, h - 2], fill=(118, 116, 112))
    for i in range(0, w * h, 7):
        x, y = (i * 13) % w, (i * 29) % h
        if 2 < x < w - 3 and 2 < y < h - 3:
            d.point((x, y), fill=(98, 96, 93) if i % 2 else (138, 135, 130))
    brass_frame(im)
    im.alpha_composite(inner.crop(box), ((w - sprite_w) // 2, 4))
    star = ["00100", "11111", "01110", "01010"]
    total = 5 * 6 - 1
    for n in range(5):
        x0 = (w - total) // 2 + n * 6
        for yy, row in enumerate(star):
            for xx, bit in enumerate(row):
                if bit == "1":
                    d.point((x0 + xx, h - 11 + yy), fill=BRASS_LIGHT if yy < 2 else BRASS)
    return im


def grow(mask, px):
    """The mask thickened by px pixels in every direction (a square outline)."""
    out = Image.new("1", (mask.size[0] + 2 * px, mask.size[1] + 2 * px), 0)
    for dx in range(2 * px + 1):
        for dy in range(2 * px + 1):
            out.paste(1, (dx, dy), mask)
    return out


def logo_text(text, scale, depth):
    """Chunky title lettering: dark outline, a brass bevel extruded downwards and a gradient face."""
    face = font_mask(text, scale)
    w, h = face.size
    layer = Image.new("RGBA", (w + 4, h + depth + 4), (0, 0, 0, 0))
    solid = Image.new("1", layer.size, 0)
    for d in range(depth + 1):
        solid.paste(1, (2, 2 + d), face)
    layer.paste(Image.new("RGBA", layer.size, SHADOW), (0, 0), grow(solid, 1).crop((1, 1, layer.size[0] + 1, layer.size[1] + 1)))
    for d in range(depth, 0, -1):
        tone = BRASS_DARK if d > 1 else (150, 100, 42)
        layer.paste(Image.new("RGBA", face.size, tone), (2, 2 + d), face)
    gradient = Image.new("RGBA", face.size)
    gd = ImageDraw.Draw(gradient)
    for y in range(h):
        t = y / max(1, h - 1)
        gd.line([0, y, w, y], fill=tuple(int(a + (b - a) * t) for a, b in zip(BRASS_LIGHT, BRASS)) + (255,))
    gd.line([0, 0, w, 0], fill=(255, 236, 180, 255))
    layer.paste(gradient, (2, 2), face)
    return layer


def outlined_text(text, scale, color):
    face = font_mask(text, scale)
    layer = Image.new("RGBA", (face.size[0] + 2, face.size[1] + 2), (0, 0, 0, 0))
    layer.paste(Image.new("RGBA", layer.size, SHADOW), (0, 0), grow(face, 1))
    layer.paste(Image.new("RGBA", face.size, color), (1, 1), face)
    return layer


def banner():
    """An in-game shot of a brewery, softened and darkened, with the title lettering over it."""
    W, H, scale = 1600, 400, 5
    shot = Image.open(OUT / "shot_line.png").convert("RGB")
    cover = max(W / shot.width, H / shot.height)
    shot = shot.resize((round(shot.width * cover), round(shot.height * cover)), Image.LANCZOS)
    left, top = (shot.width - W) // 2, (shot.height - H) // 2
    bg = shot.crop((left, top, left + W, top + H)).filter(ImageFilter.GaussianBlur(3)).convert("RGBA")
    shade = Image.new("RGBA", (W, H))
    sd = ImageDraw.Draw(shade)
    for x in range(W):
        edge = min(x, W - 1 - x) / (W / 2)
        sd.line([x, 0, x, H], fill=(18, 10, 6, int(110 + 100 * (1 - edge))))
    bg.alpha_composite(shade)
    art = Image.new("RGBA", (W // scale, H // scale), (0, 0, 0, 0))
    title = logo_text("CREATE BREWERY", 3, 3)
    art.alpha_composite(title, ((art.width - title.width) // 2, 14))
    tag = outlined_text("BREW - DISTILL - AGE - TOAST", 1, CREAM)
    art.alpha_composite(tag, ((art.width - tag.width) // 2, 14 + title.height + 6))
    shelf = ["beer", "wine", "whiskey", "mead", "rum"]
    x0 = (art.width - len(shelf) * 18) // 2
    for i, name in enumerate(shelf):
        art.alpha_composite(sprite(f"{name}_bottle", 16), (x0 + i * 18 + 1, 14 + title.height + 18))
    bg.alpha_composite(art.resize((W, H), Image.NEAREST))
    frame = ImageDraw.Draw(bg)
    for i, c in enumerate((SHADOW, BRASS_DARK, BRASS, BRASS_DARK)):
        frame.rectangle([i * 2, i * 2, W - 1 - i * 2, H - 1 - i * 2], outline=c, width=2)
    return bg


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
