import io, sys

def patch(path, subs):
    s = io.open(path, encoding='utf-8').read()
    for old, new in subs:
        if old not in s:
            print('MISS in', path, ':')
            print(old[:200])
            sys.exit(1)
        s = s.replace(old, new)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(s)
    print('ok', path)

# --- PotatoStructureResolver: rule 1 carries everything (except BEs), rule 2 offset bug ---
patch('src/main/java/dev/zcode/piston_diversified/logic/PotatoStructureResolver.java', [
 # member classification: no isPushable gate — rule 1 carries unpushable blocks (obsidian etc.)
 ('''            } else if (!PistonBaseBlock.isPushable(state, this.level, pos, this.pushDirection, false, this.pushDirection)) {
                return this.fail("unpushable member blocks the structure: " + state.getBlock());
            } else if (state.hasProperty(BlockStateProperties.WATERLOGGED)''',
  '''            } else if (state.hasProperty(BlockStateProperties.WATERLOGGED)'''),
 ('''            } else {
                kind = MemberKind.NORMAL;
            }

            this.memberSet.add(pos);''',
  '''            } else {
                // rule 1 carries unpushable blocks too (obsidian, bedrock, ...) — an
                // irreplaceable block in front of a pushed block simply joins the structure
                kind = MemberKind.NORMAL;
            }

            this.memberSet.add(pos);'''),
])

print('part 1 done')
