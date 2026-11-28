Latest geometry correction: [crest depth repair, 2026-11-27](worldboss-review/SOVEREIGN-SEAL-Z-FIGHTING-2026-11-27.md).

Latest build and native review: [theatre and material pass, 2026-11-27](worldboss-review/SOVEREIGN-SEAL-THEATRE-MATERIALS-2026-11-27.md).

Latest verification: [native seal review](worldboss-review/SOVEREIGN-SEAL-NATIVE-REVIEW-2026-11-26.md).

# Sovereign Seal prototype — 2026-11-26

Status: code, assets and offline geometry review are ready. A private native preview loaded the band and Giratina; full visual approval is pending. See [the particle pass](worldboss-review/SOVEREIGN-SEAL-EFFECTS.md) for the latest effects build.

## Implemented scope

One full-size broken gold orbital band and one floating black/gold/scarlet crest, based on the Sovereign Seal concept. These are solid native cuboid models, not a texture painted over the triangular dome. The complete three-band/six-crest seal, spectral ribbons and phase-change scenes are outside this first prototype.

The scene uses two packet-only item displays. They are never added to the server world, saved, given collision or assigned AI. Poses update on the existing eight-tick border cadence, with native transform interpolation. The band turns once in 3,600 game ticks; the crest bobs over 180 ticks. Shape and motion remain subject to in-game review.

Activation requires both `-Dcobbleraids.worldboss.sovereignPrototype=true` and a TEST encounter. The usual dome remains the default, and a live encounter cannot opt in through the JVM flag. The new materials have their own Polymer-allocated model IDs and resource paths; the Halloween assets remain separate.

The crest accounts for the extra Y half-turn in Minecraft 1.21.1's `ItemDisplayRenderer`. The native signatures and renderer operation were verified from the pinned cached Minecraft JAR after the Minecraft-dev connector failed with a Node/SQLite ABI mismatch.

## Review artifacts

* [Actual model geometry preview](worldboss-review/sovereign-prototype-geometry.png). This is an offline render with illustrative lighting, explicitly not a Minecraft screenshot. It omits Giratina and does not establish in-game appearance.
* Private launcher: `C:/Users/Facel/SnobblemonRaids/docs/worldboss-review/Start-Sovereign-Preview.ps1`. It prepares a fresh loopback-only fixture from the recorded built artifact, validates the generated pack, launches the offline test client, and starts a reward-suppressed TEST encounter. It completed an approved private launch for the initial geometry build; the subsequent particle pass still needs its own native visual review.
* The launcher preserves existing fixtures, checks the recorded client, uses ownership-checked RCON, restores inherited Java options, and requires the resource pack for this private visual preview. No production configuration was changed.

## Validation

Initial geometry build: 1,401 core tests plus 8 optional radar-client tests; zero failures, errors or skips. Seven prototype tests cover finite/unit poses, animation wraparound, native crest facing, geometry/model bounds, floor clearance at supported radius extremes, and the TEST-only gate.

The original `ci_local.sh` passed before the final facing correction. After that correction, its complete set of checks passed through a direct Windows driver, including a clean build/remap, economy/Showdown validations, 86 guarded injection points, 43 verified mixin targets, and all source/JAR checks. Log: `C:/Users/Facel/SnobblemonRaids/build/sovereign-ci-direct.log`.

Git Bash could not initialize its named objects in the sandbox. Java ZIP filesystems also reported workspace output paths read-only, despite ordinary file writes working. The final verification used an offline workspace-local Gradle cache and the sandbox's writable temporary directory for packaging, then copied the resulting artifacts back before running the validators. Existing artifact outputs were moved to a checked workspace backup path, not deleted. No approval check, host permission or Java access flag was changed.

The required graphical radarSmoke attempt failed at login because the probe client omitted Accessories and Cobblemon Cards while the server loaded them. The harness now mirrors client-compatible server dependencies by mod metadata, excluding server-only mods and the core mod for its companion-only test. The latest particle build reran the corrected harness successfully in its documented map mode (`-Dcobbleraids.raidio=off`); see the particle-pass report for results and limits.

Native construction of both objects and partial appearance were observed. Full appearance, motion, oversized-model/anchor-chunk visibility, viewer joins/leaves, reconnect/dimension cleanup, seal shader compatibility and 50-player performance remain unverified. None is certified by the unit tests or offline geometry preview. The cached private runtime previously used Fabric Loader 0.19.5 while the source pin is 0.19.3; pinned production-runtime acceptance remains separate.

## Current artifact and blocker

Raids artifact: `0.8.211-worldboss-playtest`, SHA256 `f97654cd09bb0c193ca5eebd6f100f3070b69852de7444cdbaae71a1860b8b82`.

The earlier private server was stopped. The graphical harness's disposable server also stopped after its failed check. The prepared fixture `84f8a7152b739cff` was never launched and contains an earlier pre-correction artifact; the new launcher prepares a fresh fixture instead of mutating its manifest. A later approved launch created private rig `f0b5f2539a272233`. Its pack loaded and both seal objects were constructed; the band and shared Giratina were observed. The user stopped Computer Use before the remaining visual checks. That running fixture contains the earlier artifact, without the new particles.

An earlier approval-review usage limit prevented an inspection. Subsequent launch approval succeeded. The latest particle-build graphical harness passed in map mode; the private preview still needs a restart to show the new effects. No commit, push or production deployment was performed.
