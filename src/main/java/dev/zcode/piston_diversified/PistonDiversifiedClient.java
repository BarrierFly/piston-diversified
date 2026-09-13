package dev.zcode.piston_diversified;

import dev.zcode.piston_diversified.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;

public class PistonDiversifiedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.PROJECTILE_BLOCK, FallingBlockRenderer::new);
    }
}
