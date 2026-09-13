package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
//? if >=1.20.5 {
import net.minecraft.core.particles.ColorParticleOption;
//?}
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 虚弱活塞 — cannot push anything, but destroys destroy-on-push blocks in front as it extends.
 * Emits weakness potion particles and has a 2×2 pixel rod (see {@link WeakPistonHeadBlock}).
 */
public class WeakPistonBlock extends ModPistonBaseBlock {
    public static final int WEAKNESS_COLOR = 0x5A5A76;

    public WeakPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.WEAK_PISTON_HEAD;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < 0.4F) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble() * 0.5;
            double z = pos.getZ() + random.nextDouble();
            //? if >=1.20.5 {
            level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, WEAKNESS_COLOR), x, y, z, 0.0, 0.05, 0.0);
            //?} else {
            level.addParticle((SimpleParticleType) ParticleTypes.ENTITY_EFFECT, x, y, z, 0.0, 0.05, 0.0);
            //?}
        }
    }
}
