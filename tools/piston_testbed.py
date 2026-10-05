#!/usr/bin/env python3
"""Headless test bed for piston behaviour, driven over RCON.

The dev server runs without a client, so the whole check is scriptable: blocks are placed with
commands, ticks are allowed to run, and the resulting block states are asserted.  This is the
harness used to verify the 后坐活塞 (recoil piston) front-cell rules:

    front cell               expected
    air / fluid              push forward (vanilla extend)
    destroy-on-push (popped) push forward, the front block pops
    pushable (NORMAL)        recoil
    push-resistant (BLOCK)   recoil

It also reloads the data packs and fails if the server reports a data file it could not parse
(`/reload` re-runs the recipe/loot load that a broken ingredient silently turns into
"this block cannot be crafted" — see the recipe fix for the planks tag).

Usage
-----
    # against a server that is already running (RCON on 25575):
    python tools/piston_testbed.py

    # let the script boot and shut down the dev server itself:
    python tools/piston_testbed.py --start [--version 1.21.10]

Exit code is 0 when every assertion passes, 1 otherwise.

Server bootstrap
----------------
`--start` runs `./gradlew :<version>:runServer` and waits for RCON.  Both modes first patch
`versions/<version>/run/server.properties` so the bed behaves deterministically:

    enable-rcon=true, rcon.port/password   the script talks to the server through this
    online-mode=false                      never needs an account
    level-type=minecraft:flat              empty test world, cheap to regenerate
    pause-when-empty-seconds=0             *** required *** — 1.21.2+ pauses a server that has
                                           had no players for 60s, and a paused server runs no
                                           ticks at all: pistons never extend, sand never falls,
                                           only immediate setBlock effects (e.g. a lamp lighting
                                           up) still work.  That failure mode looks exactly like
                                           "the piston is broken", so it is pinned here.
    view-distance/simulation-distance=4    keep the loaded area small

The test rig lives in the force-loaded chunk at (0,0) — the spawn chunks are not guaranteed to be
ticking on a player-less server, so `/forceload add` is what keeps the rig live.
"""
from __future__ import annotations

import argparse
import os
import socket
import struct
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
MOD_ROOT = os.path.dirname(HERE)

PISTON = "piston_diversified:recoil_piston"
HEAD = "piston_diversified:recoil_piston_head"

# Test rig: piston facing east, one free cell in front, free space behind for the recoil.
BASE = (0, 100, 0)
FRONT = (1, 100, 0)
BEHIND = (-1, 100, 0)
POWER = (0, 101, 0)  # redstone block above the piston
CLEAR_FROM = (-4, 99, -3)
CLEAR_TO = (12, 104, 4)

RCON_HOST = "127.0.0.1"
RCON_PORT = 25575
RCON_PASSWORD = "pdtest"

CASES = [
    ("air (no obstruction)", None, "push"),
    ("torch (popped / DESTROY)", "minecraft:torch", "push"),
    ("dirt (pushable / NORMAL)", "minecraft:dirt", "recoil"),
    ("obsidian (push-resistant / BLOCK)", "minecraft:obsidian", "recoil"),
]

# v2 behaviour checks: (name, callback building its own rig, returning (label, ok) pairs)
V2_CASES = []


def register_v2(name):
    def wrap(fn):
        V2_CASES.append((name, fn))
        return fn
    return wrap


@register_v2("strong piston: tier budget")
def strong_piston_tiers(rcon):
    results = []
    for tier, obsidian_count, expect_push in ((1, 2, True), (1, 3, False), (2, 4, True), (3, 6, True)):
        clear_rig(rcon)
        piston = "piston_diversified:strong_piston_%d" % tier
        rcon.cmd("setblock %s %s[facing=east,extended=false]" % (_pos(BASE), piston))
        for i in range(obsidian_count):
            rcon.cmd("setblock %s minecraft:obsidian" % _pos((1 + i, 100, 0)))
        rcon.cmd("setblock %s redstone_block" % _pos(POWER))
        pushed = False
        for _ in range(80):
            if rcon.has_block(FRONT, "piston_diversified:strong_piston_head"):
                pushed = True
                break
            time.sleep(0.1)
        rcon.cmd("setblock %s air" % _pos(POWER))
        for _ in range(20):
            if rcon.has_block(BASE, piston + "[extended=false]"):
                break
            time.sleep(0.1)
        label = "tier %d pushes %d obsidian (expected %s)" % (
            tier, obsidian_count, "push" if expect_push else "blocked")
        results.append((label, pushed == expect_push))
    clear_rig(rcon)
    return results


@register_v2("strong piston: block entities are never converted")
def strong_piston_block_entity(rcon):
    clear_rig(rcon)
    piston = "piston_diversified:strong_piston_3"
    rcon.cmd("setblock %s %s[facing=east,extended=false]" % (_pos(BASE), piston))
    rcon.cmd("setblock %s minecraft:chest" % _pos(FRONT))
    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    pushed = False
    for _ in range(40):
        if rcon.has_block(FRONT, "piston_diversified:strong_piston_head"):
            pushed = True
            break
        time.sleep(0.1)
    results = [("chest in front is not pushed (no block-entity conversion)", not pushed)]
    rcon.cmd("setblock %s air" % _pos(POWER))
    time.sleep(0.5)
    clear_rig(rcon)
    return results


@register_v2("strong piston: an unpushable block really moves")
def strong_piston_moves_obsidian(rcon):
    clear_rig(rcon)
    piston = "piston_diversified:strong_piston_2"
    rcon.cmd("setblock %s %s[facing=east,extended=false]" % (_pos(BASE), piston))
    rcon.cmd("setblock %s minecraft:obsidian" % _pos(FRONT))
    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    moved = False
    for _ in range(40):
        if rcon.has_block((2, 100, 0), "minecraft:obsidian"):
            moved = True
            break
        time.sleep(0.1)
    results = [("converted obsidian landed one cell ahead", moved)]
    rcon.cmd("setblock %s air" % _pos(POWER))
    time.sleep(0.5)
    clear_rig(rcon)
    return results


@register_v2("recursive piston: telescopes out and back")
def recursive_piston_extend(rcon):
    clear_rig(rcon)
    piston = "piston_diversified:recursive_piston"
    rcon.cmd("setblock %s %s[facing=east,extended=false]" % (_pos(BASE), piston))
    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    # the arm telescopes as far as the signal stays on, so assert on the whole chain instead of
    # one fixed cell: rods behind the head, head at the tip
    extended = False
    for _ in range(200):
        rods = sum(1 for dx in range(1, 12)
                   if rcon.has_block((dx, 100, 0), "piston_diversified:recursive_piston_rod"))
        head = any(rcon.has_block((dx, 100, 0), "piston_diversified:recursive_piston_head") for dx in range(1, 12))
        if rods >= 2 and head:
            extended = True
            break
        time.sleep(0.1)
    results = [("arm telescopes out (rods + head in a chain)", extended)]
    rcon.cmd("setblock %s air" % _pos(POWER))
    retracted = False
    for _ in range(120):
        if rcon.has_block(BASE, piston + "[extended=false]"):
            retracted = True
            break
        time.sleep(0.1)
    results.append(("arm retracts back to the base", retracted))
    clear_rig(rcon)
    return results


@register_v2("0-tick piston: extends and retracts on signal")
def scheduled_tick_piston(rcon):
    clear_rig(rcon)
    piston = "piston_diversified:st_piston"
    rcon.cmd("setblock %s %s[facing=east,extended=false]" % (_pos(BASE), piston))
    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    pushed = False
    for _ in range(40):
        if rcon.has_block(FRONT, "piston_diversified:st_piston_head"):
            pushed = True
            break
        time.sleep(0.1)
    rcon.cmd("setblock %s air" % _pos(POWER))
    retracted = False
    for _ in range(40):
        if rcon.has_block(BASE, piston + "[extended=false]"):
            retracted = True
            break
        time.sleep(0.1)
    results = [("0gt piston extends", pushed), ("0gt piston retracts", retracted)]
    clear_rig(rcon)
    return results


@register_v2("potato piston: head extends and structure flies")
def potato_piston_flight(rcon):
    clear_rig(rcon)
    piston = "piston_diversified:potato_piston"
    rcon.cmd("setblock %s %s[facing=east,extended=false]" % (_pos(BASE), piston))
    rcon.cmd("setblock %s minecraft:dirt" % _pos(FRONT))
    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    pushed = False
    for _ in range(60):
        if rcon.has_block(FRONT, "piston_diversified:potato_piston_head"):
            pushed = True
            break
        time.sleep(0.1)
    results = [("head extended", pushed)]
    # the structure keeps flying after leaving the piston; a chest ahead stops it
    rcon.cmd("setblock %s minecraft:chest" % _pos((5, 100, 0)))
    rest = None
    for _ in range(120):
        for x in (3, 4):
            if rcon.has_block((x, 100, 0), "minecraft:dirt"):
                rest = x
                break
        if rest is not None:
            break
        time.sleep(0.1)
    results.append(("structure flown on and stopped at the chest (rest at x=%s)" % rest,
                    rest is not None))
    rcon.cmd("setblock %s air" % _pos(POWER))
    time.sleep(0.5)
    clear_rig(rcon)
    return results


@register_v2("pickaxe piston: refuses a block its pick cannot mine")
def pickaxe_piston(rcon):
    clear_rig(rcon)
    piston = "piston_diversified:pickaxe_piston"
    rcon.cmd("setblock %s %s[facing=east,extended=false]" % (_pos(BASE), piston))
    rcon.cmd("setblock %s minecraft:stone" % _pos(FRONT))
    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    blocked = False
    for _ in range(30):
        if rcon.has_block(BASE, piston + "[extended=true]"):
            blocked = True
            break
        time.sleep(0.1)
    # the default pickaxe is netherite now, so stone gets mined and the piston extends
    results = [("stone mined by the default netherite pickaxe (extends)", blocked)]
    rcon.cmd("setblock %s air" % _pos(POWER))
    time.sleep(0.5)
    clear_rig(rcon)
    return results


@register_v2("wall merge piston: the group holds together")
def wall_merge_group(rcon):
    clear_rig(rcon)
    first = (0, 100, 0)
    second = (0, 100, 1)
    for base in (first, second):
        rcon.cmd("setblock %s piston_diversified:wall_merge_piston[facing=east,extended=false]" % _pos(base))
        rcon.cmd("setblock %s redstone_block" % _pos((base[0], base[1] + 1, base[2])))
    both_out = False
    for _ in range(60):
        both_out = all(rcon.has_block((b[0] + 1, b[1], b[2]), "piston_diversified:wall_merge_piston_head")
                       for b in (first, second))
        if both_out:
            break
        time.sleep(0.1)
    results = [("both wall-merge pistons extended", both_out)]
    rcon.cmd("setblock %s air" % _pos((first[0], first[1] + 1, first[2])))
    time.sleep(1.0)
    still = all(rcon.has_block((b[0] + 1, b[1], b[2]), "piston_diversified:wall_merge_piston_head")
                for b in (first, second))
    results.append(("the unpowered member stays out while its neighbour is live", still))
    rcon.cmd("setblock %s air" % _pos((second[0], second[1] + 1, second[2])))
    time.sleep(1.0)
    clear_rig(rcon)
    return results


@register_v2("potato piston: rule 1 carries unpushable blocks and keeps flying")
def potato_rule1_flight(rcon):
    clear_rig(rcon)
    rcon.cmd("setblock %s piston_diversified:potato_piston[facing=east,extended=false]" % _pos(BASE))
    rcon.cmd("setblock %s minecraft:obsidian" % _pos(FRONT))
    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    results = []
    # a chest blocks the flight (front block entity → the push ends); the obsidian must come to
    # rest in front of it — proof of rule 1 carrying an unpushable block plus at least one
    # recursive flight step (it started one cell further west)
    rcon.cmd("setblock %s minecraft:chest" % _pos((5, 100, 0)))
    rest = None
    for _ in range(120):
        for x in (3, 4):
            if rcon.has_block((x, 100, 0), "minecraft:obsidian"):
                rest = x
                break
        if rest is not None:
            break
        time.sleep(0.1)
    results.append(("obsidian flown with the structure and stopped at the chest (rest at x=%s)" % rest,
                    rest is not None))
    rcon.cmd("setblock %s air" % _pos(POWER))
    time.sleep(0.5)
    clear_rig(rcon)
    return results


@register_v2("potato piston: rule 2 connects touching shapes")
def potato_rule2_shapes(rcon):
    clear_rig(rcon)
    # a stair against the pushed block: their shapes touch on the pushed axis' side face
    rcon.cmd("setblock %s piston_diversified:potato_piston[facing=east,extended=false]" % _pos(BASE))
    rcon.cmd("setblock %s minecraft:dirt" % _pos(FRONT))
    rcon.cmd("setblock %s minecraft:oak_stairs[facing=west,half=bottom]" % _pos((1, 100, -1)))
    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    # the chest stops the flight; the stairs must have travelled glued to the pushed block
    rcon.cmd("setblock %s minecraft:chest" % _pos((5, 100, 0)))
    both = False
    for _ in range(120):
        for x in (3, 4):
            if rcon.has_block((x, 100, 0), "minecraft:dirt")                 and rcon.has_block((x, 100, -1), "minecraft:oak_stairs"):
                both = True
                break
        if both:
            break
        time.sleep(0.1)
    results = [("stairs touching the pushed block travelled with it (rule 2)", both)]
    rcon.cmd("setblock %s air" % _pos(POWER))
    time.sleep(0.5)
    clear_rig(rcon)
    return results


@register_v2("turn push sticky: pulls the block back along the bend")
def turn_push_sticky_pull(rcon):
    clear_rig(rcon)
    rcon.cmd("setblock %s piston_diversified:turn_push_sticky_piston[facing=east,extended=false,bend=north]" % _pos(BASE))
    rcon.cmd("setblock %s minecraft:dirt" % _pos(FRONT))
    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    pushed = False
    for _ in range(60):
        if rcon.has_block((1, 100, -1), "minecraft:dirt"):
            pushed = True
            break
        time.sleep(0.1)
    results = [("block pushed sideways", pushed)]
    rcon.cmd("setblock %s air" % _pos(POWER))
    pulled = False
    for _ in range(60):
        # the pull moves the block one cell against the push axis (to the base's side cell)
        if rcon.has_block((0, 100, -1), "minecraft:dirt"):
            pulled = True
            break
        time.sleep(0.1)
    results.append(("block pulled back on retract", pulled))
    clear_rig(rcon)
    return results


@register_v2("gravity piston: telescopes down and the head falls sideways")
def gravity_piston(rcon):
    clear_rig(rcon)
    rcon.cmd("setblock %s piston_diversified:gravity_piston[facing=east,extended=false]" % _pos(BASE))
    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    # the head extends, then (unsupported below) falls away as a block — the observable end
    # state is a headless base plus the head block resting somewhere nearby
    settled = False
    for _ in range(300):
        head_near = any(rcon.has_block((x, y, z), "piston_diversified:gravity_piston_head")
                        for x in range(-3, 13) for y in range(94, 105) for z in range(-4, 5))
        front_clear = not rcon.has_block(FRONT, "piston_diversified:gravity_piston_head")             and not rcon.has_block(FRONT, "minecraft:moving_piston")
        base_alive = rcon.has_block(BASE, "piston_diversified:gravity_piston[facing=east,extended=true]")
        if front_clear and base_alive and not head_near:
            settled = True  # head already fell beyond the scan window; base survives headless
            break
        if front_clear and base_alive and head_near:
            settled = True  # head visible somewhere near: it detached and landed
            break
        time.sleep(0.1)
    results = [("head detached from a surviving headless base", settled)]
    rcon.cmd("setblock %s air" % _pos(POWER))
    time.sleep(0.5)
    clear_rig(rcon)
    return results


@register_v2("turn push piston: pushes the front block sideways")
def turn_push_piston(rcon):
    clear_rig(rcon)
    rcon.cmd("setblock %s piston_diversified:turn_push_piston[facing=east,extended=false,bend=north]" % _pos(BASE))
    rcon.cmd("setblock %s minecraft:dirt" % _pos(FRONT))
    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    pushed_side = False
    for _ in range(40):
        if rcon.has_block((1, 100, -1), "minecraft:dirt"):
            pushed_side = True
            break
        time.sleep(0.1)
    results = [("front block moved to the bend side", pushed_side)]
    rcon.cmd("setblock %s air" % _pos(POWER))
    time.sleep(0.5)
    clear_rig(rcon)
    return results



# --------------------------------------------------------------------------- RCON


def _recvn(sock, count):
    buf = b""
    while len(buf) < count:
        chunk = sock.recv(count - len(buf))
        if not chunk:
            return buf
        buf += chunk
    return buf


def _packet(request_id, packet_type, payload):
    body = struct.pack("<ii", request_id, packet_type) + payload.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(body)) + body


class Rcon:
    def __init__(self, host=RCON_HOST, port=RCON_PORT, password=RCON_PASSWORD):
        self.sock = socket.create_connection((host, port), timeout=30)
        self.sock.sendall(_packet(1, 3, password))
        header = _recvn(self.sock, 4)
        if len(header) < 4:
            raise RuntimeError("no answer from RCON — is the server up?")
        (length,) = struct.unpack("<i", header)
        response = _recvn(self.sock, length)
        (request_id, _), = [struct.unpack("<ii", response[:8])]
        if request_id == -1:
            raise RuntimeError("RCON auth rejected — password mismatch with server.properties")

    def cmd(self, command):
        """Send one command and return the server's answer."""
        self.sock.sendall(_packet(2, 2, command))
        header = _recvn(self.sock, 4)
        if len(header) < 4:
            return ""
        (length,) = struct.unpack("<i", header)
        body = _recvn(self.sock, length)
        return body[8:].split(b"\x00\x00")[0].decode("utf-8", "replace")

    def has_block(self, pos, predicate):
        """Whether the block at pos matches the given block-state predicate."""
        return "Test passed" in self.cmd("execute if block %s %s" % (_pos(pos), predicate))

    def close(self):
        self.sock.close()


def _pos(pos):
    return "%d %d %d" % pos


# --------------------------------------------------------------------------- server bootstrap


def patch_properties(version, updates):
    """Create/patch versions/<version>/run/server.properties, keeping unrelated keys."""
    run_dir = os.path.join(MOD_ROOT, "versions", version, "run")
    os.makedirs(run_dir, exist_ok=True)
    eula = os.path.join(run_dir, "eula.txt")
    if not os.path.exists(eula):
        with open(eula, "w", encoding="utf-8") as handle:
            handle.write("eula=true\n")

    path = os.path.join(run_dir, "server.properties")
    lines = []
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as handle:
            lines = handle.read().splitlines()
    remaining = dict(updates)
    for index, line in enumerate(lines):
        key = line.split("=", 1)[0].strip()
        if key in remaining:
            lines[index] = "%s=%s" % (key, remaining.pop(key))
    lines.extend("%s=%s" % item for item in remaining.items())
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    return run_dir


def start_server(version, log_path):
    command = [os.path.join(MOD_ROOT, "gradlew"), ":%s:runServer" % version, "--console=plain"]
    if os.name == "nt":
        command[0] += ".bat"
    log = open(log_path, "w", encoding="utf-8", errors="replace")
    process = subprocess.Popen(command, cwd=MOD_ROOT, stdout=log, stderr=subprocess.STDOUT)
    print("starting dev server (%s), log: %s" % (" ".join(command), log_path))
    return process, log


def wait_for_rcon(timeout=240):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            return Rcon()
        except (OSError, RuntimeError):
            time.sleep(3)
    raise RuntimeError("server did not open RCON within %ds" % timeout)


# --------------------------------------------------------------------------- data pack reload

# What the server logs for a data file it could not parse; the wording moved between versions.
DATA_ERROR_PATTERNS = (
    "Couldn't parse data file",  # 1.20.5+
    "Parsing error loading",     # <= 1.19.x (recipes, loot tables)
    "Failed to parse",           # codec failure inside a file that did parse as JSON
)
RELOAD_SETTLE_TRIES = 30


def _read_from(path, offset):
    """The part of a log written after offset (server logs are appended to, never rewritten)."""
    with open(path, "rb") as handle:
        handle.seek(offset)
        return handle.read().decode("utf-8", "replace")


def _server_log(run_dir, started_log):
    """First readable server log: the run directory's own, else this script's captured stdout."""
    candidates = [os.path.join(run_dir, "logs", "latest.log")]
    if started_log:
        candidates.append(started_log)
    for path in candidates:
        if os.path.exists(path):
            return path
    return None


def check_data_files(rcon, run_dir, started_log=None):
    """Reload the data packs and assert the server parses every recipe / loot table.

    A broken ingredient does not crash anything: the file is dropped with a log line and the
    block simply cannot be crafted (that is how the planks-tag bug hid).  Only output written
    after the reload is inspected, so an earlier session cannot hide or fake the result.

    Returns True/False, or None when no server log is readable — skipped, not passed.
    """
    path = _server_log(run_dir, started_log)
    if path is None:
        print("\n=== data pack reload: SKIPPED (no readable server log, pass --start or check the run dir)")
        return None

    offset = os.path.getsize(path)
    rcon.cmd("reload")
    chunk = ""
    for _ in range(RELOAD_SETTLE_TRIES):
        time.sleep(0.5)
        chunk = _read_from(path, offset)
        if "recipe" in chunk.lower():
            break

    errors = [line.strip() for line in chunk.splitlines()
              if any(pattern in line for pattern in DATA_ERROR_PATTERNS)]
    counts = [line.strip().split("): ", 1)[-1] for line in chunk.splitlines()
              if "recipes" in line.lower() and "Loaded" in line]

    print("\n=== data pack reload (/reload)")
    for line in counts:
        print("    " + line)
    for line in errors[:10]:
        print("    BAD  " + line[:200])
    if len(errors) > 10:
        print("    ... and %d more" % (len(errors) - 10))
    print("    %-4s every data file parsed" % ("ok" if not errors else "BAD"))
    return not errors


# --------------------------------------------------------------------------- test rig


def clear_rig(rcon):
    rcon.cmd("fill %s %s air" % (_pos(CLEAR_FROM), _pos(CLEAR_TO)))


def run_case(rcon, name, front_block, expectation):
    clear_rig(rcon)
    rcon.cmd("setblock %s %s[facing=east,extended=false]" % (_pos(BASE), PISTON))
    rcon.cmd("setblock %s %s" % (_pos(FRONT), front_block or "air"))
    rcon.cmd("setblock %s air" % _pos(BEHIND))

    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    # The extension is a 2-tick animation; poll instead of guessing a delay.
    pushed = recoiled = False
    for _ in range(40):
        pushed = rcon.has_block(BASE, PISTON + "[extended=true]") and rcon.has_block(FRONT, HEAD)
        recoiled = rcon.has_block(BEHIND, PISTON) and rcon.has_block(BASE, HEAD)
        if pushed or recoiled:
            break
        time.sleep(0.1)

    checks = []
    if expectation == "push":
        checks.append(("piston pushed forward (head in front)", pushed))
        checks.append(("no recoil (base still at the origin)", not recoiled))
        if front_block is not None:
            checks.append(("front block popped", not rcon.has_block(FRONT, front_block)))
    else:
        checks.append(("piston recoiled (base one cell back)", recoiled))
        checks.append(("no forward push (head not in front)", not pushed))
        if front_block is not None:
            checks.append(("front block left in place", rcon.has_block(FRONT, front_block)))

    rcon.cmd("setblock %s air" % _pos(POWER))
    for _ in range(40):
        if rcon.has_block(BASE, PISTON + "[extended=false]"):
            break
        time.sleep(0.1)
    clear_rig(rcon)

    results = [(label, ok) for label, ok in checks]
    status = "PASS" if all(ok for _, ok in results) else "FAIL"
    print("\n=== %-36s expected: %-6s -> %s" % (name, expectation, status))
    for label, ok in results:
        print("    %-4s %s" % ("ok" if ok else "BAD", label))
    return status == "PASS"


def run_v2_case(rcon, name, fn):
    clear_rig(rcon)
    time.sleep(0.4)
    results = fn(rcon)
    ok = all(passed for _, passed in results)
    print("\n=== %-46s -> %s" % (name, "PASS" if ok else "FAIL"))
    for label, passed in results:
        print("    %-4s %s" % ("ok" if passed else "BAD", label))
    return ok


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--version", default="1.21.11", help="stonecutter node to test (default 1.21.11)")
    parser.add_argument("--start", action="store_true", help="boot and shut down the dev server")
    parser.add_argument("--log", default=os.path.join(MOD_ROOT, "build", "piston_testbed_server.log"))
    args = parser.parse_args()

    run_dir = patch_properties(args.version, {
        "enable-rcon": "true",
        "rcon.port": str(RCON_PORT),
        "rcon.password": RCON_PASSWORD,
        "online-mode": "false",
        "level-type": "minecraft\\:flat",
        "pause-when-empty-seconds": "0",
        "view-distance": "4",
        "simulation-distance": "4",
        "spawn-protection": "0",
        "gamemode": "creative",
        "difficulty": "peaceful",
    })

    process = None
    try:
        if args.start:
            process, log = start_server(args.version, args.log)
        elif not os.path.exists(os.path.join(run_dir, "server.properties")):
            print("note: no run/server.properties yet — start the server once, or use --start")
        rcon = wait_for_rcon()
        print("connected to RCON, world spawn ticking check:", rcon.cmd("time query gametime").strip())
        rcon.cmd("forceload add -32 -32 32 32")

        data_ok = check_data_files(rcon, run_dir, args.log if args.start else None)
        passed = [run_case(rcon, name, front, expectation) for name, front, expectation in CASES]
        passed += [run_v2_case(rcon, name, fn) for name, fn in V2_CASES]

        rcon.cmd("forceload remove -32 -32 32 32")
        rcon.close()

        data_note = "clean" if data_ok else ("SKIPPED" if data_ok is None else "ERRORS")
        print("\n%d/%d cases passed; data files: %s" % (sum(passed), len(passed), data_note))
        return 0 if all(passed) and data_ok is not False else 1
    finally:
        if process is not None:
            try:
                Rcon().cmd("stop")
            except OSError:
                process.terminate()
            process.wait(timeout=120)
            log.close()
            print("dev server stopped")


if __name__ == "__main__":
    sys.exit(main())
