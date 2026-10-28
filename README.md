# Iron Lung — standalone Forge 1.20.1 mod

A rusted one-person submarine, an ocean of blood, and a camera that is your only window.
Mod ID `ironlung`. Separate from every other mod; it touches nothing else.

## Install

1. Double-click `build_and_install.bat`.
   - It finds Java (including the one bundled with the Minecraft Launcher), builds the mod with Gradle and
     copies `ironlung-1.0.0.jar` into `%APPDATA%\.minecraft\mods`.
   - The first build downloads Forge + Minecraft and takes 5–15 minutes. Later builds take under a minute.
2. Start your **forge 1.20.1** profile.
3. Get **Dive Orders** (creative tab "Iron Lung", or craft: paper + iron ingot + redstone + ink sac, shapeless),
   then use them. Or run `/ironlung start`.

Manual build: `gradlew build` → `build/libs/ironlung-1.0.0.jar`.

## How to play

You are sealed inside a 3×5 interior. Everything is a physical control on the walls; right-click it.

| Control | Where | What it does |
|---|---|---|
| Camera Monitor | front, eye level | Live external feed. **A/D** pan, **W/S** tilt, **Q/E** switch mount (fore / port / starboard / aft / keel), **SPACE** take photo, **TAB** photographs |
| Helm Console | front, low | **W/S** thrust, **A/D** rudder, **R/F** ballast (rise / dive). Shows X/Y coordinates, heading, speed, depth, proximity lamps |
| Ballast Controls | front-left, low | Opens the helm |
| Pressure / Depth gauges | front, eye level | Read-outs: pressure, hull %, depth, keel clearance |
| Proximity Panel | front-right, low | Distances to rock fore / starboard / aft / port. Also beeps on its own |
| Survey Chart | left wall | The sites you must photograph and their coordinates. **It does not show where you are.** Surface / Abort here |
| Sealant Station | left wall, low | Weld a breach: +22% hull per charge (3 charges). Stay next to it while it works |
| Power Breaker | right wall | Reset after a blackout |
| Sealed Hatch | back wall | It does not open |

The loop: read the chart → navigate by the helm's coordinates → point the camera at each site → photograph it
(it must be within ~40 m, in frame, not blocked by rock, and the camera must not be glitching) →
when all sites are logged, **Surface** from the chart → dive report.

Survival:
- **Hull** drops from collisions, impacts and going below the 212 m rating. Below 60% it leaks; at 0 it implodes.
- **Power** can trip from impacts, events or bad luck at the worst moment. Lights, helm and camera die until you
  throw the breaker. Restoring takes a few seconds.
- **Camera condition** degrades with impacts → more static, more ruined exposures.
- Rock pillars rise from the floor. Go around them, or climb (R) over them. You cannot rise above 60 m.

## What's in it

- An opening cutscene for every crew member: a rig on the blood sea under a blood moon, the pilot boarding the
  hanging submarine, the hatch slamming and the boat lowered under (SPACE skips; client config `openingCutscene`).
- Custom void dimension (`ironlung:iron_lung`), no sky, fixed night, procedurally built rusty interior with
  lamps and consoles whose lit/unlit states follow the power bus.
- A seeded 1.1 km blood ocean computed identically on server and client: rolling seabed, trenches, rock pillars,
  impassable margins, and points of interest (wreckage, skeletal remains, biological formations, spires,
  an unclassified monolith, a carcass) plus **uncatalogued** things that are not on the chart.
- A software-rendered camera: ray-marched seabed + signed-distance objects, lit only by the camera's floodlight
  through dense red water. Live feed is low-res, low-framerate, grainy, scan-lined; photographs use the flash
  and develop after a delay. Signal interference, tearing and snow.
- A horror director with tension that rises with progress, time, depth, damage and proximity to sites.
  Events: creaks, groans, distant knocks, hull strikes, something scraping along the hull, pressure spikes,
  light flicker, blackouts, camera interference, shapes crossing the feed, eyes in the dark, false proximity
  contacts, silence, distant calls, knocking at the hatch, photos containing something the live feed never
  showed, and one rare encounter with something very large. Things in the feed only happen while you watch it.
  Major events share a long cooldown; nothing stacks or spams.
- A finale on ascent: the power dies, something knocks, the hatch tears open and it gets in — a full-screen
  jumpscare that kills the whole crew (config `finaleKills`). Then a typed dive report.
- 25 synthesized sound events (no stock audio), positional where it matters, plus drone / motor / wiring loops
  that fall away during silences.
- Camera shake, darkness on blackout, blood vignette on hull failure, minimal HUD readout.
- Config (`config/ironlung-common.toml`, `ironlung-client.toml`): sites per dive, uncatalogued count, map size,
  speed, sealant, event frequency, hull damage, leviathan on/off, lethal implosion; feed resolution/fps, photo
  resolution, shake, distortion, HUD, ambient volume.
- Commands: `/ironlung start [seed|player]`, `/ironlung invite <player>`, `/ironlung join <player>`, `/ironlung leave`, `/ironlung abort`, `/ironlung status`, `/ironlung event <type>` (op).

## Multiplayer

Up to 4 players (config `maxCrew`) crew one submarine together: one ocean, one hull, one camera, one fate.

- Start a dive, then `/ironlung invite <player>`. They get a clickable **[Join dive]** link (valid 2 minutes),
  or can type `/ironlung join <you>`. Ops can join without an invite; `openJoin = true` lets anyone join.
- Helm input from everyone is combined, so two people can steer and work the ballast at once.
- There is one camera: whoever is steering it is the operator, and everyone else's monitor shows what they see.
  Photos, logged sites and events are shared by the whole crew.
- `/ironlung leave` (or LEAVE DIVE on the chart) takes just you home. The leader's ABORT ends the dive for
  everyone. If the leader leaves, leadership passes on.
- If a crew member dies, the rest carry on. If the hull implodes, nobody does.
- Logging out mid-dive is fine: you rejoin where you were. If the dive ends while you're offline, you're sent
  home the next time you log in. The dive pauses only when nobody is aboard.
- Works on dedicated servers (install the jar on the server and on every client).

## Honest notes

- This project was written without access to Forge or Minecraft jars, so it has **not been compiled against
  the real Forge yet**. It was type-checked against stand-in classes matching Forge 47 / 1.20.1 signatures,
  and the ocean + camera renderer were compiled and test-rendered for real. If `build_and_install.bat`
  reports an error, send the error text and it can be fixed quickly.
- Not play-tested in game yet: pacing numbers (event intervals, damage, speed) are first-pass values and are
  all configurable.
- The camera is rendered by the mod's own renderer, not by Minecraft's world renderer; the ocean is not
  made of blocks.
- Tools in `tools/` regenerate textures, sounds and data JSON (Python + numpy/scipy/Pillow + ffmpeg).
