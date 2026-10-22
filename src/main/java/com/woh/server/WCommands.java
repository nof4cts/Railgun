package com.woh.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.woh.W;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** /woh restore [casts] puts back what his last moves destroyed; /woh reload re-reads config/jjk-woh.properties. */
@Mod.EventBusSubscriber(modid = "jjk")
public final class WCommands {
    private WCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("woh")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("restore")
                        .executes(c -> restore(c.getSource(), 1))
                        .then(Commands.argument("casts", IntegerArgumentType.integer(1, 12))
                                .executes(c -> restore(c.getSource(), IntegerArgumentType.getInteger(c, "casts")))))
                .then(Commands.literal("reload").executes(c -> {
                    W.load();
                    c.getSource().sendSuccess(() -> Component.literal("Modulo Woh settings reloaded (destruction: " + W.destruction
                            + (W.unrestricted ? ", UNRESTRICTED" : "") + ")"), true);
                    return 1;
                })));
    }

    private static int restore(CommandSourceStack s, int casts) {
        int n = Destroy.restore(s.getLevel(), casts);
        s.sendSuccess(() -> Component.literal("Restored " + n + " blocks"), true);
        return n;
    }
}
