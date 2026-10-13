package com.gojo.mod;

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
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "gojo");
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "gojo");
   public static final RegistryObject<Item> INFINITY = technique(
      "infinity", 10, 10, "Toggle Infinity: attacks slow to a stop before they reach you.", Techniques::infinity
   );
   public static final RegistryObject<Item> BLUE = technique("blue", 10, 40, "Lapse: Blue - pulls everything toward a point and crushes it.", Techniques::blue);
   public static final RegistryObject<Item> RED = technique("red", 10, 60, "Reversal: Red - a violent repelling blast.", Techniques::red);
   public static final RegistryObject<Item> PURPLE = technique(
      "purple", 40, 200, "Hollow Technique: Purple - Blue and Red collide into imaginary mass that erases everything in its path.", Techniques::purple
   );
   public static final RegistryObject<Item> PURPLE_200 = technique(
      "purple_200", 50, 1200, "200% Hollow Purple - a colossal Purple that tears a canyon through the world.", Techniques::purple200
   );
   public static final RegistryObject<Item> UNLIMITED_VOID = technique(
      "unlimited_void",
      50,
      600,
      "Domain Expansion: Infinite Void - floods everyone around you with infinite information. Nobody but you can move.",
      Techniques::unlimitedVoid
   );
   public static final RegistryObject<Item> TELEPORT = technique("teleport", 10, 10, "Compress space and blink to where you are looking.", Techniques::teleport);
   public static final RegistryObject<Item> REVERSE_CURSED_TECHNIQUE = technique(
      "reverse_cursed_technique", 40, 200, "Fully heal and purge harmful effects.", Techniques::reverseCursedTechnique
   );
   public static final RegistryObject<Item> SIX_EYES = technique(
      "six_eyes", 10, 200, "Use: sense every living thing nearby. Carry: triples cursed energy regen.", Techniques::sixEyes
   );
   public static final RegistryObject<Item> CURTAIN = technique(
      "curtain", 10, 400, "Drop a Curtain: storm, darkness for others, and you vanish.", Techniques::curtain
   );
   public static final RegistryObject<CreativeModeTab> TAB = TABS.register(
      "gojo",
      () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.gojo"))
            .icon(() -> new ItemStack((ItemLike)SIX_EYES.get()))
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
