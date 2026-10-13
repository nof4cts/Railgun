package com.jjk.mod;

import com.jjk.mod.client.ClientSorcerers;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.simple.SimpleChannel;

public final class JjkNet {
   private static final String V = "1";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("jjk", "main"), () -> "1", "1"::equals, "1"::equals);

   private JjkNet() {
   }

   static void register() {
      CHANNEL.registerMessage(
         0,
         JjkNet.Choose.class,
         (m, b) -> b.writeVarInt(m.id),
         b -> new JjkNet.Choose(b.readVarInt()),
         JjkNet.Choose::handle,
         Optional.of(NetworkDirection.PLAY_TO_SERVER)
      );
      CHANNEL.registerMessage(
         1,
         JjkNet.Key.class,
         (m, b) -> b.writeByte(m.slot),
         b -> new JjkNet.Key(b.readByte()),
         JjkNet.Key::handle,
         Optional.of(NetworkDirection.PLAY_TO_SERVER)
      );
      CHANNEL.registerMessage(2, JjkNet.Sync.class, (m, b) -> {
         b.writeVarInt(m.entity);
         b.writeVarInt(m.id);
      }, b -> new JjkNet.Sync(b.readVarInt(), b.readVarInt()), JjkNet.Sync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(
         3,
         JjkNet.EnergySync.class,
         (m, b) -> b.writeVarInt(m.value),
         b -> new JjkNet.EnergySync(b.readVarInt()),
         JjkNet.EnergySync::handle,
         Optional.of(NetworkDirection.PLAY_TO_CLIENT)
      );
   }

   public static record Choose(int id) {
      static void handle(JjkNet.Choose m, Supplier<Context> ctx) {
         ServerPlayer p = ctx.get().getSender();
         ctx.get().enqueueWork(() -> {
            if (p != null) {
               Sorcerers.choose(p, Sorcerer.byId(m.id));
            }
         });
         ctx.get().setPacketHandled(true);
      }
   }

   public static record EnergySync(int value) {
      public static int clientValue = 1000;

      static void handle(JjkNet.EnergySync m, Supplier<Context> ctx) {
         ctx.get().enqueueWork(() -> clientValue = m.value);
         ctx.get().setPacketHandled(true);
      }
   }

   public static record Key(int slot) {
      static void handle(JjkNet.Key m, Supplier<Context> ctx) {
         ServerPlayer p = ctx.get().getSender();
         ctx.get().enqueueWork(() -> {
            if (p != null && Balance.used(p, m.slot)) {
               Dispatch.key(p, m.slot);
            }
         });
         ctx.get().setPacketHandled(true);
      }
   }

   public static record Sync(int entity, int id) {
      static void handle(JjkNet.Sync m, Supplier<Context> ctx) {
         ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSorcerers.set(m.entity, Sorcerer.byId(m.id))));
         ctx.get().setPacketHandled(true);
      }
   }
}
