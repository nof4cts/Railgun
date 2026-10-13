package com.ryu.mod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "ryu");
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "ryu");
   public static final RegistryObject<Item> GRANITE_BLAST = technique(
      "granite_blast", 40, 100, "Full-power discharge: a massive cyan beam that levels everything in its path.", Techniques::graniteBlast
   );
   public static final RegistryObject<Item> HAIR_CANNON = technique(
      "hair_cannon", 10, 6, "Quick cyan shot from the pompadour. Hands stay free.", Techniques::hairCannon
   );
   public static final RegistryObject<Item> BEAM_VOLLEY = technique(
      "beam_volley", 10, 40, "Beam Control: split the discharge into a five-beam spread.", Techniques::beamVolley
   );
   public static final RegistryObject<Item> REDIRECT_BLAST = technique(
      "redirect_blast", 40, 40, "Beam Control: a blast that bends mid-flight into the nearest target you face.", Techniques::redirectBlast
   );
   public static final RegistryObject<Item> DOMAIN_EXPANSION = technique(
      "domain_expansion", 50, 1200, "Beams rain on every foe within 20 blocks for 10s. No burnout: techniques are free during it.", Techniques::domainExpansion
   );
   public static final RegistryObject<Item> SKYFALL = technique(
      "skyfall", 50, 600, "A beam into the sky that falls back down on your target 10 seconds later.", Techniques::skyfall
   );
   public static final RegistryObject<Item> REINFORCEMENT = technique(
      "reinforcement", 10, 10, "Toggle Cursed Energy Reinforcement: stronger hits, heavy knockback, kinetic shield.", Techniques::reinforcement
   );
   public static final RegistryObject<CreativeModeTab> TAB = TABS.register(
      "ryu",
      () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ryu"))
            .icon(() -> new ItemStack((ItemLike)GRANITE_BLAST.get()))
            .displayItems((params, output) -> ITEMS.getEntries().forEach(entry -> output.accept((ItemLike)entry.get())))
            .build()
   );

   private ModItems() {
   }

   private static RegistryObject<Item> technique(String name, int cost, int cooldownTicks, String description, TechniqueItem.Technique technique) {
      return ITEMS.register(
         name, () -> new TechniqueItem(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), cost, cooldownTicks, description, technique)
      );
   }
}
