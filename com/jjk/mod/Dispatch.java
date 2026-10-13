package com.jjk.mod;

import com.geto.csm.entity.DragonEntity;
import com.gojo.mod.ModItems;
import com.gojo.mod.TechniqueItem;
import com.hakari.idg.ModRegistry;
import com.hakari.idg.TechniqueScrollItem;
import com.hakari.idg.server.HakariLogic;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

public final class Dispatch {
   private Dispatch() {
   }

   public static List<RegistryObject<Item>> gojo() {
      return List.of(
         ModItems.BLUE,
         ModItems.RED,
         ModItems.PURPLE,
         ModItems.TELEPORT,
         ModItems.INFINITY,
         ModItems.UNLIMITED_VOID,
         ModItems.PURPLE_200,
         ModItems.REVERSE_CURSED_TECHNIQUE,
         ModItems.SIX_EYES,
         ModItems.CURTAIN
      );
   }

   public static List<RegistryObject<Item>> ryu() {
      return List.of(
         com.ryu.mod.ModItems.HAIR_CANNON,
         com.ryu.mod.ModItems.BEAM_VOLLEY,
         com.ryu.mod.ModItems.GRANITE_BLAST,
         com.ryu.mod.ModItems.REDIRECT_BLAST,
         com.ryu.mod.ModItems.REINFORCEMENT,
         com.ryu.mod.ModItems.DOMAIN_EXPANSION,
         com.ryu.mod.ModItems.SKYFALL
      );
   }

   public static List<RegistryObject<Item>> hakari() {
      return List.of(
         ModRegistry.RESERVE_BALL, ModRegistry.SHUTTER_DOORS, ModRegistry.ROUGH_ENERGY, ModRegistry.FEVER_BREAKER, ModRegistry.COUNTER, ModRegistry.DOMAIN
      );
   }

   public static Item cooldownItem(Sorcerer s, int slot) {
      List<RegistryObject<Item>> l = switch (s) {
         case GOJO -> gojo();
         case RYU -> ryu();
         case HAKARI -> hakari();
         default -> List.of();
      };
      return slot >= 0 && slot < l.size() ? (Item)l.get(slot).get() : null;
   }

   static void key(ServerPlayer p, int slot) {
      if (p.isAlive() && !p.isSpectator()) {
         if (p.getVehicle() instanceof DragonEntity dragon) {
            dragon.riderMove(p, slot);
         } else {
            Sorcerer s = Sorcerers.get(p);
            Item item = cooldownItem(s, slot);
            if (item != null) {
               switch (s) {
                  case GOJO:
                     ((TechniqueItem)item).castByKey(p);
                     break;
                  case RYU:
                     ((com.ryu.mod.TechniqueItem)item).castByKey(p);
                     break;
                  case HAKARI:
                     if (!p.getCooldowns().isOnCooldown(item)) {
                        HakariLogic.use(p, (TechniqueScrollItem)item);
                     }
               }
            }
         }
      }
   }
}
