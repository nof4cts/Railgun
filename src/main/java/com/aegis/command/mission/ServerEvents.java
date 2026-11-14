package com.aegis.command.mission;

import com.aegis.command.AegisCommand;
import net.minecraft.nbt.Tag;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AegisCommand.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    /** Keep mission history and weapon preferences through death and dimension travel. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        var old = e.getOriginal().getPersistentData();
        if (old.contains(MissionData.KEY, Tag.TAG_COMPOUND)) {
            e.getEntity().getPersistentData().put(MissionData.KEY, old.getCompound(MissionData.KEY).copy());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        StrikeManager.forget(e.getEntity().getUUID());
    }
}
