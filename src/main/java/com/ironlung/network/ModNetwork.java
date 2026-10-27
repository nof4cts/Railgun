package com.ironlung.network;

import com.ironlung.IronLungMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private ModNetwork() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            IronLungMod.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(Packets.MissionInit.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.MissionInit::encode).decoder(Packets.MissionInit::decode)
                .consumerMainThread(Packets.MissionInit::handle).add();
        CHANNEL.messageBuilder(Packets.SubState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.SubState::encode).decoder(Packets.SubState::decode)
                .consumerMainThread(Packets.SubState::handle).add();
        CHANNEL.messageBuilder(Packets.HorrorEvent.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.HorrorEvent::encode).decoder(Packets.HorrorEvent::decode)
                .consumerMainThread(Packets.HorrorEvent::handle).add();
        CHANNEL.messageBuilder(Packets.PhotoResult.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.PhotoResult::encode).decoder(Packets.PhotoResult::decode)
                .consumerMainThread(Packets.PhotoResult::handle).add();
        CHANNEL.messageBuilder(Packets.MissionEnd.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Packets.MissionEnd::encode).decoder(Packets.MissionEnd::decode)
                .consumerMainThread(Packets.MissionEnd::handle).add();
        CHANNEL.messageBuilder(Packets.HelmInput.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.HelmInput::encode).decoder(Packets.HelmInput::decode)
                .consumerMainThread(Packets.HelmInput::handle).add();
        CHANNEL.messageBuilder(Packets.CameraState.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.CameraState::encode).decoder(Packets.CameraState::decode)
                .consumerMainThread(Packets.CameraState::handle).add();
        CHANNEL.messageBuilder(Packets.Surface.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Packets.Surface::encode).decoder(Packets.Surface::decode)
                .consumerMainThread(Packets.Surface::handle).add();
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
