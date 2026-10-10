package com.hakari.idg.network;

import com.hakari.idg.HakariMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class HakariNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            HakariMod.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private static final double FX_RANGE_SQ = 96 * 96;

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(FxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FxPacket::encode).decoder(FxPacket::decode).consumerMainThread(FxPacket::handle).add();
        CHANNEL.messageBuilder(CutscenePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CutscenePacket::encode).decoder(CutscenePacket::decode).consumerMainThread(CutscenePacket::handle).add();
        CHANNEL.messageBuilder(StatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StatePacket::encode).decoder(StatePacket::decode).consumerMainThread(StatePacket::handle).add();
    }

    public static void fx(ServerLevel level, FxType type, int src, int tgt, Vec3 pos, float a, float b) {
        FxPacket packet = new FxPacket(type.ordinal(), src, tgt, pos.x, pos.y, pos.z, a, b);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < FX_RANGE_SQ) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }

    public static void fx(ServerPlayer sp, FxType type, Entity src, Entity tgt, Vec3 pos, float a, float b) {
        fx(sp.serverLevel(), type, src == null ? -1 : src.getId(), tgt == null ? -1 : tgt.getId(), pos, a, b);
    }

    public static void cutscene(ServerPlayer to, CutType type, int anchor, int... args) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> to), new CutscenePacket(type.ordinal(), anchor, args));
    }

    public static void state(ServerPlayer about, StatePacket packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> about), packet);
    }

    private HakariNetwork() {}
}
