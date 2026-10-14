package com.shrine.common;

public final class Timing {
   public static final int DISMANTLE_CUT = 2;
   public static final int DISMANTLE_END = 30;
   public static final int BARRAGE_FIRST = 3;
   public static final int BARRAGE_EVERY = 2;
   public static final int BARRAGE_COUNT = 16;
   public static final int BARRAGE_END = 55;
   public static final int CLEAVE_HIT = 3;
   public static final int CLEAVE_END = 30;
   public static final int WEB_HIT = 7;
   public static final int WEB_END = 50;
   public static final int FLASH_HIT = 4;
   public static final int FLASH_END = 30;
   public static final int FUGA_IGNITE = 0;
   public static final int FUGA_CARD = 8;
   public static final int FUGA_BOW = 30;
   public static final int FUGA_DRAW = 48;
   public static final int FUGA_RELEASE = 80;
   public static final int FUGA_FLIGHT = 9;
   public static final int FUGA_IMPACT = 89;
   public static final int FUGA_END = 175;
   /** Open with its film: the cutscene (the author's Blender film, 1483 frames at 30 fps) runs this many ticks; the blast lands the tick it ends. */
   public static final int FUGA_FILM = 989;
   /** After the blast its crater burns: ticks the flames stand and hurt whoever walks in them. */
   public static final int FUGA_BURN = 600;
   /** In the film version every Fuga beat is shifted so the impact (tick 89) lands on the film's last tick. */
   public static final int FUGA_FILM_SHIFT = FUGA_FILM - FUGA_IMPACT;

   public static int end(Move m, boolean film) {
      return film && m == Move.FUGA ? FUGA_END + FUGA_FILM_SHIFT : end(m);
   }
   public static final int DOMAIN_SIGN = 0;
   public static final int DOMAIN_CARD = 26;
   public static final int DOMAIN_NAME = 40;
   public static final int DOMAIN_OPEN = 52;
   public static final int DOMAIN_SPREAD = 40;
   public static final int DOMAIN_CUT_END = 150;
   public static final int WS_SIGN1 = 0;
   public static final int WS_SIGN2 = 18;
   public static final int WS_SIGN3 = 36;
   public static final int WS_AIM = 54;
   public static final int WS_SLASH = 78;
   public static final int WS_SPLIT = 92;
   public static final int WS_END = 200;

   private Timing() {
   }

   public static int end(Move m) {
      return switch (m) {
         case DISMANTLE -> 30;
         case BARRAGE -> 55;
         case CLEAVE -> 30;
         case SPIDERWEB -> 50;
         case BLACK_FLASH -> 30;
         case FUGA -> 175;
         case DOMAIN -> 2452;
         case WORLD_SLASH -> 200;
      };
   }
}
