package com.avatar.mod;

import com.jjk.mod.Energy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

final class Elem {
   private Elem() {
   }

   static boolean pay(ServerPlayer p, int cost) {
      if (!has(p, cost)) {
         return false;
      } else {
         spend(p, cost);
         return true;
      }
   }

   static boolean has(ServerPlayer p, int cost) {
      return Energy.has(p, cost);
   }

   static void spend(ServerPlayer p, int cost) {
      Energy.set(p, Math.max(0, Energy.get(p) - cost));
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

   static BlockPos ground(ServerLevel l, Vec3 at) {
      BlockPos pos = BlockPos.containing(at).above(2);

      for (int i = 0; i < 12; pos = pos.below()) {
         if (l.getBlockState(pos).isFaceSturdy(l, pos, Direction.UP) && l.getBlockState(pos.above()).getCollisionShape(l, pos.above()).isEmpty()) {
            return pos;
         }

         i++;
      }

      return null;
   }

   static BlockState earthOf(ServerLevel l, BlockPos pos) {
      BlockState st = pos == null ? Blocks.STONE.defaultBlockState() : l.getBlockState(pos);
      if (!st.isAir() && l.getBlockEntity(pos) == null && st.isCollisionShapeFullBlock(l, pos) && st.getFluidState().isEmpty()) {
         return st.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : st;
      } else {
         return Blocks.STONE.defaultBlockState();
      }
   }

   static boolean enemy(LivingEntity e, ServerPlayer p) {
      return e != p && e.isAlive() && !e.isSpectator() && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == p);
   }

   static void shieldTick(ServerPlayer p, double r, boolean reflect) {
      ServerLevel l = p.serverLevel();
      Vec3 c = p.position().add(0.0, 1.0, 0.0);

      for (Entity e : l.getEntities(p, new AABB(c, c).inflate(r))) {
         Vec3 d = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0).subtract(c);
         if (!(d.length() > r)) {
            if (e instanceof Projectile) {
               Projectile pr = (Projectile)e;
               if (pr.getOwner() != p) {
                  if (reflect) {
                     pr.setDeltaMovement(d.normalize().scale(Math.max(1.2, pr.getDeltaMovement().length())));
                     pr.setOwner(p);
                     pr.hasImpulse = true;
                  } else {
                     pr.discard();
                  }
                  continue;
               }
            }

            if (e instanceof LivingEntity) {
               LivingEntity le = (LivingEntity)e;
               if (le.isAlive()) {
                  Vec3 out = d.lengthSqr() < 1.0E-4 ? p.getLookAngle() : d.normalize();
                  le.setDeltaMovement(out.x * (reflect ? 0.9 : 0.6), 0.2, out.z * (reflect ? 0.9 : 0.6));
                  le.hurtMarked = true;
               }
            }
         }
      }
   }
}
