package com.firestar.santoryu.network;

import com.firestar.santoryu.SantoryuMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            SantoryuMod.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(C2SCast.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SCast::encode).decoder(C2SCast::decode).consumerMainThread(C2SCast::handle).add();
        CHANNEL.messageBuilder(S2CCast.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCast::encode).decoder(S2CCast::decode).consumerMainThread(S2CCast::handle).add();
        CHANNEL.messageBuilder(S2CCooldowns.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCooldowns::encode).decoder(S2CCooldowns::decode).consumerMainThread(S2CCooldowns::handle).add();
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void toTrackingAndSelf(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), msg);
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
