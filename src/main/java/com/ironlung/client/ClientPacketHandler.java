package com.ironlung.client;

import com.ironlung.client.screen.SummaryScreen;
import com.ironlung.network.Packets;
import net.minecraft.client.Minecraft;

public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void onInit(Packets.MissionInit m) {
        ClientMissionState.init(m);
        if (!m.active) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof com.ironlung.client.screen.SubScreen) mc.setScreen(null);
            ClientSounds.stopAll();
        }
    }

    public static void onState(Packets.SubState s) {
        ClientMissionState.onState(s);
    }

    public static void onHorror(Packets.HorrorEvent e) {
        ClientMissionState.onHorror(e);
    }

    public static void onPhoto(Packets.PhotoResult r) {
        ClientMissionState.onPhoto(r);
    }

    public static void onEnd(Packets.MissionEnd e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isAlive()) {
            ClientMissionState.pendingSummary = e;
            return;
        }
        mc.setScreen(new SummaryScreen(e));
    }
}
