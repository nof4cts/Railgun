package com.hakari.idg;

import com.hakari.idg.client.ClientItemExtensions;
import com.hakari.idg.server.HakariLogic;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

public class TechniqueScrollItem extends Item {
   public final Technique technique;

   public TechniqueScrollItem(Technique technique) {
      super(new Properties().stacksTo(1).rarity(technique == Technique.DOMAIN ? Rarity.EPIC : Rarity.RARE));
      this.technique = technique;
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         HakariLogic.use(sp, this);
      }

      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
   }

   public boolean isFoil(ItemStack stack) {
      return this.technique == Technique.DOMAIN;
   }

   // $VF: One or more variable merging failures!
   // $VF: Could not properly define all variable types!
   // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
      <unknown> var5;
      for (String l : var5_1) {
         tip.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
      }
   }

   public void initializeClient(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(ClientItemExtensions.create());
   }
}
