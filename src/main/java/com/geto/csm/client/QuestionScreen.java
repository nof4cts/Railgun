package com.geto.csm.client;

import com.geto.csm.network.AnswerPacket;
import com.geto.csm.network.CsmNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** The binding-vow question. Answer, or stay silent and let the timer answer for you. */
public class QuestionScreen extends Screen {
    public final int entityId;
    private final int stage;
    private final long start = Vfx.now();
    private final long timeoutMs;
    private boolean sent;

    public QuestionScreen(int entityId, int stage, int timeoutTicks) {
        super(Component.literal("Am I pretty?"));
        this.entityId = entityId;
        this.stage = stage;
        this.timeoutMs = timeoutTicks * 50L;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int bw = 120, gap = 10, y = (int) (height * 0.72f);
        int x0 = width / 2 - (bw * 3 + gap * 2) / 2;
        addRenderableWidget(Button.builder(Component.literal("Yes, you're pretty"), b -> answer(0)).bounds(x0, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("No"), b -> answer(1)).bounds(x0 + bw + gap, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("You're... average"), b -> answer(2)).bounds(x0 + (bw + gap) * 2, y, bw, 20).build());
    }

    private void answer(int a) {
        if (!sent) {
            sent = true;
            CsmNetwork.sendToServer(new AnswerPacket(entityId, a));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        long el = Vfx.now() - start;
        g.fill(0, 0, width, height, 0xB0200006);
        float jit = (el / 60) % 9 == 0 ? 2f : 0f;
        String jp = stage == 2 ? "これでも…？" : "私、きれい？";
        String en = stage == 2 ? "...EVEN LIKE THIS?" : "AM I PRETTY?";
        float s = el < 220 ? Mth.lerp(Ease.outExpo(el / 220f), 12f, 5f) : 5f;
        ScreenFx.bigText(g, font, jp, width / 2f + jit, height * 0.32f, s, 0xFFFFE8EC, true, 1f);
        ScreenFx.bigText(g, font, en, width / 2f, height * 0.32f + 40, 1.8f, 0xFFE0283C, false, Math.min(1f, el / 400f));
        ScreenFx.bigText(g, font, "Binding vow: no one can strike until you answer.", width / 2f, height * 0.32f + 60, 0.9f, 0xFFC8B0B8, false, 1f);
        float left = Math.max(0, 1 - el / (float) timeoutMs);
        int bw = 240;
        g.fill(width / 2 - bw / 2, (int) (height * 0.66f), width / 2 + bw / 2, (int) (height * 0.66f) + 3, 0x60000000);
        g.fill(width / 2 - bw / 2, (int) (height * 0.66f), (int) (width / 2 - bw / 2 + bw * left), (int) (height * 0.66f) + 3, 0xFFE0283C);
        super.render(g, mx, my, partial);
        if (left <= 0) onClose();
    }
}
