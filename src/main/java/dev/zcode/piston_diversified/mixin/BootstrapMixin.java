package dev.zcode.piston_diversified.mixin;

import dev.zcode.piston_diversified.PdGamerules;
import net.minecraft.server.Bootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers the mod's gamerule during the vanilla bootstrap. Mod initializers run after the
 * built-in registries have been frozen, so {@code potato_push_limit} has to be added here —
 * right after vanilla has filled its registries, before the freeze.
 */
@Mixin(Bootstrap.class)
public class BootstrapMixin {
    @Inject(method = "bootStrap", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/registries/BuiltInRegistries;bootStrap()V", shift = At.Shift.AFTER))
    private static void pistonDiversified$registerGamerules(CallbackInfo ci) {
        PdGamerules.register();
    }
}