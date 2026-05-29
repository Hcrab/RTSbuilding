# Porting Notes

## Current Catch-up Source

The active cross-version queue lives in the root `PORT_MATRIX.md`.

Current status:

- this project last had a broad successful sync on 2026-04-05
- public beta metadata, current mainline resources, and world-level storage
  session snapshots have been refreshed on 2026-05-26
- most of the mainline May 22-24 feature wave is not yet fully ported here
- catch this project up before replaying the same work into the 1.19.2 sister

## Why This Is a Sister Project

The `1.21.1 NeoForge` mainline and the `1.20.1 Forge` down-port diverge on build tooling, networking APIs, capability APIs, and some lifecycle/event details. Keeping the port in a separate project prevents the support code from polluting the mainline branch.

## Recommended Migration Order

1. Keep this project compiling as a minimal Forge shell.
2. Port shared pure-Java utility code first.
3. Port registries, entities, and saved data.
4. Replace NeoForge networking with Forge `SimpleChannel`.
5. Replace NeoForge capability access with Forge capability lookups.
6. Reintroduce client screens and remote interaction flows.
7. Re-enable compat layers one by one: JEI, AE2, FTB, Sophisticated Storage.
8. Add mixin support only when the Sophisticated Storage workaround is being moved over.

## High-Risk Areas From the NeoForge Mainline

- `network/`
  The current mainline uses `CustomPacketPayload`, `StreamCodec`, and `PayloadRegistrar`; Forge 1.20.1 should use `SimpleChannel`.
- `server/RtsStorageManager.java`
  Uses NeoForge block capability access that must be rewritten for Forge capabilities.
- `RtsbuildingMod.java`
  Uses NeoForge-specific registration helpers and split tick events that need Forge equivalents.
- `mixin/` and `rtsbuilding.mixins.json`
  The Forge project does not yet include mixin tooling.

## Dependency Strategy

- Add only Forge itself at bootstrap time.
- Add JEI early if you need the craft terminal UX during development.
- Delay AE2, FTB, and Sophisticated Storage until the base port compiles and runs.
- Delay mixin setup until the Sophisticated Storage remote-menu fix is ported.
