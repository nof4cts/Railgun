package com.higuruma.mod;

import com.jjk.mod.Balance;
import com.jjk.mod.Energy;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.kashimo.mod.KashimoMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Higuruma {
   public static final int SLOTS = 7;
   public static final int AMP_TICKS = 200;
   public static final int SWORD_TICKS = 1200;
   public static final int CUT = 70;
   public static final int PLEA = 400;
   public static final int DELIB = 50;
   public static final int AFTER = 40;
   public static final int CONFISCATE_TICKS = 12000;
   public static final int LIGHT_HITS = 5;
   private static final Map<UUID, Integer> DOMAINS = new HashMap<>();
   private static final Map<UUID, Integer> LIGHT = new HashMap<>();
   private static final Map<UUID, UUID> SWORD_TARGET = new HashMap<>();
   private static final String[] REMORSE = new String[]{
      "sorry",
      "apolog",
      "didn't mean",
      "didnt mean",
      "did not mean",
      "accident",
      "forgive",
      "regret",
      "mistake",
      "won't do it again",
      "wont do it again",
      "never again",
      "please",
      "my fault",
      "i was wrong",
      "self defense",
      "self-defense",
      "had to",
      "protect"
   };
   public static final int GIANT_WIND = 12;
   private static final int[] COST = new int[]{10, 15, 40, 40, 40, 50, 0};
   private static final int[] COOLDOWN = new int[]{7, 36, 160, 600, 400, 2400, 20};
   private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
   private static final Map<UUID, long[]> COMBO = new HashMap<>();
   private static final Map<UUID, Long> AMP = new HashMap<>();
   private static final Map<UUID, Long> SWORD = new HashMap<>();
   private static final Map<UUID, Long> HEAL = new HashMap<>();
   private static final List<Higuruma.Thrown> THROWN = new ArrayList<>();
   private static final List<Higuruma.Timed> TIMED = new ArrayList<>();
   private static final List<Higuruma.Trial> TRIALS = new ArrayList<>();
   private static int serial;
   private static long now;
   private static final String[] MOB_CHARGES = new String[]{
      "attacking travellers in the dark", "trespassing on another's land", "the destruction of property", "endangering villagers", "lurking with intent"
   };
   private static final Map<UUID, Long> HOLD = new HashMap<>();

   private Higuruma() {
   }

   public static boolean isHiguruma(Player p) {
      return Sorcerers.is(p, Sorcerer.HIGURUMA);
   }

   public static void useKey(ServerPlayer p, int slot) {
      if (slot >= 0 && slot < 7 && p.isAlive() && !p.isSpectator() && isHiguruma(p)) {
         if (trialOf(p) != null) {
            say(p, "Violence is forbidden in the court.", ChatFormatting.GOLD);
         } else if (!hasGavel(p)) {
            say(p, "Hold the Judge's Gavel in your right hand.", ChatFormatting.GOLD);
         } else {
            long[] cd = COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new long[7]);
            if (cd[slot] <= now) {
               boolean sword = swordActive(p);
               if (slot == 0 && sword) {
                  slot = 6;
               }

               if (slot == 6 && !sword) {
                  say(p, "Only a death sentence turns the gavel into the Sword of Light.", ChatFormatting.GRAY);
               } else if (Energy.has(p, COST[slot])) {
                  ServerLevel l = p.serverLevel();

                  boolean ok = switch (slot) {
                     case 0 -> strike(l, p);
                     case 1 -> throwGavel(l, p);
                     case 2 -> giant(l, p);
                     case 3 -> amplify(l, p);
                     case 4 -> rct(l, p);
                     case 5 -> domain(l, p);
                     default -> execute(l, p);
                  };
                  if (ok) {
                     Energy.set(p, Energy.get(p) - COST[slot]);
                     cd[slot] = now + (long)COOLDOWN[slot];
                  }
               }
            }
         }
      }
   }

   static boolean strike(ServerLevel l, ServerPlayer p) {
      long[] c = COMBO.computeIfAbsent(p.getUUID(), k -> new long[]{-1L, 0L});
      c[0] = now - c[1] < 24L ? (c[0] + 1L) % 3L : 0L;
      c[1] = now;
      int step = (int)c[0];
      float size = step == 0 ? 1.0F : (step == 1 ? 1.5F : 2.6F);
      double reach = step == 2 ? 6.0 : 4.2;
      float dmg = step == 2 ? 12.0F : 7.0F;
      Vec3 look = p.getLookAngle();
      p.swing(InteractionHand.MAIN_HAND, true);
      Vec3 step0 = flat(look).scale(step == 2 ? 0.75 : 0.4);
      p.setDeltaMovement(p.getDeltaMovement().add(step0.x, p.onGround() ? 0.05 : 0.0, step0.z));
      p.hurtMarked = true;
      pose(l, p, 1, 8);
      Net.fx(l, new Net.Fx((byte)0, p.position(), look, size, p.getId(), step, 9));
      play(l, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.1F - (float)step * 0.15F);
      Vec3 eye = p.getEyePosition();

      for (LivingEntity e : l.getEntitiesOfClass(
         LivingEntity.class, p.getBoundingBox().inflate(reach + 1.0), ex -> ex != p && ex.isAlive() && !ex.isSpectator()
      )) {
         Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
         if (!(to.length() > reach + (double)(e.getBbWidth() / 2.0F)) && !(to.normalize().dot(look) < 0.35)) {
            hurt(p, e, dmg);
            knock(e, flat(to), step == 2 ? 1.4 : 0.7, step == 2 ? 0.5 : 0.25);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 2, false, false));
            Net.fx(l, new Net.Fx((byte)12, e.getBoundingBox().getCenter(), look, step == 2 ? 1.6F : 1.0F, p.getId(), e.getId(), 0));
            play(l, e.position(), SoundEvents.ANVIL_LAND, 0.35F, 1.7F + (float)step * 0.1F);
            play(l, e.position(), SoundEvents.WOOD_BREAK, 1.0F, 0.6F);
         }
      }

      if (step == 2) {
         Vec3 at = p.position().add(flat(look).scale(3.2));
         int gy = l.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
         if (Math.abs((double)gy - at.y) < 3.0) {
            at = new Vec3(at.x, (double)gy, at.z);
         }

         Vec3 slam = at;
         Vec3 fwd = flat(look);
         TIMED.add(
            new Higuruma.Timed(
               3,
               () -> {
                  Net.fx(l, new Net.Fx((byte)3, slam, fwd, 2.8F, p.getId(), -1, 12));
                  play(l, slam, SoundEvents.ANVIL_LAND, 1.0F, 0.8F);
                  play(l, slam, SoundEvents.GENERIC_EXPLODE, 0.5F, 1.3F);

                  for (LivingEntity ex : l.getEntitiesOfClass(
                     LivingEntity.class, new AABB(slam, slam).inflate(2.8, 2.0, 2.8), exx -> exx != p && exx.isAlive() && !exx.isSpectator()
                  )) {
                     if (ex.position().distanceTo(slam) < 3.2) {
                        hurt(p, ex, 6.0F);
                        ex.setDeltaMovement(ex.getDeltaMovement().add(0.0, 0.55, 0.0));
                        ex.hurtMarked = true;
                     }
                  }
               }
            )
         );
      }

      return true;
   }

   static boolean throwGavel(ServerLevel l, ServerPlayer p) {
      pose(l, p, 1, 8);
      Higuruma.Thrown t = new Higuruma.Thrown(p, l, hand(p), p.getLookAngle().scale(1.75), ++serial);
      THROWN.add(t);
      play(l, p.position(), SoundEvents.TRIDENT_THROW, 1.0F, 0.7F);
      return true;
   }

   static boolean giant(ServerLevel l, ServerPlayer p) {
      Vec3 look = p.getLookAngle();
      Vec3 fwd = flat(look);
      Vec3 at = p.position().add(fwd.scale(4.5));
      int gy = l.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
      if (Math.abs((double)gy - at.y) < 4.0) {
         at = new Vec3(at.x, (double)gy, at.z);
      }

      Vec3 slamAt = at;
      hold(p, 16);
      pose(l, p, 21, 22);
      Net.fx(l, new Net.Fx((byte)2, slamAt, fwd, 1.0F, p.getId(), -1, 12));
      play(l, p.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.5F);
      TIMED.add(
         new Higuruma.Timed(
            12,
            () -> {
               float r = 6.0F;
               Net.fx(l, new Net.Fx((byte)3, slamAt, fwd, r, p.getId(), -1, 20));
               play(l, slamAt, SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
               play(l, slamAt, SoundEvents.GENERIC_EXPLODE, 1.2F, 0.7F);
               play(l, slamAt, (SoundEvent)KashimoMod.SND_SHOCKWAVE.get(), 1.6F, 0.8F);

               for (LivingEntity e : l.getEntitiesOfClass(
                  LivingEntity.class, new AABB(slamAt, slamAt).inflate((double)r, 3.0, (double)r), ex -> ex != p && ex.isAlive() && !ex.isSpectator()
               )) {
                  double d = e.position().distanceTo(slamAt);
                  if (!(d > (double)r + 0.5)) {
                     hurt(p, e, 16.0F * (float)(1.0 - d / (double)(r + 1.0F)) + 4.0F);
                     e.setDeltaMovement(e.getDeltaMovement().add(0.0, 0.7, 0.0));
                     e.hurtMarked = true;
                     e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
                  }
               }

               TIMED.add(
                  new Higuruma.Timed(
                     6,
                     () -> {
                        float r2 = 9.0F;
                        Net.fx(l, new Net.Fx((byte)3, slamAt, fwd, r2, p.getId(), -1, 16));
                        play(l, slamAt, SoundEvents.GENERIC_EXPLODE, 0.9F, 0.5F);

                        for (LivingEntity ex : l.getEntitiesOfClass(
                           LivingEntity.class,
                           new AABB(slamAt, slamAt).inflate((double)r2, 3.0, (double)r2),
                           exx -> exx != p && exx.isAlive() && !exx.isSpectator()
                        )) {
                           double dx = ex.position().distanceTo(slamAt);
                           if (dx > 3.0 && dx < (double)r2 + 0.5) {
                              hurt(p, ex, 5.0F);
                              knock(ex, flat(ex.position().subtract(slamAt)), 1.0, 0.4);
                           }
                        }
                     }
                  )
               );
            }
         )
      );
      return true;
   }

   static boolean amplify(ServerLevel l, ServerPlayer p) {
      AMP.put(p.getUUID(), now + 200L);
      Net.fx(l, new Net.Fx((byte)4, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 200));
      play(l, p.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 0.7F);
      play(l, p.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.8F, 0.6F);
      say(p, "Domain Amplification: enemy techniques are neutralised (10 s).", ChatFormatting.GOLD);
      return true;
   }

   static boolean rct(ServerLevel l, ServerPlayer p) {
      HEAL.put(p.getUUID(), now + 40L);
      p.removeEffect(MobEffects.POISON);
      p.removeEffect(MobEffects.WITHER);
      p.removeEffect(MobEffects.WEAKNESS);
      p.clearFire();
      Net.fx(l, new Net.Fx((byte)5, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 40));
      play(l, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.8F);
      play(l, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.6F);
      return true;
   }

   static boolean execute(ServerLevel l, ServerPlayer p) {
      if ((SWORD_TARGET.containsKey(p.getUUID()) ? l.getEntity(SWORD_TARGET.get(p.getUUID())) : null) instanceof LivingEntity sentenced && sentenced.isAlive()) {
         LivingEntity aim = sentenced.distanceTo(p) < 14.0F ? sentenced : null;
         if (aim != null && (double)aim.distanceTo(p) > 3.5) {
            Vec3 d = flat(aim.position().subtract(p.position()));
            Vec3 to = aim.position().subtract(d.scale(1.8));
            p.connection.teleport(to.x, aim.getY(), to.z, p.getYRot(), p.getXRot());
            Net.fx(l, new Net.Fx((byte)12, to.add(0.0, 1.0, 0.0), d, 1.2F, p.getId(), -1, 0));
            play(l, to, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.6F);
         }

         Vec3 look = p.getLookAngle();
         Vec3 eye = p.getEyePosition();
         pose(l, p, 5, 12);
         p.swing(InteractionHand.MAIN_HAND, true);
         LivingEntity victim = null;

         for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6.0), ex -> ex == sentenced)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            if (to.length() <= 5.5 + (double)(e.getBbWidth() / 2.0F)
               && to.normalize().dot(look) > 0.5
               && (victim == null || to.length() < victim.getBoundingBox().getCenter().distanceTo(eye))) {
               victim = e;
            }
         }

         Vec3 at = victim != null ? victim.getBoundingBox().getCenter() : eye.add(look.scale(3.5));
         boolean lethal = victim != null && LIGHT.getOrDefault(victim.getUUID(), 0) + 1 >= 5;
         Net.fx(l, new Net.Fx((byte)11, at, look, lethal ? 1.0F : 0.0F, p.getId(), victim != null ? victim.getId() : -1, 20));
         play(l, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
         if (victim == null) {
            return true;
         }

         int hits = LIGHT.merge(victim.getUUID(), 1, Integer::sum);
         int left = 5 - hits;
         play(l, at, SoundEvents.AMETHYST_BLOCK_BREAK, 1.6F, 1.6F);
         play(l, at, SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.8F);
         victim.invulnerableTime = 0;
         victim.hurt(l.damageSources().magic(), 0.01F);
         if (left <= 0) {
            play(l, at, SoundEvents.ELDER_GUARDIAN_CURSE, 1.2F, 0.6F);
            LIGHT.remove(victim.getUUID());
            victim.hurt(l.damageSources().genericKill(), Float.MAX_VALUE);
            if (victim.isAlive()) {
               victim.kill();
            }
         } else {
            victim.setHealth(Math.max(0.5F, victim.getHealth() - victim.getMaxHealth() / 5.0F));
         }

         Component msg = Component.literal(
               left <= 0
                  ? "Sword of Light: EXECUTED."
                  : "Sword of Light: " + left + (left == 1 ? " hit" : " hits") + " left on " + victim.getDisplayName().getString()
            )
            .withStyle(ChatFormatting.YELLOW);
         p.displayClientMessage(msg, true);
         if (victim instanceof ServerPlayer vp) {
            vp.displayClientMessage(
               Component.literal(
                     left <= 0
                        ? "The Sword of Light has executed you."
                        : "Sword of Light: " + left + (left == 1 ? " more hit" : " more hits") + " and you die."
                  )
                  .withStyle(ChatFormatting.RED),
               true
            );
         }

         return true;
      }

      dropSword(p);
      return false;
   }

   public static boolean hasGavel(Player p) {
      return p.getMainHandItem().is((Item)HigurumaMod.GAVEL.get());
   }

   static void keepOneGavel(ServerPlayer p) {
      Inventory inv = p.getInventory();
      Item g = (Item)HigurumaMod.GAVEL.get();
      boolean higuruma = isHiguruma(p);
      int keep = -1;
      if (higuruma && inv.getSelected().is(g)) {
         keep = inv.selected;
      }

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack st = inv.getItem(i);
         if (st.is(g)) {
            if (!higuruma) {
               inv.setItem(i, ItemStack.EMPTY);
            } else {
               if (st.getCount() > 1) {
                  st.setCount(1);
               }

               if (keep < 0) {
                  keep = i;
               } else if (i != keep) {
                  inv.setItem(i, ItemStack.EMPTY);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onToss(ItemTossEvent e) {
      if (e.getEntity().getItem().is((Item)HigurumaMod.GAVEL.get()) && e.getPlayer() instanceof ServerPlayer p && isHiguruma(p)) {
         ItemStack st = e.getEntity().getItem().copy();
         e.setCanceled(true);
         if (p.getMainHandItem().isEmpty()) {
            p.setItemInHand(InteractionHand.MAIN_HAND, st);
         } else if (!p.getInventory().add(st)) {
            p.setItemInHand(InteractionHand.OFF_HAND, st);
         }

         say(p, "The gavel stays with you.", ChatFormatting.GOLD);
      }
   }

   static void dropSword(ServerPlayer p) {
      SWORD_TARGET.remove(p.getUUID());
      if (SWORD.remove(p.getUUID()) != null) {
         Net.fx(p.serverLevel(), new Net.Fx((byte)10, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 0));
      }
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent e) {
      UUID dead = e.getEntity().getUUID();

      for (Entry<UUID, UUID> s : new HashMap<>(SWORD_TARGET).entrySet()) {
         if (s.getValue().equals(dead) || s.getKey().equals(dead)) {
            ServerPlayer h = e.getEntity().getServer() == null ? null : e.getEntity().getServer().getPlayerList().getPlayer(s.getKey());
            if (h != null) {
               dropSword(h);
               say(h, "The sentence is carried out. The Sword of Light fades.", ChatFormatting.YELLOW);
            } else {
               SWORD_TARGET.remove(s.getKey());
               SWORD.remove(s.getKey());
            }
         }
      }

      LIGHT.remove(dead);
   }

   public static boolean swordActive(Player p) {
      Long t = SWORD.get(p.getUUID());
      return t != null && t > now;
   }

   static boolean domain(ServerLevel l, ServerPlayer p) {
      LivingEntity d = target(p, 24.0);
      if (d == null) {
         say(p, "There is no defendant. Look at someone.", ChatFormatting.GRAY);
         return false;
      } else if (trialOf(d) != null) {
         say(p, "That one is already on trial.", ChatFormatting.GRAY);
         return false;
      } else {
         Vec3 dir = flat(d.position().subtract(p.position()));
         Vec3 center = p.position().add(dir.scale(4.5));
         Vec3 dpos = center.add(dir.scale(4.5));

         for (int up = 0; up <= 4; up++) {
            if (l.noCollision(d, d.getBoundingBox().move(dpos.x - d.getX(), dpos.y + (double)up - d.getY(), dpos.z - d.getZ()))) {
               dpos = dpos.add(0.0, (double)up, 0.0);
               break;
            }
         }

         Higuruma.Trial t = new Higuruma.Trial(l, p, d, center, dir, p.position(), dpos);
         t.defOrigin = d.position();
         t.prosOrigin = p.position();
         t.second = DOMAINS.merge(p.getUUID(), 1, Integer::sum) >= 2;
         TRIALS.add(t);
         Net.fx(l, new Net.Fx((byte)6, center, dir, 0.0F, p.getId(), d.getId(), 70));
         pose(l, p, 12, 70);
         play(l, center, SoundEvents.BEACON_ACTIVATE, 2.0F, 0.5F);
         play(l, center, (SoundEvent)SoundEvents.AMBIENT_CAVE.value(), 2.0F, 0.6F);
         TIMED.add(new Higuruma.Timed(30, () -> play(l, center, SoundEvents.BELL_BLOCK, 2.5F, 0.6F)));
         TIMED.add(new Higuruma.Timed(52, () -> play(l, center, SoundEvents.BELL_BLOCK, 2.5F, 0.5F)));
         return true;
      }
   }

   static Higuruma.Trial trialOf(LivingEntity e) {
      for (Higuruma.Trial t : TRIALS) {
         if (t.pros == e || t.def == e) {
            return t;
         }
      }

      return null;
   }

   private static String charge(LivingEntity d) {
      if (d instanceof ServerPlayer sp) {
         int kills = sp.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
         int pk = sp.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
         int mined = sp.getStats().getValue(Stats.CUSTOM.get(Stats.ANIMALS_BRED));
         if (pk > 0) {
            return "the killing of " + pk + " " + (pk == 1 ? "person" : "people") + " and " + kills + " creatures";
         } else if (kills > 0) {
            return "the slaughter of " + kills + " creatures";
         } else {
            return mined > 0 ? "meddling with the lives of " + mined + " animals" : "contempt of this court";
         }
      } else {
         return MOB_CHARGES[Math.floorMod(d.getId(), MOB_CHARGES.length)];
      }
   }

   private static void judge(Higuruma.Trial t, String msg) {
      Component c = Component.literal("[Judgeman] ").withStyle(ChatFormatting.GOLD).append(Component.literal(msg).withStyle(ChatFormatting.WHITE));
      t.pros.sendSystemMessage(c);
      if (t.def instanceof ServerPlayer sp) {
         sp.sendSystemMessage(c);
      }
   }

   private static void tickTrial(Higuruma.Trial t, Iterator<Higuruma.Trial> it) {
      long age;
      ServerLevel l;
      boolean var10000;
      label88: {
         age = now - t.start;
         l = t.l;
         label76:
         if (t.pros.isAlive() && t.def.isAlive() && t.pros.level() == l && t.def.level() == l && !t.pros.hasDisconnected()) {
            if (t.def instanceof ServerPlayer sp && sp.hasDisconnected()) {
               break label76;
            }

            var10000 = false;
            break label88;
         }

         var10000 = true;
      }

      boolean gone = var10000;
      if (gone) {
         end(t);
         it.remove();
      } else {
         boolean holding = t.phase < 3 || age < t.verdictAt + 6L;
         if (holding) {
            pin(t.pros, t.prosPos, t.dir);
            pin(t.def, t.defPos, t.dir.scale(-1.0));
         }

         if (age == 70L) {
            t.phase = 1;
            judge(t, "The defendant, " + t.def.getDisplayName().getString() + ", stands accused of " + charge(t.def) + ".");
            if (t.def instanceof ServerPlayer sp) {
               judge(t, "Plead your case.");
               sp.displayClientMessage(Component.literal("Plead your case.").withStyle(ChatFormatting.GOLD), true);
               t.pleaEnd = age + 400L;
            } else {
               judge(t, "The defendant does not speak.");
               t.pleaEnd = age + 50L;
            }

            Net.fx(l, new Net.Fx((byte)7, t.center, t.dir, 1.0F, t.pros.getId(), t.def.getId(), (int)(t.pleaEnd - age)));
         }

         if (t.phase == 1 && age >= t.pleaEnd) {
            t.phase = 2;
            t.verdictAt = age + 50L;
            if (t.plea == 0) {
               judge(t, "The defendant has remained silent. The court will now deliberate.");
            } else {
               judge(t, "The court has heard the defence. Deliberating.");
            }

            Net.fx(l, new Net.Fx((byte)7, t.center, t.dir, 2.0F, t.pros.getId(), t.def.getId(), 50));
            play(l, t.center, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.6F);
         }

         if (t.phase == 2 && age >= t.verdictAt) {
            t.phase = 3;
            verdict(t);
         }

         if (t.phase == 3 && age >= t.verdictAt + 40L) {
            end(t);
            it.remove();
         }
      }
   }

   private static void pin(LivingEntity e, Vec3 at, Vec3 face) {
      float yaw = (float)(Mth.atan2(face.z, face.x) * 180.0F / (float)Math.PI) - 90.0F;
      if (e.position().distanceToSqr(at) > 0.01) {
         if (e instanceof ServerPlayer sp) {
            sp.connection.teleport(at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
         } else {
            e.teleportTo(at.x, at.y, at.z);
            e.setYRot(yaw);
            e.setYHeadRot(yaw);
         }
      }

      e.setDeltaMovement(Vec3.ZERO);
      e.fallDistance = 0.0F;
      if (e instanceof Mob m) {
         m.getNavigation().stop();
         m.setTarget(null);
      }
   }

   private static void verdict(Higuruma.Trial t) {
      ServerLevel l = t.l;
      boolean death = false;
      boolean guilty;
      if (t.second) {
         guilty = true;
         death = true;
      } else {
         double p = Math.max(0.15, 0.6 - (double)t.words * 0.1);
         guilty = l.random.nextDouble() < p;
         death = guilty && l.random.nextDouble() < 0.19;
      }

      int v = !guilty ? 0 : (death ? 2 : 1);
      Net.fx(l, new Net.Fx((byte)8, t.center, t.dir, (float)v, t.pros.getId(), t.def.getId(), 40));
      if (v == 0) {
         judge(t, "NOT GUILTY. The defendant is free to go.");
         play(l, t.center, SoundEvents.BELL_BLOCK, 2.0F, 1.2F);
      } else {
         play(l, t.center, SoundEvents.ANVIL_LAND, 2.0F, 0.6F);
         play(l, t.center, SoundEvents.BELL_BLOCK, 2.5F, 0.5F);
         judge(t, "GUILTY. Confiscation: the defendant's " + (hasTechnique(t.def) ? "cursed technique" : "cursed energy") + " is confiscated.");
         confiscate(t.def);
         if (v == 2) {
            judge(t, "...and by the gravity of these crimes: DEATH PENALTY.");
            DOMAINS.remove(t.pros.getUUID());
            SWORD.put(t.pros.getUUID(), now + 1200L);
            SWORD_TARGET.put(t.pros.getUUID(), t.def.getUUID());
            Net.fx(l, new Net.Fx((byte)10, t.pros.position(), Vec3.ZERO, 0.0F, t.pros.getId(), t.def.getId(), 1200));
            play(l, t.center, SoundEvents.ELDER_GUARDIAN_CURSE, 2.0F, 0.5F);
            play(l, t.center, SoundEvents.WITHER_SPAWN, 0.8F, 1.4F);
            say(t.pros, "The gavel becomes the Sword of Light: 5 cuts kill anyone, any armour (press 1).", ChatFormatting.YELLOW);
         }
      }
   }

   private static boolean hasTechnique(LivingEntity e) {
      if (e instanceof ServerPlayer sp && Sorcerers.get(sp) != Sorcerer.NONE) {
         return true;
      }

      return false;
   }

   private static void confiscate(LivingEntity e) {
      if (e instanceof ServerPlayer sp) {
         if (Sorcerers.get(sp) != Sorcerer.NONE) {
            Balance.confiscate(sp, 12000);
            sp.displayClientMessage(
               Component.literal("Your " + (Sorcerers.get(sp).isAvatar() ? "bending" : "cursed technique") + " has been confiscated for 10 minutes.")
                  .withStyle(ChatFormatting.RED),
               false
            );
         } else {
            Energy.set(sp, 0);
            sp.displayClientMessage(Component.literal("Your cursed energy has been confiscated.").withStyle(ChatFormatting.RED), false);
         }
      } else {
         e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 3, false, true));
         e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 1200, 1, false, true));
      }
   }

   private static void end(Higuruma.Trial t) {
      for (LivingEntity e : new LivingEntity[]{t.pros, t.def}) {
         Vec3 back = e == t.def ? t.defOrigin : t.prosOrigin;
         if (back != null && e.isAlive() && e.level() == t.l && !t.l.noCollision(e, e.getBoundingBox())) {
            if (e instanceof ServerPlayer sp) {
               sp.connection.teleport(back.x, back.y, back.z, sp.getYRot(), sp.getXRot());
            } else {
               e.teleportTo(back.x, back.y, back.z);
            }
         }
      }

      Net.fx(t.l, new Net.Fx((byte)9, t.center, t.dir, 0.0F, t.pros.getId(), t.def.getId(), 0));
      play(t.l, t.center, SoundEvents.BEACON_DEACTIVATE, 1.5F, 0.6F);
   }

   @SubscribeEvent
   public static void onChat(ServerChatEvent e) {
      ServerPlayer p = e.getPlayer();

      for (Higuruma.Trial t : TRIALS) {
         if (t.def == p && t.phase == 1) {
            e.setCanceled(true);
            String msg = e.getRawText().trim();
            String low = msg.toLowerCase();
            Component said = Component.literal("[Defendant] ").withStyle(ChatFormatting.GRAY).append(Component.literal(msg).withStyle(ChatFormatting.WHITE));
            p.sendSystemMessage(said);
            t.pros.sendSystemMessage(said);
            t.plea = 1;

            for (String w : REMORSE) {
               if (low.contains(w) && t.remorse.add(w)) {
                  t.words++;
               }
            }

            return;
         }
      }
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent e) {
      if (e.phase == Phase.START) {
         now = e.getServer().overworld().getGameTime();
      } else {
         for (Higuruma.Timed t : new ArrayList<>(TIMED)) {
            if (--t.left <= 0) {
               TIMED.remove(t);
               t.r.run();
            }
         }

         Iterator<Higuruma.Thrown> it = THROWN.iterator();

         while (it.hasNext()) {
            if (!it.next().tick()) {
               it.remove();
            }
         }

         it = TRIALS.iterator();

         while (it.hasNext()) {
            tickTrial((Higuruma.Trial)it.next(), it);
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent e) {
      if (e.phase == Phase.END && e.player instanceof ServerPlayer p) {
         UUID var10 = p.getUUID();
         if (p.tickCount % 10 == 0) {
            keepOneGavel(p);
         }

         Long h = HEAL.get(var10);
         if (h != null) {
            if (h < now) {
               HEAL.remove(var10);
            } else if (now % 4L == 0L) {
               p.heal(2.0F);
            }
         }

         Long hold = HOLD.get(var10);
         if (hold != null) {
            if (hold < now) {
               HOLD.remove(var10);
            } else {
               p.setDeltaMovement(0.0, Math.min(0.0, p.getDeltaMovement().y), 0.0);
               p.hurtMarked = true;
            }
         }

         Long s = SWORD.get(var10);
         if (s != null && (s < now || !isHiguruma(p))) {
            SWORD.remove(var10);
            Net.fx(p.serverLevel(), new Net.Fx((byte)10, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 0));
         }

         int plea = -1;

         for (Higuruma.Trial t : TRIALS) {
            if (t.def == p && t.phase == 1) {
               plea = (int)Math.max(0L, t.pleaEnd - (now - t.start));
            }
         }

         if (p.tickCount % 2 == 0 && (isHiguruma(p) || plea >= 0)) {
            long[] cd = COOLDOWNS.getOrDefault(var10, new long[7]);
            int[] left = new int[7];

            for (int i = 0; i < 7; i++) {
               left[i] = (int)Math.max(0L, cd[i] - now);
            }

            Long a = AMP.get(var10);
            Net.CHANNEL
               .send(
                  PacketDistributor.PLAYER.with(() -> p),
                  new Net.State(a == null ? 0 : (int)Math.max(0L, a - now), s == null ? 0 : (int)Math.max(0L, s - now), plea, left)
               );
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGH
   )
   public static void onAttack(LivingAttackEvent e) {
      Entity src = e.getSource().getEntity();

      for (Higuruma.Trial t : TRIALS) {
         if (t.phase < 3 && (e.getEntity() == t.pros || e.getEntity() == t.def || src == t.pros || src == t.def)) {
            e.setCanceled(true);
            return;
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOW
   )
   public static void onHurt(LivingHurtEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         Long a = AMP.get(p.getUUID());
         if (a != null && a >= now) {
            Entity src = e.getSource().getEntity();
            boolean technique = src instanceof ServerPlayer sp && Sorcerers.get(sp) != Sorcerer.NONE
               || e.getSource().is(DamageTypeTags.WITCH_RESISTANT_TO)
               || src != null && src != e.getSource().getDirectEntity();
            if (technique) {
               e.setAmount(e.getAmount() * 0.15F);
               Net.fx(p.serverLevel(), new Net.Fx((byte)4, p.position(), Vec3.ZERO, 1.0F, p.getId(), -1, 0));
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent e) {
      UUID id = e.getEntity().getUUID();
      COOLDOWNS.remove(id);
      AMP.remove(id);
      SWORD.remove(id);
      HEAL.remove(id);
   }

   @SubscribeEvent
   public static void onStop(ServerStoppingEvent e) {
      COOLDOWNS.clear();
      AMP.clear();
      SWORD.clear();
      HEAL.clear();
      THROWN.clear();
      TIMED.clear();
      TRIALS.clear();
      HOLD.clear();
   }

   public static void leave(ServerPlayer p) {
      AMP.remove(p.getUUID());
      if (SWORD.remove(p.getUUID()) != null) {
         Net.fx(p.serverLevel(), new Net.Fx((byte)10, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 0));
      }
   }

   private static void hold(ServerPlayer p, int ticks) {
      HOLD.put(p.getUUID(), now + (long)ticks);
   }

   static Vec3 hand(ServerPlayer p) {
      Vec3 look = p.getLookAngle();
      Vec3 r = flat(look).cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      return p.getEyePosition().add(look.scale(0.8)).add(r.scale(0.35)).add(0.0, -0.35, 0.0);
   }

   static Vec3 flat(Vec3 v) {
      Vec3 f = new Vec3(v.x, 0.0, v.z);
      return f.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : f.normalize();
   }

   static LivingEntity target(ServerPlayer p, double range) {
      Vec3 eye = p.getEyePosition();
      Vec3 end = eye.add(p.getLookAngle().scale(range));
      BlockHitResult bh = p.level().clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, p));
      if (bh.getType() != Type.MISS) {
         end = bh.getLocation();
      }

      EntityHitResult eh = ProjectileUtil.getEntityHitResult(
         p.level(), p, eye, end, new AABB(eye, end).inflate(1.5), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
      );
      return eh != null ? (LivingEntity)eh.getEntity() : null;
   }

   static void hurt(ServerPlayer p, LivingEntity e, float dmg) {
      e.invulnerableTime = 0;
      e.hurt(p.damageSources().playerAttack(p), dmg);
   }

   static void knock(LivingEntity e, Vec3 dir, double h, double up) {
      e.setDeltaMovement(e.getDeltaMovement().add(dir.x * h, up, dir.z * h));
      e.hurtMarked = true;
   }

   static void pose(ServerLevel l, ServerPlayer p, int pose, int dur) {
      Net.fx(l, new Net.Fx((byte)13, p.position(), Vec3.ZERO, (float)pose, p.getId(), -1, dur));
   }

   static void play(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch) {
      l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
   }

   static void say(ServerPlayer p, String msg, ChatFormatting c) {
      p.displayClientMessage(Component.literal(msg).withStyle(c), true);
   }

   private static final class Thrown {
      final ServerPlayer owner;
      final ServerLevel l;
      final int serial;
      final Set<Integer> hitOut = new HashSet<>();
      final Set<Integer> hitBack = new HashSet<>();
      Vec3 pos;
      Vec3 vel;
      boolean back;
      int age;

      Thrown(ServerPlayer owner, ServerLevel l, Vec3 pos, Vec3 vel, int serial) {
         this.owner = owner;
         this.l = l;
         this.pos = pos;
         this.vel = vel;
         this.serial = serial;
      }

      boolean tick() {
         this.age++;
         if (this.owner.isAlive() && this.owner.level() == this.l && this.age <= 120) {
            if (this.back) {
               Vec3 to = Higuruma.hand(this.owner).subtract(this.pos);
               if (to.length() < 1.3) {
                  Net.fx(this.l, new Net.Fx((byte)1, this.pos, Vec3.ZERO, 0.0F, this.owner.getId(), this.serial, 0));
                  Higuruma.play(this.l, this.owner.position(), SoundEvents.ITEM_PICKUP, 0.8F, 0.6F);
                  return false;
               }

               this.vel = this.vel.scale(0.6).add(to.normalize().scale(0.75));
            } else if (this.age > 13) {
               this.back = true;
            }

            Vec3 next = this.pos.add(this.vel);
            if (!this.back) {
               BlockHitResult bh = this.l.clip(new ClipContext(this.pos, next, Block.COLLIDER, Fluid.NONE, this.owner));
               if (bh.getType() != Type.MISS) {
                  next = bh.getLocation();
                  this.back = true;
                  this.vel = this.vel.scale(-0.5);
                  Higuruma.play(this.l, next, SoundEvents.WOOD_HIT, 1.4F, 0.6F);
               }
            }

            Set<Integer> hit = this.back ? this.hitBack : this.hitOut;
            Vec3 a = this.pos;
            Vec3 b = next;

            for (LivingEntity e : this.l
               .getEntitiesOfClass(
                  LivingEntity.class, new AABB(a, next).inflate(0.8), ex -> ex != this.owner && ex.isAlive() && !ex.isSpectator() && !hit.contains(ex.getId())
               )) {
               if (!e.getBoundingBox().inflate(0.4).clip(a, b).isEmpty() || e.getBoundingBox().inflate(0.4).contains(a)) {
                  hit.add(e.getId());
                  Higuruma.hurt(this.owner, e, 7.0F);
                  Higuruma.knock(e, Higuruma.flat(this.vel), 0.8, 0.3);
                  e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 2, false, false));
                  Net.fx(this.l, new Net.Fx((byte)12, e.getBoundingBox().getCenter(), this.vel.normalize(), 1.0F, this.owner.getId(), e.getId(), 0));
                  Higuruma.play(this.l, e.position(), SoundEvents.ANVIL_LAND, 0.3F, 1.8F);
                  if (!this.back && this.hitOut.size() >= 3) {
                     this.back = true;
                     this.vel = this.vel.scale(-0.4);
                  }
               }
            }

            this.pos = next;
            Net.fx(this.l, new Net.Fx((byte)1, this.pos, this.vel, 0.0F, this.owner.getId(), this.serial, 1));
            return true;
         } else {
            Net.fx(this.l, new Net.Fx((byte)1, this.pos, Vec3.ZERO, 0.0F, this.owner.getId(), this.serial, 0));
            return false;
         }
      }
   }

   private static final class Timed {
      int left;
      final Runnable r;

      Timed(int left, Runnable r) {
         this.left = left;
         this.r = r;
      }
   }

   private static final class Trial {
      final ServerLevel l;
      final ServerPlayer pros;
      final LivingEntity def;
      final Vec3 center;
      final Vec3 dir;
      final Vec3 prosPos;
      final Vec3 defPos;
      final long start = Higuruma.now;
      int phase;
      int plea;
      int words;
      final Set<String> remorse = new HashSet<>();
      boolean second;
      long pleaEnd = Long.MAX_VALUE;
      long verdictAt = Long.MAX_VALUE;
      Vec3 defOrigin;
      Vec3 prosOrigin;

      Trial(ServerLevel l, ServerPlayer pros, LivingEntity def, Vec3 center, Vec3 dir, Vec3 prosPos, Vec3 defPos) {
         this.l = l;
         this.pros = pros;
         this.def = def;
         this.center = center;
         this.dir = dir;
         this.prosPos = prosPos;
         this.defPos = defPos;
      }
   }
}
