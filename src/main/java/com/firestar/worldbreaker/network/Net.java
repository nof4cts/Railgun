package com.firestar.worldbreaker.network;

import com.firestar.worldbreaker.WorldbreakerMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            WorldbreakerMod.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(C2SCall.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SCall::encode).decoder(C2SCall::decode).consumerMainThread(C2SCall::handle).add();
        CHANNEL.messageBuilder(S2CMarch.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CMarch::encode).decoder(S2CMarch::decode).consumerNetworkThread(S2CMarch::handle).add();
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void toTrackingAndSelf(Entity e, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), msg);
    }

    public static void toLevel(net.minecraft.server.level.ServerLevel level, Object msg) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), msg);
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
