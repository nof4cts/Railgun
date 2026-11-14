package com.aegis.command.mission;

import com.aegis.command.item.AegisWeaponItem;
import com.aegis.command.item.WeaponType;
import com.aegis.command.network.AegisNetwork;
import com.aegis.command.network.Packets;
import com.aegis.command.registry.ModItems;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Server handling for the ARSENAL tab: equip, unequip, switch and customise. */
public final class ArsenalService {
    private ArsenalService() {}

    public static void handle(ServerPlayer p, Packets.ArsenalAction a) {
        WeaponType type = WeaponType.byIndex(a.weapon());
        Inventory inv = p.getInventory();
        Item item = ModItems.weapon(type);
        switch (a.action()) {
            case Packets.EQUIP -> {
                if (MissionData.missions(p) < type.unlockMissions && !p.isCreative()) break;
                if (findSlot(inv, item) >= 0) break;
                ItemStack stack = AegisWeaponItem.create(type, MissionData.finish(p, type), MissionData.energy(p, type));
                if (!inv.add(stack)) p.drop(stack, false);
                p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
            case Packets.UNEQUIP -> {
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (inv.getItem(i).is(item)) inv.setItem(i, ItemStack.EMPTY);
                }
            }
            case Packets.SELECT -> {
                int slot = findSlot(inv, item);
                if (slot < 0) break;
                if (Inventory.isHotbarSlot(slot)) {
                    inv.selected = slot;
                } else {
                    ItemStack cur = inv.getItem(inv.selected);
                    inv.setItem(inv.selected, inv.getItem(slot));
                    inv.setItem(slot, cur);
                }
                p.connection.send(new ClientboundSetCarriedItemPacket(inv.selected));
            }
            case Packets.SET_FINISH -> {
                int v = Math.floorMod(a.value(), WeaponType.FINISH_COLORS.length);
                MissionData.setFinish(p, type, v);
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    if (s.is(item)) s.getOrCreateTag().putInt("Finish", v);
                }
            }
            case Packets.SET_ENERGY -> {
                int v = Math.floorMod(a.value(), WeaponType.ENERGY_COLORS.length);
                MissionData.setEnergy(p, type, v);
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    if (s.is(item)) s.getOrCreateTag().putInt("Energy", v);
                }
            }
            default -> {}
        }
        p.inventoryMenu.broadcastChanges();
        AegisNetwork.toPlayer(p, new Packets.SyncData(MissionData.buildSync(p)));
    }

    private static int findSlot(Inventory inv, Item item) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) return i;
        }
        return -1;
    }
}
