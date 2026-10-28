package com.ironlung.client;

import com.ironlung.IronLungMod;
import com.ironlung.client.overlay.SubOverlay;
import com.ironlung.client.screen.SummaryScreen;
import com.ironlung.config.IronLungConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ClientEvents {
    private ClientEvents() {}

    @Mod.EventBusSubscriber(modid = IronLungMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
            event.register(IronLungMod.id("iron_lung"), new IronLungEffects());
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("submarine", SubOverlay::render);
        }
    }

    @Mod.EventBusSubscriber(modid = IronLungMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            ClientMissionState.tick();
            Cutscene.tick();
            if (ClientMissionState.finaleBlack && mc.screen instanceof com.ironlung.client.screen.SubScreen) mc.setScreen(null);
            ClientSounds.tick();
            if (ClientMissionState.pendingSummary != null && mc.player != null && mc.player.isAlive() && mc.screen == null) {
                mc.setScreen(new SummaryScreen(ClientMissionState.pendingSummary));
                ClientMissionState.pendingSummary = null;
            }
        }

        @SubscribeEvent
        public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            if (!ClientMissionState.inSub() || !IronLungConfig.CAMERA_SHAKE.get()) return;
            float s = ClientMissionState.shake * IronLungConfig.SHAKE_INTENSITY.get().floatValue();
            if (s <= 0.001f) return;
            double t = ClientMissionState.now();
            float yaw = (float) (Math.sin(t * 37.0) * 0.6 + Math.sin(t * 13.3) * 0.4) * s * 1.6f;
            float pitch = (float) (Math.sin(t * 29.0 + 1.3) * 0.6 + Math.sin(t * 11.1) * 0.4) * s * 1.4f;
            float roll = (float) Math.sin(t * 7.7) * s * 2.2f;
            event.setYaw(event.getYaw() + yaw);
            event.setPitch(event.getPitch() + pitch);
            event.setRoll(event.getRoll() + roll);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientMissionState.clear();
            ClientSounds.stopAll();
        }
    }

    /** No sky, no sun, no stars. A faint dried-blood fog colour if anything ever reaches it. */
    public static final class IronLungEffects extends DimensionSpecialEffects {
        public IronLungEffects() {
            super(Float.NaN, false, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
            return new Vec3(0.06, 0.0, 0.0);
        }

        @Override
        public boolean isFoggyAt(int x, int z) {
            return false;
        }
    }
}
