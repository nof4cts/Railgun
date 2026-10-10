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

   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
      String[] lines = switch (this.technique) {
         case RESERVE_BALL -> new String[]{"Flick a pachinko reserve ball.", "Point-blank: ragdolls. Range: stuns.", "Counts as a visual inside the domain.", "\u00a76JACKPOT \u2014 Lucky Volley: 12-hit barrage + launcher"};
         case SHUTTER_DOORS -> new String[]{"Two shutter doors slam shut on the target.", "AoE stun. Counts as a visual (hit or miss).", "\u00a76JACKPOT \u2014 Lucky Rushdown: dash grab, launch, spike"};
         case ROUGH_ENERGY -> new String[]{"Wind up, then a sandpaper-rough cursed punch.", "Unblockable. Breaks shields.", "\u00a76JACKPOT \u2014 Overwhelming Luck: grab-run, then throw"};
         case FEVER_BREAKER -> new String[]{"Dash kick. On hit: doors + launching second kick.", "Counts as a visual only if it lands.", "\u00a76JACKPOT \u2014 Energy Surge: leap, crater slam, kick"};
         case COUNTER -> new String[]{"Doors parry the next hit and slam the attacker.", "Counts as a visual when it triggers.", "\u00a76JACKPOT \u2014 Rhythm: resets cooldowns, faster moves"};
         case DOMAIN -> new String[]{"Domain Expansion: Idle Death Gamble.", "Traps everyone within 20 blocks.", "2 visuals = 1 spin (riichi). Odd jackpots raise future odds.", "3 misses \u2192 pity jackpot on the next spin."};
      };
      for (String l : lines) {
         tip.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
      }
   }

   public void initializeClient(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(ClientItemExtensions.create());
   }
}
