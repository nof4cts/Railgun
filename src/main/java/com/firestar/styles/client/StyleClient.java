package com.firestar.styles.client;

import com.firestar.styles.Style;
import com.firestar.styles.StylesMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** The N key, the switcher, and a small badge naming the style you fight in. */
public final class StyleClient {
    public static final KeyMapping SWITCH = new KeyMapping("key.fightingstyles.switch", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N, "key.categories.fightingstyles");

    private StyleClient() {}

    @Mod.EventBusSubscriber(modid = StylesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Reg {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent e) {
            e.register(SWITCH);
        }
    }

    @Mod.EventBusSubscriber(modid = StylesMod.MODID, value = Dist.CLIENT)
    public static final class Forge {
        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            while (SWITCH.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new StyleScreen());
                }
            }
        }

        @SubscribeEvent
        public static void onGui(RenderGuiEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.screen instanceof StyleScreen || cinematic()) {
                return;
            }
            Style s = ClientStyles.mine();
            GuiGraphics g = e.getGuiGraphics();
            Font font = mc.font;
            String label = s == Style.NONE ? "No style  [" + SWITCH.getTranslatedKeyMessage().getString().toUpperCase() + "]" : s.title;
            int w = font.width(label) + 12;
            int x = 4, y = 4;
            g.fill(x, y, x + w, y + 12, 0x90000000);
            g.fill(x, y, x + 2, y + 12, 0xFF000000 | s.color);
            g.drawString(font, label, x + 6, y + 2, 0xFF000000 | s.color, false);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            ClientStyles.clear();
        }
    }

    static boolean cinematic() {
        return com.firestar.mih.client.cinematic.Director.active() || com.firestar.silver.client.cinematic.Director.active()
                || com.firestar.santoryu.client.cinematic.Director.active() || com.firestar.rose.client.cam.Lens.active()
                || com.firestar.sage.client.cam.Director.active();
    }
}
