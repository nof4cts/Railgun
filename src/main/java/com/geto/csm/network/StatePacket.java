package com.geto.csm.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The local player's manipulator state, for the HUD and selector. */
public class StatePacket {
    public final int selected, absorbed, slots;
    public final int[] cooldowns;

    public StatePacket(int selected, int absorbed, int slots, int[] cooldowns) {
        this.selected = selected; this.absorbed = absorbed; this.slots = slots; this.cooldowns = cooldowns;
    }

    public static void encode(StatePacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.selected); b.writeVarInt(p.absorbed); b.writeVarInt(p.slots); b.writeVarIntArray(p.cooldowns);
    }

    public static StatePacket decode(FriendlyByteBuf b) {
        return new StatePacket(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarIntArray());
    }

    public static void handle(StatePacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.geto.csm.client.ClientPacketHandler.state(p));
    }
}
