package com.aegis.command.client.screen;

import com.aegis.command.AegisConfig;
import com.aegis.command.client.Sfx;
import com.aegis.command.client.fx.ScreenFx;
import com.aegis.command.client.ui.Theme;
import com.aegis.command.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec;

public class SystemPanel extends Panel {
    private ForgeConfigSpec.IntValue draggingSlider;
    private float sliderX0, sliderX1;

    public SystemPanel(LaptopScreen s) {
        super(s);
    }

    private static void save() {
        AegisConfig.CLIENT_SPEC.save();
    }

    private void toggle(String id, float x, float y, float w, String label, ForgeConfigSpec.BooleanValue v) {
        int acc = Theme.accent();
        boolean on = v.get();
        Ui.text(label, x, y + 3, Theme.a(Theme.WHITE, 0.85f), 0.55f);
        float bx = x + w - 34;
        s.button(id, bx, y, x + w, y + 11, on ? "ON" : "OFF", on ? acc : Theme.MUTED, true, on, () -> {
            v.set(!v.get());
            save();
        });
    }

    private void slider(String id, float x, float y, float w, String label, ForgeConfigSpec.IntValue v, Runnable onChange) {
        int acc = Theme.accent();
        Ui.text(label, x, y, Theme.a(Theme.WHITE, 0.85f), 0.55f);
        Ui.textR(v.get() + "%", x + w, y, Theme.a(acc, 1f), 0.55f);
        float sx0 = x, sx1 = x + w, sy = y + 10;
        float f = v.get() / 100f;
        Ui.rect(sx0, sy, sx1, sy + 3, Theme.a(acc, 0.12f));
        Ui.gradH(sx0, sy, sx0 + (sx1 - sx0) * f, sy + 3, Theme.a(Theme.scale(acc, 0.4f), 1f), Theme.a(acc, 1f));
        for (int i = 0; i <= 10; i++) Ui.rect(sx0 + (sx1 - sx0) * i / 10f, sy + 4, sx0 + (sx1 - sx0) * i / 10f + 0.5f, sy + 6, Theme.a(acc, 0.3f));
        float kx = sx0 + (sx1 - sx0) * f;
        boolean hov = s.hovered(sx0 - 3, sy - 4, sx1 + 3, sy + 7);
        Ui.rect(kx - 2, sy - 3, kx + 2, sy + 6, Theme.a(hov || draggingSlider == v ? Theme.WHITE : acc, 1f));
        s.hit(id, sx0 - 3, sy - 4, sx1 + 3, sy + 7, true, () -> {
            draggingSlider = v;
            sliderX0 = sx0;
            sliderX1 = sx1;
            setSlider(s.vmx());
            if (onChange != null) onChange.run();
        });
    }

    private void setSlider(float mx) {
        if (draggingSlider == null) return;
        int val = Math.round(Math.max(0, Math.min(1, (mx - sliderX0) / (sliderX1 - sliderX0))) * 100);
        if (val != draggingSlider.get()) {
            draggingSlider.set(val);
            save();
        }
    }

    @Override
    public void render(float mx, float my, float dt) {
        int acc = Theme.accent();
        var cfg = AegisConfig.CLIENT;
        float colW = 160;
        float[] cols = {10, 182, 354};
        String[] heads = {"AUDIO", "DISPLAY", "CINEMATIC"};
        for (int i = 0; i < 3; i++) {
            Ui.panel(cols[i] - 4, 42, cols[i] + colW + 4, 232, acc, 0.8f);
            Ui.textGlow(heads[i], cols[i], 47, acc, 0.8f, 0.4f);
        }

        // AUDIO
        float x = cols[0], y = 64;
        slider("vol", x, y, colW, "MASTER VOLUME", cfg.volume, null);
        y += 24;
        toggle("mute", x, y, colW, "MUTE ALL", cfg.muted);
        y += 16;
        toggle("uis", x, y, colW, "INTERFACE SOUNDS", cfg.uiSounds);
        y += 22;
        s.button("testA", x, y, x + colW, y + 12, "TEST ALERT TONE", Theme.AMBER, true, false, () -> Sfx.alarm(2));
        y += 16;
        s.button("testB", x, y, x + colW, y + 12, "TEST RADIO CHANNEL", acc, true, false, Sfx::radio);
        y += 16;
        s.button("testC", x, y, x + colW, y + 12, "TEST IMPACT AUDIO", Theme.RED, true, false, () -> {
            var p = Minecraft.getInstance().player;
            if (p != null) Sfx.impact(p.getX(), p.getY(), p.getZ(), 3f, 30);
        });

        // DISPLAY
        x = cols[1];
        y = 64;
        Ui.text("ACCENT", x, y, Theme.a(Theme.WHITE, 0.85f), 0.55f);
        y += 8;
        float bw = (colW - 4) / 3f;
        String[] an = {"GREEN", "CYAN", "AMBER"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            s.button("acc" + i, x + i * (bw + 2), y, x + i * (bw + 2) + bw, y + 12, an[i], Theme.ACCENTS[i], true,
                    cfg.accent.get() == i, () -> {
                        cfg.accent.set(idx);
                        save();
                    });
        }
        y += 20;
        toggle("scan", x, y, colW, "SCANLINES", cfg.scanlines);
        y += 16;
        toggle("noise", x, y, colW, "INTERFACE NOISE", cfg.noise);
        y += 16;
        toggle("full", x, y, colW, "FULLSCREEN TERMINAL", cfg.fullscreenTerminal);
        y += 16;
        toggle("boot", x, y, colW, "BOOT SEQUENCE", cfg.bootSequence);
        y += 20;
        Ui.text("ACCENT CHANGES APPLY TO THE", x, y, Theme.a(Theme.MUTED, 0.8f), 0.45f);
        Ui.text("TACTICAL MAP ON NEXT OPEN", x, y + 6, Theme.a(Theme.MUTED, 0.8f), 0.45f);

        // CINEMATIC
        x = cols[2];
        y = 64;
        toggle("cine", x, y, colW, "CINEMATIC CAMERA", cfg.cinematicCamera);
        y += 16;
        toggle("lbox", x, y, colW, "LETTERBOX", cfg.letterbox);
        y += 16;
        toggle("subs", x, y, colW, "RADIO SUBTITLES", cfg.subtitles);
        y += 20;
        slider("shake", x, y, colW, "SCREEN SHAKE", cfg.shake, null);
        y += 24;
        s.button("testS", x, y, x + colW, y + 12, "TEST SCREEN SHAKE", acc, true, false, () -> ScreenFx.addTrauma(0.8f));
        y += 16;
        Ui.text("CINEMATIC CAMERA OFF = WATCH", x, y + 2, Theme.a(Theme.MUTED, 0.8f), 0.45f);
        Ui.text("THE STRIKE LIVE ON THE GRID", x, y + 8, Theme.a(Theme.MUTED, 0.8f), 0.45f);

        // footer
        Ui.panel(6, 240, 514, 292, acc, 0.6f);
        var mc = Minecraft.getInstance();
        String op = mc.player != null ? mc.player.getGameProfile().getName().toUpperCase() : "OPERATOR";
        int ping = 0;
        if (mc.getConnection() != null && mc.player != null) {
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) ping = info.getLatency();
        }
        Ui.text("AEGIS OS 4.2.1  //  FORGE 1.20.1  //  OPERATOR " + op, 12, 246, Theme.a(acc, 1f), 0.55f);
        Ui.text("UPLINK LATENCY " + ping + " ms  //  ALL SIMULATED OPERATIONS ARE FICTIONAL", 12, 256, Theme.a(Theme.MUTED, 1f), 0.47f);
        Ui.text("SHORTCUTS: 1-4 SWITCH TABS  //  ESC CLOSE  //  R RELOAD  G INSPECT  V SWITCH WEAPON (IN GAME)", 12, 265, Theme.a(Theme.MUTED, 0.8f), 0.45f);
        s.button("reset", 400, 272, 508, 286, "RESET DEFAULTS", Theme.AMBER, true, false, () -> {
            cfg.volume.set(80); cfg.muted.set(false); cfg.uiSounds.set(true); cfg.accent.set(0);
            cfg.scanlines.set(true); cfg.noise.set(true); cfg.fullscreenTerminal.set(false); cfg.bootSequence.set(true);
            cfg.cinematicCamera.set(true); cfg.letterbox.set(true); cfg.subtitles.set(true); cfg.shake.set(70);
            save();
        });
    }

    @Override
    public boolean drag(float mx, float my, float dx, float dy, int button) {
        if (draggingSlider != null) {
            setSlider(mx);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseUp(float mx, float my, int button) {
        draggingSlider = null;
        return false;
    }
}
