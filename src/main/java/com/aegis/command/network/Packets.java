package com.aegis.command.network;

import com.aegis.command.client.ClientPacketHandler;
import com.aegis.command.item.AegisWeaponItem;
import com.aegis.command.mission.MissionData;
import com.aegis.command.mission.StrikeManager;
import com.aegis.command.mission.ArsenalService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Every packet the mod sends. Client handling is routed through {@link ClientPacketHandler}. */
public final class Packets {
    private Packets() {}

    private static CompoundTag nbt(FriendlyByteBuf buf) {
        CompoundTag t = buf.readNbt();
        return t == null ? new CompoundTag() : t;
    }

    // ------------------------------------------------------------------ server -> client

    public record OpenLaptop(BlockPos pos, CompoundTag data) {
        public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeNbt(data); }
        public static OpenLaptop decode(FriendlyByteBuf buf) { return new OpenLaptop(buf.readBlockPos(), nbt(buf)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openLaptop(pos, data));
        }
    }

    public record SyncData(CompoundTag data) {
        public void encode(FriendlyByteBuf buf) { buf.writeNbt(data); }
        public static SyncData decode(FriendlyByteBuf buf) { return new SyncData(nbt(buf)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.sync(data));
        }
    }

    public record StrikeResponse(boolean accepted, String message, String codename, int delayTicks) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(accepted); buf.writeUtf(message); buf.writeUtf(codename); buf.writeVarInt(delayTicks);
        }
        public static StrikeResponse decode(FriendlyByteBuf buf) {
            return new StrikeResponse(buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readVarInt());
        }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.strikeResponse(this));
        }
    }

    /** Full flight plan of a launched aircraft, so the client can direct the cinematic deterministically. */
    public record StrikeLaunched(CompoundTag plan) {
        public void encode(FriendlyByteBuf buf) { buf.writeNbt(plan); }
        public static StrikeLaunched decode(FriendlyByteBuf buf) { return new StrikeLaunched(nbt(buf)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.strikeLaunched(plan));
        }
    }

    /** kind: 0 = aerial bomb, 1 = TITAN-X shell, 2 = small plasma impact */
    public record ImpactFx(double x, double y, double z, float power, byte kind) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeFloat(power); buf.writeByte(kind);
        }
        public static ImpactFx decode(FriendlyByteBuf buf) {
            return new ImpactFx(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readByte());
        }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.impact(this));
        }
    }

    /** A beam / tracer visual between two points. kind: 0 = PHANTOM beam, 1 = NOVA-RAIL lance. */
    public record Tracer(double ax, double ay, double az, double bx, double by, double bz, int color, byte kind) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeDouble(ax); buf.writeDouble(ay); buf.writeDouble(az);
            buf.writeDouble(bx); buf.writeDouble(by); buf.writeDouble(bz);
            buf.writeInt(color); buf.writeByte(kind);
        }
        public static Tracer decode(FriendlyByteBuf buf) {
            return new Tracer(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readInt(), buf.readByte());
        }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.tracer(this));
        }
    }

    public record MissionComplete(CompoundTag record, CompoundTag data) {
        public void encode(FriendlyByteBuf buf) { buf.writeNbt(record); buf.writeNbt(data); }
        public static MissionComplete decode(FriendlyByteBuf buf) { return new MissionComplete(nbt(buf), nbt(buf)); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.missionComplete(record, data));
        }
    }

    // ------------------------------------------------------------------ client -> server

    /** heading: -1 = auto (approach over the laptop), 0..3 = from N, E, S, W */
    public record LaunchStrike(BlockPos laptop, int aircraft, int targetX, int targetZ, int heading) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(laptop); buf.writeVarInt(aircraft); buf.writeInt(targetX); buf.writeInt(targetZ); buf.writeInt(heading);
        }
        public static LaunchStrike decode(FriendlyByteBuf buf) {
            return new LaunchStrike(buf.readBlockPos(), buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) StrikeManager.requestStrike(p, this);
        }
    }

    public static final int EQUIP = 0, UNEQUIP = 1, SELECT = 2, SET_FINISH = 3, SET_ENERGY = 4;

    public record ArsenalAction(int action, int weapon, int value) {
        public void encode(FriendlyByteBuf buf) { buf.writeVarInt(action); buf.writeVarInt(weapon); buf.writeVarInt(value); }
        public static ArsenalAction decode(FriendlyByteBuf buf) { return new ArsenalAction(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ArsenalService.handle(p, this);
        }
    }

    public record Reload() {
        public void encode(FriendlyByteBuf buf) {}
        public static Reload decode(FriendlyByteBuf buf) { return new Reload(); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            ItemStack stack = p.getMainHandItem();
            if (stack.getItem() instanceof AegisWeaponItem w) w.startReload(stack, p.level(), p);
        }
    }

    public record ClearHistory() {
        public void encode(FriendlyByteBuf buf) {}
        public static ClearHistory decode(FriendlyByteBuf buf) { return new ClearHistory(); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            MissionData.clearHistory(p);
            AegisNetwork.toPlayer(p, new SyncData(MissionData.buildSync(p)));
        }
    }
}
