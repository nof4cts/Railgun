package com.ironlung.client.overlay;

import com.ironlung.client.ClientMissionState;
import com.ironlung.client.StaticNoise;
import com.ironlung.config.IronLungConfig;
import com.ironlung.mission.Mission;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Locale;

/** Darkness when the power dies, blood creeping in when the hull is failing, snow when things go wrong. */
public final class SubOverlay {
    private SubOverlay() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        if (!ClientMissionState.inSub()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        boolean distortion = IronLungConfig.SCREEN_DISTORTION.get();
        Mission.Power pw = ClientMissionState.power();

        if (distortion) {
            if (pw == Mission.Power.TRIPPED) {
                g.fill(0, 0, w, h, 0xE6000000);
            } else if (pw == Mission.Power.RESTORING) {
                int a = Math.random() < 0.3 ? 0x60 : 0xD0;
                g.fill(0, 0, w, h, a << 24);
            }
            float hull = ClientMissionState.hull();
            if (hull < 0.6f) {
                int a = (int) Math.min(170, (0.6f - hull) / 0.6f * 170);
                int col = (a << 24) | 0x2A0000;
                g.fillGradient(0, 0, w, h / 3, col, 0);
                g.fillGradient(0, h * 2 / 3, w, h, 0, col);
            }
            if (mc.screen == null) {
                float sa = 0;
                if (ClientMissionState.glitchTicks > 0 && pw == Mission.Power.ONLINE) sa += 0.08f;
                if (ClientMissionState.shake > 0.5f) sa += (ClientMissionState.shake - 0.5f) * 0.12f;
                StaticNoise.draw(g, 0, 0, w, h, sa);
            }
        }

        if (IronLungConfig.HUD_READOUT.get() && mc.screen == null && ClientMissionState.cur != null && ClientMissionState.introTicks == 0) {
            String line;
            if (pw == Mission.Power.ONLINE) {
                line = String.format(Locale.ROOT, "HULL %03d%%   DEPTH %05.1fm   PWR NOMINAL", Math.round(ClientMissionState.hull() * 100), ClientMissionState.depth());
            } else if (pw == Mission.Power.RESTORING) {
                line = "PWR RESTORING";
            } else {
                line = "PWR ----";
            }
            g.pose().pushPose();
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, line, (int) (6 / 0.75f), (int) ((h - 12) / 0.75f), pw == Mission.Power.ONLINE ? 0xFF8A2A22 : 0xFF4A1010, false);
            g.pose().popPose();
        }

        if (ClientMissionState.toastTicks > 0 && ClientMissionState.toast != null && mc.screen == null) {
            int a = Math.min(255, ClientMissionState.toastTicks * 8);
            int tw = font.width(ClientMissionState.toast);
            g.drawString(font, ClientMissionState.toast, (w - tw) / 2, h / 2 + 30, (a << 24) | 0xB03020, false);
        }

        int intro = ClientMissionState.introTicks;
        if (intro > 0) {
            int black = intro > 60 ? 255 : (int) (intro / 60.0 * 255);
            g.fill(0, 0, w, h, black << 24);
            if (intro > 40) {
                int ta = (int) Math.min(255, (160 - intro) * 6.0);
                if (intro < 70) ta = (int) ((intro - 40) / 30.0 * 255);
                ta = Math.max(5, Math.min(255, ta));
                int col = (ta << 24) | 0x9A2A22;
                String[] lines = {
                        "SURVEY ORDER  " + String.format(Locale.ROOT, "%04d", Math.floorMod(ClientMissionState.seed, 10000)),
                        "",
                        "Photograph each marked site. Coordinates are on the chart.",
                        "The vessel is not rated below " + (int) com.ironlung.ocean.OceanMap.CRUSH_DEPTH + " metres.",
                        "Do not attempt to open the hatch."
                };
                int y = h / 2 - 30;
                for (String s : lines) {
                    g.drawString(font, s, (w - font.width(s)) / 2, y, col, false);
                    y += 12;
                }
            }
        }
    }
}
