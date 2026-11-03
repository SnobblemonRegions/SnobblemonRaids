## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues (`cmormods-mc/SnobblemonRaids`); skills use the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels used as-is (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: `CONTEXT.md` + `docs/adr/` at the repo root (created lazily by `/domain-modeling`). See `docs/agents/domain.md`.

## Platform (pinned — do not drift)

Fabric, Minecraft 1.21.1, Fabric Loader 0.19.3, Java 21, Loom 1.13.6, official Mojang mappings
(`loom.officialMojangMappings()`). Cobblemon 1.8.2, Polymer 0.9.19. The Fabric API pin is
`0.116.6+1.21.1`; change it only on request. Use Mojang names (`ResourceLocation`, `Level`,
`ServerPlayer`), never Yarn names (`Identifier`, `World`, `ServerPlayerEntity`). Training-data
memory of Minecraft/Cobblemon APIs is not a source; the decompiled 1.21.1 source is.

## Hard constraints

- **Sides.** `radar-client` and anything under `net.minecraft.client` never loads on a dedicated
  server. `radar-shared` holds Minecraft-common value/codec types only: no client, no Cobblemon.
- **Optional mods stay optional.** Text Placeholder API is `modCompileOnly`; guard every use so
  the mod runs without it. Never promote a soft dependency to a hard one.
- **Mixins.** Target the concrete class used at runtime, verified by decompiling, never a vanilla
  ancestor on faith (ADR 0004). Every mixin callback gets a fault barrier
  (`validation/validate_mixin_guards.py`). A mixin that compiles proves nothing — see Verification.
- **Logic goes where tests can reach it.** Put maths, state machines and validation in
  Minecraft-free classes with JUnit tests; keep the Minecraft/Cobblemon-facing layer thin.
- **Vocabulary.** Use the terms in `CONTEXT.md` exactly, honor the `_Avoid_` lists, and read the
  ADRs in `docs/adr/` that touch the area before changing it.
- **Scope.** Surgical changes. No drive-by refactors, no new dependencies, no version bumps
  unless asked. Do not touch `validation/showdown/` (vendored).

## Verification — nothing is "done" until the tier below passes

| Change touches | Run |
|---|---|
| any Java | `./gradlew test` |
| any Java, resources, mixins, `fabric.mod.json`, build files | `./gradlew test`, then `bash validation/ci_local.sh` |
| rendering, `radar-client`, mixins that affect visuals | all of the above, then the `radarSmoke` graphical harness (`validation/radar/`) |

`ci_local.sh` mirrors `.github/workflows/build.yml`; if you add a CI step, add it there too.
Report results verbatim. If a check fails or was skipped, say so; never claim green from a
compile alone. A passing build does not prove a mixin fires or a frame renders correctly.
Say what was and was not verified in-game.

## Workflow

Use the project skill `cobbleraids-dev` for any feature or fix: read domain docs → verify APIs
against decompiled source → tests first for logic → implement → run the gate → review.
