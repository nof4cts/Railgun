package com.avatar.mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class TempBlocks {
   private static final Map<String, TempBlocks.T> BLOCKS = new HashMap<>();

   private TempBlocks() {
   }

   static boolean place(ServerLevel l, BlockPos pos, BlockState st, long until) {
      BlockState old = l.getBlockState(pos);
      return old.canBeReplaced() && old.getFluidState().isEmpty() ? set(l, pos, st, until, old) : false;
   }

   static boolean carve(ServerLevel l, BlockPos pos, BlockState st, long until) {
      BlockState old = l.getBlockState(pos);
      return l.getBlockEntity(pos) == null && !(old.getDestroySpeed(l, pos) < 0.0F) && old.getFluidState().isEmpty() ? set(l, pos, st, until, old) : false;
   }

   private static boolean set(ServerLevel l, BlockPos pos, BlockState st, long until, BlockState old) {
      String key = l.dimension().location() + "|" + pos.asLong();
      TempBlocks.T t = BLOCKS.get(key);
      if (t != null) {
         old = t.old;
      }

      BLOCKS.put(key, new TempBlocks.T(l, pos.immutable(), old, until));
      l.setBlock(pos, st, 3);
      return true;
   }

   static void tick(long now) {
      Iterator<TempBlocks.T> it = BLOCKS.values().iterator();

      while (it.hasNext()) {
         TempBlocks.T t = it.next();
         if (t.until <= now) {
            t.l.setBlock(t.pos, t.old, 3);
            it.remove();
         }
      }
   }

   static void restoreAll() {
      for (TempBlocks.T t : BLOCKS.values()) {
         t.l.setBlock(t.pos, t.old, 3);
      }

      BLOCKS.clear();
   }

   private static record T(ServerLevel l, BlockPos pos, BlockState old, long until) {
   }
}
