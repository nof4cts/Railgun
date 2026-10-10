package com.geto.csm.network;

import com.geto.csm.CsmMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CsmNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CsmMod.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private static final double FX_RANGE_SQ = 128 * 128;

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(FxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FxPacket::encode).decoder(FxPacket::decode).consumerMainThread(FxPacket::handle).add();
        CHANNEL.messageBuilder(CutscenePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CutscenePacket::encode).decoder(CutscenePacket::decode).consumerMainThread(CutscenePacket::handle).add();
        CHANNEL.messageBuilder(StatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StatePacket::encode).decoder(StatePacket::decode).consumerMainThread(StatePacket::handle).add();
        CHANNEL.messageBuilder(QuestionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuestionPacket::encode).decoder(QuestionPacket::decode).consumerMainThread(QuestionPacket::handle).add();
        CHANNEL.messageBuilder(SelectPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectPacket::encode).decoder(SelectPacket::decode).consumerMainThread(SelectPacket::handle).add();
        CHANNEL.messageBuilder(AnswerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AnswerPacket::encode).decoder(AnswerPacket::decode).consumerMainThread(AnswerPacket::handle).add();
    }

    public static void fx(ServerLevel level, FxType type, int src, int tgt, Vec3 pos, float a, float b) {
        FxPacket packet = new FxPacket(type.ordinal(), src, tgt, pos.x, pos.y, pos.z, a, b);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < FX_RANGE_SQ) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    public static void fx(Entity at, FxType type, Entity src, Entity tgt, Vec3 pos, float a, float b) {
        if (at.level() instanceof ServerLevel sl) fx(sl, type, src == null ? -1 : src.getId(), tgt == null ? -1 : tgt.getId(), pos, a, b);
    }

    public static void cutscene(ServerPlayer to, CutType type, int anchor, int... args) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> to), new CutscenePacket(type.ordinal(), anchor, args));
    }

    public static void toPlayer(ServerPlayer to, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> to), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    private CsmNetwork() {}
}
