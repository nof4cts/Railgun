package com.sixpaths.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StatePacket {
    public final int selected, pretaTicks, ascendTicks;
    public final int[] cooldowns;

    public StatePacket(int selected, int pretaTicks, int ascendTicks, int[] cooldowns) {
        this.selected = selected; this.pretaTicks = pretaTicks; this.ascendTicks = ascendTicks; this.cooldowns = cooldowns;
    }

    public static void encode(StatePacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.selected); b.writeVarInt(p.pretaTicks); b.writeVarInt(p.ascendTicks); b.writeVarIntArray(p.cooldowns);
    }

    public static StatePacket decode(FriendlyByteBuf b) {
        return new StatePacket(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarIntArray());
    }

    public static void handle(StatePacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.sixpaths.client.ClientPacketHandler.state(p));
    }
}
