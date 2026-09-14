package dev.zcode.piston_diversified.entity;

import dev.zcode.piston_diversified.logic.PistonlessPush;
import dev.zcode.piston_diversified.mixin.FallingBlockEntityAccessor;
import dev.zcode.piston_diversified.registry.ModEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 抛射活塞专属下落方块 — a vanilla falling block that, when launched by a 抛射活塞, performs
 * the original impact/scrape mechanics against blocks it crashes into (规划 v2 §五.3):
 *
 * <ul>
 *   <li>qualifying axes: |velocity| &ge; 2/3 blocks/tick, processed fastest first;</li>
 *   <li>axis truncated by the collision = impact (push event, velocity set to 0);</li>
 *   <li>other qualifying axes = scrape (push event along that axis, velocity clamped to 0.5
 *       if it was above);</li>
 *   <li>the first successful push ends the sequence; remaining axes are untouched;</li>
 *   <li>landing runs afterwards in the same tick (vanilla behavior) — unless a downward impact
 *       just displaced the landing support, in which case the block keeps falling and lands on
 *       the new support next tick instead of shattering into an item;</li>
 * </ul>
 *
 * <p>Movement, timeout (600t) and landing behavior are inherited from vanilla.</p>
 */
public class ProjectileBlockEntity extends FallingBlockEntity {
    public static final float QUALIFY_SPEED = 2.0F / 3.0F;
    public static final float SCRAPE_SPEED = 0.5F;

    public ProjectileBlockEntity(EntityType<? extends FallingBlockEntity> type, Level level) {
        super(type, level);
    }

    private ProjectileBlockEntity(Level level, double x, double y, double z, BlockState state) {
        this(ModEntities.PROJECTILE_BLOCK, level);
        pd$setBlockState(state);
        this.blocksBuilding = true;
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.setStartPos(this.blockPosition());
    }

    /** Launches the front block of a 抛射活塞 with the af2022 dispenser-style initial motion. */
    public static ProjectileBlockEntity launch(ServerLevel level, BlockPos pos, BlockState state, Vec3 motion) {
        ProjectileBlockEntity entity = new ProjectileBlockEntity(
            level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, state
        );
        entity.setDeltaMovement(motion);
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3);
        level.addFreshEntity(entity);
        return entity;
    }

    private void pd$setBlockState(BlockState state) {
        ((FallingBlockEntityAccessor) this).pistonDiversified$setBlockState(state);
    }

    private BlockState pd$getBlockState() {
        return ((FallingBlockEntityAccessor) this).pistonDiversified$getBlockState();
    }

    private Level pdLevel() {
        //? if >=1.20.5 {
        return this.level();
        //?} else {
        return this.level;
        //?}
    }

    private static int pdMinY(Level level) {
        //? if >=1.20.5 {
        return level.getMinY();
        //?} else {
        return level.getMinBuildHeight();
        //?}
    }

    private static int pdMaxY(Level level) {
        //? if >=1.20.5 {
        return level.getMaxY();
        //?} else {
        return level.getMaxBuildHeight();
        //?}
    }

    private boolean pdOnGround() {
        //? if >=1.20.5 {
        return this.onGround();
        //?} else {
        return this.onGround;
        //?}
    }

    @Override
    public void tick() {
        if (pd$getBlockState().isAir()) {
            this.discard();
            return;
        }

        Block block = pd$getBlockState().getBlock();
        this.time++;
        //? if >=1.20.5 {
        this.applyGravity();
        //?} else {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
        //?}

        Vec3 preMove = this.getDeltaMovement();
        this.move(MoverType.SELF, preMove);
        //? if >=1.20.5 {
        this.applyEffectsFromBlocks();
        //?}
        //? if >=1.20.5 {
        this.handlePortal();
        //?}

        if (pdLevel() instanceof ServerLevel serverLevel
            //? if >=1.20.5 {
            && (this.isAlive() || this.forceTickAfterTeleportToDuplicate)
            //?} else {
            && (this.isAlive())
            //?}
        ) {
            boolean supportAffected = this.impactAndScrape(serverLevel, preMove);

            BlockPos pos = this.blockPosition();
            boolean concrete = pd$getBlockState().getBlock() instanceof net.minecraft.world.level.block.ConcretePowderBlock;
            boolean inWater = concrete && pdLevel().getFluidState(pos).is(FluidTags.WATER);
            double speedSqr = this.getDeltaMovement().lengthSqr();
            if (concrete && speedSqr > 1.0) {
                BlockHitResult hit = pdLevel().clip(
                    new ClipContext(new Vec3(this.xo, this.yo, this.zo), this.position(), ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, this)
                );
                if (hit.getType() != HitResult.Type.MISS && pdLevel().getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
                    pos = hit.getBlockPos();
                    inWater = true;
                }
            }

            if (!pdOnGround() && !inWater) {
                if (this.time > 100 && (pos.getY() <= pdMinY(pdLevel()) || pos.getY() > pdMaxY(pdLevel())) || this.time > 600) {
                    if (this.dropItem && dev.zcode.piston_diversified.PdHelpers.entityDropsEnabled(serverLevel)) {
                        dev.zcode.piston_diversified.PdHelpers.spawnBlockDrop(this, serverLevel, block);
                    }
                    this.discard();
                }
            } else if (!supportAffected) {
                BlockState landingState = pdLevel().getBlockState(pos);
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
                if (!landingState.is(Blocks.MOVING_PISTON)) {
                    if (!((FallingBlockEntityAccessor) this).pistonDiversified$getCancelDrop()) {
                        boolean replaceable = landingState.canBeReplaced(
                            new DirectionalPlaceContext(pdLevel(), pos, Direction.DOWN, ItemStack.EMPTY, Direction.UP)
                        );
                        boolean freeBelow = FallingBlock.isFree(pdLevel().getBlockState(pos.below())) && (!concrete || !inWater);
                        boolean canSurvive = pd$getBlockState().canSurvive(pdLevel(), pos) && !freeBelow;
                        if (replaceable && canSurvive) {
                            BlockState toPlace = pd$getBlockState();
                            if (toPlace.hasProperty(BlockStateProperties.WATERLOGGED) && pdLevel().getFluidState(pos).getType() == Fluids.WATER) {
                                toPlace = toPlace.setValue(BlockStateProperties.WATERLOGGED, true);
                            }
                            pd$setBlockState(toPlace);
                            if (pdLevel().setBlock(pos, toPlace, 3)) {
                                this.discard();
                                if (block instanceof Fallable fallable) {
                                    fallable.onLand(pdLevel(), pos, toPlace, landingState, this);
                                }
                                pd$mergeBeData(serverLevel, pos);
                            } else if (this.dropItem && dev.zcode.piston_diversified.PdHelpers.entityDropsEnabled(serverLevel)) {
                                this.discard();
                                this.callOnBrokenAfterFall(block, pos);
                                dev.zcode.piston_diversified.PdHelpers.spawnBlockDrop(this, serverLevel, block);
                            }
                        } else {
                            this.discard();
                            if (this.dropItem && dev.zcode.piston_diversified.PdHelpers.entityDropsEnabled(serverLevel)) {
                                this.callOnBrokenAfterFall(block, pos);
                                dev.zcode.piston_diversified.PdHelpers.spawnBlockDrop(this, serverLevel, block);
                            }
                        }
                    } else {
                        this.discard();
                        this.callOnBrokenAfterFall(block, pos);
                    }
                }
            }
        }

        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
    }

    // ------------------------------------------------------------ impact & scrape

    /**
     * @return true if a downward impact removed or shifted the cell this entity would land on —
     *         the landing pass must then be skipped, otherwise the block always shatters into an
     *         item because its support is gone; it keeps falling and lands on the new support.
     */
    private boolean impactAndScrape(ServerLevel level, Vec3 preMove) {
        Vec3 post = this.getDeltaMovement();
        boolean supportAffected = false;

        List<Direction> qualifying = new ArrayList<>();
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (Math.abs(preMove.get(axis)) >= QUALIFY_SPEED) {
                qualifying.add(Direction.fromAxisAndDirection(axis, preMove.get(axis) >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE));
            }
        }
        if (qualifying.isEmpty()) {
            return false;
        }
        qualifying.sort(Comparator.comparingDouble((Direction d) -> -Math.abs(preMove.get(d.getAxis()))));

        for (Direction direction : qualifying) {
            Direction.Axis axis = direction.getAxis();
            boolean truncated = preMove.get(axis) != 0.0 && post.get(axis) == 0.0;
            BlockPos blocker = findBlocker(level, axis, direction.getAxisDirection());
            if (blocker == null) {
                continue;
            }

            // The resolver treats the given position as the "piston base" and resolves from the
            // cell in front of it — pass the flying block's own cell so the push line starts AT
            // the blocker. Passing the blocker itself would only ever push what lies beyond it.
            boolean success = PistonlessPush.execute(level, this.blockPosition(), direction, false, true, true);
            if (success && direction == Direction.DOWN) {
                supportAffected = true;
            }
            if (truncated) {
                this.setVelocityComponent(axis, 0.0);
            } else if (Math.abs(this.getDeltaMovement().get(axis)) > SCRAPE_SPEED) {
                this.setVelocityComponent(axis, Math.signum(preMove.get(axis)) * SCRAPE_SPEED);
            }

            if (success) {
                break;
            }
        }
        return supportAffected;
    }

    /** The cell just beyond the entity's bounding box face along the axis (nearest to the collision face). */
    private BlockPos findBlocker(ServerLevel level, Direction.Axis axis, Direction.AxisDirection sign) {
        AABB box = this.getBoundingBox();
        double along = sign == Direction.AxisDirection.POSITIVE ? box.max(axis) : box.min(axis);
        along += (sign == Direction.AxisDirection.POSITIVE ? 1 : -1) * 0.05;
        Vec3 center = box.getCenter();
        Vec3 probe = switch (axis) {
            case X -> new Vec3(along, center.y, center.z);
            case Y -> new Vec3(center.x, along, center.z);
            case Z -> new Vec3(center.x, center.y, along);
        };
        BlockPos cell = BlockPos.containing(probe);
        if (cell.equals(this.blockPosition())) {
            return null;
        }
        return level.getBlockState(cell).getCollisionShape(level, cell).isEmpty() ? null : cell;
    }

    private void setVelocityComponent(Direction.Axis axis, double value) {
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(switch (axis) {
            case X -> new Vec3(value, v.y, v.z);
            case Y -> new Vec3(v.x, value, v.z);
            case Z -> new Vec3(v.x, v.y, value);
        });
    }

    private void pd$mergeBeData(ServerLevel serverLevel, BlockPos pos) {
        CompoundTag blockData = this.blockData;
        if (blockData != null && pd$getBlockState().hasBlockEntity()) {
            BlockEntity be = pdLevel().getBlockEntity(pos);
            if (be != null) {
                //? if >=1.21.2 {
                try (net.minecraft.util.ProblemReporter.ScopedCollector collector =
                         new net.minecraft.util.ProblemReporter.ScopedCollector(dev.zcode.piston_diversified.multiver.MultiverLogger.LOGGER)) {
                    net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        collector.forChild(be.problemPath()), serverLevel.registryAccess()
                    );
                    be.saveWithoutMetadata(out);
                    CompoundTag merged = out.buildResult();
                    blockData.forEach((key, tag) -> merged.put(key, tag.copy()));
                    be.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                        collector.forChild(be.problemPath()), serverLevel.registryAccess(), merged
                    ));
                }
                //?} else {
                CompoundTag merged = be.saveWithoutMetadata();
                for (String key : blockData.getAllKeys()) {
                    merged.put(key, blockData.get(key).copy());
                }
                be.load(merged);
                //?}
                be.setChanged();
            }
        }
    }
}
