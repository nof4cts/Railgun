# Cataclysm Spells (Forge 1.20.1)

Three destructive spells, each with a full cinematic cutscene. All visuals are custom geometry. No vanilla particles.

| Spell | What it does |
|---|---|
| **Falling Star** 流星 | Calls a star down from orbit onto where you look (up to 64 blocks). The impact deals up to 70 damage within 16 blocks, then a fireball, a mushroom cloud, a shock dome and a burning crater about 19 blocks wide. |
| **Event Horizon** 事象の地平線 | A black hole with an accretion disk and polar jets. It drags in enemies and rips up terrain, then collapses: 50 damage and a spherical crater. |
| **Skyfall Lance** 天墜槍 | Gold sigils appear on the ground and in the sky, then a beam from orbit carves a 36-block molten trench (38 damage). |

## Rendering

- **GPU impact frames.** The real scene is traced into ink: bleach → ink → negative → duotone frame stutter, radial speed lines, chromatic aberration, zoom blur, and shockwave refraction rings.
- **Bloom pyramid and god rays.** Bright pass, a 4-level downsample/upsample bloom, and radial light shafts from the star, the fireball, the accretion disk and the lance.
- **Procedural plasma shader.** Animated star surface with limb darkening and a streaming corona, a Doppler-beamed accretion disk, and a flowing beam core.
- **Space sky.** The sky tears open to a procedural starfield, nebulae and a galactic band during each spell.
- **Gravitational lensing.** The black hole bends the image around it, with an Einstein ring.
- **Lots of layers:** meteor fragments, fireballs, a mushroom cloud, shock domes, dust walls, branching lava cracks, ember rain, smoke columns, lightning on the beam, colour grading, heat haze, letterboxed multi-shot cutscenes.

Right-click a spell to cast it. Each has a 40 s cooldown. You can't be hurt during your own cutscene.

**Warning: the impact frames flash hard.** Terrain damage follows the `mobGriefing` game rule, and chests and other block entities are never destroyed.
