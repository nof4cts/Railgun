package com.teengojo;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Teen Gojo's one packet: an effect for clients to draw (and his own cooldowns, for his HUD). */
@Mod.EventBusSubscriber(modid = "jjk", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TeenNet {
    private static final String V = "1";
    public static SimpleChannel CHANNEL;

    // effect kinds
    public static final int RED_CHARGE = 1, RED_BOLT = 2, RED_BLAST = 3, BLUE = 4, BLUE_END = 5, BLINK = 6, INFINITY = 7, RIPPLE = 8,
            PURPLE_CHARGE = 9, PURPLE_SHOT = 10, PURPLE_END = 11, DOMAIN = 12, COOLDOWN = 13, FROZEN = 14;

    private TeenNet() {}

    @SubscribeEvent
    public static void onSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(TeenNet::register);
    }

    static synchronized void register() {
        if (CHANNEL != null) {
            return;
        }
        CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("jjk", "teen_gojo"), () -> V, V::equals, V::equals);
        CHANNEL.registerMessage(0, Fx.class, Fx::encode, Fx::decode, Fx::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** kind, the entity it belongs to (or -1), a position, a direction, and two numbers whose meaning depends on the kind. */
    public record Fx(int kind, int entity, double x, double y, double z, float dx, float dy, float dz, float a, float b) {
        public static Fx of(int kind, int entity, Vec3 at, Vec3 dir, float a, float b) {
            return new Fx(kind, entity, at.x, at.y, at.z, (float) dir.x, (float) dir.y, (float) dir.z, a, b);
        }

        static void encode(Fx m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.kind);
            buf.writeVarInt(m.entity + 1);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
            buf.writeFloat(m.dx);
            buf.writeFloat(m.dy);
            buf.writeFloat(m.dz);
            buf.writeFloat(m.a);
            buf.writeFloat(m.b);
        }

        static Fx decode(FriendlyByteBuf buf) {
            return new Fx(buf.readVarInt(), buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        static void handle(Fx m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.teengojo.client.TeenFx.receive(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    static void around(ServerLevel level, Vec3 at, Fx msg) {
        if (CHANNEL == null) {
            return;
        }
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 128, level.dimension())), msg);
    }

    static void to(ServerPlayer p, Fx msg) {
        if (CHANNEL == null) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }
}
