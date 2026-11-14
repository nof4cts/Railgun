package com.aegis.command.client.screen;

import com.aegis.command.AegisConfig;
import com.aegis.command.client.ClientData;
import com.aegis.command.client.Sfx;
import com.aegis.command.client.cinematic.CinematicDirector;
import com.aegis.command.client.ui.Ease;
import com.aegis.command.client.ui.Theme;
import com.aegis.command.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The Aegis laptop: a single military terminal hosting AIRSTRIKE COMMAND, ARSENAL, MISSION HISTORY
 * and SYSTEM. Everything is drawn into a fixed virtual canvas that is scaled to the window, so the
 * layout is identical at every GUI scale. Buttons are immediate-mode: drawn and registered each frame.
 */
public class LaptopScreen extends net.minecraft.client.gui.screens.Screen {
    public static final float VW = 520, VH = 300;
    public static final String[] TAB_NAMES = {"AIRSTRIKE COMMAND", "ARSENAL", "MISSION HISTORY", "SYSTEM"};
    public static final int AIRSTRIKE = 0, ARSENAL = 1, HISTORY = 2, SYSTEM = 3;

    private static int lastTab = AIRSTRIKE;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final RandomSource RNG = RandomSource.create();

    public final BlockPos pos;
    private final Panel[] panels;
    private int tab;
    private int prevTab = -1;
    private long tabMs;
    private final long openMs = System.currentTimeMillis();
    private final boolean boot;
    private final boolean fromCinematic;
    private long lastFrameNs = System.nanoTime();

    // virtual canvas transform
    private float k = 1, ox, oy;
    private float vmx, vmy;

    private record Hit(String id, float x0, float y0, float x1, float y1, Runnable action, boolean enabled) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> frameHits = new ArrayList<>();
    private String hoveredId = "";

    public LaptopScreen(BlockPos pos, boolean fromCinematic) {
        super(Component.literal("Aegis Terminal"));
        this.pos = pos;
        this.fromCinematic = fromCinematic;
        this.panels = new Panel[]{new AirstrikePanel(this), new ArsenalPanel(this), new HistoryPanel(this), new SystemPanel(this)};
        this.tab = ClientData.pendingDebrief != null || fromCinematic ? AIRSTRIKE : lastTab;
        this.boot = !fromCinematic && AegisConfig.CLIENT.bootSequence.get();
        this.tabMs = System.currentTimeMillis() - 2000;
    }

    @Override
    protected void init() {
        layout();
        if (prevTab == -1) {
            prevTab = tab;
            panels[tab].show();
            if (boot) Sfx.startup();
        }
    }

    private void layout() {
        boolean full = AegisConfig.CLIENT.fullscreenTerminal.get();
        float needW = full ? VW + 8 : VW + 40;
        float needH = full ? VH + 8 : VH + 44;
        k = Math.min(width / needW, height / needH);
        ox = (width - VW * k) / 2f;
        oy = full ? (height - VH * k) / 2f : (height - (VH + 18) * k) / 2f;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    public int tab() { return tab; }

    public void switchTab(int t) {
        if (t == tab) return;
        prevTab = tab;
        tab = t;
        lastTab = t;
        tabMs = System.currentTimeMillis();
        panels[t].show();
        Sfx.tab();
    }

    public float bootProgress() {
        if (!boot || skipBoot) return 1;
        return Math.min(1, (System.currentTimeMillis() - openMs) / 1500f);
    }

    public boolean booting() { return bootProgress() < 1; }

    public float vmx() { return vmx; }
    public float vmy() { return vmy; }
    public float scale() { return k; }

    // ------------------------------------------------------------------ immediate-mode widgets

    public boolean hovered(float x0, float y0, float x1, float y1) {
        return vmx >= x0 && vmx < x1 && vmy >= y0 && vmy < y1;
    }

    public void hit(String id, float x0, float y0, float x1, float y1, boolean enabled, Runnable action) {
        frameHits.add(new Hit(id, x0, y0, x1, y1, action, enabled));
    }

    /**
     * Standard terminal button. style colour drives the accent; active = latched/selected state.
     */
    public boolean button(String id, float x0, float y0, float x1, float y1, String label, int color,
                          boolean enabled, boolean active, Runnable action) {
        boolean hov = enabled && hovered(x0, y0, x1, y1);
        int c = enabled ? color : Theme.MUTED;
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 180.0);
        Ui.rect(x0, y0, x1, y1, Theme.a(active ? Theme.scale(c, 0.35f) : Theme.SURFACE_HI, active ? 0.95f : 0.85f));
        if (hov) Ui.rect(x0, y0, x1, y1, Theme.a(c, 0.16f + 0.06f * pulse));
        Ui.frame(x0, y0, x1, y1, 0.5f, Theme.a(c, enabled ? (hov || active ? 0.95f : 0.45f) : 0.25f));
        if (hov || active) Ui.brackets(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 3, 0.75f, Theme.a(c, 0.9f));
        float scale = Math.min(0.75f, (x1 - x0 - 4) / Math.max(1, Ui.width(label)));
        float ty = (y0 + y1) / 2f - 3.4f * scale / 0.75f;
        Ui.textC(label, (x0 + x1) / 2f, ty, Theme.a(enabled ? (active ? Theme.WHITE : Theme.mix(c, Theme.WHITE, hov ? 0.6f : 0.25f)) : Theme.MUTED, 1f), scale);
        hit(id, x0, y0, x1, y1, enabled, action);
        return hov;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1f, (nowNs - lastFrameNs) / 1e9f);
        lastFrameNs = nowNs;
        layout();
        Ui.begin(graphics);
        vmx = (mouseX - ox) / k;
        vmy = (mouseY - oy) / k;
        frameHits.clear();

        // dim the world behind the laptop
        Ui.gradV(0, 0, width, height, 0xB0000000, 0xE0000000);

        long now = System.currentTimeMillis();
        float openT = Ease.outCubic((now - openMs) / 450f);
        CinematicDirector.Phase phase = CinematicDirector.phase();
        float alertPush = 0;
        if (phase == CinematicDirector.Phase.ALERT || phase == CinematicDirector.Phase.TRANSITION) {
            alertPush = Ease.inOutCubic(CinematicDirector.alertProgress());
        }

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(ox, oy, 0);
        pose.scale(k, k, 1);
        // opening lift + alert "camera push" toward the display
        float zoom = (0.94f + 0.06f * openT) * (1 + 0.16f * alertPush);
        pose.translate(VW / 2f, VH / 2f, 0);
        pose.scale(zoom, zoom, 1);
        pose.translate(-VW / 2f, -VH / 2f, 0);
        Ui.alpha = openT;

        // the mouse in canvas space has to account for the zoom too
        vmx = (vmx - VW / 2f) / zoom + VW / 2f;
        vmy = (vmy - VH / 2f) / zoom + VH / 2f;

        if (!AegisConfig.CLIENT.fullscreenTerminal.get()) drawChassis();
        // display glass
        Ui.rect(0, 0, VW, VH, Theme.a(Theme.BG, 1f));
        Ui.gradV(0, 0, VW, VH * 0.6f, Theme.a(Theme.accent(), 0.035f), Theme.a(Theme.accent(), 0f));

        if (!booting()) {
            drawHeader();
            drawTabs();
            float tt = Ease.outCubic((now - tabMs) / 320f);
            float prevAlpha = Ui.alpha;
            Ui.alpha = prevAlpha * tt;
            pose.pushPose();
            pose.translate((1 - tt) * 14, 0, 0);
            panels[tab].render(vmx - (1 - tt) * 14, vmy, dt);
            pose.popPose();
            Ui.alpha = prevAlpha;
            // wipe line sweeping across on tab change
            if (tt < 1) {
                float wx = tt * VW;
                Ui.rect(wx - 1, 36, wx + 1, VH - 2, Theme.a(Theme.accent(), 0.6f * (1 - tt)));
                Ui.gradH(wx - 30, 36, wx, VH - 2, Theme.a(Theme.accent(), 0f), Theme.a(Theme.accent(), 0.12f * (1 - tt)));
            }
        } else {
            drawBoot();
        }

        drawOverlays(now, alertPush);
        pose.popPose();
        Ui.alpha = 1;
        Ui.g.flush();

        // hover sound on entering a new control
        String hov = "";
        for (Hit h : frameHits) if (h.enabled && vmx >= h.x0 && vmx < h.x1 && vmy >= h.y0 && vmy < h.y1) hov = h.id;
        if (!hov.isEmpty() && !hov.equals(hoveredId)) Sfx.hover();
        hoveredId = hov;
        hits.clear();
        hits.addAll(frameHits);
    }

    private void drawChassis() {
        float b = 10;
        // lid bezel
        Ui.rect(-b, -b, VW + b, VH + b, 0xFF15191B);
        Ui.gradV(-b, -b, VW + b, VH + b, 0xFF22282B, 0xFF0E1113);
        Ui.frame(-b, -b, VW + b, VH + b, 1, 0xFF2E3538);
        Ui.frame(-1.5f, -1.5f, VW + 1.5f, VH + 1.5f, 1.5f, 0xFF050606);
        // webcam + status LED
        Ui.disc(VW / 2f, -b / 2f, 1.6f, 0xFF050708, 10);
        Ui.disc(VW / 2f + 8, -b / 2f, 0.9f, Theme.a(Theme.accent(), 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 400.0)), 8);
        Ui.text("AEGIS", 4 - b + 6, VH + b - 8.5f, 0x60FFFFFF, 0.55f);
        // hinge + base deck
        Ui.gradV(-b - 8, VH + b, VW + b + 8, VH + b + 8, 0xFF2A3033, 0xFF101315);
        Ui.rect(VW / 2f - 40, VH + b + 8, VW / 2f + 40, VH + b + 10, 0xFF0A0C0D);
    }

    private void drawHeader() {
        int acc = Theme.accent();
        long now = System.currentTimeMillis();
        Ui.rect(0, 0, VW, 16, Theme.a(Theme.SURFACE, 0.95f));
        Ui.rect(0, 16, VW, 16.5f, Theme.a(acc, 0.35f));
        // logo mark
        Ui.tri(6, 12, 10, 3, 14, 12, Theme.a(acc, 0.9f));
        Ui.tri(8.5f, 12, 10, 8.5f, 11.5f, 12, Theme.a(Theme.BG, 1f));
        Ui.textGlow("AEGIS TACNET", 18, 4.5f, acc, 0.8f, 0.6f);
        Ui.text("// SECURE TERMINAL 07", 18 + Ui.width("AEGIS TACNET") * 0.8f + 4, 5, Theme.a(Theme.MUTED, 1f), 0.6f);

        var mc = Minecraft.getInstance();
        String local = LocalTime.now().format(CLOCK);
        long dayTime = mc.level != null ? (mc.level.getDayTime() + 6000) % 24000 : 0;
        String mct = String.format("%02d:%02d", dayTime / 1000, (dayTime % 1000) * 60 / 1000);
        boolean blink = (now / 500) % 2 == 0;
        String right = "LOCAL " + local + "   WORLD " + mct + "   ";
        float rx = VW - 8;
        Ui.disc(rx - 2, 8, 2f, Theme.a(ClientData.strikeActive() ? Theme.RED : acc, blink ? 1f : 0.35f), 10);
        Ui.textR("LINK", rx - 7, 5, Theme.a(acc, 0.9f), 0.6f);
        Ui.textR(right, rx - 7 - Ui.width("LINK") * 0.6f, 5, Theme.a(Theme.WHITE, 0.8f), 0.6f);
    }

    private void drawTabs() {
        int acc = Theme.accent();
        float x = 6;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            String label = String.format("%02d  %s", i + 1, TAB_NAMES[i]);
            float w = Ui.width(label) * 0.65f + 16;
            boolean sel = i == tab;
            boolean hov = hovered(x, 19, x + w, 33);
            int tabCol = i == ARSENAL ? Theme.TECH : acc;
            Ui.rect(x, 19, x + w, 33, Theme.a(sel ? Theme.scale(tabCol, 0.22f) : Theme.SURFACE, sel ? 1f : 0.8f));
            if (hov && !sel) Ui.rect(x, 19, x + w, 33, Theme.a(tabCol, 0.1f));
            Ui.rect(x, sel ? 31.5f : 32.5f, x + w, 33, Theme.a(tabCol, sel ? 1f : 0.25f));
            if (sel) Ui.gradV(x, 19, x + w, 33, Theme.a(tabCol, 0f), Theme.a(tabCol, 0.18f));
            Ui.text(label, x + 8, 23, Theme.a(sel ? Theme.WHITE : Theme.mix(tabCol, Theme.MUTED, 0.5f), 1f), 0.65f);
            final int idx = i;
            hit("tab" + i, x, 19, x + w, 33, true, () -> switchTab(idx));
            x += w + 3;
        }
        // status chip on the right of the tab bar
        String st = ClientData.status;
        int col = switch (st) {
            case "ALERT", "INBOUND", "ACTIVE" -> Theme.RED;
            case "REARMING" -> Theme.AMBER;
            default -> acc;
        };
        float w = Ui.width(st) * 0.65f + 26;
        Ui.rect(VW - 6 - w, 19, VW - 6, 33, Theme.a(Theme.scale(col, 0.15f), 1f));
        Ui.frame(VW - 6 - w, 19, VW - 6, 33, 0.5f, Theme.a(col, 0.8f));
        float bl = (System.currentTimeMillis() / 300) % 2 == 0 ? 1 : 0.4f;
        Ui.disc(VW - w + 2, 26, 2, Theme.a(col, bl), 10);
        Ui.text(st, VW - w + 8, 23, Theme.a(col, 1f), 0.65f);
    }

    private void drawBoot() {
        int acc = Theme.accent();
        float p = bootProgress();
        String name = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getGameProfile().getName().toUpperCase() : "OPERATOR";
        String[] lines = {
                "AEGIS OS 4.2.1  //  SECURE BOOT",
                "VERIFYING CRYPTOGRAPHIC KEYS ............ OK",
                "MOUNTING TACTICAL FILESYSTEM ............ OK",
                "UPLINK SATCOM-7 ......................... OK",
                "LOADING TERRAIN GRID .................... OK",
                "ARMORY INVENTORY ........................ 3 SIGNATURES",
                "WELCOME, OPERATOR " + name
        };
        float y = 70;
        for (int i = 0; i < lines.length; i++) {
            float lp = (p * 1.25f - i * 0.12f) / 0.12f;
            if (lp <= 0) break;
            boolean last = i == lines.length - 1;
            Ui.text(Ui.typed(lines[i], lp), 120, y, Theme.a(last ? Theme.WHITE : acc, 1f), last ? 0.85f : 0.7f);
            y += last ? 14 : 11;
        }
        // emblem
        float cx = VW / 2f, cy = 42;
        float spin = (System.currentTimeMillis() % 100000) / 600f;
        Ui.arc(cx, cy, 16, 1.2f, spin, spin + 4.2f, Theme.a(acc, 0.9f), 32);
        Ui.arc(cx, cy, 11, 0.8f, -spin * 1.6f, -spin * 1.6f + 2.5f, Theme.a(acc, 0.6f), 24);
        Ui.tri(cx - 6, cy + 5, cx, cy - 7, cx + 6, cy + 5, Theme.a(acc, 0.9f));
        // progress bar
        Ui.frame(120, 220, 400, 226, 0.5f, Theme.a(acc, 0.6f));
        Ui.rect(121, 221, 121 + 278 * Ease.outCubic(p), 225, Theme.a(acc, 0.85f));
        Ui.text(String.format("%3d%%", (int) (p * 100)), 404, 220, Theme.a(acc, 1f), 0.65f);
        Ui.textC("CLICK TO SKIP", VW / 2f, 240, Theme.a(Theme.MUTED, 0.7f), 0.55f);
    }

    private void drawOverlays(long now, float alertPush) {
        int acc = Theme.accent();
        // alert state (cutscene A)
        CinematicDirector.Phase phase = CinematicDirector.phase();
        if (phase == CinematicDirector.Phase.ALERT || phase == CinematicDirector.Phase.TRANSITION) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 140.0);
            Ui.frame(0, 0, VW, VH, 3, Theme.a(Theme.RED, 0.35f + 0.4f * pulse));
            Ui.gradV(0, 0, VW, 40, Theme.a(Theme.RED, 0.25f * pulse), Theme.a(Theme.RED, 0f));
            Ui.gradV(0, VH - 40, VW, VH, Theme.a(Theme.RED, 0f), Theme.a(Theme.RED, 0.25f * pulse));
            // hazard stripes banner
            float by = VH / 2f - 20;
            Ui.rect(0, by, VW, by + 40, Theme.a(0x140404, 0.88f));
            float off = (now / 25f) % 16;
            Ui.scissor(0, by, VW, by + 4);
            for (float sx = -16 + off; sx < VW; sx += 16) Ui.tri(sx, by + 4, sx + 8, by, sx + 12, by, Theme.a(Theme.AMBER, 0.85f));
            Ui.endScissor();
            Ui.scissor(0, by + 36, VW, by + 40);
            for (float sx = -16 - off; sx < VW + 16; sx += 16) Ui.tri(sx, by + 40, sx + 8, by + 36, sx + 12, by + 36, Theme.a(Theme.AMBER, 0.85f));
            Ui.endScissor();
            Ui.textGlow("STRIKE AUTHORIZED  //  WEAPONS FREE", VW / 2f - Ui.width("STRIKE AUTHORIZED  //  WEAPONS FREE") * 0.6f, by + 9, Theme.RED, 1.2f, pulse);
            float left = CinematicDirector.alertSecondsLeft();
            String cd = left > 0 ? String.format("%s  //  AIRCRAFT LAUNCH T-%04.1f", ClientData.activeCodename, left) : ClientData.activeCodename + "  //  AIRCRAFT AIRBORNE";
            Ui.textC(cd, VW / 2f, by + 25, Theme.a(Theme.AMBER, 1f), 0.65f);
        }

        // scanlines + interface noise
        if (AegisConfig.CLIENT.scanlines.get()) {
            for (float y = 0; y < VH; y += 2) Ui.rect(0, y, VW, y + 0.5f, 0x12000000);
            float roll = (now / 12f) % (VH + 40) - 20;
            Ui.gradV(0, roll - 20, VW, roll, Theme.a(acc, 0f), Theme.a(acc, 0.03f));
        }
        if (AegisConfig.CLIENT.noise.get()) {
            for (int i = 0; i < 24; i++) {
                float x = RNG.nextFloat() * VW, y = RNG.nextFloat() * VH;
                Ui.rect(x, y, x + 1 + RNG.nextFloat() * 3, y + 0.5f, Theme.a(acc, 0.05f + RNG.nextFloat() * 0.07f));
            }
            if (RNG.nextInt(90) == 0) {
                float y = RNG.nextFloat() * VH;
                Ui.rect(0, y, VW, y + 1.5f, Theme.a(acc, 0.08f));
            }
        }
        // vignette
        Ui.gradH(0, 0, 40, VH, 0x50000000, 0x00000000);
        Ui.gradH(VW - 40, 0, VW, VH, 0x00000000, 0x50000000);
        Ui.gradV(0, 0, VW, 26, 0x40000000, 0x00000000);
        Ui.gradV(0, VH - 26, VW, VH, 0x00000000, 0x50000000);

        // fade to black into the cinematic, or in from it
        float black = 0;
        if (phase == CinematicDirector.Phase.TRANSITION) black = CinematicDirector.transitionProgress();
        if (fromCinematic) black = Math.max(black, 1 - Ease.outCubic((now - openMs) / 700f));
        if (black > 0.001f) {
            float a = Ui.alpha;
            Ui.alpha = 1;
            Ui.rect(-60, -60, VW + 60, VH + 60, Theme.a(0, black));
            Ui.alpha = a;
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (booting()) {
            tabMs = System.currentTimeMillis() - 2000;
            skipBoot = true;
            return true;
        }
        if (CinematicDirector.phase() == CinematicDirector.Phase.TRANSITION) return true;
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (vmx >= h.x0 && vmx < h.x1 && vmy >= h.y0 && vmy < h.y1) {
                    if (h.enabled) { Sfx.click(); h.action.run(); }
                    else Sfx.deny();
                    return true;
                }
            }
        }
        return panels[tab].mouseDown(vmx, vmy, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean skipBoot;

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return panels[tab].mouseUp(vmx, vmy, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return panels[tab].drag(vmx, vmy, (float) (dx / k), (float) (dy / k), button) || super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return panels[tab].scroll(vmx, vmy, delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4 && !booting()) {
            switchTab(key - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        for (Panel p : panels) p.close();
        super.removed();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return CinematicDirector.phase() != CinematicDirector.Phase.TRANSITION;
    }
}
