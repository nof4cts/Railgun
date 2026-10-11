package com.firestar.orbital.network;

import com.firestar.orbital.common.Cannon;
import com.firestar.orbital.common.Remote;
import com.firestar.orbital.config.OConfig;
import com.firestar.orbital.registry.ModSounds;
import com.firestar.orbital.server.Strikes;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

/** A cannon's key was pressed with the Stellar Remote in hand. */
public record C2SFire(int cannon) {
    public static void encode(C2SFire m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.cannon);
    }

    public static C2SFire decode(FriendlyByteBuf buf) {
        return new C2SFire(buf.readVarInt());
    }

    public static void handle(C2SFire m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            if (p == null || !Remote.holding(p)) {
                return;
            }
            if (OConfig.SERVER.opsOnly.get() && !p.hasPermissions(2)) {
                p.displayClientMessage(Component.literal("Only operators can call the orbital cannons.").withStyle(ChatFormatting.RED), true);
                return;
            }
            if (Strikes.fire(p, Cannon.of(m.cannon))) {
                p.level().playSound(null, p.blockPosition(), ModSounds.ARM.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
