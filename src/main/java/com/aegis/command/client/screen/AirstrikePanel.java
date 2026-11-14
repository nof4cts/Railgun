package com.aegis.command.client.screen;

import com.aegis.command.AegisConfig;
import com.aegis.command.client.ClientData;
import com.aegis.command.client.Sfx;
import com.aegis.command.client.cinematic.CinematicDirector;
import com.aegis.command.client.ui.Ease;
import com.aegis.command.client.ui.Theme;
import com.aegis.command.client.ui.Ui;
import com.aegis.command.entity.AerialBomb;
import com.aegis.command.entity.StrikeJet;
import com.aegis.command.mission.AircraftType;
import com.aegis.command.network.AegisNetwork;
import com.aegis.command.network.Packets;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AirstrikePanel extends Panel {
    private static final float MX0 = 8, MY0 = 42, MS = 200;
    private static final String[] HEADINGS = {"AUTO", "N", "E", "S", "W"};

    private TacticalMap map;
    private float sweep;
    private long holdStart = -1;
    private long debriefMs = -1;
    private CompoundTag shownDebrief;

    public AirstrikePanel(LaptopScreen s) {
        super(s);
    }

    @Override
    public void show() {
        super.show();
        if (map == null) map = new TacticalMap(s.pos, ClientData.maxRange() + 16);
        Sfx.scan();
    }

    @Override
    public void close() {
        if (map != null) map.close();
        map = null;
    }

    // ------------------------------------------------------------------ geometry helpers

    private float cx() { return s.pos.getX() + 0.5f; }
    private float cz() { return s.pos.getZ() + 0.5f; }
    private float ppb() { return MS / 2f / map.radius; }

    private float toMapX(double wx) { return MX0 + MS / 2f + (float) (wx - cx()) * ppb(); }
    private float toMapY(double wz) { return MY0 + MS / 2f + (float) (wz - cz()) * ppb(); }
    private double toWorldX(float mx) { return cx() + (mx - MX0 - MS / 2f) / ppb(); }
    private double toWorldZ(float my) { return cz() + (my - MY0 - MS / 2f) / ppb(); }

    private boolean inMap(float mx, float my) {
        return mx >= MX0 && mx < MX0 + MS && my >= MY0 && my < MY0 + MS;
    }

    private double[] approachDir() {
        if (ClientData.targetX == null) return new double[]{0, 1};
        return switch (ClientData.heading) {
            case 0 -> new double[]{0, 1};
            case 1 -> new double[]{-1, 0};
            case 2 -> new double[]{0, -1};
            case 3 -> new double[]{1, 0};
            default -> {
                double dx = ClientData.targetX + 0.5 - cx(), dz = ClientData.targetZ + 0.5 - cz();
                double l = Math.sqrt(dx * dx + dz * dz);
                yield l < 4 ? new double[]{0, 1} : new double[]{dx / l, dz / l};
            }
        };
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(float mx, float my, float dt) {
        if (map == null) show();
        if (!map.done()) map.step(12);
        boolean alert = CinematicDirector.phase() == CinematicDirector.Phase.ALERT;
        float speed = alert ? 5f : ClientData.strikeActive() ? 2.4f : 1.3f;
        float before = sweep;
        sweep = (sweep + dt * speed) % (float) (Math.PI * 2);
        if (sweep < before) Sfx.ping();

        drawMap(mx, my);
        drawAircraft();
        drawStatus();
        drawApproach();
        drawExecute(mx, my);
        drawFeed();
        drawDebrief();
    }

    private void drawMap(float mx, float my) {
        int acc = Theme.accent();
        boolean alert = ClientData.strikeActive();
        int frameCol = alert ? Theme.RED : acc;
        Ui.panel(MX0 - 3, MY0 - 3, MX0 + MS + 3, MY0 + MS + 3, frameCol, 1f);

        // terrain texture, revealed as it is scanned
        float prog = map.progress();
        Ui.g.flush();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, Ui.alpha);
        int rows = (int) (map.size * prog);
        if (rows > 0) {
            int h = (int) (MS * prog);
            Ui.g.blit(TacticalMap.TEX, (int) MX0, (int) MY0, (int) MS, h, 0, 0, map.size, rows, map.size, map.size);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (!map.done()) {
            float sy = MY0 + MS * prog;
            Ui.rect(MX0, sy - 1, MX0 + MS, sy + 1, Theme.a(acc, 0.9f));
            Ui.gradV(MX0, sy - 18, MX0 + MS, sy, Theme.a(acc, 0f), Theme.a(acc, 0.25f));
            Ui.textC("SCANNING TERRAIN " + (int) (prog * 100) + "%", MX0 + MS / 2f, MY0 + MS - 12, Theme.a(acc, 1f), 0.55f);
        }

        Ui.scissor(MX0, MY0, MX0 + MS, MY0 + MS);
        float c0x = MX0 + MS / 2f, c0y = MY0 + MS / 2f;
        // grid
        for (float gx = MX0; gx <= MX0 + MS; gx += 20) Ui.rect(gx, MY0, gx + 0.5f, MY0 + MS, Theme.a(acc, 0.08f));
        for (float gy = MY0; gy <= MY0 + MS; gy += 20) Ui.rect(MX0, gy, MX0 + MS, gy + 0.5f, Theme.a(acc, 0.08f));
        // range rings & bearing ticks
        float rr = ClientData.maxRange() * ppb();
        Ui.ring(c0x, c0y, rr, 0.8f, Theme.a(acc, 0.55f), 96);
        Ui.ring(c0x, c0y, rr * 2 / 3f, 0.5f, Theme.a(acc, 0.25f), 72);
        Ui.ring(c0x, c0y, rr / 3f, 0.5f, Theme.a(acc, 0.25f), 48);
        for (int d = 0; d < 360; d += 10) {
            double a = Math.toRadians(d - 90);
            float in = d % 30 == 0 ? rr - 5 : rr - 2.5f;
            Ui.line(c0x + (float) Math.cos(a) * in, c0y + (float) Math.sin(a) * in,
                    c0x + (float) Math.cos(a) * rr, c0y + (float) Math.sin(a) * rr, 0.6f, Theme.a(acc, 0.6f));
        }
        Ui.textC("N", c0x, c0y - rr - 9, Theme.a(acc, 0.9f), 0.55f);
        Ui.textC("S", c0x, c0y + rr + 3, Theme.a(acc, 0.9f), 0.55f);
        Ui.text("E", c0x + rr + 3, c0y - 2, Theme.a(acc, 0.9f), 0.55f);
        Ui.textR("W", c0x - rr - 3, c0y - 2, Theme.a(acc, 0.9f), 0.55f);

        // radar sweep
        for (int i = 0; i < 22; i++) {
            float a0 = sweep - i * 0.04f, a1 = a0 - 0.04f;
            float al = 0.28f * (1 - i / 22f);
            Ui.tri(c0x, c0y, c0x + (float) Math.cos(a0) * MS, c0y + (float) Math.sin(a0) * MS,
                    c0x + (float) Math.cos(a1) * MS, c0y + (float) Math.sin(a1) * MS,
                    Theme.a(acc, al), Theme.a(acc, al * 0.3f), Theme.a(acc, al * 0.3f));
        }
        Ui.line(c0x, c0y, c0x + (float) Math.cos(sweep) * MS, c0y + (float) Math.sin(sweep) * MS, 1f, Theme.a(acc, 0.9f));

        // live contacts
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                float ex = toMapX(e.getX()), ey = toMapY(e.getZ());
                if (!inMap(ex, ey)) continue;
                double ang = Math.atan2(ey - c0y, ex - c0x);
                float diff = (float) ((sweep - ang) % (Math.PI * 2));
                if (diff < 0) diff += (float) (Math.PI * 2);
                float fresh = Math.max(0.3f, 1 - diff / (float) Math.PI);
                if (e instanceof StrikeJet jet) {
                    drawPlane(jet.aircraft(), ex, ey, 7, (float) Math.atan2(jet.dirZ(), jet.dirX()) + (float) Math.PI / 2, Theme.a(Theme.AMBER, 1f));
                    Ui.ring(ex, ey, 9 + 2 * (float) Math.sin(System.currentTimeMillis() / 150.0), 0.6f, Theme.a(Theme.AMBER, 0.6f), 24);
                } else if (e instanceof AerialBomb) {
                    Ui.disc(ex, ey, 1.4f, Theme.a(Theme.WHITE, 1f), 8);
                } else if (e instanceof Player p) {
                    if (p == mc.player) {
                        float yaw = (float) Math.toRadians(p.getYRot());
                        float fx = -(float) Math.sin(yaw), fz = (float) Math.cos(yaw);
                        Ui.tri(ex + fx * 5, ey + fz * 5, ex - fz * 3 - fx * 3, ey + fx * 3 - fz * 3, ex + fz * 3 - fx * 3, ey - fx * 3 - fz * 3, Theme.a(Theme.WHITE, 1f));
                    } else {
                        Ui.disc(ex, ey, 2.2f, Theme.a(Theme.TECH, fresh), 10);
                    }
                } else if (e instanceof Enemy) {
                    Ui.disc(ex, ey, 1.8f, Theme.a(Theme.RED, fresh), 8);
                    if (fresh > 0.8f) Ui.ring(ex, ey, 3.5f, 0.5f, Theme.a(Theme.RED, fresh * 0.6f), 12);
                } else if (e instanceof Animal) {
                    Ui.disc(ex, ey, 1.1f, Theme.a(acc, fresh * 0.7f), 6);
                }
            }
        }
        // terminal position
        Ui.rect(c0x - 2, c0y - 2, c0x + 2, c0y + 2, Theme.a(acc, 1f));
        Ui.frame(c0x - 4, c0y - 4, c0x + 4, c0y + 4, 0.5f, Theme.a(acc, 0.7f));

        // impact marks
        long now = System.currentTimeMillis();
        ClientData.IMPACTS.removeIf(i -> now - i[2] > 9000);
        for (double[] i : ClientData.IMPACTS) {
            float age = (now - (long) i[2]) / 9000f;
            float ix = toMapX(i[0]), iy = toMapY(i[1]);
            Ui.ring(ix, iy, 3 + age * 16, 0.8f, Theme.a(Theme.RED, 1 - age), 24);
            Ui.disc(ix, iy, 2.5f, Theme.a(Theme.AMBER, 1 - age), 10);
        }

        // target + predicted flight path + drop pattern
        if (ClientData.targetX != null) {
            float tx = toMapX(ClientData.targetX + 0.5), ty = toMapY(ClientData.targetZ + 0.5);
            double[] dir = approachDir();
            float L = MS * 1.5f;
            float off = (now / 30f) % 12;
            Ui.dashed(tx - (float) dir[0] * L, ty - (float) dir[1] * L, tx + (float) dir[0] * L, ty + (float) dir[1] * L, 0.8f, 6, -off, Theme.a(Theme.AMBER, 0.7f));
            for (int k = -3; k <= 3; k++) {
                float ax = tx + (float) dir[0] * k * 26, ay = ty + (float) dir[1] * k * 26;
                float bx = ax - (float) dir[0] * 4, by = ay - (float) dir[1] * 4;
                float px = -(float) dir[1] * 2.5f, py = (float) dir[0] * 2.5f;
                if (k != 0) Ui.tri(ax, ay, bx + px, by + py, bx - px, by - py, Theme.a(Theme.AMBER, 0.75f));
            }
            AircraftType ac = AircraftType.byIndex(ClientData.selectedAircraft);
            for (int i = 0; i < ac.bombs; i++) {
                double o = (i - (ac.bombs - 1) / 2.0) * ac.spacing;
                double lat = (i - (ac.bombs - 1) / 2.0) * ac.lateral * 28;
                float bx = tx + (float) ((dir[0] * o - dir[1] * lat) * ppb());
                float by = ty + (float) ((dir[1] * o + dir[0] * lat) * ppb());
                Ui.ring(bx, by, ac.power * 0.9f * ppb() + 1, 0.5f, Theme.a(Theme.RED, 0.55f), 16);
            }
            float pulse = (now % 1400) / 1400f;
            Ui.ring(tx, ty, 4 + pulse * 14, 0.8f, Theme.a(Theme.RED, 1 - pulse), 32);
            float spin = (now % 100000) / 700f;
            float r = 7;
            for (int q = 0; q < 4; q++) {
                double a = spin + q * Math.PI / 2;
                Ui.arc(tx, ty, r, 1.2f, (float) a, (float) a + 0.9f, Theme.a(Theme.RED, 1f), 6);
            }
            Ui.line(tx - 12, ty, tx - 4, ty, 0.8f, Theme.a(Theme.RED, 1f));
            Ui.line(tx + 4, ty, tx + 12, ty, 0.8f, Theme.a(Theme.RED, 1f));
            Ui.line(tx, ty - 12, tx, ty - 4, 0.8f, Theme.a(Theme.RED, 1f));
            Ui.line(tx, ty + 4, tx, ty + 12, 0.8f, Theme.a(Theme.RED, 1f));
            Ui.text("TGT-01", tx + 9, ty - 11, Theme.a(Theme.RED, 1f), 0.5f);
        }

        // live aircraft from the flight plan (also when the entity is out of tracking range)
        Vec3 jp = CinematicDirector.jetPos(mc.getFrameTime());
        CompoundTag plan = CinematicDirector.plan();
        if (jp != null && plan != null) {
            float jx = toMapX(jp.x), jy = toMapY(jp.z);
            AircraftType ac = AircraftType.byIndex(plan.getInt("Ac"));
            float ang = (float) Math.atan2(plan.getDouble("DZ"), plan.getDouble("DX")) + (float) Math.PI / 2;
            for (int i = 1; i < 10; i++) {
                float tx2 = jx - (float) plan.getDouble("DX") * i * 4, ty2 = jy - (float) plan.getDouble("DZ") * i * 4;
                Ui.disc(tx2, ty2, 1f, Theme.a(Theme.AMBER, 0.5f * (1 - i / 10f)), 6);
            }
            drawPlane(ac, jx, jy, 8, ang, Theme.a(Theme.AMBER, 1f));
            Ui.text(ac.callsign, jx + 8, jy + 4, Theme.a(Theme.AMBER, 1f), 0.5f);
        }
        Ui.endScissor();

        // hover readout
        if (inMap(mx, my) && !ClientData.strikeActive()) {
            double wx = toWorldX(mx), wz = toWorldZ(my);
            double d = Math.sqrt((wx - cx()) * (wx - cx()) + (wz - cz()) * (wz - cz()));
            boolean ok = d <= ClientData.maxRange();
            Ui.line(mx, MY0, mx, MY0 + MS, 0.4f, Theme.a(ok ? acc : Theme.RED, 0.35f));
            Ui.line(MX0, my, MX0 + MS, my, 0.4f, Theme.a(ok ? acc : Theme.RED, 0.35f));
            String rd = String.format("GRID %d, %d   RNG %dm%s", (int) Math.floor(wx), (int) Math.floor(wz), (int) d, ok ? "" : "  OUT OF RANGE");
            Ui.rect(MX0, MY0 + MS - 10, MX0 + MS, MY0 + MS, Theme.a(Theme.BG, 0.8f));
            Ui.text(rd, MX0 + 3, MY0 + MS - 8, Theme.a(ok ? acc : Theme.RED, 1f), 0.5f);
        }
        Ui.text("TACTICAL GRID // " + (int) (map.radius * 2) + "m", MX0, MY0 - 9, Theme.a(acc, 0.9f), 0.55f);
        Ui.textR("L-CLICK DESIGNATE  R-CLICK CLEAR", MX0 + MS, MY0 - 9, Theme.a(Theme.MUTED, 0.9f), 0.45f);
    }

    /** Top-down fictional airframe silhouettes for the map and cards. angle: 0 = nose up. */
    public static void drawPlane(AircraftType ac, float cx, float cy, float size, float angle, int color) {
        float[][] tris = switch (ac) {
            case KESTREL -> new float[][]{
                    {0, -1, -0.12f, -0.5f, 0.12f, -0.5f},
                    {-0.12f, -0.5f, 0.12f, -0.5f, 0.12f, 0.8f}, {-0.12f, -0.5f, 0.12f, 0.8f, -0.12f, 0.8f},
                    {-0.1f, -0.25f, -0.85f, 0.35f, -0.1f, 0.3f}, {0.1f, -0.25f, 0.85f, 0.35f, 0.1f, 0.3f},
                    {-0.1f, 0.5f, -0.42f, 0.85f, -0.1f, 0.8f}, {0.1f, 0.5f, 0.42f, 0.85f, 0.1f, 0.8f}};
            case GOLIATH -> new float[][]{
                    {0, -0.95f, -0.16f, -0.6f, 0.16f, -0.6f},
                    {-0.16f, -0.6f, 0.16f, -0.6f, 0.16f, 0.85f}, {-0.16f, -0.6f, 0.16f, 0.85f, -0.16f, 0.85f},
                    {-0.15f, -0.25f, -1f, 0.02f, -1f, 0.2f}, {-0.15f, -0.25f, -1f, 0.2f, -0.15f, 0.18f},
                    {0.15f, -0.25f, 1f, 0.02f, 1f, 0.2f}, {0.15f, -0.25f, 1f, 0.2f, 0.15f, 0.18f},
                    {-0.12f, 0.62f, -0.5f, 0.82f, -0.12f, 0.85f}, {0.12f, 0.62f, 0.5f, 0.82f, 0.12f, 0.85f}};
            case WRAITH -> new float[][]{
                    {0, -1, -1, 0.55f, 0, 0.3f}, {0, -1, 1, 0.55f, 0, 0.3f},
                    {-0.45f, 0.3f, -1f, 0.55f, -0.3f, 0.6f}, {0.45f, 0.3f, 1f, 0.55f, 0.3f, 0.6f}};
        };
        float cs = (float) Math.cos(angle), sn = (float) Math.sin(angle);
        for (float[] t : tris) {
            float[] p = new float[6];
            for (int i = 0; i < 3; i++) {
                float x = t[i * 2] * size, y = t[i * 2 + 1] * size;
                p[i * 2] = cx + x * cs - y * sn;
                p[i * 2 + 1] = cy + x * sn + y * cs;
            }
            Ui.tri(p[0], p[1], p[2], p[3], p[4], p[5], color);
        }
    }

    private void drawAircraft() {
        int acc = Theme.accent();
        boolean locked = ClientData.strikeActive() || overlayUp();
        for (int i = 0; i < 3; i++) {
            AircraftType ac = AircraftType.byIndex(i);
            float x0 = 216 + i * 99, y0 = 42, x1 = x0 + 95, y1 = 106;
            boolean sel = ClientData.selectedAircraft == i;
            boolean hov = !locked && s.hovered(x0, y0, x1, y1);
            Ui.panel(x0, y0, x1, y1, sel ? acc : Theme.MUTED, sel ? 1f : 0.6f);
            if (sel) Ui.gradV(x0, y0, x1, y1, Theme.a(acc, 0.12f), Theme.a(acc, 0.02f));
            if (hov) Ui.rect(x0, y0, x1, y1, Theme.a(acc, 0.06f));
            Ui.text(ac.displayName, x0 + 4, y0 + 4, Theme.a(sel ? Theme.WHITE : Theme.mix(acc, Theme.WHITE, 0.3f), 1f), 0.62f);
            Ui.text(ac.role, x0 + 4, y0 + 11, Theme.a(Theme.MUTED, 1f), 0.45f);
            float bob = sel ? (float) Math.sin(System.currentTimeMillis() / 300.0) * 1.2f : 0;
            drawPlane(ac, x0 + 24, y0 + 32 + bob, 11, 0, Theme.a(sel ? acc : Theme.scale(acc, 0.55f), 1f));
            if (sel) Ui.ring(x0 + 24, y0 + 32, 15, 0.5f, Theme.a(acc, 0.35f), 32);
            for (int st = 0; st < 3; st++) {
                float sy = y0 + 22 + st * 9;
                Ui.text(AircraftType.STAT_NAMES[st], x0 + 44, sy, Theme.a(Theme.MUTED, 1f), 0.42f);
                float bw = 44;
                Ui.rect(x0 + 44, sy + 4.2f, x0 + 44 + bw, sy + 5.8f, Theme.a(acc, 0.12f));
                float f = Ease.outCubic(since() * 2 - st * 0.15f) * ac.stats[st] / 10f;
                Ui.rect(x0 + 44, sy + 4.2f, x0 + 44 + bw * f, sy + 5.8f, Theme.a(sel ? acc : Theme.scale(acc, 0.6f), 1f));
            }
            Ui.text(ac.bombs + "x ORDNANCE  //  " + ac.callsign, x0 + 4, y1 - 9, Theme.a(sel ? acc : Theme.MUTED, 1f), 0.45f);
            final int idx = i;
            s.hit("ac" + i, x0, y0, x1, y1, !locked, () -> {
                ClientData.selectedAircraft = idx;
                ClientData.feed("AIRFRAME SET: " + ac.displayName + " (" + ac.callsign + ")", ClientData.Level.INFO);
            });
        }
    }

    private void drawStatus() {
        int acc = Theme.accent();
        float x0 = 216, y0 = 112, x1 = 512, y1 = 176;
        String st = ClientData.status;
        boolean armed = st.equals("STANDBY") && ClientData.targetX != null && ClientData.cooldownRemaining() == 0;
        if (armed) st = "ARMED";
        int col = switch (st) {
            case "ALERT", "INBOUND", "ACTIVE" -> Theme.RED;
            case "REARMING" -> Theme.AMBER;
            case "COMPLETE" -> acc;
            case "ARMED" -> Theme.AMBER;
            default -> acc;
        };
        Ui.panel(x0, y0, x1, y1, col, 1f);
        Ui.text("MISSION STATUS", x0 + 5, y0 + 4, Theme.a(Theme.MUTED, 1f), 0.5f);
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 200.0);
        boolean hot = col == Theme.RED;
        Ui.textGlow(st, x0 + 5, y0 + 13, col, 1.7f, hot ? pulse : 0.4f);
        String sub = !ClientData.activeCodename.isEmpty() && (ClientData.strikeActive() || st.equals("COMPLETE")) ? ClientData.activeCodename
                : st.equals("REARMING") ? "AIRFRAME REARMING  " + (ClientData.cooldownRemaining() / 20 + 1) + "s"
                : armed ? "TARGET LOCKED  //  AWAITING EXECUTE" : "NO ACTIVE TASKING";
        Ui.text(sub, x0 + 5, y0 + 31, Theme.a(Theme.WHITE, 0.85f), 0.5f);

        // animated telemetry trace
        float gx0 = x0 + 5, gx1 = x0 + 145, gy = y0 + 50;
        Ui.rect(gx0, gy - 8, gx1, gy + 8, Theme.a(col, 0.05f));
        float t = System.currentTimeMillis() / 1000f;
        float amp = hot ? 6 : 3;
        float prevX = gx0, prevY = gy;
        for (float x = gx0; x <= gx1; x += 2) {
            float u = (x - gx0) / 14f;
            float y = gy + (float) (Math.sin(u + t * 3) * 0.6 + Math.sin(u * 2.7 + t * 5.3) * 0.3 + Math.sin(u * 7 + t * 9) * 0.1) * amp;
            if (x > gx0) Ui.line(prevX, prevY, x, y, 0.6f, Theme.a(col, 0.8f));
            prevX = x; prevY = y;
        }

        // telemetry readouts
        AircraftType ac = AircraftType.byIndex(ClientData.selectedAircraft);
        float tx = x0 + 156;
        String tgt = "--", rng = "--", brg = "--", eta = "--";
        if (ClientData.targetX != null) {
            double dx = ClientData.targetX + 0.5 - cx(), dz = ClientData.targetZ + 0.5 - cz();
            tgt = ClientData.targetX + " / " + ClientData.targetZ;
            rng = (int) Math.sqrt(dx * dx + dz * dz) + " m";
            brg = String.format("%03d", (int) ((Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360));
            double approach = AegisConfig.COMMON.approachDistance.get();
            eta = String.format("%.1f s", (approach / ac.speed + 50) / 20.0);
        }
        float wind = 4 + 2 * (float) Math.sin(t * 0.3);
        String[][] rows = {{"TARGET X/Z", tgt}, {"RANGE", rng}, {"BEARING", brg}, {"RELEASE ALT", "36 m AGL"},
                {"AIRFRAME", ac.displayName}, {"TIME ON TGT", eta}, {"WIND", String.format("%.1f kt %03d", wind, (int) (200 + 20 * Math.sin(t * 0.1)))}};
        for (int i = 0; i < rows.length; i++) {
            float ry = y0 + 4 + i * 8.3f;
            Ui.text(rows[i][0], tx, ry, Theme.a(Theme.MUTED, 1f), 0.47f);
            Ui.textR(rows[i][1], x1 - 5, ry, Theme.a(Theme.WHITE, 0.9f), 0.47f);
            Ui.rect(tx, ry + 6.6f, x1 - 5, ry + 6.9f, Theme.a(acc, 0.08f));
        }
    }

    private void drawApproach() {
        int acc = Theme.accent();
        float y0 = 182, y1 = 197;
        Ui.text("APPROACH", 216, y0 + 4.5f, Theme.a(Theme.MUTED, 1f), 0.55f);
        boolean locked = ClientData.strikeActive() || overlayUp();
        for (int i = 0; i < HEADINGS.length; i++) {
            float x0 = 262 + i * 50;
            final int h = i - 1;
            s.button("hd" + i, x0, y0, x0 + 46, y1, HEADINGS[i], acc, !locked, ClientData.heading == h, () -> {
                ClientData.heading = h;
                ClientData.feed("APPROACH VECTOR: " + (h < 0 ? "AUTO (OVER TERMINAL)" : "FROM " + HEADINGS[h + 1]), ClientData.Level.INFO);
            });
        }
    }

    private String executeBlocker() {
        if (CinematicDirector.phase() != CinematicDirector.Phase.NONE || ClientData.strikeActive()) return "STRIKE IN PROGRESS";
        if (ClientData.pendingDebrief != null) return "ACKNOWLEDGE DEBRIEF";
        if (ClientData.cooldownRemaining() > 0) return "REARMING  " + (ClientData.cooldownRemaining() / 20 + 1) + "s";
        if (ClientData.targetX == null) return "DESIGNATE A TARGET ON THE GRID";
        return null;
    }

    private void drawExecute(float mx, float my) {
        float x0 = 216, y0 = 203, x1 = 512, y1 = 242;
        String blocker = executeBlocker();
        boolean enabled = blocker == null;
        long now = System.currentTimeMillis();
        float hold = holdStart < 0 ? 0 : Math.min(1, (now - holdStart) / 900f);
        if (holdStart >= 0 && (!enabled || !s.hovered(x0, y0, x1, y1))) holdStart = -1;
        if (hold >= 1 && enabled) {
            holdStart = -1;
            launch();
        }
        int col = enabled ? Theme.RED : Theme.MUTED;
        boolean hov = enabled && s.hovered(x0, y0, x1, y1);
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 160.0);
        Ui.rect(x0, y0, x1, y1, Theme.a(Theme.scale(col, 0.16f), 1f));
        if (enabled) {
            float off = (now / 40f) % 14;
            Ui.scissor(x0, y0, x1, y1);
            for (float sx = x0 - 20 + off; sx < x1 + 20; sx += 14) {
                Ui.tri(sx, y1, sx + 7, y0, sx + 10, y0, Theme.a(col, 0.05f + (hov ? 0.04f : 0)));
            }
            Ui.endScissor();
        }
        if (hold > 0) {
            Ui.rect(x0, y0, x0 + (x1 - x0) * hold, y1, Theme.a(col, 0.55f));
            Ui.rect(x0 + (x1 - x0) * hold - 1, y0, x0 + (x1 - x0) * hold + 1, y1, Theme.a(Theme.WHITE, 0.9f));
        }
        Ui.frame(x0, y0, x1, y1, 1, Theme.a(col, enabled ? 0.6f + 0.4f * (hov ? pulse : 0.3f) : 0.4f));
        Ui.brackets(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 6, 1, Theme.a(col, enabled ? 1f : 0.4f));
        String label = enabled ? (hold > 0 ? "AUTHORIZING  " + (int) (hold * 100) + "%" : "HOLD TO EXECUTE STRIKE") : blocker;
        Ui.textGlow(label, (x0 + x1) / 2f - Ui.width(label) * 0.55f, y0 + 10, enabled ? Theme.WHITE : Theme.MUTED, 1.1f, enabled && hov ? pulse : 0);
        String sub = enabled ? AircraftType.byIndex(ClientData.selectedAircraft).callsign + "  //  " + ClientData.targetX + ", " + ClientData.targetZ
                : "FICTIONAL SIMULATION  //  AEGIS TACNET";
        Ui.textC(sub, (x0 + x1) / 2f, y0 + 26, Theme.a(enabled ? Theme.AMBER : Theme.MUTED, 0.9f), 0.5f);
    }

    private static boolean overlayUp() {
        return ClientData.pendingDebrief != null || CinematicDirector.awaitingDebrief();
    }

    private void launch() {
        ClientData.status = "AUTHORIZING";
        ClientData.feed("EXECUTE ORDER SENT // AWAITING AUTHORIZATION", ClientData.Level.WARN);
        Sfx.confirm();
        AegisNetwork.toServer(new Packets.LaunchStrike(s.pos, ClientData.selectedAircraft, ClientData.targetX, ClientData.targetZ, ClientData.heading));
    }

    private void drawFeed() {
        int acc = Theme.accent();
        float x0 = 8, y0 = 248, x1 = 512, y1 = 294;
        Ui.panel(x0, y0, x1, y1, acc, 0.7f);
        boolean blink = (System.currentTimeMillis() / 450) % 2 == 0;
        Ui.text("LIVE EVENT FEED" + (blink ? " _" : ""), x0 + 5, y0 + 3, Theme.a(acc, 1f), 0.5f);
        Ui.textR(ClientData.FEED.size() + " EVENTS", x1 - 5, y0 + 3, Theme.a(Theme.MUTED, 1f), 0.45f);
        List<ClientData.FeedEntry> feed = ClientData.FEED;
        int n = Math.min(4, feed.size());
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            ClientData.FeedEntry e = feed.get(feed.size() - n + i);
            float ry = y0 + 12 + i * 8;
            int col = switch (e.level()) {
                case OK -> acc;
                case WARN -> Theme.AMBER;
                case ALERT -> Theme.RED;
                default -> Theme.WHITE;
            };
            float age = (now - e.createdMs()) / 600f;
            if (age < 1) Ui.rect(x0 + 3, ry - 1, x1 - 3, ry + 7, Theme.a(col, 0.18f * (1 - age)));
            Ui.text("[" + e.time() + "]", x0 + 5, ry, Theme.a(Theme.MUTED, 1f), 0.5f);
            Ui.text(Ui.typed(e.text(), Math.min(1, age * 1.5f + 0.1f)), x0 + 46, ry, Theme.a(col, 1f), 0.5f);
        }
    }

    // ------------------------------------------------------------------ debrief (cutscene D)

    private void drawDebrief() {
        CompoundTag rec = ClientData.pendingDebrief;
        boolean waiting = rec == null && CinematicDirector.awaitingDebrief();
        if (rec == null && !waiting) {
            debriefMs = -1;
            return;
        }
        int acc = Theme.accent();
        long now = System.currentTimeMillis();
        if (rec != null && shownDebrief != rec) {
            shownDebrief = rec;
            debriefMs = now;
            Sfx.missionComplete();
        }
        Ui.rect(4, 36, 516, 296, Theme.a(0x020403, 0.86f));
        if (rec == null) {
            float spin = (now % 100000) / 300f;
            Ui.arc(260, 150, 14, 1.5f, spin, spin + 4f, Theme.a(acc, 1f), 32);
            Ui.textC("AWAITING BATTLE DAMAGE ASSESSMENT", 260, 172, Theme.a(acc, 1f), 0.6f);
            return;
        }
        float t = (now - debriefMs) / 1000f;
        float flash = Math.max(0, 1 - t / 0.35f);
        if (flash > 0) Ui.rect(4, 36, 516, 296, Theme.a(Theme.WHITE, 0.25f * flash));

        float cx = 260;
        float stamp = Ease.outBack(t / 0.45f);
        float sc = 2.8f - 1.2f * stamp;
        String title = "MISSION COMPLETE";
        Ui.g.pose().pushPose();
        Ui.g.pose().translate(cx, 66, 0);
        Ui.g.pose().scale(sc / 1.6f, sc / 1.6f, 1);
        Ui.textGlow(title, -Ui.width(title) * 0.8f, -6, acc, 1.6f, 0.8f);
        Ui.g.pose().popPose();
        float lineW = 180 * Ease.outCubic(t * 2 - 0.4f);
        Ui.rect(cx - lineW, 84, cx + lineW, 84.6f, Theme.a(acc, 0.8f));

        // check mark in a ring
        float ck = Ease.outCubic(t * 1.6f - 0.3f);
        Ui.ring(cx, 112, 14, 1.2f, Theme.a(acc, ck), 40);
        float p1 = Math.min(1, ck * 2), p2 = Math.max(0, ck * 2 - 1);
        Ui.line(cx - 7, 112, cx - 7 + 5 * p1, 112 + 5 * p1, 2, Theme.a(acc, 1f));
        if (p2 > 0) Ui.line(cx - 2, 117, cx - 2 + 10 * p2, 117 - 11 * p2, 2, Theme.a(acc, 1f));

        Ui.textC(Ui.typed(rec.getString("Code"), t * 1.4f - 0.4f), cx, 132, Theme.a(Theme.WHITE, 1f), 0.8f);

        AircraftType ac = AircraftType.byIndex(rec.getInt("Ac"));
        int bombs = rec.getInt("Bombs"), hits = rec.getInt("Hits"), kills = rec.getInt("Kills");
        float count = Ease.outCubic(t / 1.4f - 0.5f);
        int eff = bombs > 0 ? Math.round(100f * hits / bombs) : 0;
        String[][] rows = {
                {"AIRFRAME", ac.displayName + "  (" + ac.callsign + ")"},
                {"TARGET GRID", rec.getInt("X") + ", " + rec.getInt("Y") + ", " + rec.getInt("Z")},
                {"ORDNANCE DELIVERED", String.valueOf(Math.round(bombs * count))},
                {"IMPACTS CONFIRMED", String.valueOf(Math.round(hits * count))},
                {"HOSTILES NEUTRALIZED", String.valueOf(Math.round(kills * count))},
                {"MISSION TIME", String.format("%.1f s", rec.getInt("Dur") / 20f * count)},
                {"STRIKE EFFECTIVENESS", Math.round(eff * count) + "%"}};
        for (int i = 0; i < rows.length; i++) {
            float ra = Ease.outCubic(t * 3 - 1.2f - i * 0.18f);
            if (ra <= 0) continue;
            float ry = 148 + i * 12;
            float slide = (1 - ra) * 20;
            Ui.rect(140 - slide, ry - 2, 380 - slide, ry + 9, Theme.a(acc, 0.05f * ra));
            Ui.text(rows[i][0], 146 - slide, ry, Theme.a(Theme.MUTED, ra), 0.6f);
            Ui.textR(rows[i][1], 374 - slide, ry, Theme.a(i == 6 ? acc : Theme.WHITE, ra), 0.6f);
        }
        if (t > 1.6f) {
            s.button("ack", cx - 60, 238, cx + 60, 256, "ACKNOWLEDGE", acc, true, false, () -> {
                ClientData.pendingDebrief = null;
                ClientData.activeCodename = "";
                ClientData.status = ClientData.cooldownRemaining() > 0 ? "REARMING" : "STANDBY";
                ClientData.feed("DEBRIEF FILED TO MISSION HISTORY", ClientData.Level.OK);
            });
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseDown(float mx, float my, int button) {
        if (ClientData.pendingDebrief != null || CinematicDirector.awaitingDebrief()) return true;
        if (s.hovered(216, 203, 512, 242) && executeBlocker() == null && button == 0) {
            holdStart = System.currentTimeMillis();
            Sfx.alarm(1);
            return true;
        }
        if (inMap(mx, my) && !ClientData.strikeActive()) {
            if (button == 1) {
                ClientData.targetX = null;
                ClientData.targetZ = null;
                ClientData.feed("TARGET DESIGNATION CLEARED", ClientData.Level.INFO);
                Sfx.click();
                return true;
            }
            double wx = toWorldX(mx), wz = toWorldZ(my);
            double d = Math.sqrt((wx - cx()) * (wx - cx()) + (wz - cz()) * (wz - cz()));
            if (d > ClientData.maxRange()) {
                Sfx.deny();
                ClientData.feed("TARGET OUTSIDE STRIKE RADIUS", ClientData.Level.ALERT);
                return true;
            }
            ClientData.targetX = (int) Math.floor(wx);
            ClientData.targetZ = (int) Math.floor(wz);
            ClientData.feed("TARGET DESIGNATED  GRID " + ClientData.targetX + ", " + ClientData.targetZ + "  (" + (int) d + "m)", ClientData.Level.WARN);
            Sfx.confirm();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseUp(float mx, float my, int button) {
        if (holdStart >= 0) {
            holdStart = -1;
            return true;
        }
        return false;
    }
}
