package com.firestar.santoryu.network;

import com.firestar.santoryu.common.Move;
import com.firestar.santoryu.server.Techniques;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record C2SCast(int move) {
    public static void encode(C2SCast m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.move);
    }

    public static C2SCast decode(FriendlyByteBuf buf) {
        return new C2SCast(buf.readVarInt());
    }

    public static void handle(C2SCast m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            Techniques.request(player, Move.byId(m.move));
        }
    }
}
