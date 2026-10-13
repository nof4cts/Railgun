package com.shrine.network;

import com.shrine.client.Casts;
import com.shrine.common.Move;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public final class S2CCast {
   public final Move move;
   public final int casterId;
   public final long startTick;
   public final Vec3 origin;
   public final Vec3 dir;
   public final Vec3 point;
   public final long seed;
   public final float size;
   public final int targetId;
   public final float cutsceneRadius;
   /** Open only: this cast plays the film cutscene first. */
   public final boolean film;

   public S2CCast(Move move, int casterId, long startTick, Vec3 origin, Vec3 dir, Vec3 point, long seed, float size, int targetId, float cutsceneRadius) {
      this(move, casterId, startTick, origin, dir, point, seed, size, targetId, cutsceneRadius, false);
   }

   public S2CCast(Move move, int casterId, long startTick, Vec3 origin, Vec3 dir, Vec3 point, long seed, float size, int targetId, float cutsceneRadius,
                  boolean film) {
      this.film = film;
      this.move = move;
      this.casterId = casterId;
      this.startTick = startTick;
      this.origin = origin;
      this.dir = dir;
      this.point = point;
      this.seed = seed;
      this.size = size;
      this.targetId = targetId;
      this.cutsceneRadius = cutsceneRadius;
   }

   private static Vec3 vec(FriendlyByteBuf b) {
      return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
   }

   private static void vec(FriendlyByteBuf b, Vec3 v) {
      b.writeDouble(v.x);
      b.writeDouble(v.y);
      b.writeDouble(v.z);
   }

   public S2CCast(FriendlyByteBuf b) {
      this(Move.byId(b.readVarInt()), b.readVarInt(), b.readLong(), vec(b), vec(b), vec(b), b.readLong(), b.readFloat(), b.readVarInt(), b.readFloat(),
         b.readBoolean());
   }

   public void encode(FriendlyByteBuf b) {
      b.writeVarInt(this.move.ordinal());
      b.writeVarInt(this.casterId);
      b.writeLong(this.startTick);
      vec(b, this.origin);
      vec(b, this.dir);
      vec(b, this.point);
      b.writeLong(this.seed);
      b.writeFloat(this.size);
      b.writeVarInt(this.targetId);
      b.writeFloat(this.cutsceneRadius);
      b.writeBoolean(this.film);
   }

   public void handle(Supplier<Context> ctx) {
      DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Casts.add(this));
      ctx.get().setPacketHandled(true);
   }
}
