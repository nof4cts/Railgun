package com.jjk.mod;

public enum Sorcerer {
   NONE("No Technique", "Non-sorcerer", "無", 10133670, "Fight with your fists. Pick a sorcerer to awaken a cursed technique.", new String[0]),
   GOJO(
      "Satoru Gojo",
      "The Strongest",
      "無下限呪術",
      7328767,
      "The Limitless and the Six Eyes. Attract with Blue, repel with Red, erase with Purple; Maximum Output, the 200% Purple, the Hollow Nuke and Unlimited Void.",
      new String[]{
         "Lapse: Blue",
         "Reversal: Red",
         "Hollow Purple (Hanami)",
         "Teleport",
         "Infinity (toggle)",
         "Maximum Output: Blue",
         "Maximum Output: Red",
         "Imaginary Technique: Purple",
         "200% Hollow Purple",
         "Hollow Nuke",
         "Domain: Unlimited Void",
         "0.2-Second Domain",
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
      new String[]{
         "Hair Cannon",
         "Beam Volley",
         "Granite Blast",
         "Redirect Blast",
         "Reinforcement (toggle)",
         "Domain: Grand Fireworks",
         "Skyfall",
         "Punch Volley",
         "Granite Fist"
      }
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
      "Idle Death Gamble. Land visuals inside the domain to spin - every spin has an 8% chance. Hit the JACKPOT for immortality: up to 2:00 of endless healing (less the longer it took), nothing can kill you.",
      new String[]{"Reserve Ball", "Shutter Doors", "Rough Energy", "Fever Breaker", "Door Counter", "Domain: Idle Death Gamble"}
   ),
   GETO(
      "Suguru Geto",
      "Curse Control",
      "呪霊操術",
      11832575,
      "Starts with two dragons (Gloomscale Wyrm, Atroxiia). Kill monsters and other curses, swallow their orbs to collect new curses; pick which go in your quick wheel from the big collection menu. Max 3 curses out. Saddle a dragon to ride it and use its moves on your move keys.",
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
   FIRE(
      "Firebender",
      "Fire Nation",
      "火",
      16738847,
      "Firebending is fuelled by breath. Sharp punches and kicks that throw fire, Iroh's Dragon's Breath, Azula's blue fire and lightning - and the art of redirecting it.",
      new String[]{
         "Fire Blast (hold: charge)",
         "Fire Whip",
         "Fire Daggers",
         "Dragon's Breath",
         "Lightning (hold)",
         "Lightning Redirection",
         "Blue Fire",
         "Cook Food (8 s)"
      }
   ),
   WATER(
      "Waterbender",
      "Water Tribe",
      "水",
      4172287,
      "Waterbending follows the push and pull of the moon. Carry a water bucket and draw the water out (G) to bend it: whips, ice, a fountain shield, healing, a great wave, bloodbending. Stronger in water or rain.",
      new String[]{
         "Draw Water (bucket)",
         "Water Whip",
         "Ice Shards",
         "Water Shield",
         "Healing",
         "Water Wave",
         "Bloodbending",
         "Octopus Form (right-click: lash)",
         "Overhead Water (hold, let go: throw)"
      }
   ),
   EARTH(
      "Earthbender",
      "Earth Kingdom",
      "土",
      8110147,
      "Earthbending is strength and patience: stand your ground, then strike. Raise walls and pillars, hurl boulders, feel every footstep through the earth.",
      new String[]{"Earth Wave", "Boulder Throw", "Earth Pillars", "Rock Gauntlets", "Earth Wall", "Seismic Sense", "Earth Coffin", "Vein Miner (1 min)"}
   ),
   AIR(
      "Airbender",
      "Air Nomads",
      "氣",
      16041008,
      "Airbending is freedom: push, redirect and dodge, rarely striking head-on. Ride the air scooter, glide, and call down a tornado.",
      new String[]{"Air Blast", "Air Slice", "Air Scooter", "Air Shield", "Tornado", "Glider", "Air Dash", "Air Leap"}
   ),
   NAOYA(
      "Naoya Zen'in",
      "Fastest of the Zen'in",
      "投射呪法",
      15913594,
      "Projection Sorcery. Split one second into 24 frames and trace them at impossible speed. Touch someone who can't keep up and they freeze, flat as a single frame.",
      new String[]{
         "Projection Sprint (toggle)",
         "Frame Freeze",
         "24-Frame Barrage",
         "Afterimage Step",
         "Sound Barrier Break",
         "Frame Trap",
         "Frame Shatter",
         "ULT: Mach 3 Strike",
         "Rapid Punches",
         "Domain: Time Cell Moon Palace"
      }
   ),
   HIGURUMA(
      "Hiromi Higuruma",
      "Deadly Sentencing",
      "誅伏賜死",
      14267466,
      "A lawyer turned sorcerer. A gavel that changes size, Domain Amplification, Reverse Cursed Technique - and his domain, a courtroom where Judgeman tries the defendant. Guilty means Confiscation; the death penalty turns his gavel into the Sword of Light.",
      new String[]{
         "Gavel Strike",
         "Gavel Throw",
         "Giant Gavel",
         "Domain Amplification",
         "Reverse Cursed Technique",
         "Domain: Deadly Sentencing",
         "Sword of Light (5 hits)"
      }
   ),
   TEN_SHADOWS(
      "Ten Shadows",
      "Megumi Fushiguro",
      "十種影法術",
      9414344,
      "Shikigami drawn from shadows. Its last resort is the strongest shikigami of all: Eight-Handled Sword Divergent Sila Divine General Mahoraga. Nobody has ever tamed it - the one who summons it dies, and the Ten Shadows is sealed for an hour. Needs another creature nearby to drag into the ritual.",
      new String[]{"Summon Mahoraga (press twice)"}
   ),
   YUTA(
      "Yuta Okkotsu",
      "Special Grade",
      "乙骨憂太",
      8366335,
      "Copy and the Queen of Curses. Rika hunts hostile mobs. Sneak + key = 2nd move.",
      new String[]{
         "Katana",
         "Rika!",
         "Don't Move",
         "Dismantle / Cleave",
         "Ice Breaker / Deflect",
         "Distort / Sky Walk",
         "Clairvoyance",
         "Dhruv",
         "Full Rika",
         "Domain / Ladder",
         "Pure Love (Full Rika)"
      }
   ),
   GER(
      "Golden Experience Requiem",
      "Stand: Requiem",
      "鎮魂歌",
      16763196,
      "A golden Stand at your shoulder. It gives life, returns actions to zero, and kills endlessly.",
      new String[]{"Zero Strike", "Life Creation", "Return to Zero", "Muda Barrage", "Nature's Revenge", "Fated Reflection", "ULT: Infinite Death"}
   ),
   URO(
      "Takako Uro",
      "Sky Manipulation",
      "烏鷺亨子",
      16228824,
      "Space is a flexible, transparent surface: grab it, fold it, break it. Horizon Sweep, Sky Spin, Sky Wrap, Sky Lift are original moves (not canon).",
      new String[]{
         "Sky Distortion",
         "Thin Ice Breaker",
         "Sky Steps",
         "Skyfall (while flying)",
         "Distorted Strike",
         "Sky Deflection",
         "Sky Veil",
         "Horizon Sweep",
         "Sky Spin",
         "Sky Wrap",
         "Sky Lift",
         "Domain: Blue Flame Canopy"
      }
   ),
   KUROURUSHI(
      "Kurourushi",
      "Special Grade Curse",
      "黒漆死",
      12075823,
      "The cockroach curse. Endless swarms crawl around him, surge over his prey, burrow and erupt, take wing. His Festering Life Sword unfolds from his arm, and his ultimate floods the land with roaches and clones of himself.",
      new String[]{
         "Cockroach Swarm",
         "Insect Ambush",
         "Flying Cockroach Assault",
         "Biting Execution",
         "Festering Life Sword",
         "Cockroach Cloud",
         "ULT: Thousandfold Roach Apocalypse"
      }
   ),
   TODO(
      "Aoi Todo",
      "Boogie Woogie",
      "東堂葵",
      5219327,
      "Clap to swap places with anything that has cursed energy. A brawler built around feints, combos and Black Flash. Pebble + clap swaps you to the pebble; press Brute Force again for the Finisher. The Awakening unlocks the last three moves for 80 s: your partner's ghost (pick a player or Yuji below) fights them with you.",
      new String[]{
         "Boogie Woogie",
         "Spin Kick",
         "Pebble Throw",
         "Brute Force / Finisher",
         "Feint Clap",
         "Black Flash",
         "ULT: Awakening",
         "Imagination (Awakened)",
         "Side by Side (Awakened)",
         "Heart Rush (Awakened)"
      }
   ),
   YAGA(
      "Puppet Manipulation",
      "Masamichi Yaga",
      "夜蛾正道",
      13668024,
      "Stitch cursed corpses at a Puppet Assembly Table, charge their cores with your cursed energy and bring them to life. Only Puppet Manipulation users can make, charge and command puppets. Talk to a puppet (right-click) for every command; these keys order all of your puppets at once.",
      new String[]{"All Puppets: Follow Me", "All Puppets: Stay Here", "All Puppets: Come Here", "Charge Cursed Core"}
   ),
   TOJI(
      "Toji Fushiguro",
      "Sorcerer Killer",
      "天与呪縛",
      9068448,
      "Heavenly Restriction: zero cursed energy, a body beyond any sorcerer. The Inventory Curse on his shoulder holds four weapons - the Inverted Spear of Heaven on the Chain of a Thousand Miles, Playful Cloud (hits harder the faster you move), the Split Soul Katana (soul cuts ignore armour) and his pistol. Keys 1-4: the four moves of the weapon in hand. 5: Heavenly Dash (works in mid-air too). 6: Evade. G: Sorcerer Killer (ultimate). H: Domain Breaker (Inverted Spear only). V: cycle weapon. Heaven's Pierce shuts off the target's technique for 3 seconds. Left click chains combos, hold right click for a heavy, sprint + sneak slides, jump into a wall to wall-kick.",
      new String[]{"Heaven's Pierce", "Thousand-Mile Lash", "Chain Hook", "Heavenly Reel"}
   ),
   TEEN_GOJO(
      "Teen Gojo",
      "Hidden Inventory",
      "無下限呪術",
      9427199,
      "Gojo before the awakening: the Limitless, still raw. Lapse: Blue drags everything near into a point; Reversal: Red is flicked from a fingertip and bursts; Blink jumps him up to twelve blocks; Infinity holds for five seconds; Hollow Purple: Unrefined is a small Purple that pierces everything in a line; the 0.2-Second Domain freezes everyone near for three seconds. Each move costs cursed energy and has its own cooldown.",
      new String[]{"Lapse: Blue", "Reversal: Red", "Blink", "Infinity (5s)", "Hollow Purple: Unrefined", "0.2-Second Domain"}
   ),
   JOHNNY(
      "Johnny Joestar",
      "Stand: Tusk",
      "牙",
      15909198,
      "Tusk fires Johnny's spinning fingernails like bullets, growing Act by Act. ACT1: Nail Shot and Finger Cutter. ACT2: the Golden Rectangle nail, whose hole chases its mark. ACT3: shoot yourself into the hole and come out somewhere else. ACT4 needs the Golden Spin - instant on a galloping horse, or spun up on foot: Infinite Rotation pins its target spinning in place, and the ORA ORA rush. Ten nails, which grow back. Keys 1-6.",
      new String[]{"ACT1: Nail Shot", "ACT1: Finger Cutter", "ACT2: Golden Rectangle Nail", "ACT3: Wormhole", "ACT4: Infinite Rotation", "ACT4: ORA ORA Rush"}
   ),
   GYRO(
      "Gyro Zeppeli",
      "The Spin",
      "回転",
      7327639,
      "The Zeppeli Spin and two Steel Balls that return to your hand. Steel Ball: its spin shakes whatever it hits. Wrecking Ball: small spheres that leave a player blind on the left side. Spin Hardening, Spin Healing. Golden Spin (instant on a galloping horse) powers up your throws and unlocks Ball Breaker, which ages whatever it strikes. Keys 1-6."
      ,
      new String[]{"Steel Ball", "Wrecking Ball", "Spin Hardening", "Spin Healing", "Golden Spin", "Ball Breaker"}
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
      return this != NONE && this != GETO && (!this.isAvatar() || this.moves.length > 0);
   }

   public boolean isAvatar() {
      return this.ordinal() >= FIRE.ordinal() && this.ordinal() <= AIR.ordinal();
   }

   public boolean isJojo() {
      return this == GER || this == JOHNNY || this == GYRO;
   }

   public boolean available() {
      return !this.isAvatar() || this.moves.length > 0;
   }

   public static Sorcerer byId(int id) {
      Sorcerer[] v = values();
      return id >= 0 && id < v.length ? v[id] : NONE;
   }
}
