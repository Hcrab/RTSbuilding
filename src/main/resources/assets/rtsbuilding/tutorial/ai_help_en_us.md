# RTSBuilding 1.20.1 Forge Guide

## Getting started

Press `G` to open RTSBuilding and enter the overhead building view. Press `G` again or `Esc` to leave.

The interface has three main areas:

- Top bar: switch between Interact, Storage binding, Funnel, and Rotate; open Quick Build, Range Culling, Settings, and Help.
- Bottom bar: browse storage, select blocks and tools, use blueprints, craft remotely, and manage plugins.
- Side overlay: appears beside inventories and machines, letting you move items between containers, your inventory, and RTS storage.

Use `W` / `A` / `S` / `D` to pan the camera, `Space` to rise, and left `Shift` to descend. Hold the sprint key to move faster. Hold and drag right mouse to rotate, hold and drag middle mouse to pan, and use the wheel to zoom. A short middle click picks the block under the crosshair.

Hold `Alt` to open the mode wheel near the pointer. Move toward Interact, Storage binding, Funnel, or Rotate, then left click to select it.

You can also press `I` for Interact, `L` for Storage binding, or `R` for Rotate. `F` is quick Funnel: hold it to collect and release it to return to the previous mode.

`Ctrl + right-click` sends the player toward the target. Double-click quickly to try to land precisely above the target block. Press `Q` to drop one selected item at the pointer target. The top status line shows the current mode and the next action.

## Your first remote build

1. Select Storage binding on the top bar.
2. Point at a chest. Left click for read/write access; right click for extract-only access.
3. Open Storage on the bottom bar and click a block to select it.
4. Return to Interact mode, point at a block face, and right click to place.
5. Hold `Shift` while right clicking when placing against a chest, machine, or another interactive block.

Use the empty-hand slot to clear the selected item. Press `Ctrl + Z` to undo a recently placed group.

## Storage binding

The in-game “Storage binding” action is the Storage Link mode on the top bar. Press `L`, or choose it from the `Alt` mode wheel, then point at a chest, machine, AE terminal, or another supported storage block. Left click binds read/write access; right click binds extract-only access. Read/write storage can provide materials for building/crafting and receive auto-stored mining drops. Extract-only storage can provide materials but never receives drops.

Hover the Storage binding button and open link details to refresh, change priority, switch access mode, or unlink an endpoint. If items are missing, verify the Storage Integration plugin, that the target storage is loaded and that the current session has access, then refresh the details. AE2/RS network storage updates through network snapshots, so waiting or refreshing shows the latest state.

### Range binding

Range binding scans storage endpoints in one loaded area. Switch to Storage binding, hold Ctrl to enter range binding, left-click the first corner, then left-click the opposite corner. When the box is ready, scroll to adjust height; hold Alt for faster height changes; press Enter or click again to submit. The mode stays active for another selection; Esc clears the current box first and exits on the next Esc. The current implementation submits read/write binding, accepts up to 64 × 64 × 64, scans loaded chunks only, and deduplicates AE2/Refined Storage networks to one representative endpoint. Oversized, unloaded, or unauthorized targets are filtered by the server.

## Top bar modes

### Interact

Interact is the default mode. Right click uses blocks, machines, chests, doors, and entities first; if there is no valid interaction, it places the selected item. Left click mines with the selected real tool. `Shift + right click` prioritizes the natural placement path.

### Storage binding

Left click links read/write storage. Right click links extract-only storage. Hover over the Storage Link button to open link details, refresh state, or change access mode.

After switching the top bar to Storage binding, point at a chest, barrel, machine, AE terminal, or another supported storage endpoint:

- Left click is read/write: building, crafting, and mining can take materials from it, and auto-store can put mining drops back.
- Right click is extract-only: RTS may take materials but will not insert drops; use it for a dedicated source endpoint.
- Hover the top-bar button and open View Links to refresh, inspect dimension/position, change priority/access, or unlink. If the storage page is temporarily empty, refresh and check that the target and its Storage Integration/network are available before assuming data was lost.

Range binding is inside Storage binding: hold Ctrl, click the first corner and the opposite corner, scroll to adjust height, hold Alt for faster changes, then press Enter or click again to submit. The mode stays active for another selection; Esc clears the current box first and exits on the next Esc. It currently submits read/write links, accepts up to 64 × 64 × 64, scans loaded chunks only, and deduplicates AE2/Refined Storage networks to one representative endpoint.

### Funnel

Link storage that accepts items first. Switch to Funnel, move the pointer near dropped items, then hold `F` or right mouse to collect them. Release to stop.

### Rotate

Select Rotate and left click a placed block, then left click one of the rotation arcs around it. The left and right arcs rotate horizontally; the upper and lower arcs flip vertically. You can also use the arrow keys or numpad `2` / `4` / `6` / `8`. Right mouse remains available for camera dragging.

To choose the state of the next block before placing it, select the block, point at the intended position, and press `R`. Left click a state to use it; right click or `Esc` cancels. Use the page buttons or left/right keys when there is more than one page.

### Quick Build and Range Destroy

Open Quick Build, choose Range Build or Range Destroy, then choose a shape and fill mode. Range Build uses right-click to set its points; Range Destroy uses left-click. When the preview is locked, press the confirm key; the default is `Enter`.

Lines, squares, circles, and balls use A/B points. Lines and circles can be horizontal or vertical. Walls, cylinders, and boxes also need height. Advanced mode gives walls, cylinders, balls, and boxes a 3D box with direction handles. Connected mode fills diagonal corners so line and wall paths remain face-connected.

Chain is a Range Destroy shape. Left click the starting block to find connected blocks of the same type. The Limit control caps the block count. With Survival Balance enabled, Chain is unlocked independently by the Chain Break Plugin. Soft blocks such as dirt, snow, and sand need no harvest-tier plugin; stone and harder blocks still require the matching tier plugin and a usable real tool. Placement and destruction wireframes, ghosts, and animations can be toggled separately in Settings.

Creative Range Build also shows Overwrite. When enabled, the submitted shape directly replaces existing blocks at its targets and ignores entity obstruction. Survival uses the normal material and placement rules.

#### Smart Build: Fill

Fill is located at Quick Build → Range Build → Tools. It creates a preview for the bounded cavity under the crosshair. Use it like this:

1. Open Quick Build, choose Range Build, open Tools, and select Fill.
2. Select the block to place from the bottom bar.
3. Adjust Maximum blocks and Detection diameter. The defaults are 512 blocks and diameter 16.
4. Aim at the cavity opening and left click to lock the preview; left click again to submit the fill.
5. Press `Esc` during the preview to clear the selection, and supply more of the selected block when the material count is low.

Fill is suited to enclosed caves, wall gaps, and underground cavities. The preview shows the build targets; `Enter` also submits with the default confirm key.

#### Smart Destroy tools

Quick Build → Range Destroy → Tools provides three quick tools. Select a tool, aim at a target, and left click to preview and submit:

- **Range Quarry**: repeats a saved X/Y/Z box at each target, useful for clearing ground, opening tunnels, and repeating holes.
- **Chunk Quarry**: mines the chunk under the crosshair; adjust the upward and downward height in the panel to clear a vertical slice.
- **One-click Tree Fell**: starts at a log, natural leaf, or giant mushroom under the crosshair and processes connected tree blocks; Maximum blocks controls the operation size.

Each tool shows its block preview first. Chunk Quarry and Range Quarry use loaded target chunks; One-click Tree Fell scans the connected tree before submitting. After changing a parameter, aim at the next target to run the tool again.

### Range Culling

Range Culling hides existing blocks locally and lets RTS rays pass through them. It does not destroy server blocks.

Left click two corners, use the wheel to change height, and press `Enter`. Select an existing box to reveal six direction handles; click a handle and use the wheel to adjust that side. Press `Delete` / `Backspace`, or use the Delete button, to remove the selected box.

Cull regions are stored per world and do not carry into another save.

### FTB Quest Scan

When FTB Quests and FTB Teams are both installed, a quest-scan button appears on the top bar. It scans the player inventory and linked storage to update item-task progress when the task does not consume items and does not require crafting. Tasks that consume items or require crafting still use their own submission flow.

## Storage and hotbars

The Storage tab can show the player hotbar, RTS shortcuts, linked chests, machines, storage systems, and fluids.

Creative players also get a Creative tab. It groups items by vanilla and mod creative tabs and supports search and paging. Selecting an item there does not modify the player inventory.

- Search by item, material, or mod name.
- Use categories to narrow the result.
- Sort by quantity, mod, or name, in ascending or descending order.
- Click an item to select it for placement.
- Click player hotbar slots 1–9 to select real tools and items.
- Select a storage item, hover an empty RTS shortcut, and press `P` to bind it. `Shift + click` a bound shortcut to clear it.
- Right click a bucket or supported fluid container to move one bucket into linked fluid storage.

With Auto-store enabled, mined drops go to linked storage first. With Shift Deposit enabled, `Shift + right click` an item in an inventory, chest, or machine screen to send it to RTS storage.

## Changing the UI theme

Open the gear Settings button in RTS, expand the `UI / Display` category, find `Global UI Palette / UI Theme`, and click `Adjust`. Select a theme on the left, inspect the live preview, and click `Apply` in the lower-right to save it; `Cancel` discards the draft. Built-in themes include Legacy / Resource Pack, Calibrated RTS Dark, Nord Command, Carbon Operations, and Material Field.

Legacy keeps the original resource-pack texture path. The other themes use an in-game semantic palette shared by windows, controls, status/workflow elements, and some world overlays. Themes are client-local: they do not change server rules, tasks, storage contents, or permissions. The theme window can import/export user themes; if validation or rendering fails, switch back to Legacy and inspect the client log.

## Crafting

After installing the Crafting Terminal plugin, click `C` on the bottom bar or press `C`. Search for an item, right click it to choose an amount, then confirm. Ingredients come from available linked storage. Missing ingredients are shown in the panel.

If JEI is installed, inspect the recipe there first, then search for the item in RTSBuilding.

## Blueprints

Open Blueprint Space on the bottom bar. Supported imports include `.nbt`, `.schem`, `.schematic`, `.litematic`, and Building Gadgets `.json`.

Select a blueprint to show its world preview. Right click a target block to pin it. Use the direction controls, arrow keys, and `Page Up` / `Page Down` to nudge it. Press `R` to rotate clockwise, `Shift + R` to rotate backward, or `X` to cancel the selected blueprint and preview. Check its position and materials before choosing Place.

To capture a blueprint:

1. Choose Create Blueprint.
2. Left click the first and opposite corners. To capture one block, press `Enter` after the first corner.
3. Click blocks inside the selection to exclude or include them. Click a face handle and use the wheel to move that boundary.
4. Use arrow keys and `Page Up` / `Page Down` to move the whole selection.
5. Press `Enter`, enter a name, and save.

Survival checks materials. Creative placement does not consume them. Missing blocks are identified in the blueprint panel.

## Side overlay

The side overlay can appear beside inventories, chests, crafting tables, and machines. It shows linked RTS storage and a Craft entry. Hold its drag control to move it; the position is saved.

Click stored items to take them. While carrying a stack on the cursor, click the import area or a storage slot to return it. Left click returns a stack; right click returns one item. Disable the overlay in RTS Settings if you do not need it.

## Survival progression and plugins

Survival progression is controlled by the world, modpack, or server. Multiplayer uses the server configuration.

Open the player inventory and click the top `RTS` button, or click Plugin Manager on the RTS bottom bar. Plugins are normal items obtained from recipes, quests, shops, or loot.

- RTS Camera: RTS view, basic interaction, and a base action radius of 16 blocks.
- Remote Control Core: remote placement, mining, and rotation.
- Stone Harvest: mining stone-tier blocks.
- Iron, Diamond, and Unlimited Harvest: progressively higher RTS harvest access.
- Storage Integration: links, browsing, auto-store, funnel, fluids, and remote menus.
- Crafting Terminal: remote crafting.
- Chain Destroy: unlocks Chain.
- Range Destroy: unlocks shape-based bulk mining.
- Blueprint: library, preview, and placement.
- Range Culling: local visual culling.
- Field Deployment: bypasses the home-area opening restriction and allows easier home relocation.
- Range I / II / III provide 16 / 32 / 48 blocks; Maximum Range uses the server's maximum allowed radius.

Soft blocks such as dirt, snow, and sand do not require a harvest plugin. Stone, ores, and higher-tier blocks require the matching plugin, and the selected real tool must still be able to mine them.

## RTS Home and action range

With survival progression enabled, open RTS Home from the player inventory, choose Set Initial Home, return to RTS view, and left click the target block. The target must be inside the 3×3 chunks around the position where home selection began.

Without Field Deployment, stand in the home chunk or one of its eight neighboring chunks to open RTS. This is a 3×3 chunk square centered on the home chunk. Opening outside it shows a prominent range message.

After RTS opens, the action area is centered on the player block where that session opened. The radius extends in both directions on X and Z, so the boundary is a square, not a circle. Home decides where RTS may open; it is not intersected with the session action square. Reopen RTS elsewhere to move the action center.

## Settings and tips

Open the gear panel:

- RTS UI Scale changes only RTSBuilding bars and windows.
- Sensitivity and Smooth Camera adjust drag, pan, and wheel feel.
- Auto-store, Shift Deposit, and Tool Protection adjust personal workflows.
- Preview and Animation options separately control block ghosts, wireframes, destruction animation, and the Range Destroy skeleton.
- Storage Refresh controls 30-second automatic refresh and whether the manual refresh button turns green.
- Recover RTS-placed Blocks directly takes back recorded placements without tool, harvest-tier, or Silk Touch checks.
- Jade Follow Mouse / Hide Jade in RTS keep Jade away from the top prompt.
- Container Side Overlay controls the panel beside inventories and machines.
- Damage feedback and low-health exit help protect the player body while using the overhead view.

The RTS button at the top of the vanilla inventory is automatically centered and does not follow other UI offsets. If it overlaps another mod, hide it in the RTSBuilding mod configuration.

The default confirm key is `Enter`. Change it in Minecraft Controls by searching for RTSBuilding.

Modpack and server rules are not changed in the gear panel. Use `Mods → RTSBuilding → Config`, or edit the server's `config/rts_building/rtsbuilding-common.toml`.

## Common questions

### Why are buttons missing?

With survival progression enabled, buttons appear when their plugins are installed. Open Plugin Manager, install the matching plugin, and refresh.

### Why can I not mine?

Return to Interact mode and check Remote Control Core. For stone, ores, and higher tiers, check both the harvest plugin and the selected real tool. The action-bar message reports blocks skipped by harvest tier.

### How do I keep Funnel collecting?

Link writable storage, switch to Funnel, move the pointer near the dropped items, and hold `F` or right mouse.

### Jade covers the top prompt

Open RTS Settings and enable Jade Follow Mouse or Hide Jade in RTS.

### More help

If you need GitHub, Discord, QQ, or another feedback channel, reply “反馈渠道”.

---

# Technical Information Appendix

## AI diagnostic context: client to server

### Overall implementation

An RTSBuilding action starts in the client UI, mode, and selection, crosses a C2S payload and the loader-specific network adapter, then reaches the server thread. The server rechecks the player, dimension, session, range, plugins, permissions, target, real tool, and storage endpoints before admitting a Workflow and durable task. The Task Engine runs tick/slice work; leases, resource waits, cursor, succeeded count, cancellation reason, and persistence are server-authoritative. S2C progress and terminal messages update the client projection only, so a disappearing UI is not proof that the real task ended.

### Client, network, and server log boundaries

- Client logs answer input ownership, mode/focus, selection, C2S send, S2C receive, and local workflow projection.
- Compare the same trace across client BEGIN/send/receive and the server request. BEGIN without a server request points first to connection, registration, decoding, or thread handoff.
- Server logs answer FILTER/validation rejection, admission, TASK_FIRST_SLICE, TASK_WAIT, tool/storage results, TERMINAL, tombstone, and restore.
- latest.log supplies time order; diagnostics-client.jsonl and diagnostics-server.jsonl supply trace-oriented detail. Neither justifies treating the final Error as the root cause.

### Task, storage, and theme boundaries

A new single mine, chain mine, or other mining replacement must genuinely cancel same-player, same-dimension non-terminal MINING and area-destruction DESTRUCTION through RtsTaskEngine.cancelWorkflowTask, reaching CANCELLED, releasing leases, preserving cursor/succeeded, writing a tombstone, closing the workflow, and preventing projection restore from reviving the old area. Without a replacement action, WAITING_RESOURCE remains resumable; PLACE, QUICK_BUILD, and BLUEPRINT_BUILD are outside the scope. Storage binding left-click is read/write and right-click is extract-only; Range binding starts from the top-bar Storage binding mode with Ctrl, submits two corners, and lets the server scan loaded endpoints. AE2/RS snapshot delay is not automatically a task failure. UI themes are client-local draft/preview/validation/persistence and do not cross the network or change server tasks.

### Log-analysis method

1. Confirm version, loader, singleplayer/server, mode, real tool, storage type, and the shortest reproduction.
2. Find the first abnormal event in client and server time order, then correlate trace → seq → op → workflow → task.
3. No BEGIN points to input/ownership; BEGIN without a server request points to the network boundary; FILTER/rejection points to server validation; TASK_WAIT without a replacement is normally valid resource waiting; a terminal UI with a live or restored durable snapshot is a projection/real-task mismatch.
4. Separate directly logged evidence, supported conclusions, and remaining inferences. Mark missing evidence unknown and ask only 1–3 discriminating questions.

For ordinary controls, Storage binding, Range binding, UI themes, and building how-to questions, answer briefly from the guide without requesting logs or expanding internals. Use this appendix against recent logs only for anomalies, stalls, task resurrection, network rejection, storage failure, or crashes.

### Forge 1.20.1 adapter note

Use Forge event bus, mods.toml, and Forge 1.20.1 packet/menu adapters; do not substitute NeoForge names.

This appendix is for maintainers, modpack authors, and AI-assisted troubleshooting. New players do not need it; player-facing guides remain the source for step-by-step instructions.

It documents stable responsibility chains, validation order, and setting semantics rather than line numbers. Review it against production code before every release.

## End-to-end RTS operation flow

### Client input and ownership

While the RTS screen is open, input is offered in visual priority order:

1. Modal dialogs, text boxes, and floating windows.
2. Top bar, bottom bar, and their child panels.
3. Active tools such as shape selection, blueprint capture, and range culling.
4. World interaction and camera control only when no UI/tool owns the input.

Therefore, “nothing happened” may mean a dialog, focused text box, scrolling region, or unfinished A/B selection consumed the input before any server request existed. Mouse wheel input over a scrollable panel must not also zoom the camera.

Camera controls and world actions are separate. Middle-button pan, right-button view rotation, keyboard movement, and wheel zoom only change the client camera. Interaction, placement, mining, and batch actions create server requests. Leaving RTS, death, disconnect, dimension change, or world exit must clean up camera and session state on the appropriate sides.

### Mode, tool state, and ray casting

The top-bar mode decides what an input means. Interact, move, funnel, quick build, and range culling each interpret only their own actions.

World actions ray cast from the RTS camera and mouse position, not the player body's facing. The result can be a block, face, or entity. Batch tools may freeze the first hit as point A and wait for point B, size, rotation, or the confirm key. If a preview is correct but the submitted location is wrong, compare the preview ray semantics with the coordinates and face sent in the request.

### Network boundary

The client expresses intent: place an item, interact/mine a target, submit a chain or shape, place a transformed blueprint, or perform a storage operation. A preview is not authority.

Network handlers return to the server main thread and resolve the player, session, and target. Inventory, permission, plugins, tools, range, and world mutation are server-authoritative.

If `latest.log` contains no corresponding server request/workflow at all, inspect client input ownership, mode, ray target, and packet send. If the request arrived and was rejected, follow the server checkpoints.

### Pipeline, Workflow, and Task Engine

- **Pipeline** is the ordered validation and preparation chain. It validates session, dimension, progression, tools, targets, and permissions; borrows real stacks/tools; executes or submits work; then returns tools, records history, and refreshes UI. A pipe can succeed, fail, or skip with a reason.
- **Workflow** is the player-visible identity and lifecycle of long-running work. It owns type, priority, progress, pause/resume/completion state, and client progress sync.
- **Task Engine** fairly executes server work each tick. It rotates between players within global unit and time budgets so a large build, mine, or blueprint job cannot monopolize the main thread.

Small actions may finish inside one pipeline run. Large shapes and blueprints are usually validated by the pipeline and then executed across ticks. A visible preview proves only that client selection succeeded.

The durable Task is authoritative for long-running work; Workflow is its player-facing projection. Pausing, leaving a world, or rejoining must not let wall-clock expiry delete a non-terminal Task that still exists. Resume continues from persisted progress. When the last projection disappears, the server sends an idle state to clear stale client progress; deleting an already-missing row also triggers authoritative reconciliation.

Each durable task payload is bounded by both a 40 MiB limit and a one-million-node NBT limit. If an unusually large area task exceeds either boundary, the Task Engine terminates only that task from its last valid checkpoint instead of allowing the capacity exception to escape the server tick; other tasks and the player's existing undo history remain intact.

New player workflows take precedence over old unpinned workflows. When the workflow panel or the relevant task-family limit is full, the Task Engine evicts the oldest unpinned task in the current dimension and relevant family; a new request is rejected for capacity only when every relevant older task is pinned. A non-terminal durable task that has lost its valid visible Workflow projection is cancelled and cleaned up instead of remaining as a hidden slot blocker, and an old task restoring its projection cannot evict a newly created visible workflow. Internal funnel and recovery helper tasks may remain hidden, but they do not consume player workflow slots.

### Structured diagnostic logs

The server writes one-line `[RTS-DIAG]` records at shared Pipeline and Workflow boundaries instead of logging every block or tick:

- `BEGIN`: the request arrived, with operation ID, player, mode, task type, and target count.
- `RESULT`: accepted, skipped, or rejected, with workflow ID, failed stage, and stable reason code.
- `FILTER`: one aggregate record when a target batch is filtered by harvest tier, tool, or session range.
- `TERMINAL`: completed, partially failed, cancelled, or timed out, with completed and failed counts.

Use `op` to correlate `BEGIN` and `RESULT`, then use `workflow` to find the asynchronous `TERMINAL`. Common reasons include `FEATURE_LOCKED`, `HARVEST_TIER_TOO_LOW`, `TOOL_CANNOT_HARVEST`, `OUTSIDE_SESSION_RANGE`, and `CLAIM_DENIED`. Ordinary successful single-block actions are normally DEBUG-only; key batch actions and rejections remain visible in normal logs.

## Stable responsibility chains

### Remote interaction

The client ray selects a block, face, or entity. The server recreates a bounded remote interaction context and invokes normal block/item interaction behavior. Claim protection, session dimension, and range still apply. Shift-natural interaction and ordinary RTS interaction are distinct intents; troubleshooting should record the actual input used.

### Placement and batch building

The server validates session, dimension, action range, progression/plugins, claims, and placement legality, then obtains the real `ItemStack` from inventory or linked storage. Capability, NBT, durability, and energy mutations must remain on the real extracted stack. Large shapes become task slices. Only confirmed changes trigger placement animation and material/storage refresh.

### Single, chain, and area mining

Targets pass soft-block classification, plugin harvest tier, real-tool suitability, tool protection, range, claim, and recovery rules. Chain requests obey target-count and per-tick limits; area requests additionally obey per-axis and total-volume limits.

Each chain/area request has independent progress and task identity. Overlap must tolerate a target already becoming air without duplicating drops. “Recover RTS-placed blocks” uses a dedicated recorded-block path that bypasses tool, tier, and Silk Touch requirements.

Area destruction freezes the selected tool slot when the task is submitted. A real tool leased from linked storage takes priority; without a lease, execution reads the task's frozen hotbar slot rather than mutable or stale session state. Harvest checks, tool protection, and durability write-back all use that same real stack.

Drops follow normal break logic, then optionally transfer to linked storage. A failed or partial insertion needs an explicit fallback and must never silently delete items.

### Undo and redo history

Ctrl+Z uses server-authoritative history and executes with the creative/survival mode frozen when the original operation occurred; changing game mode before undo cannot change its resource or NBT rules. Each player keeps only the latest three complete placement or destruction operations. If one operation exceeds the block-count or compressed-NBT limit, the whole history entry is rejected with player feedback instead of retaining an incomplete snapshot.

Creative history stores complete before/after BlockState and block-entity NBT snapshots. Ctrl+Z restores the pre-operation snapshot, while Ctrl+Y redoes only a successfully undone creative operation; any new world modification clears the old redo branch. Survival placement undo removes successfully placed blocks and refunds material to linked storage or player inventory. Survival destruction undo stores only positions and BlockStates, never block-entity NBT, and pays for restored blocks from linked storage, the recorded hotbar slot, or an explicit fallback source. Partial success must trim history by exact positions rather than treating an unprocessed list prefix as completed.

### Blueprints

The client owns the local library, parsing UI, capture selection, naming, rotation, anchor, material preview, and ghost. The server revalidates format, non-air count, missing blocks, plugin, range, materials, claims, and placement legality. Large jobs use durable workflows and the Task Engine; large blueprint data is persisted separately from lightweight task metadata.

Directional/connected blocks and block entities need transform and sanitization. Distortion should be reduced to a minimal blueprint and checked across block-state rotation, block-entity NBT transformation, and neighbor updates.

### Storage, crafting, and funnel

The player/team session owns bindings, browser state, filters, and session flags. Binding records verifiable endpoints; pages are server snapshots sent in bounded pages. Expensive AE2/Refined Storage snapshots are throttled.

Extraction must preserve the real returned stack. Tools, energy items, backpacks, and capability/NBT-heavy items cannot be matched only by item ID and copied with mutations discarded. Remainders return to the original source first, then an explicit fallback.

Funnel mode is held input: while the key/right mouse is held, the client expresses continued collection intent and the server performs bounded work. Crafting is server-authoritative for recipes, ingredients, container remainders, and destination insertion.

### Plugins, survival balance, and RTS Home

With survival progression disabled, plugin/home gates do not restrict ordinary capabilities, but safety limits, claims, and item rules still apply.

When enabled, the server uses the player/team installed-plugin snapshot. Plugins unlock control, remote operations, storage, crafting, chain/area mining, blueprints, culling, field deployment, range, and harvest tiers. Soft blocks such as dirt, snow, and sand need no tier plugin; stone and harder targets require both the plugin tier and a real tool that can mine them.

Without Field Deployment, RTS can start only in the home chunk or its eight neighbors (a 3×3 chunk area). After opening, the session action area is a square centered on the opening position, extending the radius in ±X and ±Z. Home controls where RTS may start; session anchor/radius controls where that session may act.

### Range culling and preview rendering

Range culling is client-only visibility and never changes server blocks. Its persistent identity must distinguish worlds/servers so areas cannot leak between saves.

Placement/destroy previews, selection boxes, skeletons, ghosts, and confirmed animations are visual:

- Preview ghost/wireframe: planned placement before confirmation.
- Range destroy skeleton: merged boundary for non-chain shapes; chain uses skeleton style.
- Place/destroy animation: after server-confirmed mutation.

Preview and execution share shape/rotation/fill/coordinate semantics, but the server independently validates. Renderers must not flush or end Minecraft shared buffers unless lifecycle safety is proven.

UI Core snapshots may be created while `BuilderScreen` is still being constructed. At that point the Screen is not yet attached to Minecraft, so constructor-time code must not read `screen.getMinecraft()`. If current-player state is required, use the nullable client singleton; font- or size-dependent objects wait for normal screen initialization.

## In-RTS settings

All settings below apply immediately and need no restart. Most live in the client UI-state file; items marked “client config” live in `config/rts_building/rtsbuilding-client.toml`. They cannot override multiplayer server rules.

### Controls

| Setting | Default | Effect |
|---|---:|---|
| Middle-button pan sensitivity | Middle preset | Client camera pan input strength. |
| Right-button view sensitivity | Middle preset | Client view rotation input strength. |
| Keyboard movement sensitivity | Middle preset | Client camera keyboard movement preset. |
| Wheel zoom sensitivity | Middle preset | Client wheel zoom input strength. |
| Start camera above player | Off | Starts RTS camera near the player's head. |
| Invert horizontal pan | Off | Reverses left/right drag mapping. |
| Invert vertical pan | Off | Reverses up/down drag mapping. |
| Keyboard final confirmation | On | Batch placement/destruction requires the configurable confirm key (`Enter` by default); client config. |

### UI / display

| Setting | Default | Effect |
|---|---:|---|
| RTS UI scale | 2.0× | Scales RTS UI only; 1.0×–4.0× in 0.5× steps. |
| Player status overlay | On | Shows health, hunger, armor, and absorption in RTS. |
| Container RTS overlay | Off | Shows the RTS side overlay in containers/machines. |
| Shift import | Off | Allows Shift actions in that overlay to insert into linked RTS storage. |
| Storage-ready popup | Off | Brief popup after storage scanning completes. |
| Workflow panel | On | Shows technical background-task progress and controls. |
| Jade follows mouse | Off | Visible when Jade is installed; otherwise fixed left of the top settings button. |
| Hide Jade in RTS | Off | Hides Jade only while the RTS screen is open. |

### Helpers

| Setting | Default | Effect |
|---|---:|---|
| Auto-store mined drops | On | Server session flag; mined drops first try linked storage. |
| Quiet refresh | Off | Prevents storage changes from turning the manual refresh button green; does not disable refresh. |
| Auto-refresh every 30 seconds | On | When storage changed, requests at most one automatic page refresh per 30 seconds. |
| Recover RTS-placed blocks | Off | Recorded RTS placements bypass tool, tier, and Silk Touch checks when recovered. |
| Tool protection | On | Batch mining stops around 5% remaining durability. |
| Auto-exit RTS at half health | On | Returns to ordinary view after damage leaves health at or below half. |
| Beyond Dimensions network | On | Includes an available Beyond Dimensions main network in the storage session. |

### Sound

| Setting | Default | Effect |
|---|---:|---|
| RTS sound master | On | Controls RTS placement, break, and damage sounds. |
| Block placement sounds | On | Controls single, shape, and blueprint placement sounds. |
| Block break sounds | On | Controls single/chain/area break sounds. |
| RTS damage sound | On | Plays vanilla damage feedback while in RTS. |
| Max block sounds per tick | 8 | Client limit 1–16; excess is dropped, not queued. Server has a separate send limit. |

### Animation

| Setting | Default | Effect |
|---|---:|---|
| Smooth RTS camera | On | Uses render-frame prediction/interpolation for movement and drag. |
| Preview block ghosts | Off | Translucent block models before placement confirmation. Client config. |
| Place ghost animation | On | Ghost animation after confirmed placement. Client config. |
| Destroy ghost animation | On | Shrinking ghost after confirmed destruction. Client config. |
| Preview wireframe | Off | Wireframe before placement confirmation. Client config. |
| Place wireframe animation | Off | Wireframe animation after confirmed placement. Client config. |
| Destroy wireframe animation | Off | Wireframe animation after confirmed destruction. Client config. |
| Range destroy skeleton | On | Merged skeleton for non-chain area destruction; chain always uses skeleton. Client config. |

## Mod and server configuration

Saving through “Mods → RTSBuilding → Config” applies to subsequent requests and writes the config. After manually editing TOML, restart the relevant client, integrated world, or dedicated server.

- **COMMON**: `config/rts_building/rtsbuilding-common.toml`; multiplayer uses the server's values.
- **CLIENT**: `config/rts_building/rtsbuilding-client.toml`; local visuals, confirmation, and developer display only.
- **SERVER**: the world's `serverconfig/rts_building/rtsbuilding-server.toml`; server-authoritative.

### Common rules

| Key | Default (range) | Effect |
|---|---:|---|
| `enableSurvivalProgression` | `false` | Enables plugins, survival progression, home, and session range gates. |
| `shareSurvivalProgressionWithTeams` | `false` | Shares home/plugins via FTB Teams, OpenPAC party, or scoreboard team. |
| `maxActionRadiusBlocks` | `128` (48–512) | Final server ceiling for action radius. |
| `enableBlueprints` | `true` | Enables blueprint library/upload/server placement. |
| `maxBlueprintBlocks` | `20000` (1–200000) | Non-air blocks allowed per import, capture, or placement. |

### Client config

| Key | Default | Effect |
|---|---:|---|
| `useBlockGhostPreview` | `false` | Block ghosts before confirmation. |
| `usePlaceBlockGhostAnimation` | `true` | Ghost after confirmed placement. |
| `useDestroyBlockGhostAnimation` | `true` | Ghost after confirmed destruction. |
| `useWireframePreview` | `false` | Wireframe before confirmation. |
| `usePlaceWireframeAnimation` | `false` | Wireframe after confirmed placement. |
| `useDestroyWireframeAnimation` | `false` | Wireframe after confirmed destruction. |
| `useRangeDestroySkeleton` | `true` | Skeleton for non-chain area destruction. |
| `showInventoryRtsButton` | `true` | Shows the RTS entry button at the top of the vanilla inventory. |
| `requireKeyboardBatchConfirm` | `true` | Requires keyboard final confirmation. |
| `developerMode` | `false` | Shows developer scenario entry and enables local developer diagnostics. |

### Server runtime limits

| Key | Default (range) | Effect |
|---|---:|---|
| `mining.ultimineMaxBlocks` | `256` (1–4096) | Maximum targets collected by one chain request. |
| `mining.areaMineMaxSize` | `36` (1–64) | Compatibility per-axis ceiling; in-game save syncs it to max(width,height,depth), clamped to 64. |
| `mining.areaMineMaxVolume` | `46656` (1–262144) | Width×height×depth limit. |
| `mining.areaMineMaxWidth` | `36` (1–256) | X width limit. |
| `mining.areaMineMaxHeight` | `36` (1–256) | Y height limit. |
| `mining.areaMineMaxDepth` | `36` (1–256) | Z depth limit. |
| `mining.areaMineMaxHarvestTier` | `UNLIMITED` | Server ceiling for area-mining plugin tier. |
| `mining.areaDestroyMaxTargets` | `98304` (1–262144) | Explicit target positions accepted by one area request. |
| `mining.ultimineBlocksPerTick` | `32` (1–128) | Batch targets processed by one mining task slice. |
| `storage.ae2NetworkRefreshThrottle` | `10` (1–200) | Refresh cycles between expensive AE2 snapshots. |
| `storage.refinedStorageNetworkRefreshThrottle` | `10` (1–200) | Refresh cycles between expensive RS snapshots. |
| `storage.pageCacheMaxPlayers` | `256` (1–4096) | Player entries retained in the page LRU cache. |
| `storage.defaultStoragePageSize` | `90` (1–4096) | Default entries per page, capped by max page size. |
| `storage.maxStoragePageSize` | `180` (1–8192) | Maximum entries accepted in one page request. |
| `placement.buildBatchBlocksPerTick` | `64` (1–512) | Remote placements processed per player per tick. |
| `placement.buildBatchMaxQueuedJobs` | `4` (1–32) | Queued quick-build jobs per player. |
| `taskEngine.maxUnitsPerTick` | `256` (1–4096) | Global work-unit hard limit per tick. |
| `taskEngine.maxUnitsPerSlice` | `32` (1–512) | Units given to one player before rotation. |
| `taskEngine.maxNanosPerTick` | `8000000` (250000–20000000) | Main-thread cooperative budget in nanoseconds per tick. |
| `interaction.remotePovBlockReach` | `4.0` (1.0–16.0) | Temporary reach while replaying a remote action. |
| `mining.dropScanRadius` | `1.25` (0.25–8.0) | Radius for absorbing drops around remotely mined blocks. |
| `placement.remoteBlockActionSoundsPerTick` | `16` (0–16) | Remote block-action sounds sent per player per tick; excess is dropped. |
| `fluid.internalFluidCapacityBuckets` | `100` (1–4096) | Fallback internal fluid capacity in buckets. |

## Symptom-to-checkpoint table

| Symptom | Likely checkpoint | Player verification |
|---|---|---|
| Click/key does nothing | UI ownership, text focus, wrong mode, pending A/B state, no packet | Close windows, cancel tool with `Esc`, reselect mode, report exact input. |
| Progress is stuck after rejoin, has no highlight, and X does nothing | Durable Task resume, Workflow projection expiry, or missing client idle sync | Preserve the save and provide logs before/after rejoin; clicking X requests authoritative reconciliation. |
| Preview appears, confirm does nothing | Confirm key, packet, session/dimension, plugin, range, claim, task admission | Press configured confirm key; check toast/actionbar and whether `latest.log` contains a request/workflow. |
| Right click needs two attempts | First click became drag, UI/mode consumed it, release did not form interaction | Short-click away from panels and report mode/highlight before and after first release. |
| Chain cannot mine dirt or snow | Not normally harvest-tier-gated: inspect Remote Control, Chain Break, session, claims, and soft-block classification | Try single-block mining; report progression, installed plugins, and block ID. |
| Stone/ore cannot be mined | Tier plugin, server tier ceiling, real tool, or tool protection | Compare with vanilla diamond pickaxe; report required/current tier message. |
| Disabling progression fixes it | Progression gate, plugin snapshot, home start gate, session radius | Refresh plugin page and report reinstall history, home, and RTS opening position. |
| Cannot open away from home | Home 3×3 start gate | Return to home chunk/neighbors or verify Field Deployment. |
| RTS opens but distant action fails | Square session anchor/radius or server maximum | Calculate ±X/±Z from the position where RTS opened. |
| Materials exist but build fails | Item identity/NBT, storage snapshot, plugin, claim, replaceability, full job queue | Try one vanilla block, refresh storage, verify claim and target replaceability. |
| Blueprint directional blocks distort | State rotation, block-entity NBT, neighbor update, compatibility | Test minimal file at 0/90/180/270 and provide block IDs/file. |
| Drops do not enter storage | Auto-store, binding, full/invalid endpoint, scan radius, insertion compatibility | Refresh storage, insert same item manually, check ground drops/capacity. |
| Installed plugin still locked | Stale server snapshot, team identity, failed durable install | Refresh plugin screen and verify installed list/contributor after relog. |
| Culling leaks across worlds | Persistence key lacks world/server identity or world-switch cleanup | Report both world/server identities and dimension; clear and switch again. |
| Preview/animation is missing | Client setting, render stage, buffer conflict, optimization/shader compatibility | Check both ghost/wireframe toggles and compare without shader/optimization mod. |
| Large task is slow | Per-operation tick limit, global Task Engine units/time, competing players | Compare smaller target and report TPS, target count, concurrent workflows. |
| Storage page refresh is slow | 30-second refresh, quiet mode only hides cue, AE2/RS throttle/network size | Click manual refresh and report storage type/network size. |

## Troubleshooting information

The most useful bundle contains RTSBuilding/Minecraft/loader versions, singleplayer or server, current mode, survival progression, installed plugins, target block/item IDs, real tool, linked storage type, minimal steps from opening RTS, and `latest.log` captured during reproduction.

When information is incomplete, offer safe reversible checks first, then ask for the most important 1–3 missing details. Do not demand the complete mod list immediately. If the guide cannot establish the cause, state that clearly instead of presenting inference as fact.

## Version-specific technical appendix: Forge 1.20.1

The release-preparation version for this line is `1.1.7-beta1`; if the test build is unstable, return to the latest release `1.1.6-patch2`. This is the Forge 1.20.1 port, so NeoForge event/API names are not direct evidence here.

### Task cancellation and diagnosis

When a new single mine, chain mine, or area-destruction replacement action starts, non-terminal `TaskType.MINING` and area-destruction `TaskType.DESTRUCTION` tasks for the same player and dimension must enter the real `CANCELLED` state through `RtsTaskEngine.cancelWorkflowTask(...)`. The path must release leases, write a tombstone, preserve cursor/succeeded, and close the workflow. Hiding the UI or cancelling only a token is not enough. Without a new replacement action, `WAITING_RESOURCE` may remain and resume after resources recover; PLACE, QUICK_BUILD, and BLUEPRINT_BUILD must not be cancelled.

### Log-analysis quick reference

The prompt includes the last 200 lines of `latest.log` and the latest 200 RTSBuilding records. Find the earliest abnormal event, correlate `trace` across `seq/op/workflow/task`, and inspect `[RTS-TRACE]`, `[RTS-DIAG]`, `[RTS-SERVER-HEALTH]`, `BEGIN/RESULT/FILTER/TERMINAL`, and the first relevant stack trace. Separate client/network failure, server rejection, resource waiting, terminal state, and cascade errors. Distinguish logged facts, supported conclusions, and inferences; do not treat the last error as the root cause or request the entire log/mod list by default.

### Forge 1.20.1 differences

This line uses the Forge event bus, `mods.toml`, and 1.20.1 networking/menu adapters. The durable TaskStore and workflow-restoration semantics are shared, but class paths, mappings, and Forge APIs differ from NeoForge. A server durable snapshot is more reliable evidence than a vanished client workflow.

### Feedback channels

If you need GitHub, Discord, QQ, or another feedback channel, reply “反馈渠道”.

### Smart Build and Smart Destroy implementation chain

The Smart Build player entry is Fill inside Quick Build → Range Build → Tools. SmartFillClientSession uses the crosshair, selected material, Maximum blocks, and Detection diameter to create the preview; the first left click saves the anchor and the second left click sends confirmation. The C2S payload carries the clicked position, hit face, material, and parameters. RtsSmartFillService then rescans the cavity on the server with SmartFillPlanner and SmartFillCandidateClassifier, checks loaded boundaries, candidate blocks, size limits, and material, and passes accepted targets to the PLACE_BATCH workflow. A client preview is not the server plan.

The Smart Destroy player entry is Quick Build → Range Destroy → Tools. QuickBuildConvenienceController maps Range Quarry, Chunk Quarry, and One-click Tree Fell to REPEAT_BOX, CHUNK_QUARRY, and TREE_FELL; RtsDestroyPreviewPlanner creates the client preview and the submission sends the anchor, hit face, and parameters. RtsConvenienceDestroyPlanner rebuilds the targets on the server: Range Quarry repeats an X/Y/Z box, Chunk Quarry covers the aimed chunk with adjustable upward/downward height, and One-click Tree Fell scans connected logs, natural leaves, and giant mushrooms through 26-neighbor traversal. Target chunks must be loaded; volume or tool limits reject the complete request, and accepted targets enter the DESTRUCTION workflow.
