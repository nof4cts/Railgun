package com.gojo.mod;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class TechniqueItem extends Item {
   private final int cost;
   private final int cooldownTicks;
   private final String description;
   private final TechniqueItem.Technique technique;

   public TechniqueItem(Properties properties, int cost, int cooldownTicks, String description, TechniqueItem.Technique technique) {
      super(properties);
      this.cost = cost;
      this.cooldownTicks = cooldownTicks;
      this.description = description;
      this.technique = technique;
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
         return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
      }

      if (!CursedEnergy.consume(serverPlayer, this.cost)) {
         serverPlayer.displayClientMessage(Component.literal("Not enough cursed energy (" + this.cost + " needed)").withStyle(ChatFormatting.RED), true);
         return InteractionResultHolder.fail(stack);
      } else if (!this.technique.cast(serverLevel, serverPlayer)) {
         CursedEnergy.refund(serverPlayer, this.cost);
         return InteractionResultHolder.fail(stack);
      } else {
         if (this.cooldownTicks > 0) {
            serverPlayer.getCooldowns().addCooldown(this, this.cooldownTicks);
         }

         return InteractionResultHolder.success(stack);
      }
   }

   public boolean castByKey(ServerPlayer serverPlayer) {
      if (serverPlayer.getCooldowns().isOnCooldown(this)) {
         return false;
      } else if (!CursedEnergy.consume(serverPlayer, this.cost)) {
         serverPlayer.displayClientMessage(Component.literal("Not enough cursed energy (" + this.cost + " needed)").withStyle(ChatFormatting.RED), true);
         return false;
      } else if (!this.technique.cast(serverPlayer.serverLevel(), serverPlayer)) {
         CursedEnergy.refund(serverPlayer, this.cost);
         return false;
      } else {
         if (this.cooldownTicks > 0) {
            serverPlayer.getCooldowns().addCooldown(this, this.cooldownTicks);
         }

         return true;
      }
   }

   public boolean isFoil(ItemStack stack) {
      return true;
   }

   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.literal(this.description).withStyle(ChatFormatting.GRAY));
      if (this.cost > 0) {
         tooltip.add(Component.literal("Cost: " + this.cost + " cursed energy").withStyle(ChatFormatting.DARK_AQUA));
      }
   }

   @FunctionalInterface
   public interface Technique {
      boolean cast(ServerLevel var1, ServerPlayer var2);
   }
}
