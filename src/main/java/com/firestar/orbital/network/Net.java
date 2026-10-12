package com.firestar.orbital.network;

import com.firestar.orbital.OrbitalMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            OrbitalMod.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(S2CStrike.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CStrike::encode).decoder(S2CStrike::decode).consumerNetworkThread(S2CStrike::handle).add();
        CHANNEL.messageBuilder(S2CBeam.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBeam::encode).decoder(S2CBeam::decode).consumerNetworkThread(S2CBeam::handle).add();
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
