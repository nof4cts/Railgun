package com.gojo.mod.cine;

import com.gojo.mod.Techniques;
import com.gojo.mod.cine.client.CineClient;
import com.gojo.mod.fx.Fx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.PacketDistributor.TargetPoint;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Cine {
   public static final byte BLUE = 0;
   public static final byte RED = 1;
   public static final byte PURPLE = 2;
   public static final byte PURPLE200 = 3;
   public static final byte VOID = 4;
   public static final int PURPLE_MERGE = 76;
   public static final int PURPLE_FIRE = 112;
   public static final int PURPLE_END = 150;
   public static final int P200_MERGE = 120;
   public static final int P200_FIRE = 168;
   public static final int P200_END = 215;
   public static final int VOID_OPEN = 72;
   public static final int VOID_LENGTH = 220;
   public static final int VOID_CUT_END = 120;
   public static final double VOID_RADIUS = 22.0;
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "gojo");
   public static final RegistryObject<SoundEvent> SND_SHOCKWAVE = sound("shockwave");
   public static final RegistryObject<SoundEvent> SND_RUMBLE = sound("rumble");
   public static final RegistryObject<SoundEvent> SND_CRACK = sound("blast_crack");
   private static final List<Cine.Running> RUNNING = new ArrayList<>();
   private static final List<Cine.Flight> FLIGHTS = new ArrayList<>();
   public static final double FLICK_SPEED = 2.6;
   private static final List<Cine.Shot> SHOTS = new ArrayList<>();

   public static int travel(Vec3 from, Vec3 to) {
      return Math.max(3, (int)Math.ceil(from.distanceTo(to) / 2.6));
   }

   public static void shoot(ServerLevel level, ServerPlayer caster, byte kind, Vec3 from, Vec3 to) {
      Cine.Packet p = new Cine.Packet(kind, caster.getId(), to, to.subtract(from).normalize(), level.random.nextLong(), from);
      Fx.CHANNEL.send(PacketDistributor.NEAR.with(() -> new TargetPoint(to.x, to.y, to.z, 160.0, level.dimension())), p);
      FLIGHTS.add(new Cine.Flight(level, caster, kind, to, travel(from, to)));
      play(level, from, kind == 0 ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BLAZE_SHOOT, 1.2F, kind == 0 ? 1.4F : 0.8F);
      play(level, from, SoundEvents.FIRECHARGE_USE, 0.8F, kind == 0 ? 1.6F : 0.7F);
   }

   private Cine() {
   }

   private static RegistryObject<SoundEvent> sound(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("gojo", name)));
   }

   public static void register() {
      Fx.CHANNEL
         .registerMessage(1, Cine.Packet.class, Cine.Packet::encode, Cine.Packet::decode, Cine.Packet::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
   }

   public static void start(ServerLevel level, ServerPlayer caster, byte kind, Vec3 pos, Vec3 dir) {
      long seed = level.random.nextLong();
      Cine.Packet p = new Cine.Packet(kind, caster.getId(), pos, dir, seed, pos);
      Fx.CHANNEL.send(PacketDistributor.NEAR.with(() -> new TargetPoint(pos.x, pos.y, pos.z, 160.0, level.dimension())), p);
      if (kind == 2 || kind == 3 || kind == 4) {
         RUNNING.add(new Cine.Running(level, caster, kind, pos, dir));
      }
   }

   public static boolean busy(ServerPlayer p) {
      for (Cine.Running r : RUNNING) {
         if (r.caster == p) {
            return true;
         }
      }

      return false;
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent event) {
      if (event.phase == Phase.END) {
         Iterator<Cine.Running> it = RUNNING.iterator();

         while (it.hasNext()) {
            if (it.next().tick()) {
               it.remove();
            }
         }

         it = FLIGHTS.iterator();

         while (it.hasNext()) {
            if (((Cine.Flight)it.next()).tick()) {
               it.remove();
            }
         }

         it = SHOTS.iterator();

         while (it.hasNext()) {
            if (((Cine.Shot)it.next()).tick()) {
               it.remove();
            }
         }

         Cine.Freeze.tick();
      }
   }

   @SubscribeEvent
   public static void onStop(ServerStoppingEvent event) {
      RUNNING.clear();
      SHOTS.clear();
      FLIGHTS.clear();
      Cine.Freeze.clear();
   }

   private static void play(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
      level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
   }

   private static final class Flight {
      final ServerLevel level;
      final ServerPlayer caster;
      final byte kind;
      final Vec3 to;
      int left;

      Flight(ServerLevel level, ServerPlayer caster, byte kind, Vec3 to, int left) {
         this.level = level;
         this.caster = caster;
         this.kind = kind;
         this.to = to;
         this.left = left;
      }

      boolean tick() {
         if (--this.left > 0) {
            return false;
         } else {
            if (this.kind == 0) {
               Techniques.blueImpact(this.level, this.caster, this.to);
            } else {
               Techniques.redImpact(this.level, this.caster, this.to);
            }

            return true;
         }
      }
   }

   public static final class Freeze {
      private static final UUID SPEED_ID = UUID.fromString("0b1f6a52-5c4e-4c55-9f0e-2f4a7c1e0aa1");
      private static final Map<LivingEntity, long[]> HELD = new HashMap<>();
      private static final Map<LivingEntity, Vec3> AT = new HashMap<>();
      private static long now;

      public static boolean frozen(Entity e) {
         if (e instanceof LivingEntity le && HELD.containsKey(le)) {
            return true;
         }

         return false;
      }

      static void hold(LivingEntity e, int ticks) {
         long[] until = HELD.get(e);
         if (until == null) {
            HELD.put(e, new long[]{now + (long)ticks});
            AT.put(e, e.position());
            AttributeInstance a = e.getAttribute(Attributes.MOVEMENT_SPEED);
            if (a != null && a.getModifier(SPEED_ID) == null) {
               a.addTransientModifier(new AttributeModifier(SPEED_ID, "gojo freeze", -1.0, Operation.MULTIPLY_TOTAL));
            }
         } else {
            until[0] = Math.max(until[0], now + (long)ticks);
         }
      }

      static void tick() {
         now++;
         Iterator<Entry<LivingEntity, long[]>> it = HELD.entrySet().iterator();

         while (it.hasNext()) {
            Entry<LivingEntity, long[]> en = it.next();
            LivingEntity e = en.getKey();
            Vec3 at = AT.get(e);
            if (e.isAlive() && en.getValue()[0] > now) {
               e.setDeltaMovement(0.0, Math.min(0.0, e.getDeltaMovement().y), 0.0);
               if (e instanceof ServerPlayer) {
                  ServerPlayer sp = (ServerPlayer)e;
                  if (sp.position().distanceToSqr(at) > 0.01) {
                     sp.connection.teleport(at.x, Math.min(at.y, sp.getY()), at.z, sp.getYRot(), sp.getXRot());
                  }
               } else if (e instanceof Mob m) {
                  m.getNavigation().stop();
                  m.setTarget(null);
               }
            } else {
               AttributeInstance a = e.getAttribute(Attributes.MOVEMENT_SPEED);
               if (a != null) {
                  a.removeModifier(SPEED_ID);
               }

               AT.remove(e);
               it.remove();
            }
         }
      }

      static void clear() {
         HELD.clear();
         AT.clear();
      }

      @SubscribeEvent
      public static void onAttack(LivingAttackEvent event) {
         if (frozen(event.getSource().getEntity()) && (!(event.getSource().getEntity() instanceof Player p) || !busyCaster(p))) {
            event.setCanceled(true);
         }
      }

      @SubscribeEvent
      public static void onJump(LivingJumpEvent event) {
         if (frozen(event.getEntity())) {
            event.getEntity().setDeltaMovement(0.0, 0.0, 0.0);
         }
      }

      @SubscribeEvent
      public static void onInteract(PlayerInteractEvent event) {
         if (event.isCancelable() && frozen(event.getEntity()) && !busyCaster(event.getEntity())) {
            event.setCanceled(true);
         }
      }

      private static boolean busyCaster(Player p) {
         if (p instanceof ServerPlayer sp && Cine.busy(sp)) {
            return true;
         }

         return false;
      }
   }

   public static final class Packet {
      public final byte kind;
      public final int caster;
      public final Vec3 pos;
      public final Vec3 dir;
      public final Vec3 from;
      public final long seed;

      public Packet(byte kind, int caster, Vec3 pos, Vec3 dir, long seed, Vec3 from) {
         this.from = from;
         this.kind = kind;
         this.caster = caster;
         this.pos = pos;
         this.dir = dir;
         this.seed = seed;
      }

      static void encode(Cine.Packet p, FriendlyByteBuf b) {
         b.writeByte(p.kind);
         b.writeVarInt(p.caster);
         b.writeDouble(p.pos.x);
         b.writeDouble(p.pos.y);
         b.writeDouble(p.pos.z);
         b.writeDouble(p.dir.x);
         b.writeDouble(p.dir.y);
         b.writeDouble(p.dir.z);
         b.writeLong(p.seed);
         b.writeDouble(p.from.x);
         b.writeDouble(p.from.y);
         b.writeDouble(p.from.z);
      }

      static Cine.Packet decode(FriendlyByteBuf b) {
         return new Cine.Packet(
            b.readByte(),
            b.readVarInt(),
            new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
            new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
            b.readLong(),
            new Vec3(b.readDouble(), b.readDouble(), b.readDouble())
         );
      }

      static void handle(Cine.Packet p, Supplier<Context> ctx) {
         Context c = ctx.get();
         c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CineClient.start(p)));
         c.setPacketHandled(true);
      }
   }

   private static final class Running {
      final ServerLevel level;
      final ServerPlayer caster;
      final byte kind;
      final Vec3 pos;
      final Vec3 dir;
      int age;
      final List<LivingEntity> victims = new ArrayList<>();

      Running(ServerLevel level, ServerPlayer caster, byte kind, Vec3 pos, Vec3 dir) {
         this.level = level;
         this.caster = caster;
         this.kind = kind;
         this.pos = pos;
         this.dir = dir;
      }

      boolean tick() {
         this.age++;
         if (this.caster.isAlive() && this.caster.level() == this.level) {
            Vec3 c = this.caster.position();
            if (this.kind != 2 && this.kind != 3) {
               if (this.age < 72) {
                  Cine.Freeze.hold(this.caster, 2);
               }

               if (this.age == 6) {
                  Cine.play(this.level, c, SoundEvents.BEACON_ACTIVATE, 2.0F, 0.5F);
               }

               if (this.age == 40) {
                  Cine.play(this.level, c, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.6F);
               }

               if (this.age == 72) {
                  Cine.play(this.level, c, (SoundEvent)Cine.SND_CRACK.get(), 2.0F, 0.5F);
                  Cine.play(this.level, c, (SoundEvent)Cine.SND_SHOCKWAVE.get(), 2.0F, 1.2F);
                  Cine.play(this.level, c, SoundEvents.END_PORTAL_SPAWN, 2.0F, 0.5F);

                  for (LivingEntity e : this.level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(22.0), LivingEntity::isAlive)) {
                     if (e != this.caster && e.distanceToSqr(c) < 484.0) {
                        this.victims.add(e);
                     }
                  }

                  this.caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 220, 2, false, false));
                  this.caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 220, 1, false, false));
               }

               if (this.age >= 72 && this.age < 292) {
                  int t = this.age - 72;

                  for (LivingEntity ex : this.victims) {
                     if (ex.isAlive()) {
                        Cine.Freeze.hold(ex, 3);
                        if (t % 20 == 10) {
                           ex.hurt(this.level.damageSources().indirectMagic(this.caster, this.caster), 3.0F);
                        }
                     }
                  }

                  if (t % 40 == 0) {
                     Cine.play(this.level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.5F);
                  }
               }

               if (this.age == 292) {
                  Cine.play(this.level, c, SoundEvents.GLASS_BREAK, 2.0F, 0.5F);
                  Cine.play(this.level, c, SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.6F);
               }

               return this.age > 292;
            } else {
               boolean big = this.kind == 3;
               int merge = big ? 120 : 76;
               int fire = big ? 168 : 112;
               if (this.age < fire) {
                  Cine.Freeze.hold(this.caster, 2);
               }

               if (this.age == 4) {
                  Cine.play(this.level, c, SoundEvents.BEACON_POWER_SELECT, 1.5F, 0.5F);
                  Cine.play(this.level, c, SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 1.6F);
               }

               if (this.age == (big ? 40 : 28)) {
                  Cine.play(this.level, c, SoundEvents.BLAZE_SHOOT, 1.5F, 0.5F);
                  Cine.play(this.level, c, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.5F, 0.6F);
               }

               if (this.age > merge - 26 && this.age < merge && this.age % 4 == 0) {
                  Cine.play(this.level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.5F + (float)(this.age - merge + 26) / 20.0F);
               }

               if (this.age == merge) {
                  Cine.play(this.level, c, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.5F);
                  Cine.play(this.level, c, (SoundEvent)Cine.SND_CRACK.get(), 1.5F, 0.6F);
                  Cine.play(this.level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 1.6F);
               }

               if (this.age == merge + 8) {
                  Cine.play(this.level, c, SoundEvents.BEACON_AMBIENT, 2.0F, 0.5F);
                  Cine.play(this.level, c, SoundEvents.END_PORTAL_SPAWN, 1.2F, 0.6F);
               }

               if (this.age == fire) {
                  Cine.play(this.level, c, (SoundEvent)Cine.SND_SHOCKWAVE.get(), 2.5F, big ? 0.6F : 0.85F);
                  Cine.play(this.level, c, (SoundEvent)Cine.SND_RUMBLE.get(), 2.5F, big ? 0.7F : 0.9F);
                  Cine.play(this.level, c, SoundEvents.WARDEN_SONIC_BOOM, 2.5F, 0.5F);
                  Vec3 start = this.caster.getEyePosition().add(this.dir.scale(2.5));
                  Cine.SHOTS.add(new Cine.Shot(this.level, this.caster, start, this.dir, big ? 6 : 3, big ? 150 : 64, big ? 4.0 : 3.0, big));
               }

               return this.age >= (big ? 215 : 150);
            }
         } else {
            return true;
         }
      }
   }

   private static final class Shot {
      final ServerLevel level;
      final ServerPlayer caster;
      final Vec3 start;
      final Vec3 dir;
      final int radius;
      final int range;
      final double speed;
      final boolean big;
      double travelled;
      final Set<Integer> hit = new HashSet<>();

      Shot(ServerLevel level, ServerPlayer caster, Vec3 start, Vec3 dir, int radius, int range, double speed, boolean big) {
         this.level = level;
         this.caster = caster;
         this.start = start;
         this.dir = dir;
         this.radius = radius;
         this.range = range;
         this.speed = speed;
         this.big = big;
      }

      boolean tick() {
         double from = this.travelled;
         this.travelled = Math.min((double)this.range, this.travelled + this.speed);
         MutableBlockPos cur = new MutableBlockPos();
         DamageSource src = this.level.damageSources().indirectMagic(this.caster, this.caster);

         for (double s = from; s <= this.travelled; s++) {
            Vec3 p = this.start.add(this.dir.scale(s));
            int r = this.radius;
            BlockPos c = BlockPos.containing(p);

            for (int dx = -r; dx <= r; dx++) {
               for (int dy = -r; dy <= r; dy++) {
                  for (int dz = -r; dz <= r; dz++) {
                     if (dx * dx + dy * dy + dz * dz <= r * r + 1) {
                        cur.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                        BlockState st = this.level.getBlockState(cur);
                        if (!st.isAir() && st.getDestroySpeed(this.level, cur) >= 0.0F) {
                           this.level.setBlock(cur, Blocks.AIR.defaultBlockState(), 2);
                        }
                     }
                  }
               }
            }

            for (LivingEntity e : this.level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate((double)r + 1.5), LivingEntity::isAlive)) {
               if (e != this.caster && this.hit.add(e.getId())) {
                  e.hurt(src, this.big ? 2000.0F : 1000.0F);
               }
            }
         }

         if (this.travelled >= (double)this.range) {
            if (this.big) {
               Vec3 end = this.start.add(this.dir.scale((double)this.range));
               this.level.explode(this.caster, end.x, end.y, end.z, 9.0F, ExplosionInteraction.BLOCK);
               Cine.play(this.level, end, (SoundEvent)Cine.SND_CRACK.get(), 3.0F, 0.5F);
            }

            return true;
         } else {
            return false;
         }
      }
   }
}
