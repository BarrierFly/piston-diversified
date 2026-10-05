import io, sys

p = 'tools/gen_assets.py'
s = io.open(p, encoding='utf-8').read()

# --- world bend -> model-local bend, per facing rotation ---
helper = '''
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

'''
anchor = 'def gen_shared_assets(textures):'
assert anchor in s
s = s.replace(anchor, helper + '\n' + anchor, 1)

# --- base blockstate + models generation rewrite ---
old = '''    for vid, sticky, custom, head_kind in VARIANTS:
        # --- base blockstate
        variants = {}
        has_bend = head_kind == "bent"
        for extended in (False, True):
            for facing, rot in FACING_ROT.items():
                extra = ""
                if has_bend:
                    extra += f",bend={BEND_FOR[facing]}"
                if head_kind == "pickaxe":
                    extra += ",tool=iron"
                key = f"extended={str(extended).lower()},facing={facing}{extra}"
                if custom and head_kind == "end_rod":
                    # Rotation must not be dropped here, or the rod model faces one way only.
                    variants[key] = {"model": f"{NS}:block/{vid}_base", **rot}
                elif extended:
                    variants[key] = {"model": f"{NS}:block/{vid}_extended", **rot}
                else:
                    variants[key] = {"model": f"{NS}:block/{vid}", **rot}
        if head_kind == "pickaxe":
            # one plate per carried pickaxe (all other states share the base model)
            for tool in PICKAXE_TINTS:
                tool_variants = {}
                for extended in (False, True):
                    for facing, rot in FACING_ROT.items():
                        model = (f"{NS}:block/{vid}_extended" if extended else f"{NS}:block/{vid}")
                        tool_variants[f"extended={str(extended).lower()},facing={facing},tool={tool}"] = {"model": model, **rot}
                write_json(os.path.join(bs_dir, vid + "_" + tool + ".json"), {"variants": tool_variants})
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
        elif head_kind == "bent":
            # 拐推: the plate texture follows the bend blockstate
            for bend in ("north", "south", "west", "east"):
                write_json(os.path.join(model_dir, f"{vid}_base_{bend}.json"), bent_base_model(vid, bend, sticky, True))
                write_json(os.path.join(model_dir, f"{vid}_{bend}.json"), bent_base_model(vid, bend, sticky, False))
                write_json(os.path.join(model_dir, f"{vid}_extended_{bend}.json"), bent_base_model(vid, bend, sticky, True))
        else:
            write_json(os.path.join(model_dir, vid + ".json"), base_model_retracted(vid, sticky))
            write_json(os.path.join(model_dir, vid + "_extended.json"), base_model_extended(vid))
        write_json(os.path.join(model_dir, vid + "_inventory.json"), base_model_inventory(vid, sticky))'''

new = '''    for vid, sticky, custom, head_kind in VARIANTS:
        # --- base blockstate
        variants = {}
        if head_kind == "bent":
            # (extended, facing, bend) — the retracted model carries the arrow plate pointing
            # at the model-local bend direction
            for extended in (False, True):
                for facing, rot in FACING_ROT.items():
                    for bend in ("north", "south", "west", "east"):
                        key = f"extended={str(extended).lower()},facing={facing},bend={bend}"
                        if extended:
                            variants[key] = {"model": f"{NS}:block/{vid}_extended", **rot}
                        else:
                            lb = local_bend(facing, bend)
                            variants[key] = {"model": f"{NS}:block/{vid}_{lb}", **rot}
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
            # 拐推: retracted base per local bend (arrow plate); extended shares the plain model
            for lb in ("north", "south", "west", "east", "up", "down"):
                write_json(os.path.join(model_dir, f"{vid}_{lb}.json"), bent_base_model(vid, lb, sticky, False))
            write_json(os.path.join(model_dir, vid + "_extended.json"), base_model_extended(vid))
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
            write_json(os.path.join(model_dir, vid + "_inventory.json"), base_model_inventory(vid, sticky))'''

assert old in s
s = s.replace(old, new)

# --- head generation: skip "none"; bent uses local-bend plate models; strong head emitted once ---
old2 = '''        # --- head blockstate + models
        hid = vid + "_head"
        head_variants = {}
        head_models = {}
        if head_kind == "plate":'''
new2 = '''        # --- head blockstate + models
        if head_kind == "none":
            continue  # 强力 piston: base assets only (the shared head is emitted after the loop)
        hid = vid + "_head"
        head_variants = {}
        head_models = {}
        if head_kind == "plate":'''
assert old2 in s
s = s.replace(old2, new2)

old3 = '''        elif head_kind == "bent":
            for bend in ("north", "south", "west", "east"):
                head_models[("normal", False, False, bend)] = bent_head_model(vid, bend, sticky, False)
                head_models[("normal", True, False, bend)] = bent_head_model(vid, bend, sticky, True)
                if sticky:
                    head_models[("sticky", False, False, bend)] = bent_head_model(vid, bend, sticky, False, sticky_plate=True)
                    head_models[("sticky", True, False, bend)] = bent_head_model(vid, bend, sticky, True, sticky_plate=True)'''
new3 = '''        elif head_kind == "bent":
            for lb in ("north", "south", "west", "east", "up", "down"):
                head_models[("normal", False, False, lb)] = bent_head_model(vid, lb, sticky, False)
                head_models[("normal", True, False, lb)] = bent_head_model(vid, lb, sticky, True)
                if sticky:
                    head_models[("sticky", False, False, lb)] = bent_head_model(vid, lb, sticky, False, sticky_plate=True)
                    head_models[("sticky", True, False, lb)] = bent_head_model(vid, lb, sticky, True, sticky_plate=True)'''
assert old3 in s
s = s.replace(old3, new3)

# bent head blockstate key: bend stays the *world* bend; model files keyed by local bend
old4 = '''            elif extra and head_kind == "bent":
                key = f"bend={extra},facing={{facing}},short={str(short).lower()},type={ptype}"'''
new4 = '''            elif extra and head_kind == "bent":
                key = f"bend={extra},facing={{facing}},short={str(short).lower()},type={ptype}"  # extra resolved per facing below'''
assert old4 in s
s = s.replace(old4, new4)

# the variant fill loop must map (facing, world bend) -> local-bend model for bent heads
old5 = '''            for facing, rot in FACING_ROT.items():
                head_variants[key.format(facing=facing)] = {
                    "model": f"{NS}:block/{fname}", **rot
                }'''
new5 = '''            for facing, rot in FACING_ROT.items():
                fname_here = fname
                if head_kind == "bent" and extra:
                    fname_here = fname.replace("_" + extra, "_" + local_bend(facing, extra))
                head_variants[key.format(facing=facing)] = {
                    "model": f"{NS}:block/{fname_here}", **rot
                }'''
assert old5 in s
s = s.replace(old5, new5)

# the sticky-type alias loop must enumerate all extra values for bent/pickaxe
old6 = '''            for facing in FACING_ROT:
                for extra in (["north", "south", "west", "east"] if head_kind == "bent" else
                              list(PICKAXE_TINTS) if head_kind == "pickaxe" else [None]):'''
new6 = '''            for facing in FACING_ROT:
                for extra in (["north", "south", "west", "east"] if head_kind == "bent" else
                              list(PICKAXE_TINTS) if head_kind == "pickaxe" else [None]):'''
if old6 not in s:
    # locate by the actual written form
    old6b = '''            for facing in FACING_ROT:
                        for extra in ('''
    print('alias loop already matches' if old6 in s else 'alias loop variant found')
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('part 8 done')
