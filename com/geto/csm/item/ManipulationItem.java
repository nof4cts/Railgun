package com.geto.csm.item;

import com.geto.csm.client.ClientHooks;
import com.geto.csm.entity.WyrmEntity;
import com.geto.csm.server.CsmLogic;
import java.util.List;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

public class ManipulationItem extends Item {
   public ManipulationItem() {
      super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (player.isShiftKeyDown()) {
         if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::openSelector);
         }

         return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
      } else {
         if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (sp.getVehicle() instanceof WyrmEntity w && w.ownerId() == sp.getId()) {
               w.riderAttack(sp);
               return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }

            CsmLogic.summon(sp);
         }

         return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
      }
   }

   public boolean isFoil(ItemStack stack) {
      return true;
   }

   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
      tip.add(Component.literal("Right-click: summon the selected curse").withStyle(ChatFormatting.GRAY));
      tip.add(Component.literal("Sneak + right-click: choose a curse").withStyle(ChatFormatting.GRAY));
      tip.add(Component.literal("Saddle a dragon, then right-click it to ride (sneak to dismount)").withStyle(ChatFormatting.GRAY));
      tip.add(Component.literal("Riding: move keys = the dragon's moves, right-click = bite").withStyle(ChatFormatting.GRAY));
      tip.add(Component.literal("Kill monsters while holding this to drop curse orbs").withStyle(ChatFormatting.DARK_PURPLE));
   }
}
