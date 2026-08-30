# RTS Building Asset Licensing

RTS Building uses separate licenses for source code and original media assets.

## Original RTS Building assets

The following paths are licensed under [LICENSE-ASSETS](LICENSE-ASSETS):

- `src/main/resources/assets/rtsbuilding/textures/**`
- `src/main/resources/assets/rtsbuilding/sounds/**` when original RTS Building audio is added

These assets are Copyright (C) 2026 JerryLunar (Hcrab / RTS Building). All
Rights Reserved. The complete, unmodified official mod package may still be
redistributed through Minecraft modpacks, launchers, server packs, mod hosting
platforms, and archival mirrors as described in `LICENSE-ASSETS`.

## Community-contributed RTS Building assets

The interface artwork added or revised in
[PR #133](https://github.com/Hcrab/RTSbuilding/pull/133), including the
`new_2nd_icons`, revised top-bar states, and `ui/terminal.png`, was created and
contributed by Re_Construction (`ReConstruction-127`). The adjacent
`ui/terminal_button.png` is an exact 15×13 crop of that contributed terminal
sheet. The 24×24 backgrounds used by `ui/terminal_sort_name.png`,
`terminal_sort_quantity.png`, `terminal_sort_ascending.png`, and
`terminal_sort_descending.png` are offline derivatives of that button; production
rendering selects one complete PNG and draws it at 1:1 without runtime enlargement
or icon/background composition.

Copyright (C) 2026 Re_Construction. These files are distributed as part of
official RTS Building packages under the normal mod-distribution permissions
in `LICENSE-ASSETS`. Copyright and attribution remain with the contributor.

The `textures/gui/color/colorwheel.png` and `color_palette_indicator.png`
artwork was first developed on the repository's `NeoForge-RTSBuildin-v2.0`
branch by 怡然 and is reused by the current Palette editor with attribution
preserved.

The pixel symbols embedded in the four terminal sorting PNGs above are exact
16×16 crops from `sort.png` and `sort_order.png` on the repository's
`NeoForge-RTSBuildin-v2.0` branch. Their original sheets are retained under
`src/uiPreview/resources/v2-sort/` for development-time provenance and automated
pixel verification; the runtime JAR uses only the four complete semantic PNGs.

The `guide_*` and `developer_*` top-bar states and the monochrome files under
`textures/gui/guide/` were mechanically frozen in July 2026 from the existing
mainline `i` / `D` glyphs and guide pixel-icon drawing paths. They contain no
third-party artwork and are original RTS Building assets covered by
`LICENSE-ASSETS`; the surrounding button-state palette remains derived from the
credited PR #133 top-bar set.

## LGPL-covered project files

Unless another notice applies, source code and non-media project files remain
licensed under `LGPL-3.0-only`, including Java source, build scripts, language
files, and model or data JSON files.

## Third-party materials

Third-party materials are not covered by the RTS Building original asset
license. In particular:

- `src/main/resources/assets/rtsbuilding/pinyin/**` includes PinIn data under
  the MIT License. Its notice is packaged at
  `src/main/resources/META-INF/licenses/PinIn-LICENSE.txt`.
- Minecraft, Mojang, dependency, and contributor materials remain subject to
  their respective licenses and ownership notices.

New third-party assets must be listed here before release. Do not place them in
an original-assets path without preserving their license and attribution.

## Effective date and earlier copies

This split-license policy applies from the commit that introduced these files.
Asset copies from earlier public releases retain the license grants that
accompanied those releases.
