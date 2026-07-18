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
contributed by Re_Construction (`ReConstruction-127`).

Copyright (C) 2026 Re_Construction. These files are distributed as part of
official RTS Building packages under the normal mod-distribution permissions
in `LICENSE-ASSETS`. Copyright and attribution remain with the contributor.

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
