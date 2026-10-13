package com.avatar.mod;

public final class FxKind {
   public static final byte POSE = 0;
   public static final byte CHARGE = 1;
   public static final byte CHARGED = 2;
   public static final byte BLAST = 3;
   public static final byte IMPACT = 4;
   public static final byte FADE = 5;
   public static final byte FIZZLE = 6;
   public static final byte CINE = 7;
   public static final byte WHIP = 8;
   public static final byte BREATH = 9;
   public static final byte LCHARGE = 10;
   public static final byte LFULL = 11;
   public static final byte BOLT = 12;
   public static final byte LCINE = 13;
   public static final byte BCINE = 14;
   public static final byte BLUE = 15;
   public static final byte REDIRECT = 16;
   public static final byte STANCE = 17;
   public static final byte BLUECINE = 18;
   public static final byte WWHIP = 19;
   public static final byte SHIELD = 20;
   public static final byte HEAL = 21;
   public static final byte WAVE = 22;
   public static final byte BLOOD = 23;
   public static final byte OCTO = 24;
   public static final byte EWAVE = 25;
   public static final byte RISE = 26;
   public static final byte DUST = 27;
   public static final byte GAUNT = 28;
   public static final byte SENSE = 29;
   public static final byte COFFIN = 30;
   public static final byte ABLAST = 31;
   public static final byte SCOOTER = 32;
   public static final byte TORNADO = 33;
   public static final byte GLIDE = 34;
   public static final byte DASH = 35;
   public static final byte ECUT = 36;
   public static final byte DRAW = 37;
   public static final int E_FIRE = 0;
   public static final int E_WATER = 1;
   public static final int E_ROCK = 2;
   public static final int E_AIR = 3;
   public static final int E_ICE = 4;
   public static final int CUT_WAVE = 1;
   public static final int CUT_BLOOD = 2;
   public static final int CUT_OCTO = 3;
   public static final int CUT_COFFIN = 4;
   public static final int CUT_TORNADO = 5;
   public static final int F_BLUE = 1;
   public static final int F_IMPACT = 2;
   public static final int POSE_PUNCH_R = 1;
   public static final int POSE_PUNCH_L = 2;
   public static final int POSE_KICK = 3;
   public static final int POSE_CHARGE = 4;
   public static final int POSE_THRUST = 5;
   public static final int POSE_WHIP = 6;
   public static final int POSE_INHALE = 7;
   public static final int POSE_BREATH = 8;
   public static final int POSE_LGEN = 9;
   public static final int POSE_LAIM = 10;
   public static final int POSE_REDIRECT = 11;
   public static final int POSE_BLUE = 12;
   public static final int POSE_LASH = 13;
   public static final int POSE_STOMP = 14;
   public static final int POSE_HEAL = 15;
   public static final int POSE_BLOOD = 16;
   public static final int POSE_SPIN = 17;
   public static final int POSE_SCOOTER = 18;
   public static final int POSE_GLIDE = 19;
   public static final int POSE_LIFT = 20;
   public static final int POSE_HAMMER = 21;

   public static int elem(int flags) {
      return flags >> 4 & 7;
   }

   private FxKind() {
   }
}
