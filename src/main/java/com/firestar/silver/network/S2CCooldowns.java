package com.firestar.silver.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Game time at which each move is ready again, plus each one's full cooldown, for the HUD. */
public record S2CCooldowns(long[] readyAt, int[] total) {
    public static void encode(S2CCooldowns m, FriendlyByteBuf buf) {
        buf.writeLongArray(m.readyAt);
        buf.writeVarIntArray(m.total);
    }

    public static S2CCooldowns decode(FriendlyByteBuf buf) {
        return new S2CCooldowns(buf.readLongArray(), buf.readVarIntArray());
    }

    public static void handle(S2CCooldowns m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.silver.client.Hud.cooldowns(m.readyAt, m.total));
    }
}
