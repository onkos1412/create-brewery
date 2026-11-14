"""Drink vessel sprites for the item textures: one silhouette per drink, shaded like vanilla items.

Each sprite is drawn as a 16x16 character map. A letter names the material of that pixel; the
renderer adds a 1-pixel coloured outline around the shape, shades every material from light on
the left to dark on the right (the light comes from the top left, as on vanilla items), puts a
glint on glass, and draws the surface of a liquid one tone lighter.

Materials:
  g  glass (the bottle itself, or the empty part above a drink in clear glass)
  l  liquid seen through the glass
  c  cap, cork or stopper           w  wax seal
  m  metal (wire, crown, rope)      b  label paper    e  label print
  h  clay or wood body              H  handle (same material as h, never glinted)
  s  sparkle: a single bright pixel, never shaded
  k  crack: a single dark pixel, never shaded
"""
from PIL import Image

SHADED = "gclwmbehH"
GLINT = "gl"


def _rgb(hex_color):
    h = hex_color.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def _mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def render(rows, palette, outline_from="g"):
    """Renders a character map. `palette` maps a material letter to (light, mid, dark) or one colour."""
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    mask = {(x, y) for y, r in enumerate(rows) for x, ch in enumerate(r) if ch != "."}
    ramps = {k: tuple(_rgb(c) for c in (v if isinstance(v, (tuple, list)) else (v, v, v)))
             for k, v in palette.items() if len(k) == 1}

    def run(x, y):
        # The horizontal stretch of the vessel this pixel belongs to, handles left out.
        left = right = x
        while (left - 1, y) in mask and rows[y][left - 1] != "H":
            left -= 1
        while (right + 1, y) in mask and rows[y][right + 1] != "H":
            right += 1
        return left, right

    for (x, y) in mask:
        ch = rows[y][x]
        light, mid, dark = ramps[ch]
        if ch not in SHADED:
            img.putpixel((x, y), light)
            continue
        left, right = run(x, y) if ch != "H" else (x, x)
        t = 0.5 if right == left else (x - left) / (right - left)
        color = light if t < 0.3 else dark if t > 0.72 else mid
        below = (x, y + 1) not in mask
        if below and ch != "H":
            color = dark
        # The surface of a drink in clear glass catches the light.
        if ch == "l" and y > 0 and rows[y - 1][x] == "g" and palette.get("clear"):
            color = _mix(light, (255, 255, 255, 255), 0.35)
        # A glint down the left of the glass.
        if ch in GLINT and right - left >= 3 and x == left + 1 and not below:
            color = _mix(light, (255, 255, 255, 255), 0.55)
        img.putpixel((x, y), color)

    if palette.get("clear") and outline_from == "g":
        outline_from = "l"
    base = ramps[outline_from][2]
    outline = _mix(base, (12, 10, 9, 255), 0.6)
    for y in range(16):
        for x in range(16):
            if (x, y) in mask:
                continue
            if any((x + dx, y + dy) in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.putpixel((x, y), outline)
    return img


# ---- palettes ------------------------------------------------------------------------------

CORK = ("#c9a26a", "#a87d45", "#7a5628")
GOLD = ("#fbe58a", "#e0b443", "#9a6e1e")
STEEL = ("#e2e6ea", "#a9b0b8", "#6c737c")
PAPER = ("#f6efd9", "#e2d5b0", "#bba97f")
CLEAR = ("#eef6f8", "#cfe0e6", "#9fb8c2")
RED_WAX = ("#e0564a", "#b02a20", "#6e1610")


def drinks():
    """Every drink's sprite, by texture name."""
    return {
        "beer_bottle": render([
            "................",
            "......mmmm......",
            ".......gg.......",
            ".......gg.......",
            ".......gg.......",
            "......gggg......",
            ".....gggggg.....",
            ".....gggggg.....",
            ".....bbbbbb.....",
            ".....beeeeb.....",
            ".....bbbbbb.....",
            ".....gggggg.....",
            ".....gggggg.....",
            ".....gggggg.....",
            ".....gggggg.....",
            "................",
        ], {"g": ("#c98a3a", "#94561c", "#5e320c"), "m": GOLD, "b": PAPER,
            "e": ("#e0564a", "#c0352a", "#7a1a14")}),

        "stout_bottle": render([
            "................",
            "................",
            "......mmmm......",
            ".......gg.......",
            "......gggg......",
            ".....gggggg.....",
            "....gggggggg....",
            "....gggggggg....",
            "....bbbbbbbb....",
            "....beebbeeb....",
            "....bbbbbbbb....",
            "....gggggggg....",
            "....gggggggg....",
            "....gggggggg....",
            ".....gggggg.....",
            "................",
        ], {"g": ("#4a3a30", "#2a1e17", "#140d09"), "m": GOLD, "b": ("#f0e2bc", "#d9c592", "#a8945e"),
            "e": ("#c9a040", "#b58a2e", "#8a6420")}),

        "kvass_bottle": render([
            "................",
            "......cccc......",
            "......hhhh......",
            ".....hhhhhh.....",
            ".....hhhhhh.HH..",
            "....hhhhhhhh.H..",
            "...hhhhhhhhhHH..",
            "...hhhhhhhhh.H..",
            "...heeeeeeeh.H..",
            "...hhhhhhhhhHH..",
            "...hhhhhhhhh....",
            "...hhhhhhhhh....",
            "...hhhhhhhhh....",
            "....hhhhhhh.....",
            ".....hhhhh......",
            "................",
        ], {"h": ("#d08a52", "#a8622e", "#6e3a16"), "H": ("#b8723c", "#b8723c", "#8a4f22"),
            "c": CORK, "e": ("#f2e3c4", "#e8d6ae", "#c9b386")}, outline_from="h"),

        "cider_bottle": render([
            "................",
            "......cccc......",
            ".....mcccm......",
            ".......gg.......",
            "......mggm......",
            "......gggg......",
            ".....gggggg.....",
            "....gggggggg....",
            "....bbbbbbbb....",
            "....beeeeeeb....",
            "....bbbbbbbb....",
            "....gggggggg....",
            "....gggggggg....",
            ".....gggggg.....",
            "......gggg......",
            "................",
        ], {"g": ("#9ccf6a", "#5f9a3a", "#33621f"), "c": ("#ffffff", "#e8e8e2", "#b8b8ae"), "m": STEEL,
            "b": PAPER, "e": ("#d84a3a", "#c0352a", "#7a1a14")}),

        "mead_bottle": render([
            "................",
            ".......ww.......",
            "......wwww......",
            ".......wg.......",
            ".......gg.......",
            "......gggg......",
            "....gggggggg....",
            "...llllllllll...",
            "...llllllllll...",
            "...lbbbbbbbbl...",
            "...lbeeeeeebl...",
            "...lbbbbbbbbl...",
            "...llllllllll...",
            "....llllllll....",
            ".....llllll.....",
            "................",
        ], {"g": CLEAR, "clear": True, "l": ("#ffd25a", "#e8a823", "#a8700f"), "w": ("#f0b040", "#c8841c", "#8a560c"),
            "b": PAPER, "e": ("#6a3a14", "#6a3a14", "#6a3a14")}),

        "wine_bottle": render([
            "................",
            ".......mm.......",
            ".......mm.......",
            ".......mm.......",
            ".......gg.......",
            ".......gg.......",
            "......gggg......",
            ".....gggggg.....",
            ".....gggggg.....",
            ".....bbbbbb.....",
            ".....bebbeb.....",
            ".....bbbbbb.....",
            ".....gggggg.....",
            ".....gggggg.....",
            ".....gggggg.....",
            "................",
        ], {"g": ("#9a2a48", "#661430", "#3a0818"), "m": ("#e86a7a", "#b8283c", "#701424"), "b": PAPER,
            "e": ("#c9a040", "#c9a040", "#c9a040")}),

        "calvados_bottle": render([
            "................",
            ".......cc.......",
            "......cccc......",
            ".......cc.......",
            ".......gg.......",
            ".......gg.......",
            ".....gggggg.....",
            "....llllllll....",
            "...llllllllll...",
            "...llllbbllll...",
            "...llllbbllll...",
            "...llllllllll...",
            "....llllllll....",
            ".....llllll.....",
            "......bbbb......",
            "................",
        ], {"g": CLEAR, "clear": True, "c": CLEAR, "l": ("#f0a050", "#c96a24", "#86400e"),
            "b": ("#ff6a5a", "#d0302a", "#8a1810")}),

        "whiskey_bottle": render([
            "................",
            "......wwww......",
            "......wwww......",
            ".......ww.......",
            "......gggg......",
            "...llllllllll...",
            "...llllllllll...",
            "...llllllllll...",
            "...bbbbbbbbbb...",
            "...beeeeeeeeb...",
            "...bbbbbbbbbb...",
            "...llllllllll...",
            "...llllllllll...",
            "...llllllllll...",
            "...llllllllll...",
            "................",
        ], {"g": CLEAR, "clear": True, "l": ("#f2a640", "#c8761c", "#7c430c"), "w": RED_WAX,
            "b": ("#2a3a4e", "#1d2a3a", "#121b26"), "e": ("#fbe58a", "#efcf6a", "#b58a2e")}),

        "rum_bottle": render([
            "................",
            ".......cc.......",
            ".......cc.......",
            ".......gg.......",
            "......mmmm......",
            ".......gg.......",
            ".....gggggg.....",
            "...gggggggggg...",
            "..gggggggggggg..",
            "..gbbbbbbbbbbg..",
            "..gbeeeeeeeebg..",
            "..gbbbbbbbbbbg..",
            "..gggggggggggg..",
            "...gggggggggg...",
            "....gggggggg....",
            "................",
        ], {"g": ("#7a4a2a", "#4e2a14", "#2a1408"), "c": CORK, "m": ("#e0c890", "#b89a5c", "#7a6234"),
            "b": PAPER, "e": ("#3a2a20", "#2a1d14", "#1a120c")}),

        "vodka_bottle": render([
            "................",
            "......mmmm......",
            "......mmmm......",
            ".......gg.......",
            ".......gg.......",
            "......gggg......",
            "......llll......",
            "......llll......",
            "......llll......",
            "......bbbb......",
            "......eeee......",
            "......bbbb......",
            "......llll......",
            "......llll......",
            "......llll......",
            "................",
        ], {"g": CLEAR, "clear": True, "l": ("#ffffff", "#e6f2f6", "#b6ccd4"), "m": ("#ff7a6a", "#d0302a", "#8a1810"),
            "b": ("#ffffff", "#f2f0ea", "#c8c4b8"), "e": ("#e0463a", "#c0302a", "#801810")}),

        "tequila_bottle": render([
            "................",
            ".......cc.......",
            "......cccc......",
            ".......gg.......",
            ".......gg.......",
            "...llllllllll...",
            "...llllllllll...",
            "....llllllll....",
            "....bbbbbbbb....",
            "....beeeeeeb....",
            "....bbbbbbbb....",
            ".....llllll.....",
            ".....llllll.....",
            ".....llllll.....",
            "....llllllll....",
            "................",
        ], {"g": CLEAR, "clear": True, "c": CORK, "l": ("#fbe6a0", "#e8c870", "#b0903e"), "b": PAPER,
            "e": ("#4a8a3a", "#2f6a26", "#1d4416")}),

        "absinthe_bottle": render([
            "................",
            ".......cc.......",
            ".......cc.......",
            ".......gg.......",
            ".......gg.......",
            "......gggg......",
            ".....gggggg.....",
            "....gggggggg....",
            "....gggggggg....",
            "....bbbbbbbb....",
            "....beeeeeeb....",
            "....bbbbbbbb....",
            "....gggggggg....",
            "....gggggggg....",
            "....gggggggg....",
            "................",
        ], {"g": ("#b6ec6a", "#6cb030", "#36681a"), "c": CORK, "b": ("#1d2a1a", "#152014", "#0c140c"),
            "e": ("#e8f8b0", "#d8e8a0", "#a8b870")}),

        "firefly_bottle": render([
            "................",
            ".......cc.......",
            ".......gg.......",
            ".......gg.......",
            "......gggg......",
            "....gggggggg....",
            "...llllllllll...",
            "..llllsllllsll..",
            "..llllllllllll..",
            "..lslllllllsll..",
            "..llllllslllll..",
            "..llllllllllll..",
            "...llsllllllll..",
            "....llllllll....",
            ".....llllll.....",
            "................",
        ], {"g": CLEAR, "clear": True, "c": CORK, "l": ("#ffd84a", "#f6a020", "#b8620c"), "s": "#fffbe0"}),

        "distillate": render([
            "................",
            "......cccc......",
            ".......gg.......",
            ".......gg.......",
            ".......gg.......",
            "......gggg......",
            "......gggg......",
            ".....gggggg.....",
            ".....llllll.....",
            "....llllllll....",
            "....llllllll....",
            "...llllllllll...",
            "...llllllllll...",
            "..llllllllllll..",
            "..llllllllllll..",
            "................",
        ], {"g": CLEAR, "clear": True, "c": CORK, "l": ("#fbf2d0", "#ecdca4", "#c4ae70")}),

        "mash_bottle": render([
            "................",
            "....cccccccc....",
            "...cccccccccc...",
            "....mmmmmmmm....",
            "....gggggggg....",
            "...llllllllll...",
            "...llllllllll...",
            "...llbbbbblll...",
            "...llbeeeblll...",
            "...llbbbbblll...",
            "...llllllllll...",
            "...llllllllll...",
            "...llllllllll...",
            "...llllllllll...",
            "...llllllllll...",
            "................",
        ], {"g": CLEAR, "clear": True, "c": ("#e8dcc0", "#c9b48c", "#957e54"), "m": ("#a87d45", "#7a5628", "#4c3416"),
            "l": ("#d8c088", "#b09858", "#7a6634"), "b": PAPER, "e": ("#5a4628", "#5a4628", "#5a4628")}),

        "spoiled_bottle": render([
            "................",
            ".......cc.......",
            ".......gg.......",
            ".......gg.......",
            "......gggg......",
            ".....gggggg.....",
            ".....llllll.....",
            ".....lllkll.....",
            ".....llkkll.....",
            ".....lllkll.....",
            ".....llllkl.....",
            ".....lslllll....",
            ".....llllll.....",
            ".....llllsl.....",
            ".....llllll.....",
            "................",
        ], {"g": ("#a8b48a", "#7a865c", "#4c5636"), "c": ("#8a7a5a", "#6a5a3a", "#3e3420"),
            "l": ("#8a9a3a", "#5c6a20", "#36400e"), "k": "#1c2008", "s": "#c8d870"}),
    }


def mug(filled_hint=False):
    """The wooden mug: rim on top, the inside (where the drink shows) below the rim, iron hoops."""
    rows = [
        "................",
        "................",
        "..cccccccccc....",
        "..cddddddddc....",
        "..cddddddddc....",
        "..hhhhhhhhhh....",
        "..mmmmmmmmmmHH..",
        "..hhhhhhhhhh.H..",
        "..hhhhhhhhhh.H..",
        "..hhhhhhhhhh.H..",
        "..hhhhhhhhhh.H..",
        "..mmmmmmmmmmHH..",
        "..hhhhhhhhhh....",
        "..hhhhhhhhhh....",
        "...hhhhhhhh.....",
        "................",
    ]
    oak = ("#bf9c5f", "#8f6d3b", "#5a401f")
    return render(rows, {"c": ("#d8b77a", "#b8955a", "#7a5a2c"), "d": "#2e1e0e", "h": oak,
                         "H": ("#a9854c", "#a9854c", "#75552c"), "m": ("#9aa0a8", "#70757d", "#3a3d43")},
                  outline_from="h")


def mug_liquid():
    """The drink inside the mug, in greys for the item colour handler: froth at the rim, a glint."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(3, 11):
        img.putpixel((x, 3), _rgb("#ffffff"))
        img.putpixel((x, 4), _rgb("#c4c4c4"))
    for x in (4, 7, 9):
        img.putpixel((x, 4), _rgb("#e6e6e6"))
    img.putpixel((3, 4), _rgb("#e0e0e0"))
    return img


def flat(rows, colors):
    """Paints a character map pixel for pixel, with no outline or shading added."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), _rgb(colors[ch]))
    return img


def book():
    """The brewer's book: the vanilla book's pose (lying at an angle, cover on top, page edges below),
    redrawn in the mod's colours, with brass corners and clasp in Create's style and a little amber
    bottle tooled into the cover."""
    return flat([
        "................",
        "........ooo.....",
        "......ooCYCo....",
        "....ooCCcccco...",
        "..ooCccYccccdo..",
        "ooCccccaccccddo.",
        "oYccccaaacccccdo",
        "osccccaaacccddp.",
        "osPcccaaacddpPp.",
        "osPpsdccddpPPpYo",
        ".osPpsddpPPpoYoo",
        "..osPppPPpoooo..",
        "...osPppPpoo....",
        "....osPpoo......",
        ".....ooo........",
        "................",
    ], {"o": "#2a1608", "C": "#b0703c", "c": "#8a4e24", "d": "#683614", "s": "#4a2410",
        "P": "#f4ead2", "p": "#c8b48a", "Y": "#efcf6a", "a": "#f0a030"})
