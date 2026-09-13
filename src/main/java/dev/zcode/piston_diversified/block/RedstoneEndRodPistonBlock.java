package dev.zcode.piston_diversified.block;

import net.minecraft.world.level.BlockGetter;
import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
//? if >=1.20.5 {
import net.minecraft.core.particles.ColorParticleOption;
//?}
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * 活塞红石端杆 — end rod arm with a redstone torch flame; light 8 while retracted, and the head
 * outputs a redstone-torch style signal pointing forwards (see {@link RedstoneEndRodPistonHeadBlock}).
 */
public class RedstoneEndRodPistonBlock extends ModPistonBaseBlock {
    public static final int REDSTONE_COLOR = 0xFF7000;

    public RedstoneEndRodPistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.REDSTONE_END_ROD_PISTON_HEAD;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return Shapes.or(PdShapes.slab(facing, 12, 16), PdShapes.rod(facing, 4, 16, 4));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < 0.25F) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            //? if >=1.20.5 {
            level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, REDSTONE_COLOR), x, y, z, 0.0, 0.05, 0.0);
            //?} else {
            level.addParticle((SimpleParticleType) ParticleTypes.ENTITY_EFFECT, x, y, z, 0.0, 0.05, 0.0);
            //?}
        }
    }
}
