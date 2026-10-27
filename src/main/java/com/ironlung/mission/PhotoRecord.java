package com.ironlung.mission;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A photograph is stored as the exact camera pose plus flags; the image itself is
 * re-developed on the client from the shared ocean seed, so it never needs to be sent.
 */
public final class PhotoRecord {
    public static final int FLAG_ANOMALY = 1, FLAG_RUINED = 2, FLAG_FINAL = 4;

    public final int id;
    public final double x, y, z, yaw, pitch;
    public final long tick;
    public final int flags;
    public final long frameSeed;
    /** Indices of points of interest that the photo captured. */
    public final int[] hits;

    public PhotoRecord(int id, double x, double y, double z, double yaw, double pitch, long tick, int flags, long frameSeed, int[] hits) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.tick = tick;
        this.flags = flags;
        this.frameSeed = frameSeed;
        this.hits = hits;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(yaw);
        buf.writeDouble(pitch);
        buf.writeLong(tick);
        buf.writeVarInt(flags);
        buf.writeLong(frameSeed);
        buf.writeVarIntArray(hits);
    }

    public static PhotoRecord read(FriendlyByteBuf buf) {
        return new PhotoRecord(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readLong(), buf.readVarInt(), buf.readLong(), buf.readVarIntArray());
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putDouble("x", x);
        t.putDouble("y", y);
        t.putDouble("z", z);
        t.putDouble("yaw", yaw);
        t.putDouble("pitch", pitch);
        t.putLong("tick", tick);
        t.putInt("flags", flags);
        t.putLong("seed", frameSeed);
        t.putIntArray("hits", hits);
        return t;
    }

    public static PhotoRecord load(CompoundTag t) {
        return new PhotoRecord(t.getInt("id"), t.getDouble("x"), t.getDouble("y"), t.getDouble("z"),
                t.getDouble("yaw"), t.getDouble("pitch"), t.getLong("tick"), t.getInt("flags"), t.getLong("seed"), t.getIntArray("hits"));
    }
}
