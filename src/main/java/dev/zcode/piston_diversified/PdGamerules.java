package dev.zcode.piston_diversified;

import net.minecraft.server.level.ServerLevel;

/**
 * Mod gamerules. Currently only {@code potatoPushLimit} (马铃薯活塞 pushing budget, default 32).
 *
 * <p>1.21.11+ exposes a public {@code GameRule} constructor and the {@code GAME_RULE} registry, so
 * the rule is registered directly. 1.19.4/1.21.10 keep {@code GameRules.register} private — the
 * registration goes through one reflective call during static init (the signature is stable in
 * both versions; failure falls back to the hardcoded default instead of crashing the server).
 */
public final class PdGamerules {
    public static final String POTATO_PUSH_LIMIT_NAME = "potato_push_limit";
    public static final int POTATO_PUSH_LIMIT_DEFAULT = 32;

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
                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1024),
                (visitor, gameRule) -> visitor.visitInteger((net.minecraft.world.level.gamerules.GameRule<Integer>) gameRule),
                com.mojang.serialization.Codec.intRange(1, 1024),
                value -> value,
                POTATO_PUSH_LIMIT_DEFAULT,
                net.minecraft.world.flag.FeatureFlagSet.of()
            );
            potatoPushLimitKey = net.minecraft.core.Registry.register(
                net.minecraft.core.registries.BuiltInRegistries.GAME_RULE, POTATO_PUSH_LIMIT_NAME, rule
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerPotatoPushLimit() {
        try {
            java.lang.reflect.Method create = net.minecraft.world.level.GameRules.IntegerValue.class.getDeclaredMethod("create", int.class);
            create.setAccessible(true); // package-private on 1.19.4, public on 1.21.10 — accessible either way
            Object integerRule = create.invoke(null, POTATO_PUSH_LIMIT_DEFAULT);
            java.lang.reflect.Method register = net.minecraft.world.level.GameRules.class.getDeclaredMethod(
                "register", String.class, net.minecraft.world.level.GameRules.Category.class, net.minecraft.world.level.GameRules.Type.class
            );
            register.setAccessible(true);
            Object key = register.invoke(
                null, POTATO_PUSH_LIMIT_NAME, net.minecraft.world.level.GameRules.Category.MISC, integerRule
            );
            potatoPushLimitKey = key;
            potatoPushLimitRegistered = true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
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
