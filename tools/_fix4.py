import io, sys

def patch(path, subs):
    s = io.open(path, encoding='utf-8').read()
    for old, new in subs:
        if old not in s:
            print('MISS in', path, ':')
            print(old[:300])
            sys.exit(1)
        s = s.replace(old, new)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(s)
    print('ok', path)

BASE = 'src/main/java/dev/zcode/piston_diversified/'

# --- recursive retract: source=false so the renderer never treats the head as a piston base
# (vanilla's render path calls setValue(EXTENDED) on the moved state for source retract pistons,
# which crashes on head states that lack the property) ---
patch(BASE + 'block/RecursivePistonBlock.java', [
 ('''        // the head slides back onto the rod: a source moving piston on the rod cell renders it
        BlockState movingState = Blocks.MOVING_PISTON
            .defaultBlockState()
            .setValue(MovingPistonBlock.FACING, direction)
            .setValue(MovingPistonBlock.TYPE, this.sticky ? PistonType.STICKY : PistonType.DEFAULT);
        level.setBlock(rodPos, movingState, 276);
        net.minecraft.world.level.block.entity.BlockEntity retractBe =
            MovingPistonBlock.newMovingBlockEntity(rodPos, movingState, headState, direction, false, true);''',
  '''        // The head slides back onto the rod. A non-source moving piston still renders the head
        // sliding from headPos onto rodPos — a *source* one would make the vanilla renderer treat
        // the moved head state as a piston base and crash on setValue(EXTENDED).
        BlockState movingState = Blocks.MOVING_PISTON
            .defaultBlockState()
            .setValue(MovingPistonBlock.FACING, direction)
            .setValue(MovingPistonBlock.TYPE, this.sticky ? PistonType.STICKY : PistonType.DEFAULT);
        level.setBlock(rodPos, movingState, 276);
        net.minecraft.world.level.block.entity.BlockEntity retractBe =
            MovingPistonBlock.newMovingBlockEntity(rodPos, movingState, headState, direction, false, false);'''),
])

# --- gravity retract: same fix ---
patch(BASE + 'block/GravityPistonBlock.java', [
 ('''        level.setBlock(rodPos, movingState, 276);
        net.minecraft.world.level.block.entity.BlockEntity retractBe =
            MovingPistonBlock.newMovingBlockEntity(rodPos, movingState, headState, direction, false, true);''',
  '''        level.setBlock(rodPos, movingState, 276);
        // non-source: see RecursivePistonBlock — a source retract piston crashes the renderer
        net.minecraft.world.level.block.entity.BlockEntity retractBe =
            MovingPistonBlock.newMovingBlockEntity(rodPos, movingState, headState, direction, false, false);'''),
])

# --- gravity: rods must survive under a gravity piston too ---
patch(BASE + 'block/RecursivePistonRodBlock.java', [
 ('''        return behind.getBlock() instanceof RecursivePistonBlock
            && behind.getValue(RecursivePistonBlock.EXTENDED)
            && behind.getValue(RecursivePistonBlock.FACING) == facing;''',
  '''        if (behind.getBlock() instanceof RecursivePistonBlock) {
            return behind.getValue(RecursivePistonBlock.EXTENDED)
                && behind.getValue(RecursivePistonBlock.FACING) == facing;
        }
        // the gravity piston telescopes down through the same rods
        return behind.getBlock() instanceof GravityPistonBlock
            && behind.getValue(RecursivePistonBlock.EXTENDED)
            && behind.getValue(RecursivePistonBlock.FACING) == facing;'''),
])

# --- gravity: removing the head must never destroy the base (the base deliberately outlives
# its head and waits for a new one; the falling head "removal" is part of normal operation) ---
patch(BASE + 'block/GravityPistonHeadBlock.java', [
 ('''    /** The fallen head must be able to exist anywhere — it never depends on a base. */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }''',
  '''    /** The fallen head must be able to exist anywhere — it never depends on a base. */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    /** 活塞头消失（下落/破坏）不得连带破坏底座 — the base just goes headless. */
    //? if >=1.20.3 {
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
        // no base destruction
    }
    //?} else {
    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // no base destruction; skip the vanilla head behaviour entirely
    }
    //?}'''),
])

print('part 4 done')
