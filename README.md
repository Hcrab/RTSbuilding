# RTS Building: Build From Above Forge 1.20.1

This directory is the dedicated `Minecraft 1.20.1 + Forge` port of RTS Building: Build From Above.

## Baseline

- Minecraft: `1.20.1`
- Forge: `47.4.16`
- Java: `17`
- MDK source: `upstream/forge-1.20.1-47.4.16-mdk.zip`
- Current port version: `1.0.0-beta-forge.1.20.1`
- Release archive base name: `rtsbuilding-build-from-above-forge-1.20.1`

## Recommended Layout

- `src/main/java/com/rtsbuilding/rtsbuilding/`
  The actual Forge 1.20.1 codebase. Keep the package identical to the NeoForge mainline to minimize copy/edit churn.
- `src/main/resources/META-INF/mods.toml`
  Forge metadata entrypoint.
- `src/main/resources/assets/rtsbuilding/`
  Client assets, lang files, models, sounds, textures.
- `src/main/resources/data/rtsbuilding/`
  Recipes, tags, loot, advancements, other datapack content.
- `src/generated/resources/`
  Data-generator output.
- `docs/`
  Porting notes, dependency decisions, and API migration records.
- `upstream/`
  Frozen third-party bootstrap inputs such as the MDK zip.

## Architecture Snapshot

- `RtsbuildingMod` wires Forge lifecycle hooks, client render registration, and player/server events.
- `RtsStorageManager` owns the RTS session model and most gameplay logic:
  - linked storage set, linked dimension, link modes, and storage page aggregation
  - UI/session state such as page, search, category, sort, and sort direction
  - quick slots, GUI bindings, recent entries, internal fluid cache, funnel state, and mining state
  - persistence to player NBT so RTS state survives reconnects and world reloads
- `RtsCameraManager` handles camera activation and teardown.
- `network/` contains the C2S/S2C payloads that move RTS actions between client and server.
- `client/` contains UI, input gating, and rendering for the RTS overlay and terminals.
- `compat/` contains optional integration layers for AE2, FTB, and Sophisticated Storage.
- `mixin/` contains the Forge-side compatibility mixins that need direct screen/menu interception.
- `server/data/` contains world save data used by block tracking and other persistent server-side records.

## Local Build

Use `tools/build_all_ports.ps1` from the repository root so this project does not accidentally run under the machine default Java 25.

PowerShell build example:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ..\..\tools\build_all_ports.ps1 -SkipMain -Skip119
```

Direct project build example:

```powershell
$env:RTSBUILDING_PORT_JAVA_HOME = 'E:\RTSbuilding\.gradle-user-home\jdks\eclipse_adoptium-21-amd64-windows.2'
powershell -NoProfile -ExecutionPolicy Bypass -File ..\..\tools\build_all_ports.ps1 -SkipMain -Skip119
```

## Current Catch-up Status

The root `PORT_MATRIX.md` is the source of truth. This 1.20.1 port is the first sister project to catch up; the 1.19.2 port should replay this work afterward.
