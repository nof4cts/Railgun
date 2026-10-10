package com.geto.csm.item;

import com.geto.csm.entity.WyrmEntity;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The manipulator's focus. Right-click: summon. Sneak + right-click: pick a curse. While riding the wyrm: breath. */
public class ManipulationItem extends Item {

    public ManipulationItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.geto.csm.client.ClientHooks::openSelector);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (sp.getVehicle() instanceof WyrmEntity w && w.ownerId() == sp.getId()) {
                w.riderAttack(sp);
            } else {
                CsmLogic.summon(sp);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Right-click: summon the selected curse").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Sneak + right-click: choose a curse").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Right-click a Wyrm or Ray: ride it (sneak to dismount)").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("While riding the Wyrm: right-click to breathe").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Kill monsters while holding this to drop curse orbs").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
