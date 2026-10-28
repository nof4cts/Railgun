# The Abomination (Forge 1.20.1)

A horror mob that watches, stalks, hunts and finally tears your head off with your spine still attached.

## Build & install
Requires **JDK 17** (Gradle downloads Minecraft + Forge 47.3.0 on first build).

```
./gradlew build          # Windows: gradlew.bat build
```
Copy `build/libs/abomination-1.0.0.jar` into your `mods` folder (Forge 1.20.1, 47.x).
To test in a dev client: `./gradlew runClient`.

Quick test: `/summon abomination:abomination ~ ~ ~5` in survival at night, or use the spawn egg.
Boss: craft a **Throbbing Heart** (4 Heart Fragments around a Fermented Spider Eye) and use it on the ground at night or underground.

## What it does
- **Observe** – watches from 24-44 blocks, half-hidden behind trees/walls. Look straight at it and it vanishes in a ripple.
- **Stalk** – follows at 9-16 blocks and freezes dead when you look at it. Fake footsteps behind you, ambient sound cuts out, whispers by your ear, appears in your peripheral vision.
- **Hunt** – drops onto all fours and sprints, feints sideways, throws its voice, leaps gaps, climbs walls, tears through blocks (hardness <= 3 by default).
- **Nightmare** (<50% HP) – darkness pulses, light flicker, false screams, proportions warp, controlled jumpscare teleports.
- **Boss stages** at 66% / 33% HP – roar + shockwave, mutation, ground-slam attack, flesh-corruption hazard pools, escalating music.
- **Finisher** – on a killing blow: grabbed into its shadow, face close-up with the jaw unhinging, silence, impact freeze, then your head (with your skin) is ripped upward and your spine is drawn out vertebra by vertebra while blood fountains from the neck in arterial pulses. It holds the head and dangling spine aloft, cut to black, death screen. Bystanders see and hear all of it. Death message: "X had their head and spine torn out by The Abomination".
- **Hidden encounter** – it grows pulsing Flesh Growth around its lairs (beats, whispers, bleeds). Breaking it can drop Heart Fragments -> Throbbing Heart -> Awakened Abomination (650 HP, emerges from the ground, drops its Spine).

## Config
`config/abomination-common.toml` (server): `finisherEnabled`, `naturalSpawning`, `blockBreaking`, `maxBreakHardness`, `corruption`, `jumpscares`, `healthMultiplier`, `damageMultiplier`.
`config/abomination-client.toml`: `cinematicCamera`, `screenEffects`, `cameraShake`, `dynamicMusic`, `goreLevel` (0-3).

## Code map
- `entity/AbominationEntity` – phases, boss stages, combat, stalking tricks, corruption.
- `entity/ai/*` – Observe / Stalk / Hunt / Attack / Locked goals.
- `finisher/FinisherManager` – server timeline: cancels the death, pins the victim, kills with `abomination:spine_rip` at tick 96.
- `client/fx/ClientFinisher` – head + verlet spine rendering, blood, camera takeover, sound timeline.
- `client/fx/ClientFx` – sub-bass/heartbeat proximity, music, shake, aberration, flicker overlay.
- `client/render/AbominationModel` + `MeshLibrary` – sculpted smooth-shaded mesh (no cubes) on a 63-bone procedurally animated skeleton. Regenerate mesh, atlas and skeleton with `python3 tools/sculpt.py src/main/resources/assets/abomination <skeleton.txt> <preview.png>`.

## Audio
`gore_*`, `rip_tear`, `gush_*` are cut from the supplied GoreBox clip; `music_*`, `finisher_sting`, `emerge_sting` are cut from the supplied eerie compilation; everything else is synthesized. The sections were chosen by loudness, not by ear, so swap any `.ogg` in `assets/abomination/sounds/` you don't like. Those two source files come from other creators - fine for personal play, but clear the rights before publishing the mod.
