"""Quick RCON probe: place a piston variant, power it, and dump the cells around it."""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from piston_testbed import Rcon, clear_rig, BASE, FRONT, POWER, _pos  # noqa: E402


def dump(rcon, label, base, direction="east"):
    print("--- %s" % label)
    for dz in range(-1, 2):
        row = []
        for dx in range(-1, 5):
            x = base[0] + dx
            z = base[2] + dz
            out = rcon.cmd("execute if block %d %d %d minecraft:air run say ." % (x, base[1], z))
            block = rcon.cmd("data get block %d %d %d id" % (x, base[1], z))
            if "Test failed" in out or "." in out:
                row.append("   .  ")
            else:
                row.append((block.split('"')[-2] if '"' in block else "?").replace("minecraft:", "").replace("piston_diversified:", "pd:")[:10])
        print("   z%+d  %s" % (dz, " | ".join(row)))


def main():
    which = sys.argv[1] if len(sys.argv) > 1 else "potato"
    rcon = Rcon()
    rcon.cmd("forceload add -32 -32 32 32")
    rcon.cmd("fill -4 99 -3 6 104 3 air")
    if which == "potato":
        rcon.cmd("setblock %s piston_diversified:potato_piston[facing=east,extended=false]" % _pos(BASE))
        rcon.cmd("setblock %s minecraft:dirt" % _pos(FRONT))
    else:
        rcon.cmd("setblock %s piston_diversified:recursive_piston[facing=east,extended=false]" % _pos(BASE))
    dump(rcon, "before powering", BASE)
    rcon.cmd("setblock %s redstone_block" % _pos(POWER))
    for step in range(12):
        time.sleep(0.5)
        dump(rcon, "t+%.1fs" % ((step + 1) * 0.5), BASE)
        if "extended=true" not in rcon.cmd("execute if block %s piston_diversified:%s_piston[facing=east,extended=true]" % (_pos(BASE), which)):
            pass
        else:
            break
    rcon.cmd("fill -4 99 -3 6 104 3 air")
    rcon.cmd("forceload remove -32 -32 32 32")
    rcon.close()


if __name__ == "__main__":
    main()