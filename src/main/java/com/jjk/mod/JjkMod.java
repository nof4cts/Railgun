package com.jjk.mod;

import com.choso.mod.ChosoMod;
import com.geto.csm.CsmMod;
import com.geto.csm.ModRegistry;
import com.gojo.mod.GojoMod;
import com.hakari.idg.HakariMod;
import com.kashimo.mod.KashimoMod;
import com.ryu.mod.RyuMod;
import com.shrine.ShrineMod;
import com.yuki.mod.YukiMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod("jjk")
public class JjkMod {
   public static final String MODID = "jjk";
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "jjk");
   public static final RegistryObject<CreativeModeTab> TAB = TABS.register(
      "jjk",
      () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.jjk"))
            .icon(() -> new ItemStack((ItemLike)ModRegistry.MANIPULATION.get()))
            .displayItems((params, out) -> {
               out.accept((ItemLike)KashimoMod.NYOI_STAFF.get());
               out.accept((ItemLike)ModRegistry.MANIPULATION.get());
               out.accept((ItemLike)ModRegistry.UZUMAKI.get());
               out.accept((ItemLike)ModRegistry.CURSE_ORB.get());
            })
            .build()
   );

   public JjkMod() {
      IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
      TABS.register(bus);
      JjkNet.register();
      new GojoMod();
      new RyuMod();
      new KashimoMod();
      new ChosoMod();
      new HakariMod();
      new ShrineMod();
      new CsmMod();
      new YukiMod();
      new com.naoya.ps.Naoya();
   }
}
