package com.geto.csm.item;

import com.geto.csm.server.CsmLogic;
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
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Maximum: Uzumaki — fuse every curse you hold into one supercondensed spiral blast. */
public class UzumakiItem extends Item {

    public UzumakiItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) CsmLogic.uzumaki(sp, this);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Combines every summoned curse + every stored orb").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Damage: 20 + 5 per curse (max 100), 52-block beam").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Special-grade curses in the mix: technique extracted (buffs)").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
