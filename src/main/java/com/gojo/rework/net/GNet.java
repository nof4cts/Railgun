package com.gojo.rework.net;

import com.gojo.rework.Gojo2;
import com.gojo.rework.common.GMove;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Gojo's messages to the clients: a technique begun, Infinity raised or lowered, a ripple on Infinity. */
public final class GNet {
    private static final String V = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Gojo2.id("rework"), () -> V, V::equals, V::equals);

    private GNet() {}

    public static void register() {
        int i = 0;
        CHANNEL.registerMessage(i++, Cast.class, Cast::encode, Cast::decode, Cast::handle);
        CHANNEL.registerMessage(i++, Inf.class, Inf::encode, Inf::decode, Inf::handle);
        CHANNEL.registerMessage(i++, Ripple.class, Ripple::encode, Ripple::decode, Ripple::handle);
    }

    /** To everyone within {@code range} of {@code at} in the caster's level. */
    public static void near(ServerPlayer p, Vec3 at, double range, Object msg) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, range, p.level().dimension())), msg);
    }

    public static void to(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    static void vec(FriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x);
        b.writeDouble(v.y);
        b.writeDouble(v.z);
    }

    static Vec3 vec(FriendlyByteBuf b) {
        return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
    }

    /** A technique begun: who, when (game time), from where, which way, at what, and a seed for its look. */
    public record Cast(int move, int caster, long start, Vec3 origin, Vec3 dir, Vec3 point, long seed, boolean film) {
        public GMove gmove() {
            return GMove.byId(move);
        }

        static void encode(Cast m, FriendlyByteBuf b) {
            b.writeVarInt(m.move);
            b.writeVarInt(m.caster);
            b.writeLong(m.start);
            vec(b, m.origin);
            vec(b, m.dir);
            vec(b, m.point);
            b.writeLong(m.seed);
            b.writeBoolean(m.film);
        }

        static Cast decode(FriendlyByteBuf b) {
            return new Cast(b.readVarInt(), b.readVarInt(), b.readLong(), vec(b), vec(b), vec(b), b.readLong(), b.readBoolean());
        }

        static void handle(Cast m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.gojo.rework.client.GCasts.begin(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Infinity on or off for an entity (burst: it was just raised). */
    public record Inf(int entity, boolean on, boolean burst) {
        static void encode(Inf m, FriendlyByteBuf b) {
            b.writeVarInt(m.entity);
            b.writeBoolean(m.on);
            b.writeBoolean(m.burst);
        }

        static Inf decode(FriendlyByteBuf b) {
            return new Inf(b.readVarInt(), b.readBoolean(), b.readBoolean());
        }

        static void handle(Inf m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.gojo.rework.client.GInfinity.state(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Something met Infinity: rings spread over the boundary at {@code at}, facing {@code normal}. */
    public record Ripple(Vec3 at, Vec3 normal) {
        static void encode(Ripple m, FriendlyByteBuf b) {
            vec(b, m.at);
            vec(b, m.normal);
        }

        static Ripple decode(FriendlyByteBuf b) {
            return new Ripple(vec(b), vec(b));
        }

        static void handle(Ripple m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.gojo.rework.client.GInfinity.ripple(m)));
            ctx.get().setPacketHandled(true);
        }
    }
}
