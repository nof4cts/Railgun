package com.geto.csm.client;

import com.geto.csm.CurseKind;
import com.geto.csm.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Bottom-left panel while holding the manipulation focus: selected curse, cooldown, stored orbs, slots. */
public final class Hud {

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || CameraDirector.active()) return;
        boolean holding = mc.player.getMainHandItem().is(ModRegistry.MANIPULATION.get()) || mc.player.getOffhandItem().is(ModRegistry.MANIPULATION.get())
                || mc.player.getMainHandItem().is(ModRegistry.UZUMAKI.get());
        if (!holding) return;
        CurseKind k = CurseKind.byId(ClientState.selected);
        int x = 10, y = g.guiHeight() - 74;
        g.fill(x - 4, y - 4, x + 170, y + 46, 0xA0080410);
        g.fill(x - 4, y - 4, x - 2, y + 46, k.color);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.8f, 1.8f, 1);
        g.drawString(mc.font, k.jp, 0, 0, k.color, false);
        g.pose().popPose();
        g.drawString(mc.font, k.title, x + 4 + (int) (mc.font.width(k.jp) * 1.8f), y + 4, 0xFFFFFFFF, false);
        g.drawString(mc.font, k.grade, x, y + 19, 0xFFB0A0C0, false);
        float cd = ClientState.cooldownSeconds(k.ordinal());
        String status = cd > 0 ? String.format("recovering %.1fs", cd) : "ready — right-click";
        g.drawString(mc.font, status, x, y + 30, cd > 0 ? 0xFF9A8AA8 : 0xFF9CFF3A, false);
        String right = "◉ " + ClientState.absorbed + "   " + ClientState.slots + "/3";
        g.drawString(mc.font, right, x + 166 - mc.font.width(right), y + 30, 0xFFB98CFF, false);
    }

    private Hud() {}
}
