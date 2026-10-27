package com.ironlung.client.screen;

import com.ironlung.client.ClientMissionState;
import com.ironlung.network.ModNetwork;
import com.ironlung.network.Packets;
import com.ironlung.ocean.OceanMap;
import com.ironlung.ocean.Poi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The survey chart pinned to the wall. It shows where the sites are. It does not show where you are.
 */
public class ChartScreen extends SubScreen {
    private static final int PAPER = 0xFF8E8068;
    private static final int PAPER_DARK = 0xFF6E6250;
    private static final int INK = 0xFF2A1E18;
    private static final int INK_FAINT = 0x50302018;
    private static final int RED_INK = 0xFF7A1410;

    private Button surface, abort;
    private boolean confirmAbort;

    public ChartScreen() {
        super(Component.translatable("screen.ironlung.chart"));
    }

    private int mapSize() {
        return (int) Math.min(height - 40, width * 0.56);
    }

    @Override
    protected void init() {
        int ms = mapSize();
        int mx = (width - ms - 170) / 2;
        int colX = mx + ms + 14;
        int by = (height + ms) / 2 - 46;
        surface = addRenderableWidget(Button.builder(Component.translatable("screen.ironlung.surface"), b -> {
            ModNetwork.toServer(new Packets.Surface(false));
            onClose();
        }).bounds(colX, by, 150, 20).build());
        boolean lead = ClientMissionState.leader;
        abort = addRenderableWidget(Button.builder(Component.translatable(lead ? "screen.ironlung.abort" : "screen.ironlung.leave"), b -> {
            if (!confirmAbort) {
                confirmAbort = true;
                b.setMessage(Component.translatable(lead ? "screen.ironlung.abort_confirm" : "screen.ironlung.leave_confirm"));
            } else {
                ModNetwork.toServer(new Packets.Surface(true));
                onClose();
            }
        }).bounds(colX, by + 24, 150, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (surface != null) surface.active = ClientMissionState.allDone() && ClientMissionState.phase() == com.ironlung.mission.Mission.Phase.DIVING;
        if (abort != null) abort.active = ClientMissionState.phase() == com.ironlung.mission.Mission.Phase.DIVING;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float pt) {
        OceanMap map = ClientMissionState.map;
        if (map == null) return;
        int ms = mapSize();
        int mx = (width - ms - 170) / 2;
        int my = (height - ms) / 2;
        // paper with a darker, dog-eared border
        g.fill(mx - 6, my - 6, mx + ms + 6, my + ms + 6, PAPER_DARK);
        g.fill(mx, my, mx + ms, my + ms, PAPER);
        // blotches
        java.util.Random stain = new java.util.Random(ClientMissionState.seed);
        for (int i = 0; i < 14; i++) {
            int sx = mx + stain.nextInt(ms), sy = my + stain.nextInt(ms), r = 4 + stain.nextInt(16);
            g.fill(sx - r, sy - r / 2, sx + r, sy + r / 2, 0x18301008);
        }
        double scale = ms / (double) map.size;
        // unsurveyed margin
        int margin = (int) (90 * scale);
        g.fill(mx, my, mx + ms, my + margin, 0x30200C08);
        g.fill(mx, my + ms - margin, mx + ms, my + ms, 0x30200C08);
        g.fill(mx, my + margin, mx + margin, my + ms - margin, 0x30200C08);
        g.fill(mx + ms - margin, my + margin, mx + ms, my + ms - margin, 0x30200C08);
        // grid
        for (int v = 0; v <= map.size; v += 100) {
            int p = (int) (v * scale);
            g.fill(mx + p, my, mx + p + 1, my + ms, INK_FAINT);
            g.fill(mx, my + p, mx + ms, my + p + 1, INK_FAINT);
            if (v % 200 == 0 && v > 0 && v < map.size) {
                g.pose().pushPose();
                g.pose().scale(0.6f, 0.6f, 1f);
                g.drawString(font, String.valueOf(v), (int) ((mx + p + 2) / 0.6f), (int) ((my + 2) / 0.6f), INK, false);
                g.drawString(font, String.valueOf(v), (int) ((mx + 2) / 0.6f), (int) ((my + p + 2) / 0.6f), INK, false);
                g.pose().popPose();
            }
        }
        // launch point
        int lx = mx + (int) (map.startX * scale), ly = my + (int) (map.startZ * scale);
        g.fill(lx - 2, ly - 2, lx + 3, ly + 3, INK);
        g.drawString(font, "L", lx + 4, ly - 3, INK, false);
        // survey sites
        for (int i = 0; i < map.objectives.size(); i++) {
            Poi p = map.objectives.get(i);
            int px = mx + (int) (p.x * scale), py = my + (int) (p.z * scale);
            boolean done = i < ClientMissionState.done.length && ClientMissionState.done[i];
            int c = done ? INK : RED_INK;
            g.fill(px - 4, py, px + 5, py + 1, c);
            g.fill(px, py - 4, px + 1, py + 5, c);
            if (done) {
                g.fill(px - 5, py - 5, px + 6, py - 4, c);
                g.fill(px - 5, py + 5, px + 6, py + 6, c);
                g.fill(px - 5, py - 5, px - 4, py + 6, c);
                g.fill(px + 5, py - 5, px + 6, py + 6, c);
            }
            g.drawString(font, String.format(Locale.ROOT, "%02d", i + 1), px + 6, py - 9, c, false);
        }
        // compass rose (north is up)
        int rx = mx + ms - 18, ry = my + ms - 22;
        g.fill(rx, ry - 8, rx + 1, ry + 8, INK);
        g.fill(rx - 6, ry, rx + 7, ry + 1, INK);
        g.drawString(font, "N", rx - 2, ry - 18, INK, false);

        // right column: the orders
        int colX = mx + ms + 14;
        int y = my;
        text(g, "SURVEY ORDER " + String.format(Locale.ROOT, "%04d", Math.floorMod(ClientMissionState.seed, 10000)), colX, y, TEXT_HOT);
        y += 14;
        String[] brief = {"Photograph each site.", "Report coordinates as", "read at the helm.", "Rated to " + (int) OceanMap.CRUSH_DEPTH + " m."};
        for (String b : brief) {
            text(g, b, colX, y, TEXT_DIM);
            y += 10;
        }
        if (ClientMissionState.crew.size() > 1) {
            y += 4;
            text(g, "CREW  " + String.join(", ", ClientMissionState.crew), colX, y, TEXT_DIM);
            y += 10;
        }
        y += 8;
        for (int i = 0; i < map.objectives.size(); i++) {
            Poi p = map.objectives.get(i);
            boolean done = i < ClientMissionState.done.length && ClientMissionState.done[i];
            String line = String.format(Locale.ROOT, "%02d  X %04.0f  Y %04.0f", i + 1, p.x, p.z);
            text(g, line, colX, y, done ? TEXT_DIM : TEXT);
            if (done) {
                g.fill(colX, y + 4, colX + font.width(line), y + 5, TEXT_DIM);
                text(g, "LOGGED", colX + font.width(line) + 6, y, TEXT_DIM);
            }
            y += 11;
            if (map.objectives.size() <= 5) {
                text(g, "    " + p.type.displayName.toUpperCase(Locale.ROOT), colX, y, TEXT_DIM);
                y += 13;
            }
        }
    }
}
