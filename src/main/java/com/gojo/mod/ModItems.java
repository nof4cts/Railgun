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
   public static final RegistryObject<Item> RED = technique(
      "red", 15, 80, "Reversal: Red - a violent repelling blast that craters and levels everything around where it lands.", Techniques::red
   );
   public static final RegistryObject<Item> MAX_BLUE = technique(
      "max_blue", 35, 400, "Maximum Output: Blue - a vast Blue that drags everything in and tears a trench through the land.", Techniques::maxBlue
   );
   public static final RegistryObject<Item> MAX_RED = technique(
      "max_red", 35, 400, "Maximum Output: Red - Red at full output: a blast that rips a long furrow through everything ahead.", Techniques::maxRed
   );
   public static final RegistryObject<Item> IMAGINARY_PURPLE = technique(
      "imaginary_purple", 50, 1200, "Imaginary Technique: Purple - the incantation, then the imaginary mass he aimed at Toji: a sphere that erases everything it crosses.",
      Techniques::imaginaryPurple
   );
   public static final RegistryObject<Item> HOLLOW_NUKE = technique(
      "hollow_nuke", 60, 2400, "Hollow Nuke - Maximum Blue and Maximum Red crash together in the sky: an imaginary mass detonates over the land.",
      Techniques::hollowNuke
   );
   public static final RegistryObject<Item> DOMAIN_02 = technique(
      "domain_02", 30, 900, "0.2-Second Domain - Unlimited Void for a fifth of a second: everyone around is struck senseless.", Techniques::domain02
   );
   public static final RegistryObject<Item> PURPLE = technique(
      "purple", 40, 300, "Hollow Technique: Purple - Blue in one hand, Red in the other, brought together and flicked: an imaginary mass that erases everything in its path.", Techniques::purple
   );
   public static final RegistryObject<Item> PURPLE_200 = technique(
      "purple_200", 60, 1800, "200% Hollow Purple - Nine Ropes. Polarized Light. Crow and Declaration. Between Front and Back. Purple, at twice its strength.", Techniques::purple200
   );
   public static final RegistryObject<Item> UNLIMITED_VOID = technique(
      "unlimited_void",
      50,
      600,
      "Domain Expansion: Unlimited Void - floods everyone inside with infinite information. Nobody but you can move.",
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
