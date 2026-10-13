package com.geto.csm.item;

import com.geto.csm.server.CsmLogic;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class CurseOrbItem extends Item {
   public CurseOrbItem() {
      super(new Properties().stacksTo(64).rarity(Rarity.UNCOMMON));
   }

   public UseAnim getUseAnimation(ItemStack stack) {
      return UseAnim.EAT;
   }

   public int getUseDuration(ItemStack stack) {
      return 24;
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      player.startUsingItem(hand);
      return InteractionResultHolder.consume(player.getItemInHand(hand));
   }

   public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
      if (!level.isClientSide && entity instanceof ServerPlayer sp) {
         CsmLogic.absorb(sp);
         sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
      }

      if (entity instanceof Player p && p.getAbilities().instabuild) {
         return stack;
      }

      stack.shrink(1);
      return stack;
   }

   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
      tip.add(Component.literal("Hold right-click to swallow and store this curse.").withStyle(ChatFormatting.GRAY));
      tip.add(Component.literal("Stored curses fuel Maximum: Uzumaki.").withStyle(ChatFormatting.DARK_PURPLE));
   }
}
