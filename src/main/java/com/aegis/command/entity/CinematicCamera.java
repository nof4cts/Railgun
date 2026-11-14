package com.aegis.command.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Client-only camera rig. Never added to a level; Minecraft simply renders from its position. */
public class CinematicCamera extends Entity {
    public CinematicCamera(EntityType<? extends CinematicCamera> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Place the rig for this frame with no interpolation. */
    public void place(double x, double y, double z, float yaw, float pitch) {
        setPos(x, y, z);
        xo = x; yo = y; zo = z;
        xOld = x; yOld = y; zOld = z;
        setYRot(yaw);
        setXRot(pitch);
        yRotO = yaw;
        xRotO = pitch;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
