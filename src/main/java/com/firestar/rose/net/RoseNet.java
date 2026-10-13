package com.firestar.rose.net;

import com.firestar.rose.RoseMod;
import com.firestar.rose.common.RMove;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Requiem Rose's channel: a key pressed (Act), a technique begun (Play), a reaper's form (Form), cooldowns (Ready). */
public final class RoseNet {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(RoseMod.id("main"), () -> V, V::equals, V::equals);

    private RoseNet() {}

    public static void register() {
        int id = 0;
        CH.messageBuilder(Act.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder(Act::write).decoder(Act::read).consumerMainThread(Act::handle).add();
        CH.messageBuilder(Play.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Play::write).decoder(Play::read).consumerMainThread(Play::handle).add();
        CH.messageBuilder(Form.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Form::write).decoder(Form::read).consumerMainThread(Form::handle).add();
        CH.messageBuilder(Ready.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(Ready::write).decoder(Ready::read).consumerMainThread(Ready::handle).add();
    }

    public static void toServer(Object m) {
        CH.sendToServer(m);
    }

    public static void around(Entity e, Object m) {
        CH.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), m);
    }

    public static void to(ServerPlayer p, Object m) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), m);
    }

    static void vec(FriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x);
        b.writeDouble(v.y);
        b.writeDouble(v.z);
    }

    static Vec3 vec(FriendlyByteBuf b) {
        return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
    }

    /** A technique key. */
    public record Act(int move) {
        static void write(Act m, FriendlyByteBuf b) {
            b.writeVarInt(m.move);
        }

        static Act read(FriendlyByteBuf b) {
            return new Act(b.readVarInt());
        }

        static void handle(Act m, Supplier<NetworkEvent.Context> c) {
            ServerPlayer p = c.get().getSender();
            if (p != null) {
                com.firestar.rose.server.Reaping.request(p, RMove.byId(m.move));
            }
        }
    }

    /**
     * A technique has begun: who, from where (eye), which way, at what (point, target), where the reaper ends up (exit),
     * and when (server game time, so every client plays the same frame at the same moment).
     */
    public record Play(RMove move, int caster, Vec3 eye, Vec3 dir, Vec3 point, Vec3 exit, long start, int seed, int target) {
        static void write(Play m, FriendlyByteBuf b) {
            b.writeVarInt(m.move.ordinal());
            b.writeVarInt(m.caster);
            vec(b, m.eye);
            vec(b, m.dir);
            vec(b, m.point);
            vec(b, m.exit);
            b.writeLong(m.start);
            b.writeInt(m.seed);
            b.writeInt(m.target);
        }

        static Play read(FriendlyByteBuf b) {
            return new Play(RMove.byId(b.readVarInt()), b.readVarInt(), vec(b), vec(b), vec(b), vec(b), b.readLong(), b.readInt(), b.readInt());
        }

        static void handle(Play m, Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.rose.client.RoseCasts.start(m));
        }
    }

    /** A reaper's form: 0 mortal, 1 ascended; and when it changed (for the aura to build). */
    public record Form(int entity, int form, long since) {
        static void write(Form m, FriendlyByteBuf b) {
            b.writeVarInt(m.entity);
            b.writeVarInt(m.form);
            b.writeLong(m.since);
        }

        static Form read(FriendlyByteBuf b) {
            return new Form(b.readVarInt(), b.readVarInt(), b.readLong());
        }

        static void handle(Form m, Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.rose.client.RoseForms.accept(m));
        }
    }

    /** When each technique is ready again, and each full cooldown. */
    public record Ready(long[] at, int[] total) {
        static void write(Ready m, FriendlyByteBuf b) {
            b.writeLongArray(m.at);
            b.writeVarIntArray(m.total);
        }

        static Ready read(FriendlyByteBuf b) {
            return new Ready(b.readLongArray(), b.readVarIntArray());
        }

        static void handle(Ready m, Supplier<NetworkEvent.Context> c) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.rose.client.RoseHud.cooldowns(m.at, m.total));
        }
    }
}
