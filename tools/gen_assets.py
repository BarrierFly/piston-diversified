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
}

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

    return out

# ---------------------------------------------------------------- variants metadata

# (id, sticky, custom, head_kind)
# head_kind: "plate" (vanilla geometry), "end_rod", "weak", "recoil", "skull"
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
                "from": [6, 6, 4], "to": [10, 10, 16],
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

# ---------------------------------------------------------------- generation

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
        for extended in (False, True):
            for facing, rot in FACING_ROT.items():
                key = f"extended={extended},facing={facing}"
                if custom and head_kind == "end_rod":
                    variants[key] = {"model": f"{NS}:block/{vid}_base"}
                elif extended:
                    variants[key] = {"model": f"{NS}:block/{vid}_extended", **rot}
                else:
                    variants[key] = {"model": f"{NS}:block/{vid}", **rot}
        write_json(os.path.join(bs_dir, vid + ".json"), {"variants": variants})

        # --- base models
        if custom and head_kind == "end_rod":
            model = end_rod_base_model(vid)
            write_json(os.path.join(model_dir, vid + "_base.json"), model)
        else:
            write_json(os.path.join(model_dir, vid + ".json"), base_model_retracted(vid, sticky))
            write_json(os.path.join(model_dir, vid + "_extended.json"), base_model_extended(vid))
        write_json(os.path.join(model_dir, vid + "_inventory.json"), base_model_inventory(vid, sticky))

        # --- item models (both formats; unused format is ignored by the other versions)
        write_json(os.path.join(item_def_dir, vid + ".json"),
                   {"model": {"type": "minecraft:model", "model": f"{NS}:block/{vid}_inventory"}})
        write_json(os.path.join(item_model_dir, vid + ".json"),
                   {"parent": f"{NS}:block/{vid}_inventory"})

        # --- head blockstate + models
        hid = vid + "_head"
        head_variants = {}
        head_models = {}
        if head_kind == "plate":
            head_models[("normal", False, False)] = head_model_plate(vid, False)
            head_models[("normal", True, False)] = head_model_plate_short(vid, False)
            head_models[("sticky", False, False)] = head_model_plate(vid, True)
            head_models[("sticky", True, False)] = head_model_plate_short(vid, True)
        elif head_kind == "skull":
            head_models[("normal", False, False)] = head_model_plate(vid, False)
            head_models[("normal", True, False)] = head_model_plate_short(vid, False)
            head_models[("normal", False, True)] = {**head_model_plate(vid, False),
                                                    "textures": {**head_model_plate(vid, False)["textures"],
                                                                 "platform": f"{NS}:block/{vid}_top_powered"}}
            head_models[("normal", True, True)] = {**head_model_plate_short(vid, False),
                                                   "textures": {**head_model_plate_short(vid, False)["textures"],
                                                                "platform": f"{NS}:block/{vid}_top_powered"}}
        elif head_kind == "end_rod":
            head_models[("normal", False, False)] = end_rod_head_model(vid, False)
            head_models[("normal", True, False)] = end_rod_head_model(vid, True)
        elif head_kind == "weak":
            head_models[("normal", False, False)] = weak_head_model(vid, False)
            head_models[("normal", True, False)] = weak_head_model(vid, True)
        elif head_kind == "recoil":
            head_models[("normal", False, False)] = recoil_head_model(vid, False)
            head_models[("normal", True, False)] = recoil_head_model(vid, True)

        for (ptype, short, powered), model in head_models.items():
            suffix = ""
            if head_kind == "skull":
                suffix = "_powered" if powered else ""
                key = f"facing={{facing}},powered={str(powered).lower()},short={str(short).lower()},type={ptype}"
            else:
                key = f"facing={{facing}},short={str(short).lower()},type={ptype}"
            for facing, rot in FACING_ROT.items():
                head_variants[key.format(facing=facing)] = {
                    "model": f"{NS}:block/{hid}{suffix}", **rot
                }
            fname = hid + suffix + ("_short" if short else "")
            write_json(os.path.join(model_dir, fname + ".json"), model)
        write_json(os.path.join(bs_dir, hid + ".json"), {"variants": head_variants})

# ---------------------------------------------------------------- per-version data

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
                                       {"T": "minecraft:planks", "P": "minecraft:planks",
                                        "#": "minecraft:cobblestone", "X": "minecraft:iron_ingot",
                                        "R": "minecraft:redstone"})),
    ("end_rod_piston", "shapeless", ["minecraft:piston", "minecraft:end_rod"], None),
    ("redstone_end_rod_piston", "shapeless", ["minecraft:piston", "minecraft:end_rod", "minecraft:redstone"], None),
    ("long_push_piston", "shapeless", ["minecraft:piston", "minecraft:redstone_block"], None),
    ("qc_piston", "shaped", None, (["TRT", "#X#", "#P#"],
                                   {"T": "minecraft:planks", "R": "minecraft:redstone",
                                    "#": "minecraft:cobblestone", "X": "minecraft:iron_ingot",
                                    "P": "minecraft:planks"})),
    ("qc_sticky_piston", "shapeless", ["piston_diversified:qc_piston", "minecraft:slime_ball"], None),
    ("observer_piston", "shapeless", ["minecraft:piston", "minecraft:observer"], None),
    ("observer_sticky_piston", "shapeless", ["piston_diversified:observer_piston", "minecraft:slime_ball"], None),
    ("weak_piston", "shaped", None, (["TTT", "#N#", "#R#"],
                                     {"T": "minecraft:planks", "#": "minecraft:cobblestone",
                                      "N": "minecraft:iron_nugget", "R": "minecraft:redstone"})),
    ("fast_piston", "shaped", None, (["TTT", "#I#", "#R#"],
                                     {"T": "minecraft:planks", "#": "minecraft:cobblestone",
                                      "I": "minecraft:ice", "R": "minecraft:redstone"})),
    ("fast_sticky_piston", "shapeless", ["piston_diversified:fast_piston", "minecraft:slime_ball"], None),
]

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
            "description": "Adds piston variants. v1 batch: 19 pistons (honey, projectile, chain, loop, wind charge, silent, recoil, end rod, skull, directional QC, observer, redstone end rod, long push, weak, fast).",
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

        for name, kind, ingredients, shaped in RECIPES:
            if name == "wind_charge_piston" and ver == "1.19.4":
                continue  # no wind charge item on 1.19.4 (规划: 没有风弹的版本不可合成)
            data = recipe_json_119(name, kind, ingredients, shaped) if old_data else recipe_json_modern(name, kind, ingredients, shaped)
            write_json(os.path.join(res, "data", NS, recipe_dirname, name + ".json"), data)

        for vid, *_ in VARIANTS:
            table = loot_json(True)
            table["pools"][0]["entries"][0]["name"] = f"{NS}:{vid}"
            if not old_data:
                table["random_sequence"] = f"{NS}:blocks/{vid}"
            write_json(os.path.join(res, "data", NS, loot_dirname, "blocks", vid + ".json"), table)

def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else JAR_DEFAULT
    v = Vanilla(jar)
    textures = build_textures(v)
    gen_shared_assets(textures)
    gen_version_trees()
    print(f"Generated {len(textures)} textures + assets + version trees from {jar}")

if __name__ == "__main__":
    main()
