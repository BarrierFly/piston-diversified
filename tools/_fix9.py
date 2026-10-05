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

# 1) createResolver receives the captured base state (the base cell is a moving piston by the
# time the retract move runs, so level.getBlockState(pos) cannot be used to read the bend)
patch(BASE + 'block/ModPistonBaseBlock.java', [
 ('''    /**
     * The structure resolver for the extend pre-check and every move/pull. Return null for the
     * vanilla resolver. 强力 swaps in the push-conversion resolver; 拐推 returns its bent pull
     * resolver for retracts ({@code extending=false}).
     */
    protected PdResolver createResolver(Level level, BlockPos pos, Direction direction, boolean extending) {
        return null;
    }''',
  '''    /**
     * The structure resolver for the extend pre-check and every move/pull. Return null for the
     * vanilla resolver. 强力 swaps in the push-conversion resolver; 拐推 returns its bent pull
     * resolver for retracts ({@code extending=false}). {@code baseState} is the piston's own
     * captured state — during a retract move the block at {@code pos} is already a moving
     * piston, so the state cannot be re-read from the level.
     */
    protected PdResolver createResolver(Level level, BlockPos pos, Direction direction, boolean extending, BlockState baseState) {
        return null;
    }'''),
 ('''    private boolean moveBlocks(Level level, BlockPos pos, Direction facing, boolean extending, BlockState baseState) {
        PdResolver custom = this.createResolver(level, pos, facing, extending);''',
  '''    private boolean moveBlocks(Level level, BlockPos pos, Direction facing, boolean extending, BlockState baseState) {
        PdResolver custom = this.createResolver(level, pos, facing, extending, baseState);'''),
 ('''    protected boolean resolveExtend(Level level, BlockPos pos, Direction direction) {
        PdResolver resolver = this.createResolver(level, pos, direction, true);''',
  '''    protected boolean resolveExtend(Level level, BlockPos pos, Direction direction) {
        PdResolver resolver = this.createResolver(level, pos, direction, true, null);'''),
])

patch(BASE + 'block/StrongPistonBlock.java', [
 ('''    @Override
    protected PdResolver createResolver(Level level, BlockPos pos, Direction direction, boolean extending) {
        return new ModPistonStructureResolver(level, pos, direction, extending, true, false, false, this.tier);
    }''',
  '''    @Override
    protected PdResolver createResolver(Level level, BlockPos pos, Direction direction, boolean extending, BlockState baseState) {
        return new ModPistonStructureResolver(level, pos, direction, extending, true, false, false, this.tier);
    }'''),
])

patch(BASE + 'block/TurnPushPistonBlock.java', [
 ('''    @Override
    protected PdResolver createResolver(Level level, BlockPos pos, Direction direction, boolean extending) {
        if (extending) {
            return null;
        }
        Direction bend = this.bendOf(level, pos, direction);
        if (bend == null || bend.getAxis() == direction.getAxis()) {
            return null;
        }
        return new ModPistonStructureResolver(
            level, pos.relative(direction).relative(bend), direction.getOpposite(), pos, true
        );
    }

    /**
     * The bend of whatever bend-carrying block sits at pos (the base itself; also called for the
     * just-placed base during placement edge cases). Falls back to the facing-derived default.
     */
    private Direction bendOf(Level level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TurnPushPistonBlock && state.hasProperty(BEND)) {
            Direction bend = state.getValue(BEND);
            if (bend.getAxis() != direction.getAxis()) {
                return bend;
            }
        }
        return this.defaultBend(direction);
    }''',
  '''    @Override
    protected PdResolver createResolver(Level level, BlockPos pos, Direction direction, boolean extending, BlockState baseState) {
        if (extending || baseState == null) {
            return null;
        }
        // read the bend from the captured base state — the base cell is a moving piston during
        // the retract move, so the level no longer holds the piston state
        Direction bend = baseState.getBlock() instanceof TurnPushPistonBlock && baseState.hasProperty(BEND)
            ? baseState.getValue(BEND)
            : this.defaultBend(direction);
        if (bend.getAxis() == direction.getAxis()) {
            return null;
        }
        return new ModPistonStructureResolver(
            level, pos.relative(direction).relative(bend), direction.getOpposite(), pos, true
        );
    }'''),
 # the pre-check resolveExtend also needs the piston's own (still present) state
 ('''    @Override
    protected boolean resolveExtend(Level level, BlockPos pos, Direction direction) {
        this.clearRodNub(level, pos, direction);
        return super.resolveExtend(level, pos, direction);
    }''', None) if False else
 ('''    @Override
    protected boolean resolveExtend(Level level, BlockPos pos, Direction direction) {
        Direction bend = this.bendOf(level, pos, direction);
        if (bend == null) {
            return super.resolveExtend(level, pos, direction);
        }
        return new ModPistonStructureResolver(level, pos.relative(direction), bend, pos, true).resolve();
    }''',
  '''    @Override
    protected boolean resolveExtend(Level level, BlockPos pos, Direction direction) {
        // the piston state is still in place during the pre-check; the sideways pre-resolve must
        // answer "can the front structure be pushed along the bend" (not forward)
        Level stateHolder = level;
        BlockState self = stateHolder.getBlockState(pos);
        Direction bend = self.getBlock() instanceof TurnPushPistonBlock && self.hasProperty(BEND)
            ? self.getValue(BEND)
            : this.defaultBend(direction);
        if (bend.getAxis() == direction.getAxis()) {
            return super.resolveExtend(level, pos, direction);
        }
        return new ModPistonStructureResolver(level, pos.relative(direction), bend, pos, true).resolve();
    }'''),
])

print('part 9 done')
