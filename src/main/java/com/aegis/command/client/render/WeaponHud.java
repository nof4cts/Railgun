package com.aegis.command.client.render;

import com.aegis.command.client.ui.Theme;
import com.aegis.command.client.ui.Ui;
import com.aegis.command.item.AegisWeaponItem;
import com.aegis.command.item.WeaponType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** Compact weapon readout: name, segmented cell bar, reload/charge state, and a reactive reticle ring. */
public final class WeaponHud {
    private WeaponHud() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.level == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        WeaponType t = AegisWeaponItem.typeOf(stack);
        if (t == null) return;
        Ui.begin(g);
        int ec = AegisWeaponItem.energyColor(stack);
        int ammo = AegisWeaponItem.ammo(stack);
        boolean reloading = AegisWeaponItem.isReloading(stack, mc.level);

        float x1 = w - 8, x0 = x1 - 120, y1 = h - 8, y0 = y1 - 32;
        Ui.rect(x0, y0, x1, y1, Theme.a(0x05080C, 0.72f));
        Ui.gradH(x0, y0, x1, y1, Theme.a(ec, 0.0f), Theme.a(ec, 0.12f));
        Ui.brackets(x0, y0, x1, y1, 5, 1, Theme.a(ec, 0.9f));
        Ui.text(t.displayName, x0 + 5, y0 + 4, Theme.a(Theme.WHITE, 1f), 0.8f);
        Ui.textR(t.classification, x1 - 5, y0 + 5, Theme.a(Theme.MUTED, 1f), 0.5f);

        float bx0 = x0 + 5, bx1 = x1 - 30, by = y0 + 17;
        int segs = Math.min(t.capacity, 24);
        float per = t.capacity / (float) segs;
        float sw = (bx1 - bx0) / segs;
        for (int i = 0; i < segs; i++) {
            boolean on = i < Math.ceil(ammo / per);
            Ui.rect(bx0 + i * sw, by, bx0 + (i + 1) * sw - 1, by + 5, Theme.a(on ? ec : 0x1A2630, on ? 0.95f : 0.8f));
        }
        Ui.textR(String.valueOf(ammo), x1 - 5, by - 2, Theme.a(ammo == 0 ? Theme.RED : Theme.WHITE, 1f), 1f);

        String state;
        int sc = Theme.MUTED;
        if (reloading) {
            float p = AegisWeaponItem.reloadProgress(stack, mc.level, partial);
            Ui.rect(bx0, by + 7, bx0 + (bx1 - bx0) * p, by + 8.5f, Theme.a(Theme.AMBER, 1f));
            state = "RECHARGING " + (int) (p * 100) + "%";
            sc = Theme.AMBER;
        } else if (ammo == 0) {
            state = "CELLS DEPLETED - [R] RELOAD";
            sc = Theme.RED;
        } else if (t == WeaponType.TITAN_X && mc.player.isUsingItem()) {
            float c = Math.min(1f, mc.player.getTicksUsingItem() / (float) AegisWeaponItem.TITAN_FULL_CHARGE);
            Ui.rect(bx0, by + 7, bx0 + (bx1 - bx0) * c, by + 8.5f, Theme.a(c >= 1 ? Theme.WHITE : ec, 1f));
            state = c >= 1 ? "CHARGE MAX - RELEASE" : "CHARGING " + (int) (c * 100) + "%";
            sc = ec;
        } else {
            state = "[R] RELOAD  [G] INSPECT  [V] SWITCH";
        }
        Ui.text(state, x0 + 5, y1 - 7, Theme.a(sc, 0.9f), 0.45f);

        // reticle ring
        float cx = w / 2f, cy = h / 2f;
        WeaponAnimator.Pose p = WeaponAnimator.HAND.pose(t, false);
        float spread = 6 + p.tz * 40 + p.charge * -2;
        Ui.ring(cx, cy, spread, 0.6f, Theme.a(ec, 0.45f), 28);
        if (t == WeaponType.TITAN_X && p.charge > 0) {
            Ui.arc(cx, cy, spread + 3, 1.2f, (float) -Math.PI / 2, (float) (-Math.PI / 2 + Math.PI * 2 * p.charge), Theme.a(ec, 0.9f), 32);
        }
        g.flush();
    }
}
