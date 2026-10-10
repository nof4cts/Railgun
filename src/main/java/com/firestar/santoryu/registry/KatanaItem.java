package com.firestar.santoryu.registry;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** One of the swordsman's blades. Techniques count these in the inventory: one, two, three, or Enma. */
public class KatanaItem extends SwordItem {
    public final String blade;
    public final float power;
    public final int color;
    private final String lore;

    public KatanaItem(Tier tier, int damage, float speed, Properties props, String blade, float power, int color, String lore) {
        super(tier, damage, speed, props);
        this.blade = blade;
        this.power = power;
        this.color = color;
        this.lore = lore;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(lore).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tip.add(Component.literal("Technique power x" + power).withStyle(ChatFormatting.DARK_RED));
    }
}
