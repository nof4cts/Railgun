# Six Paths (Forge 1.20.1)

The Rinnegan's techniques as a Minecraft mod. It has 17 techniques across the six paths, plus a transformation, an ultimate revival and four summons. Every big move comes with a cinematic cutscene, shader impact frames, manga panels and real terrain destruction.

All visuals are procedural, cel-shaded geometry with ink outlines. The mod uses no vanilla particles and no block models for its creatures.

## Install

1. Install **Minecraft Forge 1.20.1** (47.x).
2. Put `sixpaths-1.0.0.jar` in your `.minecraft/mods` folder. The mod has no other dependencies.
3. Launch the game. In Creative, open the **Six Paths** tab and take the **Six Paths Focus**. In Survival, use `/give @s sixpaths:path_focus`.

## Controls

| Input | What it does |
|---|---|
| Right-click with the Focus | Uses the selected technique |
| Sneak + right-click | Opens the technique board (click a card to select it) |
| Sneak + scroll | Cycles through techniques without opening the board |

While you hold the Focus, a HUD in the bottom-left shows the selected technique and its cooldown.

## Techniques

| Technique | Path | What it does | Cooldown |
|---|---|---|---|
| **Almighty Push** 神羅天征 | Deva | Repels everything within 9 blocks (14 while Ascended) for 10 damage, or 16 while Ascended. Reflects projectiles back at whoever fired them and blows away soft terrain. Has an impact frame. | 5 s |
| **Universal Pull** 万象天引 | Deva | Drags the target you're looking at (up to 32 blocks) into your hand, then impales it on a receiver for 9 damage and a stun. Has an impact frame and manga panels. | 6 s |
| **Chakra Receivers** | All | Throws a fan of 3 black rods. Each hit deals 6 damage, stuns for 3 s and applies Weakness. | 3.5 s |
| **Planetary Devastation** 地爆天星 | Deva · ULT | Throws a gravity core 14 blocks above where you aim. It tears up to 220 blocks out of the ground, drags in everything within 24 blocks, then seals it all inside a stone moon. Anything trapped takes 40 damage. 7.5 s cutscene. | 120 s |
| **Heavenly Descent** | Deva · ULT | You rise 22 blocks, then flatten everything below. The crater is 19 blocks wide and the blast deals up to 60 damage across 32 blocks. 8 s cutscene. | 120 s |
| **Asura Barrage** | Asura | Fires 10 homing missiles from your shoulders that spread across nearby hostiles. | 10 s |
| **Asura Cannon** | Asura | A charged arm cannon that bores a tunnel up to 48 blocks long. Deals 30 damage along the beam and 14 at the blast. Has an impact frame. | 15 s |
| **Soul Extraction** | Human | Rips the soul out of a target within 7 blocks. It **one-shots** anything with 60 or less max HP, except bosses and players. Everything else takes 30 damage and is crippled. | 20 s |
| **Summon: Horned Behemoth** | Animal | A drill-horned rhino-beast with a 14-damage charge. Its signature move gets an impact frame. | 30 s |
| **Summon: Great Centipede** | Animal | A huge centipede that coils around its prey and crushes it. Its bites are venomous. | 30 s |
| **Summon: Three-Headed Hound** | Animal | Every heavy hit it takes splits off another hound, up to 4 generations. | 30 s |
| **Summon: Sky Roc** | Animal | A four-winged bird. **Right-click it to ride**, and it carries two. When nobody is riding it, it dive-bombs enemies. It stays for 5 min. | 20 s |
| **Preta Absorption** 封術吸印 | Preta | For 4.5 s, projectiles, explosions, fire and magic heal you instead of hurting you. It also drains anyone grappling you. | 20 s |
| **King of Hell** | Naraka | A colossal crowned head rises out of the ground. It fully heals you, removes harmful effects, grants Absorption III, and tears 20 HP out of the nearest foe. | 60 s |
| **Samsara of Heavenly Life** 輪廻天生 | Outer · ULT | Souls pour out of a giant head and fully restore every player within 96 blocks, and nearby animals and villagers. You're left at 1 heart. 11 s cutscene. | 300 s |
| **Deva Ascension** | Transformation | For 60 s you get flight, halved cooldowns, Resistance, Speed and a wider Almighty Push. A halo of receivers floats behind you. | 150 s |
| **Shared Sight** | All | Marks every hostile within 48 blocks through walls and gives you night vision. | 15 s |

You can't be hurt during a cutscene. Terrain damage follows the `mobGriefing` game rule, and chests and other block entities are never touched. You can have up to 4 summons out at once. Summoning a 5th dismisses the oldest.

## How the visuals work

- **Shader impact frames.** Custom GLSL core shaders drive the bleach → ink → negative → duotone sequences, the shock rings, chromatic aberration and the gravity-lens warp around the Planetary Devastation core.
- **Manga panels.** For the biggest hits, the frame is cut into slanted, inked panels.
- **Bloom pipeline.** A 13-tap downsample, tent upsample and god-ray pass make every glow bloom.
- **Cinematic camera.** A separate camera rig with keyframed cuts, FOV punches, roll, shake and letterboxing.
- **Cel-shaded models.** Every creature, the King of Hell, the moon, the rods and the missiles are built in code. Each is drawn as smooth tubes and ellipsoids with three-band lighting and an inverted-hull ink outline.

## Building from source

GitHub Actions builds the jar on every push. To build it yourself, install JDK 17, run `gradlew build`, and the jar ends up in `build/libs/`.
