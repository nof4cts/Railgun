package com.firestar.worldbreaker.network;

import com.firestar.worldbreaker.server.Marches;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** The caller pressed the key: begin a march (the server decides whether they may). */
public record C2SCall() {
    public static void encode(C2SCall m, FriendlyByteBuf buf) {
    }

    public static C2SCall decode(FriendlyByteBuf buf) {
        return new C2SCall();
    }

    public static void handle(C2SCall m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) {
            Marches.call(p);
        }
        ctx.get().setPacketHandled(true);
    }
}
