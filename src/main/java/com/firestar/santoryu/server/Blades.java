package com.firestar.santoryu.server;

import com.firestar.santoryu.registry.KatanaItem;
import com.firestar.santoryu.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The swordsman's three blades, picked from what he carries: the one in his hand stays in his right hand, Wado goes
 * in his mouth when he has it (it always does), and the third goes in his left hand.
 */
public final class Blades {
    public final int count;
    public final boolean enma;
    public final float power;
    public final ItemStack right;
    public final ItemStack left;
    public final ItemStack mouth;

    private Blades(int count, boolean enma, float power, ItemStack right, ItemStack left, ItemStack mouth) {
        this.count = count;
        this.enma = enma;
        this.power = power;
        this.right = right;
        this.left = left;
        this.mouth = mouth;
    }

    public static Blades of(ServerPlayer p) {
        List<ItemStack> found = new ArrayList<>();
        ItemStack held = p.getMainHandItem();
        if (held.getItem() instanceof KatanaItem) {
            found.add(held);
        }
        ItemStack off = p.getOffhandItem();
        if (off.getItem() instanceof KatanaItem) {
            found.add(off);
        }
        for (ItemStack s : p.getInventory().items) {
            if (s != held && s.getItem() instanceof KatanaItem && found.stream().noneMatch(f -> f.getItem() == s.getItem())) {
                found.add(s);
            }
        }
        // distinct blades only: three copies of one sword are still one sword
        List<ItemStack> distinct = new ArrayList<>();
        for (ItemStack s : found) {
            if (distinct.stream().noneMatch(d -> d.getItem() == s.getItem())) {
                distinct.add(s);
            }
        }
        boolean enma = distinct.stream().anyMatch(s -> s.getItem() == ModItems.ENMA.get());
        float power = 0.0F;
        for (ItemStack s : distinct) {
            power = Math.max(power, ((KatanaItem) s.getItem()).power);
        }
        ItemStack right = held.getItem() instanceof KatanaItem ? held.copy() : ItemStack.EMPTY;
        ItemStack mouth = ItemStack.EMPTY;
        ItemStack left = ItemStack.EMPTY;
        for (ItemStack s : distinct) {
            if (!right.isEmpty() && s.getItem() == right.getItem()) {
                continue;
            }
            if (mouth.isEmpty() && s.getItem() == ModItems.WADO.get()) {
                mouth = s.copy();
            }
        }
        for (ItemStack s : distinct) {
            if (!right.isEmpty() && s.getItem() == right.getItem()) {
                continue;
            }
            if (!mouth.isEmpty() && s.getItem() == mouth.getItem()) {
                continue;
            }
            if (left.isEmpty()) {
                left = s.copy();
            } else if (mouth.isEmpty()) {
                mouth = s.copy();
            }
        }
        return new Blades(distinct.size(), enma, Math.max(power, 1.0F), right, left, mouth);
    }
}
