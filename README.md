# Relict

Fabric client mod for base hunting. See [PLAN.md](PLAN.md).

- Minecraft **26.3**, **26.2**, **26.1.2**, **1.21.11**, **1.21.10** and **1.21.4**, Fabric Loader 0.20.1, Kotlin (fabric-language-kotlin)
- Fabric Loom 1.19 needs **Java 25+ to run Gradle**. The jars for 1.21.x compile to Java 21; the 26.x jars need Java 25
  (which the Minecraft launcher supplies by itself for those versions).

## The game GUI

Press Right Shift in game (it closes again on the same key, or Esc). The window is a Svelte page shown through MCEF
(Chromium), floating over the world. To open instantly, the page is loaded in the background a few seconds after you
join a world and then waits parked at one frame per second; Chromium itself is not started until then. Modules are grouped
in the sidebar (Donut, Search), each row expands to its settings and has a switch, and the Theme page picks the accent
colour. The page is sized by the window, not by Minecraft's GUI scale. To work on the look without starting Minecraft, run
`npm run dev` in `ui/`: it uses made-up modules, so nothing needs the game.

The region map, the base finder list and the pop-ups are a second page (`#hud`) drawn over the world by a transparent,
always-on browser that is only as tall as the panels need and closes after 30 seconds of having nothing to show. They
animate in and out at 60 fps. "HUD size" in Settings scales them. Versions without this layer (1.21.4 and 26.x for now)
use plain pixel HUDs and the game's own toasts instead.

## Region map and chunk outlines

DonutSMP is a Velocity network of regional servers, so your connection changes from area to area. The region map colours
every area by its group (EU Central, EU West, NA East, NA West, Asia, Oceania, read from the server's own map), shows
your ping in each area you have stood in (averaged, and remembered per server), and turns the area you are in white. A
setting switches the numbers to the server's region numbers instead.

Chunk ESP outlines suspicious chunks flat on the world floor (Y -64) so they can be seen from anywhere, fading as you get close
to Y -30. The colour is the chance, the same one the Base Finder list uses: orange-red when it is unlikely, yellow kind of
likely, green likely, blue at 99%. Purple means a player may be in the chunk (blocks changed there lately; it is a hint, not
proof). A dot in the middle of each chunk shows its details, a card beside the crosshair, when you aim at it. The Stash/Farm
ESP outlines stashes, machines and farms in plain white, with the same dot and card. Block ESP outlines the blocks you list,
one colour per kind of block, and every dye colour of a block (shulker boxes, beds, banners...) counts as one.

What the finder scores: containers and hoppers, redstone parts (repeaters and comparators never generate on their own), plain
stone in the deepslate layer and deepslate above Y 8 (not natural), how many different kinds of player-made blocks a chunk
holds, and farms (plants growing with the machines that harvest them). It leaves out what a fresh world makes by itself:
ancient cities, abandoned mineshafts, strongholds, trial chambers and dungeons are recognised and their chests, planks,
rails and bricks do not count, and kelp, grass, dripstone, amethyst and other plants count for nothing unless machines are built
around them. Anything that is only a sign that a player may be in a chunk is called "possible player".

The **Notifier** is a module of its own: it shows a notification in the bottom right for a new stash, farm or base (at least the
chance you set), for a chunk where blocks changed, and for a player the game has loaded near you.

## Donut modules

Off by default, one toggle each. DonutSMP staff have said a client like this is allowed as long as it does not harm other players,
so it only reads what the game has already loaded and never acts for you (see the staff note in [PLAN.md](PLAN.md)): **Donut Regions**
(the 9x9 region map), **Base Finder** (the list), **Live Players**, **Notifier**, **Stash/Farm ESP**, **Block ESP** and **Chunk ESP**.
Reach, speed, flight and other combat or movement help are not part of Relict.

| Minecraft | HUDs and the finder | In-world overlays (Base/Block/Chunk ESP) |
|---|---|---|
| 1.21.11 | ✅ | ✅ through walls, tested |
| 1.21.10 | ✅ | ❌ no public way to build the render layers; the module cards say so |
| 1.21.4, 26.x | ⚠️ finder logic works, HUD/overlay drawing not ported yet | ❌ |

## Supported Minecraft versions

Relict needs an embedded Chromium (MCEF), so a Minecraft version is only possible where an MCEF build exists.
"Tested" means `./gradlew runClientGametest` passes in a real world on that version.

| Minecraft | Status | Notes |
|---|---|---|
| 26.3, 26.2, 26.1.2 | ✅ supported, tested | MCEF Modern, Mojang names, no remapping (`build.mojmap.gradle.kts`). The 26.1 jar accepts any 26.1.x |
| 1.21.11, 1.21.10 | ✅ supported, tested | MCEF Modern, Yarn names |
| 1.21.4 | ✅ supported, tested | CinemaMod's MCEF, Yarn names |
| 1.21.2, 1.21.3 | ⚠️ should work, not shipped | same code as 1.21.4, but Fabric's client game-test API (what proves a version) only exists from 1.21.4 |
| 1.21, 1.21.1 | ⚠️ feasible | MCEF exists; needs a one-line shader compat, and the same test gap |
| 1.20.x | ⚠️ feasible, more work | MCEF exists; Java 17 target and older GUI APIs |
| 1.21.5 – 1.21.9 | ❌ not possible | no MCEF build exists for these versions |
| 1.19 and older | ❌ not practical | no maintained MCEF |

```
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./gradlew build        # jar in build/libs/ (newest Minecraft version)
./gradlew runClient    # dev client
```

## Supporting several Minecraft versions

Shared code never names a Minecraft class: it talks to the `Game` interface (`src/main/kotlin/relict/game`) and to
`KeyHandle`/`KeyCodes`, so the same source compiles under Yarn names and Mojang names. What differs lives under
`src/compat/<name>/kotlin`, and each version lists the folders it uses in `compat_dirs`:

| Folder | Holds |
|---|---|
| `modern` / `legacy` | the MCEF wrapper: MCEF Modern (1.21.10+) or CinemaMod's MCEF (1.21.4) |
| `yarn` / `mojmap` | the `Game` implementation and key codes for Yarn-named (1.21.11 and older) or Mojang-named (26.x) Minecraft |
| `modern-yarn`, `legacy-yarn`, `modern-mojmap` | the browser screen, key handling and key registration, which depend on both |
| `<mc>` or a small named folder (`mojmap-gui-screen`, …) | one-off differences between neighbouring versions |
| `gametest-*` (under `src/gametest-compat`) | the same idea for the game test's helpers |

`gradle.properties` holds the defaults (the newest Yarn version); `versions/<mc>.properties` overrides them per version.
26.x builds use `build.mojmap.gradle.kts` (a different Loom plugin: nothing is remapped); `settings.gradle.kts` picks it
when `-PmcVersion` starts with `26.`. Shared Gradle logic is in `gradle/*.gradle.kts`.

```
./gradlew build -PmcVersion=26.2      # one version
./scripts/build-all.sh                # every version in versions/, jars collected in dist/
```

To add a version: add `versions/<mc>.properties` (copy the nearest one, and choose its `compat_dirs`), then prove it with
`./gradlew runClientGametest -PmcVersion=<mc>`, then run `./scripts/build-all.sh` and
`python3 scripts/gen-installer-manifest.py`. Only add versions that pass the game test. Expect new Minecraft versions to
need a few lines in a small per-version folder: the compiler tells you which.

`ExitGuard` makes the game quit cleanly on 26.2+, which waits for every thread to end and otherwise writes a crash
report: Chromium leaves idle AWT threads behind on macOS, so once the game has finished the guard exits the process.

## Tests

```
./gradlew test                              # unit tests (settings, modules, storage, coverage grid, waypoints, API)
./gradlew runClientGametest [-PmcVersion=…] # starts a real game, creates a world, flies a path, drops waypoints,
                                            # opens the GUI with Right Shift and screenshots every page
./gradlew -p installer test                 # installer tests
```

Screenshots from the game test land in `build/run/clientGameTest/screenshots/`.

## Installer (Mac and Windows)

`installer/` is a small desktop app that sets Relict up for you. Its window is a web page (the same dark indigo design as the
in-game GUI) shown in a JavaFX WebView; the Kotlin side does the work. The page lives in `ui/src/installer`, so the whole
design system is shared with the game: `npm run dev:installer` in `ui/` previews it in a browser with made-up launchers, and
`--screenshot` below paints the real window. It looks for the launchers on your computer by itself:

| Launcher | What it finds | What it does |
|---|---|---|
| Minecraft Launcher (official) | the `.minecraft` data folder | installs Fabric Loader, adds a "Relict <version>" profile, and puts Relict in its own instance (or the main folder, your choice) |
| Prism Launcher, PolyMC | every instance, with its Minecraft version and Fabric Loader from `mmc-pack.json` | puts Relict and its dependencies into that instance's `mods` folder |
| Modrinth App | every profile, with its version and loader read from the app's `app.db` (a copy, never the live file) | the same, into the profile's `mods` folder |
| Anything else | "Browse…" for any game folder | the same, you pick the Minecraft version |

The Prism and Modrinth parts follow those apps' file formats; Prism was checked against real instances, the Modrinth reader
against a database built to its table layout and not yet against a real Modrinth App install.

Instances that run a Minecraft version Relict doesn't support, or that don't use Fabric (or have a Fabric Loader older
than 0.18.4), are shown with the reason instead of being installed into. Dependencies are downloaded, checked against
pinned SHA-512 hashes, and an older copy of Fabric API in an instance is moved aside (restored when you uninstall).
Close Minecraft before installing. Installs made before the client was renamed (when it was "Veyly") are replaced in place.

```
./scripts/build-all.sh                          # 1. the Relict jars the installer carries
python3 scripts/gen-installer-manifest.py       # 2. only when versions change: pin dependency files + hashes
./installer/package.sh                          # 3. Mac: "Relict Installer.app" and a .dmg in installer/build/package/
./gradlew -p installer fatJar                   #    or a plain jar: installer/build/fat/RelictInstaller.jar
```

`jpackage` can't cross-compile, so the Windows build runs on Windows: `.github/workflows/release.yml` builds both the
Mac `.dmg` and the Windows app (`Relict Installer.exe` in a zip) on GitHub runners. Neither is code-signed, so macOS and
Windows will show an "unidentified developer" / SmartScreen warning until you sign them with your own certificates.

The logo (a radar with a donut) is `branding/relict-logo.svg`, drawn by `scripts/gen-logo.py`. `scripts/make-icons.sh`
turns it into the app icons (`.icns`, `.ico`, the mod icon), and `scripts/sync-logo.py` copies it into the in-game GUI.
The installer's own version is in `installer/src/main/resources/installer.properties`.

A command-line mode exists for scripts and testing:

```
RelictInstaller --cli list                                      # what it found, numbered
RelictInstaller --cli install|uninstall|status --target 3       # one of those (an instance knows its Minecraft version)
RelictInstaller --cli install 1.21.11 [--dir <minecraft folder>] [--layout separate|main]
RelictInstaller --cli install 1.21.11 --instance <game folder>  # any folder that has (or gets) a mods folder
RelictInstaller --cli check                                     # is there a newer Relict or installer in the update feed?
RelictInstaller --cli update                                    # install it wherever Relict is installed
RelictInstaller --screenshot out.png [--update] [--select <n>]  # opens the window, saves it as a PNG and quits
```

### Updates

The installer carries one Relict build, but it can also look for newer ones. Updates come from a feed:
`update.json`, the installer's manifest plus a download URL and SHA-512 for each Relict jar (and, optionally, for a newer
installer app).

**On GitHub** (the repository has to be public) `.github/workflows/release.yml` does all of it:

```
# 1. bump mod_version in gradle.properties, then pin the dependency files for it (needs network)
python3 scripts/gen-installer-manifest.py
# 2. commit, tag and push
git tag -a v0.2.0 -m "What changed (shown in the installer's update banner)"
git push origin v0.2.0
```

The workflow checks that the tag, `mod_version` and the manifest agree, builds the six jars, packages the Mac and Windows
installers (with this repository's `releases/latest/download/update.json` built in as their feed), writes `update.json` and
publishes a Release with all of it. Anyone who already has Relict gets it the next time they open the installer. Running
the workflow by hand only builds the files and keeps them as downloadable artifacts. The Mac installer is built for
Apple Silicon only; Intel Macs are not offered it, and the Windows build is not tested outside CI.

**Anywhere else:** upload the files yourself.

```
./scripts/build-all.sh
python3 scripts/gen-update-feed.py https://your-host/relict --notes "What changed"   # writes dist/update.json
# upload dist/update.json and the dist/relict-mc*.jar files to https://your-host/relict/
# then set feedUrl=https://your-host/relict/update.json in installer/src/main/resources/update.properties
# and rebuild the installer once (package.sh). After that, new builds only need the upload.
```

The window checks the feed when it opens. With "Update automatically" on (the default) it installs the newer build everywhere
Relict is already installed, on any launcher it found; with it off, a banner offers an "Update now" button. Updating keeps the
layout the version was installed with, and a failed download or hash check leaves the working install untouched. The
feed must be https, and its jars may only come from the feed's own host. Without a `feedUrl` the installer says updates
aren't set up. For trying it out, `-Drelict.updateUrl=...` or the `RELICT_UPDATE_URL` variable overrides the address.

The feed can also announce a newer installer app (`--mac` / `--windows` for `gen-update-feed.py`). A running app can't replace
itself, so the installer downloads the new file to your Downloads folder (checked against its hash) and opens it.

On first launch the mod's MCEF Modern downloads its Chromium (JCEF) build into `<game dir>/config/mcef-modern/`.
