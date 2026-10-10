package com.hakari.idg;

import com.hakari.idg.client.ClientItemExtensions;
import com.hakari.idg.server.HakariLogic;
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
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class TechniqueScrollItem extends Item {
    public final Technique technique;

    public TechniqueScrollItem(Technique technique) {
        super(new Item.Properties().stacksTo(1).rarity(technique == Technique.DOMAIN ? Rarity.EPIC : Rarity.RARE));
        this.technique = technique;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            HakariLogic.use(sp, this);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return technique == Technique.DOMAIN;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        String[] lines = switch (technique) {
            case RESERVE_BALL -> new String[]{"Flick a pachinko reserve ball.", "Point-blank: ragdolls. Range: stuns.", "Counts as a visual inside the domain.", "§6JACKPOT → Lucky Volley: 12-hit barrage + launcher"};
            case SHUTTER_DOORS -> new String[]{"Two shutter doors slam shut on the target.", "AoE stun. Counts as a visual (hit or miss).", "§6JACKPOT → Lucky Rushdown: dash grab, launch, spike"};
            case ROUGH_ENERGY -> new String[]{"Wind up, then a sandpaper-rough cursed punch.", "Unblockable. Breaks shields.", "§6JACKPOT → Overwhelming Luck: grab-run, then throw"};
            case FEVER_BREAKER -> new String[]{"Dash kick. On hit: doors + launching second kick.", "Counts as a visual only if it lands.", "§6JACKPOT → Energy Surge: leap, crater slam, kick"};
            case COUNTER -> new String[]{"Doors parry the next hit and slam the attacker.", "Counts as a visual when it triggers.", "§6JACKPOT → Rhythm: resets cooldowns, faster moves"};
            case DOMAIN -> new String[]{"Domain Expansion: Idle Death Gamble.", "Traps everyone within 20 blocks.", "2 visuals = 1 spin (riichi). Odd jackpots raise future odds.", "3 misses → pity jackpot on the next spin.", "§6JACKPOT: 4:11 of infinite cursed energy + auto-healing."};
        };
        for (String l : lines) tip.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ClientItemExtensions.create());
    }
}
