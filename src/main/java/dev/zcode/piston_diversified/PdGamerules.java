package dev.zcode.piston_diversified;

import net.minecraft.server.level.ServerLevel;

/**
 * Mod gamerules. Currently only {@code potato_push_limit} (马铃薯活塞 pushing budget, default 32,
 * range 1..1024).
 *
 * <p>1.21.11+ exposes a public {@code GameRule} constructor and the {@code GAME_RULE} registry, so
 * the rule is registered directly under the mod's own namespace ({@code piston_diversified:potato_push_limit};
 * the command accepts the full id — the bare {@code potato_push_limit} form would clash with the
 * vanilla namespace). 1.19.4/1.21.10 keep {@code GameRules.register} private, and reaching it by
 * name via reflection works only in a dev (named-mappings) environment — in a production jar the
 * runtime names are intermediary and the lookup always failed, leaving the rule unregistered and
 * silently falling back to the constant. Those versions go through fabric-game-rule-api-v1 instead
 * ({@code GameRuleRegistry}/{@code GameRuleFactory}), which routes through a mapping-agnostic
 * accessor. Failure falls back to the hardcoded default instead of crashing the server.
 */
public final class PdGamerules {
    public static final String POTATO_PUSH_LIMIT_NAME = "potato_push_limit";
    public static final int POTATO_PUSH_LIMIT_DEFAULT = 32;
    public static final int POTATO_PUSH_LIMIT_MIN = 1;
    public static final int POTATO_PUSH_LIMIT_MAX = 1024;

    private static Object potatoPushLimitKey;
    private static boolean potatoPushLimitRegistered;

    private PdGamerules() {
    }

    /**
     * Idempotent registration, called from {@code BootstrapMixin} at the very start of the
     * vanilla bootstrap — the game-rule registry is frozen before mods are initialized, so a
     * mod-init-time registration would always come too late.
     */
    public static void register() {
        if (!potatoPushLimitRegistered) {
            registerPotatoPushLimit();
        }
    }

    //? if >=1.21.11 {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerPotatoPushLimit() {
        try {
            net.minecraft.world.level.gamerules.GameRule<Integer> rule = new net.minecraft.world.level.gamerules.GameRule<>(
                net.minecraft.world.level.gamerules.GameRuleCategory.MISC,
                net.minecraft.world.level.gamerules.GameRuleType.INT,
                com.mojang.brigadier.arguments.IntegerArgumentType.integer(POTATO_PUSH_LIMIT_MIN, POTATO_PUSH_LIMIT_MAX),
                (visitor, gameRule) -> visitor.visitInteger((net.minecraft.world.level.gamerules.GameRule<Integer>) gameRule),
                com.mojang.serialization.Codec.intRange(POTATO_PUSH_LIMIT_MIN, POTATO_PUSH_LIMIT_MAX),
                value -> value,
                POTATO_PUSH_LIMIT_DEFAULT,
                net.minecraft.world.flag.FeatureFlagSet.of()
            );
            potatoPushLimitKey = net.minecraft.core.Registry.register(
                net.minecraft.core.registries.BuiltInRegistries.GAME_RULE, PdHelpers.id(POTATO_PUSH_LIMIT_NAME), rule
            );
            potatoPushLimitRegistered = true;
        } catch (RuntimeException exception) {
            PistonDiversified.LOGGER.warn("could not register gamerule {}: {}", POTATO_PUSH_LIMIT_NAME, exception.toString());
        }
    }

    /** The current potato push limit (gamerule value, or the default when registration failed). */
    public static int potatoPushLimit(ServerLevel level) {
        if (potatoPushLimitRegistered && potatoPushLimitKey instanceof net.minecraft.world.level.gamerules.GameRule<?> rule) {
            return level.getGameRules().get((net.minecraft.world.level.gamerules.GameRule<Integer>) rule);
        }
        return POTATO_PUSH_LIMIT_DEFAULT;
    }
    //?} else {
    private static void registerPotatoPushLimit() {
        try {
            potatoPushLimitKey = net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry.register(
                POTATO_PUSH_LIMIT_NAME,
                net.minecraft.world.level.GameRules.Category.MISC,
                net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory.createIntRule(
                    POTATO_PUSH_LIMIT_DEFAULT, POTATO_PUSH_LIMIT_MIN, POTATO_PUSH_LIMIT_MAX
                )
            );
            potatoPushLimitRegistered = true;
        } catch (RuntimeException exception) {
            PistonDiversified.LOGGER.warn("could not register gamerule {}: {}", POTATO_PUSH_LIMIT_NAME, exception.toString());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int potatoPushLimit(ServerLevel level) {
        if (potatoPushLimitRegistered && potatoPushLimitKey instanceof net.minecraft.world.level.GameRules.Key key) {
            return level.getGameRules().getInt((net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.IntegerValue>) key);
        }
        return POTATO_PUSH_LIMIT_DEFAULT;
    }
    //?}
}
