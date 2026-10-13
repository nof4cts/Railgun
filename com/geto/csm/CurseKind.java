package com.geto.csm;

public enum CurseKind {
   WYRM("Gloomscale Wyrm", "冥鱗竜", "SPECIAL GRADE · VOID DRAGON", -12784641, 24000, 500, true),
   PYRE("Pyre Wraith", "焚骸", "GRADE 1 · 120°C → 3000°C", -42470, 24000, 400, false),
   KUCHISAKE("Kuchisake-onna", "口裂け女", "SPECIAL GRADE · FOLKLORE", -2086852, 24000, 600, true),
   TAMAMO("Tamamo-no-Mae", "玉藻前", "SPECIAL GRADE · FOLKLORE", -868018, 24000, 900, true),
   ATROXIIA("Atroxiia", "氷獄竜", "SPECIAL GRADE · FROST DRAGON", -7550721, 24000, 900, true),
   CINDERVANE("Cindervane", "燼翼竜", "GRADE 1 · FIRE DRAGON", -34253, 24000, 700, true),
   RAEVYX("Raevyx", "嵐角竜", "SPECIAL GRADE · STORM DRAGON", -5008129, 24000, 900, true);

   public final String title;
   public final String jp;
   public final String grade;
   public final int color;
   public final int lifetime;
   public final int cooldown;
   public final boolean special;
   private static final CurseKind[] VALUES = values();

   private CurseKind(String title, String jp, String grade, int color, int lifetime, int cooldown, boolean special) {
      this.title = title;
      this.jp = jp;
      this.grade = grade;
      this.color = color;
      this.lifetime = lifetime;
      this.cooldown = cooldown;
      this.special = special;
   }

   public static CurseKind byId(int id) {
      return VALUES[Math.floorMod(id, VALUES.length)];
   }
}
