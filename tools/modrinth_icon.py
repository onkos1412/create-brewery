"""Draws the Modrinth project icon (512x512) from a hand-placed 32x32 pixel map: a foaming wooden
mug on a dark oak tile in a brass rim.

Run: python tools/modrinth_icon.py  ->  docs/modrinth/img/icon.png
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "docs/modrinth/img"

PALETTE = {
    "K": (20, 12, 8),        # outline
    "F": (250, 244, 228),    # foam
    "f": (214, 202, 178),    # foam shade
    "W": (255, 255, 255),    # foam highlight
    "w": (140, 92, 52),      # wood
    "l": (182, 128, 76),     # wood light
    "d": (96, 60, 33),       # wood dark
    "i": (88, 92, 100),      # iron band
    "j": (158, 164, 174),    # iron highlight
    "a": (226, 150, 36),     # beer
    "B": (196, 140, 58),     # brass
    "L": (240, 196, 108),    # brass light
    "D": (122, 82, 34),      # brass dark
    "S": (255, 236, 176),    # brass shine
}

# Foaming wooden mug, 20x22.
MUG = [
    "....KKKK...KKKK.....",
    "...KFFFFK.KFFFFK....",
    "..KFFWFFFKFFFFFFK...",
    ".KFFWFFFFFFFFFFFFK..",
    ".KFFFFFFFFFFFFFFFK..",
    "KfFFFFFFFFFFFFFFFfK.",
    "KffFFfffFFFffFFfffK.",
    ".KlfFwwfFwwwfFwwdK..",
    ".KlwFwwwfwwwwfwwdKKK",
    ".KjjiiiiiiiiiiiiiKdK",
    ".KlwwdwwdwwwdwwdKKdK",
    ".KlwwdwwdwwwdwwdK.dK",
    ".KlwwdwwdwwwdwwdK.dK",
    ".KlwwdwwdwwwdwwdK.dK",
    ".KlwwdwwdwwwdwwdK.dK",
    ".KlwwdwwdwwwdwwdKKdK",
    ".KjjiiiiiiiiiiiiiKdK",
    ".KlwwdwwdwwwdwwdKKKK",
    ".KlwwdwwdwwwdwwdK...",
    ".KlwwdwwdwwwdwwdK...",
    ".KddddddddddddddK...",
    "..KKKKKKKKKKKKKK....",
]


def stamp(im, rows, x0, y0):
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x0 + x, y0 + y] = PALETTE[ch] + (255,)


def tile(size=32):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=6, fill=(46, 31, 21), outline=PALETTE["K"])
    d.rounded_rectangle([1, 1, size - 2, size - 2], radius=5, outline=PALETTE["D"])
    for y in range(3, size - 3):
        d.line([3, y, size - 4, y], fill=(50, 34, 23) if (y // 4) % 2 else (44, 29, 20))
        if y % 4 == 3:
            d.line([3, y, size - 4, y], fill=(36, 24, 16))
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    im = tile()
    stamp(im, MUG, 6, 5)
    im.resize((512, 512), Image.NEAREST).save(OUT / "icon.png")


if __name__ == "__main__":
    main()
