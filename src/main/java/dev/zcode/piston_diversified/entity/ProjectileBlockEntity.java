package dev.zcode.piston_diversified.entity;

import dev.zcode.piston_diversified.PistonDiversified;
import dev.zcode.piston_diversified.logic.ModPistonStructureResolver;
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
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.SoundType;
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

    /** Ticks between two scrape sounds, so a long grind does not machine-gun the step sound. */
    private static final int SCRAPE_SOUND_INTERVAL = 4;

    /**
     * Debug switch for the impact / scrape pass; off by default. Toggle it at runtime with
     * {@code /pistondiversified impactdebug <true|false>} (no argument reports the current value).
     * When on, every processed axis logs the entity's exact base coordinate, the blocker cell, the
     * action and direction, and the cell the pistonless push is issued from.
     */
    public static boolean DEBUG_IMPACT_AND_SCRAPE = false;

    /** Countdown gating the next scrape sound (transient; a relaunch/load restarts it at 0). */
    private int pdScrapeSoundCooldown;

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
        if (this.pdScrapeSoundCooldown > 0) {
            this.pdScrapeSoundCooldown--;
        }
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

        // Impact/scrape only exists at a collision. The collision blocker is the block on the
        // truncated axis — a real contact. The truncation is checked on every axis, not just the
        // qualifying ones: a block skidding over the floor collides on the slow Y axis, and that
        // floor block is the one the horizontal scrape must operate on. Probing a side face of a
        // non-truncated axis instead finds blocks the block merely flew past and pushes them into
        // empty space. Whichever axes qualify, the impact and every scrape then operate on that
        // one blocker (规划 v2 §五.3: "冲击和刮动均对此阻挡方块操作").
        Direction impactDirection = null;
        double impactSpeed = 0.0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            double velocity = preMove.get(axis);
            if (velocity != 0.0 && post.get(axis) == 0.0 && Math.abs(velocity) > impactSpeed) {
                impactSpeed = Math.abs(velocity);
                impactDirection = Direction.fromAxisAndDirection(axis,
                    velocity >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            }
        }
        if (impactDirection == null) {
            return false; // no contact this tick
        }
        BlockPos blocker = findBlocker(level, impactDirection.getAxis(), impactDirection.getAxisDirection());
        if (blocker == null) {
            return false;
        }

        boolean soundPlayed = false;
        List<String> traces = DEBUG_IMPACT_AND_SCRAPE ? new ArrayList<>() : null;

        for (int index = 0; index < qualifying.size(); index++) {
            Direction direction = qualifying.get(index);
            Direction.Axis axis = direction.getAxis();
            boolean truncated = preMove.get(axis) != 0.0 && post.get(axis) == 0.0;

            // The resolver treats the given position as the "piston base" and resolves from the
            // cell in front of it — so issue the push from the cell behind the blocker, which
            // makes the push line start exactly AT the blocker.
            BlockPos pushPos = blocker.relative(direction.getOpposite());
            PistonlessPush.Result result = PistonlessPush.execute(level, pushPos, direction, false, true, true);
            boolean success = result.success();
            if (success && direction == Direction.DOWN) {
                supportAffected = true;
            }
            if (truncated) {
                this.setVelocityComponent(axis, 0.0);
            } else if (Math.abs(this.getDeltaMovement().get(axis)) > SCRAPE_SPEED) {
                this.setVelocityComponent(axis, Math.signum(preMove.get(axis)) * SCRAPE_SPEED);
            }

            BlockState fragileGlass = truncated ? pdFirstFragileDestroy(result.destroyed()) : null;

            if (traces != null) {
                traces.add(pdAxisTrace(direction, preMove, truncated ? "impact" : "scrape",
                    blocker, pushPos, success, fragileGlass != null));
                if (success) {
                    for (int rest = index + 1; rest < qualifying.size(); rest++) {
                        traces.add(pdAxisTrace(qualifying.get(rest), preMove, "not-attempted", null, null, false, false));
                    }
                }
            }

            // At most one sound per tick: a collision that truncated the axis is a hard impact,
            // any other qualifying axis is a scrape and is throttled so a long grind stays quiet.
            // An impact that fragile-destroys glass plays that block's shatter instead.
            if (!soundPlayed) {
                if (truncated) {
                    if (fragileGlass != null) {
                        this.pdPlayGlassBreakSound(level, fragileGlass);
                    } else {
                        this.pdPlayImpactSound(level);
                    }
                    soundPlayed = true;
                } else if (this.pdScrapeSoundCooldown <= 0) {
                    this.pdPlayScrapeSound(level);
                    this.pdScrapeSoundCooldown = SCRAPE_SOUND_INTERVAL;
                    soundPlayed = true;
                }
            }

            if (success) {
                break;
            }
        }

        if (traces != null) {
            pdDebugEmit(preMove, traces);
        }
        return supportAffected;
    }

    /**
     * The launched block slams into a surface. The block's own material break sound is used so a
     * stone block crunches, a wood block cracks, glass shatters, etc. Played regardless of whether
     * the push succeeded — a bonk on obsidian is still a bonk.
     */
    private void pdPlayImpactSound(ServerLevel level) {
        SoundType sound = pd$getBlockState().getSoundType();
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
            sound.getBreakSound(), SoundSource.BLOCKS, 0.8F, sound.getPitch() * 0.75F);
    }

    /** The launched block grinds along a surface on a non-impact axis (throttled by the caller). */
    private void pdPlayScrapeSound(ServerLevel level) {
        SoundType sound = pd$getBlockState().getSoundType();
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
            sound.getStepSound(), SoundSource.BLOCKS, 0.4F, sound.getPitch() * 1.3F);
    }

    /** A fragile (glass-sounding, normally pushable) block that the impact destroyed, if any. */
    private static BlockState pdFirstFragileDestroy(List<BlockState> destroyed) {
        for (BlockState state : destroyed) {
            if (ModPistonStructureResolver.isFragileDestroy(state)) {
                return state;
            }
        }
        return null;
    }

    /** The destroyed fragile block's own shatter, played for the impact that broke it. */
    private void pdPlayGlassBreakSound(ServerLevel level, BlockState glassState) {
        SoundType sound = glassState.getSoundType();
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
            sound.getBreakSound(), SoundSource.BLOCKS, 1.0F, sound.getPitch() * 0.9F);
    }

    /**
     * One debug line for a pass whose motion was obstructed: the entity at its base coordinate
     * ({@link #position()}, i.e. the feet plane — not the bounding-box centre the blocker search
     * uses), its three velocity components, then one line per processed axis in descending speed
     * order carrying the action ({@code impact}/{@code scrape}/{@code none}) and, for an action,
     * the blocker cell, the cell the pistonless push is issued from (one behind the blocker) and
     * whether it moved anything.
     */
    private void pdDebugEmit(Vec3 preMove, List<String> axisTraces) {
        PistonDiversified.LOGGER.info(
            "[piston_diversified] motion obstructed: entity=({}, {}, {}) vel=({}, {}, {})",
            fmt(this.getX()), fmt(this.getY()), fmt(this.getZ()),
            fmt(preMove.x), fmt(preMove.y), fmt(preMove.z)
        );
        for (String trace : axisTraces) {
            PistonDiversified.LOGGER.info("[piston_diversified]   {}", trace);
        }
    }

    private static String pdAxisTrace(Direction direction, Vec3 preMove, String action,
                                      BlockPos blocker, BlockPos pushPos, boolean pushed, boolean glass) {
        StringBuilder line = new StringBuilder();
        line.append(direction.getAxis().getName()).append(' ')
            .append(fmt(Math.abs(preMove.get(direction.getAxis()))))
            .append(" -> ").append(action);
        if (blocker != null) {
            line.append(" dir=").append(direction.getName())
                .append(" blocker=(").append(blocker.getX()).append(", ").append(blocker.getY()).append(", ").append(blocker.getZ()).append(')')
                .append(" push=(").append(pushPos.getX()).append(", ").append(pushPos.getY()).append(", ").append(pushPos.getZ()).append(')')
                .append(" pushed=").append(pushed);
            if (glass) {
                line.append(" glass=true");
            }
        }
        return line.toString();
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
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
