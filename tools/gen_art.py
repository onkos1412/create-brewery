#!/usr/bin/env python3
"""Generates the mod's block/item textures, GUI sheets, block models and Ponder structures.

Usage: python tools/gen_art.py
"""
import gzip
import json
import math
import pathlib
import random
import struct
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


def solid(color):
    img = tex()
    img.paste(rgb(color), (0, 0, 16, 16))
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
    save(solid("#2a1610"), BLOCK, "kettle_inside")
    # Liquids are grey and take their colour from the drink: see ModClientSetup and ModFluids.
    grey = ["#8c8c8c", "#a6a6a6", "#c0c0c0", "#d6d6d6", "#ffffff"]
    animation = json.dumps({"animation": {"frametime": 3, "interpolate": True}}, indent=2) + "\n"
    calm = ["#b4b4b4", "#c0c0c0", "#c8c8c8", "#d0d0d0", "#c8c8c8"]
    for name, ramp, seed in (("kettle_boil", grey, 5), ("kettle_still", calm, 6), ("wort_still", calm, 7), ("wort_flow", grey, 8)):
        save(surface(8, ramp, seed), BLOCK, name)
        (BLOCK / f"{name}.png.mcmeta").write_text(animation, encoding="utf-8")


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
    save(bottle("#6a7040", ["#7a8a30", "#5c6a20", "#3f4a14"], None, False), ITEM, "spoiled_bottle")
    save(bottle("#7a4a2a", ["#8a4a20", "#6b3a1a", "#4c2810"], ("#e8dcc0", "#6b3a1a"), False), ITEM, "kvass_bottle")
    save(bottle("#2a2018", ["#3e2514", "#2e1a0e", "#1c0f06"], ("#e8dcc0", "#2e1a0e"), False), ITEM, "stout_bottle")
    save(bottle("#d8c88a", ["#ecd88a", "#d4b865", "#a88f45"], ("#2a4a26", "#efcf6a"), True), ITEM, "tequila_bottle")
    save(bottle("#a8c878", ["#9fd05a", "#7fa83a", "#5a8024"], ("#1d2a1a", "#d8e8a0"), True), ITEM, "absinthe_bottle")
    save(bottle("#c9a050", ["#f6c040", "#f0a830", "#c07c1c"], ("#2a1a3a", "#f6e070"), True), ITEM, "firefly_bottle")
    save(bottle("#dfe9e6", ["#f3e7c0", "#e6d49a", "#cbb676"], None, True), ITEM, "distillate")


# ---- models ------------------------------------------------------------------------
# Square volumes dressed in Create's own textures, so the machines sit naturally beside
# its basin, tanks and spouts. Create is a required dependency, so they are always there.

FULL = [0, 0, 16, 16]


def face(texture, uv=None, rotation=None):
    built = {"texture": f"#{texture}"}
    if uv:
        built["uv"] = uv
    if rotation:
        built["rotation"] = rotation
    return built


def box(start, end, texture, faces="north south east west up down", **overrides):
    """A cuboid. Unlisted faces are left off; keyword overrides set a face's (texture, uv, rotation).

    Faces without a uv take the part of the texture under them, at the game's own pixel size.
    A face given a uv must keep that size too: ModelTexelTest fails on a stretched one.
    """
    built = {name: face(texture) for name in faces.split()}
    for name, value in overrides.items():
        built[name] = value if isinstance(value, dict) else face(*value) if isinstance(value, tuple) else face(value)
    return {"from": start, "to": end, "faces": built}


def model(particle, textures, elements):
    return {"parent": "minecraft:block/block",
            "textures": {"particle": particle, **textures},
            "elements": elements}


def walls(lo, hi, outer, inner):
    """A square vessel's four walls, two thick: `outer` outside, `inner` inside."""
    return [
        box([0, lo, 0], [16, hi, 2], outer, "", north=outer, south=inner, east=outer, west=outer),
        box([0, lo, 14], [16, hi, 16], outer, "", south=outer, north=inner, east=outer, west=outer),
        box([0, lo, 2], [2, hi, 14], outer, "", west=outer, east=inner),
        box([14, lo, 2], [16, hi, 14], outer, "", east=outer, west=inner),
    ]


def rim(lo, hi, texture):
    """A flat frame closing the top of walls, like the lip of Create's basin."""
    return [
        box([0, lo, 0], [16, hi, 2], texture),
        box([0, lo, 14], [16, hi, 16], texture),
        box([0, lo, 2], [2, hi, 14], texture, "east west up down"),
        box([14, lo, 2], [16, hi, 14], texture, "east west up down"),
    ]


def kettle_elements():
    """A copper basin with a brass lip on an andesite foot, open to show the brew."""
    return [
        box([2, 0, 2], [14, 2, 14], "foot"),
        box([0, 2, 0], [16, 3, 16], "copper", "north south east west down"),
        *walls(3, 13, "tank", "inside"),
        box([2, 11, 2], [14, 11.5, 14], "liquid", "", up={"texture": "#liquid", "tintindex": 0}),
        *rim(13, 16, "brass"),
    ]


def distillery_elements():
    """A sealed copper tank on an andesite machine base, collared to take the column above it."""
    return [
        box([0, 0, 0], [16, 4, 16], "casing", down=("casing", FULL)),
        box([1, 4, 1], [15, 12, 15], "tank", "north south east west"),
        box([0, 12, 0], [16, 13, 16], "brass", down=("brass", FULL), up=("top", FULL)),
        box([4, 13, 4], [12, 14, 12], "brass", "north south east west up"),
        # The foot of the column and of its return pipe, which the block above carries on.
        box([5, 14, 5], [11, 16, 11], "tank", "north south east west"),
        box([12, 13, 6.5], [15, 16, 9.5], "tank", "north south east west"),
        # A collar around the shaft that enters the back; the turning shaft itself is rendered live.
        # The whole gearbox face in miniature, the one texture meant to be squeezed: see ModelTexelTest.
        box([5, 5, 15], [11, 11, 15.5], "casing", "north east west up down", south=("gearbox", FULL)),
    ]


def distillery_top_elements():
    """The still's column: a brass-banded tank, an arm over the top, and a pipe back down to the base."""
    return [
        box([5, 0, 5], [11, 9, 11], "tank", "north south east west"),
        box([4.5, 1, 4.5], [11.5, 2, 11.5], "brass", "north south east west up down"),
        box([4.5, 6, 4.5], [11.5, 7, 11.5], "brass", "north south east west up down"),
        box([5, 9, 5], [11, 10, 11], "brass"),
        box([6, 10, 6], [10, 13, 10], "tank", "north south east west", up=("top", [6, 6, 10, 10])),
        box([10, 10, 6.5], [15, 13, 9.5], "tank", "north south up down"),
        box([12, 0, 6.5], [15, 10, 9.5], "tank", "north south east west"),
        box([11.5, 4, 6], [15.5, 5, 10], "brass", "north south east west up down"),
    ]


def along_z(texture):
    """Top and bottom faces whose planks run along the barrel, like those on its sides."""
    return {"up": (texture, None, 90), "down": (texture, None, 90)}


def barrel_elements(open_front=False, open_back=False):
    """A cask squared off like a Create casing. An open end joins the next barrel with no head between."""
    tap = "north south east west up down"
    planks = along_z("staves")
    lo = 0 if open_front else 2
    hi = 16 if open_back else 14
    parts = [
        box([1, 1, lo], [15, 15, hi], "staves", "east west", **planks),
        box([0, 0, 6], [16, 16, 7], "hoop", "up down east west"),
        box([0, 0, 9], [16, 16, 10], "hoop", "up down east west"),
    ]
    if not open_front:
        parts += [
            box([0, 0, 0], [16, 16, 2], "hoop", "east west up down", north=("head", FULL), south="staves"),
            box([6.5, 3, -1], [9.5, 5, 0], "brass", tap),
            box([7, 1.5, -1], [9, 3, 0], "brass", tap),
        ]
    if not open_back:
        parts.append(box([0, 0, 14], [16, 16, 16], "hoop", "east west up down", south=("head", FULL), north="staves"))
    return parts


def raised(elements):
    """Elements moved up a block, for an item showing two blocks. Their uvs are pinned first,
    since uvs worked out from a position above the block would run off the texture."""
    lifted = []
    for element in elements:
        x0, y0, z0 = element["from"]
        x1, y1, z1 = element["to"]
        uvs = {
            "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
            "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
            "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0],
        }
        faces = {name: {"uv": uvs[name], **spec} for name, spec in element["faces"].items()}
        lifted.append({"from": [x0, y0 + 16, z0], "to": [x1, y1 + 16, z1], "faces": faces})
    return lifted


def spout_elements():
    """A copper tap in Create's manner: a casing bracket, a square pipe, a brass collar and nozzle."""
    return [
        box([3, 3, 14], [13, 13, 16], "casing"),
        box([6, 6, 9], [10, 10, 14], "tank", "east west up down"),
        box([5, 5, 11], [11, 11, 12], "brass", "north south east west up down"),
        box([5, 4, 3], [11, 10, 9], "tank", "north south east west down", up=("top", [5, 5, 11, 11])),
        box([5.5, 2, 3.5], [10.5, 4, 8.5], "brass", "north south east west"),
        box([6.5, 1, 4.5], [9.5, 2, 7.5], "casing", "north south east west down"),
        box([7, 10, 5], [9, 12, 7], "brass", "north south east west"),
        box([5, 12, 5], [11, 13, 7], "brass"),
    ]


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def create(name):
    return f"create:block/{name}"


def write_models():
    dump(MODELS / "kettle.json", model(create("copper_casing"), {
        "foot": create("andesite_casing"), "copper": create("copper_casing"), "tank": create("fluid_tank"),
        "brass": create("brass_block"), "inside": "brewery:block/kettle_inside",
        "liquid": "brewery:block/kettle_still",
    }, kettle_elements()))
    dump(MODELS / "kettle_brewing.json", {
        "parent": "brewery:block/kettle",
        "textures": {"liquid": "brewery:block/kettle_boil"},
    })
    still_textures = {
        "casing": create("andesite_casing"), "gearbox": create("gearbox"), "tank": create("fluid_tank"),
        "brass": create("brass_block"), "top": create("fluid_tank_top"), "copper": create("copper_casing"),
    }
    dump(MODELS / "distillery.json", model(create("fluid_tank"), still_textures, distillery_elements()))
    dump(MODELS / "distillery_top.json", model(create("fluid_tank"), still_textures, distillery_top_elements()))
    barrel_textures = {
        "staves": "minecraft:block/spruce_planks", "head": create("andesite_casing"),
        "hoop": create("andesite_block"), "brass": create("brass_block"),
    }
    for front in (False, True):
        for back in (False, True):
            name = "barrel" + ("_open_front" if front else "") + ("_open_back" if back else "")
            dump(MODELS / f"{name}.json", model("minecraft:block/spruce_planks", barrel_textures,
                                                barrel_elements(front, back)))
    dump(MODELS / "spout.json", model(create("copper_casing"), {
        "casing": create("copper_casing"), "tank": create("fluid_tank"), "top": create("fluid_tank_top"),
        "brass": create("brass_block"),
    }, spout_elements()))

    dump(STATES / "kettle.json", {"variants": {
        "brewing=false": {"model": "brewery:block/kettle"},
        "brewing=true": {"model": "brewery:block/kettle_brewing"},
    }})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for front in (False, True):
            for back in (False, True):
                name = "barrel" + ("_open_front" if front else "") + ("_open_back" if back else "")
                key = f"facing={facing},open_back={str(back).lower()},open_front={str(front).lower()}"
                variants[key] = {"model": f"brewery:block/{name}", "y": y}
    dump(STATES / "barrel.json", {"variants": variants})
    for name in ("distillery", "distillery_top"):
        dump(STATES / f"{name}.json", {"variants": {
            "facing=north": {"model": f"brewery:block/{name}"},
            "facing=east": {"model": f"brewery:block/{name}", "y": 90},
            "facing=south": {"model": f"brewery:block/{name}", "y": 180},
            "facing=west": {"model": f"brewery:block/{name}", "y": 270},
        }})

    items = ROOT / "models/item"
    dump(items / "barrel.json", {"parent": "brewery:block/barrel"})
    # The item shows the whole two-block still, scaled down to fit a slot.
    dump(items / "distillery.json", {
        **model(create("fluid_tank"), still_textures, distillery_elements() + raised(distillery_top_elements())),
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -3.5, 0], "scale": [0.4, 0.4, 0.4]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.15, 0.15, 0.15]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, -3, 0], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.25, 0.25, 0.25]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
        },
    })
    dump(items / "distillate.json", {"parent": "minecraft:item/generated",
                                     "textures": {"layer0": "brewery:item/distillate"}})
    # The base model is beer; the `brewery:drink` property picks the others (see ModClientSetup).
    art = ["whiskey", "cider", "mash", "calvados", "vodka", "rum", "wine", "mead", "spoiled",
           "kvass", "stout", "tequila", "absinthe", "firefly"]
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

PANEL = "#b8b4a6"
PANEL_LIGHT = "#dcd8ca"
PANEL_DARK = "#77736a"
INV = "#a09c8e"
INV_LIGHT = "#c4c0b2"
INV_DARK = "#6a665c"
SLOT_FILL = "#5a574e"
DIM = "#8a867a"


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
    d.line((x - 1, y + 16, x - 1, y - 1), fill=rgb("#2e2c26"))
    d.line((x - 1, y - 1, x + 16, y - 1), fill=rgb("#2e2c26"))
    d.line((x + 16, y - 1, x + 16, y + 16), fill=rgb("#dcd8ca"))
    d.line((x - 1, y + 16, x + 16, y + 16), fill=rgb("#dcd8ca"))


def recess(d, x0, y0, x1, y1):
    d.rectangle((x0, y0, x1, y1), fill=rgb("#a8a496"))
    d.line((x0, y1, x0, y0), fill=rgb(PANEL_DARK))
    d.line((x0, y0, x1, y0), fill=rgb(PANEL_DARK))
    d.line((x1, y0, x1, y1), fill=rgb(PANEL_LIGHT))
    d.line((x0, y1, x1, y1), fill=rgb(PANEL_LIGHT))


def display(d, x0, y0, x1, y1):
    """A dark readout in a brass frame; the screen writes on it."""
    d.rectangle((x0, y0, x1, y1), fill=rgb("#1c211e"))
    d.line((x0, y1, x0, y0), fill=rgb(BRASS[1]))
    d.line((x0, y0, x1, y0), fill=rgb(BRASS[1]))
    d.line((x1, y0, x1, y1), fill=rgb(BRASS[4]))
    d.line((x0, y1, x1, y1), fill=rgb(BRASS[4]))
    d.line((x0 + 1, y0 + 1, x1 - 1, y0 + 1), fill=rgb("#101412"))


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
    color = rgb("#6e6b60")
    d.rectangle((x + 7, y + 2, x + 8, y + 5), fill=color)
    d.rectangle((x + 4, y + 6, x + 11, y + 13), fill=color)


def ghost_log(img, x, y):
    d = ImageDraw.Draw(img)
    d.rectangle((x + 3, y + 4, x + 12, y + 11), fill=rgb("#6e6b60"))
    d.rectangle((x + 5, y + 6, x + 10, y + 9), fill=rgb(SLOT_FILL))


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
        img.putpixel((x, y), rgb(rng.choice(("#b2aea0", "#bfbbad"))))
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
    display(d, 8, 52, 122, 66)
    save(img, GUI, "kettle")

    # Distillery: wort tank -> arrow -> distillate.
    img, d = machine_sheet(2)
    tank(d, 12, 18, 34)
    arrow(d, 42, 36, rgb(DIM))
    slot(d, 66, 35)
    ghost_bottle(img, 66, 35)
    display(d, 88, 16, 168, 52)
    save(img, GUI, "distillery")

    # Barrel: distillate + wood -> finished bottle.
    img, d = machine_sheet(3)
    # The gap between the slots is short, so the arrow's tail tucks under the wood slot's frame.
    arrow(d, 92, 36, rgb(DIM))
    for x, y in ((26, 17), (44, 17), (26, 35), (44, 35)):
        slot(d, x, y)
        ghost_bottle(img, x, y)
    slot(d, 80, 35)
    ghost_log(img, 80, 35)
    for x, y in ((116, 17), (134, 17), (116, 35), (134, 35)):
        slot(d, x, y)
    slot(d, 152, 35)
    ghost_bottle(img, 152, 35)
    d.rectangle((64, 41, 76, 45), fill=rgb(DIM))
    display(d, 76, 55, 168, 68)
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
                         (16, {"x": "#77736a", "o": "#858177", "y": "#928e84"})):
        for row, line in enumerate(flame):
            for col, ch in enumerate(line):
                if ch in palette:
                    parts.putpixel((70 + col, top + row), rgb(palette[ch]))
    save(parts, GUI, "machine_parts")


# ---- ponder scenes ------------------------------------------------------------------

def nbt_string(value):
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def nbt_field(tag, name, payload):
    return bytes([tag]) + nbt_string(name) + payload


def nbt_int_list(values):
    return bytes([3]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def nbt_compound_list(items):
    return bytes([10]) + struct.pack(">i", len(items)) + b"".join(items)


def write_scene(name, placed):
    """Writes a 5x4x5 structure: a stone floor plus `placed`, a {(x, y, z): (block, properties)} map."""
    blocks = {(x, 0, z): ("minecraft:smooth_stone", {}) for x in range(5) for z in range(5)}
    blocks.update(placed)
    palette = []
    entries = []
    for pos, state in blocks.items():
        if state not in palette:
            palette.append(state)
        entries.append(nbt_field(9, "pos", nbt_int_list(list(pos)))
                       + nbt_field(3, "state", struct.pack(">i", palette.index(state))) + b"\0")
    palette_nbt = []
    for block, props in palette:
        body = nbt_field(8, "Name", nbt_string(block))
        if props:
            inner = b"".join(nbt_field(8, k, nbt_string(v)) for k, v in props.items()) + b"\0"
            body += nbt_field(10, "Properties", inner)
        palette_nbt.append(body + b"\0")
    data = (bytes([10]) + nbt_string("")
            + nbt_field(3, "DataVersion", struct.pack(">i", 3955))
            + nbt_field(9, "size", nbt_int_list([5, 4, 5]))
            + nbt_field(9, "palette", nbt_compound_list(palette_nbt))
            + nbt_field(9, "blocks", nbt_compound_list(entries))
            + nbt_field(9, "entities", bytes([0]) + struct.pack(">i", 0))
            + b"\0")
    target = ROOT / "ponder" / f"{name}.nbt"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(gzip.compress(data, mtime=0))


def write_ponder():
    fire = ("minecraft:campfire", {"lit": "true", "facing": "north"})
    write_scene("kettle", {
        (2, 1, 2): fire,
        (2, 2, 2): ("brewery:kettle", {"brewing": "false"}),
        (3, 2, 2): ("brewery:spout", {"facing": "east"}),
    })
    write_scene("distillery", {
        (2, 1, 2): fire,
        (2, 2, 2): ("brewery:distillery", {"facing": "south"}),
        (2, 3, 2): ("brewery:distillery_top", {"facing": "south"}),
        (2, 2, 1): ("create:creative_motor", {"facing": "south"}),
        (1, 2, 2): ("create:mechanical_pump", {"facing": "east"}),
        (1, 3, 2): ("create:cogwheel", {"axis": "x"}),
        (0, 3, 2): ("create:creative_motor", {"facing": "east"}),
        (0, 2, 2): ("brewery:kettle", {"brewing": "false"}),
        (0, 1, 2): fire,
    })
    write_scene("barrel", {
        **{(1 + x, 1 + y, 2): ("brewery:barrel", {"facing": "north"}) for x in range(2) for y in range(2)},
        (1, 3, 2): ("create:andesite_funnel", {"facing": "up"}),
        (3, 1, 2): ("brewery:spout", {"facing": "east"}),
    })


def main():
    write_block_textures()
    write_item_textures()
    write_models()
    write_gui()
    write_ponder()


if __name__ == "__main__":
    main()
