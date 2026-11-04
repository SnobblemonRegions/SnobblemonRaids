# Raid Radar validation

The full repository gate runs the radar JUnit tests along with the existing checks:

```sh
bash validation/ci_local.sh
```

The preview uses real renderer code and Minecraft map colors, with synthetic terrain. Run with Java 21 and a classpath containing the project's mapped Minecraft JAR and `build/classes/java/main`:

```text
java -classpath <minecraftJar>;<mainClasses> validation/radar/RenderRadarPreview.java <absoluteOutputPrefix>
```

An optional second argument reads reconstructed raw map bytes from the live test instead of synthetic terrain.

It writes a native 128px PNG and a 768px nearest-neighbour preview. It also prints a warmed terrain-render microbenchmark. This is not an in-game screenshot or an FPS test.

## Real server and vanilla-protocol probe

Use an isolated Fabric 1.21.1 test server with Cobblemon, Fabric API, Polymer and the prototype CobbleRaids JAR installed. Use a fresh flat world and unused game/RCON ports, with `online-mode=false`, RCON enabled, and `server-ip=127.0.0.1`. The existing `validation/smoke/node_modules` dependencies are required. The script boots/stops that server, constructs a test platform and crafting table, gives exact ingredients, and creates raid bosses. **Do not point it at a production world.**

```text
python validation/radar/smoke_test.py --server-dir <isolatedRig> --java <jdk21Java> --output-dir <resultsDirectory>
```

It verifies the exact 3x3 recipe by actually clicking the crafting grid and checking the server's resulting item/consumed ingredients. Mineflayer only knows vanilla recipes, so its direct slot clicks explicitly supply the requested pattern; the real server decides whether it is valid. It verifies the held compass/map transformation, full initial map pixels, target markers, cycling, movement, closed-state packet silence, and offhand operation. A reconstructed map frame is saved as raw palette bytes for inspection. These checks do not prove first-person visual layout or shader-pack compatibility; those require a real graphical client.

Raidio is the radar's default, so `smoke_test.py` above, which expects the map and hologram, needs the server started with `-Dcobbleraids.raidio=off` (for example `JAVA_TOOL_OPTIONS=-Dcobbleraids.raidio=off`).

## Raidio

```text
python validation/radar/raidio_smoke.py --server-dir <isolatedRig> --output-dir <resultsDirectory>
```

Same rig and the same warning (it boots and stops the server and changes its world). A raid spawns 120 blocks south of the player and the bot opens the radar. The player is then turned with `/tp`, and the sound packets the server sends are checked: silent when still facing away; the Starter tune (a square-wave lead and a bass) at the pitches and the tempo that distance calls for, facing the raid; only quiet bleeps and the odd faint tick while sweeping away; silent again after the sweep; silent once tuned out. Everything is judged on the "record" sound category, which is the "Jukebox/Note Blocks" volume slider, so it also proves players can turn Raidio down. The harness bot reads the server's recipe list as raw bytes (`radar_bot.js`), because its protocol library cannot parse Cobblemon's recipes and otherwise never joins.

This proves what the server sends and when. It does not prove how it sounds or that it is pleasant to use: that needs ears in a real client.

## Prototype run result (2026-10-29)

Build `0.8.192-radar-prototype` passed all seven live checks: exact recipe consumption,
vanilla map transformation and full initial texture, orange raid targets, target cycling,
movement updates, closed-state packet silence and offhand operation. The server was an
isolated localhost copy of the test rig; no production server or world was changed.
The probe forces authoritative inventory resyncs for crafting: its ordinary inventory
prediction is not reliable when modded ingredients share vanilla item representations.

The repository gate passed; the final build reports 728 passing JUnit tests, including
10 radar-specific tests. No graphical Iris/Complementary acceptance test or multiplayer
load benchmark has been run.


## Optional 3D companion

See [the hologram installation and validation notes](../../docs/RAID-RADAR-HOLOGRAM.md).
The root build compiles/tests both artifacts. The live probe also verifies capability
gating and terrain snapshots. `graphical_smoke.py` runs a real isolated client with
local Iris/Complementary copies; its development probe is not packaged in either JAR.
