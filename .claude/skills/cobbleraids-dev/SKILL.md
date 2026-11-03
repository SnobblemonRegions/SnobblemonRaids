---
name: cobbleraids-dev
description: Use for any feature, fix or refactor in CobbleRaids (Fabric 1.21.1, Cobblemon, radar-client). Enforces verify-APIs-first, test-first logic, mixin safety and the tiered done-gate so changes ship without silent bugs.
---

# CobbleRaids development checklist

Follow in order. Do not skip a step because the change "looks small".

## 1. Orient
- Read `CLAUDE.md` constraints, `CONTEXT.md`, and any `docs/adr/` entry touching the area.
- Classify the change: **pure logic**, **server-side Minecraft/Cobblemon**, or **client/render/mixin**.
  This decides the gate tier in step 6.

## 2. Verify, never recall
Before writing code against any Minecraft, Fabric, Cobblemon or Polymer API:
- Minecraft 1.21.1 classes/methods: use the `minecraft-dev` MCP (`search_minecraft_code`,
  `get_minecraft_source`, `find_mapping`, `get_registry_data`) with Mojang mappings.
- Cobblemon/Polymer: decompile the pinned jar (`decompile_mod_jar` / `search_mod_code`).
- Mixins: `analyze_mixin`; confirm the **concrete runtime class** and that nothing between it and
  the target overrides the method without calling super (ADR 0004).
- If an API cannot be confirmed, stop and say so instead of guessing a signature.

## 3. Test first for logic
- Pure logic lives in Minecraft-free classes; write the failing JUnit test first (`tdd` skill),
  watch it fail for the right reason, then implement.
- Cover edges: empty/zero/max, config out-of-range, restart/reload (Pending Rewards must survive
  both), and the failure branch, not only the happy path.

## 4. Implement narrowly
- Smallest change that satisfies the request. Respect side separation and soft dependencies.
- Every mixin callback has a fault barrier; log through the project's logging discipline
  (`validation/validate_logging.py`).
- Config/datapack-facing values are validated on load with clear errors, not trusted.

## 5. Self-check before the gate
- Re-read the diff. Any API used that was not verified in step 2? Any client class reachable from
  a server path? Any new hard dependency? Any term that violates `CONTEXT.md` `_Avoid_`?

## 6. Gate (tiered, see CLAUDE.md table)
- Java change → `./gradlew test`
- Plus resources/mixins/build/metadata → `bash validation/ci_local.sh`
- Plus render/client/visual mixin → `radarSmoke` harness (`validation/radar/`)
Report actual output. A failure is fixed or reported, never hidden. State explicitly what was
**not** verified in a running game.

## 7. Review
- Run `code-review` on the diff before commit. For a bug, use `diagnosing-bugs` and write the
  regression test before the fix. Commit only when asked.
