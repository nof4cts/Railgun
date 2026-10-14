package com.firestar.styles;

import com.firestar.mih.server.StandUsers;
import com.firestar.mih.server.TimeAccel;
import com.firestar.santoryu.registry.ModItems;
import com.firestar.silver.server.Fighters;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Which style each player fights in. Taking up a style switches its kit on (the Stand is granted, the blades handed
 * over); leaving one switches it off (the Stand withdrawn, any awakening let go). Every style's keys, HUD and moves
 * ask here before they act.
 */
@Mod.EventBusSubscriber(modid = StylesMod.MODID)
public final class Styles {
    private static final String TAG = "fightingstyles_style";
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private Styles() {}

    public static Style get(Player p) {
        if (p.level().isClientSide) {
            return com.firestar.styles.client.ClientStyles.of(p);
        }
        return Style.byId(p.getPersistentData().getInt(TAG));
    }

    public static boolean is(Player p, Style s) {
        return p != null && get(p) == s;
    }

    public static void choose(ServerPlayer p, Style s) {
        Style old = get(p);
        if (old == s) {
            return;
        }
        long now = p.serverLevel().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < 30 && !p.isCreative()) {
            p.displayClientMessage(Component.literal("Wait a moment before switching again.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        LAST.put(p.getUUID(), now);
        leave(p, old);
        p.getPersistentData().putInt(TAG, s.ordinal());
        enter(p, s);
        p.displayClientMessage(Component.literal(s == Style.NONE ? "You set your style aside." : "Fighting style: " + s.title)
                .withStyle(st -> st.withColor(s.color)), true);
        broadcast(p);
    }

    private static void leave(ServerPlayer p, Style old) {
        switch (old) {
            case HEAVEN -> {
                TimeAccel.cancel(p);
                if (StandUsers.has(p)) {
                    StandUsers.grant(p, false);
                }
            }
            case INSTINCT -> {
                if (Fighters.form(p) != Fighters.NONE) {
                    Fighters.setForm(p, Fighters.NONE);
                }
            }
            case ROSE -> {
                com.firestar.rose.server.Reaping.cancel(p);
                if (com.firestar.rose.server.Reaper.form(p) != com.firestar.rose.server.Reaper.MORTAL) {
                    com.firestar.rose.server.Reaper.setForm(p, com.firestar.rose.server.Reaper.MORTAL);
                }
            }
            case SAGE -> {
                com.firestar.sage.server.Rites.cancel(p);
                if (com.firestar.sage.server.Sage.form(p) != com.firestar.sage.server.Sage.MORTAL) {
                    com.firestar.sage.server.Sage.setForm(p, com.firestar.sage.server.Sage.MORTAL);
                }
            }
            case SWORDS -> {
                // the three blades go back in their sheaths: out of the hotbar and inventory
                Item[] blades = {ModItems.WADO.get(), ModItems.KITETSU.get(), ModItems.ENMA.get()};
                net.minecraft.world.entity.player.Inventory inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack st = inv.getItem(i);
                    for (Item blade : blades) {
                        if (st.is(blade)) {
                            inv.setItem(i, ItemStack.EMPTY);
                        }
                    }
                }
                p.containerMenu.broadcastChanges();
            }
            default -> {
            }
        }
    }

    private static void enter(ServerPlayer p, Style s) {
        switch (s) {
            case HEAVEN -> {
                if (!StandUsers.has(p)) {
                    StandUsers.grant(p, true);
                }
            }
            case SWORDS -> {
                // the three blades, if he doesn't already carry them
                for (Item blade : new Item[]{ModItems.WADO.get(), ModItems.KITETSU.get(), ModItems.ENMA.get()}) {
                    if (!p.getInventory().contains(new ItemStack(blade))) {
                        ItemStack st = new ItemStack(blade);
                        if (!p.getInventory().add(st)) {
                            p.drop(st, false);
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Called when the Stand DISC is used: that is taking up Made in Heaven. */
    public static void takeUpHeaven(ServerPlayer p) {
        if (get(p) != Style.HEAVEN) {
            leave(p, get(p));
            p.getPersistentData().putInt(TAG, Style.HEAVEN.ordinal());
            broadcast(p);
        }
    }

    static void broadcast(ServerPlayer p) {
        StyleNet.CHANNEL.send(PacketDistributor.ALL.noArg(), new StyleNet.Sync(p.getId(), get(p).ordinal()));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
                StyleNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new StyleNet.Sync(o.getId(), get(o).ordinal()));
            }
            broadcast(p);
            if (get(p) == Style.NONE && !p.getPersistentData().getBoolean("fightingstyles_hinted")) {
                p.getPersistentData().putBoolean("fightingstyles_hinted", true);
                p.displayClientMessage(Component.literal("Press N to choose a fighting style.").withStyle(ChatFormatting.AQUA), false);
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            broadcast(p);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            broadcast(p);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        e.getEntity().getPersistentData().putInt(TAG, e.getOriginal().getPersistentData().getInt(TAG));
        e.getEntity().getPersistentData().putBoolean("fightingstyles_hinted", true);
    }
}
