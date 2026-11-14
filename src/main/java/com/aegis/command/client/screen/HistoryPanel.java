package com.aegis.command.client.screen;

import com.aegis.command.client.ClientData;
import com.aegis.command.client.Sfx;
import com.aegis.command.client.ui.Ease;
import com.aegis.command.client.ui.Theme;
import com.aegis.command.client.ui.Ui;
import com.aegis.command.mission.AircraftType;
import com.aegis.command.network.AegisNetwork;
import com.aegis.command.network.Packets;
import net.minecraft.nbt.CompoundTag;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class HistoryPanel extends Panel {
    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd  HH:mm");
    private static final float LX0 = 8, LX1 = 250, LY0 = 86, LY1 = 292, ROW = 22;
    private int selected;
    private float scroll, tScroll;
    private long confirmClearMs = -1;

    public HistoryPanel(LaptopScreen s) {
        super(s);
    }

    @Override
    public void show() {
        super.show();
        selected = 0;
        Sfx.scan();
    }

    @Override
    public void render(float mx, float my, float dt) {
        int acc = Theme.accent();
        List<CompoundTag> list = ClientData.history();
        float t = since();
        scroll = Ease.approach(scroll, tScroll, 12f, dt);

        // ---- lifetime stats
        int[] counts = new int[3];
        for (CompoundTag r : list) counts[Math.floorMod(r.getInt("Ac"), 3)]++;
        int top = 0;
        for (int i = 1; i < 3; i++) if (counts[i] > counts[top]) top = i;
        float c = Ease.outCubic(t / 0.9f);
        String[][] tiles = {
                {"MISSIONS FLOWN", String.valueOf(Math.round(ClientData.missions() * c))},
                {"ORDNANCE DELIVERED", String.valueOf(Math.round(ClientData.ordnance() * c))},
                {"HOSTILES NEUTRALIZED", String.valueOf(Math.round(ClientData.kills() * c))},
                {"TOTAL SORTIE TIME", String.format("%.1f s", ClientData.flightTicks() / 20f * c)},
                {"PREFERRED AIRFRAME", list.isEmpty() ? "--" : AircraftType.byIndex(top).displayName}};
        float tw = (512 - 8 - 4 * 4) / 5f;
        for (int i = 0; i < tiles.length; i++) {
            float x0 = 8 + i * (tw + 4);
            float a = Ease.outCubic(t * 3 - i * 0.15f);
            Ui.alpha *= a;
            Ui.panel(x0, 42, x0 + tw, 78, acc, 0.8f);
            Ui.text(tiles[i][0], x0 + 5, 46, Theme.a(Theme.MUTED, 1f), 0.45f);
            Ui.textGlow(tiles[i][1], x0 + 5, 57, i == 2 ? Theme.RED : Theme.WHITE, i == 4 ? 0.8f : 1.5f, 0.3f);
            Ui.alpha /= Math.max(a, 0.001f);
        }

        // ---- mission log list
        Ui.panel(LX0, LY0, LX1, LY1, acc, 0.8f);
        Ui.text("MISSION LOG", LX0 + 5, LY0 + 4, Theme.a(acc, 1f), 0.55f);
        Ui.textR(list.size() + " RECORDS", LX1 - 5, LY0 + 4, Theme.a(Theme.MUTED, 1f), 0.45f);
        float listTop = LY0 + 14;
        if (list.isEmpty()) {
            Ui.textC("NO MISSIONS ON RECORD", (LX0 + LX1) / 2f, 170, Theme.a(Theme.MUTED, 1f), 0.65f);
            Ui.textC("EXECUTE A STRIKE FROM AIRSTRIKE COMMAND", (LX0 + LX1) / 2f, 182, Theme.a(Theme.MUTED, 0.7f), 0.45f);
        } else {
            selected = Math.min(selected, list.size() - 1);
            float maxScroll = Math.max(0, list.size() * ROW - (LY1 - listTop - 4));
            tScroll = Math.max(0, Math.min(maxScroll, tScroll));
            Ui.scissor(LX0 + 1, listTop, LX1 - 1, LY1 - 2);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag r = list.get(i);
                float y0 = listTop + i * ROW - scroll, y1 = y0 + ROW - 2;
                if (y1 < listTop || y0 > LY1) continue;
                float a = Ease.outCubic(t * 4 - i * 0.12f);
                boolean sel = i == selected;
                boolean hov = s.hovered(LX0 + 3, y0, LX1 - 3, y1) && my >= listTop && my < LY1;
                Ui.alpha *= a;
                Ui.rect(LX0 + 3, y0, LX1 - 3, y1, Theme.a(sel ? Theme.scale(acc, 0.2f) : Theme.SURFACE_HI, sel ? 1f : 0.7f));
                if (hov) Ui.rect(LX0 + 3, y0, LX1 - 3, y1, Theme.a(acc, 0.08f));
                if (sel) Ui.rect(LX0 + 3, y0, LX0 + 5, y1, Theme.a(acc, 1f));
                Ui.text(r.getString("Code"), LX0 + 9, y0 + 3, Theme.a(sel ? Theme.WHITE : Theme.mix(acc, Theme.WHITE, 0.4f), 1f), 0.55f);
                Ui.text(DATE.format(new Date(r.getLong("Time"))) + "   " + AircraftType.byIndex(r.getInt("Ac")).callsign,
                        LX0 + 9, y0 + 12, Theme.a(Theme.MUTED, 1f), 0.45f);
                Ui.textR(r.getInt("Kills") + " KIA", LX1 - 8, y0 + 3, Theme.a(r.getInt("Kills") > 0 ? Theme.RED : Theme.MUTED, 1f), 0.5f);
                Ui.textR("#" + r.getInt("Id"), LX1 - 8, y0 + 12, Theme.a(Theme.MUTED, 1f), 0.45f);
                Ui.alpha /= Math.max(a, 0.001f);
                final int idx = i;
                if (y0 >= listTop - 4 && y1 <= LY1 + 4) {
                    s.hit("log" + i, LX0 + 3, Math.max(y0, listTop), LX1 - 3, Math.min(y1, LY1), true, () -> selected = idx);
                }
            }
            Ui.endScissor();
            if (maxScroll > 0) {
                float h = LY1 - listTop - 4;
                float bar = h * h / (h + maxScroll);
                float by = listTop + (h - bar) * (scroll / maxScroll);
                Ui.rect(LX1 - 3, by, LX1 - 1.5f, by + bar, Theme.a(acc, 0.6f));
            }
        }

        // ---- detail
        float x0 = 256, x1 = 512, y0 = LY0;
        Ui.panel(x0, y0, x1, LY1, acc, 0.8f);
        if (list.isEmpty()) {
            Ui.textC("SELECT A MISSION TO REVIEW", (x0 + x1) / 2f, 170, Theme.a(Theme.MUTED, 1f), 0.6f);
            return;
        }
        CompoundTag r = list.get(selected);
        AircraftType ac = AircraftType.byIndex(r.getInt("Ac"));
        Ui.textGlow(r.getString("Code"), x0 + 6, y0 + 6, Theme.WHITE, 1.05f, 0.4f);
        Ui.rect(x0 + 6, y0 + 19, x0 + 70, y0 + 27, Theme.a(acc, 0.18f));
        Ui.text("SUCCESS  #" + r.getInt("Id"), x0 + 9, y0 + 20.5f, Theme.a(acc, 1f), 0.5f);
        String[] head = {"N", "E", "S", "W"};
        int hd = r.getInt("Heading");
        String[][] rows = {
                {"DATE", DATE.format(new Date(r.getLong("Time")))},
                {"AIRFRAME", ac.displayName + "  (" + ac.callsign + ")"},
                {"TARGET GRID", r.getInt("X") + ", " + r.getInt("Y") + ", " + r.getInt("Z")},
                {"APPROACH", hd < 0 ? "AUTO" : "FROM " + head[Math.floorMod(hd, 4)]},
                {"ORDNANCE / IMPACTS", r.getInt("Bombs") + " / " + r.getInt("Hits")},
                {"HOSTILES NEUTRALIZED", String.valueOf(r.getInt("Kills"))},
                {"MISSION TIME", String.format("%.1f s", r.getInt("Dur") / 20f)}};
        for (int i = 0; i < rows.length; i++) {
            float ry = y0 + 33 + i * 9;
            Ui.text(rows[i][0], x0 + 6, ry, Theme.a(Theme.MUTED, 1f), 0.47f);
            Ui.textR(rows[i][1], x0 + 150, ry, Theme.a(Theme.WHITE, 0.9f), 0.47f);
        }

        // strike pattern diagram
        float px0 = x0 + 158, py0 = y0 + 31, px1 = x1 - 6, py1 = y0 + 96;
        Ui.rect(px0, py0, px1, py1, Theme.a(Theme.BG, 0.9f));
        Ui.frame(px0, py0, px1, py1, 0.5f, Theme.a(acc, 0.4f));
        float pcx = (px0 + px1) / 2f, pcy = (py0 + py1) / 2f;
        Ui.line(px0, pcy, px1, pcy, 0.4f, Theme.a(acc, 0.2f));
        Ui.line(pcx, py0, pcx, py1, 0.4f, Theme.a(acc, 0.2f));
        double dx = r.contains("DX") ? r.getDouble("DX") : 0, dz = r.contains("DZ") ? r.getDouble("DZ") : 1;
        float sc = 1.6f;
        Ui.line(pcx - (float) dx * 60, pcy - (float) dz * 60, pcx + (float) dx * 60, pcy + (float) dz * 60, 0.6f, Theme.a(Theme.AMBER, 0.6f));
        long now = System.currentTimeMillis();
        for (int i = 0; i < ac.bombs; i++) {
            double o = (i - (ac.bombs - 1) / 2.0) * ac.spacing;
            double lat = (i - (ac.bombs - 1) / 2.0) * ac.lateral * 28;
            float bx = pcx + (float) (dx * o - dz * lat) * sc, by = pcy + (float) (dz * o + dx * lat) * sc;
            float ph = ((now / 1000f + i * 0.13f) % 2f) / 2f;
            Ui.ring(bx, by, ac.power * sc * (0.6f + ph * 0.6f), 0.6f, Theme.a(Theme.RED, 1 - ph), 16);
            Ui.disc(bx, by, 1.5f, Theme.a(Theme.AMBER, 1f), 8);
        }
        Ui.text("IMPACT PATTERN", px0 + 3, py1 - 7, Theme.a(Theme.MUTED, 1f), 0.42f);

        // hostiles per mission, most recent 12
        float cx0 = x0 + 6, cy0 = y0 + 108, cx1 = x1 - 6, cy1 = y0 + 170;
        Ui.text("HOSTILES PER SORTIE (RECENT)", cx0, cy0 - 7, Theme.a(Theme.MUTED, 1f), 0.45f);
        Ui.rect(cx0, cy1, cx1, cy1 + 0.5f, Theme.a(acc, 0.4f));
        int n = Math.min(12, list.size());
        int max = 1;
        for (int i = 0; i < n; i++) max = Math.max(max, list.get(i).getInt("Kills"));
        float bw = (cx1 - cx0) / 12f;
        for (int i = 0; i < n; i++) {
            CompoundTag rr = list.get(n - 1 - i);
            float h = (cy1 - cy0 - 6) * rr.getInt("Kills") / (float) max * Ease.outCubic(t * 2 - i * 0.05f);
            boolean isSel = (n - 1 - i) == selected;
            Ui.rect(cx0 + i * bw + 1, cy1 - Math.max(1, h), cx0 + (i + 1) * bw - 1, cy1, Theme.a(isSel ? Theme.RED : acc, isSel ? 0.9f : 0.5f));
        }

        // actions
        s.button("retarget", x0 + 6, LY1 - 34, x0 + 125, LY1 - 20, "RE-TARGET GRID", acc, !ClientData.strikeActive(), false, () -> {
            ClientData.targetX = r.getInt("X");
            ClientData.targetZ = r.getInt("Z");
            ClientData.selectedAircraft = ac.ordinal();
            ClientData.feed("TARGET RESTORED FROM " + r.getString("Code"), ClientData.Level.WARN);
            s.switchTab(LaptopScreen.AIRSTRIKE);
        });
        boolean confirming = confirmClearMs > 0 && System.currentTimeMillis() - confirmClearMs < 3000;
        s.button("clear", x0 + 131, LY1 - 34, x1 - 6, LY1 - 20, confirming ? "CONFIRM PURGE?" : "PURGE MISSION LOG", Theme.RED, true, confirming, () -> {
            if (confirming) {
                AegisNetwork.toServer(new Packets.ClearHistory());
                confirmClearMs = -1;
                selected = 0;
                ClientData.feed("MISSION LOG PURGED (LIFETIME STATS RETAINED)", ClientData.Level.WARN);
            } else {
                confirmClearMs = System.currentTimeMillis();
            }
        });
        Ui.textC("LOG KEEPS THE LAST 30 SORTIES  //  STATS ARE LIFETIME", (x0 + x1) / 2f, LY1 - 12, Theme.a(Theme.MUTED, 0.7f), 0.42f);
    }

    @Override
    public boolean scroll(float mx, float my, double delta) {
        if (mx >= LX0 && mx < LX1 && my >= LY0 && my < LY1) {
            tScroll -= (float) delta * ROW;
            return true;
        }
        return false;
    }
}
