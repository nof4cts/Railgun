package com.aegis.command.client;

import com.aegis.command.AegisCommand;
import com.aegis.command.client.cinematic.CinematicDirector;
import com.aegis.command.client.fx.FxWorld;
import com.aegis.command.client.fx.ImpactFrames;
import com.aegis.command.client.fx.ScreenFx;
import com.aegis.command.client.render.WeaponAnimator;
import com.aegis.command.item.AegisWeaponItem;
import com.aegis.command.item.WeaponType;
import com.aegis.command.network.AegisNetwork;
import com.aegis.command.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AegisCommand.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static Item lastHeld;
    private static int lastSlot = -1;

    private ClientEvents() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Sfx.tick();
        CinematicDirector.clientTick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // deploy animation whenever an Aegis weapon comes into the main hand
        ItemStack held = mc.player.getMainHandItem();
        int slot = mc.player.getInventory().selected;
        if (held.getItem() != lastHeld || slot != lastSlot) {
            if (held.getItem() instanceof AegisWeaponItem w) {
                WeaponAnimator.HAND.deploy();
                Sfx.at(SoundEvents.ARMOR_EQUIP_NETHERITE, mc.player.getX(), mc.player.getY() + 1, mc.player.getZ(), 1.5f, 0.35f);
                Sfx.later(5, () -> Sfx.at(SoundEvents.BEACON_POWER_SELECT, mc.player.getX(), mc.player.getY() + 1, mc.player.getZ(), 2.0f, 0.2f));
                if (w.type.heavy) ScreenFx.addTrauma(0.05f);
            }
            lastHeld = held.getItem();
            lastSlot = slot;
        }

        while (ClientSetup.RELOAD.consumeClick()) {
            if (held.getItem() instanceof AegisWeaponItem w && !AegisWeaponItem.isReloading(held, mc.level)
                    && AegisWeaponItem.ammo(held) < w.type.capacity) {
                w.startReload(held, mc.level, mc.player);
                AegisNetwork.toServer(new Packets.Reload());
            }
        }
        while (ClientSetup.INSPECT.consumeClick()) {
            if (held.getItem() instanceof AegisWeaponItem && !WeaponAnimator.HAND.busy()) {
                WeaponAnimator.HAND.inspect();
                Sfx.at(SoundEvents.ARMOR_EQUIP_CHAIN, mc.player.getX(), mc.player.getY() + 1, mc.player.getZ(), 1.2f, 0.5f);
            }
        }
        while (ClientSetup.SWITCH.consumeClick()) {
            Inventory inv = mc.player.getInventory();
            for (int i = 1; i <= 9; i++) {
                int s = (inv.selected + i) % 9;
                if (inv.getItem(s).getItem() instanceof AegisWeaponItem) {
                    inv.selected = s;
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        ScreenFx.frame();
        CinematicDirector.renderTick(e.renderTickTime);
    }

    @SubscribeEvent
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (CinematicDirector.active()) {
            e.setYaw(CinematicDirector.yaw() + ScreenFx.shakeYaw());
            e.setPitch(CinematicDirector.pitch() + ScreenFx.shakePitch());
            e.setRoll(CinematicDirector.roll() + ScreenFx.shakeRoll());
        } else if (ImpactFrames.frozen()) {
            float[] f = ImpactFrames.freeze(e.getYaw(), e.getPitch());
            e.setYaw(f[0]);
            e.setPitch(f[1]);
        } else if (ScreenFx.shakeAmount() > 0.0005f) {
            e.setYaw(e.getYaw() + ScreenFx.shakeYaw());
            e.setPitch(e.getPitch() + ScreenFx.shakePitch());
            e.setRoll(e.getRoll() + ScreenFx.shakeRoll());
        }
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        if (CinematicDirector.active()) e.setFOV(e.getFOV() * CinematicDirector.fovScale());
        if (ImpactFrames.active()) e.setFOV(e.getFOV() * ImpactFrames.fovScale());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        CinematicDirector.reset();
        FxWorld.clear();
        ClientData.FEED.clear();
        ClientData.IMPACTS.clear();
        ClientData.pendingDebrief = null;
        ClientData.targetX = null;
        ClientData.targetZ = null;
    }
}
