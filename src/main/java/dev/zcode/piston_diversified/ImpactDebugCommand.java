package dev.zcode.piston_diversified;

import com.mojang.brigadier.arguments.BoolArgumentType;
import dev.zcode.piston_diversified.entity.ProjectileBlockEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /pistondiversified impactdebug [value]} — runtime switch for the 抛射冲击/刮动 debug trace
 * (see {@link ProjectileBlockEntity#DEBUG_IMPACT_AND_SCRAPE}). Without an argument it reports the
 * current value; pass {@code true}/{@code false} to change it.
 */
public final class ImpactDebugCommand {
    private static final String PREFIX = "piston_diversified impact/scrape debug: ";

    private ImpactDebugCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("pistondiversified")
                .requires(ImpactDebugCommand::isGamemaster)
                .then(Commands.literal("impactdebug")
                    .executes(context -> report(context.getSource()))
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> set(context.getSource(), BoolArgumentType.getBool(context, "value")))))));
    }

    private static int report(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal(PREFIX + ProjectileBlockEntity.DEBUG_IMPACT_AND_SCRAPE));
        return 1;
    }

    /** Op level 2 / gamemaster; 1.21.11+ replaced the numeric check with a permission set. */
    private static boolean isGamemaster(CommandSourceStack source) {
        //? if >=1.21.11 {
        return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        //?} else {
        return source.hasPermission(2);
        //?}
    }

    private static int set(CommandSourceStack source, boolean value) {
        ProjectileBlockEntity.DEBUG_IMPACT_AND_SCRAPE = value;
        source.sendSystemMessage(Component.literal(PREFIX + value));
        return 1;
    }
}
