package com.sixpaths.network;

import com.sixpaths.server.PathsLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SelectPacket {
    public final int ability;

    public SelectPacket(int ability) {
        this.ability = ability;
    }

    public static void encode(SelectPacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.ability);
    }

    public static SelectPacket decode(FriendlyByteBuf b) {
        return new SelectPacket(b.readVarInt());
    }

    public static void handle(SelectPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) PathsLogic.select(sp, p.ability);
    }
}
