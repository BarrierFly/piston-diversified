import io, sys, re

p = 'tools/gen_assets.py'
s = io.open(p, encoding='utf-8').read()

# drop the leftover stub (first bent_head_model with the wrong docstring) and the old body;
# replace with a plate-positioned implementation
old_stub = '''def bent_head_model(vid, plate_dir, base_sticky, short, sticky_plate=False):
    """递推 head: plate + rod, rod continues into the previous cell when not short."""
    return _plate_rod_head(vid, short, sticky, rod_width=4, rod_from=0)


'''
assert old_stub in s
s = s.replace(old_stub, '')

old_body_start = s.index('def bent_head_model(vid, bend, base_sticky, short, sticky_plate=False):')
old_body_end = s.index('# ---------------------------------------------------------------- generation', old_body_start)
new_body = '''def bent_head_model(vid, plate_dir, base_sticky, short, sticky_plate=False):
    """拐推 head: the plate sits on the model-local bend face, the rod runs along FACING.

    plate_dir is one of the six model-local directions; only the ones reachable for a given
    (facing, bend) pair are referenced by the blockstate.
    """
    plate_texture = f"{NS}:block/{vid}_top_sticky" if sticky_plate else f"{NS}:block/{vid}_top"
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
            "platform": plate_texture,
            "side": side,
        },
        "elements": [plate, rod],
    }


'''
s = s[:old_body_start] + new_body + s[old_body_end:]

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('bent head model rewritten')
