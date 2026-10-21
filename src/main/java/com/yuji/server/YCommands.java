package com.yuji.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.yuji.Y;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** /yuji restore [casts] puts back what his last moves destroyed; /yuji reload re-reads config/jjk-yuji.properties. */
@Mod.EventBusSubscriber(modid = "jjk")
public final class YCommands {
    private YCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("yuji")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("restore")
                        .executes(c -> restore(c.getSource(), 1))
                        .then(Commands.argument("casts", IntegerArgumentType.integer(1, 12))
                                .executes(c -> restore(c.getSource(), IntegerArgumentType.getInteger(c, "casts")))))
                .then(Commands.literal("reload").executes(c -> {
                    Y.load();
                    c.getSource().sendSuccess(() -> Component.literal("Modulo Yuji settings reloaded (destruction: " + Y.destruction
                            + (Y.unrestricted ? ", UNRESTRICTED" : "") + ")"), true);
                    return 1;
                })));
    }

    private static int restore(CommandSourceStack s, int casts) {
        int n = Destroy.restore(s.getLevel(), casts);
        s.sendSuccess(() -> Component.literal("Restored " + n + " blocks"), true);
        return n;
    }
}
