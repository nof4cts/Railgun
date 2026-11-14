package com.aegis.command.client.cinematic;

import com.aegis.command.AegisConfig;
import com.aegis.command.client.ClientData;
import com.aegis.command.client.fx.ScreenFx;
import com.aegis.command.client.ui.Theme;
import com.aegis.command.client.ui.Ui;
import com.aegis.command.mission.AircraftType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Overlay during the cinematic: letterbox, live-feed HUD, radio subtitles, flash and fades. ESC skips. */
public class CinematicScreen extends Screen {
    private static final String[] SHOT_NAMES = {"CAM 01 // FLYBY", "CAM 02 // CHASE", "CAM 03 // GROUND", "CAM 04 // ORBIT"};

    public CinematicScreen() {
        super(Component.literal("Aegis Live Feed"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        Ui.begin(g);
        int w = width, h = height;
        int acc = Theme.accent();
        long now = System.currentTimeMillis();

        // vignette
        Ui.gradH(0, 0, w * 0.18f, h, 0x70000000, 0x00000000);
        Ui.gradH(w * 0.82f, 0, w, h, 0x00000000, 0x70000000);

        // letterbox
        float lb = AegisConfig.CLIENT.letterbox.get() ? CinematicDirector.letterbox() : 0;
        float bar = h * 0.11f * lb;
        if (bar > 0.5f) {
            Ui.rect(0, 0, w, bar, 0xFF000000);
            Ui.rect(0, h - bar, w, h, 0xFF000000);
        }

        // live feed HUD
        float top = Math.max(bar, 6) + 6;
        boolean rec = (now / 500) % 2 == 0;
        Ui.disc(14, top + 4, 3, Theme.a(Theme.RED, rec ? 1f : 0.3f), 12);
        Ui.text("LIVE", 21, top + 1, Theme.a(Theme.RED, 1f), 0.8f);
        int shot = CinematicDirector.shot();
        Ui.text("AEGIS TACNET  //  " + (shot >= 0 ? SHOT_NAMES[shot] : "LINKING"), 46, top + 1, Theme.a(acc, 0.9f), 0.8f);
        Ui.textR(ClientData.activeCodename, w - 12, top + 1, Theme.a(Theme.WHITE, 0.9f), 0.8f);
        double t = Math.max(0, CinematicDirector.flightT(partial));
        int ticks = (int) t;
        String tc = String.format("T+%02d:%02d:%02d", ticks / 1200, (ticks / 20) % 60, (int) ((t % 20) * 3));
        Ui.textR(tc, w - 12, top + 11, Theme.a(acc, 0.8f), 0.7f);
        if (CinematicDirector.plan() != null) {
            AircraftType ac = AircraftType.byIndex(CinematicDirector.plan().getInt("Ac"));
            Ui.text(ac.displayName + "  //  " + ac.callsign, 14, top + 12, Theme.a(Theme.AMBER, 0.85f), 0.7f);
        }
        // frame corners
        float m = 10, l = 18;
        Ui.brackets(m, bar + m, w - m, h - bar - m, l, 1, Theme.a(Theme.WHITE, 0.35f));
        // centre reticle on the ground shot
        if (shot == 2) {
            float cx = w / 2f, cy = h / 2f;
            Ui.ring(cx, cy, 10, 0.75f, Theme.a(acc, 0.5f), 32);
            Ui.line(cx - 22, cy, cx - 13, cy, 0.75f, Theme.a(acc, 0.6f));
            Ui.line(cx + 13, cy, cx + 22, cy, 0.75f, Theme.a(acc, 0.6f));
        }

        // radio subtitles
        if (AegisConfig.CLIENT.subtitles.get() && !CinematicDirector.subtitle.isEmpty()) {
            float age = (now - CinematicDirector.subtitleMs) / 1000f;
            if (age < 4.2f) {
                float a = Math.min(1, age * 4) * Math.min(1, (4.2f - age) * 2);
                String s = Ui.typed(CinematicDirector.subtitle, age * 2.2f);
                float sw = Ui.width(CinematicDirector.subtitle) * 0.9f;
                float sy = h - Math.max(bar, 20) - 22;
                Ui.rect(w / 2f - sw / 2 - 8, sy - 4, w / 2f + sw / 2 + 8, sy + 11, Theme.a(0x000000, 0.55f * a));
                Ui.rect(w / 2f - sw / 2 - 8, sy - 4, w / 2f - sw / 2 - 6, sy + 11, Theme.a(Theme.AMBER, a));
                Ui.text(s, w / 2f - sw / 2, sy, Theme.a(Theme.WHITE, a), 0.9f);
            }
        }
        Ui.textR("ESC  SKIP", w - 12, h - Math.max(bar, 6) - 12, Theme.a(Theme.MUTED, 0.6f), 0.6f);

        // flash + fade
        float fl = ScreenFx.flashAlpha();
        if (fl > 0.01f) Ui.rect(0, 0, w, h, Theme.a(ScreenFx.flashColor(), fl * 0.85f));
        float fade = CinematicDirector.fade();
        if (fade > 0.001f) Ui.rect(0, 0, w, h, Theme.a(0, fade));
        g.flush();
    }

    @Override
    public void onClose() {
        CinematicDirector.end(true);
    }
}
