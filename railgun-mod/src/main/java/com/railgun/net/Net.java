package com.railgun.net;

import com.railgun.RailgunMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTO = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RailgunMod.MODID, "main"), () -> PROTO, PROTO::equals, PROTO::equals);

    public static void register() {
        CHANNEL.registerMessage(0, ShotPacket.class, ShotPacket::encode, ShotPacket::decode, ShotPacket::handle);
    }
    private Net() {}
}
