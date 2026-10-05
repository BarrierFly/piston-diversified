#!/usr/bin/env python3
"""Asset generator for Piston Diversified (mod v1 batch).

Derives all textures from the vanilla 1.21.11 client jar, generates blockstates, models,
item models (both 1.21.4+ item definitions and classic 1.19.4 item models), per-version
recipes + loot tables (data pack formats differ), lang files and the mod icon.

Usage: python tools/gen_assets.py [path/to/1.21.11.jar]
"""
import json
import os
import sys
import zipfile
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
WORKSPACE = os.path.dirname(MOD)
JAR_DEFAULT = os.path.join(WORKSPACE, "minecraft versions", "1.21.11", "1.21.11.jar")

NS = "piston_diversified"

# ---------------------------------------------------------------- palette utils

def tint(img, color, factor):
    """Blend every pixel toward color by factor (0..1)."""
    out = img.copy().convert("RGBA")
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            px[x, y] = (
                int(r + (color[0] - r) * factor),
                int(g + (color[1] - g) * factor),
                int(b + (color[2] - b) * factor),
                a,
            )
    return out

def recolor_greenish(img, color):
    """Recolor the green slime pattern of piston_top_sticky to honey amber."""
    out = img.copy().convert("RGBA")
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a and g > r + 8 and g >= b:
                px[x, y] = (color[0], color[1], color[2], a)
    return out

def band(img, y0, y1, color, factor):
    """Blend a horizontal band of the image toward color."""
    out = img.copy().convert("RGBA")
    px = out.load()
    for y in range(y0, y1):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            px[x, y] = (
                int(r + (color[0] - r) * factor),
                int(g + (color[1] - g) * factor),
                int(b + (color[2] - b) * factor),
                a,
            )
    return out

def blend_with(img, other, factor):
    out = img.copy().convert("RGBA")
    other = other.convert("RGBA").resize(img.size)
    return Image.blend(out, other, factor)

HONEY = (237, 160, 55)
TEAL = (30, 140, 140)
PURPLE = (140, 70, 190)
WOOLISH = (235, 235, 235)
ICEBLUE = (145, 183, 245)
REDSTONE = (170, 30, 30)
ROD_BEIGE = (219, 223, 211)

# ---------------------------------------------------------------- 3x5 mini font

FONT = {
    "Q": ["###", "#.#", "#.#", "###", "..#"],
    "C": ["###", "#..", "#..", "#..", "###"],
    "S": ["###", "#..", "###", "..#", "###"],
    "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "1": ["..#", ".##", "..#", "..#", ".##"],
    "2": ["###", "..#", "###", "#..", "###"],
    "3": ["###", "..#", "###", "..#", "###"],
}

# Pickaxe materials → tint of the drawn icon (blockstate TOOL drives the plate texture)
PICKAXE_TINTS = {
    "wooden": (157, 128, 79),
    "stone": (129, 129, 129),
    "iron": (216, 216, 216),
    "golden": (252, 205, 84),
    "diamond": (86, 219, 219),
    "netherite": (68, 68, 74),
}

# 8x8 pickaxe silhouette (af2023-style), drawn centred on the plate
PICKAXE_ICON = [
    "........",
    "..###...",
    ".#####..",
    "..###.##",
    "...##..#",
    "..###...",
    ".###....",
    "........",
]

# 6x9 arrow, oriented per bend direction (arrow points at the bend). Big enough to read on a
# 16x16 piston plate at a glance — the previous 5x5 mark was a speck once blitted 1:1.
ARROW_ICON = [
    "...#...",
    "...#...",
    "..###..",
    "..###..",
    ".#####.",
    "#######",
    "...#...",
    "...#...",
    "...#...",
]


def _blit(img, icon, ox, oy, color):
    """Draw a pixel-art icon onto img at (ox, oy), one texture pixel per icon cell.

    Each cell is filled plus outlined in near-black: the piston plate is busy wood grain, and a
    flat colour alone disappeared into it. The outline is what makes the mark readable.
    """
    d = ImageDraw.Draw(img)
    h = len(icon)
    w = len(icon[0])
    outline = (16, 16, 20, 255)
    for ry, row in enumerate(icon):
        for rx, c in enumerate(row):
            if c != "#":
                continue
            x, y = ox + rx, oy + ry
            d.rectangle([x, y, x, y], fill=color)
    # outline pass: darken the border of every filled cell so the shape separates from the grain
    px = img.load()
    for ry in range(h):
        for rx in range(w):
            if icon[ry][rx] != "#":
                continue
            x, y = ox + rx, oy + ry
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < img.width and 0 <= ny < img.height:
                    if px[nx, ny][:3] != color[:3]:
                        px[nx, ny] = outline


def _draw_pickaxe_icon(img, tool, color):
    _blit(img, PICKAXE_ICON, 4, 4, (*color, 255))


def _rotate(icon, turns):
    """Rotate a square-ish icon 90° clockwise, `turns` times."""
    out = [list(row) for row in icon]
    for _ in range(turns % 4):
        out = [list(row) for row in zip(*out[::-1])]
    return out


def _draw_bend_arrow(img, bend):
    """Arrow on the plate pointing at the bend direction, on all six faces.

    The icon is authored pointing north and rotated from there, so every direction is the same
    shape — the earlier branch drew east/west sideways marks for the up/down faces.
    """
    turns = {"north": 0, "east": 1, "south": 2, "west": 3, "up": 3, "down": 1}[bend]
    arrow = _rotate(ARROW_ICON, turns)
    h = len(arrow)
    w = len(arrow[0])
    # centre it on the 16x16 plate whatever the rotated footprint is
    _blit(img, arrow, (16 - w) // 2, (16 - h) // 2, (245, 245, 250, 255))


def _draw_creaking_heart(img, origin, tint=(196, 62, 74)):
    """A small creaking-heart mark (规划 v3 §31: one per tier on the plate)."""
    icon = [
        ".##.##.",
        "#######",
        "#######",
        ".#####.",
        "..###..",
        "...#...",
    ]
    _blit(img, icon, origin[0], origin[1], (*tint, 255))


def draw_text(img, text, ox, oy, scale, color=(255, 255, 255, 255), shadow=(20, 20, 20, 255)):
    d = ImageDraw.Draw(img)
    cx = ox
    for ch in text:
        rows = FONT[ch]
        for ry, row in enumerate(rows):
            for rx, c in enumerate(row):
                if c == "#":
                    x0 = cx + rx * scale
                    y0 = oy + ry * scale
                    d.rectangle([x0 + 1, y0 + 1, x0 + scale, y0 + scale], fill=shadow)
                    d.rectangle([x0, y0, x0 + scale - 1, y0 + scale - 1], fill=color)
        cx += (len(rows[0]) + 1) * scale
    return img

def draw_rod_texture(flame=False):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(5, 11):
            base = ROD_BEIGE
            if x in (5, 10):
                base = (172, 178, 162)
            elif x in (7, 8):
                base = (238, 241, 231)
            px[x, y] = (*base, 255)
    if flame:
        for y in range(0, 4):
            for x in range(5, 11):
                px[x, y] = (255, 96 + y * 30, 20, 255)
        px[7, 0] = (255, 240, 160, 255)
        px[8, 0] = (255, 220, 120, 255)
    return img

# ---------------------------------------------------------------- vanilla sources

class Vanilla:
    def __init__(self, jar_path):
        self.z = zipfile.ZipFile(jar_path)

    def texture(self, name):
        return Image.open(self.z.open(f"assets/minecraft/textures/{name}.png")).convert("RGBA")

# ---------------------------------------------------------------- variant table

def build_textures(v: Vanilla):
    """Returns {texture_name: PIL.Image} for every texture the mod needs."""
    src = {
        "side": v.texture("block/piston_side"),
        "top": v.texture("block/piston_top"),
        "bottom": v.texture("block/piston_bottom"),
        "inner": v.texture("block/piston_inner"),
        "sticky": v.texture("block/piston_top_sticky"),
        "end_rod": v.texture("block/end_rod"),
        "dispenser_front": v.texture("block/dispenser_front"),
        "ice": v.texture("block/ice"),
        "wool": v.texture("block/white_wool"),
        "observer_front": v.texture("block/observer_front"),
        "wind_charge": v.texture("item/wind_charge"),
        "creaking_heart": v.texture("block/creaking_heart"),
        "potatoes": v.texture("block/potatoes_stage3"),
        "mud": v.texture("block/mud"),
        "granite": v.texture("block/granite"),
        "water": v.texture("block/water_still"),
    }

    out = {}

    def emit(name, side, top, bottom, inner, sticky=None, rod=None, extra=None):
        out[f"{name}_side"] = side
        out[f"{name}_top"] = top
        out[f"{name}_bottom"] = bottom
        out[f"{name}_inner"] = inner
        if sticky is not None:
            out[f"{name}_top_sticky"] = sticky
        if rod is not None:
            out[f"{name}_rod"] = rod
        if extra:
            out.update(extra)

    vanilla_set = dict(side=src["side"], top=src["top"], bottom=src["bottom"], inner=src["inner"])

    # 蜂蜜活塞: slime pattern recolored to honey
    emit("honey_piston", **vanilla_set, sticky=recolor_greenish(src["sticky"], HONEY))

    # 抛射活塞: dispenser face on the plate
    emit("projectile_piston", **{**vanilla_set, "top": src["dispenser_front"]})

    # 连锁型: teal filter everywhere
    emit("chain_piston", **{k: tint(img, TEAL, 0.45) for k, img in vanilla_set.items()},
         sticky=tint(src["sticky"], TEAL, 0.45))
    emit("chain_sticky_piston", **{k: tint(img, TEAL, 0.45) for k, img in vanilla_set.items()},
         sticky=tint(src["sticky"], TEAL, 0.45))

    # 循环型: purple filter
    emit("loop_piston", **{k: tint(img, PURPLE, 0.45) for k, img in vanilla_set.items()})

    # 风弹活塞: wind charge sprite on the plate
    wind_top = src["top"].copy()
    sprite = src["wind_charge"].resize((12, 12), Image.NEAREST)
    wind_top.alpha_composite(sprite, (2, 2))
    emit("wind_charge_piston", **{**vanilla_set, "top": wind_top})

    # 静音活塞: wool on the inner face, the plate-side band of the sides, and the rim of the plate
    silent_side = band(src["side"], 0, 4, WOOLISH, 0.85)
    silent_top = src["top"].copy()
    d = ImageDraw.Draw(silent_top)
    d.rectangle([0, 0, 15, 1], fill=(228, 228, 228, 255))
    d.rectangle([0, 14, 15, 15], fill=(228, 228, 228, 255))
    d.rectangle([0, 0, 1, 15], fill=(228, 228, 228, 255))
    d.rectangle([14, 0, 15, 15], fill=(228, 228, 228, 255))
    emit("silent_piston", side=silent_side, top=silent_top, bottom=src["bottom"],
         inner=blend_with(src["inner"], src["wool"], 0.8))

    # 后坐活塞: vanilla textures (custom geometry only)
    emit("recoil_piston", **vanilla_set)

    # 头颅活塞: observer face on the plate (+excited variant)
    excited = src["observer_front"].copy()
    d = ImageDraw.Draw(excited)
    d.rectangle([3, 4, 5, 7], fill=(255, 60, 40, 255))
    d.rectangle([10, 4, 12, 7], fill=(255, 60, 40, 255))
    d.rectangle([6, 10, 9, 12], fill=(255, 90, 60, 255))
    emit("skull_piston", **{**vanilla_set, "top": src["observer_front"]},
         extra={"skull_piston_top_powered": excited})

    # 随朝向QC活塞: QC lettering on the plate
    qc_top = draw_text(src["top"].copy(), "QC", 2, 5, 1)
    qc_sticky = draw_text(src["sticky"].copy(), "QC", 2, 5, 1)
    emit("qc_piston", **{**vanilla_set, "top": qc_top})
    emit("qc_sticky_piston", **vanilla_set, sticky=qc_sticky)

    # 侦测器活塞: observer face on the base back
    emit("observer_piston", **{**vanilla_set, "bottom": src["observer_front"]})
    emit("observer_sticky_piston", **{**vanilla_set, "bottom": src["observer_front"]},
         sticky=src["sticky"])

    # 活塞红石端杆: custom rod + flame
    emit("redstone_end_rod_piston", **vanilla_set, rod=draw_rod_texture(flame=True))

    # 活塞端杆: custom rod
    emit("end_rod_piston", **vanilla_set, rod=draw_rod_texture(flame=False))

    # 长推活塞: redstone-tinted arm band on the sides + inner
    emit("long_push_piston",
         side=band(src["side"], 0, 5, REDSTONE, 0.75),
         top=src["top"], bottom=src["bottom"],
         inner=band(src["inner"], 0, 16, REDSTONE, 0.35))

    # 虚弱活塞: vanilla textures (2x2 rod is geometry)
    emit("weak_piston", **vanilla_set)

    # 快速活塞: ice-blended sides/inner (rod band)
    ice_side = blend_with(band(src["side"], 0, 5, ICEBLUE, 0.8), src["ice"], 0.25)
    ice_inner = blend_with(src["inner"], src["ice"], 0.45)
    emit("fast_piston", side=ice_side, top=src["top"], bottom=src["bottom"], inner=ice_inner)
    emit("fast_sticky_piston", side=ice_side, top=src["top"], bottom=src["bottom"], inner=ice_inner,
         sticky=src["sticky"])

    # ---- v2 variants ----

    # 马铃薯活塞: floatater slime face (af2024) on the plate
    floatater = Image.open(os.path.join(HERE, "template_textures", "floatater_front.png")).convert("RGBA")
    emit("potato_piston", **{**vanilla_set, "top": floatater})

    # 镐活塞: one plate per pickaxe material (TOOL blockstate selects it)
    pickaxe_tops = {}
    for tool, tint_color in PICKAXE_TINTS.items():
        tool_top = src["top"].copy()
        _draw_pickaxe_icon(tool_top, tool, tint_color)
        pickaxe_tops[tool] = tool_top
    # the default state (iron) doubles as the plain "<vid>_top"
    emit("pickaxe_piston", **{**vanilla_set, "top": pickaxe_tops["iron"]},
         extra={f"pickaxe_piston_top_{tool}": top for tool, top in pickaxe_tops.items()})

    # 递推活塞: plate + bare-rod base model (custom geometry, vanilla textures)
    emit("recursive_piston", **vanilla_set)
    emit("recursive_sticky_piston", **vanilla_set, sticky=src["sticky"])

    # 拐推活塞: arrow on the plate, pointing at the *model-local* bend direction (6 variants);
    # the blockstate maps each (facing, bend) pair onto one of these.
    # The sticky variant needs BOTH sets: its retracted base and its normal-typed head show the
    # plain arrow, its sticky plate shows the slime-textured one.
    for lb in ("north", "south", "west", "east", "up", "down"):
        arrow_top = src["top"].copy()
        _draw_bend_arrow(arrow_top, lb)
        arrow_sticky = src["sticky"].copy()
        _draw_bend_arrow(arrow_sticky, lb)
        emit("turn_push_piston", **{**vanilla_set, "top": arrow_top},
             extra={f"turn_push_piston_top_{lb}": arrow_top,
                    f"turn_push_sticky_piston_top_{lb}": arrow_top,
                    f"turn_push_sticky_piston_top_sticky_{lb}": arrow_sticky})
    # base textures for the sticky variant come from the same emit; give the sticky variant its
    # own plain set so emit() does not overwrite shared side/bottom textures
    emit("turn_push_sticky_piston", **vanilla_set, sticky=src["sticky"])

    # 墙并活塞: wall-post rod (custom geometry, vanilla textures)
    emit("wall_merge_piston", **vanilla_set)
    emit("wall_merge_sticky_piston", **vanilla_set, sticky=src["sticky"])

    # 0t计划刻活塞: "ST" on the plate
    st_top = draw_text(src["top"].copy(), "ST", 2, 5, 1)
    st_sticky = draw_text(src["sticky"].copy(), "ST", 2, 5, 1)
    emit("st_piston", **{**vanilla_set, "top": st_top})
    emit("st_sticky_piston", **vanilla_set, sticky=st_sticky)

    # 重力活塞: gravel patch on the base side texture
    emit("gravity_piston", side=blend_with(src["side"], src["granite"], 0.45),
         top=src["top"], bottom=src["bottom"], inner=src["inner"])

    # 强力活塞: 1~3 creaking hearts on the plate
    for tier in (1, 2, 3):
        hearts = src["top"].copy()
        for i in range(tier):
            _draw_creaking_heart(hearts, (3 + i * 5, 4))
        emit(f"strong_piston_{tier}", **{**vanilla_set, "top": hearts})

    return out

# ---------------------------------------------------------------- variants metadata

# (id, sticky, custom, head_kind)
# head_kind: "plate" (vanilla geometry), "end_rod", "weak", "recoil", "skull",
#            "bare_rod", "wall_rod", "bent" (arrow plate, BEND blockstate), "gravity"
VARIANTS = [
    ("honey_piston", True, False, "plate"),
    ("projectile_piston", False, False, "plate"),
    ("chain_piston", False, False, "plate"),
    ("chain_sticky_piston", True, False, "plate"),
    ("loop_piston", False, False, "plate"),
    ("wind_charge_piston", False, False, "plate"),
    ("silent_piston", False, False, "plate"),
    ("recoil_piston", False, True, "recoil"),
    ("end_rod_piston", False, True, "end_rod"),
    ("skull_piston", False, False, "skull"),
    ("qc_piston", False, False, "plate"),
    ("qc_sticky_piston", True, False, "plate"),
    ("observer_piston", False, False, "plate"),
    ("observer_sticky_piston", True, False, "plate"),
    ("redstone_end_rod_piston", False, True, "end_rod"),
    ("long_push_piston", False, False, "plate"),
    ("weak_piston", False, False, "weak"),
    ("fast_piston", False, False, "plate"),
    ("fast_sticky_piston", True, False, "plate"),
    # ---- v2 ----
    ("potato_piston", False, False, "plate"),
    ("pickaxe_piston", False, False, "pickaxe"),
    ("recursive_piston", False, True, "bare_rod"),
    ("recursive_sticky_piston", True, True, "bare_rod"),
    ("turn_push_piston", False, True, "bent"),
    ("turn_push_sticky_piston", True, True, "bent"),
    ("wall_merge_piston", False, True, "wall_rod"),
    ("wall_merge_sticky_piston", True, True, "wall_rod"),
    ("st_piston", False, False, "plate"),
    ("st_sticky_piston", True, False, "plate"),
    ("gravity_piston", False, False, "gravity"),
    ("strong_piston_1", False, False, "none"),
    ("strong_piston_2", False, False, "none"),
    ("strong_piston_3", False, False, "none"),
]

LANG_EN = {
    "honey_piston": "Honey Piston",
    "projectile_piston": "Projectile Piston",
    "chain_piston": "Chain Piston",
    "chain_sticky_piston": "Chain Sticky Piston",
    "loop_piston": "Loop Piston",
    "wind_charge_piston": "Wind Charge Piston",
    "silent_piston": "Silent Piston",
    "recoil_piston": "Recoil Piston",
    "end_rod_piston": "Piston End Rod",
    "skull_piston": "Skull Piston",
    "qc_piston": "Directional QC Piston",
    "qc_sticky_piston": "Directional QC Sticky Piston",
    "observer_piston": "Observer Piston",
    "observer_sticky_piston": "Observer Sticky Piston",
    "redstone_end_rod_piston": "Piston Redstone End Rod",
    "long_push_piston": "Long Push Piston",
    "weak_piston": "Weak Piston",
    "fast_piston": "Fast Piston",
    "fast_sticky_piston": "Fast Sticky Piston",
    "potato_piston": "Potato Piston",
    "pickaxe_piston": "Pickaxe Piston",
    "recursive_piston": "Recursive Piston",
    "recursive_sticky_piston": "Recursive Sticky Piston",
    "turn_push_piston": "Turn Push Piston",
    "turn_push_sticky_piston": "Turn Push Sticky Piston",
    "wall_merge_piston": "Wall Merge Piston",
    "wall_merge_sticky_piston": "Wall Merge Sticky Piston",
    "st_piston": "0-Tick Piston",
    "st_sticky_piston": "0-Tick Sticky Piston",
    "gravity_piston": "Gravity Piston",
    "strong_piston_1": "Strong Piston I",
    "strong_piston_2": "Strong Piston II",
    "strong_piston_3": "Strong Piston III",
}

LANG_ZH = {
    "honey_piston": "蜂蜜活塞",
    "projectile_piston": "抛射活塞",
    "chain_piston": "连锁型活塞",
    "chain_sticky_piston": "连锁型黏塞",
    "loop_piston": "循环型活塞",
    "wind_charge_piston": "风弹活塞",
    "silent_piston": "静音活塞",
    "recoil_piston": "后坐活塞",
    "end_rod_piston": "活塞端杆",
    "skull_piston": "头颅活塞",
    "qc_piston": "随朝向QC活塞",
    "qc_sticky_piston": "随朝向QC黏塞",
    "observer_piston": "侦测器活塞",
    "observer_sticky_piston": "侦测器黏塞",
    "redstone_end_rod_piston": "活塞红石端杆",
    "long_push_piston": "长推活塞",
    "weak_piston": "虚弱活塞",
    "fast_piston": "快速活塞",
    "fast_sticky_piston": "快速黏塞",
    "potato_piston": "马铃薯活塞",
    "pickaxe_piston": "镐活塞",
    "recursive_piston": "递推活塞",
    "recursive_sticky_piston": "递推黏塞",
    "turn_push_piston": "拐推活塞",
    "turn_push_sticky_piston": "拐推黏塞",
    "wall_merge_piston": "墙并活塞",
    "wall_merge_sticky_piston": "墙并黏塞",
    "st_piston": "0t计划刻活塞",
    "st_sticky_piston": "0t计划刻黏塞",
    "gravity_piston": "重力活塞",
    "strong_piston_1": "强力活塞一档",
    "strong_piston_2": "强力活塞二档",
    "strong_piston_3": "强力活塞三档",
}

FACING_ROT = {
    "north": {}, "south": {"y": 180}, "west": {"y": 270}, "east": {"y": 90},
    "up": {"x": 270}, "down": {"x": 90},
}

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent="  ")
        f.write("\n")

def png_bytes(img):
    import io
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return buf.getvalue()

# ---------------------------------------------------------------- models

def base_model_retracted(vid, sticky):
    return {
        "parent": "minecraft:block/template_piston",
        "textures": {
            "bottom": f"{NS}:block/{vid}_bottom",
            "platform": f"{NS}:block/{vid}_top_sticky" if sticky else f"{NS}:block/{vid}_top",
            "side": f"{NS}:block/{vid}_side",
        },
    }

def base_model_extended(vid):
    return {
        "parent": "minecraft:block/piston_extended",
        "textures": {
            "bottom": f"{NS}:block/{vid}_bottom",
            "side": f"{NS}:block/{vid}_side",
            "inside": f"{NS}:block/{vid}_inner",
        },
    }

def base_model_inventory(vid, sticky):
    return {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "bottom": f"{NS}:block/{vid}_bottom",
            "side": f"{NS}:block/{vid}_side",
            "top": f"{NS}:block/{vid}_top_sticky" if sticky else f"{NS}:block/{vid}_top",
        },
    }

def head_model_plate(vid, sticky):
    return {
        "parent": "minecraft:block/template_piston_head",
        "textures": {
            "platform": f"{NS}:block/{vid}_top_sticky" if sticky else f"{NS}:block/{vid}_top",
            "side": f"{NS}:block/{vid}_side",
            "unsticky": f"{NS}:block/{vid}_top",
        },
    }

def head_model_plate_short(vid, sticky):
    return {
        "parent": "minecraft:block/template_piston_head_short",
        "textures": {
            "platform": f"{NS}:block/{vid}_top_sticky" if sticky else f"{NS}:block/{vid}_top",
            "side": f"{NS}:block/{vid}_side",
            "unsticky": f"{NS}:block/{vid}_top",
        },
    }

def end_rod_base_model(vid):
    rod = f"{NS}:block/{vid}_rod"
    return {
        "parent": "block/block",
        "textures": {
            "particle": f"{NS}:block/{vid}_side",
            "side": f"{NS}:block/{vid}_side",
            "bottom": f"{NS}:block/{vid}_bottom",
            "inner": f"{NS}:block/{vid}_inner",
            "rod": rod,
        },
        "elements": [
            {
                "from": [0, 0, 12], "to": [16, 16, 16],
                "faces": {
                    "down": {"uv": [0, 0, 16, 16], "texture": "#side", "rotation": 180},
                    "up": {"uv": [0, 0, 16, 16], "texture": "#side"},
                    "north": {"uv": [0, 0, 16, 16], "texture": "#inner"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
                    "west": {"uv": [0, 0, 16, 16], "texture": "#side", "rotation": 270},
                    "east": {"uv": [0, 0, 16, 16], "texture": "#side", "rotation": 90},
                },
            },
            {
                "from": [6, 6, 0], "to": [10, 10, 16],
                "faces": {
                    "down": {"uv": [5, 4, 11, 16], "texture": "#rod"},
                    "up": {"uv": [5, 4, 11, 16], "texture": "#rod"},
                    "north": {"uv": [5, 4, 11, 10], "texture": "#rod"},
                    "south": {"uv": [5, 4, 11, 16], "texture": "#rod"},
                    "west": {"uv": [4, 4, 16, 10], "texture": "#rod"},
                    "east": {"uv": [4, 4, 16, 10], "texture": "#rod"},
                },
            },
        ],
    }

def end_rod_head_model(vid, short):
    rod = f"{NS}:block/{vid}_rod"
    tip_end = 12 if short else 16
    # The rod texture carries the redstone flame in its top rows, so the collar element
    # automatically picks it up from the same texture.
    elements = [
        {
            "from": [6, 6, 3], "to": [10, 10, tip_end],
            "faces": {
                "down": {"uv": [5, 3, 11, tip_end], "texture": "#rod"},
                "up": {"uv": [5, 3, 11, tip_end], "texture": "#rod"},
                "north": {"uv": [5, 3, 11, 9], "texture": "#rod"},
                "south": {"uv": [5, 3, 11, tip_end], "texture": "#rod"},
                "west": {"uv": [3, 3, tip_end, 9], "texture": "#rod"},
                "east": {"uv": [3, 3, tip_end, 9], "texture": "#rod"},
            },
        },
        {
            "from": [5, 5, 0], "to": [11, 11, 3],
            "faces": {
                "down": {"uv": [5, 0, 11, 3], "texture": "#rod"},
                "up": {"uv": [5, 0, 11, 3], "texture": "#rod"},
                "north": {"uv": [5, 0, 11, 6], "texture": "#rod"},
                "south": {"uv": [5, 0, 11, 6], "texture": "#rod"},
                "west": {"uv": [0, 0, 3, 6], "texture": "#rod"},
                "east": {"uv": [0, 0, 3, 6], "texture": "#rod"},
            },
        },
    ]
    return {
        "textures": {"particle": rod, "rod": rod},
        "elements": elements,
    }

def weak_head_model(vid, short):
    arm_to = 16 if short else 20
    return {
        "parent": "block/block",
        "textures": {
            "particle": f"{NS}:block/{vid}_side",
            "platform": f"{NS}:block/{vid}_top",
            "side": f"{NS}:block/{vid}_side",
        },
        "elements": [
            {
                "from": [0, 0, 0], "to": [16, 16, 4],
                "faces": {
                    "down": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 180, "cullface": "down"},
                    "up": {"uv": [0, 0, 16, 4], "texture": "#side", "cullface": "up"},
                    "north": {"uv": [0, 0, 16, 16], "texture": "#platform", "cullface": "north"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#platform"},
                    "west": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 270, "cullface": "west"},
                    "east": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 90, "cullface": "east"},
                },
            },
            {
                "from": [7, 7, 4], "to": [9, 9, arm_to],
                "faces": {
                    "down": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 90},
                    "up": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 270},
                    "west": {"uv": [16, 4, 0, 0], "texture": "#side"},
                    "east": {"uv": [0, 0, 16, 4], "texture": "#side"},
                },
            },
        ],
    }

def recoil_head_model(vid, short):
    arm_to = 16 if short else 20
    return {
        "parent": "block/block",
        "textures": {
            "particle": f"{NS}:block/{vid}_side",
            "platform": f"{NS}:block/{vid}_top",
            "side": f"{NS}:block/{vid}_side",
        },
        "elements": [
            {
                "from": [0, 0, 0], "to": [16, 16, 8],
                "faces": {
                    "down": {"uv": [0, 0, 16, 8], "texture": "#side", "rotation": 180, "cullface": "down"},
                    "up": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "up"},
                    "north": {"uv": [0, 0, 16, 16], "texture": "#platform", "cullface": "north"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#platform"},
                    "west": {"uv": [0, 0, 16, 8], "texture": "#side", "rotation": 270, "cullface": "west"},
                    "east": {"uv": [0, 0, 16, 8], "texture": "#side", "rotation": 90, "cullface": "east"},
                },
            },
            {
                "from": [6, 6, 8], "to": [10, 10, arm_to],
                "faces": {
                    "down": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 90},
                    "up": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 270},
                    "west": {"uv": [16, 4, 0, 0], "texture": "#side"},
                    "east": {"uv": [0, 0, 16, 4], "texture": "#side"},
                },
            },
        ],
    }


# ---- v2 custom geometries -------------------------------------------------------


def recursive_base_model(vid, extended):
    """递推 base: thin plate at the back plus a bare rod running to the front face.

    At rest the plate still occupies the front 4px of this cell (the head is in the cell next
    door); extended it is a 4px plate + rod only.

    Cullfaces are only put on faces that actually border a neighbouring cell. The rod runs the
    full 16px depth, so its north face is interior and must NOT be culled — giving it a cullface
    made the rod's north face disappear whenever a block sat in front of the piston, which is
    exactly the "one face of the back half not showing" report.
    """
    elements = [
        {
            "from": [0, 0, 0], "to": [16, 16, 4],
            "faces": {
                "down": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 180, "cullface": "down"},
                "up": {"uv": [0, 0, 16, 4], "texture": "#side", "cullface": "up"},
                "north": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
                "west": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 270, "cullface": "west"},
                "east": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 90, "cullface": "east"},
            },
        },
        {
            "from": [6, 6, 0], "to": [10, 10, 12],
            "faces": {
                "down": {"uv": [5, 4, 11, 12], "texture": "#side", "rotation": 90},
                "up": {"uv": [5, 4, 11, 12], "texture": "#side", "rotation": 270},
                "north": {"uv": [5, 4, 11, 8], "texture": "#side"},
                "south": {"uv": [5, 4, 11, 12], "texture": "#side"},
                "west": {"uv": [12, 4, 4, 10], "texture": "#side"},
                "east": {"uv": [12, 4, 4, 10], "texture": "#side"},
            },
        },
    ]
    if not extended:
        elements.append({
            "from": [0, 0, 12], "to": [16, 16, 16],
            "faces": {
                "down": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 180},
                "up": {"uv": [0, 0, 16, 4], "texture": "#side"},
                # No cullface: this face sits at z=12, the interior boundary shared with the rod,
                # not the cell's outer edge. Culling it against the cell in front made the rear
                # half of the piston lose a face as soon as anything stood in front of it.
                "north": {"uv": [0, 0, 16, 16], "texture": "#platform"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#platform"},
                "west": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 270},
                "east": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 90},
            },
        })
    return {
        "parent": "block/block",
        "textures": {
            "particle": f"{NS}:block/{vid}_side",
            "platform": f"{NS}:block/{vid}_top",
            "side": f"{NS}:block/{vid}_side",
            "bottom": f"{NS}:block/{vid}_bottom",
        },
        "elements": elements,
    }


def wall_merge_retracted_model(vid, sticky):
    """墙并 retracted base: vanilla plate block + wall post poking 8px past the front face.

    The post is part of the base block (model + collision both live there); model elements may
    extend past the cell, vanilla renders them fine.
    """
    elements = [
        {
            "from": [0, 0, 0], "to": [16, 16, 16],
            "faces": {
                "down": {"uv": [0, 0, 16, 16], "texture": "#side", "rotation": 180, "cullface": "down"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "up"},
                "north": {"uv": [0, 0, 16, 16], "texture": "#platform", "cullface": "north"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "south"},
                "west": {"uv": [0, 0, 16, 16], "texture": "#side", "rotation": 270, "cullface": "west"},
                "east": {"uv": [0, 0, 16, 16], "texture": "#side", "rotation": 90, "cullface": "east"},
            },
        },
        {
            "from": [4, 4, -8], "to": [12, 12, 0],
            "faces": {
                "down": {"uv": [4, 8, 12, 16], "texture": "#side"},
                "up": {"uv": [4, 8, 12, 16], "texture": "#side"},
                "north": {"uv": [4, 4, 12, 12], "texture": "#side"},
                "south": {"uv": [4, 4, 12, 12], "texture": "#side"},
                "west": {"uv": [8, 4, 16, 12], "texture": "#side"},
                "east": {"uv": [8, 4, 16, 12], "texture": "#side"},
            },
        },
    ]
    return {
        "parent": "block/block",
        "textures": {
            "particle": f"{NS}:block/{vid}_side",
            "platform": f"{NS}:block/{vid}_top_sticky" if sticky else f"{NS}:block/{vid}_top",
            "side": f"{NS}:block/{vid}_side",
            "bottom": f"{NS}:block/{vid}_bottom",
        },
        "elements": elements,
    }


def bare_rod_head_model(vid, short, sticky=False):
    """递推 head: plate + rod, rod continues into the previous cell when not short."""
    return _plate_rod_head(vid, short, sticky, rod_width=4, rod_from=0)


def wall_rod_head_model(vid, short, sticky=False):
    """墙并 head: plate + 8px wall-centre-post rod."""
    return _plate_rod_head(vid, short, sticky, rod_width=8, rod_from=0)


def _plate_rod_head(vid, short, sticky, rod_width, rod_from):
    half = rod_width / 2
    arm_to = 12 if short else 20
    platform = f"{NS}:block/{vid}_top_sticky" if sticky else f"{NS}:block/{vid}_top"
    return {
        "parent": "block/block",
        "textures": {
            "particle": f"{NS}:block/{vid}_side",
            "platform": platform,
            "side": f"{NS}:block/{vid}_side",
        },
        "elements": [
            {
                "from": [0, 0, 0], "to": [16, 16, 4],
                "faces": {
                    "down": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 180, "cullface": "down"},
                    "up": {"uv": [0, 0, 16, 4], "texture": "#side", "cullface": "up"},
                    "north": {"uv": [0, 0, 16, 16], "texture": "#platform", "cullface": "north"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#platform"},
                    "west": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 270, "cullface": "west"},
                    "east": {"uv": [0, 0, 16, 4], "texture": "#side", "rotation": 90, "cullface": "east"},
                },
            },
            {
                "from": [8 - half, 8 - half, rod_from], "to": [8 + half, 8 + half, arm_to],
                "faces": {
                    "down": {"uv": [4, rod_from, 12, arm_to], "texture": "#side", "rotation": 90},
                    "up": {"uv": [4, rod_from, 12, arm_to], "texture": "#side", "rotation": 270},
                    "north": {"uv": [4, rod_from, 12, 8], "texture": "#side"},
                    "south": {"uv": [4, rod_from, 12, arm_to], "texture": "#side"},
                    "west": {"uv": [arm_to, 4, rod_from, 12], "texture": "#side"},
                    "east": {"uv": [arm_to, 4, rod_from, 12], "texture": "#side"},
                },
            },
        ],
    }


def bare_rod_model():
    """递推杆 block: a bare 4px rod spanning the cell."""
    return {
        "parent": "block/block",
        "textures": {"particle": f"{NS}:block/recursive_piston_side", "side": f"{NS}:block/recursive_piston_side"},
        "elements": [
            {
                "from": [6, 6, 0], "to": [10, 10, 16],
                "faces": {
                    "down": {"uv": [5, 0, 11, 16], "texture": "#side", "rotation": 90},
                    "up": {"uv": [5, 0, 11, 16], "texture": "#side", "rotation": 270},
                    "north": {"uv": [5, 0, 11, 6], "texture": "#side"},
                    "south": {"uv": [5, 0, 11, 16], "texture": "#side"},
                    "west": {"uv": [0, 5, 16, 11], "texture": "#side"},
                    "east": {"uv": [0, 5, 16, 11], "texture": "#side"},
                },
            },
        ],
    }


def bent_base_model(vid, bend, sticky, extended):
    """拐推 base: vanilla base geometry, plate texture from the bend state.

    The retracted plate carries the arrow pointing at the bend — it is the only place the bend
    direction is readable, so it must use the per-bend arrow texture. An extended base has no
    plate at all, so it keeps the plain top (the head beside it shows the arrow instead).
    """
    return {
        "parent": "minecraft:block/piston_extended" if extended else "minecraft:block/template_piston",
        "textures": {
            "bottom": f"{NS}:block/{vid}_bottom",
            "inside": f"{NS}:block/{vid}_inner",
            "platform": f"{NS}:block/{vid}_top",
            "side": f"{NS}:block/{vid}_side",
        },
    }


def bent_head_model(vid, plate_dir, base_sticky, short, sticky_plate=False):
    """拐推 head: the plate sits on the model-local bend face, the rod runs along FACING.

    plate_dir is one of the six model-local directions; only the ones reachable for a given
    (facing, bend) pair are referenced by the blockstate.
    """
    side = f"{NS}:block/{vid}_side"

    def slab(lo, hi, faces, tex="#side"):
        return {"from": lo, "to": hi, "faces": faces}

    plate = {
        "north": slab([0, 0, 0], [16, 16, 4], {
            "down": {"uv": [0, 12, 16, 16], "texture": side, "rotation": 180},
            "up": {"uv": [0, 12, 16, 16], "texture": side},
            "north": {"uv": [0, 0, 16, 16], "texture": "#platform", "cullface": "north"},
            "south": {"uv": [0, 0, 16, 16], "texture": side},
            "west": {"uv": [0, 12, 16, 16], "texture": side, "rotation": 270},
            "east": {"uv": [0, 12, 16, 16], "texture": side, "rotation": 90},
        }),
        "south": slab([0, 0, 12], [16, 16, 16], {
            "down": {"uv": [0, 0, 16, 4], "texture": side, "rotation": 180},
            "up": {"uv": [0, 0, 16, 4], "texture": side},
            "north": {"uv": [0, 0, 16, 16], "texture": side},
            "south": {"uv": [0, 0, 16, 16], "texture": "#platform"},
            "west": {"uv": [0, 0, 16, 4], "texture": side, "rotation": 270},
            "east": {"uv": [0, 0, 16, 4], "texture": side, "rotation": 90},
        }),
        "east": slab([12, 0, 0], [16, 16, 16], {
            "down": {"uv": [0, 0, 16, 4], "texture": side, "rotation": 180},
            "up": {"uv": [0, 0, 16, 4], "texture": side},
            "north": {"uv": [0, 0, 4, 16], "texture": side},
            "south": {"uv": [12, 0, 16, 16], "texture": side},
            "west": {"uv": [0, 0, 16, 16], "texture": side},
            "east": {"uv": [0, 0, 16, 16], "texture": "#platform", "cullface": "east"},
        }),
        "west": slab([0, 0, 0], [4, 16, 16], {
            "down": {"uv": [0, 0, 16, 4], "texture": side, "rotation": 180},
            "up": {"uv": [0, 0, 16, 4], "texture": side},
            "north": {"uv": [12, 0, 16, 16], "texture": side},
            "south": {"uv": [0, 0, 4, 16], "texture": side},
            "west": {"uv": [0, 0, 16, 16], "texture": "#platform", "cullface": "west"},
            "east": {"uv": [0, 0, 16, 16], "texture": side},
        }),
        "down": slab([0, 0, 0], [16, 4, 16], {
            "down": {"uv": [0, 0, 16, 16], "texture": "#platform", "cullface": "down"},
            "up": {"uv": [0, 0, 16, 16], "texture": side},
            "north": {"uv": [0, 12, 16, 16], "texture": side},
            "south": {"uv": [0, 12, 16, 16], "texture": side},
            "west": {"uv": [0, 12, 16, 16], "texture": side, "rotation": 270},
            "east": {"uv": [0, 12, 16, 16], "texture": side, "rotation": 90},
        }),
        "up": slab([0, 12, 0], [16, 16, 16], {
            "down": {"uv": [0, 0, 16, 16], "texture": side},
            "up": {"uv": [0, 0, 16, 16], "texture": "#platform", "cullface": "up"},
            "north": {"uv": [0, 0, 16, 4], "texture": side},
            "south": {"uv": [0, 0, 16, 4], "texture": side},
            "west": {"uv": [0, 0, 16, 4], "texture": side, "rotation": 270},
            "east": {"uv": [0, 0, 16, 4], "texture": side, "rotation": 90},
        }),
    }[plate_dir]

    # the rod runs along the push axis (local z) and pokes 4px into the base cell (z 16..20);
    # for the north plate it starts behind the plate, otherwise it hides behind/next to it
    rod_from = 12 if (short or plate_dir in ("south", "down", "up")) else 4
    rod = {
        "from": [6, 6, rod_from], "to": [10, 10, 20],
        "faces": {
            "down": {"uv": [5, 4, 11, 20 - rod_from], "texture": side, "rotation": 90},
            "up": {"uv": [5, 4, 11, 20 - rod_from], "texture": side, "rotation": 270},
            "north": {"uv": [5, 4, 11, 8], "texture": side},
            "south": {"uv": [5, 4, 11, 20 - rod_from], "texture": side},
            "west": {"uv": [rod_from, 4, 20, 12], "texture": side},
            "east": {"uv": [rod_from, 4, 20, 12], "texture": side},
        },
    }
    # side plates leave the rod centred; east/west plates pull it against the plate so it connects
    if plate_dir == "east":
        rod["from"][0], rod["to"][0] = 8, 12
    elif plate_dir == "west":
        rod["from"][0], rod["to"][0] = 4, 8

    return {
        "parent": "block/block",
        "textures": {
            "particle": side,
            # Arrow plate, so the bend stays readable on the extended arm. Only the sticky variant
            # ships a `_top_sticky_<lb>` set, so the middle segment is the discriminator.
            "platform": (f"{NS}:block/{vid}_top_sticky_{plate_dir}" if sticky_plate
                         else f"{NS}:block/{vid}_top_{plate_dir}"),
            "side": side,
        },
        "elements": [plate, rod],
    }


# ---------------------------------------------------------------- generation


# world bend -> model-local direction for a facing (blockstate rotations applied by the game)
_LOCAL_BEND = {
    "north": {"north": "north", "south": "south", "east": "east", "west": "west",
              "up": "up", "down": "down"},
    "south": {"north": "south", "south": "north", "east": "west", "west": "east",
              "up": "up", "down": "down"},
    "east": {"north": "west", "south": "east", "east": "north", "west": "south",
             "up": "up", "down": "down"},
    "west": {"north": "east", "south": "west", "east": "south", "west": "north",
             "up": "up", "down": "down"},
    "up": {"north": "down", "south": "up", "east": "east", "west": "west",
           "up": "north", "down": "south"},
    "down": {"north": "up", "south": "down", "east": "east", "west": "west",
             "up": "south", "down": "north"},
}


def local_bend(facing, bend):
    return _LOCAL_BEND[facing][bend]


def gen_shared_assets(textures):
    assets = os.path.join(MOD, "src", "main", "resources", "assets", NS)

    # textures
    tex_dir = os.path.join(assets, "textures", "block")
    os.makedirs(tex_dir, exist_ok=True)
    for name, img in textures.items():
        img.save(os.path.join(tex_dir, name + ".png"))

    # icon: honey plate upscaled
    icon = textures["honey_piston_top_sticky"].resize((128, 128), Image.NEAREST)
    icon.save(os.path.join(assets, "icon.png"))

    # lang
    write_json(os.path.join(assets, "lang", "en_us.json"),
               {**{f"block.{NS}.{k}": v for k, v in LANG_EN.items()}})
    write_json(os.path.join(assets, "lang", "zh_cn.json"),
               {**{f"block.{NS}.{k}": v for k, v in LANG_ZH.items()}})

    # blockstates + models
    bs_dir = os.path.join(assets, "blockstates")
    model_dir = os.path.join(assets, "models", "block")
    item_def_dir = os.path.join(assets, "items")
    item_model_dir = os.path.join(assets, "models", "item")
    os.makedirs(bs_dir, exist_ok=True)
    os.makedirs(model_dir, exist_ok=True)
    os.makedirs(item_def_dir, exist_ok=True)
    os.makedirs(item_model_dir, exist_ok=True)

    for vid, sticky, custom, head_kind in VARIANTS:
        # --- base blockstate
        variants = {}
        if head_kind == "bent":
            # (extended, facing, bend) — the retracted model carries the arrow plate pointing
            # at the model-local bend direction
            for extended in (False, True):
                for facing, rot in FACING_ROT.items():
                    for bend in ("north", "south", "west", "east"):
                        key = f"extended={str(extended).lower()},facing={facing},bend={bend}"
                        lb = local_bend(facing, bend)
                        # both states need the per-bend model: the arrow is the only bend cue
                        variants[key] = {"model": f"{NS}:block/{vid}{'_extended' if extended else ''}_{lb}", **rot}
        elif head_kind == "pickaxe":
            # (extended, facing, tool) in one file — every tool value needs its own variant
            for extended in (False, True):
                for facing, rot in FACING_ROT.items():
                    for tool in PICKAXE_TINTS:
                        key = f"extended={str(extended).lower()},facing={facing},tool={tool}"
                        if extended:
                            variants[key] = {"model": f"{NS}:block/{vid}_extended", **rot}
                        else:
                            variants[key] = {"model": f"{NS}:block/{vid}_{tool}", **rot}
        else:
            for extended in (False, True):
                for facing, rot in FACING_ROT.items():
                    key = f"extended={str(extended).lower()},facing={facing}"
                    if custom and head_kind == "end_rod":
                        # Rotation must not be dropped here, or the rod model faces one way only.
                        variants[key] = {"model": f"{NS}:block/{vid}_base", **rot}
                    elif extended:
                        variants[key] = {"model": f"{NS}:block/{vid}_extended", **rot}
                    else:
                        variants[key] = {"model": f"{NS}:block/{vid}", **rot}
        write_json(os.path.join(bs_dir, vid + ".json"), {"variants": variants})

        # --- base models
        if custom and head_kind == "end_rod":
            model = end_rod_base_model(vid)
            write_json(os.path.join(model_dir, vid + "_base.json"), model)
        elif head_kind == "bare_rod":
            # 递推: thin plate + rod running to the front face (retracted: full-height rod + plate)
            write_json(os.path.join(model_dir, vid + "_base.json"), recursive_base_model(vid, True))
            write_json(os.path.join(model_dir, vid + ".json"), recursive_base_model(vid, False))
            write_json(os.path.join(model_dir, vid + "_extended.json"), recursive_base_model(vid, True))
        elif head_kind == "wall_rod":
            # 墙并: retracted base with the wall post poking 8px past the front face
            write_json(os.path.join(model_dir, vid + ".json"), wall_merge_retracted_model(vid, sticky))
            write_json(os.path.join(model_dir, vid + "_extended.json"), base_model_extended(vid))
        elif head_kind == "bent":
            # 拐推: retracted base per local bend (arrow plate). An extended base has no plate,
            # so its arrow is drawn on the inner (top) face instead — the bend stays readable
            # while the arm is out, which is when it is hardest to tell.
            for lb in ("north", "south", "west", "east", "up", "down"):
                model = bent_base_model(vid, lb, sticky, False)
                # the arrow pointing at the bend is what makes the direction readable at all
                model["textures"]["platform"] = (f"{NS}:block/{vid}_top_sticky_{lb}" if sticky
                                                 else f"{NS}:block/{vid}_top_{lb}")
                write_json(os.path.join(model_dir, f"{vid}_{lb}.json"), model)
            for lb in ("north", "south", "west", "east", "up", "down"):
                model = bent_base_model(vid, lb, sticky, True)
                model["textures"]["inside"] = (f"{NS}:block/{vid}_top_sticky_{lb}" if sticky
                                               else f"{NS}:block/{vid}_top_{lb}")
                write_json(os.path.join(model_dir, f"{vid}_extended_{lb}.json"), model)
        elif head_kind == "pickaxe":
            # retracted base per tool (platform texture); extended has no plate
            for tool in PICKAXE_TINTS:
                model = base_model_retracted(vid, sticky)
                model["textures"]["platform"] = f"{NS}:block/{vid}_top_{tool}"
                write_json(os.path.join(model_dir, f"{vid}_{tool}.json"), model)
            write_json(os.path.join(model_dir, vid + "_extended.json"), base_model_extended(vid))
        elif head_kind == "none":
            pass  # 强力: the shared head block gets its assets below; base models fall through
        else:
            write_json(os.path.join(model_dir, vid + ".json"), base_model_retracted(vid, sticky))
            write_json(os.path.join(model_dir, vid + "_extended.json"), base_model_extended(vid))
        if head_kind != "none":
            write_json(os.path.join(model_dir, vid + "_inventory.json"), base_model_inventory(vid, sticky))

        # --- item models (both formats; unused format is ignored by the other versions)
        write_json(os.path.join(item_def_dir, vid + ".json"),
                   {"model": {"type": "minecraft:model", "model": f"{NS}:block/{vid}_inventory"}})
        write_json(os.path.join(item_model_dir, vid + ".json"),
                   {"parent": f"{NS}:block/{vid}_inventory"})

        # --- head blockstate + models
        if head_kind == "none":
            continue  # 强力 piston: base assets only (the shared head is emitted after the loop)
        hid = vid + "_head"
        head_variants = {}
        head_models = {}
        if head_kind == "plate":
            head_models[("normal", False, False, None)] = head_model_plate(vid, False)
            head_models[("normal", True, False, None)] = head_model_plate_short(vid, False)
            if sticky:
                head_models[("sticky", False, False, None)] = head_model_plate(vid, True)
                head_models[("sticky", True, False, None)] = head_model_plate_short(vid, True)
            else:
                # Non-sticky variants have no _top_sticky texture; the sticky model would be
                # a missing texture, so only the normal pair are generated.
                pass
        elif head_kind == "pickaxe":
            for tool in PICKAXE_TINTS:
                textures = {**head_model_plate(vid, False)["textures"],
                            "platform": f"{NS}:block/{vid}_top_{tool}",
                            "unsticky": f"{NS}:block/{vid}_top_{tool}"}
                head_models[("normal", False, False, tool)] = {"parent": "minecraft:block/template_piston_head", "textures": textures}
                short_textures = {**head_model_plate_short(vid, False)["textures"],
                                  "platform": f"{NS}:block/{vid}_top_{tool}",
                                  "unsticky": f"{NS}:block/{vid}_top_{tool}"}
                head_models[("normal", True, False, tool)] = {"parent": "minecraft:block/template_piston_head_short", "textures": short_textures}
        elif head_kind == "bent":
            for lb in ("north", "south", "west", "east", "up", "down"):
                head_models[("normal", False, False, lb)] = bent_head_model(vid, lb, sticky, False)
                head_models[("normal", True, False, lb)] = bent_head_model(vid, lb, sticky, True)
                if sticky:
                    head_models[("sticky", False, False, lb)] = bent_head_model(vid, lb, sticky, False, sticky_plate=True)
                    head_models[("sticky", True, False, lb)] = bent_head_model(vid, lb, sticky, True, sticky_plate=True)
        elif head_kind == "skull":
            head_models[("normal", False, False, None)] = head_model_plate(vid, False)
            head_models[("normal", True, False, None)] = head_model_plate_short(vid, False)
            head_models[("normal", False, True, None)] = {**head_model_plate(vid, False),
                                                    "textures": {**head_model_plate(vid, False)["textures"],
                                                                 "platform": f"{NS}:block/{vid}_top_powered"}}
            head_models[("normal", True, True, None)] = {**head_model_plate_short(vid, False),
                                                   "textures": {**head_model_plate_short(vid, False)["textures"],
                                                                "platform": f"{NS}:block/{vid}_top_powered"}}
        elif head_kind == "end_rod":
            head_models[("normal", False, False, None)] = end_rod_head_model(vid, False)
            head_models[("normal", True, False, None)] = end_rod_head_model(vid, True)
        elif head_kind == "weak":
            head_models[("normal", False, False, None)] = weak_head_model(vid, False)
            head_models[("normal", True, False, None)] = weak_head_model(vid, True)
        elif head_kind == "recoil":
            head_models[("normal", False, False, None)] = recoil_head_model(vid, False)
            head_models[("normal", True, False, None)] = recoil_head_model(vid, True)
        elif head_kind == "bare_rod":
            head_models[("normal", False, False, None)] = bare_rod_head_model(vid, False)
            head_models[("normal", True, False, None)] = bare_rod_head_model(vid, True)
            if sticky:
                head_models[("sticky", False, False, None)] = bare_rod_head_model(vid, False, sticky=True)
                head_models[("sticky", True, False, None)] = bare_rod_head_model(vid, True, sticky=True)
        elif head_kind == "wall_rod":
            head_models[("normal", False, False, None)] = wall_rod_head_model(vid, False)
            head_models[("normal", True, False, None)] = wall_rod_head_model(vid, True)
            if sticky:
                head_models[("sticky", False, False, None)] = wall_rod_head_model(vid, False, sticky=True)
                head_models[("sticky", True, False, None)] = wall_rod_head_model(vid, True, sticky=True)
        elif head_kind == "gravity":
            # the gravity head falls as a vanilla moving block, so it keeps the vanilla plate
            head_models[("normal", False, False, None)] = head_model_plate(vid, False)
            head_models[("normal", True, False, None)] = head_model_plate_short(vid, False)

        for (ptype, short, powered, extra), model in head_models.items():
            suffix = "_powered" if powered else ""
            # Normal and sticky models used to land in the same file (sticky overwrote normal),
            # leaving non-sticky variants' extended heads on the sticky texture. Differentiate.
            type_part = "_sticky" if ptype == "sticky" else ""
            extra_part = f"_{extra}" if extra else ""
            fname = hid + type_part + suffix + extra_part + ("_short" if short else "")
            write_json(os.path.join(model_dir, fname + ".json"), model)
            if head_kind == "skull":
                key = f"facing={{facing}},powered={str(powered).lower()},short={str(short).lower()},type={ptype}"
            elif extra and head_kind == "bent":
                key = f"bend={extra},facing={{facing}},short={str(short).lower()},type={ptype}"  # extra resolved per facing below
            elif extra and head_kind == "pickaxe":
                key = f"facing={{facing}},short={str(short).lower()},tool={extra},type={ptype}"
            else:
                key = f"facing={{facing}},short={str(short).lower()},type={ptype}"
            for facing, rot in FACING_ROT.items():
                fname_here = fname
                if head_kind == "bent" and extra:
                    fname_here = fname.replace("_" + extra, "_" + local_bend(facing, extra))
                head_variants[key.format(facing=facing)] = {
                    "model": f"{NS}:block/{fname_here}", **rot
                }

        # The TYPE property still has a sticky value for every head block; alias it to the
        # normal models so no reachable state is left without a model.
        if not sticky:
            for short in (False, True):
                for powered in ((False, True) if head_kind == "skull" else (False,)):
                    for facing in FACING_ROT:
                        for extra in (["north", "south", "west", "east"] if head_kind == "bent" else
                                      list(PICKAXE_TINTS) if head_kind == "pickaxe" else [None]):
                            powered_part = f"powered={str(powered).lower()}," if head_kind == "skull" else ""
                            extra_part = f"{extra}," if extra else ""
                            normal_key = f"{extra_part}facing={facing},{powered_part}short={str(short).lower()},type=normal"
                            sticky_key = f"{extra_part}facing={facing},{powered_part}short={str(short).lower()},type=sticky"
                            if normal_key in head_variants:
                                head_variants[sticky_key] = dict(head_variants[normal_key])
        write_json(os.path.join(bs_dir, hid + ".json"), {"variants": head_variants})

    # --- 强力 piston: one shared, vanilla-textured head for all three tiers -------------
    strong_head_variants = {}
    for (ptype, short) in (("normal", False), ("normal", True)):
        fname = f"strong_piston_head{'_short' if short else ''}"
        model = head_model_plate("strong_piston", False) if not short else head_model_plate_short("strong_piston", False)
        # the shared head uses vanilla piston plate textures
        model = {
            "parent": "minecraft:block/template_piston_head" if not short else "minecraft:block/template_piston_head_short",
            "textures": {
                "platform": "minecraft:block/piston_top",
                "side": "minecraft:block/piston_side",
                "unsticky": "minecraft:block/piston_top",
            },
        }
        write_json(os.path.join(model_dir, fname + ".json"), model)
        key = f"facing={{facing}},short={str(short).lower()},type={ptype}"
        for facing, rot in FACING_ROT.items():
            strong_head_variants[key.format(facing=facing)] = {"model": f"{NS}:block/{fname}", **rot}
    # alias type=sticky to the normal models (the head has no sticky texture of its own)
    for short in (False, True):
        for facing in FACING_ROT:
            normal_key = f"facing={facing},short={str(short).lower()},type=normal"
            sticky_key = f"facing={facing},short={str(short).lower()},type=sticky"
            if normal_key in strong_head_variants:
                strong_head_variants[sticky_key] = dict(strong_head_variants[normal_key])
    write_json(os.path.join(bs_dir, "strong_piston_head.json"), {"variants": strong_head_variants})

    # --- structural parts (递推杆) ---------------------------------------------------
    # Only the recursive rod is a real block; the wall-merge rod lives inside the base/head
    # models, so there is no wall_merge_rod block to emit assets for.
    for part, rod_model in (("recursive_piston_rod", bare_rod_model),):
        part_variants = {}
        for facing, rot in FACING_ROT.items():
            model = f"{NS}:block/{part}" if rod_model is bare_rod_model else f"{NS}:block/{part}"
            part_variants[f"facing={facing}"] = {"model": model, **rot}
        write_json(os.path.join(bs_dir, part + ".json"), {"variants": part_variants})
        write_json(os.path.join(model_dir, part + ".json"), rod_model())

    return assets


# ---------------------------------------------------------------- per-version data

# Wall variants split so each version only lists the walls it actually has.
WALL_INGREDIENTS = [
    # 1.19-era walls
    ["minecraft:cobblestone_wall", "minecraft:mossy_cobblestone_wall",
     "minecraft:stone_brick_wall", "minecraft:mossy_stone_brick_wall",
     "minecraft:brick_wall", "minecraft:nether_brick_wall",
     "minecraft:sandstone_wall"],
    # walls added in 1.20+
    ["minecraft:red_nether_brick_wall", "minecraft:blackstone_wall",
     "minecraft:polished_blackstone_wall", "minecraft:polished_blackstone_brick_wall",
     "minecraft:cobbled_deepslate_wall", "minecraft:deepslate_brick_wall",
     "minecraft:deepslate_tile_wall", "minecraft:tuff_wall", "minecraft:tuff_brick_wall"],
]

# Ingredients are item ids, or "#ns:tag" for a tag (the 1.19 emitter turns the prefix into
# {"tag": ...}, the modern one keeps the "#..." string the codec reads as a tag).
RECIPES = [
    ("honey_piston", "shapeless", ["minecraft:piston", "minecraft:honey_bottle"], None),
    ("projectile_piston", "shapeless", ["minecraft:piston", "minecraft:bow"], None),
    ("wind_charge_piston", "shapeless", ["minecraft:piston", "minecraft:wind_charge"], "wind_charge"),
    ("silent_piston", "shapeless", ["minecraft:piston", "#minecraft:wool"], None),
    ("skull_piston", "shapeless", ["minecraft:piston",
                                   ["minecraft:skeleton_skull", "minecraft:wither_skeleton_skull",
                                    "minecraft:zombie_head", "minecraft:player_head",
                                    "minecraft:creeper_head", "minecraft:dragon_head"]], None),
    ("recoil_piston", "shaped", None, (["TTT", "PXP", "#R#"],
                                       {"T": "#minecraft:planks", "P": "#minecraft:planks",
                                        "#": "minecraft:cobblestone", "X": "minecraft:iron_ingot",
                                        "R": "minecraft:redstone"})),
    ("end_rod_piston", "shapeless", ["minecraft:piston", "minecraft:end_rod"], None),
    ("redstone_end_rod_piston", "shapeless", ["minecraft:piston", "minecraft:end_rod", "minecraft:redstone"], None),
    ("long_push_piston", "shapeless", ["minecraft:piston", "minecraft:redstone_block"], None),
    ("qc_piston", "shaped", None, (["TRT", "#X#", "#P#"],
                                   {"T": "#minecraft:planks", "R": "minecraft:redstone",
                                    "#": "minecraft:cobblestone", "X": "minecraft:iron_ingot",
                                    "P": "#minecraft:planks"})),
    ("qc_sticky_piston", "shapeless", ["piston_diversified:qc_piston", "minecraft:slime_ball"], None),
    ("observer_piston", "shapeless", ["minecraft:piston", "minecraft:observer"], None),
    ("observer_sticky_piston", "shapeless", ["piston_diversified:observer_piston", "minecraft:slime_ball"], None),
    ("weak_piston", "shaped", None, (["TTT", "#N#", "#R#"],
                                     {"T": "#minecraft:planks", "#": "minecraft:cobblestone",
                                      "N": "minecraft:iron_nugget", "R": "minecraft:redstone"})),
    ("fast_piston", "shaped", None, (["TTT", "#I#", "#R#"],
                                     {"T": "#minecraft:planks", "#": "minecraft:cobblestone",
                                      "I": "minecraft:ice", "R": "minecraft:redstone"})),
    ("fast_sticky_piston", "shapeless", ["piston_diversified:fast_piston", "minecraft:slime_ball"], None),

    # ---- v2 recipes ----
    ("potato_piston", "shapeless",
     ["minecraft:piston", ["minecraft:potato", "minecraft:poisonous_potato"]], None),
    # 镐活塞: piston + any pickaxe, keeping the tool (custom recipe — emitted separately)
    ("recursive_piston", "shapeless", ["minecraft:piston", "minecraft:piston"], None),
    ("recursive_sticky_piston", "shaped", None, (["SS", "SS"],
                                                 {"S": "minecraft:sticky_piston"})),
    ("turn_push_piston", "shaped", None, (["#TT", "#X#", "#R#"],
                                           {"T": "#minecraft:planks", "#": "minecraft:cobblestone",
                                            "X": "minecraft:iron_ingot", "R": "minecraft:redstone"})),
    ("turn_push_piston_west", "shaped", None, (["TT#", "X##", "R#R"],
                                               {"T": "#minecraft:planks", "#": "minecraft:cobblestone",
                                                "X": "minecraft:iron_ingot", "R": "minecraft:redstone"})),
    ("turn_push_sticky_piston", "shapeless",
     ["piston_diversified:turn_push_piston", "minecraft:slime_ball"], None),
    # the wall list is version-gated below: 1.19.4 has no deepslate/tuff walls
    ("wall_merge_piston", "shapeless",
     ["minecraft:piston", WALL_INGREDIENTS[0]], None),
    ("wall_merge_piston_deep", "shapeless",
     ["minecraft:piston", WALL_INGREDIENTS[1]], None),
    ("wall_merge_sticky_piston", "shapeless",
     ["piston_diversified:wall_merge_piston", "minecraft:slime_ball"], None),
    ("st_piston", "shapeless", ["minecraft:piston", "minecraft:string"], None),
    ("st_sticky_piston", "shapeless",
     ["piston_diversified:st_piston", "minecraft:slime_ball"], None),
    ("gravity_piston", "shaped", None, (["TTT", "#G#", "#R#"],
                                         {"T": "#minecraft:planks", "#": "minecraft:cobblestone",
                                          "G": "minecraft:gravel", "R": "minecraft:redstone"})),
    # 强力活塞: 1/2/3 creaking hearts replacing any of the three planks (7 recipes)
    ("strong_piston_1", "shaped", None, (["HTT", "#X#", "#R#"],
                                          {"T": "#minecraft:planks", "H": "minecraft:creaking_heart",
                                           "#": "minecraft:cobblestone", "X": "minecraft:iron_ingot",
                                           "R": "minecraft:redstone"})),
    ("strong_piston_1_b", "shaped", None, (["THT", "#X#", "#R#"],
                                            {"T": "#minecraft:planks", "H": "minecraft:creaking_heart",
                                             "#": "minecraft:cobblestone", "X": "minecraft:iron_ingot",
                                             "R": "minecraft:redstone"})),
    ("strong_piston_1_c", "shaped", None, (["TTH", "#X#", "#R#"],
                                            {"T": "#minecraft:planks", "H": "minecraft:creaking_heart",
                                             "#": "minecraft:cobblestone", "X": "minecraft:iron_ingot",
                                             "R": "minecraft:redstone"})),
    ("strong_piston_2", "shaped", None, (["HHT", "#X#", "#R#"],
                                          {"T": "#minecraft:planks", "H": "minecraft:creaking_heart",
                                           "#": "minecraft:cobblestone", "X": "minecraft:iron_ingot",
                                           "R": "minecraft:redstone"})),
    ("strong_piston_2_b", "shaped", None, (["HTH", "#X#", "#R#"],
                                            {"T": "#minecraft:planks", "H": "minecraft:creaking_heart",
                                             "#": "minecraft:cobblestone", "X": "minecraft:iron_ingot",
                                             "R": "minecraft:redstone"})),
    ("strong_piston_2_c", "shaped", None, (["THH", "#X#", "#R#"],
                                            {"T": "#minecraft:planks", "H": "minecraft:creaking_heart",
                                             "#": "minecraft:cobblestone", "X": "minecraft:iron_ingot",
                                             "R": "minecraft:redstone"})),
    ("strong_piston_3", "shaped", None, (["HHH", "#X#", "#R#"],
                                          {"#": "minecraft:cobblestone", "H": "minecraft:creaking_heart",
                                           "X": "minecraft:iron_ingot", "R": "minecraft:redstone"})),
]

# Recipes whose result differs from the file name (one block, several recipe files).
RECIPE_RESULT_OVERRIDE = {
    "wall_merge_piston_deep": "wall_merge_piston",
    "turn_push_piston_west": "turn_push_piston",
    "strong_piston_1_b": "strong_piston_1",
    "strong_piston_1_c": "strong_piston_1",
    "strong_piston_2_b": "strong_piston_2",
    "strong_piston_2_c": "strong_piston_2",
}

# Recipes that exist only on some versions (no ingredient item there).
RECIPE_VERSION_GATES = {
    "wind_charge_piston": {"min": "1.20"},
    "wall_merge_piston_deep": {"min": "1.20"},
    "strong_piston_1": {"min": "1.21.4"}, "strong_piston_1_b": {"min": "1.21.4"},
    "strong_piston_1_c": {"min": "1.21.4"}, "strong_piston_2": {"min": "1.21.4"},
    "strong_piston_2_b": {"min": "1.21.4"}, "strong_piston_2_c": {"min": "1.21.4"},
    "strong_piston_3": {"min": "1.21.4"},
}

def recipe_json_modern(name, kind, ingredients, shaped):
    if kind == "shapeless":
        return {
            "type": "minecraft:crafting_shapeless",
            "category": "redstone",
            "ingredients": [ing if isinstance(ing, str) else list(ing) for ing in ingredients],
            "result": {"count": 1, "id": f"{NS}:{name}"},
        }
    pattern, key = shaped
    return {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "key": {k: v for k, v in key.items()},
        "pattern": pattern,
        "result": {"count": 1, "id": f"{NS}:{name}"},
    }

def recipe_json_119(name, kind, ingredients, shaped):
    def ing(i):
        if isinstance(i, list):
            return [{"item": x} for x in i]
        if i.startswith("#"):
            return {"tag": i[1:]}
        return {"item": i}

    if kind == "shapeless":
        flat = []
        for i in ingredients:
            if isinstance(i, list):
                flat.extend([{"item": x} for x in i])
            else:
                flat.append(ing(i))
        return {
            "type": "minecraft:crafting_shapeless",
            "category": "redstone",
            "ingredients": flat,
            "result": {"item": f"{NS}:{name}", "count": 1},
        }
    pattern, key = shaped
    return {
        "type": "minecraft:crafting_shaped",
        "category": "redstone",
        "key": {k: ing(v) for k, v in key.items()},
        "pattern": pattern,
        "result": {"item": f"{NS}:{name}", "count": 1},
    }

def loot_json(random_sequence):
    table = {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": "..."}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }
    return table


def version_supports(ver, min_version):
    """Whether a stonecutter node is at least the given Minecraft version."""
    node = {"1.19.4": (1, 19), "1.21.10": (1, 21, 10), "1.21.11": (1, 21, 11), "26.2.x": (26, 2)}.get(ver, (0,))
    want = tuple(int(part) for part in min_version.split("."))
    return node >= want

def gen_version_trees():
    versions = ["1.19.4", "1.21.10", "1.21.11", "26.2.x"]
    compat = {"1.19.4": "1.19.4", "1.21.10": "1.21.10", "1.21.11": "1.21.11", "26.2.x": "~26.2"}
    for ver in versions:
        res = os.path.join(MOD, "versions", ver, "src", "main", "resources")
        old_data = ver == "1.19.4"
        recipe_dirname = "recipes" if old_data else "recipe"
        loot_dirname = "loot_tables" if old_data else "loot_table"

        fabric_mod = {
            "schemaVersion": 1,
            "id": "${id}",
            "version": "${version}",
            "name": "${name}",
            "description": "Adds 33 piston variants: v1 batch (honey, projectile, chain, loop, wind charge, silent, recoil, end rod, skull, directional QC, observer, redstone end rod, long push, weak, fast) and v2 batch (potato, pickaxe, recursive, turn push, wall merge, 0-tick, gravity, strong I-III).",
            "authors": ["ZCode"],
            "license": "WTFPL",
            "icon": f"assets/{NS}/icon.png",
            "environment": "*",
            "entrypoints": {
                "main": ["dev.zcode.piston_diversified.PistonDiversified"],
                "client": ["dev.zcode.piston_diversified.PistonDiversifiedClient"],
            },
            "mixins": [f"{NS}.mixins.json"],
            "depends": {
                "fabricloader": ">=0.16.0",
                "fabric-api": "*",
                "minecraft": "${minecraft}",
            },
        }
        write_json(os.path.join(res, "fabric.mod.json"), fabric_mod)

        write_json(os.path.join(res, "piston_diversified.mixins.json"), {
            "required": True,
            "minVersion": "0.8",
            "package": "dev.zcode.piston_diversified.mixin",
            "compatibilityLevel": "JAVA_17",
            "mixins": [
                "BootstrapMixin",
                "FallingBlockEntityAccessor",
                "PistonMovingBlockEntityAccessor",
                "PistonMovingBlockEntityMixin",
            ],
            "client": [],
            "injectors": {"defaultRequire": 1},
        })

        # the pickaxe piston keeps the crafted pickaxe — its recipe is a custom one (registered
        # in ModRecipes), so only the recipe *declaration* is data-driven
        write_json(os.path.join(res, "data", NS, recipe_dirname, "pickaxe_piston.json"), {
            "type": f"{NS}:pickaxe_piston",
            "category": "redstone",
        })

        for name, kind, ingredients, shaped in RECIPES:
            gate = RECIPE_VERSION_GATES.get(name)
            if gate and not version_supports(ver, gate["min"]):
                continue  # ingredient item does not exist on this version (规划: 不可合成)
            data = recipe_json_119(name, kind, ingredients, shaped) if old_data else recipe_json_modern(name, kind, ingredients, shaped)
            result_block = RECIPE_RESULT_OVERRIDE.get(name)
            if result_block:
                # a recipe file's name is only a file name — variants of one block (the mirrored
                # turn-push layout, the 1/2-heart strong tiers) must declare their result in the body
                data["result"]["id" if not old_data else "item"] = f"{NS}:{result_block}"
            write_json(os.path.join(res, "data", NS, recipe_dirname, name + ".json"), data)

        for vid, *_ in VARIANTS:
            table = loot_json(True)
            table["pools"][0]["entries"][0]["name"] = f"{NS}:{vid}"
            if not old_data:
                table["random_sequence"] = f"{NS}:blocks/{vid}"
            write_json(os.path.join(res, "data", NS, loot_dirname, "blocks", vid + ".json"), table)

def verify_texture_refs(assets):
    """Every `#ns:block/...` reference must resolve to a generated texture.

    A typo in a texture name is invisible at generation time — the model JSON is written happily
    and the block renders with the purple-black missing texture in game. That is exactly how the
    turn-push sticky plate (`top_{lb}` emitted vs `top_sticky_{lb}` referenced) went unnoticed, so
    the check is part of the generator rather than something to remember.
    """
    import glob

    # references are `NS:block/<name>`, so strip both segments and look under textures/
    tex_dir = os.path.join(assets, "textures")
    missing = set()
    for path in glob.glob(os.path.join(assets, "models", "block", "*.json")):
        with open(path, encoding="utf-8") as handle:
            model = json.load(handle)
        for value in (model.get("textures") or {}).values():
            if isinstance(value, str) and value.startswith(f"{NS}:"):
                if not os.path.exists(os.path.join(tex_dir, value.split(":", 1)[1] + ".png")):
                    missing.add((os.path.basename(path), value))
    return missing


def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else JAR_DEFAULT
    v = Vanilla(jar)
    textures = build_textures(v)
    assets = gen_shared_assets(textures)
    gen_version_trees()
    print(f"Generated {len(textures)} textures + assets + version trees from {jar}")
    missing = verify_texture_refs(assets)
    if missing:
        print(f"ERROR: {len(missing)} model(s) reference a texture that was never generated:")
        for name, ref in sorted(missing)[:20]:
            print(f"    {name} -> {ref}")
        raise SystemExit(1)
    print("All model texture references resolve.")

if __name__ == "__main__":
    main()
