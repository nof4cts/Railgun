package com.cataclysm.spells.item;

import com.cataclysm.spells.server.Spells;
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

public class SpellItem extends Item {
    private final Spells.Spell spell;

    public SpellItem(Spells.Spell spell) {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
        this.spell = spell;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) Spells.cast(sp, this, spell);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        String[] lines = switch (spell) {
            case FALLING_STAR -> new String[]{"Call a star down from orbit onto where you look (64 blocks).", "Up to 70 damage in 16 blocks. Leaves a burning crater."};
            case EVENT_HORIZON -> new String[]{"Open a black hole up to 36 blocks away.", "Drags in enemies and terrain, then collapses (50 damage)."};
            case SKYFALL_LANCE -> new String[]{"A beam from the sky carves a 36-block trench ahead of you.", "38 damage to everything in its path."};
        };
        for (String l : lines) tip.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Terrain damage follows the mobGriefing game rule. 40 s cooldown.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
