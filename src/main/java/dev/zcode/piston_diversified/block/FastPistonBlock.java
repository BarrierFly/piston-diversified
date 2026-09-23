package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
//? if >=1.20.5 {
import net.minecraft.core.particles.ColorParticleOption;
//?}
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 快速活塞 / 快速黏塞 — the moving pistons it creates are flagged "fast" and convert to their
 * final block the same tick the movement completes instead of one tick later. Emits swiftness
 * potion particles (规划 v2 §29/30 外观).
 */
public class FastPistonBlock extends ModPistonBaseBlock {
    public static final int SWIFTNESS_COLOR = 0x7CAFC6;

    public FastPistonBlock(boolean sticky, Properties properties) {
        super(sticky, properties);
    }

    @Override
    public Block headBlock() {
        return this.sticky ? ModBlocks.FAST_STICKY_PISTON_HEAD : ModBlocks.FAST_PISTON_HEAD;
    }

    @Override
    protected boolean marksMovingPistonsFast() {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < 0.4F) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble() * 0.5;
            double z = pos.getZ() + random.nextDouble();
            //? if >=1.20.5 {
            level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, SWIFTNESS_COLOR), x, y, z, 0.0, 0.05, 0.0);
            //?} else {
            level.addParticle((SimpleParticleType) ParticleTypes.ENTITY_EFFECT, x, y, z, 0.0, 0.05, 0.0);
            //?}
        }
    }
}
