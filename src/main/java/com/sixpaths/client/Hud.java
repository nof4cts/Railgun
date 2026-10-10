package com.sixpaths.client;

import com.sixpaths.Ability;
import com.sixpaths.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Bottom-left panel while holding the focus: selected technique, cooldown, active windows. */
public final class Hud {

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || CameraDirector.active()) return;
        boolean holding = mc.player.getMainHandItem().is(ModRegistry.FOCUS.get()) || mc.player.getOffhandItem().is(ModRegistry.FOCUS.get());
        float asc = ClientState.ascendSeconds();
        if (!holding && asc <= 0) return;
        Ability a = Ability.byId(ClientState.selected);
        int x = 10, y = g.guiHeight() - 78;
        g.fill(x - 4, y - 4, x + 196, y + 50, 0xA0080410);
        g.fill(x - 4, y - 4, x - 2, y + 50, a.color);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.6f, 1.6f, 1);
        g.drawString(mc.font, a.jp, 0, 0, a.color, false);
        g.pose().popPose();
        g.drawString(mc.font, a.title, x, y + 16, 0xFFFFFFFF, false);
        g.drawString(mc.font, a.path, x, y + 27, 0xFFB0A0C0, false);
        float cd = ClientState.cooldownSeconds(a.ordinal());
        String status = cd > 0 ? String.format("recovering %.1fs", cd) : "ready — right-click";
        g.drawString(mc.font, status, x, y + 38, cd > 0 ? 0xFF9A8AA8 : 0xFFB48CFF, false);
        if (asc > 0) {
            String s = String.format("ASCENDED %.0fs", asc);
            g.drawString(mc.font, s, x + 190 - mc.font.width(s), y + 38, 0xFFFFF0C0, false);
        }
    }

    private Hud() {}
}
