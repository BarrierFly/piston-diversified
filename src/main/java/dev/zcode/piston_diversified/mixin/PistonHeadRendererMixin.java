package dev.zcode.piston_diversified.mixin;

//? if <1.21.9 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
//? } else {
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.PistonHeadRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
//? }
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
//? if >=26.1 {
import net.minecraft.client.multiplayer.ClientLevel;
//? }
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import dev.zcode.piston_diversified.block.ModPistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 收回时把原版活塞头换成 mod 自己的头 (the retract animation drew a vanilla head for every
 * modded piston).
 *
 * <p>Vanilla draws a retracting piston as two pieces: the head sliding back into the base cell, and
 * the base still rendered {@code EXTENDED=true} while its extension shrinks. The base half is built
 * from the moving block entity's own state, so it already picks the modded model; the head half is
 * not — it is hardcoded:
 *
 * <pre>{@code
 * } else if (blockEntity.isSourcePiston() && !blockEntity.isExtending()) {
 *     BlockState head = Blocks.PISTON_HEAD.defaultBlockState()          // <-- hardcoded
 *         .setValue(PistonHeadBlock.TYPE, moved.is(Blocks.STICKY_PISTON) ? STICKY : DEFAULT)
 *         .setValue(PistonHeadBlock.FACING, moved.get(PistonBaseBlock.FACING))
 *         .setValue(PistonHeadBlock.SHORT, progress >= 0.5F);
 *     state.block = createMovingBlock(headPos, head, biome, level);
 *     state.base  = createMovingBlock(basePos, moved.setValue(EXTENDED, true), biome, level);
 * }
 * }</pre>
 *
 * <p>So every retract of all 38 variants flashed a vanilla piston head. Extending was never wrong:
 * that path takes the {@code else} branch and renders the moved state as-is, which for an extending
 * piston is the modded head itself.
 *
 * <p>The moving piston's block state only carries {@code facing} and {@code type}, so the variant
 * cannot be smuggled in there — but the block entity does carry the modded base, and
 * {@link ModPistonBaseBlock#headBlock()} turns it back into the right head. That is all this mixin
 * does: swap the state handed to the renderer's own {@code createMovingBlock}/{@code renderBlock},
 * leaving every bit of vanilla's placement, offset and interpolation alone.
 *
 * <p>Heads with extra state of their own (镐's tool, 拐推's bend, 骷髅's power) come out of their
 * default state here, the same way vanilla builds its own head state: the retracted animation has
 * no source for those values, and the default variant is what vanilla would pick for an unknown head.
 */
@Mixin(PistonHeadRenderer.class)
public abstract class PistonHeadRendererMixin {
    @Unique
    private PistonMovingBlockEntity pistonDiversified$be;
    @Unique
    private float pistonDiversified$tick;

    /**
     * The modded replacement for a hardcoded vanilla head state, or {@code head} itself when this
     * render is not a modded piston's retract (vanilla pistons, pushed cells, the pulled block).
     */
    @Unique
    private BlockState pistonDiversified$moddedHead(BlockState head) {
        PistonMovingBlockEntity be = this.pistonDiversified$be;
        if (be == null || !head.is(Blocks.PISTON_HEAD)) {
            return head;
        }
        BlockState moved = be.getMovedState();
        if (!(moved.getBlock() instanceof ModPistonBaseBlock)) {
            return head;
        }
        ModPistonBaseBlock base = (ModPistonBaseBlock) moved.getBlock();
        return base.headBlock().defaultBlockState()
                .setValue(PistonHeadBlock.FACING, moved.getValue(PistonHeadBlock.FACING))
                .setValue(PistonHeadBlock.TYPE, base.pdIsSticky() ? PistonType.STICKY : PistonType.DEFAULT)
                .setValue(PistonHeadBlock.SHORT, be.getProgress(this.pistonDiversified$tick) >= 0.5F);
    }

//? if <1.21.9 {
    @Inject(method = "render(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"))
    private void pistonDiversified$capture(PistonMovingBlockEntity be, float partialTick, PoseStack pose,
                                          MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
        this.pistonDiversified$be = be;
        this.pistonDiversified$tick = partialTick;
    }

    @Redirect(method = "render(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/PistonHeadRenderer;renderBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;ZI)V"))
    private void pistonDiversified$moddedHeadState(BlockPos pos, BlockState state, PoseStack pose,
                                                  MultiBufferSource buffer, Level level, boolean extended,
                                                  int overlay) {
        renderBlock(pos, this.pistonDiversified$moddedHead(state), pose, buffer, level, extended, overlay);
    }

    @Shadow
    private void renderBlock(BlockPos pos, BlockState state, PoseStack pose, MultiBufferSource buffer,
                             Level level, boolean extended, int overlay) {
        throw new AssertionError();
    }
//? } else if >=26.1 {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/PistonHeadRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At("HEAD"))
    private void pistonDiversified$capture(PistonMovingBlockEntity be, PistonHeadRenderState renderState,
                                           float partialTicks, Vec3 cameraPos,
                                           ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo ci) {
        this.pistonDiversified$be = be;
        this.pistonDiversified$tick = partialTicks;
    }

    @Redirect(method = "extractRenderState(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/PistonHeadRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/PistonHeadRenderer;createMovingBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Holder;Lnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/block/MovingBlockRenderState;"))
    private MovingBlockRenderState pistonDiversified$moddedHeadState(BlockPos pos, BlockState state,
                                                                   Holder<Biome> biome, ClientLevel level) {
        return createMovingBlock(pos, this.pistonDiversified$moddedHead(state), biome, level);
    }

    @Shadow
    private static MovingBlockRenderState createMovingBlock(BlockPos pos, BlockState state, Holder<Biome> biome,
                                                            ClientLevel level) {
        throw new AssertionError();
    }
//? } else {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/PistonHeadRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At("HEAD"))
    private void pistonDiversified$capture(PistonMovingBlockEntity be, PistonHeadRenderState renderState,
                                           float partialTicks, Vec3 cameraPos,
                                           ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo ci) {
        this.pistonDiversified$be = be;
        this.pistonDiversified$tick = partialTicks;
    }

    @Redirect(method = "extractRenderState(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/PistonHeadRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/PistonHeadRenderer;createMovingBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/Level;)Lnet/minecraft/client/renderer/block/MovingBlockRenderState;"))
    private MovingBlockRenderState pistonDiversified$moddedHeadState(BlockPos pos, BlockState state,
                                                                   Holder<Biome> biome, Level level) {
        return createMovingBlock(pos, this.pistonDiversified$moddedHead(state), biome, level);
    }

    @Shadow
    private static MovingBlockRenderState createMovingBlock(BlockPos pos, BlockState state, Holder<Biome> biome,
                                                            Level level) {
        throw new AssertionError();
    }
//?}
}