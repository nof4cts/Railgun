# Handheld Railgun – Forge 1.20.1

## Build
1. Download the official **Forge 1.20.1 MDK** (47.2.0+) from files.minecraftforge.net and unzip it.
2. Delete the MDK's `src/` folder and copy this project's `src/` in. Replace the MDK's `gradle.properties` with this one
   (it sets `mod_id=railgun` so the dev run configs match).
3. Java 17. Run `./gradlew build` -> jar in `build/libs/`. `./gradlew runClient` to test in dev.

## Play
- Craft it (Creative > Combat tab). Recipe is in `data/railgun/recipes/railgun.json`.
- **Hold right-click** to charge (ring appears at the crosshair, gun coils spin up). **Release** to fire.
  Minimum ~0.7 s; full power at 2 s. Beam pierces all mobs and punches through glass/leaves/wool-hardness blocks.
- Repair on an anvil with diamonds.

## Options (config/railgun-client.toml)
- `impactFrames` – the strobing invert/ink frames. **Flashing warning**: turn off if you're photosensitive.
- `screenShake`, `heavyParticles`.

## Tuning the look
- First-person placement: constants at the top of `RailgunRenderer.java` (`FP_X/FP_Y/FP_Z/FP_SCALE/FP_YAW`).
- Beam colours/radii: `ShotFx.render`. Gun geometry: static block in `GunModel.java`.

## Easiest way to get a jar (no setup)
Push this folder to a new GitHub repo. The included workflow (`.github/workflows/build.yml`) builds it automatically.
Open the repo's **Actions** tab -> latest run -> download the `railgun-mod-jar` artifact -> put the jar in `.minecraft/mods`.
