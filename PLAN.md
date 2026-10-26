# Relict Client — Plan

A Fabric client mod for DonutSMP base hunting. It makes searching **organized and efficient** (coverage tracking, waypoints, route planning, logging) and gives you **no information the game wouldn't normally show you**.

## 0. Ground rules (decide these before writing code)

**Check DonutSMP's rules first.** I couldn't find authoritative rules online (search returned only low-quality pages), so read their website/Discord rules channel and ask staff about each module below. Staff rulings override this plan.

Every module is tagged by risk:

| Tier | Meaning | Policy |
|---|---|---|
| 🟢 Green | Bookkeeping/UI only. Uses only your own position, your own screenshots, what you type, chat you receive. | Build by default |
| 🟡 Yellow | Automatically analyses what's in your render distance. Info is "visible" but automation can cross a line. | Off by default, ship only after staff OK |
| 🔴 Red | Reveals hidden info or exploits server behaviour. | **Never build** |

**Red list (explicitly out of scope):** Xray/ore-ESP/block-ESP through walls, StorageESP/chest ESP, entity ESP through blocks, NewChunks / chunk-age detection, chunk-load-pattern or packet-timing exploits, tracers to unseen players, coordinate/seed cracking, anything that spoofs the client brand, auto-flight/auto-pathing that plays for you, kill-aura-style automation.

Don't spoof the client brand; leave it as `fabric` so the server sees an honest client.

### Update: staff-approved "Donut" modules

The project owner reports that DonutSMP staff said **base-hunting utilities are allowed** (a Base Finder, Base ESP,
Block ESP, Chunk ESP, a live-player search, and the region / search HUDs), while anything that gives a combat or
movement advantage (reach, speed, flight, and the like) is **not**. On that basis the red list above is relaxed for
these specific modules only, and they are built as the **Donut** group (tier 🟡, off by default, each with its own
toggle). This rests on that report: get the staff statement in writing (a Discord message or screenshot) and keep it.

Still out of scope, whatever the rules say: reach, speed, flight, kill-aura or any combat/movement automation; anything
that spoofs the client brand or hides the client from the server; process injection.

## 1. Tech stack

- **Minecraft/Java:** 1.21.11 (last Yarn-mapped version), Java 21 target. Gradle itself needs Java 25 (Loom 1.19). Confirm DonutSMP accepts this version.
- **Loader:** Fabric + Fabric API. Language: Kotlin (or Java if you prefer; MCEF has Java API).
- **UI:** [MCEF Modern](https://github.com/DimasKama/mcef-modern) (Chromium Embedded Framework for Minecraft; the CinemaMod MCEF stops at 1.21.4). It has no custom `mod://` scheme, so the bundled UI is extracted to `<game dir>/relict/ui` and loaded over `file://`. Front-end in Svelte or React + Vite, bundled into the jar.
- **Perf-sensitive overlays (HUD, minimap) stay native** (`HudRenderCallback` / `DrawContext`). Use MCEF only for full-screen panels (dashboard, map, settings). Chromium rendering every frame costs FPS; only create/render the browser while a panel is open and destroy or pause it when closed.
- **Storage:** JSON/SQLite under `.minecraft/relict/` (per-server profile).

MCEF caveats to plan for:
- First launch downloads ~100+ MB of CEF binaries from `mcef-download.cinemamod.com`. Show a progress screen and a clear error if blocked.
- Antivirus can interfere; document it.
- MCEF is a **dependency mod**: users install it alongside Relict (declare in `fabric.mod.json` `depends`). Pin to a version built for your MC version.
- Verify the exact MCEF API (browser creation, resolution/scaling, mouse/keyboard forwarding, message router for JS↔Java) against the current repo before coding. Don't trust older tutorials.

## 2. Architecture

```
relict-client/
├─ src/main/kotlin/relict/
│  ├─ RelictClient.kt          # ClientModInitializer
│  ├─ core/
│  │  ├─ Module.kt            # name, tier, enabled, settings, onEnable/onDisable
│  │  ├─ ModuleManager.kt
│  │  ├─ EventBus.kt          # tick, world join/leave, chat, render
│  │  ├─ Settings.kt          # typed settings → JSON, exposed to UI
│  │  └─ Storage.kt           # per-server/per-dimension data
│  ├─ ui/
│  │  ├─ RelictScreen.kt       # Screen that hosts the MCEF browser
│  │  ├─ Bridge.kt            # JS <-> Java messages (JSON RPC)
│  │  └─ hud/                 # native HUD widgets
│  └─ modules/                # one package per module
├─ src/main/resources/assets/relict/ui/   # built web bundle
├─ ui/                        # Vite project (source of the web UI)
└─ PLAN.md
```

**Bridge:** the page calls `window.relict.invoke("waypoints.list", {...})` → Java handler → JSON back. Java pushes state with `relict.emit("player.pos", {...})` at a low rate (4–10 Hz, not every tick). Keep the UI dumb; all logic and state in Java.

**Module contract:** each module declares id, tier, default-enabled, settings schema (auto-rendered in the UI), and optional HUD widget and UI page. Yellow modules are hidden behind a "Experimental" toggle.

## 3. Modules

### 🟢 Green — build these

1. **Search Coverage Map**: records which chunks you've *been near* (your position + your actual render distance) per dimension and draws a zoomable map of searched vs. unsearched area, with heat for time spent. The core base-hunting tool: never fly the same region twice.
2. **Search Planner**: define a region (rectangle/ring around spawn/rtp zone), generate a lawnmower/spiral route with lane spacing based on your render distance, show the next waypoint as a HUD arrow + distance + ETA. It guides *you*; it doesn't move you.
3. **Waypoints & Finds Log**: one keybind to drop a find at your position with tags (suspected base, farm, lava, sign, other), notes, screenshot, dimension, timestamp. Filter/sort/export in the UI.
4. **Coordinate parser**: detect coordinates in chat or pasted text (`x y z`, `/tp` style, `[x, z]`) and offer "save as waypoint".
5. **Nether/Overworld converter**: ÷8 / ×8 and linked-portal estimate, shown in the HUD and map.
6. **Player sighting log**: log names of players you actually see in render range / tab list, with coords and time (no through-wall tracking).
7. **Travel HUD**: coords, facing, speed, distance flown, elytra durability, firework count, hunger/HP warnings, session timer.
8. **Safety alerts**: low elytra durability, no fireworks, low HP, void/lava proximity, AFK/idle warnings.
9. **Session stats**: area covered per hour, finds per session, distance travelled; session history in the UI.
10. **Screenshot annotator**: screenshots stamped with coords/dimension/time, with quick markup in the UI.
11. **Share/import**: export waypoints/coverage as a code or JSON file to paste to your team or Discord; import theirs.
12. **UI shell**: dashboard, map view, settings, module toggles, theme.

### 🟡 Yellow — only after staff approval, off by default

- **Visible-anomaly marker**: scans only blocks within your render distance *and* line of sight for obviously player-placed blocks on natural terrain (e.g., glass, concrete, torches, signs) and offers to log them. Risk: it's automated scanning, so treat it as ESP-adjacent until staff say otherwise.
- **Fullbright/gamma** helper (many servers accept it, some don't).
- **Lightweight minimap** with entity dots limited to what's already visible.

### 🔴 Red — not building

See ground rules.

## 4. Phases

| Phase | Deliverable | Done when |
|---|---|---|
| 0 Setup | Fabric template, Gradle, MCEF dependency, runs in dev on your Mac | `./gradlew runClient` opens MC with Relict + MCEF loaded |
| 1 Browser spike | A `Screen` showing a local page via MCEF; input works; closes cleanly | Click/type/scroll work; no leaks; CEF downloads on first run |
| 2 Core | Module system, settings, storage, event bus, keybinds | Toggle a dummy module from code and from UI |
| 3 Bridge + UI shell | JSON bridge, Svelte app, dashboard + settings + module list | UI reads/writes real settings |
| 4 Coverage Map + Waypoints | Modules 1, 3, 5 with map view | Fly around; map fills; waypoints persist across restarts |
| 5 Planner + HUD | Modules 2, 7, 8 | Route arrow guides a full lane sweep |
| 6 Logs & sharing | Modules 4, 6, 9, 10, 11 | Round-trip export/import with a friend |
| 7 Compliance + polish | Staff review, perf pass, crash handling, packaging | Stable 60+ FPS with panel closed; release jar |

## 5. Risks

- **Rules:** server may disallow *any* unapproved mod; get written/Discord confirmation before using it online.
- **MCEF compatibility:** version coupling to MC version; Apple Silicon quirks; CEF download failure. Mitigate with graceful fallback to a native config screen.
- **Performance:** keep Chromium off the HUD path; throttle bridge events.
- **Data growth:** coverage data is large; store as per-region bitsets (e.g., 512×512 chunk regions), not per-chunk JSON.
- **Server changes:** DonutSMP details (spawn/rtp region size, dimensions, version) may change; make route regions configurable.

## 6. Open questions for you

1. Which MC version/launcher do you play on?
2. Kotlin or Java?
3. Solo tool, or a client you'll share (affects distribution and rule risk)?
4. Do you want team sync later (needs a small backend), or just file/code sharing?

## 7. Open items (reminder list, 2026-10-26)

Left over from the Donut modules work:

- **Donut GUI page** in the Svelte UI: region grid, Base Finder list, live players.
- **Port HUDs and world overlays** to 1.21.4 and 26.1.2 / 26.2 / 26.3 (1.21.11 has both; 1.21.10 has HUDs only: no public way to build the through-walls render layers).
- **In-world tooltips/labels** on Base ESP boxes (e.g. "Unclear 583 score…"); screenshots proving Block ESP and Chunk ESP.
- **From the project owner:** DonutSMP API/data for the region to group mapping (only 3 of 81 cells known: 22 EU Central, 107 EU West, 5 NA East) and what the server shows for a "live player".
- **Get the DonutSMP staff approval in writing** (Discord message or screenshot); the Donut modules rest on that report.
- Phase 4 gap (Nether/Overworld converter), Phase 5 (Planner, Travel HUD, Safety alerts), Phases 6 and 7.

Installer: set `feedUrl` in `installer/src/main/resources/update.properties` once an https host for `update.json` exists (see README, "Updates").

Installer follow-ups: try the Modrinth App reader against a real Modrinth install (only tested against a database built to its table layout), sign the Mac/Windows installers, and push the project to a public GitHub repo so `release.yml` can run (it has never run: the first tag will show any problems, especially the Windows packaging step).
