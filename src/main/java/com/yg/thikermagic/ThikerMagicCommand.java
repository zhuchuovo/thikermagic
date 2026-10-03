package com.yg.thikermagic;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Debug command for the Ars Nouveau hooks.
 *
 * <p>{@code /thikermagic mana} prints the attribute totals the tool is feeding in and the Ars Nouveau
 * values that came out, so a data pack that failed to load is visible in game: an unloaded pack
 * leaves the attribute totals at zero and the "without modifiers" cap identical to the current one.
 */
public final class ThikerMagicCommand {
    private ThikerMagicCommand() {}

    /** Lives on the game event bus, registered from {@link ThikerMagic}. */
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("thikermagic").then(mana()));
        event.getDispatcher().register(Commands.literal("tmana").then(mana()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mana() {
        return Commands.literal("mana")
                .requires(source -> source.hasPermission(0))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();

                    ServerPlayer player;
                    try {
                        player = source.getPlayerOrException();
                    } catch (CommandSyntaxException exception) {
                        source.sendFailure(Component.literal("thikermagic:mana 需要一名玩家执行者。"));
                        return 0;
                    }

                    for (Component line : ThikerMagic.manaReadout(player)) {
                        source.sendSuccess(() -> line, false);
                    }
                    return 1;
                });
    }
}
