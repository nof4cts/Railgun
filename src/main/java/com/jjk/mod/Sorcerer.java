package com.jjk.mod;

public enum Sorcerer {
   NONE("No Technique", "Non-sorcerer", "無", 10133670, "Fight with your fists. Pick a sorcerer to awaken a cursed technique.", new String[0]),
   GOJO(
      "Satoru Gojo",
      "The Strongest",
      "無下限呪術",
      7328767,
      "The Limitless and the Six Eyes. Bend space itself: attract with Blue, repel with Red, erase with Purple.",
      new String[]{
         "Lapse: Blue",
         "Reversal: Red",
         "Hollow Purple",
         "Teleport",
         "Infinity (toggle)",
         "Domain: Unlimited Void",
         "200% Hollow Purple",
         "Reverse Cursed Technique",
         "Six Eyes Scan",
         "Curtain"
      }
   ),
   SUKUNA(
      "Ryomen Sukuna",
      "King of Curses",
      "伏魔御廚子",
      15022139,
      "Shrine. Invisible slashes that dismantle and cleave anything, fire arrows, and the Malevolent Shrine.",
      new String[]{"Dismantle (hold: Barrage)", "Cleave", "Spiderweb", "Black Flash", "Fuga", "Domain: Malevolent Shrine", "World-Cutting Slash"}
   ),
   RYU(
      "Ryu Ishigori",
      "Granite Blast",
      "高出力",
      4186320,
      "Monstrous cursed energy output, fired from the top of his head: cyan blasts that level everything in front of him.",
      new String[]{"Hair Cannon", "Beam Volley", "Granite Blast", "Redirect Blast", "Reinforcement (toggle)", "Domain Expansion", "Skyfall"}
   ),
   KASHIMO(
      "Hajime Kashimo",
      "God of Lightning",
      "幻獣琥珀",
      9206783,
      "Electric cursed energy, sure-hit lightning and Mythical Beast Amber. Hits build charge on enemies.",
      new String[]{
         "Lightning Strike / Sonic Wave",
         "Plant Nyoi Staff",
         "Sure-Hit / Atomic Discharge",
         "Thunderclap / Singularity",
         "Hollow Wicker Basket",
         "Domain: Palace of the Storm God",
         "Mythical Beast Amber / Final",
         "Thunder Repulsion",
         "Raijin's Fury"
      }
   ),
   CHOSO(
      "Choso",
      "Death Painting",
      "赤血操術",
      14100029,
      "Blood Manipulation. Compress, harden and fire his own poisonous blood. Ultimate: Blood Rain.",
      new String[]{
         "Blood Edge",
         "Blood Meteorite",
         "Piercing Blood",
         "Slicing Exorcism",
         "Crimson Binding",
         "Domain: Kusozu Blood Realm",
         "Flowing Red Scale / Stack",
         "Convergence",
         "ULT: Blood Rain",
         "Supernova"
      }
   ),
   HAKARI(
      "Kinji Hakari",
      "Restless Gambler",
      "坐殺博徒",
      5436042,
      "Idle Death Gamble. Land visuals inside the domain to spin; hit the jackpot for 4:11 of immortality.",
      new String[]{"Reserve Ball", "Shutter Doors", "Rough Energy", "Fever Breaker", "Door Counter", "Domain: Idle Death Gamble"}
   ),
   GETO(
      "Suguru Geto",
      "Curse Control",
      "呪霊操術",
      11832575,
      "Command absorbed curses and four dragons. Saddle a dragon to ride it and use its moves on your move keys; sneak + right-click: sit.",
      new String[]{"Summon Curse", "Maximum: Uzumaki", "Curse Orbs", "Ride a dragon: its moves on keys"}
   ),
   YUKI(
      "Yuki Tsukumo",
      "Star Rage",
      "星の怒り",
      11562239,
      "Bom Ba Ye: she adds virtual mass to herself and her shikigami Garuda without losing speed. Final resort: collapse into a black hole.",
      new String[]{
         "Star Rage Strike",
         "Garuda: Whip",
         "Garuda: Star Rage Ball",
         "Garuda: Bind",
         "Simple Domain",
         "Domain Expansion",
         "Star Rage: Mass Up",
         "Reverse Cursed Technique",
         "FINAL: Black Hole (x2)",
         "Summon Garuda"
      }
   ),
   NAOYA(
      "Naoya Zen'in",
      "Fastest of the Zen'in",
      "投射呪法",
      15913594,
      "Projection Sorcery. Split one second into 24 frames and trace them at impossible speed. Touch someone who can't keep up and they freeze, flat as a single frame.",
      new String[]{
         "Projection Dash",
         "Frame Freeze",
         "24-Frame Barrage",
         "Afterimage Step",
         "Sound Barrier Break",
         "Frame Trap",
         "Frame Shatter",
         "ULT: Mach 3 Strike"
      }
   );

   public final String displayName;
   public final String title;
   public final String kanji;
   public final String description;
   public final int color;
   public final String[] moves;

   private Sorcerer(String displayName, String title, String kanji, int color, String description, String[] moves) {
      this.displayName = displayName;
      this.title = title;
      this.kanji = kanji;
      this.color = color;
      this.description = description;
      this.moves = moves;
   }

   public boolean usesKeys() {
      return this != NONE && this != GETO;
   }

   public static Sorcerer byId(int id) {
      Sorcerer[] v = values();
      return id >= 0 && id < v.length ? v[id] : NONE;
   }
}
