package com.firestar.silver.network;

import com.firestar.silver.common.Move;
import com.firestar.silver.server.Techniques;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** A key was pressed: which move. */
public record C2SMove(int move) {
    public static void encode(C2SMove m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.move);
    }

    public static C2SMove decode(FriendlyByteBuf buf) {
        return new C2SMove(buf.readVarInt());
    }

    public static void handle(C2SMove m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            Techniques.request(player, Move.byId(m.move));
        }
    }
}
