# Cursed Spirit Manipulation (Forge 1.20.1)

Exorcise curses, swallow them as orbs, summon them in battle, ride them, and fuse them all with **Maximum: Uzumaki**.

Every creature is smooth, cel-shaded geometry with ink outlines, built in code with no textures and no block models. No vanilla particles are used anywhere.

## Items (Creative tab: *Cursed Spirit Manipulation*)

| Item | Use |
|---|---|
| **Cursed Spirit Manipulation** | **Right-click** summons the selected curse. **Sneak + right-click** opens the radial selector, where you can also scroll to change the selection. Kill monsters while holding it and they drop **curse orbs**. While riding the Wyrm, right-click to breathe. |
| **Curse Orb** | Hold right-click to swallow it. It's stored and fuels Uzumaki. |
| **Maximum: Uzumaki** | Fuses every summoned curse and every stored orb into a 52-block spiral beam. Damage is 20 + 5 per curse, up to 100. If a special grade is in the mix, you get **Cursed Technique Extracted**: Strength II, Resistance and Regeneration for 90 s. |

You can have 3 summons out at once. Summoning a 4th dismisses the oldest.

## The roster

| Curse | Grade | What it does |
|---|---|---|
| **Gloomscale Wyrm** 冥鱗竜 | Special | An armoured serpent-dragon about 24 blocks long. Its body follows its flight path, and it takes 55% less damage. Attacks: bite (14), tail sweep, dark-fire breath. **Right-click to ride and fly it.** |
| **Maw Burrower** 喰穴蟲 | 1 | Erupts under your target with a cutscene, swallows it whole, grinds it for 3.5 s, then spits it out. |
| **Veil Ray** 帳鱝 | 2 | A gliding manta mount. **Two riders.** It gusts enemies away. |
| **Cinder Centipedes** 煤百足 | 3 | A swarm of five. Venomous bites. |
| **Pyre Wraith** 焚骸 | 1 | Its temperature climbs from **120°C to 3000°C** (shown above its head). Its burning aura grows with the heat, and it has a flare cone attack. |
| **Kuchisake-onna** 口裂け女 | Special (folklore) | Traps a target in a **binding-vow** question: "Am I pretty?" "Yes" makes her drop the mask and ask again. "No" brings the giant shears. "You're... average" makes her hesitate, and you escape. |
| **Tamamo-no-Mae** 玉藻前 | Special (folklore) | A floating nine-tailed fox. Homing foxfire, a nine-tail sweep, and the **Killing Stone**, which crashes down and leaves a withering miasma. |
| **Ōnamazu** 大鯰 | 1 (folklore) | The earthquake catfish. It swims half-sunk in the ground and sends out rolling quake rings that launch enemies. |

## Signature moves

Normal attacks don't flash the screen. Each curse has **one signature move, used about once a minute**, and that's the only move that gets an impact frame:

- Wyrm: dark-fire breath
- Maw Burrower: swallow
- Veil Ray: gust
- Centipedes: venom burst
- Pyre Wraith: flare
- Kuchisake-onna: the binding-vow question
- Tamamo-no-Mae: Killing Stone
- Ōnamazu: quake

## Cataclysm spells (40 s cooldown each)

| Spell | What it does |
|---|---|
| **Falling Star** 流星 | Calls a star down from orbit onto where you look. 7 s cutscene. The impact deals up to 70 damage within 16 blocks, then a fireball, a 32-block mushroom cloud, a shock dome and a burning crater about 19 blocks wide. |
| **Event Horizon** 事象の地平線 | Opens a black hole with an accretion disk and polar jets. It drags in enemies and rips up terrain, then collapses: 50 damage and a spherical crater. |
| **Skyfall Lance** 天墜槍 | Draws a gold sigil in the ground and the sky, then a beam from orbit carves a 36-block molten trench (38 damage). |

Every spell ends in a burst of rapid impact frames and a world split. **Warning: these flash hard.** Terrain damage follows the `mobGriefing` game rule, and chests and other block entities are never destroyed.

## Building

GitHub Actions builds the jar automatically on every push. To build it yourself, install JDK 17 and run `gradlew build`. The jar ends up in `build/libs/`.
