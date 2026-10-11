package com.naoya.ps;

import com.naoya.ps.server.Projection;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Naoya.id("main"), () -> "1", "1"::equals, "1"::equals);

    private Net() {}

    static void register() {
        CHANNEL.registerMessage(0, Move.class, (m, b) -> b.writeByte(m.slot), b -> new Move(b.readByte()), Move::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Fx.class, Fx::encode, Fx::decode, Fx::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, State.class, State::encode, State::decode, State::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void fx(ServerLevel level, Fx f) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(f.a().x, f.a().y, f.a().z, 160, level.dimension())), f);
    }

    public static void state(ServerPlayer p, State s) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), s);
    }

    /** A move key pressed while Naoya. */
    public record Move(int slot) {
        static void handle(Move m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null) {
                    Projection.useKey(p, m.slot);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Something to show: kind, two points, a strength, who did it, to whom, how long. */
    public record Fx(int kind, Vec3 a, Vec3 b, float f, int caster, int other, int dur) {
        public static final int DASH = 0, PALM = 1, FREEZE = 2, UNFREEZE = 3, BARRAGE = 4, HIT = 5, STEP = 6, BOOM = 7, TRAP = 8, SHATTER = 9,
                ULT = 10, ULT_BLINK = 11, ULT_HIT = 12;

        static void encode(Fx x, FriendlyByteBuf b) {
            b.writeByte(x.kind);
            b.writeDouble(x.a.x);
            b.writeDouble(x.a.y);
            b.writeDouble(x.a.z);
            b.writeDouble(x.b.x);
            b.writeDouble(x.b.y);
            b.writeDouble(x.b.z);
            b.writeFloat(x.f);
            b.writeVarInt(x.caster);
            b.writeVarInt(x.other);
            b.writeVarInt(x.dur);
        }

        static Fx decode(FriendlyByteBuf b) {
            return new Fx(b.readByte(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                    b.readFloat(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }

        static void handle(Fx m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.naoya.ps.client.NFx.accept(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** The caller's HUD: when each move is ready (game time), its full cooldown, and the acceleration stacks. */
    public record State(long[] ready, int[] total, int accel) {
        static void encode(State s, FriendlyByteBuf b) {
            b.writeVarInt(s.ready.length);
            for (int i = 0; i < s.ready.length; i++) {
                b.writeLong(s.ready[i]);
                b.writeVarInt(s.total[i]);
            }
            b.writeVarInt(s.accel);
        }

        static State decode(FriendlyByteBuf b) {
            int n = b.readVarInt();
            long[] r = new long[n];
            int[] t = new int[n];
            for (int i = 0; i < n; i++) {
                r[i] = b.readLong();
                t[i] = b.readVarInt();
            }
            return new State(r, t, b.readVarInt());
        }

        static void handle(State m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.naoya.ps.client.Hud.accept(m)));
            ctx.get().setPacketHandled(true);
        }
    }
}
