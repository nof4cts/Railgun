package com.firestar.sage.net;

import com.firestar.sage.SageMod;
import com.firestar.sage.common.SMove;
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

/** The Celestial Sage's messages: a rite asked for, a rite begun, a form taken, the cooldowns. */
public final class SageNet {
    private static final String V = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(SageMod.id("main"), () -> V, V::equals, V::equals);

    private SageNet() {}

    public static void register() {
        int i = 0;
        CHANNEL.registerMessage(i++, Ask.class, Ask::encode, Ask::decode, Ask::handle);
        CHANNEL.registerMessage(i++, Rite.class, Rite::encode, Rite::decode, Rite::handle);
        CHANNEL.registerMessage(i++, Form.class, Form::encode, Form::decode, Form::handle);
        CHANNEL.registerMessage(i++, Ready.class, Ready::encode, Ready::decode, Ready::handle);
    }

    public static void around(ServerPlayer p, Object msg) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), msg);
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

    /** Client to server: perform a rite. */
    public record Ask(int move) {
        static void encode(Ask m, FriendlyByteBuf b) {
            b.writeVarInt(m.move);
        }

        static Ask decode(FriendlyByteBuf b) {
            return new Ask(b.readVarInt());
        }

        static void handle(Ask m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p != null) {
                    com.firestar.sage.server.Rites.ask(p, SMove.byId(m.move));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server to clients: a rite has begun. */
    public record Rite(int move, int caster, long start, Vec3 origin, Vec3 dir, Vec3 point, int target, long seed, float size) {
        static void encode(Rite m, FriendlyByteBuf b) {
            b.writeVarInt(m.move);
            b.writeVarInt(m.caster);
            b.writeLong(m.start);
            vec(b, m.origin);
            vec(b, m.dir);
            vec(b, m.point);
            b.writeVarInt(m.target);
            b.writeLong(m.seed);
            b.writeFloat(m.size);
        }

        static Rite decode(FriendlyByteBuf b) {
            return new Rite(b.readVarInt(), b.readVarInt(), b.readLong(), vec(b), vec(b), vec(b), b.readVarInt(), b.readLong(), b.readFloat());
        }

        static void handle(Rite m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.sage.client.SageCasts.begin(m)));
            ctx.get().setPacketHandled(true);
        }

        public SMove rite() {
            return SMove.byId(move);
        }
    }

    /** Server to clients: a sage's form (0 mortal, 1 ascended) and when it began. */
    public record Form(int entity, int form, long since) {
        static void encode(Form m, FriendlyByteBuf b) {
            b.writeVarInt(m.entity);
            b.writeVarInt(m.form);
            b.writeLong(m.since);
        }

        static Form decode(FriendlyByteBuf b) {
            return new Form(b.readVarInt(), b.readVarInt(), b.readLong());
        }

        static void handle(Form m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.sage.client.SageForms.accept(m)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server to the sage: when each rite is ready again (game time), and its full cooldown. */
    public record Ready(long[] at, int[] total) {
        static void encode(Ready m, FriendlyByteBuf b) {
            b.writeVarInt(m.at.length);
            for (int i = 0; i < m.at.length; i++) {
                b.writeLong(m.at[i]);
                b.writeVarInt(m.total[i]);
            }
        }

        static Ready decode(FriendlyByteBuf b) {
            int n = b.readVarInt();
            long[] at = new long[n];
            int[] total = new int[n];
            for (int i = 0; i < n; i++) {
                at[i] = b.readLong();
                total[i] = b.readVarInt();
            }
            return new Ready(at, total);
        }

        static void handle(Ready m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.sage.client.SageHud.ready(m)));
            ctx.get().setPacketHandled(true);
        }
    }
}
