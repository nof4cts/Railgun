package com.higuruma.mod;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class HigurumaMod {
   public static final String MODID = "higuruma";
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "higuruma");
   public static final RegistryObject<Item> GAVEL = ITEMS.register(
      "judges_gavel", () -> new Item(new Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant())
   );

   public HigurumaMod() {
      ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
      Net.register();
   }
}
