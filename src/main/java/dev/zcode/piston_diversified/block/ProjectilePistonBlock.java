package dev.zcode.piston_diversified.block;

import dev.zcode.piston_diversified.entity.ProjectileBlockEntity;
import dev.zcode.piston_diversified.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.Vec3;

/**
 * 抛射活塞 — extends normally while the front looks pushable; otherwise fires the front block as
 * a {@link ProjectileBlockEntity} (af2022 dispenser-style launch) and extends into the cleared cell.
 *
 * <p>Launch decision (规划 v2 §五.2): normal push iff the front cell is air / flowing liquid, or
 * holds a block entity, or the second cell has a collision shape. Otherwise the front block is
 * launched, destroy-on-push blocks included.</p>
 */
public class ProjectilePistonBlock extends ModPistonBaseBlock {
    public ProjectilePistonBlock(Properties properties) {
        super(false, properties);
    }

    @Override
    public Block headBlock() {
        return ModBlocks.PROJECTILE_PISTON_HEAD;
    }

    @Override
    protected boolean handleExtend(Level level, BlockPos pos, Direction direction, BlockState state) {
        BlockPos frontPos = pos.relative(direction);
        BlockState frontState = level.getBlockState(frontPos);
        if (this.shouldPushNormally(level, frontPos, frontState, pos.relative(direction, 2))) {
            return false;
        }

        if (level instanceof ServerLevel serverLevel) {
            // Launch: the front block flies as a falling block with af2022 dispenser motion.
            Vec3 motion = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ()).add(0.0, 0.1, 0.0);
            ProjectileBlockEntity.launch(serverLevel, frontPos, frontState, motion);
            // Dispenser-style launch report at the muzzle (the front cell the block left).
            serverLevel.playSound(null, frontPos.getX() + 0.5, frontPos.getY() + 0.5, frontPos.getZ() + 0.5,
                SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 0.8F, 1.0F);
        } else {
            // Client mirrors the decision so it does not animate a vanilla push.
            level.setBlock(frontPos, frontState.getFluidState().createLegacyBlock(), 3);
        }
        return false;
    }

    private boolean shouldPushNormally(Level level, BlockPos frontPos, BlockState frontState, BlockPos secondPos) {
        if (frontState.isAir()) {
            return true;
        }
        if (!frontState.getFluidState().isEmpty() && !frontState.getFluidState().isSource()) {
            return true;
        }
        if (frontState.hasBlockEntity()) {
            return true;
        }
        return !level.getBlockState(secondPos).getCollisionShape(level, secondPos).isEmpty();
    }
}
