#!/usr/bin/env python3
"""Generates the mod's block/item textures, GUI sheets and block models.

Usage: python tools/gen_art.py [preview.png]
With an argument it also writes an isometric preview of the block models.
"""
import json
import math
import pathlib
import random
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/assets/brewery"
BLOCK = ROOT / "textures/block"
ITEM = ROOT / "textures/item"
GUI = ROOT / "textures/gui"
MODELS = ROOT / "models/block"
STATES = ROOT / "blockstates"

COPPER = ["#5a2a1c", "#7d3f26", "#a55a32", "#c97a45", "#e39a60", "#f6c08c"]
BRASS = ["#5e4216", "#8a6420", "#b58a2e", "#d8ae45", "#efcf6a", "#fbeaa0"]
IRON = ["#26282c", "#3a3d43", "#54585f", "#70757d", "#92979e"]
OAK = ["#3f2c15", "#5a401f", "#75552c", "#8f6d3b", "#a9854c", "#bf9c5f"]
ANDESITE = ["#4b4c4a", "#62635f", "#7a7b76", "#92938d", "#a9aaa3"]


def rgb(hex_color, alpha=255):
    h = hex_color.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


def tex(size=16):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def save(img, directory, name):
    directory.mkdir(parents=True, exist_ok=True)
    img.save(directory / f"{name}.png")


def plate(ramp, seed):
    """Sheet metal: bright along the middle of a facet, darker at its edges, faint hammer marks."""
    rng = random.Random(seed)
    img = tex()
    top = len(ramp) - 2
    for y in range(16):
        for x in range(16):
            edge = abs(x - 7.5) / 7.5
            shade = top - edge * 2.2 + rng.choice((0, 0, 0, 0, 0, 0, 0, 0.5, -0.5))
            img.putpixel((x, y), rgb(ramp[max(0, min(top + 1, round(shade)))]))
    return img


def bands(ramp, seed):
    """A plate with riveted horizontal seams, for the tall bellies of the vessels."""
    img = plate(ramp, seed)
    for y in (0, 15):
        for x in range(16):
            img.putpixel((x, y), rgb(ramp[1]))
    for x in range(1, 16, 3):
        img.putpixel((x, 2), rgb(ramp[5]))
        img.putpixel((x, 3), rgb(ramp[0]))
        img.putpixel((x, 12), rgb(ramp[5]))
        img.putpixel((x, 13), rgb(ramp[0]))
    return img


def solid(color):
    img = tex()
    img.paste(rgb(color), (0, 0, 16, 16))
    return img


def casing():
    """Andesite-and-brass machine base, matching Create's casings."""
    rng = random.Random(7)
    img = tex()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rgb(ANDESITE[rng.choice((1, 2, 2, 2, 3))]))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 15, 15), outline=rgb(BRASS[2]))
    d.line((0, 0, 15, 0), fill=rgb(BRASS[4]))
    d.line((0, 0, 0, 15), fill=rgb(BRASS[3]))
    d.line((15, 1, 15, 15), fill=rgb(BRASS[1]))
    d.line((1, 15, 15, 15), fill=rgb(BRASS[1]))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((x, y), rgb(IRON[4]))
    return img


def staves():
    """Barrel staves running along the texture's U axis, since the barrel lies on its side."""
    rng = random.Random(11)
    img = tex()
    for y in range(16):
        row = y // 4
        base = (3, 2, 3, 4)[row]
        for x in range(16):
            shade = base + rng.choice((0, 0, 0, -1, 1))
            if y % 4 == 3:
                shade = 0
            img.putpixel((x, y), rgb(OAK[max(0, min(5, shade))]))
    return img


def barrel_end():
    rng = random.Random(13)
    img = tex()
    for y in range(16):
        for x in range(16):
            plank = x // 4
            shade = (3, 2, 3, 4)[plank] + rng.choice((0, 0, 0, -1))
            if x % 4 == 3:
                shade = 1
            img.putpixel((x, y), rgb(OAK[shade]))
    return img


def surface(frames, ramp, seed):
    """An animated vertical strip of a boiling liquid."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16 * frames))
    bubbles = [(rng.randrange(16), rng.randrange(16), rng.randrange(frames)) for _ in range(9)]
    for frame in range(frames):
        for y in range(16):
            for x in range(16):
                wave = math.sin((x + frame * 2) * 0.7) + math.cos((y - frame) * 0.9)
                shade = 2 + round(wave * 0.8)
                img.putpixel((x, y + frame * 16), rgb(ramp[max(0, min(len(ramp) - 1, shade))]))
        for bx, by, start in bubbles:
            age = (frame - start) % frames
            if age < 3:
                color = ramp[-1] if age < 2 else ramp[0]
                img.putpixel((bx, by + frame * 16), rgb(color))
    return img


def write_block_textures():
    save(bands(COPPER, 1), BLOCK, "copper_belly")
    save(plate(COPPER, 2), BLOCK, "copper_plate")
    save(plate(BRASS, 3), BLOCK, "brass_plate")
    save(plate(IRON, 4), BLOCK, "iron_hoop")
    save(casing(), BLOCK, "machine_casing")
    save(staves(), BLOCK, "barrel_staves")
    save(barrel_end(), BLOCK, "barrel_end")
    save(solid(COPPER[1]), BLOCK, "copper_trim")
    save(solid(BRASS[3]), BLOCK, "brass_trim")
    save(solid(OAK[1]), BLOCK, "oak_trim")
    save(solid("#2a1610"), BLOCK, "kettle_inside")
    save(solid(ANDESITE[0]), BLOCK, "casing_dark")
    gauge = solid(BRASS[2])
    d = ImageDraw.Draw(gauge)
    d.ellipse((2, 2, 13, 13), fill=rgb("#efe7d0"), outline=rgb(IRON[0]))
    d.line((7, 8, 11, 4), fill=rgb("#b02a20"))
    save(gauge, BLOCK, "gauge")
    wort = surface(8, ["#6b3d10", "#8c5518", "#b07422", "#cf9334", "#efc56a"], 5)
    save(wort, BLOCK, "kettle_boil")
    (BLOCK / "kettle_boil.png.mcmeta").write_text(
        json.dumps({"animation": {"frametime": 3, "interpolate": True}}, indent=2) + "\n", encoding="utf-8")


def bottle(glass, liquid, label, shoulders):
    """A 16x16 item sprite. `shoulders` makes the squarer whiskey bottle."""
    outline = rgb("#1d1a18")
    img = tex()
    p = img.putpixel
    # Neck and stopper.
    for y in range(1, 5):
        for x in (7, 8):
            p((x, y), rgb(glass))
        p((6, y), outline)
        p((9, y), outline)
    for x in (6, 7, 8, 9):
        p((x, 0), rgb(OAK[3]))
    p((7, 1), rgb(OAK[4]))
    p((8, 1), rgb(OAK[2]))
    half = 4 if shoulders else 3
    start = 6 if shoulders else 7
    # Shoulder.
    for x in range(8 - half, 8 + half):
        p((x, start - 1), outline)
    p((7, 5), rgb(glass))
    p((8, 5), rgb(glass))
    # Body.
    for y in range(start, 15):
        p((8 - half - 1, y), outline)
        p((8 + half, y), outline)
        for x in range(8 - half, 8 + half):
            column = x - (8 - half)
            color = liquid[0] if column == 0 else liquid[2] if column >= 2 * half - 2 else liquid[1]
            p((x, y), rgb(color))
    for x in range(8 - half, 8 + half):
        p((x, 15), outline)
        p((x, start), rgb(glass))
    # Glass glint.
    for y in range(start + 1, 11):
        p((8 - half, y), rgb("#fff4d8", 255))
    # Label.
    if label:
        for y in range(10, 14):
            for x in range(8 - half + 1, 8 + half - 1):
                p((x, y), rgb(label[0]))
        for x in range(8 - half + 1, 8 + half - 1):
            p((x, 10), rgb(label[1]))
            p((x, 13), rgb(label[1]))
    return img


def write_item_textures():
    amber = ["#d98f20", "#b86f14", "#8c4f0c"]
    save(bottle("#8c6a3c", ["#a56f24", "#7d4f18", "#5c3810"], ("#e8dcc0", "#b02a20"), False), ITEM, "beer_bottle")
    save(bottle("#c9b48c", amber, ("#1d2a3a", "#efcf6a"), True), ITEM, "whiskey_bottle")
    save(bottle("#a9b878", ["#d8d066", "#b8ae48", "#8f8834"], ("#e8dcc0", "#3f7a30"), False), ITEM, "cider_bottle")
    save(bottle("#b5a98c", ["#cdbb8a", "#a8966a", "#857550"], None, False), ITEM, "mash_bottle")
    save(bottle("#c9a070", ["#c9702a", "#a4551c", "#7c3e12"], ("#2a4a26", "#efcf6a"), True), ITEM, "calvados_bottle")
    save(bottle("#dfe9ee", ["#f4f9fb", "#e2eef3", "#c4d6de"], ("#b02a20", "#f4f0e4"), True), ITEM, "vodka_bottle")
    save(bottle("#8c5a3c", ["#7a3a18", "#5c2a10", "#3f1c0a"], ("#e8dcc0", "#1d1a18"), True), ITEM, "rum_bottle")
    save(bottle("#7a4a5a", ["#9c2440", "#7a1a30", "#5a1022"], ("#f0e8d8", "#7a1a30"), False), ITEM, "wine_bottle")
    save(bottle("#d8b870", ["#f2c84a", "#d8a830", "#b08820"], ("#3a2a14", "#f2c84a"), False), ITEM, "mead_bottle")
    save(bottle("#dfe9e6", ["#f3e7c0", "#e6d49a", "#cbb676"], None, True), ITEM, "distillate")


# ---- models ------------------------------------------------------------------------

TAN = math.tan(math.pi / 8)


def face(texture, **extra):
    return {"texture": f"#{texture}", **extra}


def octagon(center, r, axis_range, side, cap=None, axis="y"):
    """A regular octagonal prism: four rectangles whose short ends are the eight facets.

    Coplanar caps overlap, so `cap` should be a flat-colour texture.
    """
    a, b = center
    lo, hi = axis_range
    s = r * TAN
    parts = []
    for long_first, angle in ((True, 0), (False, 0), (True, 45), (False, 45)):
        du, dv = (r, s) if long_first else (s, r)
        if axis == "y":
            start = [a - du, lo, b - dv]
            end = [a + du, hi, b + dv]
            ends = ("east", "west") if long_first else ("north", "south")
            caps = ("up", "down")
            origin = [a, (lo + hi) / 2, b]
        else:  # along z, the rectangle lies in the x/y plane
            start = [a - du, b - dv, lo]
            end = [a + du, b + dv, hi]
            ends = ("east", "west") if long_first else ("up", "down")
            caps = ("north", "south")
            origin = [a, b, (lo + hi) / 2]
        faces = {name: face(side) for name in ends}
        if cap:
            faces.update({name: face(cap) for name in caps})
        element = {"from": rounded(start), "to": rounded(end), "faces": faces}
        if angle:
            element["rotation"] = {"origin": rounded(origin), "axis": axis, "angle": angle}
        parts.append(element)
    return parts


def ring(center, r, y_range, thickness, side, cap):
    """An open octagonal wall, for the rim of a vessel."""
    a, b = center
    lo, hi = y_range
    s = r * TAN
    parts = []
    for rotated in (False, True):
        for start, end, faces in (
            ([a - s, lo, b - r], [a + s, hi, b - r + thickness], ("north", "south")),
            ([a - s, lo, b + r - thickness], [a + s, hi, b + r], ("north", "south")),
            ([a - r, lo, b - s], [a - r + thickness, hi, b + s], ("east", "west")),
            ([a + r - thickness, lo, b - s], [a + r, hi, b + s], ("east", "west")),
        ):
            built = {name: face(side) for name in faces}
            built["up"] = face(cap)
            element = {"from": rounded(start), "to": rounded(end), "faces": built}
            if rotated:
                element["rotation"] = {"origin": rounded([a, (lo + hi) / 2, b]), "axis": "y", "angle": 45}
            parts.append(element)
    return parts


def box(start, end, texture, faces="north south east west up down", **overrides):
    built = {name: face(texture) for name in faces.split()}
    for name, value in overrides.items():
        built[name] = face(value)
    return {"from": rounded(start), "to": rounded(end), "faces": built}


def rounded(values):
    return [round(v, 3) for v in values]


def model(particle, textures, elements):
    all_textures = {"particle": f"brewery:block/{particle}"}
    all_textures.update({key: f"brewery:block/{value}" for key, value in textures.items()})
    return {"parent": "minecraft:block/block", "textures": all_textures, "elements": elements}


KETTLE_TEXTURES = {
    "belly": "copper_belly", "plate": "copper_plate", "trim": "copper_trim",
    "brass": "brass_plate", "brass_trim": "brass_trim", "gauge": "gauge",
    "liquid": "kettle_inside",
}


def kettle_elements():
    c = (8, 8)
    parts = []
    # Brass feet, so the fire underneath stays visible.
    for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):
        parts.append(box([x, 0, z], [x + 2, 4.5, z + 2], "brass"))
    parts += octagon(c, 5.5, (2, 4), "plate", "trim")
    parts += octagon(c, 7.5, (4, 10), "belly", "trim")
    parts += octagon(c, 6.5, (10, 12), "plate", "trim")
    parts += octagon(c, 5.5, (12, 13), "plate", "trim")
    # The surface sits inside the rim.
    parts.append(box([3, 13, 3], [13, 13.5, 13], "liquid", "up"))
    parts += ring(c, 6.5, (13, 15), 1, "brass", "brass_trim")
    # Thermometer and drain on the front.
    parts.append(box([6.5, 6, 0.1], [9.5, 9, 1], "gauge", "north up down east west"))
    parts.append(box([10, 3, 1], [12, 5, 3], "brass"))
    return parts


def distillery_elements():
    c = (8, 8)
    parts = [box([0, 0, 0], [16, 2, 16], "casing")]
    # Pot, shoulder, neck: the onion shape of a pot still.
    parts += octagon(c, 6, (2, 7), "belly", "trim")
    parts += octagon(c, 4.5, (7, 9), "plate", "trim")
    parts += octagon(c, 2.5, (9, 13), "plate", "trim")
    parts += octagon(c, 3, (13, 14), "brass", "brass_trim")
    parts += octagon(c, 2, (14, 16), "plate", "trim")
    # The lyne arm runs over to the condenser in the corner.
    parts.append(box([9, 14, 7], [14.5, 16, 9], "plate"))
    parts.append(box([12.5, 3, 7], [14.5, 14, 9], "plate"))
    for y in (5, 8, 11):
        parts.append(box([12, y, 6.5], [15, y + 1, 9.5], "brass"))
    parts.append(box([11, 2, 6], [16, 3, 10], "brass"))
    # Kinetic input and gauge.
    parts.append(box([5, 2, 13], [11, 6, 16], "casing"))
    parts.append(box([6.5, 3, 15.5], [9.5, 6, 16.1], "iron"))
    parts.append(box([6.5, 3.5, 1.2], [9.5, 6.5, 2.1], "gauge", "north up down east west"))
    return parts


def barrel_elements():
    centre = (8, 8.5)
    parts = []
    # Cradle.
    for z in (2, 11):
        parts.append(box([1, 0, z], [15, 3, z + 3], "beam"))
        parts.append(box([1, 3, z], [3, 5, z + 3], "beam"))
        parts.append(box([13, 3, z], [15, 5, z + 3], "beam"))
    # A bulge, stave by stave, with iron hoops proud of the wood.
    for r, lo, hi, side in (
        (5.8, 0.5, 2, "wood"), (6.3, 2, 5, "wood"), (6.9, 5, 11, "wood"),
        (6.3, 11, 14, "wood"), (5.8, 14, 15.5, "wood"),
        (6.1, 1, 2, "hoop"), (6.6, 3.5, 4.5, "hoop"), (7.2, 6.5, 7, "hoop"),
        (7.2, 9, 9.5, "hoop"), (6.6, 11.5, 12.5, "hoop"), (6.1, 14, 15, "hoop"),
    ):
        parts += octagon(centre, r, (lo, hi), side, "trim", axis="z")
    # Heads sit a hair proud so they win over the octagon's flat caps.
    a = 5.8 * math.sqrt(0.5)
    s = 5.8 * TAN
    for z_lo, z_hi, name in ((0.45, 0.5, "north"), (15.5, 15.55, "south")):
        for dx, dy in ((a, a), (5.8, s), (s, 5.8)):
            parts.append(box([8 - dx, 8.5 - dy, z_lo], [8 + dx, 8.5 + dy, z_hi], "head", name))
    # Tap in the front head.
    parts.append(box([7, 4, 0], [9, 6, 0.5], "brass"))
    parts.append(box([7.5, 6, 0.1], [8.5, 7, 0.4], "brass"))
    return parts


def spout_elements():
    parts = [
        # Bolted flange against the machine.
        box([4, 4, 14.5], [12, 12, 16], "brass"),
        box([3, 6, 14.5], [13, 10, 16], "brass"),
        box([6, 3, 14.5], [10, 13, 16], "brass"),
        # Pipe with a collar, then the elbow.
        box([6, 7, 8], [10, 11, 14.5], "copper"),
        box([5.5, 6.5, 12], [10.5, 11.5, 13], "brass"),
        box([6, 4, 4], [10, 11, 8], "copper"),
        box([5.5, 2, 3.5], [10.5, 4, 8.5], "brass"),
        box([6.5, 1, 4.5], [9.5, 2, 7.5], "dark", "down north south east west"),
        # Valve stem and handwheel.
        box([7.25, 11, 9.25], [8.75, 13, 10.75], "copper"),
        box([5, 13, 9], [11, 14, 11], "brass"),
        box([7, 13, 7], [9, 14, 13], "brass"),
    ]
    return parts


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def write_models():
    kettle = model("copper_plate", KETTLE_TEXTURES, kettle_elements())
    dump(MODELS / "kettle.json", kettle)
    dump(MODELS / "kettle_brewing.json", {
        "parent": "brewery:block/kettle",
        "textures": {"liquid": "brewery:block/kettle_boil"},
    })
    dump(MODELS / "distillery.json", model("copper_plate", {
        "belly": "copper_belly", "plate": "copper_plate", "trim": "copper_trim",
        "brass": "brass_plate", "brass_trim": "brass_trim", "casing": "machine_casing",
        "iron": "iron_hoop", "gauge": "gauge",
    }, distillery_elements()))
    dump(MODELS / "barrel.json", model("barrel_staves", {
        "wood": "barrel_staves", "hoop": "iron_hoop", "trim": "oak_trim",
        "head": "barrel_end", "beam": "barrel_end", "brass": "brass_plate",
    }, barrel_elements()))
    dump(MODELS / "spout.json", model("brass_plate", {
        "brass": "brass_plate", "copper": "copper_plate", "dark": "kettle_inside",
    }, spout_elements()))

    dump(STATES / "kettle.json", {"variants": {
        "brewing=false": {"model": "brewery:block/kettle"},
        "brewing=true": {"model": "brewery:block/kettle_brewing"},
    }})
    for name in ("distillery", "barrel"):
        dump(STATES / f"{name}.json", {"variants": {
            "facing=north": {"model": f"brewery:block/{name}"},
            "facing=east": {"model": f"brewery:block/{name}", "y": 90},
            "facing=south": {"model": f"brewery:block/{name}", "y": 180},
            "facing=west": {"model": f"brewery:block/{name}", "y": 270},
        }})

    items = ROOT / "models/item"
    dump(items / "distillate.json", {"parent": "minecraft:item/generated",
                                     "textures": {"layer0": "brewery:item/distillate"}})
    # The base model is beer; the `brewery:drink` property picks the others (see ModClientSetup).
    art = ["whiskey", "cider", "mash", "calvados", "vodka", "rum", "wine", "mead"]
    for name in art:
        dump(items / f"{name}_bottle.json", {"parent": "minecraft:item/generated",
                                             "textures": {"layer0": f"brewery:item/{name}_bottle"}})
    dump(items / "drink_bottle.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "brewery:item/beer_bottle"},
        "overrides": [{"predicate": {"brewery:drink": index + 1}, "model": f"brewery:item/{name}_bottle"}
                      for index, name in enumerate(art)],
    })


# ---- GUI ----------------------------------------------------------------------------

PANEL = "#d8c8a4"
PANEL_LIGHT = "#f4e9cc"
PANEL_DARK = "#8f7d58"
INV = "#b9ae98"
INV_LIGHT = "#dcd3bf"
INV_DARK = "#7d7461"
SLOT_FILL = "#6f6656"


def bevel(d, box_xy, fill, light, dark):
    x0, y0, x1, y1 = box_xy
    d.rectangle(box_xy, fill=rgb(fill))
    d.line((x0, y1, x0, y0), fill=rgb(light))
    d.line((x0, y0, x1, y0), fill=rgb(light))
    d.line((x1, y0, x1, y1), fill=rgb(dark))
    d.line((x0, y1, x1, y1), fill=rgb(dark))


def slot(d, x, y):
    """Frames the 16x16 item area whose top-left is (x, y), as the menu's slot coordinates do."""
    d.rectangle((x - 1, y - 1, x + 16, y + 16), fill=rgb(SLOT_FILL))
    d.line((x - 1, y + 16, x - 1, y - 1), fill=rgb("#3e382d"))
    d.line((x - 1, y - 1, x + 16, y - 1), fill=rgb("#3e382d"))
    d.line((x + 16, y - 1, x + 16, y + 16), fill=rgb("#f6efdc"))
    d.line((x - 1, y + 16, x + 16, y + 16), fill=rgb("#f6efdc"))


def recess(d, x0, y0, x1, y1):
    d.rectangle((x0, y0, x1, y1), fill=rgb("#c8b78f"))
    d.line((x0, y1, x0, y0), fill=rgb(PANEL_DARK))
    d.line((x0, y0, x1, y0), fill=rgb(PANEL_DARK))
    d.line((x1, y0, x1, y1), fill=rgb(PANEL_LIGHT))
    d.line((x0, y1, x1, y1), fill=rgb(PANEL_LIGHT))


def tank(d, x, y, height):
    """A 10px wide tank frame with a graduated scale; levels are drawn by the screen."""
    d.rectangle((x - 1, y - 1, x + 10, y + height), fill=rgb("#2a2620"))
    d.line((x - 1, y + height, x - 1, y - 1), fill=rgb(BRASS[1]))
    d.line((x - 1, y - 1, x + 10, y - 1), fill=rgb(BRASS[1]))
    d.line((x + 10, y - 1, x + 10, y + height), fill=rgb(BRASS[4]))
    d.line((x - 1, y + height, x + 10, y + height), fill=rgb(BRASS[4]))
    for tick in range(1, 4):
        ty = y + height * tick // 4
        d.line((x + 11, ty, x + 12, ty), fill=rgb(PANEL_DARK))


def arrow(d, x, y, color):
    """A 22x15 arrow, the same footprint as the furnace's."""
    d.rectangle((x, y + 5, x + 13, y + 9), fill=color)
    for i in range(8):
        d.line((x + 14 + i, y + i, x + 14 + i, y + 14 - i), fill=color)


def ghost_bottle(img, x, y):
    d = ImageDraw.Draw(img)
    color = rgb("#857b69")
    d.rectangle((x + 7, y + 2, x + 8, y + 5), fill=color)
    d.rectangle((x + 4, y + 6, x + 11, y + 13), fill=color)


def ghost_log(img, x, y):
    d = ImageDraw.Draw(img)
    d.rectangle((x + 3, y + 4, x + 12, y + 11), fill=rgb("#857b69"))
    d.rectangle((x + 5, y + 6, x + 10, y + 9), fill=rgb("#6f6656"))


def machine_sheet(seed):
    """The common frame: machine panel, inventory slab, brass trim. 256x256 like vanilla sheets."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bevel(d, (0, 0, 175, 165), PANEL, PANEL_LIGHT, PANEL_DARK)
    d.rectangle((0, 0, 175, 165), outline=rgb(BRASS[1]))
    d.rectangle((1, 1, 174, 164), outline=rgb(BRASS[3]))
    d.line((2, 2, 173, 2), fill=rgb(BRASS[5]))
    d.line((2, 2, 2, 163), fill=rgb(BRASS[4]))
    d.line((173, 3, 173, 163), fill=rgb(BRASS[2]))
    d.line((2, 163, 173, 163), fill=rgb(BRASS[2]))
    # Faint brushed metal on the panel.
    for _ in range(420):
        x, y = rng.randrange(4, 172), rng.randrange(4, 70)
        img.putpixel((x, y), rgb(rng.choice(("#d2c29c", "#dfd0ac"))))
    # Inventory slab, under a brass divider.
    d.rectangle((3, 71, 172, 162), fill=rgb(INV))
    d.line((3, 70, 172, 70), fill=rgb(BRASS[2]))
    d.line((3, 71, 172, 71), fill=rgb(INV_LIGHT))
    for row in range(3):
        for col in range(9):
            slot(d, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        slot(d, 8 + col * 18, 142)
    # Corner rivets.
    for x, y in ((5, 5), (170, 5), (5, 160), (170, 160)):
        d.rectangle((x - 1, y - 1, x + 1, y + 1), fill=rgb(BRASS[2]))
        img.putpixel((x - 1, y - 1), rgb(BRASS[5]))
        img.putpixel((x + 1, y + 1), rgb(BRASS[0]))
    return img, d


def write_gui():
    # Kettle: ingredients, progress, and the two tanks.
    img, d = machine_sheet(1)
    recess(d, 22, 16, 100, 40)
    for i in range(4):
        slot(d, 26 + i * 18, 20)
    tank(d, 130, 18, 46)
    tank(d, 150, 18, 46)
    save(img, GUI, "kettle")

    # Distillery: wort tank -> arrow -> distillate.
    img, d = machine_sheet(2)
    tank(d, 20, 18, 34)
    arrow(d, 50, 36, rgb("#a8986f"))
    slot(d, 80, 35)
    ghost_bottle(img, 80, 35)
    save(img, GUI, "distillery")

    # Barrel: distillate + wood -> finished bottle.
    img, d = machine_sheet(3)
    # The gap between the slots is short, so the arrow's tail tucks under the wood slot's frame.
    arrow(d, 92, 36, rgb("#a8986f"))
    slot(d, 44, 35)
    ghost_bottle(img, 44, 35)
    slot(d, 80, 35)
    ghost_log(img, 80, 35)
    slot(d, 116, 35)
    slot(d, 116, 15)
    ghost_bottle(img, 116, 15)
    d.rectangle((64, 41, 76, 45), fill=rgb("#a8986f"))
    save(img, GUI, "barrel")

    # Parts sheet shared by the screens.
    parts = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(parts)
    # Lit arrow at (0, 0).
    arrow(d, 0, 0, rgb(COPPER[3]))
    # Buttons at y 16, 30, 44: normal, hover, disabled-free pressed look. 64x14.
    for index, (fill, light, dark) in enumerate((
        (BRASS[2], BRASS[4], BRASS[0]),
        (BRASS[3], BRASS[5], BRASS[1]),
    )):
        bevel(d, (0, 16 + index * 14, 63, 29 + index * 14), fill, light, dark)
    # Flame at (70, 0) lit and (70, 16) unlit, 13x13.
    flame = [
        "......x......", ".....xx......", ".....xxx.....", "....xxxx.....",
        "....xxxxx.x..", "...xxxxxxxx..", "..xxxoxxxxx..", "..xxoooxxxxx.",
        ".xxxooooxxxx.", ".xxoooyooxxx.", ".xxooyyyooxx.", "..xooyyyoox..",
        "...xxooooxx..",
    ]
    for top, palette in ((0, {"x": "#d8501c", "o": "#f39a24", "y": "#fde07a"}),
                         (16, {"x": "#8b8069", "o": "#9b907a", "y": "#a99e88"})):
        for row, line in enumerate(flame):
            for col, ch in enumerate(line):
                if ch in palette:
                    parts.putpixel((70 + col, top + row), rgb(palette[ch]))
    save(parts, GUI, "machine_parts")


# ---- preview ------------------------------------------------------------------------

def preview(path):
    """A small z-buffered isometric renderer for reviewing the models outside the game."""
    scale = 11
    names = [("kettle", None), ("kettle", "kettle_boil"), ("distillery", None), ("barrel", None), ("spout", None)]
    views = [(35, 30), (215, 30)]
    tile = 320
    sheet = Image.new("RGBA", (tile * len(names), tile * len(views)), rgb("#88a0b0"))
    for vi, (yaw, pitch) in enumerate(views):
        for ni, (name, liquid) in enumerate(names):
            data = json.loads((MODELS / f"{name}.json").read_text(encoding="utf-8"))
            sprites = {k: v.split("/")[-1] for k, v in data["textures"].items()}
            if liquid:
                sprites["liquid"] = liquid
            render_model(sheet, ni * tile, vi * tile, tile, data["elements"], sprites, yaw, pitch, scale)
    sheet.save(path)


FACE_AXES = {
    "north": (2, 0), "south": (2, 1), "west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1),
}
LIGHT = {"north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6, "up": 1.0, "down": 0.5}


def default_uv(name, p):
    x, y, z = p
    return {
        "north": (16 - x, 16 - y), "south": (x, 16 - y), "west": (z, 16 - y),
        "east": (16 - z, 16 - y), "up": (x, z), "down": (x, 16 - z),
    }[name]


def render_model(sheet, ox, oy, tile, elements, sprites, yaw, pitch, scale):
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def project(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        rx, rz = x * cy - z * sy, x * sy + z * cy
        ry, depth = y * cp - rz * sp, y * sp + rz * cp
        return ox + tile / 2 + rx * scale, oy + tile / 2 - ry * scale, depth

    zbuffer = {}
    cache = {}
    for el in elements:
        lo, hi = el["from"], el["to"]
        rot = el.get("rotation")
        for name, spec in el["faces"].items():
            axis, side = FACE_AXES[name]
            fixed = (hi if side else lo)[axis]
            others = [i for i in range(3) if i != axis]
            sprite = sprites[spec["texture"][1:]]
            if sprite not in cache:
                cache[sprite] = Image.open(BLOCK / f"{sprite}.png").convert("RGBA")
            img = cache[sprite]
            steps_a = max(2, int((hi[others[0]] - lo[others[0]]) * 6))
            steps_b = max(2, int((hi[others[1]] - lo[others[1]]) * 6))
            for ia in range(steps_a + 1):
                for ib in range(steps_b + 1):
                    p = [0, 0, 0]
                    p[axis] = fixed
                    p[others[0]] = lo[others[0]] + (hi[others[0]] - lo[others[0]]) * ia / steps_a
                    p[others[1]] = lo[others[1]] + (hi[others[1]] - lo[others[1]]) * ib / steps_b
                    u, v = default_uv(name, p)
                    color = img.getpixel((min(15, max(0, int(u))), min(15, max(0, int(v)))))
                    world = rotate(p, rot)
                    px, py, depth = project(world)
                    shade = LIGHT[name]
                    pixel = (int(px), int(py))
                    for dx in (0, 1):
                        for dy in (0, 1):
                            key = (pixel[0] + dx, pixel[1] + dy)
                            if key not in zbuffer or depth > zbuffer[key] + 1e-3:
                                zbuffer[key] = depth
                                if 0 <= key[0] < sheet.width and 0 <= key[1] < sheet.height:
                                    sheet.putpixel(key, tuple(int(c * shade) for c in color[:3]) + (255,))


def rotate(p, rot):
    if not rot:
        return p
    origin = rot["origin"]
    angle = math.radians(rot["angle"])
    c, s = math.cos(angle), math.sin(angle)
    x, y, z = p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]
    if rot["axis"] == "y":
        x, z = x * c + z * s, -x * s + z * c
    elif rot["axis"] == "z":
        x, y = x * c - y * s, x * s + y * c
    else:
        y, z = y * c - z * s, y * s + z * c
    return [x + origin[0], y + origin[1], z + origin[2]]


def main():
    write_block_textures()
    write_item_textures()
    write_models()
    write_gui()
    if len(sys.argv) > 1:
        preview(sys.argv[1])


if __name__ == "__main__":
    main()
