package com.aegis.command.client;

import com.aegis.command.client.cinematic.CinematicDirector;
import com.aegis.command.client.fx.ImpactFx;
import com.aegis.command.client.screen.LaptopScreen;
import com.aegis.command.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Client-side packet reactions. Only ever loaded on the physical client. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void openLaptop(BlockPos pos, CompoundTag data) {
        ClientData.laptop = pos;
        ClientData.apply(data);
        if (CinematicDirector.phase() == CinematicDirector.Phase.CINEMATIC) return;
        ClientData.feed("UPLINK ESTABLISHED // TERMINAL " + pos.getX() + "," + pos.getY() + "," + pos.getZ(), ClientData.Level.OK);
        Minecraft.getInstance().setScreen(new LaptopScreen(pos, false));
    }

    public static void sync(CompoundTag data) {
        ClientData.apply(data);
    }

    public static void strikeResponse(Packets.StrikeResponse r) {
        if (r.accepted()) {
            CinematicDirector.onAccepted(r.codename(), r.delayTicks());
        } else {
            ClientData.status = ClientData.cooldownRemaining() > 0 ? "REARMING" : "STANDBY";
            ClientData.feed("STRIKE DENIED: " + r.message(), ClientData.Level.ALERT);
            Sfx.deny();
        }
    }

    public static void strikeLaunched(CompoundTag plan) {
        CinematicDirector.onLaunched(plan);
    }

    public static void impact(Packets.ImpactFx fx) {
        ImpactFx.spawn(fx.x(), fx.y(), fx.z(), fx.power(), fx.kind());
        if (fx.kind() == 0) CinematicDirector.onImpact(fx.x(), fx.y(), fx.z());
    }

    public static void missionComplete(CompoundTag record, CompoundTag data) {
        ClientData.apply(data);
        CinematicDirector.onComplete(record);
    }
}
