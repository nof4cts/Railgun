package com.firestar.silver.network;

import com.firestar.silver.SilverMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            SilverMod.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(C2SMove.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SMove::encode).decoder(C2SMove::decode).consumerMainThread(C2SMove::handle).add();
        CHANNEL.messageBuilder(S2CCast.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCast::encode).decoder(S2CCast::decode).consumerMainThread(S2CCast::handle).add();
        CHANNEL.messageBuilder(S2CCooldowns.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCooldowns::encode).decoder(S2CCooldowns::decode).consumerMainThread(S2CCooldowns::handle).add();
        CHANNEL.messageBuilder(S2CState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CState::encode).decoder(S2CState::decode).consumerMainThread(S2CState::handle).add();
        CHANNEL.messageBuilder(S2CFx.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CFx::encode).decoder(S2CFx::decode).consumerMainThread(S2CFx::handle).add();
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void toTrackingAndSelf(Entity e, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), msg);
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
