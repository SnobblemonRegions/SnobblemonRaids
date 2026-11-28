# Rift Crown production sprite

Built-in `image_gen` created the transparent frame from the approved **A — Rift Crown** concept. The PNG was imported unchanged. Runtime UV selection excludes the unused transparent outer margin; the game draws the name, current HP, damage echo and status independently.

Sprite: `radar-client/src/main/resources/assets/cobbleraids_radar/textures/worldboss/rift_crown.png` in the integration source tree. [Hash, dimensions and functional coordinates](rift-crown-asset-2026-11-27.json).

The following prompt was used with the approved concept sheet as the sole reference image and `transparent_background=true`. The first export received an output-stage tool rejection; one identical retry succeeded. No external API/CLI or alternate model was used.

## Final generation prompt

Use case: stylized-concept / production game sprite asset.
Reference image role: approved art-direction sheet. Reproduce ONLY design A, RIFT CROWN, as one isolated finished horizontal bossbar FRAME sprite with a genuinely transparent background. Preserve A's distinct creature-shaped silhouette, its Giratina face and crown at top center, broad black wings with crimson tips, three-dimensional stepped gold ribs gripping the ends of the health rail, obsidian/purple inset surfaces and small violet distortion seams. The user selected A, not B or C. Do not redesign it into a normal battle-card rectangle.

This is a production sprite, not another concept sheet: NO headings, labels, thumbnails, text, letters, numerals, watermarks, status glyphs or checkerboard pixels. NO baked-in green health fill or damage trail. Remove ALL text from the reference design. Leave the CENTER NAME AREA as a clean dark, text-free space beneath the head and above the health track. Leave the long HEALTH TRACK as an empty, flat dark recessed rectangle with a thin rim, occupying about 65% of the overall frame width and aligned centrally. Leave a separate clean opaque dark STATUS STRIP beneath the health track, same inner width, about enough height for two small native Minecraft text rows. Do not place ornament over the name, health-track interior, or status interior: the game will draw those elements dynamically.

VISUAL QUALITY: exceptional, carefully authored Minecraft-compatible pixel art. Strong square pixel clusters, bold dark outline, warm stepped gold shading, charcoal wings with violet shade planes, crimson tips, recognizable Giratina grey face and red eyes. Retain the character, wing anatomy and crown artistry from A. No blurry glows, no antialiasing, no smooth high-resolution metal filigree, no vector gradients, no photorealism, no font graphics, no Pokeball badge. Violet light must be expressed as a few crisp pixel seams.

COMPOSITION: one wide horizontal symmetrical frame, all wing tips and crown tips fully visible, no cropping. Target display silhouette around 360 pixels wide by 88 pixels high; draw as if on that native pixel grid enlarged about four times with nearest-neighbor for crisp clarity. The head/crown takes the upper central portion; the wings extend laterally behind and beside the dark name space. The health track lies in the lower third; the dark status strip is directly below it. Pack the frame tightly within its visible bounds; unused outer canvas must be alpha-transparent. Clean background-free sprite suitable for drawing directly over the Minecraft world. Transparent air gaps between wing tips and gold ribs must remain transparent.

## Runtime preparation

The exported frame's visible proportions led to a 360 × 96 normal display footprint. Its pixel file remains unchanged; health/effect layers are separately authored at native UI resolution by `tools/rift_crown_effects.py`. Font metrics and viewport width control text fitting and caption stacking. The runtime is subject to native visual review; the source image alone is not evidence of implementation or performance approval.
