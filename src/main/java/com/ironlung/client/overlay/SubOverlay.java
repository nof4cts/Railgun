package com.ironlung.client.overlay;

import com.ironlung.client.ClientMissionState;
import com.ironlung.client.StaticNoise;
import com.ironlung.config.IronLungConfig;
import com.ironlung.mission.Mission;
import com.ironlung.network.Packets;
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
        if (com.ironlung.client.Cutscene.active) {
            com.ironlung.client.Cutscene.render(g, w, h, partialTick);
            return;
        }
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

        if (ClientMissionState.feedInsetTicks > 0 && !(mc.screen instanceof com.ironlung.client.screen.CameraScreen)
                && ClientMissionState.powered() && ClientMissionState.introTicks == 0) {
            int iw = (int) Math.min(w * 0.55, h * 0.55 * 4 / 3), ih = iw * 3 / 4;
            int ix = (w - iw) / 2, iy = Math.max(8, h / 2 - ih / 2 - 20);
            g.fill(ix - 4, iy - 14, ix + iw + 4, iy + ih + 4, 0xFF0A0606);
            g.fill(ix - 1, iy - 1, ix + iw + 1, iy + ih + 1, 0xFF2A0C0A);
            Packets.SubState s = ClientMissionState.cur;
            float cond = s == null ? 1f : s.cameraCondition;
            double glitch = ClientMissionState.glitchTicks > 0 ? 0.65 : (1 - cond) * 0.2;
            net.minecraft.resources.ResourceLocation feed = com.ironlung.client.FeedSystem.live(glitch, 0.28 + (1 - cond) * 0.6, 1.0);
            int lw = com.ironlung.client.FeedSystem.liveWidth(), lh = com.ironlung.client.FeedSystem.liveHeight();
            if (feed != null && lw > 0) g.blit(feed, ix, iy, iw, ih, 0, 0, lw, lh, lw, lh);
            StaticNoise.draw(g, ix, iy, iw, ih, 0.06f + (ClientMissionState.glitchTicks > 0 ? 0.3f : 0f));
            if ((ClientMissionState.feedInsetTicks / 8) % 2 == 0) g.fill(ix + 2, iy - 10, ix + 7, iy - 5, 0xFFD02818);
            g.drawString(font, "EXTERNAL CAMERA", ix + 10, iy - 11, 0xFFB0352A, false);
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

        if (ClientMissionState.finaleBlack) {
            g.fill(0, 0, w, h, 0xFF000000);
            int js = ClientMissionState.jumpscareTicks;
            int total = ClientMissionState.jumpscareTotal;
            int age = total - js;
            if (js > 0) {
                int fw = com.ironlung.client.FeedSystem.SCARE_W, fh = com.ironlung.client.FeedSystem.SCARE_H;
                if (age < 7) {
                    // a glimpse at the far end of the dark: just the eyes catching the light
                    net.minecraft.resources.ResourceLocation far = com.ironlung.client.FeedSystem.jumpscareFar();
                    if (far != null && age % 3 != 2) {
                        int dw = (int) Math.max(w, h * fw / (double) fh), dh = dw * fh / fw;
                        g.blit(far, (w - dw) / 2, (h - dh) / 2, dw, dh, 0, 0, fw, fh, fw, fh);
                    }
                    StaticNoise.draw(g, 0, 0, w, h, 0.12f);
                } else if (age < 12) {
                    StaticNoise.draw(g, 0, 0, w, h, 0.05f); // nothing. then:
                } else {
                    net.minecraft.resources.ResourceLocation face = com.ironlung.client.FeedSystem.jumpscare();
                    boolean cut = js % 6 == 0 || js % 7 == 3;
                    if (face != null && !cut) {
                        double grow = 1.15 + (age - 12) * 0.018;
                        int dw = (int) (Math.max(w, h * fw / (double) fh) * grow), dh = dw * fh / fw;
                        int ox = (int) ((Math.random() - 0.5) * 30), oy = (int) ((Math.random() - 0.5) * 22);
                        g.blit(face, (w - dw) / 2 + ox, (h - dh) / 2 + oy, dw, dh, 0, 0, fw, fh, fw, fh);
                        g.fill(0, 0, w, h, (js % 4 < 2 ? 0x58 : 0x20) << 24 | 0x900000);
                    }
                    StaticNoise.draw(g, 0, 0, w, h, cut ? 0.6f : 0.22f);
                }
            }
            return;
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
