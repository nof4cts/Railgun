package com.shrine.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;

final class Leveler {
   private static final int FLAGS = 50;
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private final ServerLevel level;
   private final BlockPos centre;
   private final int floorY;
   private final List<int[]> columns = new ArrayList<>();
   private int index;
   private int y = Integer.MIN_VALUE;
   private double allowed;
   private final long seed;
   private final int radius;

   Leveler(ServerLevel level, BlockPos centre, int radius, int floorY, long seed) {
      this.seed = seed;
      this.radius = Math.max(1, radius);
      this.level = level;
      this.centre = centre;
      this.floorY = floorY;
      int r2 = radius * radius;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz <= r2) {
               this.columns.add(new int[]{dx, dz, dx * dx + dz * dz});
            }
         }
      }

      this.columns.sort(Comparator.comparingInt(c -> c[2]));
   }

   void allow(double radius) {
      this.allowed = radius;
   }

   /** The domain leaves nothing standing: every block above the floor inside its reach is cut away. */
   private boolean cut(int[] c, int y) {
      return true;
   }

   boolean done() {
      return this.index >= this.columns.size();
   }

   void tick(int budget) {
      int changed = 0;
      int scanned = 0;
      MutableBlockPos pos = new MutableBlockPos();
      double allowed2 = this.allowed * this.allowed;

      while (this.index < this.columns.size() && changed < budget && scanned < budget * 6) {
         int[] c = this.columns.get(this.index);
         if ((double)c[2] > allowed2) {
            return;
         }

         int x = this.centre.getX() + c[0];
         int z = this.centre.getZ() + c[1];
         pos.set(x, this.floorY, z);
         if (!this.level.isLoaded(pos)) {
            this.index++;
            this.y = Integer.MIN_VALUE;
         } else {
            if (this.y == Integer.MIN_VALUE) {
               this.y = this.level.getHeight(Types.WORLD_SURFACE, x, z);
            }

            for (; this.y >= this.floorY && changed < budget; this.y--) {
               pos.set(x, this.y, z);
               scanned++;
               BlockState s = this.level.getBlockState(pos);
               if (!s.isAir() && s.getDestroySpeed(this.level, pos) >= 0.0F && this.cut(c, this.y)) {
                  this.level.setBlock(pos, AIR, 50);
                  changed++;
               }
            }

            if (this.y < this.floorY) {
               this.index++;
               this.y = Integer.MIN_VALUE;
            }
         }
      }
   }
}
