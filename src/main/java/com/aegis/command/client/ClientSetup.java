package com.aegis.command.client;

import com.aegis.command.AegisCommand;
import com.aegis.command.client.fx.ScreenFx;
import com.aegis.command.client.render.AerialBombRenderer;
import com.aegis.command.client.render.AircraftModels;
import com.aegis.command.client.render.EnergyBoltRenderer;
import com.aegis.command.client.render.StrikeJetRenderer;
import com.aegis.command.client.render.WeaponHud;
import com.aegis.command.client.render.WeaponRenderer;
import com.aegis.command.client.ui.Theme;
import com.aegis.command.item.WeaponType;
import com.aegis.command.registry.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = AegisCommand.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    public static final String CATEGORY = "key.categories.aegiscommand";
    public static final KeyMapping RELOAD = new KeyMapping("key.aegiscommand.reload", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping INSPECT = new KeyMapping("key.aegiscommand.inspect", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping SWITCH = new KeyMapping("key.aegiscommand.switch", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    private ClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.STRIKE_JET.get(), StrikeJetRenderer::new);
        e.registerEntityRenderer(ModEntities.AERIAL_BOMB.get(), AerialBombRenderer::new);
        e.registerEntityRenderer(ModEntities.ENERGY_BOLT.get(), EnergyBoltRenderer::new);
        e.registerEntityRenderer(ModEntities.CINEMATIC_CAMERA.get(), NoopRenderer::new);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(AircraftModels.KESTREL, AircraftModels::kestrel);
        e.registerLayerDefinition(AircraftModels.GOLIATH, AircraftModels::goliath);
        e.registerLayerDefinition(AircraftModels.WRAITH, AircraftModels::wraith);
        e.registerLayerDefinition(AircraftModels.BOMB, AircraftModels::bomb);
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional e) {
        for (WeaponType t : WeaponType.values()) {
            e.register(WeaponRenderer.bodyModel(t));
            e.register(WeaponRenderer.glowModel(t));
        }
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(RELOAD);
        e.register(INSPECT);
        e.register(SWITCH);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("weapon_hud", WeaponHud::render);
        e.registerAboveAll("impact_flash", (gui, g, partial, w, h) -> {
            float a = ScreenFx.flashAlpha();
            if (a > 0.01f) g.fill(0, 0, w, h, Theme.a(ScreenFx.flashColor(), a * 0.8f));
        });
    }
}
