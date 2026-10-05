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

# --- potato landing hook: vanilla's own tick removes the entity before we run, so
# `!be.isRemoved()` was always false and the flight event never fired; waterlogging
# restoration must apply to every potato-pushed cell, not just the primary one ---
patch('src/main/java/dev/zcode/piston_diversified/mixin/PistonMovingBlockEntityMixin.java', [
 ('''        // 马铃薯活塞: the landing finished this tick (the vanilla tick wrote the final block and
        // dropped the entity) — restore waterlogging and queue the next flight step.
        if (duck.pistonDiversified$getFlight().length > 0
            && be.isExtending()
            && duck.pistonDiversified$isFlightPrimary()
            && !be.isRemoved()
            && !level.getBlockState(pos).is(Blocks.MOVING_PISTON)) {
            pistonDiversified$landPotato(level, pos, be, duck);
        }''',
  '''        // 马铃薯活塞: the landing finished this tick (the vanilla tick wrote the final block and
        // dropped the entity — the entity is already removed by the time the TAIL runs, so the
        // check is on the block state) — restore waterlogging on every pushed cell and queue the
        // next flight step on the leading one.
        if (duck.pistonDiversified$getFlight().length > 0
            && be.isExtending()
            && !level.isClientSide()
            && !level.getBlockState(pos).is(Blocks.MOVING_PISTON)) {
            pistonDiversified$landPotato(level, pos, be, duck);
        }'''),
 ('''    /** 到位有水恢复含水 + 在身后登记延迟 1gt 的无活塞推出事件（悬浮飞行）。 */
    @Unique
    private static void pistonDiversified$landPotato(Level level, BlockPos pos, PistonMovingBlockEntity be, PistonDuck duck) {
        BlockState landed = level.getBlockState(pos);
        boolean wantsWater = duck.pistonDiversified$landsInWater();
        if (!level.isClientSide()
            && landed.hasProperty(BlockStateProperties.WATERLOGGED)
            && landed.getValue(BlockStateProperties.WATERLOGGED) != wantsWater) {
            landed = landed.setValue(BlockStateProperties.WATERLOGGED, wantsWater);
            level.setBlock(pos, landed, 3);
        }
        if (!level.isClientSide() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            dev.zcode.piston_diversified.logic.PotatoFlightQueue.add(
                serverLevel, pos, be.getDirection(), duck.pistonDiversified$getFlight(), landed.getBlock()
            );
        }
    }''',
  '''    /**
     * 到位有水恢复含水（每个被推的格子）+ 首格在身后登记延迟 1gt 的无活塞推出事件（悬浮飞行）。
     * 到位无水且不可无水的方块走原版落地逻辑（破坏掉落）。
     */
    @Unique
    private static void pistonDiversified$landPotato(Level level, BlockPos pos, PistonMovingBlockEntity be, PistonDuck duck) {
        BlockState landed = level.getBlockState(pos);
        boolean wantsWater = duck.pistonDiversified$landsInWater();
        if (landed.hasProperty(BlockStateProperties.WATERLOGGED)
            && landed.getValue(BlockStateProperties.WATERLOGGED) != wantsWater) {
            level.setBlock(pos, landed.setValue(BlockStateProperties.WATERLOGGED, wantsWater), 3);
        }
        if (duck.pistonDiversified$isFlightPrimary()
            && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            dev.zcode.piston_diversified.logic.PotatoFlightQueue.add(
                serverLevel, pos, be.getDirection(), duck.pistonDiversified$getFlight(), landed.getBlock()
            );
        }
    }'''),
])

print('part 3 done')
