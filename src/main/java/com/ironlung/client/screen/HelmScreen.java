package com.ironlung.client.screen;

import com.ironlung.block.ControlType;
import com.ironlung.client.ClientMissionState;
import com.ironlung.client.ClientSounds;
import com.ironlung.mission.Mission;
import com.ironlung.network.ModNetwork;
import com.ironlung.network.Packets;
import com.ironlung.ocean.OceanMap;
import com.ironlung.registry.ModSounds;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Navigation: hold W/S for thrust, A/D to turn, R/F to trim ballast. There is no map here, only numbers. */
public class HelmScreen extends SubScreen {
    private float sentThrottle = 99, sentTurn = 99, sentBallast = 99;
    private int resendTimer;

    public HelmScreen() {
        super(Component.translatable("screen.ironlung.helm"));
    }

    private boolean down(int key) {
        return minecraft != null && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key);
    }

    @Override
    public void tick() {
        super.tick();
        boolean powered = ClientMissionState.powered() && ClientMissionState.phase() == Mission.Phase.DIVING;
        float throttle = 0, turn = 0, ballast = 0;
        if (powered) {
            if (down(GLFW.GLFW_KEY_W) || down(GLFW.GLFW_KEY_UP)) throttle += 1;
            if (down(GLFW.GLFW_KEY_S) || down(GLFW.GLFW_KEY_DOWN)) throttle -= 1;
            if (down(GLFW.GLFW_KEY_D) || down(GLFW.GLFW_KEY_RIGHT)) turn += 1;
            if (down(GLFW.GLFW_KEY_A) || down(GLFW.GLFW_KEY_LEFT)) turn -= 1;
            if (down(GLFW.GLFW_KEY_F)) ballast += 1;
            if (down(GLFW.GLFW_KEY_R)) ballast -= 1;
        }
        boolean changed = throttle != sentThrottle || turn != sentTurn || ballast != sentBallast;
        if (changed && sentThrottle != 99 && (throttle != 0 || turn != 0 || ballast != 0)) {
            ClientSounds.playAt(ControlType.HELM, ModSounds.HELM_CLICK.get(), 0.5f, 0.9f + (float) Math.random() * 0.2f);
        }
        if (changed || --resendTimer <= 0) {
            ModNetwork.toServer(new Packets.HelmInput(throttle, turn, ballast));
            sentThrottle = throttle;
            sentTurn = turn;
            sentBallast = ballast;
            resendTimer = 20;
        }
    }

    @Override
    public void removed() {
        ModNetwork.toServer(new Packets.HelmInput(0, 0, 0));
        super.removed();
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int pw = 300, ph = 190;
        int x = (width - pw) / 2, y = (height - ph) / 2;
        panel(g, x, y, pw, ph);
        text(g, "NAVIGATION", x + 12, y + 10, TEXT_DIM);

        Packets.SubState s = ClientMissionState.cur;
        boolean powered = ClientMissionState.powered();
        int sx = x + 12, sy = y + 24, sw = pw - 24, sh = 96;
        bezel(g, sx, sy, sw, sh);
        if (!powered || s == null) {
            if (ClientMissionState.power() == Mission.Power.RESTORING && blink(3)) textCentered(g, "RESTORING", sx + sw / 2, sy + sh / 2 - 4, TEXT_DIM);
            else if (blink(20)) textCentered(g, "NO POWER", sx + sw / 2, sy + sh / 2 - 4, TEXT_DIM);
        } else {
            double hdg = Math.toDegrees(ClientMissionState.heading());
            hdg = ((hdg % 360) + 360) % 360;
            double depth = ClientMissionState.depth();
            if (ClientMissionState.spikeTicks > 0) depth += ClientMissionState.spikeAmount * Math.sin(ticks * 1.7) * ClientMissionState.spikeTicks / 50.0;
            int c = TEXT;
            text(g, String.format(Locale.ROOT, "X  %06.1f", ClientMissionState.x()), sx + 10, sy + 10, TEXT_HOT);
            text(g, String.format(Locale.ROOT, "Y  %06.1f", ClientMissionState.z()), sx + 10, sy + 24, TEXT_HOT);
            text(g, String.format(Locale.ROOT, "HDG  %03.0f", hdg), sx + 10, sy + 42, c);
            text(g, String.format(Locale.ROOT, "SPD  %+.1f m/s", s.speed), sx + 10, sy + 56, c);
            int depthColor = depth > OceanMap.CRUSH_DEPTH ? (blink(4) ? TEXT_HOT : TEXT_DIM) : depth > OceanMap.WARN_DEPTH ? AMBER : c;
            text(g, String.format(Locale.ROOT, "DEPTH  %05.1f m", depth), sx + 10, sy + 74, depthColor);

            // compass strip
            int cx = sx + 190, cy = sy + 18;
            g.fill(cx - 50, cy - 8, cx + 50, cy + 10, 0xFF0A0303);
            for (int d = -60; d <= 60; d += 10) {
                double mark = hdg + d;
                double rounded = Math.round(mark / 10.0) * 10.0;
                int px = (int) (cx + (rounded - hdg) * 0.8);
                if (px < cx - 48 || px > cx + 48) continue;
                int deg = (int) (((rounded % 360) + 360) % 360);
                g.fill(px, cy + 4, px + 1, cy + (deg % 30 == 0 ? 10 : 7), TEXT_DIM);
                if (deg % 90 == 0) {
                    String l = deg == 0 ? "N" : deg == 90 ? "E" : deg == 180 ? "S" : "W";
                    text(g, l, px - 2, cy - 6, TEXT);
                }
            }
            g.fill(cx, cy - 8, cx + 1, cy + 10, TEXT_HOT);

            // proximity lamps: fore, starboard, aft, port
            int lx = sx + 190, ly = sy + 50;
            text(g, "PROXIMITY", lx - 26, ly - 14, TEXT_DIM);
            float[] p = s.prox;
            drawProx(g, lx - 3, ly, p[0]);
            drawProx(g, lx + 18, ly + 18, p[1]);
            drawProx(g, lx - 3, ly + 34, p[2]);
            drawProx(g, lx - 24, ly + 18, p[3]);
            g.fill(lx - 4, ly + 12, lx + 10, ly + 32, 0xFF2A0D0A); // hull outline
        }

        // input legend + throttle bars
        int ly = y + 130;
        String[] legend = {"W/S  THRUST", "A/D  RUDDER", "R/F  BALLAST  (RISE/DIVE)"};
        for (int i = 0; i < legend.length; i++) text(g, legend[i], x + 14, ly + i * 12, TEXT_DIM);
        lamp(g, x + pw - 70, ly, ClientMissionState.hull() > 0.35f || blink(5), ClientMissionState.hull() > 0.6f ? 0xFF6A1A12 : 0xFFE0401F);
        text(g, "HULL", x + pw - 58, ly, TEXT_DIM);
        lamp(g, x + pw - 70, ly + 12, powered, 0xFF6A1A12);
        text(g, "POWER", x + pw - 58, ly + 12, TEXT_DIM);
        boolean deep = s != null && s.depth > OceanMap.WARN_DEPTH;
        lamp(g, x + pw - 70, ly + 24, deep && blink(6), 0xFFE0401F);
        text(g, "PRESS", x + pw - 58, ly + 24, TEXT_DIM);
    }

    private void drawProx(GuiGraphics g, int x, int y, float clearance) {
        int col;
        if (clearance < 4) col = blink(2) ? 0xFFFF3A20 : 0xFF3A0A06;
        else if (clearance < 9) col = 0xFFB0401A;
        else if (clearance < 17) col = 0xFF4A1A0E;
        else col = 0xFF140605;
        g.fill(x - 1, y - 1, x + 9, y + 9, 0xFF050404);
        g.fill(x, y, x + 8, y + 8, col);
    }
}
