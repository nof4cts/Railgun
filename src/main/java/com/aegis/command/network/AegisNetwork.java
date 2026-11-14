package com.aegis.command.network;

import com.aegis.command.AegisCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AegisNetwork {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            AegisCommand.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        // server -> client
        CHANNEL.messageBuilder(Packets.OpenLaptop.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.OpenLaptop::encode).decoder(Packets.OpenLaptop::decode)
                .consumerMainThread(Packets.OpenLaptop::handle).add();
        CHANNEL.messageBuilder(Packets.SyncData.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.SyncData::encode).decoder(Packets.SyncData::decode)
                .consumerMainThread(Packets.SyncData::handle).add();
        CHANNEL.messageBuilder(Packets.StrikeResponse.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.StrikeResponse::encode).decoder(Packets.StrikeResponse::decode)
                .consumerMainThread(Packets.StrikeResponse::handle).add();
        CHANNEL.messageBuilder(Packets.StrikeLaunched.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.StrikeLaunched::encode).decoder(Packets.StrikeLaunched::decode)
                .consumerMainThread(Packets.StrikeLaunched::handle).add();
        CHANNEL.messageBuilder(Packets.ImpactFx.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.ImpactFx::encode).decoder(Packets.ImpactFx::decode)
                .consumerMainThread(Packets.ImpactFx::handle).add();
        CHANNEL.messageBuilder(Packets.MissionComplete.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.MissionComplete::encode).decoder(Packets.MissionComplete::decode)
                .consumerMainThread(Packets.MissionComplete::handle).add();
        CHANNEL.messageBuilder(Packets.Tracer.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.Tracer::encode).decoder(Packets.Tracer::decode)
                .consumerMainThread(Packets.Tracer::handle).add();
        // client -> server
        CHANNEL.messageBuilder(Packets.LaunchStrike.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.LaunchStrike::encode).decoder(Packets.LaunchStrike::decode)
                .consumerMainThread(Packets.LaunchStrike::handle).add();
        CHANNEL.messageBuilder(Packets.ArsenalAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.ArsenalAction::encode).decoder(Packets.ArsenalAction::decode)
                .consumerMainThread(Packets.ArsenalAction::handle).add();
        CHANNEL.messageBuilder(Packets.Reload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.Reload::encode).decoder(Packets.Reload::decode)
                .consumerMainThread(Packets.Reload::handle).add();
        CHANNEL.messageBuilder(Packets.ClearHistory.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.ClearHistory::encode).decoder(Packets.ClearHistory::decode)
                .consumerMainThread(Packets.ClearHistory::handle).add();
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    private AegisNetwork() {}
}
