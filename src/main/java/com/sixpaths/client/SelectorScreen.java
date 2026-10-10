package com.sixpaths.client;

import com.sixpaths.Ability;
import com.sixpaths.network.PathsNetwork;
import com.sixpaths.network.SelectPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/** Technique board: every path's techniques as cards. Click to select, scroll to cycle. */
public class SelectorScreen extends Screen {
    private int hovered = -1;
    private final long opened = Vfx.now();
    private static final int COLS = 3;

    public SelectorScreen() {
        super(Component.literal("Six Paths"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static String describe(Ability a) {
        return switch (a) {
            case ALMIGHTY_PUSH -> "Repel everything around you. Reflects projectiles. 10 dmg, wider while ascended.";
            case UNIVERSAL_PULL -> "Drag the target you look at into your hand and impale it on a receiver.";
            case CHAKRA_RODS -> "Throw three black receivers. Hits stun and weaken.";
            case PLANETARY_DEVASTATION -> "Throw a gravity core into the sky. It tears up the land and seals enemies in a moon. 40 dmg.";
            case HEAVENLY_DESCENT -> "Rise above the battlefield, then flatten everything below into a crater. Up to 60 dmg.";
            case ASURA_BARRAGE -> "Ten homing missiles from the shoulders.";
            case ASURA_CANNON -> "A charged arm cannon that bores through terrain. 30 dmg.";
            case SOUL_EXTRACTION -> "Rip the soul from a close target. Kills anything with 60 max HP or less (not bosses or players).";
            case SUMMON_RHINO -> "A drill-horned behemoth that charges through everything.";
            case SUMMON_CENTIPEDE -> "A forty-legged centipede that coils around its prey.";
            case SUMMON_HOUND -> "A three-headed hound that splits into another hound when struck hard.";
            case SUMMON_BIRD -> "A four-winged roc. Right-click to ride; carries two.";
            case PRETA_ABSORPTION -> "For 4.5 s, absorb projectiles, explosions and magic as healing.";
            case KING_OF_HELL -> "Call the King from below: full heal, cleanse, absorption — and it judges the nearest foe.";
            case SAMSARA -> "Restore every player within 96 blocks. Costs you nearly everything.";
            case ASCENSION -> "60 s of flight, halved cooldowns and a wider Push.";
            case PATH_SIGHT -> "Mark every hostile within 48 blocks through walls.";
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        Ability[] all = Ability.values();
        float open = Ease.outBack(Math.min(1f, (Vfx.now() - opened) / 240f));
        g.fill(0, 0, width, height, 0xB0050008);
        int rows = (all.length + COLS - 1) / COLS;
        int cw = Math.min(176, (width - 40) / COLS), ch = Math.min(30, (height - 90) / rows);
        int gx = (width - cw * COLS) / 2, gy = 34;
        hovered = -1;
        ScreenFx.bigText(g, font, "六道", width / 2f, 12, 2.0f, 0xFFB48CFF, true, 1f);
        for (int i = 0; i < all.length; i++) {
            Ability a = all[i];
            int col = i % COLS, row = i / COLS;
            int x = gx + col * cw + 2, y = gy + row * ch + 2;
            int w = cw - 4, h = ch - 4;
            float slide = Mth.clamp(open * 1.4f - row * 0.06f, 0, 1);
            x += (int) ((1 - slide) * (col - 1) * 40);
            boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
            if (hov) hovered = i;
            boolean sel = i == ClientState.selected;
            int bg = hov ? Draw.alpha(a.color, 0.35f) : sel ? Draw.alpha(a.color, 0.2f) : 0xC0120A1A;
            g.fill(x, y, x + w, y + h, bg);
            g.fill(x, y, x + 2, y + h, a.ultimate ? 0xFFFFD34D : a.color);
            if (sel) {
                g.fill(x, y, x + w, y + 1, a.color);
                g.fill(x, y + h - 1, x + w, y + h, a.color);
            }
            float cd = ClientState.cooldownSeconds(i);
            if (cd > 0) {
                float k = Math.min(1f, cd * 20f / Math.max(1, a.cooldown));
                g.fill(x + 2, y + h - 2, x + 2 + (int) ((w - 2) * k), y + h, 0xFF6A5A7A);
            }
            g.drawString(font, a.title, x + 6, y + 4, hov ? 0xFFFFFFFF : 0xFFE8E0F0, false);
            String sub = cd > 0 ? String.format("%s · %.0fs", a.path, cd) : a.path;
            g.pose().pushPose();
            g.pose().translate(x + 6, y + 14, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, sub, 0, 0, cd > 0 ? 0xFF9A8AA8 : Draw.alpha(a.color, 0.95f), false);
            g.pose().popPose();
        }
        Ability focus = hovered >= 0 ? all[hovered] : Ability.byId(ClientState.selected);
        int by = gy + rows * ch + 8;
        ScreenFx.bigText(g, font, focus.jp, width / 2f, by + 8, 1.8f, focus.color, hovered >= 0, 1f);
        String d = describe(focus);
        g.drawCenteredString(font, d, width / 2, by + 22, 0xFFD8D0E0);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hovered >= 0) {
            pick(hovered);
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        pick(Mth.positiveModulo(ClientState.selected + (delta < 0 ? 1 : -1), Ability.values().length));
        return true;
    }

    static void pick(int i) {
        ClientState.selected = i;
        PathsNetwork.sendToServer(new SelectPacket(i));
        Sfx.play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.8f);
    }
}
