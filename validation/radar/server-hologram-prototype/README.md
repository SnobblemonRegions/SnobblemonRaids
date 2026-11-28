# Stationary Cobblemon projector experiment

This disposable experiment asks whether server packets and a resource pack can draw live terrain in the Cobblemon 1.8.2 client renderer. The test-only client entrypoint records screenshots and frame intervals. The render path comes from the installed Cobblemon mod.

Run from the repository root on the Windows test rig.

```powershell
.\gradlew.bat -I validation/radar/server-hologram-prototype/prototype.init.gradle remapHologramExperiment --offline
python validation/radar/server-hologram-prototype/run.py --server-dir <isolated-server> --output-dir <absolute-output>
```

The harness uses the existing normally generated meadow world and local copies of Iris 1.8.8, Sodium 0.6.13, Complementary Unbound r5.1.1 and Cobblemon 1.8.2. It installs a temporary test JAR on the isolated server and restores the properties and mod list afterward. In standalone mode it loads the resource pack from the isolated client's resourcepacks directory.

Use the same isolated rig to test the shipped projector through the radar menu.

```powershell
.\gradlew.bat -I validation/radar/server-hologram-prototype/prototype.init.gradle build remapHologramExperiment --offline
python validation/radar/server-hologram-prototype/run.py --integrated --server-dir <isolated-server> --output-dir <absolute-output>
```

Integrated mode builds the real Polymer pack, serves it over loopback and waits for the client to
accept it. The probe opens the radar menu, deploys the projector and captures live meadow terrain
with a spawned Charizard raid. It then checks item use with the projector visible, camera follow through a 90-degree turn and six-block move,
item-switch cleanup and the flat-map option. The companion is absent from this isolated client.

## Result

- Stage 0 captured the meadow baseline. Stages 1 and 2 displayed four columns on the same packet-only entity and changed one column's height from band 2 to band 12. The top of the cyan region rose by 20 pixels in the 2560×1440 captures.
- Stage 3 sampled 256 loaded-chunk heights spanning Y=59–148. A circular mask selects 188 of the 256 columns for display. The orange and white markers and route dots are illustrative.
- One animation packet carried the full terrain update. Its estimated body size was 2,483 bytes before framing and compression. A single sample-and-quantize pass took 1,579 μs on this rig. Production should reuse the existing bounded terrain sampler.
- Median frame interval was 2.71 ms for the baseline and 2.78 ms for the full map. The p95 values were 4.04 and 3.43 ms respectively. Each stage measured 120 intervals after 30 warm-up frames. This is one short stationary test on one client. Following motion, latency, and simultaneous viewers still need measurement.
- The model loaded with box UVs. Explicit transformed-part scale defaults and decimal literals with leading zeros produced bounded columns in the final run.

The capture directory is `validation/radar/out/server-projector-meadow` on the test machine. See `screenshots/stage-3.png` for the full terrain and `stage-1.png` and `stage-2.png` in the same directory for the before-and-after height change.
