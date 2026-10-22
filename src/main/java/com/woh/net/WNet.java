package com.woh.net;

import com.woh.common.WMove;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Woh's own channel (jjk:woh): casts, debris, his cooldown/state, the domain, Overdrive auras. */
@Mod.EventBusSubscriber(modid = "jjk", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WNet {
    private static final String V = "1";
    public static SimpleChannel CHANNEL;

    private WNet() {}

    @SubscribeEvent
    public static void onSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(WNet::register);
    }

    static synchronized void register() {
        if (CHANNEL != null) return;
        // lenient: a server or client without Woh's channel can still connect (no mod-mismatch disconnect)
        CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("jjk", "woh"), () -> V,
                NetworkRegistry.acceptMissingOr(V), NetworkRegistry.acceptMissingOr(V));
        int id = 0;
        CHANNEL.registerMessage(id++, Cast.class, Cast::encode, Cast::decode, Cast::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Debris.class, Debris::encode, Debris::decode, Debris::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, State.class, State::encode, State::decode, State::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Domain.class, Domain::encode, Domain::decode, Domain::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Aura.class, Aura::encode, Aura::decode, Aura::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, God.class, God::encode, God::decode, God::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Judge.class, Judge::encode, Judge::decode, Judge::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Wall.class, Wall::encode, Wall::decode, Wall::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** To every player on the server, wherever they are. */
    public static void all(Object msg) {
        if (CHANNEL != null) CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }

    /** A bad packet is logged, never fatal. */
    static void safe(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            com.woh.W.LOG.error("[woh] packet failed (ignored)", t);
        }
    }

    public static void near(ServerLevel level, Vec3 at, double range, Object msg) {
        if (CHANNEL == null) return;
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, range, level.dimension())), msg);
    }

    public static void to(ServerPlayer p, Object msg) {
        if (CHANNEL != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    static Vec3 v(FriendlyByteBuf b) {
        return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
    }

    static void v(FriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x);
        b.writeDouble(v.y);
        b.writeDouble(v.z);
    }

    /** A move began. Every client rebuilds the same timeline from these numbers. */
    public record Cast(int move, int caster, long start, Vec3 origin, Vec3 dir, Vec3 point, long seed, int target, int[] targets,
                       Vec3 domainCentre, float domainRadius, float worldEnder, float splitter, float dismantle,
                       boolean overdrive, float destructionScale) {
        static void encode(Cast m, FriendlyByteBuf b) {
            b.writeVarInt(m.move);
            b.writeVarInt(m.caster);
            b.writeLong(m.start);
            v(b, m.origin);
            v(b, m.dir);
            v(b, m.point);
            b.writeLong(m.seed);
            b.writeVarInt(m.target + 1);
            b.writeVarInt(m.targets.length);
            for (int t : m.targets) b.writeVarInt(t + 1);
            v(b, m.domainCentre);
            b.writeFloat(m.domainRadius);
            b.writeFloat(m.worldEnder);
            b.writeFloat(m.splitter);
            b.writeFloat(m.dismantle);
            b.writeBoolean(m.overdrive);
            b.writeFloat(m.destructionScale);
        }

        static Cast decode(FriendlyByteBuf b) {
            int move = b.readVarInt(), caster = b.readVarInt();
            long start = b.readLong();
            Vec3 o = v(b), d = v(b), p = v(b);
            long seed = b.readLong();
            int target = b.readVarInt() - 1;
            int n = Math.min(16, b.readVarInt());
            int[] ts = new int[n];
            for (int i = 0; i < n; i++) ts[i] = b.readVarInt() - 1;
            return new Cast(move, caster, start, o, d, p, seed, target, ts, v(b), b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readBoolean(), b.readFloat());
        }

        static void handle(Cast m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> safe(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.woh.client.WCasts.add(m))));
            ctx.get().setPacketHandled(true);
        }

        public WMove moveEnum() {
            return WMove.byId(move);
        }
    }

    /** Blocks just blown out of the world, to be flung visibly from the centre of the blast. */
    public record Debris(Vec3 centre, List<BlockPos> pos, List<BlockState> states, float power) {
        static void encode(Debris m, FriendlyByteBuf b) {
            v(b, m.centre);
            b.writeFloat(m.power);
            b.writeVarInt(m.pos.size());
            for (int i = 0; i < m.pos.size(); i++) {
                b.writeBlockPos(m.pos.get(i));
                b.writeVarInt(Block.getId(m.states.get(i)));
            }
        }

        static Debris decode(FriendlyByteBuf b) {
            Vec3 c = v(b);
            float power = b.readFloat();
            int n = Math.min(400, b.readVarInt());
            List<BlockPos> ps = new ArrayList<>(n);
            List<BlockState> ss = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ps.add(b.readBlockPos());
                ss.add(Block.stateById(b.readVarInt()));
            }
            return new Debris(c, ps, ss, power);
        }

        static void handle(Debris m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> safe(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.woh.client.WDebris.add(m))));
            ctx.get().setPacketHandled(true);
        }
    }

    /** The player's own cooldowns (ready-at ticks per move) and whether his domain / Overdrive are up. */
    public record State(long[] readyAt, int[] total, long domainUntil, long overdriveUntil) {
        static void encode(State m, FriendlyByteBuf b) {
            b.writeVarInt(m.readyAt.length);
            for (int i = 0; i < m.readyAt.length; i++) {
                b.writeLong(m.readyAt[i]);
                b.writeVarInt(m.total[i]);
            }
            b.writeLong(m.domainUntil);
            b.writeLong(m.overdriveUntil);
        }

        static State decode(FriendlyByteBuf b) {
            int n = Math.min(64, b.readVarInt());
            long[] r = new long[n];
            int[] t = new int[n];
            for (int i = 0; i < n; i++) {
                r[i] = b.readLong();
                t[i] = b.readVarInt();
            }
            return new State(r, t, b.readLong(), b.readLong());
        }

        static void handle(State m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> safe(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.woh.client.WHud.state(m))));
            ctx.get().setPacketHandled(true);
        }
    }

    /** A domain: owner, centre, radius, the tick it ends (0 = gone), and who it has marked. */
    public record Domain(int owner, Vec3 centre, float radius, long until, int[] marked) {
        static void encode(Domain m, FriendlyByteBuf b) {
            b.writeVarInt(m.owner);
            v(b, m.centre);
            b.writeFloat(m.radius);
            b.writeLong(m.until);
            b.writeVarInt(m.marked.length);
            for (int id : m.marked) b.writeVarInt(id);
        }

        static Domain decode(FriendlyByteBuf b) {
            int owner = b.readVarInt();
            Vec3 c = v(b);
            float r = b.readFloat();
            long until = b.readLong();
            int n = Math.min(32, b.readVarInt());
            int[] ids = new int[n];
            for (int i = 0; i < n; i++) ids[i] = b.readVarInt();
            return new Domain(owner, c, r, until, ids);
        }

        static void handle(Domain m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> safe(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.woh.client.WCasts.domain(m))));
            ctx.get().setPacketHandled(true);
        }
    }

    /** 4th Wall Breaker: the whole server's screens are slashed open, then it goes down. Carries who did it. */
    public record Wall(String by, int ticks) {
        static void encode(Wall m, FriendlyByteBuf b) {
            b.writeUtf(m.by, 64);
            b.writeVarInt(m.ticks);
        }

        static Wall decode(FriendlyByteBuf b) {
            return new Wall(b.readUtf(64), b.readVarInt());
        }

        static void handle(Wall m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> safe(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.woh.client.WWall.start(m))));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Heavenly Judgement, sent only to the one who raised /kill against him: plays on their screen for ticks. */
    public record Judge(String by, int ticks) {
        static void encode(Judge m, FriendlyByteBuf b) {
            b.writeUtf(m.by, 64);
            b.writeVarInt(m.ticks);
        }

        static Judge decode(FriendlyByteBuf b) {
            return new Judge(b.readUtf(64), b.readVarInt());
        }

        static void handle(Judge m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> safe(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.woh.client.WJudge.start(m))));
            ctx.get().setPacketHandled(true);
        }
    }

    /** "As God As My Witness" on (until > now) or off for an entity: his eyes burn red, a red halo over him. */
    public record God(int entity, long until) {
        static void encode(God m, FriendlyByteBuf b) {
            b.writeVarInt(m.entity);
            b.writeLong(m.until);
        }

        static God decode(FriendlyByteBuf b) {
            return new God(b.readVarInt(), b.readLong());
        }

        static void handle(God m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> safe(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.woh.client.WGod.set(m))));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Overdrive on (until > now) or off for an entity. */
    public record Aura(int entity, long until) {
        static void encode(Aura m, FriendlyByteBuf b) {
            b.writeVarInt(m.entity);
            b.writeLong(m.until);
        }

        static Aura decode(FriendlyByteBuf b) {
            return new Aura(b.readVarInt(), b.readLong());
        }

        static void handle(Aura m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> safe(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.woh.client.WCasts.aura(m))));
            ctx.get().setPacketHandled(true);
        }
    }
}
