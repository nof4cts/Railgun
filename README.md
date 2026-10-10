# Restless Gambler — Idle Death Gamble (Forge 1.20.1)

Kinji Hakari's full technique as a Forge mod. Every visual is custom geometry and screen compositing. **No vanilla particles are spawned anywhere.**

## Build the jar (one time)

You need **JDK 17** (Temurin 17 recommended) and an internet connection the first time.

1. Unzip this folder anywhere.
2. Open a terminal in it:
   - **Windows:** `gradlew.bat build`
   - **Mac/Linux:** `./gradlew build`
3. The first build downloads Minecraft + Forge, which takes about 5–10 minutes.
4. Your mod is at `build/libs/hakari-1.0.0.jar`. Drop it into `.minecraft/mods` next to Forge **47.x for 1.20.1**.

To test without installing, run `gradlew runClient` instead. It launches a dev Minecraft with the mod loaded.

## Scrolls (Creative tab: *Restless Gambler*)

Right-click a scroll to use it. Each base scroll turns into its jackpot move while you're in a Jackpot.

| Scroll | Base move | Jackpot move |
|---|---|---|
| Reserve Ball | Flick a chrome pachinko ball. Point-blank it ragdolls; at range it stuns. Counts as a **visual**. | **Lucky Volley**: a 12-hit after-image barrage, then a gold launcher with an impact frame. |
| Shutter Doors | Slatted steel doors slam shut on the target (8 dmg, AoE stun). Counts as a **visual**. | **Lucky Rushdown**: dash, grab, launch, then a teleport spike back into the ground. |
| Rough Energy | Camera cuts to a close-up while sandpaper energy spirals into your fist, then an unblockable 14-dmg punch with an impact frame. | **Overwhelming Luck**: a 2.5 s grab-run cutscene with hits every 8 ticks, then a throw that **cuts the screen in half**. |
| Fever Breaker | Dash kick. On hit, doors burst out, then a launching second kick. Counts as a visual **only if it lands**. | **Energy Surge**: leap cutscene, crater slam (6-block AoE), then a follow-up kick. |
| Door Counter | 0.7 s parry window. If you're hit, doors slam the attacker (9 dmg, 2 s stun). Counts as a visual. | **Rhythm**: resets all cooldowns, cuts cooldowns by 60% for 10 s, and gives you speed. |
| Domain | **Domain Expansion: Idle Death Gamble** (see below). | — |

## The domain

- **9-second cutscene:** eye close-up → hand sign macro shot with an impact frame and 領域展開 slam → black barrier forms with a fresnel rim → white-out → crane sweep through the station as the train rushes past → **title card with the world split in half** → whip up to the slot machine in the sky → back to gameplay.
- Everyone within 20 blocks is trapped. Trapped players get the cutscene too, plus the **rules forced into their mind** (static, typed-out rules), then a 2 s brain-freeze stun.
- **Inside the domain:** a magenta dome, a station with platforms, rails, a canopy and signs, a 4-car train every 14 s, a three-reel slot machine with real spinning reels and chasing marquee bulbs, 72 pachinko balls raining and bouncing, light pillars, spotlights, and floating 坐殺博徒 kanji.
- **2 visuals = 1 spin (riichi).** The riichi overlay shows the reels, リーチ!, the scenario (Transit Card / Seat Struggle / Potty Emergency) and the hype tier (green / red / gold / rainbow). Then you get 大当たり or ハズレ.
- After **3 misses**, the next spin is a guaranteed **pity jackpot**, which lasts half as long.
- An **odd** jackpot means 確変 Probability Up: your next domain has doubled odds.
- If you don't hit within 60 s, the domain burns out: a 45 s domain cooldown, and your moves go on cooldown.

## Jackpot

- **5.5-second cutscene:** reels slam 7-7-7 with an impact frame each → **gold world-split** → spiral crane as coins and a light pillar erupt → giant **4:11** timer card.
- **4:11 of:** auto-healing at 20 HP/s, immunity to death, cleansing, and no hunger. Your moves become the jackpot kit, and their cooldowns are reset.
- A golden helix aura, steam and sparks surround you, and a 4:11 HUD clock counts down.
- When it ends, the domain is instantly ready to cast again.

## Tuning

All numbers are in `server/HakariLogic.java` (odds, durations) and `server/Moves.java` (damage, cooldowns).
