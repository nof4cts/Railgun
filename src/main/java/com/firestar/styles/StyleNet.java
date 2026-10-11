package com.firestar.styles;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class StyleNet {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(StylesMod.id("main"), () -> "1", "1"::equals, "1"::equals);

    private StyleNet() {}

    static void register() {
        CHANNEL.registerMessage(0, Choose.class, (m, b) -> b.writeVarInt(m.id), b -> new Choose(b.readVarInt()), Choose::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Sync.class, (m, b) -> {
            b.writeVarInt(m.entity);
            b.writeVarInt(m.id);
        }, b -> new Sync(b.readVarInt(), b.readVarInt()), Sync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public record Choose(int id) {
        static void handle(Choose m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null) {
                    Styles.choose(p, Style.byId(m.id));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record Sync(int entity, int id) {
        static void handle(Sync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.firestar.styles.client.ClientStyles.set(m.entity, Style.byId(m.id))));
            ctx.get().setPacketHandled(true);
        }
    }
}
