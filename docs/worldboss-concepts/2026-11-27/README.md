# World Boss visual directions — 2026-11-27

Exploration for a long-lived seasonal world-boss presentation. These are AI-generated art targets, not Minecraft screenshots, resource-pack assets or an implementation promise. The two finished sheets include a hero view, model aura detail and particle-ground detail.

## Finished sheets

| Direction | Boundary | Ground language | Giratina aura |
| --- | --- | --- | --- |
| [Distortion Veil](01-distortion-veil.png) | Sparse, breathing violet filaments and crimson breaks suggest a sphere while leaving open views. | Fading curved particle brushstrokes and low drifting wisps. | Umbra Mantle: charcoal-violet wisps following shoulders, wings and feet, with a crimson edge. |
| [Astral Tempest](03-astral-tempest.png) | Sweeping star trails and auroral currents imply the encounter volume. | Fleeting spiral strokes and star motes, with gaps between paths. | Astral Sheath: indigo/silver contour sparks, wing-tip trails and a restrained scarlet chest accent. |

My art-direction preference for Giratina is Distortion Veil plus Umbra Mantle. Astral Tempest is the stronger reusable framework for later seasonal bosses: the palette and movement can change while its visual language stays recognisable.

## Third bubble direction: Rift Cathedral (written concept)

Six narrow spatial tears stand around the encounter boundary. Fine arcs of luminous air suggest a vaulted canopy overhead, with broad clear sightlines between the tears. Low particle currents fade into the ground near each seam. Giratina carries a restrained wing-shaped shadow wake close behind its existing wings. No stone columns, solid cage or manufactured ground disc is part of the effect.

The image service rejected this sheet twice at output generation, with only a generic `other` category supplied. No image is claimed for it.

## Additional aura variant: Riftfire Crown (written concept)

A few crimson-gold tongues rise behind the head and shoulders, with short filaments following the wing edges and ember wisps near the feet. The crown is atmospheric light and vapour, rather than a physical crest or metal ring. Preserve the face, silhouette and original Pokémon materials.

The dedicated three-panel aura study was also rejected twice. Umbra Mantle and Astral Sheath remain visible in the finished sheets' model-aura panels.

## Design constraints carried forward

* Ground effects should be smooth particle/trail drawings with soft falloff and intentional gaps. Avoid solid rotating dials, voxel outlines and dense confetti.
* Treat the boundary, ground accents and model aura as three independent layers. The Pokémon should remain readable during ordinary combat.
* A proposed body aura follows the shared arena boss; invisible duel copies must not gain an aura that reveals them.
* Full/minimal/off settings should control density and secondary layers. Performance needs measurement at the target attendance.
* True wing-following emissions, broad ribbon trails, soft edges and apparent bloom need a rendering prototype. The existing server-side dust pass does not establish a match to these images. Client-aware attachment and local effect rendering may be needed for the detailed target.
* These sheets' arena architecture, dusk lighting and atmospheric skies are presentation context, not new implemented server features.

## Files and generation record

Generated using the built-in image-generation tool. Exact prompts, statuses and rejected attempts are retained in [prompts.json](prompts.json). Generated source images are preserved; project copies use distinct filenames. No runtime code, live scene or production configuration was changed for this exploration.

The image-generation CLI fallback exists and requires a locally configured `OPENAI_API_KEY`; it was not used.

