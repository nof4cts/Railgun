package com.hakari.idg.client.post;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Hooks the post pipeline into the frame (after the whole level) and into resource reloads. */
public final class PostEvents {
    private PostEvents() {}

    @Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT)
    public static final class Game {
        private Game() {}

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            PostRenderer.render(event);
        }

        /** The standing looks: neon bloom inside a domain, the green jackpot grade on your own screen. */
        @SubscribeEvent
        public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
            if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
                return;
            }
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || com.hakari.idg.client.CameraDirector.active()) {
                return;
            }
            boolean inside = false;
            boolean jackpot = false;
            for (com.hakari.idg.network.StatePacket s : com.hakari.idg.client.ClientState.all()) {
                if (s.domainActive && com.hakari.idg.client.ClientState.center(s).distanceTo(mc.player.position()) < s.radius + 1.0F) {
                    inside = true;
                }
                if (s.entityId == mc.player.getId() && s.jackpotTicks > 0) {
                    jackpot = true;
                }
            }
            Post.grade(jackpot ? 0.16F : 0.0F);
            Post.bloom(inside ? 0.45F : jackpot ? 0.18F : 0.0F);
            Post.vignette(inside ? 0x3A0030 : 0x02160A, inside ? 0.3F : jackpot ? 0.22F : 0.0F);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            Post.clear();
        }
    }

    @Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {}

        @SubscribeEvent
        public static void onReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) PostRenderer::reload);
        }
    }
}
