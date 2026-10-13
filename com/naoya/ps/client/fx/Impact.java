package com.naoya.ps.client.fx;

public final class Impact {
   public static final int INK = 1;
   public static final int INVERT = 2;
   public static final int CRIMSON = 3;
   public static final int NEGATIVE = 4;
   public static final int WHITEOUT = 5;
   public static final int HALFTONE = 6;
   public static final int HAKI = 7;
   public static final int SLASHED = 8;
   public static final int LINES = 9;
   public static final int[][] LIGHT = new int[][]{{1, 1}, {2, 1}};
   public static final int[][] HEAVY = new int[][]{{5, 1}, {1, 1}, {2, 1}, {3, 2}, {9, 1}};
   public static final int[][] IAI = new int[][]{{5, 1}, {8, 2}, {2, 1}, {1, 1}, {8, 1}, {2, 2}};
   public static final int[][] ULTIMATE = new int[][]{{5, 1}, {1, 1}, {2, 1}, {8, 1}, {3, 1}, {2, 1}, {9, 2}, {4, 1}, {1, 1}, {6, 1}, {5, 1}, {3, 2}};
   public static final int[][] KING = new int[][]{
      {5, 1}, {7, 1}, {2, 1}, {8, 1}, {7, 1}, {3, 1}, {2, 1}, {7, 2}, {4, 1}, {9, 1}, {6, 1}, {5, 1}, {7, 2}, {1, 1}
   };

   private Impact() {
   }

   public static int length(int[][] seq) {
      int n = 0;

      for (int[] s : seq) {
         n += s[1];
      }

      return n;
   }
}
