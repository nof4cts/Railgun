package com.firestar.silver.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * A fighter's awakening: 0 none, 1 Omen, 2 Zenith, when it began (for the aura to build), and the game time Silver
 * Zenith runs out (0 = no limit).
 */
public record S2CState(int entityId, int form, long since, long until) {
    public static void encode(S2CState m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeVarInt(m.form);
        buf.writeLong(m.since);
        buf.writeLong(m.until);
    }

    public static S2CState decode(FriendlyByteBuf buf) {
        return new S2CState(buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readLong());
    }

    public static void handle(S2CState m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.silver.client.ClientState.accept(m));
    }
}
