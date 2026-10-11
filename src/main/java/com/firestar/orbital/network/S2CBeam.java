package com.firestar.orbital.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Where a Helios lance is standing this tick (it follows its caller's crosshair, so it cannot be predicted). */
public record S2CBeam(int id, Vec3 at) {
    public static void encode(S2CBeam m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.id);
        buf.writeFloat((float) m.at.x);
        buf.writeFloat((float) m.at.y);
        buf.writeFloat((float) m.at.z);
    }

    public static S2CBeam decode(FriendlyByteBuf buf) {
        return new S2CBeam(buf.readVarInt(), new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()));
    }

    public static void handle(S2CBeam m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.orbital.client.Strikes.beam(m)));
        ctx.get().setPacketHandled(true);
    }
}
