package com.abomination.horror.network;

import com.abomination.horror.AbominationMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            AbominationMod.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(FinisherPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FinisherPacket::encode).decoder(FinisherPacket::new)
                .consumerMainThread(FinisherPacket::handle).add();
        CHANNEL.messageBuilder(FxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FxPacket::encode).decoder(FxPacket::new)
                .consumerMainThread(FxPacket::handle).add();
        CHANNEL.messageBuilder(JumpscarePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(JumpscarePacket::encode).decoder(JumpscarePacket::new)
                .consumerMainThread(JumpscarePacket::handle).add();
    }

    public static void fx(ServerPlayer player, float shake, float aberration, float flash, int flicker) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FxPacket(shake, aberration, flash, flicker));
    }
}
