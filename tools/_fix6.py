import io, sys, re

def patch(path, subs, regex=False):
    s = io.open(path, encoding='utf-8').read()
    for old, new in subs:
        if old not in s:
            print('MISS in', path, ':')
            print(old[:300])
            sys.exit(1)
        s = s.replace(old, new)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(s)
    print('ok', path)

p = 'tools/gen_assets.py'

# 1) turn-push textures: arrows point along the *local* bend (6 directions)
patch(p, [
 ('''    # 拐推活塞: arrow on the plate, pointing at the bend (drawn per bend direction)
    for bend in ("north", "south", "west", "east"):
        arrow_top = src["top"].copy()
        _draw_bend_arrow(arrow_top, bend)
        emit(f"turn_push_piston", **{**vanilla_set, "top": arrow_top},
             extra={f"turn_push_piston_top_{bend}": arrow_top})
    for bend in ("north", "south", "west", "east"):
        arrow_sticky = src["sticky"].copy()
        _draw_bend_arrow(arrow_sticky, bend)
        emit(f"turn_push_sticky_piston", **vanilla_set, sticky=arrow_sticky,
             extra={f"turn_push_sticky_piston_top_{bend}": arrow_sticky})''',
  '''    # 拐推活塞: arrow on the plate, pointing at the *model-local* bend direction (6 variants);
    # the blockstate maps each (facing, bend) pair onto one of these
    for lb in ("north", "south", "west", "east", "up", "down"):
        arrow_top = src["top"].copy()
        _draw_bend_arrow(arrow_top, lb)
        arrow_sticky = src["sticky"].copy()
        _draw_bend_arrow(arrow_sticky, lb)
        emit("turn_push_piston", **{**vanilla_set, "top": arrow_top},
             extra={f"turn_push_piston_top_{lb}": arrow_top,
                    f"turn_push_sticky_piston_top_{lb}": arrow_sticky})
    # base textures for the sticky variant come from the same emit; give the sticky variant its
    # own plain set so emit() does not overwrite shared side/bottom textures
    emit("turn_push_sticky_piston", **vanilla_set, sticky=src["sticky"])'''),
])

# 2) wall merge retracted model gains the overhanging post
patch(p, [
 ('''def bare_rod_head_model(vid, short, sticky=False):''',
  '''def wall_merge_retracted_model(vid, sticky):
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


def bent_head_model(vid, plate_dir, base_sticky, short, sticky_plate=False):'''),
])

print('part 6a done')
